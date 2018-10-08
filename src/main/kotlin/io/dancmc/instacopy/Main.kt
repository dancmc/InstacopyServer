package io.dancmc.instacopy

import apoc.cypher.Cypher
import apoc.help.Help
import apoc.text.Strings
import com.auth0.jwt.interfaces.DecodedJWT
import io.dancmc.instacopy.Data.DataLoader
import io.dancmc.instacopy.Data.Database
import io.dancmc.instacopy.Routes.*
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import org.json.JSONObject
import org.neo4j.graphdb.Label
import org.neo4j.internal.kernel.api.exceptions.KernelException
import org.neo4j.kernel.impl.proc.Procedures
import org.neo4j.kernel.internal.GraphDatabaseAPI
import spark.Spark.*


class Main {

    companion object {

        val picRoute = "/instacopy/files"
//        val picFolder = "/users/daniel/downloads/unsplash"
//        val domain = "http://10.0.0.3:8080/instacopy/v1"
//        val databaseLocation = "/users/daniel/downloads/social"
        val picFolder = "/mnt/www/instacopy/photos"
        val domain = "https://dancmc.io/instacopy/v1"
        val databaseLocation  = "/mnt/www/instacopy/social"
//        val picFolder = "/var/www/instacopy/photos"
//        val domain = "https://danielchan.io/instacopy/v1"
//        val databaseLocation  = "/var/www/instacopy/social"

        val pageLimit = 20

        @JvmStatic
        fun main(args: Array<String>) {


            port(6800)

            // Do authorisation check
            before("/*") { request, response ->
                val userId: String
                val tokenDecode = request.decodeToken()


                when {
                    tokenDecode is JSONObject -> {
                        // TODO remember to change this
//                        request.attribute("user", "315022a1-3702-4997-9b95-4419caa6e81e")
                        val path = request.pathInfo()
                        if (!path.contains("/user/login") && !path.contains("/user/register") && !path.contains("/admin")) {
                            halt(401, tokenDecode.toString())
                        }
                    }
                    else -> {
                        userId = (tokenDecode as DecodedJWT).audience[0].toString()
                        request.attribute("user", userId)
                    }
                }
                response.type("application/json")
            }

            path("/instacopy/v1") {
                path("/user") {
                    post("/register", UserRoutes.register)
                    post("/login", UserRoutes.login)
                    get("/info", UserRoutes.getInfo)
                    get("/photos", UserRoutes.getPhotos)
                    post("/follow", UserRoutes.follow)
                    post("/unfollow", UserRoutes.unfollow)
                    post("/requests", UserRoutes.requests)
                    post("/approve", UserRoutes.approve)
                    get("/followers", UserRoutes.getFollows(false))
                    get("/following", UserRoutes.getFollows(true))
                    get("/followingWhoFollow", UserRoutes.getFollowingWhoFollow())
                    post("/update", UserRoutes.updateDetails)
                    get("/getDetails", UserRoutes.getDetails)
                    get("/validate", UserRoutes.validate)
                }
                get("/feed", FeedRoutes.feed)
                get("/search", DiscoverRoutes.search)
                path("/suggested") {
                    get("/users", DiscoverRoutes.suggestUsers)
                    get("/photos", DiscoverRoutes.suggestPhotos)
                }
                path("/photo") {
                    post("/upload", PhotoRoutes.upload)
                    post("/specific", PhotoRoutes.getPhotos)
                    get("/comments/retrieve", PhotoRoutes.getPhotoComments)
                    post("/comments/new", PhotoRoutes.postPhotoComment)
                    post("/comments/delete", PhotoRoutes.deletePhotoComment)
                    get("/likes/retrieve", PhotoRoutes.getPhotoLikes)
                    post("/likes/like", PhotoRoutes.likePhoto)
                    post("/likes/unlike", PhotoRoutes.unlikePhoto)
                }
                path("/activity") {
                    get("/self", ActivityRoutes.getOwnActivity)
                    get("/following", ActivityRoutes.getOthersActivity)
                }
                path("/static") {
                    get("/photos", MiscRoutes.redirectToStaticPhotos)

                }

                path("/admin") {
                    get("/changePassword", AdminRoutes.changePassword)
                    get("/kill", AdminRoutes.kill)
                }
            }



            Database.init()

            runBlocking {
                while (!Database.initialised) {
                    delay(1000)
                }

                val proceduresToRegister = listOf(Help::class.java, Cypher::class.java)
                val functionsToRegister = listOf(Strings::class.java)

                val procedures = (Database.graphDb as GraphDatabaseAPI).dependencyResolver.resolveDependency(Procedures::class.java)
                proceduresToRegister.forEach { proc ->
                    try {
                        procedures.registerProcedure(proc)
                    } catch (e: KernelException) {
                        throw RuntimeException("Error registering $proc", e)
                    }
                }
                functionsToRegister.forEach { fn ->
                    try {
                        procedures.registerFunction(fn)
                    } catch (e: KernelException) {
                        throw RuntimeException("Error registering $fn", e)
                    }
                }

//                Database.initialiseConstraints()
//                DataLoader.execute()


                val a = System.currentTimeMillis()


                Database.executeTransaction {
                }

                val b = System.currentTimeMillis() - a
                println("$b")
                Database.executeTransaction {

                }

                val c = System.currentTimeMillis() - b - a
                println("$c")

                Database.executeTransaction {

                }

                val d = System.currentTimeMillis() - b - a - c
                println("$d")


                return@runBlocking
            }
        }

    }


}
