package io.dancmc.instacopy.Routes

import io.dancmc.instacopy.Data.Database
import io.dancmc.instacopy.Main
import io.dancmc.instacopy.fail
import io.dancmc.instacopy.success
import org.json.JSONObject
import org.neo4j.graphdb.Label
import org.neo4j.graphdb.Result
import spark.Route

object FeedRoutes {

    fun photoListSub(distance: Boolean, paged: Boolean): String {
        return "MATCH (u1:User{user_id:\$user_id})\n" +
                "with u1 ${if (distance) ",me" else ""}${if (distance && paged) ",lastpt, lastp" else ""}\n" +
                "MATCH (u2:User{display_name:\$target_display_name})-[:POSTED]->(p:Photo)\n"
    }

    fun timeQuery(paged: Boolean, userID: String, isFeed: Boolean, targetDisplayName: String = "", timestamp: Long = 0L): Pair<String, HashMap<String, Any>> {
        val params = hashMapOf<String, Any>()
        params.put("user_id", userID)
        params.put("timestamp", timestamp)
        params.put("target_display_name", targetDisplayName)
        return Pair(
                (if (isFeed) "MATCH (u1:User{user_id:\$user_id})-[:FOLLOWS]->(u2), (u2)-[:POSTED]->(p:Photo)\n" else photoListSub(false, paged)) +
                        (if (paged) "where p.timestamp<\$timestamp\n" else "") +
                        "with u1,u2, p" + "\n" +
                        "order by p.timestamp desc limit ${Main.pageLimit}\n" +
                        "CALL apoc.cypher.run('optional match (u3)-[l:LIKES]->(p),(u1)-[:FOLLOWS]->(u3)  return u3 limit 2', {p:p,u1:u1}) yield value\n" +
                        "with u1, u2,  p, collect(value.u3.display_name) as like_users\n" +
                        "CALL apoc.cypher.run('optional match (u4)-[l:COMMENTED]->(p),(u1)-[:FOLLOWS]->(u4) return u4, l.text as text limit 2', {p:p,u1:u1}) yield value\n" +
                        "with u1,u2,p,like_users,collect({commenter_name:value.u4.display_name,comment_text:value.text}) as comment_previews\n" +
                        "optional match ()-[l:LIKES]->(p) \n" +
                        "with u1, u2, p, like_users, comment_previews, count(l) as total_likes\n" +
                        "optional match ()-[c:COMMENTED]->(p)\n" +
                        "with u1, u2, p, like_users, comment_previews, total_likes, count(c) as total_comments\n" +
                        "return p as photo, total_likes, total_comments, Exists((u1)-[:LIKES]->(p)) as is_liked,u2.display_name as poster_name, u2.user_id as poster_id, like_users, comment_previews order by p.timestamp desc",
                params)
    }

    fun distanceQuery(paged: Boolean, userID: String, myLat: Double, myLong: Double, isFeed: Boolean, targetDisplayName: String = "", prevID :String="", prevLat: Double = 0.0, prevLong: Double = 0.0): Pair<String, HashMap<String, Any>> {
        val params = hashMapOf<String, Any>()
        params.put("user_id", userID)
        params.put("my_lat", myLat)
        params.put("my_long", myLong)
        params.put("prev_photo_id", prevID)
        params.put("prev_photo_long", prevLong)
        params.put("prev_photo_lat", prevLat)
        params.put("target_display_name", targetDisplayName)
        return Pair("with point({longitude:\$my_long,latitude:\$my_lat}) as me\n" +
                (if (paged) ",point({longitude:\$prev_photo_long,latitude:\$prev_photo_lat}) as lastpt\n" +
                        "match (lastp:Photo{photo_id:\$prev_photo_id})\n" +
                        "with me, lastpt, lastp\n" else "") +
                (if (isFeed) "MATCH (u1:User{user_id:\$user_id})-[:FOLLOWS]->(u2), (u2)-[:POSTED]->(p:Photo)\n" else photoListSub(true, paged)) +
                "with u1, u2, p" + ", distance(me,point({longitude:p.longitude, latitude:p.latitude})) as d2\n" +
                (if (paged) ",distance(me,lastpt) as d1\n" + "where d2 >= d1 and not (lastp)=(p)\n" else "") +
                "with u1, u2, d2,p order by d2 ascending limit ${Main.pageLimit}\n" +
                "CALL apoc.cypher.run('optional match (u3)-[l:LIKES]->(p),(u1)-[:FOLLOWS]->(u3)  return u3 limit 2', {p:p,u1:u1}) yield value\n" +
                "with u1, u2, d2, p, collect(value.u3.display_name) as like_users\n" +
                "CALL apoc.cypher.run('optional match (u4)-[l:COMMENTED]->(p),(u1)-[:FOLLOWS]->(u4) return u4, l.text as text limit 2', {p:p,u1:u1}) yield value\n" +
                "with u1, u2, d2, p, like_users, collect({commenter_name:value.u4.display_name,comment_text:value.text}) as comment_previews\n" +
                "optional match ()-[l:LIKES]->(p) \n" +
                "with u1, u2, d2, p, like_users, comment_previews, count(l) as total_likes\n" +
                "optional match ()-[c:COMMENTED]->(p)\n" +
                "with u1, u2, d2, p, like_users, comment_previews, total_likes, count(c) as total_comments\n" +
                "return p as photo, total_likes,total_comments,Exists((u1)-[:LIKES]->(p)) as is_liked,d2 as distance, u2.display_name as poster_name, u2.user_id as poster_id, like_users, comment_previews order by d2 asc",
                params)
    }

    val sortOptions = hashSetOf("date", "location")

    val feed = Route { request, response ->
        val sort = request.queryParamOrDefault("sort", "").toLowerCase()
        val latitude = request.queryParamOrDefault("latitude", "a").toDoubleOrNull()
        val longitude = request.queryParamOrDefault("longitude", "a").toDoubleOrNull()
        val lastPhotoFetchedID = request.queryParamOrDefault("last_photo_fetched", "")
        val userID = request.attribute("user") as String


        // If sorting by date with no previous photo

        Database.executeTransaction("Fetch Feed") {
            val lastPhotoFetched= it.findNode(Label { "Photo" }, "photo_id", lastPhotoFetchedID)
            if(lastPhotoFetchedID.isNotBlank() && lastPhotoFetched==null){
                return@executeTransaction JSONObject().fail(message = "Invalid last photo")
            }

            if (sort !in sortOptions) {
                return@executeTransaction JSONObject().fail(message = "Invalid sort")
            }
            if (sort == "location" && (latitude == null || longitude == null)) {
                return@executeTransaction JSONObject().fail(message = "Missing latitude or longitude")
            }
            if (sort == "location" && (latitude!! >180.0 || latitude <-180.0 ||longitude!! >180.0 || longitude <-180.0)) {
                return@executeTransaction JSONObject().fail(message = "Invalid longitude or latitude")
            }

            var results: Result? = null

            when {
                sort == "date" && lastPhotoFetched == null -> {
                    val query = timeQuery(paged = false, userID = userID, isFeed = true)
                    results = it.execute(query.first, query.second)
                }
                sort == "date" && lastPhotoFetched != null -> {
                    val query = timeQuery(paged = true, userID = userID, isFeed = true, timestamp = lastPhotoFetched.getProperty("timestamp") as Long)
                    results = it.execute(query.first, query.second)
                }
                sort == "location" && lastPhotoFetched == null -> {
                    val query = distanceQuery(paged = false, userID = userID, isFeed = true, myLat = latitude!!, myLong = longitude!!)
                    results = it.execute(query.first, query.second)
                }
                sort == "location" && lastPhotoFetched != null -> {
                    val query = distanceQuery(true, userID, isFeed = true, myLat = latitude!!, myLong = longitude!!
                            , prevID = lastPhotoFetchedID, prevLat = lastPhotoFetched.getProperty("latitude") as Double, prevLong = lastPhotoFetched.getProperty("longitude") as Double)
                    results = it.execute(query.first, query.second)
                }
                else -> {

                }
            }
            if (results == null) {
                return@executeTransaction null
            }

            val json = JSONObject().success()
            json.put("sort", sort)
            json.put("photos", Database.resultToPhotoArray(results))

            return@executeTransaction json
        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")

    }
}
