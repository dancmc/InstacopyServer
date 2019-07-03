package io.dancmc.instacopy.Routes

import io.dancmc.instacopy.Data.Database
import io.dancmc.instacopy.Data.Photo
import io.dancmc.instacopy.Utils
import io.dancmc.instacopy.fail
import io.dancmc.instacopy.success
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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

        val requestJson = JSONObject(request.raw().getPart("json")?.inputStream?.bufferedReader().use { it?.readText() } ?: "{}")
        val caption = requestJson.optString("caption","")
        val latitude = requestJson.optDouble("latitude",999.0)
        val longitude = requestJson.optDouble("longitude",999.0)
        val locationName = requestJson.optString("location_name","")
        val userID = request.attribute("user") as String

        if(((latitude>90.0 || latitude<-90.0)&& latitude!=999.0)||((longitude>180.0 || longitude<-180.0)&& longitude!=999.0)){
            return@Route JSONObject().fail(message = "Invalid latitude or longitude")
        }

        var photoID = UUID.randomUUID().toString()

        Database.executeTransaction("Upload Photos") {
            var node = it.findNode( { "Photo" }, "photo_id", photoID)
            while (node != null) {
                photoID = UUID.randomUUID().toString()
                node = it.findNode( { "Photo" }, "photo_id", photoID)
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
        GlobalScope.launch {
            val dimensions = Utils.handleImage(photoID, filepart.inputStream, false)
            Database.executeTransaction("Upload Photos") {
                var node = it.findNode( { "Photo" }, "photo_id", photoID)
                dimensions.forEach { size, dimensions ->
                    node.setProperty("${size}_width",dimensions.first)
                    node.setProperty("${size}_height",dimensions.second)
                }

            }
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
        photoIDArray.forEach {
            photoIDList.add(it as String)
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
            json.put("comments", array)
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

            if(photoNode==null){
                return@executeTransaction JSONObject().fail(message = "Photo not found")
            }

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
            val photoNode = Database.graphDb.findNode(Label { "Photo" }, "photo_id", photoID)

            if(photoNode==null){
                return@executeTransaction JSONObject().fail(message = "Photo not found")
            }

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

    fun getLikesQuery(userID: String, photoID: String, recent: Boolean = false, last_fetched: String = ""): Pair<String, HashMap<String, Any>> {
        val params = hashMapOf<String, Any>()
        params.put("user_id", userID)
        params.put("photo_id", photoID)
        params.put("last_fetched", last_fetched)

        val recentQuery = "with \$photo_id as photo_id, \$user_id as user_id\n" +
                "MATCH (u:User)-[l:LIKES]->(p:Photo{photo_id:photo_id})\n" +
                "with u, l, user_id\n" +
                "return u.display_name as display_name, u.profile_name as profile_name, u.user_id as user_id,EXISTS((:User{user_id:user_id})-[:FOLLOWS]->(u)) as are_following, " +
                "EXISTS((:User{user_id:user_id})-[:REQUESTED]->(u)) as requested_them," +
                "l.timestamp as timestamp order by l.timestamp desc limit 50"

        val normalQuery = "with \$photo_id as photo_id, \$user_id as user_id, \$last_fetched as last_fetched\n" +
                (if (last_fetched.isNotBlank())"MATCH (u1:User{display_name:last_fetched})-[l1:LIKES]->(p:Photo{photo_id:photo_id})\n" else "") +
                "MATCH (u:User)-[l:LIKES]->(p:Photo{photo_id:photo_id})\n" +
//                "with u, l, user_id, l1\n" +
                (if (last_fetched.isNotBlank()) "WHERE l.timestamp>l1.timestamp\n" else "") +
                "return u.display_name as display_name, u.profile_name as profile_name, u.user_id as user_id,EXISTS((:User{user_id:user_id})-[:FOLLOWS]->(u)) as are_following,  " +
                "EXISTS((:User{user_id:user_id})-[:REQUESTED]->(u)) as requested_them," +
                "l.timestamp as timestamp order by l.timestamp asc limit 30"

        val query = if (recent) recentQuery else normalQuery

        return Pair(query, params)
    }

    val getPhotoLikes = Route { request, response ->
        val userID = request.attribute("user") as String
        val photoID = request.queryParamOrDefault("photo_id", "")
        val lastFetched = request.queryParamOrDefault("last_fetched", "")
        var recent = request.queryParamOrDefault("recent", "").toIntOrNull()

        val json = JSONObject().success()
        Database.executeTransaction {
            if (recent != null) {
                recent = Math.max(recent!!, 0)
            }
            val query = getLikesQuery(userID, photoID, recent != null, lastFetched)
            val results = it.execute(query.first, query.second)

            val unfilteredArray = Database.resultToProfileArray(results)
            val array = JSONArray()

            if (recent != null) {
                // list contains 50 most recent likes in desc order
                // want to
                unfilteredArray.forEachIndexed { index, jsonObject ->
                    if (index < recent!!) {
                        array.put(jsonObject as JSONObject)
                    }
                }
            } else {
                unfilteredArray.forEach {
                    array.put(it as JSONObject)
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

            if(photoNode==null){
                return@executeTransaction JSONObject().fail(message = "Photo not found")
            }

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

            // TODO consider cypher instead
            val photoNode = Database.graphDb.findNode(Label { "Photo" }, "photo_id", photoID)

            if(photoNode==null){
                return@executeTransaction JSONObject().fail(message = "Photo not found")
            }

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