package io.dancmc.instacopy.Routes

import io.dancmc.instacopy.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import spark.Route
import spark.Spark.halt
import java.io.File

object MiscRoutes {

    val validSizes = hashSetOf("thumb", "small", "regular", "profile")


    // redirects from danielchan.io/instacopy/photos?size=small&id=qwerty to nginx /instacopy/files/small/qwerty.jpg
    val redirectToStaticPhotos = Route { request, response ->
        // thumb, small, regular
        val sizeParam = request.queryParamOrDefault("size", "small").toString().toLowerCase()
        val name = request.queryParamOrDefault("id", "")
        if (name.isBlank() || sizeParam !in validSizes) {
            halt(404, "Photo ID not found")
        }
        val folder = File(Main.picRoute, sizeParam)
        val file = File(folder, "$name.jpg")

        response.header("Content-Type", "image/jpeg")
        println(file.absolutePath)
        response.header("X-Accel-Redirect", file.absolutePath)
    }


    val getTime = Route { request, response ->
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


}