package io.dancmc.instacopy.Routes

import io.dancmc.instacopy.Data.Database
import io.dancmc.instacopy.Utils
import io.dancmc.instacopy.fail
import io.dancmc.instacopy.random
import io.dancmc.instacopy.success
import org.json.JSONArray
import org.json.JSONObject
import spark.Route
import java.util.*
import kotlin.collections.ArrayList

object DiscoverRoutes {

    fun searchUsersQuery(paging: Boolean, displayName: String, page: Int = 1): Pair<String, HashMap<String, Any>> {
        val params = hashMapOf<String, Any>()
        params.put("display_name", displayName)
        params.put("skip", (page - 1) * 20)
        return Pair("match (u:User)  with u,  apoc.text.jaroWinklerDistance(u.display_name,\$display_name) as distance " +
                "return u.display_name as display_name, u.user_id as user_id, u.profile_name as profile_name " +
                "order by distance desc, display_name asc ${if (paging) "skip \$skip" else ""} limit 20", params)
    }

    val search = Route { request, response ->
        val userID = request.attribute("user") as String
        val displayName = request.queryParamOrDefault("display_name", "")
        val pageNumber = request.queryParamOrDefault("page", "").toIntOrNull()
        val paging = request.queryParams().contains("page")


        if (displayName.isBlank() || paging && (pageNumber == null || pageNumber < 1)) {
            return@Route JSONObject().fail(message = "Incorrect parameters")
        }

        Database.executeTransaction {
            val query = searchUsersQuery(paging, displayName, pageNumber ?: 1)
            val results = it.execute(query.first, query.second)

            val jsonArray = JSONArray()
            Database.processResult(results) {
                val userObject = JSONObject()
                userObject.put("display_name", it["display_name"] as String)
                userObject.put("profile_name", it["profile_name"] as String)
                userObject.put("profile_image", Utils.constructPhotoUrl("profile", it["user_id"] as String))
                jsonArray.put(userObject)
            }
            JSONObject().success().put("users", jsonArray)
        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")

    }

    val hexList = "0123456789abcdef".toMutableList()

    fun suggestUsersQuery(userID: String, followingFollowing: Boolean, skipNumberMax: Int): Pair<String, HashMap<String, Any>> {
        val params = hashMapOf<String, Any>()
        params.put("user_id", userID)
        params.put("skip", (0..skipNumberMax).random())
        params.put("regexp", "[${Utils.randomSublist(hexList, 8).joinToString("")}].*")

        return Pair("match (me:User{user_id:\$user_id}) match (u1) where not (me)-[:FOLLOWS]->(u1) " +
                "${if (followingFollowing) "and (me)-[:FOLLOWS*2]->(u1)" else ""} and  u1.user_id =~ \$regexp and not (u1)=(me) " +
                "return u1.display_name as display_name, u1.user_id as user_id, u1.profile_name as profile_name skip \$skip limit 40"
                , params)

    }

    val startSkipMax = 400
    val secondSkipMax = 20

    data class DiscoverUser(var displayName: String, var userID: String, var profileName: String, var reasonFollowing: Boolean)

    val suggestUsers = Route { request, response ->
        val userID = request.attribute("user") as String

        Database.executeTransaction {
            var query = suggestUsersQuery(userID, true, startSkipMax)
            var results = it.execute(query.first, query.second)

            val followingFollowingList = ArrayList<DiscoverUser>()
            Database.processResult(results) {
                val discoverUser = DiscoverUser(
                        it["display_name"] as String,
                        it["user_id"] as String,
                        it["profile_name"] as String, true)
                followingFollowingList.add(discoverUser)
            }
            if (followingFollowingList.size < 20) {
                followingFollowingList.clear()
                query = suggestUsersQuery(userID, true, secondSkipMax)
                results = it.execute(query.first, query.second)
                Database.processResult(results) {
                    val discoverUser = DiscoverUser(
                            it["display_name"] as String,
                            it["user_id"] as String,
                            it["profile_name"] as String, true)
                    followingFollowingList.add(discoverUser)
                }
            }


            query = suggestUsersQuery(userID, false, startSkipMax)
            results = it.execute(query.first, query.second)

            val randomList = ArrayList<DiscoverUser>()
            Database.processResult(results) {
                val discoverUser = DiscoverUser(
                        it["display_name"] as String,
                        it["user_id"] as String,
                        it["profile_name"] as String, false)
                randomList.add(discoverUser)
            }
            if (randomList.size < 20) {
                randomList.clear()
                query = suggestUsersQuery(userID, false, secondSkipMax)
                results = it.execute(query.first, query.second)
                Database.processResult(results) {
                    val discoverUser = DiscoverUser(
                            it["display_name"] as String,
                            it["user_id"] as String,
                            it["profile_name"] as String, false)
                    randomList.add(discoverUser)
                }
            }

            // bias towards following of following in ratio 2:1
            randomList.shuffle()
            randomList.forEachIndexed { index, discoverUser ->
                if (index < 20) {
                    followingFollowingList.add(discoverUser)
                }
            }
            followingFollowingList.shuffle()

            val jsonArray = JSONArray()
            followingFollowingList.forEachIndexed { index, discoverUser ->
                if (index < 20) {
                    val userObject = JSONObject()
                    userObject.put("display_name", discoverUser.displayName)
                    userObject.put("profile_name", discoverUser.profileName)
                    userObject.put("profile_image", Utils.constructPhotoUrl("profile", discoverUser.userID))
                    userObject.put("reason", if (discoverUser.reasonFollowing) "Based on people you follow" else "")
                    jsonArray.put(userObject)
                }
            }


            JSONObject().success().put("users", jsonArray)
        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")


    }

    fun suggestPhotosQuery(userID: String, followingFollowing: Boolean, regexp: String, page: Int): Pair<String, HashMap<String, Any>> {
        val params = hashMapOf<String, Any>()
        params.put("user_id", userID)
        params.put("skip", (page - 1) * 20)
        params.put("regexp", "[$regexp].*")

        return Pair("match (me:User{user_id:\$user_id}) \n" +
                "match (u1)-[:POSTED]->(p) where not (me)-[:FOLLOWS]->(u1) and not u1.private ${if (followingFollowing) "and (me)-[:FOLLOWS*2]->(u1)" else ""} " +
                "and  p.photo_id =~ \$regexp and not (u1)=(me)\n" +
                "return p.photo_id as photo_id skip \$skip limit 40", params)
    }

    val suggestPhotos = Route { request, response ->
        val userID = request.attribute("user") as String
        val seed = request.queryParamOrDefault("seed", "")
        val hasSeed = request.queryParams().contains("seed")

        // choose initial 4 hex letters first if none returned
        var seedReg = if (hasSeed) seed.split("_")[0] else Utils.randomSublist(hexList, 4).joinToString("")
        var seedPrevPage = if (hasSeed) seed.split("_")[1].toInt() else 0

        if (hasSeed && seed.isBlank()) {
            return@Route JSONObject().fail(message = "Incorrect parameters")
        }

        // first try following following with most recent 4 hex letters & take 40 at specified paging
        // if less than 40 returned, time to move on
        // select next 4 hex letters and try, then append these 4 letters to seedReg
        Database.executeTransaction {
            var regexp = seedReg.takeLast(4)
            var query = suggestPhotosQuery(userID, true, regexp, seedPrevPage + 1)
            var results = it.execute(query.first, query.second)

            var followFollowingList = LinkedList<String>()
            Database.processResult(results) {
                followFollowingList.add(it["photo_id"] as String)
            }
            if (followFollowingList.size < 40) {
                followFollowingList = LinkedList()
                if (seedReg.length < 16) {
                    // if prev regexp ran out of photos, get next regexp and reset page number
                    seedPrevPage = 0
                    val remaining = ArrayList(hexList)
                    remaining.removeAll(seedReg.toList())
                    remaining.shuffle()
                    regexp = remaining.takeLast(4).joinToString("")
                    seedReg += regexp

                    query = suggestPhotosQuery(userID, true, regexp, seedPrevPage + 1)
                    results = it.execute(query.first, query.second)

                    Database.processResult(results) {
                        followFollowingList.add(it["photo_id"] as String)
                    }

                }
            }

            query = suggestPhotosQuery(userID, false, regexp, seedPrevPage + 1)
            results = it.execute(query.first, query.second)

            val randomList = LinkedList<String>()
            Database.processResult(results) {
                randomList.add(it["photo_id"] as String)
            }

            val finalList = ArrayList<String>()
            val random = Random()
            while(finalList.size<60 && followFollowingList.size+randomList.size>0){
                if(random.nextDouble()<0.66){
                    if(followFollowingList.size>0){
                        finalList.add(followFollowingList.pop())
                    }
                }else {
                    if(randomList.size>0){
                        finalList.add(randomList.pop())
                    }
                }
            }

            val json = JSONObject().success()
            val jsonArray = JSONArray()
            finalList.forEach {
                val photoObject = JSONObject()
                jsonArray.put(photoObject.put("photo_id", it).put("url", JSONObject().put("small", Utils.constructPhotoUrl("small", it))))
            }

            seedPrevPage++
            val newSeed = "${seedReg}_$seedPrevPage"

            json.put("seed", newSeed).put("photos", jsonArray)
        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")
    }

}
