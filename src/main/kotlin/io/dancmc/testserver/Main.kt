package io.dancmc.testserver

import apoc.cypher.Cypher
import apoc.text.Strings

import apoc.text.Phonetic
import apoc.help.Help
import com.auth0.jwt.interfaces.DecodedJWT
import io.dancmc.testserver.Data.DataLoader
import io.dancmc.testserver.Data.Database
import io.dancmc.testserver.Routes.*
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import org.json.JSONObject
import org.neo4j.graphdb.Label
import org.neo4j.graphdb.Node
import spark.Spark.*
import org.neo4j.internal.kernel.api.exceptions.KernelException
import jdk.nashorn.internal.objects.NativeArray.forEach
import org.json.JSONArray
import org.neo4j.cypher.internal.compiler.v3_1.CartesianPoint
import org.neo4j.cypher.internal.compiler.v3_1.Coordinate
import org.neo4j.driver.v1.util.Functions
import org.neo4j.graphdb.Result
import org.neo4j.kernel.impl.proc.Procedures
import org.neo4j.kernel.internal.GraphDatabaseAPI





class Main {

    companion object {

        val picRoute = "/instacopy/files"
        val picFolder = "/users/daniel/downloads/unsplash"
//        val picFolder = "/users/daniel/downloads"
//        val domain = "https://danielchan.io/instacopy"
        val domain = "http://localhost:8080/instacopy/v1"
//        val picRoute = "/var/www/instacopy/photos"
        val pageLimit = 20

        @JvmStatic
        fun main(args: Array<String>) {


            port(6800)

            // Do authorisation check
            before("/*"){request, response->
                val userId: String
                val tokenDecode = request.decodeToken()


                when {
                    tokenDecode is JSONObject -> {
                        // TODO remember to change this
                        request.attribute("user", "315022a1-3702-4997-9b95-4419caa6e81e")
//                        halt(401, tokenDecode.toString())
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
                    post("/approve", UserRoutes.approve)
                    get("/followers", UserRoutes.getFollows(false))
                    get("/following", UserRoutes.getFollows(true))
                    post("/update", UserRoutes.updateDetails)
                    get("/getDetails", UserRoutes.getDetails)
                }
                get("/feed", FeedRoutes.feed)
                get("/search", DiscoverRoutes.search)
                path("/suggested") {
                    get("/users", DiscoverRoutes.suggestUsers)
                    get("/photos/grid", DiscoverRoutes.suggestPhotoGrid)
                    get("/photos/list", DiscoverRoutes.suggestPhotoList)
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
            }


//
//            http.post("/androidtest/pic") {
//                val tempFile = Files.createTempFile(File(picRoute).toPath(), "", "")
//
//                request.attribute("org.eclipse.jetty.multipartConfig", MultipartConfigElement(""))
//
//                val filepart = request.raw().getPart("picture")
//                val submittedName = filepart.submittedFileName
//                filepart.inputStream.use { // getPart needs to use same "name" as input field in form
//                    input ->
//                    Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
//                }
//
//                val json = JSONObject()
//                json.put("status", "success")
//                json.put("link", "https://danielchan.io/androidtest/pic/${tempFile.fileName}")
//            }


            Database.init()
            runBlocking {
                while (!Database.initialised){
                    delay(1000)
                }

                val proceduresToRegister = listOf(Help::class.java, Cypher::class.java )
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

                val d = System.currentTimeMillis() - b - a-c
                println("$d")

                return@runBlocking
            }
        }

    }


}
