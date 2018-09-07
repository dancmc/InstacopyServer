package io.dancmc.testserver

import com.auth0.jwt.interfaces.DecodedJWT
import io.dancmc.testserver.Routes.*
import org.json.JSONObject
import spark.Spark.*


class Main {

    companion object {

        val picFolder = "/instacopy/files"
        val domain = "https://danielchan.io/instacopy"
//        val picFolder = "/var/www/instacopy/photos"

        @JvmStatic
        fun main(args: Array<String>) {


            port(6800)

            // Do authorisation check
//            before("/*"){request, response->
//                val userId: Long
//                val tokenDecode = request.decodeToken()
//
//                when {
//                    tokenDecode is JSONObject -> {
//                        halt(401, tokenDecode.toString())
//                    }
//                    else -> {
//                        userId = (tokenDecode as DecodedJWT).audience[0].toLong()
//                        request.attribute("user", userId)
//                    }
//                }
//            }

            path("/instacopy/v1") {
                path("/user") {
                    post("/register", UserRoutes.register)
                    post("/login", UserRoutes.login)
                    get("/info", UserRoutes.getInfo)
                    get("/photos", UserRoutes.getPhotos)
                    post("/follow", UserRoutes.follow)
                    post("/unfollow", UserRoutes.unfollow)
                    post("/approve", UserRoutes.approve)
                    post("/followers", UserRoutes.getFollowers)
                    post("/following", UserRoutes.getFollowing)
                    post("/update", UserRoutes.updateDetails)
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
                    get("/photo/likes/retrieve", PhotoRoutes.getPhotoLikes)
                    post("/photo/likes/like", PhotoRoutes.likePhoto)
                    post("/photo/likes/unlike", PhotoRoutes.unlikePhoto)
                }
                path("/activity") {
                    get("/self", ActivityRoutes.getOwnActivity)
                    get("/following", ActivityRoutes.getOthersActivity)
                }
                path("/static") {
                    get("/photos", MiscRoutes.redirectToStaticPhotos)

                }
            }
        }


//
//            http.post("/androidtest/pic") {
//                val tempFile = Files.createTempFile(File(picFolder).toPath(), "", "")
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


//            Database.init()
//            runBlocking {
//                while (!Database.initialised){
//                    delay(1000)
//                }
////                Database.initialiseConstraints()
////                DataLoader.execute()
//
////
//            }


    }


}
