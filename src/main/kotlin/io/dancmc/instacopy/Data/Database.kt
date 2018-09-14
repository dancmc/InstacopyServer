package io.dancmc.instacopy.Data

import io.dancmc.instacopy.Main
import io.dancmc.instacopy.Routes.UserRoutes
import io.dancmc.instacopy.Utils
import org.json.JSONArray
import org.json.JSONObject
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Label
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.RelationshipType
import org.neo4j.graphdb.Result
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.kernel.configuration.BoltConnector
import java.io.File
import java.util.*
import kotlin.collections.HashMap

class Database {


    companion object {

        var initialised = false
        val bolt = BoltConnector("0")
        var graphDb = {
            val g = GraphDatabaseFactory()
                    .newEmbeddedDatabaseBuilder(File(Main.databaseLocation))
                    .setConfig(bolt.type, "BOLT")
                    .setConfig(bolt.enabled, "true")
                    .setConfig(bolt.address, "localhost:7687")
                    .newGraphDatabase()
            registerShutdownHook(g)

            initialised = true
            println("Database initialised")
            g
        }()

        public fun initialiseConstraints() {
            setUniqueConstraint(Label.label("User"), "user_id")
            setUniqueConstraint(Label.label("User"), "username")
            setUniqueConstraint(Label.label("User"), "display_name")
            setUniqueConstraint(Label.label("User"), "email")
            setUniqueConstraint(Label.label("Photo"), "photo_id")
        }

        private fun addPropertiesToUserNode(user: User, node: Node) {
            node.setProperty("user_id", user.userID)
            node.setProperty("username", user.username)
            node.setProperty("display_name", user.displayName)
            node.setProperty("password_hash", user.passwordHash)
            node.setProperty("email", user.email)
            node.setProperty("first_name", user.firstName)
            node.setProperty("last_name", user.lastName)
            node.setProperty("email_verified", user.emailVerified)
            node.setProperty("active", user.active)
            node.setProperty("is_bot", user.isBot)
            node.setProperty("facebook_token", user.facebookToken)
            node.setProperty("private", user.isPrivate)
            node.setProperty("profile_name", user.profileName)
            node.setProperty("profile_desc", user.profileDesc)
        }

        public fun addUser(user: User): Pair<Boolean, String> {

            val result = executeTransaction("Add User") {
                val userFromDb = graphDb.findNode(Label.label("User"), "username", user.username)
                if (userFromDb == null) {
                    val userNode = graphDb.createNode(Label.label("User"))
                    val uuid = graphDb.findNode(Label.label("User"), "user_id", user.userID)
                    if (uuid != null) {
                        user.userID = UUID.randomUUID().toString()
                    }
                    addPropertiesToUserNode(user, userNode)
                    return@executeTransaction true
                } else {
                    return@executeTransaction false
                }
            }
            when (result) {
                null -> return Pair(false, "DB Failure")
                true -> return Pair(true, user.userID)
                false -> return Pair(false, "Username already exists")
                else -> return Pair(false, "")
            }
        }

        public fun addUsers(users: ArrayList<User>) {
            executeTransaction("Add Users") {
                users.forEach {
                    val userFromDb = graphDb.findNode(Label.label("User"), "user_id", it.userID)
                    if (userFromDb == null) {
                        val user = graphDb.createNode(Label.label("User"))
                        addPropertiesToUserNode(it, user)
                    }
                }
            }
        }

        public fun getUser(username: String = "", userID: String = ""): Node? {
            return executeTransaction("Get User") {
                return@executeTransaction if (userID.isNotBlank()) {
                    graphDb.findNode(Label.label("User"), "user_id", userID)
                } else {
                    graphDb.findNode(Label.label("User"), "username", username)
                }
            } as Node?
        }

        public fun addPropertiesToPhotoNode(photo: Photo, node: Node) {
            node.setProperty("photo_id", photo.photo_id)
            node.setProperty("caption", photo.caption)
            node.setProperty("timestamp", photo.timestamp)
            node.setProperty("location_name", photo.location_name)
            node.setProperty("latitude", photo.latitude)
            node.setProperty("longitude", photo.longitude)
        }

        public fun addPhoto(userID:String, photo: Photo, timestamp:Long): Pair<Boolean, String> {

            val result = executeTransaction("Add Photo") {
                val userFromDb = graphDb.findNode(Label.label("User"), "user_id", userID)
                val photoFromDb = graphDb.findNode(Label.label("Photo"), "photo_id", photo.photo_id)
                if (photoFromDb == null) {
                    val photoNode = graphDb.createNode(Label.label("Photo"))
                    addPropertiesToPhotoNode(photo, photoNode)
                    val rel= userFromDb.createRelationshipTo(photoNode, RelationshipType { "POSTED" })
                    rel.setProperty("timestamp", timestamp)
                    return@executeTransaction true
                } else {
                    return@executeTransaction false
                }
            }
            when (result) {
                null -> return Pair(false, "DB Failure")
                true -> return Pair(true, "")
                false -> return Pair(false, "Photo already exists")
                else -> return Pair(false, "")
            }
        }

        public fun addPhotos(photos: ArrayList<Photo>) {
            executeTransaction("Add Photos") {
                photos.forEach {
                    val photoFromDb = graphDb.findNode(Label.label("Photo"), "photo_id", it.photo_id)
                    if (photoFromDb == null) {
                        val photo = graphDb.createNode(Label.label("Photo"))
                        photo.setProperty("photo_id", it.photo_id)
                        photo.setProperty("caption", it.caption)
                        photo.setProperty("timestamp", it.timestamp)
                        photo.setProperty("location_name", it.location_name)
                        photo.setProperty("latitude", it.latitude)
                        photo.setProperty("longitude", it.longitude)
                    }
                }
            }

        }

        public fun addFollows(userFollowMaps: HashMap<String, HashMap<String, Long>>) {
            userFollowMaps.forEach { outerMap ->
                val tx = graphDb.beginTx()
                try {
                    outerMap.value.forEach { innerMap ->
                        val sourceNode = graphDb.findNode(Label.label("User"), "user_id", outerMap.key)
                        val destNode = graphDb.findNode(Label.label("User"), "user_id", innerMap.key)
                        val rel = sourceNode.createRelationshipTo(destNode) { "FOLLOWS" }
                        rel.setProperty("timestamp", innerMap.value)
                    }
                } catch (e: Exception) {

                    println("Add Follows : ${e.message}")
                } finally {
                    tx.success()
                    tx.close()
                }

            }

        }

        public fun addPosted(userUploadMaps: HashMap<String, HashMap<String, Long>>) {
            userUploadMaps.forEach { outerMap ->
                val tx = graphDb.beginTx()
                try {
                    outerMap.value.forEach { innerMap ->
                        val sourceNode = graphDb.findNode(Label.label("User"), "user_id", outerMap.key)
                        val destNode = graphDb.findNode(Label.label("Photo"), "photo_id", innerMap.key)
                        val rel = sourceNode.createRelationshipTo(destNode) { "POSTED" }
                        rel.setProperty("timestamp", innerMap.value)
                    }
                } catch (e: Exception) {

                    println("Add Posted : ${e.message}")
                } finally {
                    tx.success()
                    tx.close()
                }

            }
        }

        public fun addLikes(userLikeMaps: HashMap<String, HashMap<String, Long>>) {
            userLikeMaps.forEach { outerMap ->
                val tx = graphDb.beginTx()
                try {
                    outerMap.value.forEach { innerMap ->
                        val sourceNode = graphDb.findNode(Label.label("User"), "user_id", outerMap.key)
                        val destNode = graphDb.findNode(Label.label("Photo"), "photo_id", innerMap.key)
                        val rel = sourceNode.createRelationshipTo(destNode) { "LIKES" }
                        rel.setProperty("timestamp", innerMap.value)
                    }
                } catch (e: Exception) {

                    println("Add Likes : ${e.message}")
                } finally {
                    tx.success()
                    tx.close()
                }

            }
        }

        public fun addComments(userCommentMaps: HashMap<String, HashMap<String, Pair<String, Long>>>) {
            userCommentMaps.forEach { outerMap ->
                val tx = graphDb.beginTx()
                try {
                    outerMap.value.forEach { innerMap ->
                        val sourceNode = graphDb.findNode(Label.label("User"), "user_id", outerMap.key)
                        val destNode = graphDb.findNode(Label.label("Photo"), "photo_id", innerMap.key)
                        val rel = sourceNode.createRelationshipTo(destNode) { "COMMENTED" }
                        rel.setProperty("comment_id", UUID.randomUUID().toString())
                        rel.setProperty("text", innerMap.value.first)
                        rel.setProperty("timestamp", innerMap.value.second)
                    }
                } catch (e: Exception) {

                    println("Add Comments : ${e.message}")
                } finally {
                    tx.success()
                    tx.close()
                }

            }
        }


        private fun setUniqueConstraint(label: Label, key: String) {
            val tx = graphDb.beginTx()
            try {
                graphDb.schema()
                        .constraintFor(label)
                        .assertPropertyIsUnique(key)
                        .create()

            } catch (e: Exception) {
                println("Set Constraint : ${e.message}")
            } finally {
                tx.success()
                tx.close()
            }
        }


        public fun registerShutdownHook(graphDb: GraphDatabaseService) {
            // Registers a shutdown hook for the Neo4j instance so that it
            // shuts down nicely when the VM exits (even if you "Ctrl-C" the
            // running application).
            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    graphDb.shutdown()
                }
            })

        }

        public fun executeTransaction(tag: String = "", fn: (g: GraphDatabaseService) -> Any?): Any? {
            val tx = graphDb.beginTx()
            try {
                return fn(graphDb)
            } catch (e: Exception) {
                println("$tag : ${e.message}")
                return null
            } finally {
                tx.success()
                tx.close()
            }
        }

        public fun processResult(results: Result, fn: (Map<String, Any>) -> Unit) {

            while (results.hasNext()) {
                fn(results.next())
            }

        }

        public fun resultToPhotoArray(results:Result):JSONArray{

            val array = JSONArray()
            Database.processResult(results){

                val photoObject = JSONObject()
                array.put(photoObject)
                val photoNode = it["photo"] as Node
                val posterID = it["poster_id"] as String
                val posterName = it["poster_name"] as String
                val previewLikeUsers =it["like_users"]!! as ArrayList<String?>
                val previewComments =it["comment_previews"]!! as ArrayList<HashMap<String?, String?>>
                photoObject.put("distance",it["distance"] as Double? ?:-1.0)
                val totalLikes = it["total_likes"] as Long
                val totalComments = it["total_comments"] as Long
                val isLiked = it["is_liked"] as Boolean


                photoObject.put("display_name", posterName)
                photoObject.put("profile_image", Utils.constructPhotoUrl("profile", posterID))
                val photoID = photoNode.getProperty("photo_id") as String
                photoObject.put("photo_id", photoID)
                photoObject.put("url", JSONObject()
                        .put("regular", Utils.constructPhotoUrl("regular", photoID))
                        .put("small", Utils.constructPhotoUrl("small", photoID)))
                val previewCommentsArray = JSONArray()
                previewComments.forEach {
                    val commenterName = it["commenter_name"]
                    val commentText = it["comment_text"]
                    if(commenterName!=null && commentText!=null) {
                        val previewCommentObject = JSONObject()
                        previewCommentObject.put("display_name", commenterName)
                        previewCommentObject.put("text", commentText)
                        previewCommentsArray.put(previewCommentObject)
                    }
                }
                photoObject.put("preview_comments", JSONObject().put("preview_text",previewCommentsArray).put("total_comments", totalComments))
                photoObject.put("is_liked", isLiked)
                val previewLikesArray = JSONArray()
                previewLikeUsers.forEach {
                    if (it!=null){
                        previewLikesArray.put(it)
                    }
                }
                photoObject.put("preview_likes", JSONObject().put("preview_names", previewLikesArray).put("total_likes", totalLikes))
                photoObject.put("location", JSONObject()
                        .put("location_name", photoNode.getProperty("location_name") as String)
                        .put("latitude", photoNode.getProperty("latitude") as Double)
                        .put("longitude", photoNode.getProperty("longitude") as Double))
                photoObject.put("timestamp", photoNode.getProperty("timestamp") as Long)
                photoObject.put("caption", photoNode.getProperty("caption") as String)

            }
            return array
        }

        public fun resultToProfileArray(results:Result):JSONArray{
            val array = JSONArray()
            val list = ArrayList<JSONObject>()
            Database.processResult(results) {
                val profileObject = JSONObject()
                list.add(profileObject)
                profileObject.put("profile_image", Utils.constructPhotoUrl("profile", it["user_id"] as String))
                profileObject.put("display_name", it["display_name"] as String)
                profileObject.put("profile_name", it["profile_name"] as String)
                profileObject.put("are_following", it["are_following"] as Boolean)
                profileObject.put("timestamp", it["timestamp"] as Long)
                array.put(profileObject)
            }
            return array
        }

        public fun userNodeToJson(userID:String, userNode:Node, json:JSONObject){
            json.put("email", userNode.getProperty("email") as String)
            json.put("first_name", userNode.getProperty("first_name") as String)
            json.put("last_name", userNode.getProperty("last_name") as String)
            json.put("display_name", userNode.getProperty("display_name") as String)
            json.put("profile_name", userNode.getProperty("profile_name") as String)
            json.put("profile_desc", userNode.getProperty("profile_desc") as String)
            json.put("profile_image", Utils.constructPhotoUrl("profile", userID))
            json.put("is_private", userNode.getProperty("private") as Boolean)
        }

        fun privacyCheck(userID: String, targetDisplayName: String): HashMap<String, Any>? {
            val params = hashMapOf<String, Any>()
            params.put("user_id", userID)
            params.put("target_display_name", targetDisplayName)


            return executeTransaction {
                val results = it.execute("MATCH (u1:User{user_id:\$user_id})\n" +
                        "with u1\n" +
                        "MATCH (u2:User{display_name:\$target_display_name})\n" +
                        "with u1, u2\n" +
                        "return u2.user_id as other_user_id, EXISTS((u1)-[:FOLLOWS]->(u2)) as is_following, EXISTS((u1)-[:REQUESTED]->(u2)) as has_requested, u2.private as is_private", params)

                val hashMap = HashMap<String,Any>()
                Database.processResult(results) {
                    hashMap.put("isFollowing", it["is_following"]!!)
                    hashMap.put("hasRequested", it["has_requested"]!!)
                    hashMap.put("isPrivate", it["is_private"]!!)
                    hashMap.put("otherID", it["other_user_id"]!!)
                }


                return@executeTransaction hashMap
            } as HashMap<String, Any>?

        }

        public fun init() {}

    }


}