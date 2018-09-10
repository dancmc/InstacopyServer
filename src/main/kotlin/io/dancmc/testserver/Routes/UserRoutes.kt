package io.dancmc.testserver.Routes

import io.dancmc.testserver.Data.Database
import io.dancmc.testserver.Data.User
import io.dancmc.testserver.Utils
import io.dancmc.testserver.fail
import io.dancmc.testserver.success
import org.json.JSONObject
import org.neo4j.graphdb.*
import spark.Route
import java.util.*

object UserRoutes {
    val register = Route { request, response ->
        val requestJson = JSONObject(request.body())
        val username = requestJson.optString("username", "")
        val password = requestJson.optString("password", "")
        val firstName = requestJson.optString("first_name", "")
        val lastName = requestJson.optString("last_name", "")
        val email = requestJson.optString("email", "")
        val displayName = requestJson.optString("display_name", "")
        if (username.isBlank() || password.isBlank() || email.isBlank() || lastName.isBlank() || displayName.isBlank()) {
            return@Route JSONObject().fail(-1, "Missing field")
        }

        val user = User(userID = UUID.randomUUID().toString(), username = username, passwordHash = Utils.Password.hashPassword(password),
                email = email, emailVerified = false, firstName = firstName, lastName = lastName, active = true,
                isBot = false, displayName = displayName, profileName = "", profileDesc = "", isPrivate = false)

        val result = Database.addUser(user)
        if (result.first) {
            val jwt = Utils.Token.createAppToken(result.second)
            JSONObject().success().put("jwt", jwt)
        } else {
            JSONObject().fail(message = result.second)
        }
    }

    val login = Route { request, response ->
        val requestJson = JSONObject(request.body())
        val username = requestJson.optString("username", "")
        val password = requestJson.optString("password", "")

        val user = Database.getUser(username = username)
        if (user == null) {
            JSONObject().fail(message = "User not found")
        } else {
            if (Utils.Password.verifyPassword(user.passwordHash, password)) {
                JSONObject().success().put("jwt", Utils.Token.createAppToken(user.userID))
            } else {
                JSONObject().fail(message = "Wrong password")
            }
        }
    }

    fun getInfoQuery(myUserID:String, otherDisplayName:String):Pair<String, HashMap<String, Any>>{
        val params = hashMapOf<String, Any>()
        params.put("my_user_id", myUserID)
        params.put("wanted_display_name", otherDisplayName)

        return Pair("with \$wanted_display_name as wanted_display_name, \$my_user_id as my_user_id\n" +
                "MATCH (u:User{display_name:wanted_display_name})\n" +
                "with u as wanted_user, my_user_id\n" +
                "MATCH ()-[f:FOLLOWS]->(wanted_user) \n" +
                "with wanted_user, count(f) as followers, my_user_id\n" +
                "MATCH ()<-[f:FOLLOWS]-(wanted_user) \n" +
                "with wanted_user, followers, count(f) as following, my_user_id\n" +
                "MATCH ()<-[p:POSTED]-(wanted_user) \n" +
                "with wanted_user, followers, following, my_user_id, count(p) as posted\n" +
                "MATCH (me:User{user_id:my_user_id})\n" +
                "return wanted_user, followers, following, posted, EXISTS((me)<-[:FOLLOWS]-(wanted_user)) as following_me, EXISTS((me)-[:FOLLOWS]->(wanted_user)) as are_following, EXISTS((me)-[:REQUESTED]->(wanted_user)) as requested_them, EXISTS((me)<-[:REQUESTED]-(wanted_user)) as requested_me",
                params)
    }

    val getInfo = Route { request, response ->
        val userID = request.attribute("user") as String
        val displayName = request.queryParamOrDefault("display_name", "")

        if (displayName.isBlank()) {
            return@Route JSONObject().fail(message = "Incorrect parameters")
        }

        Database.executeTransaction {
            val json = JSONObject().success()
            val query = getInfoQuery(userID, displayName)
            val results = it.execute(query.first, query.second)

            Database.processResult(results){
                val themNode = it["wanted_user"] as Node
                json.put("display_name", themNode.getProperty("display_name") as String)
                json.put("number_posts", it["posted"] as Long)
                json.put("private", themNode.getProperty("private") as Boolean)
                json.put("followers", it["followers"] as Long)
                json.put("following", it["following"] as Long)

                val areFollowing = it["are_following"] as Boolean
                val followingYou = it["following_me"] as Boolean
                val requestedThem = it["requested_them"] as Boolean
                val requestedMe = it["requested_me"] as Boolean

                json.put("follow_status_to_them", when{
                    areFollowing->1
                    requestedThem->2
                    else ->0
                })
                json.put("follow_status_to_me", when{
                    followingYou->1
                    requestedMe->2
                    else ->0
                })

                val themID = themNode.getProperty("user_id") as String
                json.put("profile_name", themNode.getProperty("profile_name") as String)
                json.put("profile_desc", themNode.getProperty("profile_desc") as String)
                json.put("profile_image", Utils.constructPhotoUrl("profile", themID))
            }

            json

        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")

    }


    val getPhotos = Route { request, response ->
        val displayName = request.queryParamOrDefault("display_name", "").toLowerCase()
        val sort = request.queryParamOrDefault("sort", "").toLowerCase()
        val latitude = request.queryParamOrDefault("latitude", "-1").toDoubleOrNull()
        val longitude = request.queryParamOrDefault("longitude", "-1").toDoubleOrNull()
        val lastPhotoFetchedID = request.queryParamOrDefault("last_photo_fetched", "")
        val userID = request.attribute("user") as String

        val lastPhotoFetched = if (lastPhotoFetchedID.isNotBlank()) {
            Database.executeTransaction {
                it.findNode(Label { "Photo" }, "photo_id", lastPhotoFetchedID)
            } as Node?
        } else null

        if(displayName.isBlank()){
            return@Route JSONObject().fail(message = "Missing parameters")
        }

        if (sort !in FeedRoutes.sortOptions) {
            return@Route JSONObject().fail(message = "Invalid sort")
        }
        if (sort == "location" && (latitude == null || longitude == null)) {
            return@Route JSONObject().fail(message = "Missing latitude or longitude")
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
                    val query = FeedRoutes.distanceQuery(false, userID, isFeed = false, targetDisplayName = displayName,myLat = latitude!!, myLong = longitude!!)
                    results = it.execute(query.first, query.second)
                }
                sort == "location" && lastPhotoFetched != null -> {
                    val query = FeedRoutes.distanceQuery(true, userID, isFeed = false, targetDisplayName = displayName,myLat = latitude!!, myLong = longitude!!
                            , prevLat = lastPhotoFetched.getProperty("latitude") as Double, prevLong = lastPhotoFetched.getProperty("longitude") as Double)
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

    fun followQuery(userID:String, targetDisplayName:String):Pair<String, HashMap<String, Any>>{
        val params = hashMapOf<String, Any>()
        params.put("user_id", userID)
        params.put("target_display_name", targetDisplayName)
        return  Pair("MATCH (u1:User{user_id:\$user_id})\n" +
                "with u1\n" +
                "MATCH (u2:User{display_name:\$target_display_name})\n" +
                "with u1, u2\n" +
                "return EXISTS((u1)-[:FOLLOWS]->(u2)) as is_following, EXISTS((u1)-[:REQUESTED]->(u2)) as has_requested, u2.private as is_private", params)
    }

    val follow = Route { request, response ->
        val requestJson = JSONObject(request.body())
        val userID = request.attribute("user") as String
        val displayName = requestJson.optString("display_name", "")

        if (displayName.isBlank()) {
            return@Route JSONObject().fail(message = "Incorrect parameters")
        }

        Database.executeTransaction {

            val otherUserNode = Database.graphDb.findNode(Label { "User" }, "display_name", displayName)
            val userNode = Database.graphDb.findNode(Label { "User" }, "user_id", userID)

            if (otherUserNode == null || userNode==null){
                return@executeTransaction JSONObject().fail(message = "User does not exist")
            }

            val followQuery = followQuery(userID, displayName)
            val results = it.execute(followQuery.first, followQuery.second)
            var isFollowing=false
            var hasRequested=false
            var isPrivate=false
            Database.processResult(results){
                isFollowing = it["is_following"] as Boolean
                hasRequested = it["has_requested"] as Boolean
                isPrivate = it["is_private"] as Boolean
            }

            if(isFollowing){
                return@executeTransaction JSONObject().success().put("result", 2)
            }
            if(hasRequested){
                return@executeTransaction JSONObject().success().put("result", 3)
            }
            if(isPrivate){
                val rel = userNode.createRelationshipTo(otherUserNode, RelationshipType { "REQUESTED" })
                rel.setProperty("timestamp", System.currentTimeMillis())
                return@executeTransaction JSONObject().success().put("result", 1)
            }
            val rel = userNode.createRelationshipTo(otherUserNode, RelationshipType { "FOLLOWS" })
            rel.setProperty("timestamp", System.currentTimeMillis())
            return@executeTransaction JSONObject().success().put("result", 0)

        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")

    }

    fun unfollowQuery(userID:String, targetDisplayName:String):Pair<String, HashMap<String, Any>>{
        val params = hashMapOf<String, Any>()
        params.put("user_id", userID)
        params.put("target_display_name", targetDisplayName)
        return  Pair("MATCH (:User{user_id:\$user_id})-[f:FOLLOWS|:REQUESTED]->(:User{display_name:\$target_display_name})\n" +
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

    fun approveQuery(userID:String, targetDisplayName:String, timestamp:Long):Pair<String, HashMap<String, Any>>{
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
            val query = approveQuery(userID, displayName,timestamp)
            it.execute(query.first, query.second)
            return@executeTransaction JSONObject().success()
        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")


    }

    val getFollowers = Route { request, response ->


    }

    val getFollowing = Route { request, response ->


    }

    val updateDetails = Route { request, response ->


    }
}