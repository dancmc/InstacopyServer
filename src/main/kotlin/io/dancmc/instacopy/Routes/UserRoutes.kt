package io.dancmc.instacopy.Routes

import io.dancmc.instacopy.Data.Database
import io.dancmc.instacopy.Data.User
import io.dancmc.instacopy.Utils
import io.dancmc.instacopy.fail
import io.dancmc.instacopy.success
import kotlinx.coroutines.experimental.launch
import org.json.JSONArray
import org.json.JSONObject
import org.neo4j.graphdb.Label
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.RelationshipType
import org.neo4j.graphdb.Result
import spark.Route
import java.util.*
import javax.servlet.MultipartConfigElement

object UserRoutes {
    val register = Route { request, response ->
        val requestJson = JSONObject(request.body())
        val username = requestJson.optString("username", "").toLowerCase()
        val password = requestJson.optString("password", "")
        val firstName = requestJson.optString("first_name", "")
        val lastName = requestJson.optString("last_name", "")
        val email = requestJson.optString("email", "")
        val displayName = requestJson.optString("display_name", "").toLowerCase()
        if (username.isBlank() || password.isBlank() || email.isBlank() || lastName.isBlank() || displayName.isBlank()) {
            return@Route JSONObject().fail(-1, "Missing field")
        }

        val user = User(userID = UUID.randomUUID().toString(), username = username, passwordHash = Utils.Password.hashPassword(password),
                email = email, emailVerified = false, firstName = firstName, lastName = lastName, active = true,
                isBot = false, displayName = displayName, profileName = "", profileDesc = "", isPrivate = false)

        val result = Database.addUser(user)
        if (result.first) {
            val jwt = Utils.Token.createAppToken(result.second)
            JSONObject().success()
                    .put("jwt", jwt)
                    .put("user_id", result.second)
                    .put("username", username)
                    .put("display_name", displayName)
        } else {
            JSONObject().fail(message = result.second)
        }
    }

    val login = Route { request, response ->
        val requestJson = JSONObject(request.body())
        val username = requestJson.optString("username", "").toLowerCase()
        val password = requestJson.optString("password", "")

        val user = Database.getUser(username = username)
        if (user == null) {
            JSONObject().fail(message = "User not found")
        } else {
            Database.executeTransaction {
                if (Utils.Password.verifyPassword(user.getProperty("password_hash") as String, password)) {
                    JSONObject().success()
                            .put("jwt", Utils.Token.createAppToken(user.getProperty("user_id") as String))
                            .put("username", username)
                            .put("user_id", user.getProperty("user_id") as String)
                            .put("display_name", user.getProperty("display_name") as String)
                } else {
                    JSONObject().fail(message = "Wrong password")
                }
            }
        }
    }

    fun getInfoQuery(myUserID: String, otherDisplayName: String): Pair<String, HashMap<String, Any>> {
        val params = hashMapOf<String, Any>()
        params.put("my_user_id", myUserID)
        params.put("wanted_display_name", otherDisplayName)

        return Pair("with \$wanted_display_name as wanted_display_name, \$my_user_id as my_user_id\n" +
                "MATCH (u:User{display_name:wanted_display_name})\n" +
                "with u as wanted_user, my_user_id\n" +
                "Optional MATCH ()-[f:FOLLOWS]->(wanted_user) \n" +
                "with wanted_user, count(f) as followers, my_user_id\n" +
                "optional MATCH ()<-[f:FOLLOWS]-(wanted_user) \n" +
                "with wanted_user, followers, count(f) as following, my_user_id\n" +
                "optional MATCH ()<-[p:POSTED]-(wanted_user) \n" +
                "with wanted_user, followers, following, my_user_id, count(p) as posted\n" +
                "MATCH (me:User{user_id:my_user_id})\n" +
                "Optional MATCH (me)-[:FOLLOWS]->(u1), (u1)-[:FOLLOWS]->(wanted_user)\n" +
                "return wanted_user.display_name as display_name, wanted_user.user_id as user_id, wanted_user.private as private, " +
                "wanted_user.profile_desc as profile_desc, wanted_user.profile_name as profile_name, " +
                "followers, following, posted, EXISTS((me)<-[:FOLLOWS]-(wanted_user)) as following_me, " +
                "EXISTS((me)-[:FOLLOWS]->(wanted_user)) as are_following, EXISTS((me)-[:REQUESTED]->(wanted_user)) as requested_them, " +
                "EXISTS((me)<-[:REQUESTED]-(wanted_user)) as requested_me, collect(u1.display_name) as following_following",
                params)
    }

    val getInfo = Route { request, response ->
        val userID = request.attribute("user") as String
        val displayName = request.queryParamOrDefault("display_name", "").toLowerCase()

        if (displayName.isBlank()) {
            return@Route JSONObject().fail(message = "Incorrect parameters")
        }

        Database.executeTransaction {
            val json = JSONObject().success()
            val query = getInfoQuery(userID, displayName)
            val results = it.execute(query.first, query.second)

            Database.processResult(results) {
                json.put("display_name", it["display_name"] as String)
                json.put("number_posts", it["posted"] as Long)
                json.put("private",it["private"] as Boolean)
                json.put("followers", it["followers"] as Long)
                json.put("following", it["following"] as Long)

                val areFollowing = it["are_following"] as Boolean
                val followingYou = it["following_me"] as Boolean
                val requestedThem = it["requested_them"] as Boolean
                val requestedMe = it["requested_me"] as Boolean

                json.put("follow_status_to_them", when {
                    areFollowing -> 1
                    requestedThem -> 2
                    else -> 0
                })
                json.put("follow_status_to_me", when {
                    followingYou -> 1
                    requestedMe -> 2
                    else -> 0
                })

                val themID = it["user_id"] as String
                json.put("profile_name", it["profile_name"] as String)
                json.put("profile_desc", it["profile_desc"] as String)
                json.put("profile_image", Utils.constructPhotoUrl("profile", themID))
                val following = it["following_following"] as ArrayList<String?>
                val jsonArray = JSONArray()
                following.forEach {
                    if(it!=null){
                        jsonArray.put(it)
                    }
                }
                json.put("following_who_follow", jsonArray)
            }

            json

        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")

    }


    val getPhotos = Route { request, response ->
        val displayName = request.queryParamOrDefault("display_name", "").toLowerCase()
        val sort = request.queryParamOrDefault("sort", "").toLowerCase()
        val latitude = request.queryParamOrDefault("latitude", "a").toDoubleOrNull()
        val longitude = request.queryParamOrDefault("longitude", "a").toDoubleOrNull()
        val lastPhotoFetchedID = request.queryParamOrDefault("last_photo_fetched", "")
        val userID = request.attribute("user") as String

        val lastPhotoFetched = if (lastPhotoFetchedID.isNotBlank()) {
            Database.executeTransaction {
                it.findNode(Label { "Photo" }, "photo_id", lastPhotoFetchedID)
            } as Node?
        } else null

        if(lastPhotoFetchedID.isNotBlank() && lastPhotoFetched == null){
            return@Route JSONObject().fail(message = "Invalid last photo")
        }

        if (displayName.isBlank()) {
            return@Route JSONObject().fail(message = "Missing parameters")
        }

        if (sort !in FeedRoutes.sortOptions) {
            return@Route JSONObject().fail(message = "Invalid sort")
        }
        if (sort == "location" && (latitude == null || longitude == null)) {
            return@Route JSONObject().fail(message = "Missing latitude or longitude")
        }

        var isFollowing = false
        var hasRequested = false
        var isPrivate = false

        val privacy = Database.privacyCheck(userID, displayName)
        if(privacy!=null && privacy.isNotEmpty()){
            isFollowing = privacy["isFollowing"] as Boolean
            hasRequested = privacy["hasRequested"] as Boolean
            isPrivate = privacy["isPrivate"] as Boolean
        } else {
            return@Route JSONObject().fail(message = "User does not exist")
        }

        if(isPrivate && !isFollowing){
            return@Route JSONObject().fail(code = Errors.PRIVACY, message = "User is private")
        }

        // If sorting by date with no previous photo

        Database.executeTransaction("Fetch User Photos") {

            var results: Result? = null

            when {
                sort == "date" && lastPhotoFetched == null -> {
                    val query = FeedRoutes.timeQuery(false, userID, isFeed = false, targetDisplayName = displayName)
                    results = it.execute(query.first, query.second)
                }
                sort == "date" && lastPhotoFetched != null -> {
                    val query = FeedRoutes.timeQuery(true, userID, isFeed = false, targetDisplayName = displayName, timestamp = lastPhotoFetched.getProperty("timestamp") as Long)
                    results = it.execute(query.first, query.second)
                }
                sort == "location" && lastPhotoFetched == null -> {
                    val query = FeedRoutes.distanceQuery(false, userID, isFeed = false, targetDisplayName = displayName, myLat = latitude!!, myLong = longitude!!)
                    results = it.execute(query.first, query.second)
                }
                sort == "location" && lastPhotoFetched != null -> {
                    val query = FeedRoutes.distanceQuery(true, userID, isFeed = false, targetDisplayName = displayName, myLat = latitude!!, myLong = longitude!!
                            , prevID = lastPhotoFetchedID,prevLat = lastPhotoFetched.getProperty("latitude") as Double, prevLong = lastPhotoFetched.getProperty("longitude") as Double)
                    results = it.execute(query.first, query.second)
                }
                else -> {

                }
            }
            if (results == null) {
                return@executeTransaction null
            }

            val json = JSONObject().success()
            json.put("display_name", displayName)
            json.put("sort", sort)
            json.put("photos", Database.resultToPhotoArray(results))

            return@executeTransaction json
        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")

    }


    val follow = Route { request, response ->
        val requestJson = JSONObject(request.body())
        val userID = request.attribute("user") as String
        val displayName = requestJson.optString("display_name", "")

        if (displayName.isBlank()) {
            return@Route JSONObject().fail(message = "Incorrect parameters")
        }

        var isFollowing = false
        var hasRequested = false
        var isPrivate = false

        val privacy = Database.privacyCheck(userID, displayName)
        if(privacy!=null){
            isFollowing = privacy["isFollowing"] as Boolean
            hasRequested = privacy["hasRequested"] as Boolean
            isPrivate = privacy["isPrivate"] as Boolean
        }

        Database.executeTransaction {

            val otherUserNode = Database.graphDb.findNode(Label { "User" }, "display_name", displayName)
            val userNode = Database.graphDb.findNode(Label { "User" }, "user_id", userID)

            if (otherUserNode == null || userNode == null) {
                return@executeTransaction JSONObject().fail(message = "User does not exist")
            }

            if (isFollowing) {
                return@executeTransaction JSONObject().success().put("result", 2)
            }
            if (hasRequested) {
                return@executeTransaction JSONObject().success().put("result", 3)
            }
            if (isPrivate) {
                val rel = userNode.createRelationshipTo(otherUserNode, RelationshipType { "REQUESTED" })
                rel.setProperty("timestamp", System.currentTimeMillis())
                return@executeTransaction JSONObject().success().put("result", 1)
            }
            val rel = userNode.createRelationshipTo(otherUserNode, RelationshipType { "FOLLOWS" })
            rel.setProperty("timestamp", System.currentTimeMillis())
            return@executeTransaction JSONObject().success().put("result", 0)

        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")

    }

    fun unfollowQuery(userID: String, targetDisplayName: String): Pair<String, HashMap<String, Any>> {
        val params = hashMapOf<String, Any>()
        params.put("user_id", userID)
        params.put("target_display_name", targetDisplayName)
        return Pair("MATCH (:User{user_id:\$user_id})-[f:FOLLOWS|:REQUESTED]->(:User{display_name:\$target_display_name})\n" +
                "Delete f", params)
    }

    val unfollow = Route { request, response ->
        val requestJson = JSONObject(request.body())
        val userID = request.attribute("user") as String
        val displayName = requestJson.optString("display_name", "")

        if (displayName.isBlank()) {
            return@Route JSONObject().fail(message = "Incorrect parameters")
        }

        Database.executeTransaction {
            val query = unfollowQuery(userID, displayName)
            it.execute(query.first, query.second)
            return@executeTransaction JSONObject().success()
        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")

    }

    fun requestsQuery(userID: String): Pair<String, HashMap<String, Any>> {
        val params = hashMapOf<String, Any>()
        params.put("user_id", userID)
        return Pair("match (me:User{user_id:\$user_id})<-[r:REQUESTED]-(u1:User)\n" +
                "return u1.display_name as display_name, u1.profile_name as profile_name, u1.user_id as user_id, " +
                "EXISTS((me)-[:FOLLOWS]->(u1)) as are_following, r.timestamp as timestamp", params)
    }

    val requests = Route { request, response ->
        val userID = request.attribute("user") as String

        // If sorting by date with no previous photo
        Database.executeTransaction("Get Requests") {

            val query = requestsQuery(userID)
            var results = it.execute(query.first, query.second)

            val array = JSONArray()
            Database.processResult(results){
                val requestObject = JSONObject()
                requestObject.put("timestamp", it["timestamp"] as Long)
                requestObject.put("display_name", it["display_name"] as String)
                requestObject.put("profile_image", Utils.constructPhotoUrl("profile",it["user_id"] as String))
                requestObject.put("profile_name", it["profile_name"] as String)
                requestObject.put("are_following", it["are_following"] as Boolean)
            }

            return@executeTransaction JSONObject().success().put("requests", array)
        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")

    }

    fun approveQuery(userID: String, targetDisplayName: String, timestamp: Long): Pair<String, HashMap<String, Any>> {
        val params = hashMapOf<String, Any>()
        params.put("user_id", userID)
        params.put("target_display_name", targetDisplayName)
        params.put("timestamp", timestamp)
        return Pair("match (u:User{display_name:\$target_display_name})-[r:REQUESTED]->(u1:User{user_id:\$user_id})\n" +
                "delete r\n" +
                "create (u)-[f:FOLLOWS{timestamp:\$timestamp}]->(u1)", params)
    }

    val approve = Route { request, response ->
        val requestJson = JSONObject(request.body())
        val userID = request.attribute("user") as String
        val displayName = requestJson.optString("display_name", "")

        if (displayName.isBlank()) {
            return@Route JSONObject().fail(message = "Incorrect parameters")
        }

        Database.executeTransaction {
            val timestamp = System.currentTimeMillis()
            val query = approveQuery(userID, displayName, timestamp)
            it.execute(query.first, query.second)
            return@executeTransaction JSONObject().success()
        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")

    }

    fun getFollowersQuery(paging: Boolean, userID: String, targetDisplayName: String, lastFollowerName: String = "", reverse: Boolean = false): Pair<String, HashMap<String, Any>> {
        val params = hashMapOf<String, Any>()
        params.put("user_id", userID)
        params.put("target_display_name", targetDisplayName)
        params.put("last_follower_name", lastFollowerName)
        return Pair("with \$user_id as my_id" + (if (paging) ", \$last_follower_name as last_follower_name" else "") + "\n" +
                "match (me:User{user_id:my_id})\n" +
                (if (paging) "match (last_follower:User{display_name:last_follower_name})\n" else "") +
                "match (u1:User{display_name:\$target_display_name})${if (!reverse) "<-[f:FOLLOWS]-" else "-[f:FOLLOWS]->"}(u)\n" +
                (if (paging) "match (last_follower)${if (!reverse) "-[f1:FOLLOWS]->" else "<-[f1:FOLLOWS]-"}(u1)\n" +
                        "where f.timestamp>f1.timestamp\n" else "") +
                "return u.display_name as display_name, u.profile_name as profile_name, " +
                "u.user_id as user_id,EXISTS((me)-[:FOLLOWS]->(u)) as are_following,  " +
                "f.timestamp as timestamp order by f.timestamp asc limit 30", params)
    }

    fun getFollows(reverse: Boolean): Route {
        return Route { request, response ->
            val userID = request.attribute("user") as String
            val displayName = request.queryParamOrDefault("display_name", "")
            val lastFollowerFetched = request.queryParamOrDefault(if (!reverse) "last_follower_fetched" else "last_following_fetched", "")

            if (displayName.isBlank()) {
                return@Route JSONObject().fail(message = "Incorrect parameters")
            }

            var isFollowing = false
            var hasRequested = false
            var isPrivate = false
            var otherID = ""

            val privacy = Database.privacyCheck(userID, displayName)
            if(privacy!=null){
                isFollowing = privacy["isFollowing"] as Boolean
                hasRequested = privacy["hasRequested"] as Boolean
                isPrivate = privacy["isPrivate"] as Boolean
                otherID = privacy["otherID"] as String
            }else {
                return@Route JSONObject().fail(message = "User does not exist")
            }

            Database.executeTransaction {

                if (userID != otherID && !isFollowing && isPrivate) {
                    return@executeTransaction JSONObject().fail(code = Errors.PRIVACY, message = "User is private")
                }

                val lastFollower = it.findNode({ "User" }, "display_name", lastFollowerFetched)
                if (lastFollowerFetched.isNotBlank() && lastFollower == null) {
                    return@executeTransaction JSONObject().fail(message = "Invalid last follower")
                }

                val query = getFollowersQuery(lastFollower != null, userID, displayName, (lastFollower?.getProperty("display_name") as String?)
                        ?: "", reverse = reverse)
                val array = Database.resultToProfileArray(it.execute(query.first, query.second))

                return@executeTransaction JSONObject().success().put(if (!reverse) "followers" else "following", array)

            } as JSONObject? ?: JSONObject().fail(message = "DB Failure")

        }
    }

    fun getFollowingWhoFollowQuery(paging: Boolean, userID: String, targetDisplayName: String, lastFollowerName: String = ""): Pair<String, HashMap<String, Any>> {
        val params = hashMapOf<String, Any>()
        params.put("user_id", userID)
        params.put("target_display_name", targetDisplayName)
        params.put("last_follower_name", lastFollowerName)
        return Pair("with \$user_id as my_id" + (if (paging) ", \$last_follower_name as last_follower_name" else "") + "\n" +
                "match (me:User{user_id:my_id})\n" +
                (if (paging) "match (last_follower:User{display_name:last_follower_name})\n" else "") +
                "match (u1:User{display_name:\$target_display_name})<-[f:FOLLOWS]-(u)\n" +
                (if (paging) "match (last_follower)-[f1:FOLLOWS]->(u1)\n" +
                        "where f.timestamp>f1.timestamp and (me)-[:FOLLOWS]->(u)\n" else "") +
                "return u.display_name as display_name, u.profile_name as profile_name, " +
                "u.user_id as user_id, " +
                "f.timestamp as timestamp order by f.timestamp asc limit 30", params)
    }

    fun getFollowingWhoFollow(): Route {
        return Route { request, response ->
            val userID = request.attribute("user") as String
            val displayName = request.queryParamOrDefault("display_name", "")
            val lastFollowerFetched = request.queryParamOrDefault("last_fetched", "")

            if (displayName.isBlank()) {
                return@Route JSONObject().fail(message = "Incorrect parameters")
            }

            var isFollowing = false
            var hasRequested = false
            var isPrivate = false
            var otherID = ""

            val privacy = Database.privacyCheck(userID, displayName)
            if(privacy!=null){
                isFollowing = privacy["isFollowing"] as Boolean
                hasRequested = privacy["hasRequested"] as Boolean
                isPrivate = privacy["isPrivate"] as Boolean
                otherID = privacy["otherID"] as String
            }else {
                return@Route JSONObject().fail(message = "User does not exist")
            }

            Database.executeTransaction {

                val lastFollower = it.findNode({ "User" }, "display_name", lastFollowerFetched)
                if (lastFollowerFetched.isNotBlank() && lastFollower == null) {
                    return@executeTransaction JSONObject().fail(message = "Invalid last fetched")
                }

                val query = getFollowingWhoFollowQuery(lastFollower != null, userID, displayName,
                        (lastFollower?.getProperty("display_name") as String?) ?: "")
                val array = Database.resultToProfileArray(it.execute(query.first, query.second))

                return@executeTransaction JSONObject().success().put("users", array)

            } as JSONObject? ?: JSONObject().fail(message = "DB Failure")

        }
    }


    val getDetails = Route { request, response ->
        val userID = request.attribute("user") as String

        Database.executeTransaction {
            val json = JSONObject().success()
            val userNode = it.findNode({ "User" }, "user_id", userID)
            Database.userNodeToJson(userID, userNode, json)

            json
        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")

    }

    fun approveAllRequestsQuery(userID: String, timestamp:Long): Pair<String, HashMap<String, Any>> {
        val params = hashMapOf<String, Any>()
        params.put("user_id", userID)
        params.put("timestamp", timestamp)
        return Pair("match (u:User{user_id:\$user_id})<-[r:REQUESTED]-(u1)\n" +
                "delete r\n" +
                "create (u)<-[:FOLLOWS{timestamp:\$timestamp}]-(u1)", params)
    }


    val updateDetails = Route { request, response ->
        request.attribute("org.eclipse.jetty.multipartConfig", MultipartConfigElement(""))
        val userID = request.attribute("user") as String

        val requestJson = JSONObject(request.raw().getPart("json")?.inputStream?.bufferedReader().use { it?.readText() }
                ?: "{}")

        Database.executeTransaction {
            val userNode = it.findNode({ "User" }, "user_id", userID)

            val json = JSONObject().success()
            val toCommit = HashMap<String, Any>()

            if (requestJson.has("password")) {
                val password = requestJson.getString("password")
                if (password.isBlank()) {
                    return@executeTransaction JSONObject().fail(message = "Password cannot be blank")
                }
                if(password.length<8){
                    return@executeTransaction JSONObject().fail(message = "Password must be at least 8 characters")
                }
                // check old password
                val old = requestJson.optString("old_password","")
                if(!Utils.Password.verifyPassword(userNode.getProperty("password_hash") as String, old)){
                    return@executeTransaction JSONObject().fail(message = "Old password does not match")
                }
                toCommit.put("password_hash", Utils.Password.hashPassword(password))
            }
            if (requestJson.has("email")) {
                val email = requestJson.getString("email")
                if (email.isBlank()) {
                    return@executeTransaction JSONObject().fail(message = "Email cannot be blank")
                }
                val u = it.findNode({ "User" }, "email", email)
                if (u != null && (u.getProperty("user_id") != userID)) {
                    return@executeTransaction JSONObject().fail(message = "Email already in use")
                }
                toCommit.put("email", email)
            }
            if (requestJson.has("first_name")) {
                val firstName = requestJson.getString("first_name")
                toCommit.put("first_name", firstName)
            }
            if (requestJson.has("last_name")) {
                val lastName = requestJson.getString("last_name")
                toCommit.put("last_name", lastName)
            }
            if (requestJson.has("display_name")) {
                val displayName = requestJson.getString("display_name")
                if (displayName.isBlank()) {
                    return@executeTransaction JSONObject().fail(message = "Display name cannot be blank")
                }
                val u = it.findNode({ "User" }, "display_name", displayName)
                if (u != null && (u.getProperty("user_id") != userID)) {
                    return@executeTransaction JSONObject().fail(message = "Display name already in use")
                }
                toCommit.put("display_name", displayName)
            }
            if (requestJson.has("profile_name")) {
                val profileName = requestJson.getString("profile_name")
                toCommit.put("profile_name", profileName)
            }
            if (requestJson.has("profile_desc")) {
                val profileDesc = requestJson.getString("profile_desc")
                toCommit.put("profile_desc", profileDesc)
            }
            if (requestJson.has("is_private")) {
                val isPrivate = requestJson.getBoolean("is_private")
                toCommit.put("private", isPrivate)
            }
            val wasPrivate = userNode.getProperty("private") as Boolean

            toCommit.forEach { key, value ->
                userNode.setProperty(key, value)
            }

            if (toCommit["private"] == false && wasPrivate) {
                // need to approve all current requests
                val query = approveAllRequestsQuery(userID, System.currentTimeMillis())
                it.execute(query.first, query.second)
            }

            val filepart = request.raw().getPart("profile_image")
            if (filepart != null) {
                launch {
                    Utils.handleImage(userID, filepart.inputStream, true)
                }
            }

            Database.userNodeToJson(userID, userNode, json)

            json
        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")

    }

    val validate = Route { request, response ->
        val userID = request.attribute("user") as String
        Database.executeTransaction {
            val user = it.findNode({"User"}, "user_id", userID)
            if(user==null){
                return@executeTransaction JSONObject().fail(code = Errors.JWT_NOT_VALID, message = "User JWT not valid")
            }else {
                return@executeTransaction  JSONObject().success()
            }
        } as JSONObject? ?: JSONObject().fail(message = "DB Fail")
    }


}