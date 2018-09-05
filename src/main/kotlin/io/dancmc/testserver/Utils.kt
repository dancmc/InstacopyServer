package io.dancmc.testserver

import de.mkammerer.argon2.Argon2Factory
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter

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

    fun write(file:String, lines:ArrayList<String>){
        FileWriter(File(file), true).use { writer ->
            lines.forEach {
                writer.write(it)
            }
        }
    }
}