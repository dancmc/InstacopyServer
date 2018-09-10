package io.dancmc.testserver.Routes

import io.dancmc.testserver.Data.Database
import io.dancmc.testserver.Data.Photo
import io.dancmc.testserver.Utils
import io.dancmc.testserver.fail
import io.dancmc.testserver.success
import kotlinx.coroutines.experimental.launch
import org.json.JSONArray
import org.json.JSONObject
import org.neo4j.graphdb.Direction
import org.neo4j.graphdb.Label
import org.neo4j.graphdb.RelationshipType
import spark.Route
import java.util.*
import javax.servlet.MultipartConfigElement

object PhotoRoutes {

    val upload = Route { request, response ->

        request.attribute("org.eclipse.jetty.multipartConfig", MultipartConfigElement(""))
        val caption = request.raw().getPart("caption")?.inputStream?.bufferedReader().use { it?.readText() } ?: ""
        val latitude = request.raw().getPart("latitude")?.inputStream?.bufferedReader().use { it?.readText() }?.toDoubleOrNull()
                ?: -99999.9
        val longitude = request.raw().getPart("longitude")?.inputStream?.bufferedReader().use { it?.readText() }?.toDoubleOrNull()
                ?: -99999.9
        val locationName = request.raw().getPart("location_name")?.inputStream?.bufferedReader().use { it?.readText() }
                ?: ""
        val userID = request.attribute("user") as String

        if (latitude == -99999.9 || longitude == -99999.9) {
            return@Route JSONObject().fail(message = "Incorrect parameters")
        }

        var photoID = UUID.randomUUID().toString()

        // TODO need to resize pics and also save to database
        Database.executeTransaction("Upload Photos") {
            var node = it.findNode(Label { "Photo" }, "photo_id", photoID)
            while (node != null) {
                photoID = UUID.randomUUID().toString()
                node = it.findNode(Label { "Photo" }, "photo_id", photoID)
            }
        }

        val timestamp = System.currentTimeMillis()
        val photo = Photo(photoID, caption,timestamp , latitude, longitude, locationName)
        val dbResult = Database.addPhoto(userID, photo, timestamp)

        if (!dbResult.first) {
            return@Route JSONObject().fail(message = dbResult.second)
        }

        val filepart = request.raw().getPart("photo")
//        filepart.inputStream.use { // getPart needs to use same "name" as input field in form
//            input -> Files.copy(input, temp.toPath(), StandardCopyOption.REPLACE_EXISTING)
//        }
        launch {
            Utils.handleImage(photoID, filepart.inputStream, false)
        }


        val json = JSONObject().success()
        json.put("regular", Utils.constructPhotoUrl("regular", photoID))
        json.put("small", Utils.constructPhotoUrl("small", photoID))
        json.put("thumb", Utils.constructPhotoUrl("thumb", photoID))
        json.put("photo_id", photoID)


        return@Route json
    }


    fun specificPhotoQuery(userID: String, photoIDs: List<String>): Pair<String, HashMap<String, Any>> {
        val params = hashMapOf<String, Any>()
        params.put("user_id", userID)
        params.put("photo_id_list", photoIDs)
        return Pair("with \$user_id as user_id, \$photo_id_list as photo_id_list\n" +
                "MATCH (u1:User{user_id:user_id})\n" +
                "with u1, photo_id_list\n" +
                "MATCH (u2:User)-[:POSTED]->(p:Photo) where p.photo_id in photo_id_list\n" +
                "with u1, u2, p\n" +
                "CALL apoc.cypher.run('optional match (u3)-[l:LIKES]->(p),(u1)-[:FOLLOWS]->(u3)  return u3 limit 2', {p:p,u1:u1}) yield value\n" +
                "with u1, u2,  p, collect(value.u3.display_name) as like_users\n" +
                "CALL apoc.cypher.run('optional match (u4)-[l:COMMENTED]->(p),(u1)-[:FOLLOWS]->(u4) return u4, l.text as text limit 2', {p:p,u1:u1}) yield value\n" +
                "with u1,u2,p,like_users,collect({commenter_name:value.u4.display_name,comment_text:value.text}) as comment_previews\n" +
                "optional match ()-[l:LIKES]->(p) \n" +
                "with u1, u2, p, like_users, comment_previews, count(l) as total_likes\n" +
                "optional match ()-[c:COMMENTED]->(p)\n" +
                "with u1, u2, p, like_users, comment_previews, total_likes, count(c) as total_comments\n" +
                "return p as photo, total_likes, total_comments, Exists((u1)-[:LIKES]->(p)) as is_liked,u2.display_name as poster_name, u2.user_id as poster_id, like_users, comment_previews\n",
                params)
    }

    val getPhotos = Route { request, response ->
        val requestJson = JSONObject(request.body())
        val userID = request.attribute("user") as String
        val photoIDArray = requestJson.getJSONArray("photo_ids")
        val photoIDList = ArrayList<String>()
        for (i in 0 until photoIDArray.length()) {
            photoIDList.add(photoIDArray.getString(i))
        }

        val json = JSONObject().success()
        Database.executeTransaction {
            val query = specificPhotoQuery(userID, photoIDList)
            val results = it.execute(query.first, query.second)
            json.put("photos", Database.resultToPhotoArray(results))
        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")

    }

    fun getCommentsQuery(paging: Boolean, photoID: String, lastPhotoID: String = ""): Pair<String, HashMap<String, Any>> {
        val params = hashMapOf<String, Any>()
        params.put("photo_id", photoID)
        params.put("last_comment_id", lastPhotoID)
        return Pair("with \$photo_id as photo_id\n" +
                (if (paging) ",\$last_comment_id as last_comment_id\n" +
                        "MATCH ()-[c:COMMENTED{comment_id:last_comment_id}]->(p:Photo{photo_id:photo_id})\n" +
                        "with c.timestamp as last_timestamp, photo_id\n" else "") +
                "MATCH (u:User)-[c:COMMENTED]->(p:Photo{photo_id:photo_id})\n" +
                (if (paging) "where c.timestamp>last_timestamp\n" else "") +
                "return u.display_name as display_name, u.user_id as user_id, c.comment_id as comment_id, c.text as text, c.timestamp as timestamp order by c.timestamp asc limit 30", params)
    }

    val getPhotoComments = Route { request, response ->
        val userID = request.attribute("user") as String
        val photoID = request.queryParamOrDefault("photo_id", "")
        val lastComment = request.queryParamOrDefault("last_comment_fetched", "")

        if (photoID.isBlank()) {
            return@Route JSONObject().fail(message = "No photo ID given")
        }

        val json = JSONObject().success()
        Database.executeTransaction {
            val query = getCommentsQuery(!lastComment.isBlank(), photoID, lastComment)
            val results = it.execute(query.first, query.second)
            val array = JSONArray()
            Database.processResult(results) {
                val commentObject = JSONObject()
                commentObject.put("comment_id", it["comment_id"] as String)
                commentObject.put("display_name", it["display_name"] as String)
                commentObject.put("profile_image", Utils.constructPhotoUrl("profile", it["user_id"] as String))
                commentObject.put("text", it["text"] as String)
                commentObject.put("timestamp", it["timestamp"] as Long)
                array.put(commentObject)
            }
            json.put("photos", array)
        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")


    }


    val postPhotoComment = Route { request, response ->
        val requestJson = JSONObject(request.body())
        val userID = request.attribute("user") as String
        val photoID = requestJson.optString("photo_id", "")
        val text = requestJson.optString("text", "")

        if (photoID.isBlank() || text.isBlank()) {
            return@Route JSONObject().fail(message = "Incorrect parameters")
        }

        Database.executeTransaction {
            val jsonResponse = JSONObject().success()
            val photoNode = Database.graphDb.findNode(Label { "Photo" }, "photo_id", photoID)
            val userNode = Database.graphDb.findNode(Label { "User" }, "user_id", userID)
            val rel = userNode.createRelationshipTo(photoNode, RelationshipType { "COMMENTED" })
            val commentID = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            rel.setProperty("comment_id", commentID)
            rel.setProperty("timestamp", timestamp)
            rel.setProperty("text", text)


            jsonResponse.put("photo_id", photoID)
            jsonResponse.put("comment_id", commentID)
            jsonResponse.put("timestamp", timestamp)
        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")

    }

    val deletePhotoComment = Route { request, response ->
        val requestJson = JSONObject(request.body())
        val userID = request.attribute("user") as String
        val photoID = requestJson.optString("photo_id", "")
        val commentID = requestJson.optString("comment_id", "")

        Database.executeTransaction {
            val jsonResponse = JSONObject().success()
            val photoNode = Database.graphDb.findNode(Label { "Photo" }, "photo_id", photoID)
            val userNode = Database.graphDb.findNode(Label { "User" }, "user_id", userID)

            var deleted = false
            var foundComment = false
            var correctUser = false
            val comments = photoNode.getRelationships(RelationshipType { "COMMENTED" }, Direction.INCOMING)
            comments.iterator().forEach {
                val commentNodeID = it.getProperty("comment_id")
                if (commentNodeID == commentID) {
                    foundComment = true
                    val startNode = it.startNode
                    if ((startNode.getProperty("user_id") as String) == userID) {
                        correctUser = true
                        it.delete()
                        deleted = true
                    }
                }
            }

            if (deleted) {
                return@executeTransaction JSONObject().success()
            } else {
                if (!correctUser) {
                    return@executeTransaction JSONObject().fail(message = "Comment does not belong to user ")
                } else if (!foundComment) {
                    return@executeTransaction JSONObject().fail(message = "Could not find comment")
                } else {
                    return@executeTransaction JSONObject().fail(message = "Delete failed")
                }
            }
        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")

    }

    fun getLikesQuery(userID: String, photoID: String, recent: Boolean = false, lastLikeTimestamp: Long = -1L): Pair<String, HashMap<String, Any>> {
        val params = hashMapOf<String, Any>()
        params.put("user_id", userID)
        params.put("photo_id", photoID)
        params.put("last_like_timestamp", lastLikeTimestamp)

        val recentQuery = "with \$photo_id as photo_id, \$user_id as user_id\n" +
                "MATCH (u:User)-[l:LIKES]->(p:Photo{photo_id:photo_id})\n" +
                "with u, l, user_id\n" +
                "return u.display_name as display_name, u.profile_name as profile_name, u.user_id as user_id,EXISTS((:User{user_id:user_id})-[:FOLLOWS]->(u)) as are_following,  " +
                "l.timestamp as timestamp order by l.timestamp desc limit 50"

        val normalQuery = "with \$photo_id as photo_id, \$user_id as user_id, \$last_like_timestamp as last_like_timestamp\n" +
                "MATCH (u:User)-[l:LIKES]->(p:Photo{photo_id:photo_id})\n" +
                "with u, l, user_id\n" +
                (if (lastLikeTimestamp != -1L) "WHERE l.timestamp>last_like_timestamp\n" else "") +
                "return u.display_name as display_name, u.profile_name as profile_name, u.user_id as user_id,EXISTS((:User{user_id:user_id})-[:FOLLOWS]->(u)) as are_following,  " +
                "l.timestamp as timestamp order by l.timestamp asc limit 30"

        val query = if (recent) recentQuery else normalQuery

        return Pair(query, params)
    }

    val getPhotoLikes = Route { request, response ->
        val userID = request.attribute("user") as String
        val photoID = request.queryParamOrDefault("photo_id", "")
        val lastLikeTime = request.queryParamOrDefault("last_like_timestamp", "").toLongOrNull()
        var recent = request.queryParamOrDefault("recent", "").toIntOrNull()

        val json = JSONObject().success()
        Database.executeTransaction {
            if (recent != null) {
                recent = Math.max(recent!!, 0)
            }
            val query = getLikesQuery(userID, photoID, recent != null, lastLikeTime ?: -1L)
            val results = it.execute(query.first, query.second)
            val array = JSONArray()
            val list = ArrayList<JSONObject>()
            Database.processResult(results) {
                val likeObject = JSONObject()
                list.add(likeObject)
                likeObject.put("profile_image", Utils.constructPhotoUrl("profile", it["user_id"] as String))
                likeObject.put("display_name", it["display_name"] as String)
                likeObject.put("profile_name", it["profile_name"] as String)
                likeObject.put("are_following", it["are_following"] as Boolean)
                likeObject.put("timestamp", it["timestamp"] as Long)
            }

            if (recent != null) {
                // list contains 50 most recent likes in desc order
                // want to
                list.forEachIndexed { index, jsonObject ->
                    if (index < recent!!) {
                        array.put(jsonObject)
                    }
                }
            } else {
                list.forEach {
                    array.put(it)
                }
            }

            json.put("likes", array)
        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")
    }

    val likePhoto = Route { request, response ->
        val requestJson = JSONObject(request.body())
        val userID = request.attribute("user") as String
        val photoID = requestJson.optString("photo_id", "")

        if (photoID.isBlank()) {
            return@Route JSONObject().fail(message = "Incorrect parameters")
        }

        Database.executeTransaction {

            val photoNode = Database.graphDb.findNode(Label { "Photo" }, "photo_id", photoID)
            val userNode = Database.graphDb.findNode(Label { "User" }, "user_id", userID)

            val rels = photoNode.getRelationships(RelationshipType { "LIKES" }, Direction.INCOMING)
            var isLiked = false
            rels.forEach {
                if ((it.startNode.getProperty("user_id") as String) == userID) {
                    isLiked = true
                }

            }
            if(!isLiked) {
                val rel = userNode.createRelationshipTo(photoNode, RelationshipType { "LIKES" })
                rel.setProperty("timestamp", System.currentTimeMillis())
            }

            JSONObject().success()
        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")

    }


    val unlikePhoto = Route { request, response ->
        val requestJson = JSONObject(request.body())
        val userID = request.attribute("user") as String
        val photoID = requestJson.optString("photo_id", "")

        if (photoID.isBlank()) {
            return@Route JSONObject().fail(message = "Incorrect parameters")
        }

        Database.executeTransaction {

            val photoNode = Database.graphDb.findNode(Label { "Photo" }, "photo_id", photoID)
            val userNode = Database.graphDb.findNode(Label { "User" }, "user_id", userID)

            val rels = photoNode.getRelationships(RelationshipType { "LIKES" }, Direction.INCOMING)
            rels.iterator().forEach {
                if ((it.startNode.getProperty("user_id") as String) == userID) {
                    it.delete()
                }
            }

            JSONObject().success()
        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")

    }

}