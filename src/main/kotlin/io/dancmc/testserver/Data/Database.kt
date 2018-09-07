package io.dancmc.testserver.Data

import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Label
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.kernel.configuration.BoltConnector
import java.io.File
import java.util.*

class Database {


    companion object {

        var initialised = false
        val bolt = BoltConnector("0")
        var graphDb = {
            val g = GraphDatabaseFactory()
                    .newEmbeddedDatabaseBuilder(File("/users/daniel/downloads/social"))
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

        private fun addPropertiesToUserNode(user:User, node:Node){
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

        public fun addUser(user:User):Pair<Boolean, String>{

            val result = executeTransaction ("Add User"){
                val userFromDb = graphDb.findNode(Label.label("User"), "username", user.username)
                if (userFromDb == null) {
                    val userNode = graphDb.createNode(Label.label("User"))
                    val uuid = graphDb.findNode(Label.label("User"), "user_id", user.userID)
                    if (uuid!=null){
                        user.userID = UUID.randomUUID().toString()
                    }
                    addPropertiesToUserNode(user, userNode)
                    return@executeTransaction true
                } else{
                    return@executeTransaction false
                }
            }
            when(result){
                null -> return Pair(false, "DB Failure")
                true-> return Pair(true,"")
                false-> return Pair(false,"Username already exists")
                else -> return Pair(false, "")
            }
        }

        public fun addUsers(users: ArrayList<User>) {
            executeTransaction("Add Users"){
                users.forEach {
                    val userFromDb = graphDb.findNode(Label.label("User"), "user_id", it.userID)
                    if (userFromDb == null) {
                        val user = graphDb.createNode(Label.label("User"))
                        addPropertiesToUserNode(it, user)
                    }
                }
            }
        }

        public fun addPropertiesToPhotoNode(photo:Photo, node:Node){
            node.setProperty("photo_id", photo.photo_id)
            node.setProperty("caption", photo.caption)
            node.setProperty("timestamp", photo.timestamp)
            node.setProperty("location_name", photo.location_name)
            node.setProperty("latitude", photo.latitude)
            node.setProperty("longitude", photo.longitude)
        }

        public fun addPhoto(photo:Photo):Pair<Boolean, String>{

            val result = executeTransaction ("Add Photo"){
                val userFromDb = graphDb.findNode(Label.label("Photo"), "photo_id", photo.photo_id)
                if (userFromDb == null) {
                    val photoNode = graphDb.createNode(Label.label("Photo"))
                    addPropertiesToPhotoNode(photo, photoNode)
                    return@executeTransaction true
                } else{
                    return@executeTransaction false
                }
            }
            when(result){
                null -> return Pair(false, "DB Failure")
                true-> return Pair(true,"")
                false-> return Pair(false,"Photo already exists")
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

        public fun executeTransaction(tag:String="",fn:()->Any):Any?{
            val tx = graphDb.beginTx()
            try {
                return fn()
            } catch (e: Exception) {
                println("$tag : ${e.message}")
                return null
            } finally {
                tx.success()
                tx.close()
            }
        }

        public fun init() {}

    }


}