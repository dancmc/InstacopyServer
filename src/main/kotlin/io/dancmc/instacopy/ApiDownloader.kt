package io.dancmc.instacopy

import io.dancmc.instacopy.Data.User
import kotlinx.coroutines.*
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
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.HashMap
import kotlin.collections.HashSet


class ApiDownloader {

    companion object {

        val folder = File("/users/daniel/downloads/unsplash")

        val unsplashApiUrl = "https://api.unsplash.com/"
        val randomUserApiUrl = "https://randomuser.me/api/"
        val uiFacesApiUrl = "https://uifaces.co/"
        val accessKey1 = "9abdbf4401257ac584bdac93b24b3ba0950bb116bf99d177e5c2539005cdd9c5"
        val accessKey2 = "fbf32cd39e5ecd8039d57ff9e81b76ef121ccb55f78f804495fbef764ba09f59"
        val accessKey3 = "38611c81b9fafa8ed47046c39f739f22a48cb02d33ee605bf4dab5dd37edb9b4"
        val accessKey4 = "7fdbec9aa98f872376bc6203eb41b067c7268e4e5e33e4b48211cd97a4c07e10"
        val accessKey5 = "c7b4938a0fae7f1f2d87d00ba9bdfc4c7885c0fa86b346b2c59d9c323b11ac0b"

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


        val randomUserRetrofit = Retrofit.Builder()
                .client(httpClient1)
                .baseUrl(randomUserApiUrl)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        val uiFacesRetrofit = Retrofit.Builder()
                .client(httpClient1)
                .baseUrl(uiFacesApiUrl)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        val unsplashApi = unsplashRetrofit.create(UnsplashApi::class.java)
        val randomUserApi = randomUserRetrofit.create(RandomUserApi::class.java)
        val uiFacesApi = uiFacesRetrofit.create(UiFacesApi::class.java)

        // This doesn't really have to be a suspending function, the delay can probably just as well be a Thread.sleep since only using one thread
        suspend fun getUnsplashUrls(accessKey: String, intRange: IntRange) {

            withContext(Dispatchers.IO) {
                val thumbWriter = FileWriter(File(folder, "thumb.txt"), true)
                val smallWriter = FileWriter(File(folder, "small.txt"), true)
                val regularWriter = FileWriter(File(folder, "regular.txt"), true)

                // Sequential request for each page of curated Unsplash url results desired
                intRange.forEach { i ->

                    // Add query parameters and make request
                    val queries = hashMapOf(
                            Pair("page", i.toString()),
                            Pair("per_page", "30"),
                            Pair("order_by", "latest"))
                    val responseArray = JSONArray(unsplashApi.getCurated(accessToken = "Client-ID $accessKey", queries = queries).execute().body())

                    // Extract urls from json response
                    for (j in 0 until responseArray.length()) {
                        val photoObject = responseArray.getJSONObject(j)

                        val id = photoObject.getString("id")
                        val urls = photoObject.getJSONObject("urls")
                        val thumb = urls.getString("thumb")
                        val small = urls.getString("small")
                        val regular = urls.getString("regular")

                        // Write urls in tab delimited format id, url
                        thumbWriter.appendln("$id\t$thumb")
                        smallWriter.appendln("$id\t$small")
                        regularWriter.appendln("$id\t$regular")
                    }
                    delay(500)
                }

                thumbWriter.close()
                smallWriter.close()
                regularWriter.close()
            }

        }

        // also really doesn't have to be suspending
        suspend fun checkUnsplashUrls(): Boolean {
            return withContext(Dispatchers.IO) {

                val thumbSet = HashMap<String, String>()
                val smallSet = HashMap<String, String>()
                val regularSet = HashMap<String, String>()

                listOf("thumb", "small", "regular")
                        .zip(listOf(thumbSet, smallSet, regularSet))
                        .forEach {
                            FileReader(File(folder, "${it.first}.txt")).use { r ->
                                r.forEachLine { l ->
                                    it.second.put(l.split("\t")[0], l)
                                }
                            }
                        }

                println("${thumbSet.size}, ${smallSet.size}, ${regularSet.size}")

                // validate that all lists contain same IDs
                val allSame = thumbSet.asSequence().all { (imageName, _) ->
                    smallSet.containsKey(imageName) && regularSet.containsKey(imageName)
                }

                // if all lists are same size and contain same IDs, overwrite original lists to remove duplicates
                if (thumbSet.size == smallSet.size && smallSet.size == regularSet.size && allSame) {
                    listOf("thumb", "small", "regular")
                            .zip(listOf(thumbSet, smallSet, regularSet))
                            .forEach {
                                val urlFile = File(folder, "${it.first}.txt")
                                urlFile.delete()
                                FileWriter(urlFile).use { w ->
                                    it.second.forEach { (_, v) ->
                                        w.appendln(v)
                                    }
                                }
                            }
                }

                allSame
            }
        }


        fun getPictures(size: String) {

            val jobs = mutableListOf<Job>()
            val counter = AtomicInteger()

            // to filter out photo duplicates with different urls
            val urlHashMap = HashMap<String, String>()

            FileReader(File(folder, "$size.txt")).use { r ->
                r.forEachLine { l ->
                    val idAndUrl = l.split("\t")
                    urlHashMap[idAndUrl[0]] = idAndUrl[1]
                }
            }

            urlHashMap.forEach { (id, url) ->
                jobs += GlobalScope.launch(Dispatchers.IO) {
                    processDownload(size, id, url)
                    println("${counter.incrementAndGet()} of ${jobs.size}")
                }
            }


        }


        private fun processDownload(size: String, id: String, url: String) {

            try {

                val folder = File("/users/daniel/downloads/unsplash/$size")
                if (!folder.exists()) {
                    folder.mkdirs()
                }

                val file = File(folder, "$id.jpg")
                if (!file.exists()) {
                    file.createNewFile()
                    URL(url).openStream().use { `in` -> Files.copy(`in`, file.toPath(), StandardCopyOption.REPLACE_EXISTING) }

                    // this is intentionally to rate limit downloads
                    Thread.sleep(100)
                }
            } catch (e: Exception) {
                println(e.message)
                println(id)
            }
        }

        fun saveUsersFromApi(numberWanted: Int, gender: String) {
            val jsonArrayString = JSONObject(randomUserApi.getUsers(numberWanted, gender, gender).execute().body())
                    .getJSONArray("results")
                    .toString()
            FileWriter(File(folder, "${gender}_users.txt")).use { it.write(jsonArrayString) }
        }

        // read json arrays from male and female user files, transform to user objects
        // shuffle by gender, filter duplicates, take number required, and hash the passwords
        fun getUsersFromFile(numberWanted: Int): List<User> {
            val rng = Random()
            val maleJsonArray = JSONArray(FileReader(File(folder, "male_users.txt")).use { it.readText() })
            val femaleJsonArray = JSONArray(FileReader(File(folder, "female_users.txt")).use { it.readText() })
            println("${maleJsonArray.length() + femaleJsonArray.length()} users to process")

            val users = ArrayList<User>()
            listOf(maleJsonArray, femaleJsonArray).zip(listOf(User.Gender.MALE, User.Gender.FEMALE))
                    .forEach { (array, gender) ->
                        for (i in 0 until array.length()) {
                            val userObject = array.getJSONObject(i)
                            val loginData = userObject.getJSONObject("login")
                            val userID = loginData.getString("uuid")
                            val username = loginData.getString("username")
                            val passwordHash = loginData.getString("password")
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


                            users.add(User(username = username, userID = userID, gender = gender, passwordHash = passwordHash, email = email, emailVerified = emailVerified,
                                    firstName = firstName, lastName = lastName, active = active, isBot = isBot, displayName = displayName, profileName = profileName,
                                    profileDesc = profileDesc, isPrivate = isPrivate))
                        }
                    }
            users.shuffle()
            val uniqueUsers = users
                    .asSequence()
                    .distinctBy { it.username }
                    .distinctBy { it.email }
                    .take(numberWanted)
                    .toList()

            val usersHashed = AtomicInteger()
            runBlocking {
                uniqueUsers.forEach { u ->
                    launch(Dispatchers.Default) {
                        u.passwordHash = Utils.Password.hashPassword(u.passwordHash)
                        val currentCount = usersHashed.incrementAndGet()
                        if (currentCount % 100 == 0) {
                            println("$currentCount users processed")
                        }
                    }
                }
            }

            return uniqueUsers
        }


        fun getProfilePicUrls(): HashMap<String, String> {

            val profilesToFetch = Utils.loadTSVLinked(File(folder, "profile_pics.txt").absolutePath)

            val result = HashMap<String, String>()

            val males = HashSet<String>()
            val females = HashSet<String>()
            val neutrals = HashSet<String>()

            var facesArray = JSONArray(uiFacesApi.getFaces(1000, "male").execute().body())
            for (i in 0 until facesArray.length()) {
                males.add(facesArray.getJSONObject(i).getString("photo"))
            }
            facesArray = JSONArray(uiFacesApi.getFaces(1000, "female").execute().body())
            for (i in 0 until facesArray.length()) {
                females.add(facesArray.getJSONObject(i).getString("photo"))
            }
            FileReader(File(folder, "thumb.txt")).useLines { r ->
                neutrals.addAll(r.take(2000).map { it.split("\t")[1] }.toList())
            }

            val malesLinkedList = LinkedList(males)
            val femalesLinkedList = LinkedList(females)
            val neutralsLinkedList = LinkedList(neutrals)

            profilesToFetch.forEach { p ->
                when {
                    p[1] == "male" && malesLinkedList.isNotEmpty() -> result[p[0]] = malesLinkedList.pop()
                    p[1] == "female" && femalesLinkedList.isNotEmpty() -> result[p[0]] = femalesLinkedList.pop()
                    else -> result[p[0]] = neutralsLinkedList.pop()
                }
            }

            return result
        }

        fun fetchProfilePhotos(photos: HashMap<String, String>) {

            runBlocking {
                File(folder, "profile").apply {
                    if (!this.exists()) {
                        this.mkdirs()
                    }
                }

                withContext(Dispatchers.IO) {
                    photos.forEach { (id, url) ->
                        launch {
                            processProfileDownload(id, url)
                        }
                    }
                }
                println("All profile photos fetched")
            }

        }

        private fun processProfileDownload(id: String, url: String) {
            try {

                val profileFile = File(folder, "profile/$id.jpg")
                if (!profileFile.exists()) {
                    profileFile.createNewFile()
                    URL(url).openStream().use { `in` -> Files.copy(`in`, profileFile.toPath(), StandardCopyOption.REPLACE_EXISTING) }
                    Thread.sleep(20)
                }
            } catch (e: Exception) {
                println(e.message)
                println(url)
            }
        }

        suspend fun getQuotes() {

            val inputFile = File(folder, "quotes.json")
            val outputFile = File(folder, "quotes.txt")

            // compiler complains if using an IO call in a general suspend method, so define IO context explicitly
            // note that this does not force a context/thread change, indeed Dispatchers.IO notes that if called from a
            // Dispatchers.Default context, will likely not cause any thread shift
            withContext(Dispatchers.IO) {
                val quoteSet = HashSet<String>()

                FileReader(inputFile).use { r ->
                    JSONArray(r.readText()).forEach { o ->
                        o as JSONObject
                        quoteSet.add("${o.getString("Quote")}\t${o.getString("Author")}\n")
                    }
                }

                FileWriter(outputFile, true).use { w ->
                    quoteSet.forEach { q -> w.write(q) }
                }

            }
        }

        fun processLocations() {
            FileReader(File(folder, "worldcities.csv")).use { r ->
                val writer = FileWriter(File(folder, "locations.txt"), true)

                // US cities make up disproportionate amount, cut out random portion of them
                val cities = ArrayList<String>()
                val usCities = ArrayList<String>()

                r.forEachLine { l ->
                    val parts = l.split(",")
                    val country = parts[4].removeSurrounding("\"")
                    val entry = "${parts[2].removeSurrounding("\"")}\t" +
                            "${parts[3].removeSurrounding("\"")}\t" +
                            "${parts[0].removeSurrounding("\"")}, $country"
                    if (country == "United States") {
                        usCities.add(entry)
                    } else {
                        cities.add(entry)
                    }
                }
                cities.removeAt(0) // remove the header row
                cities.addAll(usCities.shuffled().asSequence().take(2000))
                cities.shuffled().forEach { c -> writer.appendln(c) }

                writer.close()
            }
        }
    }


    interface UnsplashApi {

        @GET("/photos")
        fun getCurated(
                @Header("Authorization") accessToken: String,
                @QueryMap queries: HashMap<String, String>): Call<String>


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