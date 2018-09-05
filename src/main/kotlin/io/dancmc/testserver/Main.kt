package io.dancmc.testserver

import io.dancmc.testserver.Data.Database
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.json.JSONObject
import spark.kotlin.ignite


class Main {

    companion object {

        //        val picFolder = "/users/daniel/downloads/pics"
        val picFolder = "/var/www/androidtest/pictures"

        @JvmStatic
        fun main(args: Array<String>) {
            val http = ignite()

            http.port(6790)

            http.get("/androidtest/time") {
                val lagParam = request.queryParamOrDefault("lag", "0")
                val lag = lagParam.toLongOrNull()

                val json = JSONObject()

                if (lag == null) {
                    json.put("Error", "Lag is not a valid long")
                } else {
                    runBlocking {
                        delay(lag)
                        json.put("time", System.currentTimeMillis())
                    }
                }
            }
//            launch {
//                Database.init()
//                delay(5000)
//                Database.initialiseConstraints()
//            }



//
//            http.get("/androidtest/pic/:name") {
//                val name = request.params("name")
//                response.header("Content-Type", "image/jpeg")
//                response.header("X-Accel-Redirect", "/androidtest/pictures/$name")
//            }
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
//
//            http.get("/androidtest/pic"){
//                val sizeParam = request.queryParamOrDefault("size","small").toString()
//                val folder = File(picFolder)
//                val files = folder.listFiles { file->
//                    when(sizeParam){
//                        "all" -> true
//                        "large" -> file.length()>=100000
//                        else -> file.length()<100000
//                    }
//                }
//
//
//                val reply = JSONObject()
//                val fileArray = JSONArray()
//                files.forEach {
//                    val fileObject = JSONObject()
//                    fileObject.put("name", it.name)
//                    fileObject.put("size", it.length())
//                    fileArray.put(fileObject)
//                }
//                reply.put("images",fileArray)
//                reply
//            }

//            ImagePuller.getUrls(ImagePuller.accessKey1)
//            ImagePuller.getPictures("regular-v2")
//            DataLoader.createBuckets(30000,2000)


//            val quoteSet = HashSet<String>()
//            FileWriter(File("/users/daniel/downloads/unsplash/quotes.txt"), true).use { thumbWriter ->
//
//                while (quoteSet.size < 3000) {
//                    try {
//                        val quotes = ImagePuller.getQuotes()
//                        quoteSet.addAll(quotes)
//                        quotes.forEach { thumbWriter.appendln(it) }
//                    } catch (e:Exception){
//                        println(e.message)
//                    }
//
//                }
//
//            }
//
//            quoteSet.clear()
//            try {
//                FileWriter(File("/users/daniel/downloads/unsplash/quotes2.txt"), true).use { thumbWriter ->
//                    BufferedReader(FileReader(File("/users/daniel/downloads/unsplash/quotes.txt"))).use { br ->
//                        var line = br.readLine()
//
//                        while (line != null) {
//                            quoteSet.add(line)
//
//                            line = br.readLine()
//                        }
//
//                    }
//                    println(quoteSet.size)
//                    quoteSet.forEach { thumbWriter.appendln(it) }
//                }
//
//            } catch (e: Exception) {
//                println(e.message)
//            }
        }


    }
}