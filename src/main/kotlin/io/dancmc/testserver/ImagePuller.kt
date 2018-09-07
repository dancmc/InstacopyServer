package io.dancmc.testserver

import io.dancmc.testserver.Data.User
import kotlinx.coroutines.experimental.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.QueryMap
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*


class ImagePuller {

    companion object {

        val unsplashApiUrl = "https://api.unsplash.com/"
        val quoteApiUrl = "https://talaikis.com/"
        val randomUserApiUrl = "https://randomuser.me/api/"
        val uiFacesApiUrl = "https://uifaces.co/"
        val accessKey1 = "9abdbf4401257ac584bdac93b24b3ba0950bb116bf99d177e5c2539005cdd9c5"
        val accessKey2 = "fbf32cd39e5ecd8039d57ff9e81b76ef121ccb55f78f804495fbef764ba09f59"
        val accessKey3 = "38611c81b9fafa8ed47046c39f739f22a48cb02d33ee605bf4dab5dd37edb9b4"

        val httpClient1 = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    val request = chain.request()
                    println("HTTP REQUEST : ${request.url()}")

                    return@Interceptor chain.proceed(request)
                }).build()

        val unsplashRetrofit = Retrofit.Builder()
                .client(httpClient1)
                .baseUrl(unsplashApiUrl)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        val quoteRetrofit = Retrofit.Builder()
                .client(SSLHack.getUnsafeOkHttpClient())
                .baseUrl(quoteApiUrl)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        val randomUserRetrofit = Retrofit.Builder()
                .client(httpClient1)
                .baseUrl(randomUserApiUrl)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        val uiFacesRetrofit = Retrofit.Builder()
                .client(SSLHack.getUnsafeOkHttpClient())
                .baseUrl(uiFacesApiUrl)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        val unsplashApi = unsplashRetrofit.create(UnsplashApi::class.java)
        val quoteApi = quoteRetrofit.create(QuoteApi::class.java)
        val randomUserApi = randomUserRetrofit.create(RandomUserApi::class.java)
        val uiFacesApi = uiFacesRetrofit.create(UiFacesApi::class.java)

        fun getUrls(accessKey: String) {

            FileWriter(File("/users/daniel/downloads/unsplash/thumb-v3.txt"), true).use { thumbWriter ->
                FileWriter(File("/users/daniel/downloads/unsplash/small-v3.txt"), true).use { smallWriter ->
                    FileWriter(File("/users/daniel/downloads/unsplash/regular-v3.txt"), true).use { regularWriter ->
                        runBlocking {
                            for (i in 1..50) {

                                val queries = hashMapOf(
                                        Pair("page", i.toString()),
                                        Pair("per_page", "30"),
                                        Pair("order_by", "latest"))


                                val array = JSONArray(unsplashApi.getCurated(accessToken = "Client-ID $accessKey", queries = queries).execute().body())

                                for (j in 0 until array.length()) {
                                    val photoObject = array.getJSONObject(j)
                                    val urls = photoObject.getJSONObject("urls")

                                    val id = photoObject.getString("id")
                                    val thumb = urls.getString("thumb")
                                    val small = urls.getString("small")
                                    val regular = urls.getString("regular")
                                    thumbWriter.appendln("$id\t$thumb")
                                    smallWriter.appendln("$id\t$small")
                                    regularWriter.appendln("$id\t$regular")
                                }

                                delay(2000)
                            }
                        }

                    }
                }
            }

        }


        fun getPictures(size: String) {
            val jobs = mutableListOf<Job>()

            try {
                BufferedReader(FileReader(File("/users/daniel/downloads/unsplash/$size.txt"))).use { br ->
                    var line = br.readLine()

                    while (line != null) {
                        val components = line.split("\t")
                        jobs += launch(CommonPool) {
                            processDownload(size, components)
                        }

                        line = br.readLine()
                    }
                }
            } catch (e: Exception) {
                println(e.message)
            }

            runBlocking { jobs.forEach { it.join() } }

        }


        suspend fun processDownload(size: String, components: List<String>) {

            try {
                val id = components[0]
                val url = components[1]
                //                            val tempFile = Files.createTempFile(File("/users/daniel/downloads/unsplash/$size").toPath(), "", "")
                val tempFile = File("/users/daniel/downloads/unsplash/$size/$id.jpg")
                if (!tempFile.exists()) {
                    tempFile.createNewFile()
                    URL(url).openStream().use({ `in` -> Files.copy(`in`, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING) })
                    delay(100)
                }
            } catch (e: Exception) {
                println(e.message)
                println(components[0])
            }
        }


        fun getQuotes(): HashSet<String> {
            val set = HashSet<String>()
            val quotes = JSONArray(quoteApi.getQuotes().execute().body())
            for (i in 0 until quotes.length()) {
                val quoteObject = quotes.getJSONObject(i)
                val quote = quoteObject.getString("quote")
                set.add(quote)
            }
            return set

        }

        fun getUsers(numberWanted: Int, gender: String, useFile:Boolean): ArrayList<User> {
            val rng = Random()
            val users = ArrayList<User>()
            val getJSON = {
                val lines = Utils.loadFile("/users/daniel/downloads/unsplash/${gender}_users.txt")
                 JSONArray(lines.joinToString(""))
            }
            val result = if(useFile) getJSON()  else JSONObject(randomUserApi.getUsers(numberWanted, gender, "abcde").execute().body()).getJSONArray("results")
            var count = 0

            try {
                for (i in 0 until result.length()) {
                    val userObject = result.getJSONObject(i)
                    val loginData = userObject.getJSONObject("login")
                    val userID = loginData.getString("uuid")
                    val username = loginData.getString("username")
                    val passwordHash = Utils.Password.hashPassword(loginData.getString("password"))
//                    val passwordHash = loginData.getString("password")
                    val email = userObject.getString("email")
                    val emailVerified = true
                    val nameData = userObject.getJSONObject("name")
                    val firstName = nameData.getString("first")
                    val lastName = nameData.getString("last")
                    val active = true
                    val isBot = true
                    val displayName = username
                    val profileName = "$firstName $lastName"
                    val profileDesc = ""
                    val isPrivate = rng.nextDouble() <= 0.25


                    users.add(User(username = username, userID = userID, passwordHash = passwordHash, email = email, emailVerified = emailVerified,
                            firstName = firstName, lastName = lastName, active = active, isBot = isBot, displayName = displayName, profileName = profileName,
                            profileDesc = profileDesc, isPrivate = isPrivate))
                    count++
                    if(count%100==0){
                        println("$count users processed")
                    }
                }
            } catch (e: Exception) {
                println(e.message)
            }


            return users
        }

        fun getFaces(gender:String, limit:Int):ArrayList<String>{
            val result = ArrayList<String>()
            val facesArray = JSONArray(uiFacesApi.getFaces(limit, gender).execute().body())
            for(i in 0 until facesArray.length()){
                result.add(facesArray.getJSONObject(i).getString("photo"))
            }
            return result
        }

        fun fetchProfilePhotos() {
            val jobs = mutableListOf<Job>()

            val malePics = Utils.loadTSV("/users/daniel/downloads/unsplash/profiles_male_v2.txt")
            malePics.forEachIndexed {index, url->
                jobs += launch(CommonPool) {
                    processProfileDownload("male", url[0], index)
                }
            }
            val femalePics = Utils.loadTSV("/users/daniel/downloads/unsplash/profiles_female_v2.txt")
            femalePics.forEachIndexed {index, url->
                jobs += launch(CommonPool) {
                    processProfileDownload("female", url[0], index)
                }
            }
            runBlocking { jobs.forEach { it.join() } }

        }



        suspend fun processProfileDownload(gender:String, url: String, name:Int) {
            try {

                val tempFile = File("/users/daniel/downloads/unsplash/${gender}_pics/$name.jpg")
                if (!tempFile.exists()) {
                    tempFile.createNewFile()
                    URL(url).openStream().use({ `in` -> Files.copy(`in`, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING) })
                    delay(20)
                }
            } catch (e: Exception) {
                println(e.message)
                println(url)
            }
        }


    }


    interface UnsplashApi {

        @GET("/photos")
        fun getCurated(
                @Header("Authorization") accessToken: String,
                @QueryMap queries: HashMap<String, String>): Call<String>


    }

    interface QuoteApi {

        @GET("/api/quotes")
        fun getQuotes(): Call<String>

    }

    interface RandomUserApi {

        @GET(" ")
        fun getUsers(@Query("results") numberWanted: Int, @Query("gender") gender: String, @Query("seed") seed: String): Call<String>

    }

    interface UiFacesApi {

        @GET("/api")
        fun getFaces(@Query("limit") limit: Int, @Query("gender[]") gender: String, @Header("X-API-KEY") header: String = "d4d98cf55440043759b3920249e5d7"): Call<String>

    }
}