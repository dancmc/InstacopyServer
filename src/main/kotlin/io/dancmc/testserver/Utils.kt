package io.dancmc.testserver

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.TokenExpiredException
import de.mkammerer.argon2.Argon2Factory
import org.json.JSONObject
import spark.Request
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.*

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
        return "${Main.domain}/photos?size=$size&id=$photoID"
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