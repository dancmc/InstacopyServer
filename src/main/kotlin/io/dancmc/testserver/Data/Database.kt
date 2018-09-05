package io.dancmc.testserver.Data

import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Label
import org.neo4j.graphdb.RelationshipType
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.kernel.configuration.BoltConnector
import java.io.File

class Database {


    companion object {

        val bolt = BoltConnector("0")
        var graphDb ={
            val g = GraphDatabaseFactory()
                    .newEmbeddedDatabaseBuilder(File("/users/daniel/downloads/social"))
                    .setConfig(bolt.type, "BOLT")
                    .setConfig(bolt.enabled, "true")
                    .setConfig(bolt.address, "localhost:7687")
                    .newGraphDatabase()
            registerShutdownHook(g)

            println("Database initialised")
            g
        }()

        public fun initialiseConstraints(){
            setUniqueConstraint(Label.label("User"), "user_id")
            setUniqueConstraint(Label.label("User"), "username")
            setUniqueConstraint(Label.label("User"), "display_name")
            setUniqueConstraint(Label.label("User"), "email")
            setUniqueConstraint(Label.label("Photo"), "photo_id")
        }

        private fun addUsers(users:ArrayList<User>){
            val tx = graphDb.beginTx()
            try {
                users.forEach {
                    val userFromDb = graphDb.findNode(Label.label("Person"), "user_id", it.userID)
                    if(userFromDb==null) {
                        val user = graphDb.createNode(Label.label("Person"))
                        user.setProperty("user_id", it.userID)
                        user.setProperty("username", it.username)
                        user.setProperty("display_name", it.displayName)
                        user.setProperty("password_hash", it.passwordHash)
                        user.setProperty("email", it.email)
                        user.setProperty("first_name", it.firstName)
                        user.setProperty("last_name", it.lastName)
                        user.setProperty("email_verified", it.emailVerified)
                        user.setProperty("active", it.active)
                        user.setProperty("is_bot", it.isBot)
                        user.setProperty("facebook_token", it.facebookToken)
                        user.setProperty("private", it.isPrivate)
                        user.setProperty("profile_name", it.profileName)
                        user.setProperty("profile_desc", it.profileDesc)
                    }
                }
            } catch (e: Exception) {

                println(e.message)
            } finally {
                tx.success()
                tx.close()
            }
        }

        private fun addPhotos(photos:ArrayList<Photo>){
            val tx = graphDb.beginTx()
            try {
                photos.forEach {
                    val photoFromDb = graphDb.findNode(Label.label("Photo"), "photo_id", it.photo_id)
                    if(photoFromDb==null) {
                        val photo = graphDb.createNode(Label.label("Photo"))
                        photo.setProperty("photo_id", it.photo_id)
                        photo.setProperty("caption", it.caption)
                        photo.setProperty("timestamp", it.timestamp)
                        photo.setProperty("location_name", it.location_name)
                        photo.setProperty("latitude", it.latitude)
                        photo.setProperty("longitude", it.longitude)
                    }
                }
            } catch (e: Exception) {

                println(e.message)
            } finally {
                tx.success()
                tx.close()
            }
        }


        private fun setUniqueConstraint(label:Label, key:String){
            val tx = graphDb.beginTx()
            try {
                graphDb.schema()
                        .constraintFor(label)
                        .assertPropertyIsUnique(key)
                        .create()

            } catch (e: Exception) {
                println(e.message)
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

        public fun init(){}

    }


}