package io.dancmc.testserver.Routes

import io.dancmc.testserver.Data.Database
import io.dancmc.testserver.Data.TemporalEvents.*
import io.dancmc.testserver.Utils
import io.dancmc.testserver.fail
import io.dancmc.testserver.success
import org.json.JSONArray
import org.json.JSONObject
import spark.Route
import java.util.*
import kotlin.collections.HashMap

object ActivityRoutes {

    val getOwnActivity = Route { request, response ->
        // generate list of all :
        // follows, likes, comments, requests
        val userID = request.attribute("user") as String
        val commentsParams = hashMapOf<String, Any>(Pair("user_id", userID))
        val commentsQuery = "match (me:User{user_id:\$user_id})-[o:POSTED]->(p:Photo)\n" +
                "match (u)-[c:COMMENTED]->(p)\n" +
                "return p.photo_id as photo_id, c.timestamp as timestamp, c.text as text, " +
                "u.display_name as display_name, u.user_id as user_id order by c.timestamp desc limit 1000"

        val likesParams = hashMapOf<String, Any>(Pair("user_id", userID))
        val likesQuery = "match (me:User{user_id:\$user_id})-[o:POSTED]->(p:Photo)\n" +
                "match (u)-[c:LIKES]->(p)\n" +
                "return p.photo_id as photo_id, c.timestamp as timestamp, u.display_name as display_name, " +
                "u.user_id as user_id order by c.timestamp desc limit 1000"

        val followsParams = hashMapOf<String, Any>(Pair("user_id", userID))
        val followsQuery = "match (me:User{user_id:\$user_id})<-[f:FOLLOWS]-(u)\n" +
                "return  f.timestamp as timestamp,u.display_name as display_name, u.user_id as user_id, u.profile_name as profile_name,EXISTS((me)-[:FOLLOWS]->(u)) as are_following " +
                "order by f.timestamp desc limit 1000"

        val requestsParams = hashMapOf<String, Any>(Pair("user_id", userID))
        val requestsQuery = "match (me:User{user_id:\$user_id})<-[f:REQUESTED]-(u)\n" +
                "return f.timestamp as timestamp, u.display_name as display_name, u.user_id as user_id, u.profile_name as profile_name, EXISTS((me)-[:FOLLOWS]->(u)) as are_following " +
                "order by f.timestamp desc limit 1000"

        Database.executeTransaction {
            val initialList = LinkedList<BaseTemporalEvent>()
            val commentsResults = it.execute(commentsQuery, commentsParams)
            Database.processResult(commentsResults) {
                initialList.add(CommentTemporalEvent(
                        it["timestamp"] as Long, it["photo_id"] as String,
                        it["display_name"] as String, it["user_id"] as String,
                        it["text"] as String))
            }
            val likesResults = it.execute(likesQuery, likesParams)
            Database.processResult(likesResults) {
                initialList.add(LikeTemporalEvent(
                        it["timestamp"] as Long, it["photo_id"] as String,
                        it["display_name"] as String, it["user_id"] as String))
            }
            val followsResults = it.execute(followsQuery, followsParams)
            Database.processResult(followsResults) {
                initialList.add(FollowTemporalEvent(
                        it["timestamp"] as Long,
                        it["display_name"] as String, it["profile_name"] as String,it["user_id"] as String,
                        "", "",userID, it["are_following"]as Boolean))
            }
            val requestsResults = it.execute(requestsQuery, requestsParams)
            Database.processResult(requestsResults) {
                initialList.add(RequestTemporalEvent(
                        it["timestamp"] as Long,
                        it["display_name"] as String,it["profile_name"] as String, it["user_id"] as String,
                        "", "",userID,it["are_following"]as Boolean))
            }

            initialList.sortByDescending { it.timestamp }

            // Map photo_id, comment events
            val commentMap = HashMap<String, ArrayList<BaseTemporalEvent>>()
            // Map photo_id, like events
            val likeMap = HashMap<String, ArrayList<BaseTemporalEvent>>()
            val followList = ArrayList<BaseTemporalEvent>()
            val requestList = ArrayList<BaseTemporalEvent>()

            while (commentMap.size + likeMap.size + followList.size + requestList.size < 50 && initialList.size > 0) {
                val event = initialList.pop()
                when {
                    event is CommentTemporalEvent -> {
                        var list = commentMap[event.photoID]
                        if (list == null) {
                            list = ArrayList()
                            commentMap[event.photoID] = list

                        }
                        list.add(event)
                    }
                    event is LikeTemporalEvent -> {
                        var list = likeMap[event.photoID]
                        if (list == null) {
                            list = ArrayList()
                            likeMap[event.photoID] = list
                        }
                        list.add(event)
                    }
                    event is FollowTemporalEvent -> {
                        followList.add(event)
                    }
                    event is RequestTemporalEvent -> {
                        requestList.add(event)
                    }
                }
            }

            val finalList = ArrayList<Pair<Long, ArrayList<BaseTemporalEvent>>>()
            commentMap.forEach {
                if(it.value.isNotEmpty()) {
                    finalList.add(Pair(it.value.first().timestamp, it.value))
                }
            }
            likeMap.forEach {
                if(it.value.isNotEmpty()) {
                    finalList.add(Pair(it.value.first().timestamp, it.value))
                }
            }
            if(followList.isNotEmpty()) {
                finalList.add(Pair(followList.first().timestamp, followList))
            }
            if(requestList.isNotEmpty()) {
                finalList.add(Pair(requestList.first().timestamp, requestList))
            }
            finalList.sortByDescending {
                it.first
            }

            val jsonArray = JSONArray()
            finalList.forEach {
                val eventList = it.second
                val firstEvent = eventList.first()
                val eventObject = JSONObject().put("timestamp", it.first)
                jsonArray.put(eventObject)
                when {
                    firstEvent is CommentTemporalEvent -> {
                        eventObject.put("type", 3)
                        eventObject.put("photo_id", firstEvent.photoID)
                        eventObject.put("url", JSONObject().put("thumb", Utils.constructPhotoUrl("thumb", firstEvent.photoID)))
                        eventObject.put("recent_comments", eventList.size)
                        eventObject.put("preview_comment", JSONObject()
                                .put("display_name", firstEvent.commenterName)
                                .put("profile_image", Utils.constructPhotoUrl("profile", firstEvent.commenterID))
                                .put("comment_text", firstEvent.text))
                    }
                    firstEvent is LikeTemporalEvent -> {
                        eventObject.put("type", 2)
                        eventObject.put("photo_id", firstEvent.photoID)
                        eventObject.put("url", JSONObject().put("thumb", Utils.constructPhotoUrl("thumb", firstEvent.photoID)))
                        eventObject.put("recent_likes", eventList.size)

                        val previewArray = JSONArray()
                        previewArray.put(JSONObject().put("display_name", firstEvent.likerName).put("profile_image", Utils.constructPhotoUrl("profile", firstEvent.likerID)))
                        if (eventList.size > 1) {
                            val secondEvent = eventList[1] as LikeTemporalEvent
                            previewArray.put(JSONObject().put("display_name", secondEvent.likerName).put("profile_image", Utils.constructPhotoUrl("profile", secondEvent.likerID)))
                        }
                        eventObject.put("preview_users", previewArray)

                    }
                    firstEvent is FollowTemporalEvent->{
                        eventObject.put("type", 1)
                        val followArray = JSONArray()
                        eventObject.put("users", followArray)
                        eventList.forEach {
                            val followEvent = it as FollowTemporalEvent
                            followArray.put(JSONObject()
                                    .put("display_name", followEvent.sourceName)
                                    .put("profile_image", Utils.constructPhotoUrl("profile", followEvent.sourceID))
                                    .put("profile_name", followEvent.sourceProfileName)
                                    .put("are_following", followEvent.areFollowing)
                            )
                        }
                    }
                    firstEvent is RequestTemporalEvent->{
                        eventObject.put("type", 4)
                        val requestArray = JSONArray()
                        eventObject.put("users", requestArray)
                        eventList.forEach {
                            val requestEvent = it as RequestTemporalEvent
                            requestArray.put(JSONObject()
                                    .put("display_name", requestEvent.sourceName)
                                    .put("profile_image", Utils.constructPhotoUrl("profile", requestEvent.sourceID))
                                    .put("profile_name", requestEvent.sourceProfileName)
                                    .put("are_following", requestEvent.areFollowing)
                            )
                        }
                    }
                }
            }

            JSONObject().success().put("activities", jsonArray)
        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")


    }

    val threeMonthsMs = 1000L*60*60*24*30*3

    val getOthersActivity = Route { request, response ->
        val userID = request.attribute("user") as String
        val timestampLimit = System.currentTimeMillis() - threeMonthsMs

        val likeParams = hashMapOf<String, Any>(Pair("user_id", userID), Pair("timestamp_limit",timestampLimit))
        val likesQuery = "match (me:User{user_id:\$user_id})-[:FOLLOWS]->(u), (u)-[l:LIKES]->(p)\n" +
                "where l.timestamp>\$timestamp_limit\n" +
                "return l.timestamp as timestamp, p.photo_id as photo_id,u.display_name as display_name, " +
                "u.user_id as user_id, u.profile_name as profile_name order by l.timestamp desc limit 400"

        val followParams = hashMapOf<String, Any>(Pair("user_id", userID), Pair("timestamp_limit",timestampLimit))
        val followsQuery = "match (me:User{user_id:\$user_id})-[:FOLLOWS]->(u), (u)-[f:FOLLOWS]->(u1)\n" +
                "where f.timestamp>\$timestamp_limit\n" +
                "return f.timestamp as timestamp, u.display_name as source_display_name, u.user_id as source_user_id, u.profile_name as source_profile_name," +
                "u1.display_name as sink_display_name, u1.user_id as sink_user_id, u1.profile_name as sink_profile_name," +
                "EXISTS((me)-[:FOLLOWS]-(u1)) as are_following_sink order by f.timestamp desc limit 400"

        // grouped by user_id
        val userLikedPosts = HashMap<String, ArrayList<BaseTemporalEvent>>()
        // grouped by post_id
        val usersLikedPost = HashMap<String, ArrayList<BaseTemporalEvent>>()
        // group by user_id
        val userFollowedUsers = HashMap<String, ArrayList<BaseTemporalEvent>>()

        Database.executeTransaction {
            val initialList = LinkedList<BaseTemporalEvent>()
            val likesResult = it.execute(likesQuery, likeParams)
            Database.processResult(likesResult) {
                initialList.add(LikeTemporalEvent(
                        it["timestamp"] as Long, it["photo_id"] as String,
                        it["display_name"] as String, it["user_id"] as String))
            }
            val followsResults = it.execute(followsQuery, followParams)
            Database.processResult(followsResults) {
                initialList.add(FollowTemporalEvent(
                        it["timestamp"] as Long,
                        it["source_display_name"] as String, it["source_profile_name"] as String,it["source_user_id"] as String,
                        it["sink_display_name"] as String, it["sink_profile_name"] as String,it["sink_user_id"] as String, it["are_following_sink"]as Boolean))
            }

            initialList.sortByDescending { it.timestamp }

            while (userLikedPosts.size+usersLikedPost.size+userFollowedUsers.size<50 && initialList.size>0){
                val event = initialList.pop()
                when {

                    event is LikeTemporalEvent -> {

                        val photoID = event.photoID
                        if(usersLikedPost[photoID]==null){
                            if(userLikedPosts[event.likerID]==null) {
                                val newList = ArrayList<BaseTemporalEvent>()
                                newList.add(event)
                                userLikedPosts[event.likerID] = newList
                            }else{
                                userLikedPosts[event.likerID]!!.add(event)
                            }
                            val newList = ArrayList<BaseTemporalEvent>()
                            newList.add(event)
                            usersLikedPost[photoID] = newList
                        } else{
                            val list = usersLikedPost[photoID]!!
                            list.add(event)
                            if(list.size==1){
                                val firstUserID = (list.first() as LikeTemporalEvent).likerID
                                userLikedPosts[firstUserID]!!.removeIf { (it as LikeTemporalEvent).photoID == photoID }
                            }
                        }

                    }
                    event is FollowTemporalEvent -> {
                        if(userFollowedUsers[event.sourceID]==null){
                            val list = ArrayList<BaseTemporalEvent>()
                            list.add(event)
                            userFollowedUsers[event.sourceID] = list
                        }else {
                            userFollowedUsers[event.sourceID]!!.add(event)
                        }
                    }

                }
            }

            val finalList = ArrayList<Triple<String, Long, ArrayList<BaseTemporalEvent>>>()
             userLikedPosts.forEach {
                 if(it.value.isNotEmpty()) {
                     finalList.add(Triple("OneToMany", it.value.first().timestamp, it.value))
                 }
             }
            usersLikedPost.forEach {
                if(it.value.isNotEmpty()) {
                    finalList.add(Triple("ManyToOne", it.value.first().timestamp, it.value))
                }
            }
            userFollowedUsers.forEach {
                if(it.value.isNotEmpty()) {
                    finalList.add(Triple("Follows", it.value.first().timestamp, it.value))
                }
            }
            finalList.sortByDescending { it.second }

            val jsonArray = JSONArray()
            finalList.forEach {
                val eventList = it.third
                val eventObject = JSONObject().put("timestamp", it.second)
                jsonArray.put(eventObject)

                when(it.first){
                    "OneToMany"->{
                        val firstEvent = eventList.first() as LikeTemporalEvent
                        eventObject.put("type", 1)
                        eventObject.put("display_name", firstEvent.likerName)
                        eventObject.put("profile_image", Utils.constructPhotoUrl("profile", firstEvent.likerID))
                        eventObject.put("total_liked", eventList.size)
                        val likedPhotos = JSONArray()
                        eventList.forEachIndexed { index, baseTemporalEvent->
                            if(index<8){
                                val event = baseTemporalEvent as LikeTemporalEvent
                                val photoObject = JSONObject()
                                photoObject.put("photo_id", event.photoID)
                                photoObject.put("url", JSONObject().put("url", JSONObject().put("thumb", Utils.constructPhotoUrl("thumb", event.photoID))))
                                likedPhotos.put(photoObject)
                            }
                        }
                        eventObject.put("photos_liked", likedPhotos)
                    }
                    "ManyToOne"->{
                        val firstEvent = eventList.first() as LikeTemporalEvent
                        eventObject.put("type", 2)
                        eventObject.put("photo_id", firstEvent.photoID)
                        eventObject.put("total_liked", eventList.size)
                        eventObject.put("url", JSONObject().put("url", JSONObject().put("thumb", Utils.constructPhotoUrl("thumb", firstEvent.photoID))))
                        val usersWhoLiked = JSONArray()
                        eventList.forEachIndexed { index, baseTemporalEvent->
                            if(index<3){
                                val event = baseTemporalEvent as LikeTemporalEvent
                                val userObject = JSONObject()
                                userObject.put("display_name", event.likerName)
                                userObject.put("profile_image",Utils.constructPhotoUrl("profile", event.likerID))
                                usersWhoLiked.put(userObject)
                            }
                        }
                        eventObject.put("preview_users", usersWhoLiked)
                    }
                    "Follows"->{
                        val firstEvent = eventList.first() as FollowTemporalEvent
                        eventObject.put("type", 3)
                        eventObject.put("display_name", firstEvent.sourceName)
                        eventObject.put("profile_image",Utils.constructPhotoUrl("profile", firstEvent.sourceID) )
                        eventObject.put("total_followed", eventList.size)
                        val usersFollowed = JSONArray()
                        eventList.forEach {
                            val event = it as FollowTemporalEvent
                            val userObject = JSONObject()
                            userObject.put("display_name", event.sinkName)
                            userObject.put("profile_image",Utils.constructPhotoUrl("profile", event.sinkID))
                            userObject.put("profile_name", event.sinkProfileName)
                            userObject.put("are_following", event.areFollowing)
                            usersFollowed.put(userObject)
                        }
                        eventObject.put("users_followed", usersFollowed)
                    }
                }
            }


            JSONObject().success().put("activities", jsonArray)
        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")

    }
}