package io.dancmc.testserver

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.TokenExpiredException
import de.mkammerer.argon2.Argon2Factory
import org.json.JSONObject
import spark.Request
import java.util.*
import java.awt.image.BufferedImage
import java.awt.image.WritableRaster
import java.awt.image.ColorModel
import javax.imageio.ImageIO
import org.imgscalr.Scalr
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.imaging.ImageMetadataReader
import org.imgscalr.Scalr.Rotation
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption


object Utils {

    object Password {
        val argon2 = Argon2Factory.create()

        fun hashPassword(password: String): String {
            return argon2.hash(1, 65536, 1, password)
        }

        fun verifyPassword(hash: String, password: String): Boolean {
            val result = argon2.verify(hash, password)
            argon2.wipeArray(password.toCharArray())
            return result
        }
    }

    fun loadTSV(file: String): ArrayList<List<String>> {
        val result = ArrayList<List<String>>()

        try {
            BufferedReader(FileReader(File(file))).use { br ->
                var line = br.readLine()

                while (line != null) {
                    result.add(line.split("\t"))
                    line = br.readLine()
                }

            }
        } catch (e: Exception) {
            println(e.message)
        }
        return result
    }

    fun loadTSVLinked(file: String): LinkedList<List<String>> {
        val result = LinkedList<List<String>>()

        try {
            BufferedReader(FileReader(File(file))).use { br ->
                var line = br.readLine()

                while (line != null) {
                    result.add(line.split("\t"))
                    line = br.readLine()
                }

            }
        } catch (e: Exception) {
            println(e.message)
        }
        return result
    }

    fun loadFile(file: String): ArrayList<String> {
        val result = ArrayList<String>()

        try {
            BufferedReader(FileReader(File(file))).use { br ->
                var line = br.readLine()

                while (line != null) {
                    result.add(line)
                    line = br.readLine()
                }

            }
        } catch (e: Exception) {
            println(e.message)
        }
        return result
    }

    fun write(file: String, lines: ArrayList<String>) {
        FileWriter(File(file), true).use { writer ->
            lines.forEach {
                writer.appendln(it)
            }
        }
    }

    fun constructPhotoUrl(size:String, photoID:String):String{
        return "${Main.domain}/static/photos?size=$size&id=$photoID"
    }

    fun deepCopy(bi: BufferedImage): BufferedImage {
        val cm = bi.colorModel
        val isAlphaPremultiplied = cm.isAlphaPremultiplied
        val raster = bi.copyData(bi.raster.createCompatibleWritableRaster())
        return BufferedImage(cm, raster, isAlphaPremultiplied, null)
    }

    fun  handleImage(photoName:String,inputstream:InputStream, profile:Boolean){
        val temp = File(Main.picFolder, "${UUID.randomUUID()}.jpg")
        inputstream.use { // getPart needs to use same "name" as input field in form
            input -> Files.copy(input, temp.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        val sizes = if(profile) listOf(Pair("profile",400))
        else listOf(Pair("thumb",200),
                Pair("small",400),
                Pair("regular",1080))

        sizes.forEach {
            val originalImage = ImageIO.read(temp)
            var scaledImg = Scalr.resize(originalImage, it.second)

            // ---- Begin orientation handling ----
            val metadata = ImageMetadataReader.readMetadata(temp)
            val exifIFD0Directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)

            var orientation = 1
            try {
                orientation = exifIFD0Directory.getInt(ExifIFD0Directory.TAG_ORIENTATION)
            } catch (ex: Exception) {
                println("no orientation data")
            }


            when (orientation) {
                1 -> {
                }
                2 // Flip X
                -> scaledImg = Scalr.rotate(scaledImg, Rotation.FLIP_HORZ)
                3 // PI rotation
                -> scaledImg = Scalr.rotate(scaledImg, Rotation.CW_180)
                4 // Flip Y
                -> scaledImg = Scalr.rotate(scaledImg, Rotation.FLIP_VERT)
                5 // - PI/2 and Flip X
                -> {
                    scaledImg = Scalr.rotate(scaledImg, Rotation.CW_90)
                    scaledImg = Scalr.rotate(scaledImg, Rotation.FLIP_HORZ)
                }
                6 // -PI/2 and -width
                -> scaledImg = Scalr.rotate(scaledImg, Rotation.CW_90)
                7 // PI/2 and Flip
                -> {
                    scaledImg = Scalr.rotate(scaledImg, Rotation.CW_90)
                    scaledImg = Scalr.rotate(scaledImg, Rotation.FLIP_VERT)
                }
                8 // PI / 2
                -> scaledImg = Scalr.rotate(scaledImg, Rotation.CW_270)
                else -> {
                }
            }
            // ---- End orientation handling ----

            // todo write to different folders instead of different names
//            ImageIO.write(scaledImg, "jpeg", File(Main.picFolder,"$photoName-${it.first}.jpg"))
            ImageIO.write(scaledImg, "jpeg", File(Main.picFolder+"/${it.first}","$photoName.jpg"))
        }
        temp.delete()
    }

    object Token {
        private val appTokenSecret = "\\x95d\\x9c\\xdc:\\xa8\\xb098\\x13|\\xfb\\xcb\\x86\\x00\\xde\\x83\\xb4\\xc1.\\xfdQ\\xf7{"

        private var algorithmHSAppToken = Algorithm.HMAC256(appTokenSecret)

        private var verifierAppToken = JWT.require(algorithmHSAppToken)
                .withIssuer("dancmc")
                .build() //Reusable verifier instance


        fun createAppToken(userID: String): String {
            return JWT.create()
                    .withIssuer("dancmc")
                    .withIssuedAt(Date(System.currentTimeMillis()))
                    .withAudience(userID)
                    .withExpiresAt(Date(System.currentTimeMillis() + 2629746000L * 12))
                    .sign(algorithmHSAppToken)
        }


        fun verifyAppToken(appToken: String): Any {

            try {
                val decodedAppToken =   verifierAppToken.verify(appToken)
                return decodedAppToken
            } catch (e: TokenExpiredException) {
                return "Expired token"
            } catch (e: Exception) {
                return "Failed to decode token"
            }

        }

    }
}

fun Request.decodeToken(): Any {

    val headerToken = this.headers("Authorization") ?: ""

    val decodedAppToken = Utils.Token.verifyAppToken(headerToken)

    when {
        decodedAppToken is String -> return JSONObject().fail(message = decodedAppToken)
        else -> return decodedAppToken
    }
}

fun JSONObject.fail(code: Int = -1, message: String = ""): JSONObject {
    return this
            .put("success", false)
            .put("error_code", code)
            .put("error_message", message)
}

fun JSONObject.success(): JSONObject {
    return this.put("success", true)
}