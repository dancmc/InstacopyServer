package io.dancmc.instacopy

class Test {

    companion object {
        fun execute() {
            ImagePuller.getUrls(ImagePuller.accessKey1)
//            ImagePuller.getPictures("thumb-v2")
//            println("done")
//            DataLoader.createBuckets(30000,2000)


            val quoteSet = HashSet<String>()
//            val jobs = ArrayList<Deferred<HashSet<String>>>()
//            for(i in 1..300){
//                jobs+= async(CommonPool){
//                    return@async ImagePuller.getQuotes()
//                }
//
//            }
//            runBlocking {
//                jobs.forEach {
//                    try {
//                        quoteSet.addAll(it.await())
//                    }catch (e:Exception){
//                        println(e.message)
//                    } }
//                FileWriter(File("/users/daniel/downloads/unsplash/quotes.txt"), true).use { thumbWriter ->
//                    quoteSet.forEach {
//                        thumbWriter.appendln(it)
//                    }
//                }
//            }
//            println("done")


//
//            FileWriter(File("/users/daniel/downloads/unsplash/quotes.txt"), true).use { thumbWriter ->
//
//                while (quoteSet.size < 40000) {
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

//            ImagePuller.getFaces("male",100)


//            val tx = Database.graphDb.beginTx()
//                try {
//            val maleHash = HashSet<String>()
//                val males = ImagePuller.getUsers(1000,"male", true)
//                males.forEach {
//                    if(maleHash.contains(it.email)){
//                        println(it.email)
//                    }
//                    maleHash.add(it.email) }
//
//                val femaleHash = HashSet<String>()
//                val females = ImagePuller.getUsers(1000,"female", true)
//                females.forEach {
//                    if(maleHash.contains(it.displayName)){
//                        println(it.displayName)
//                    }
//                    maleHash.add(it.displayName) }
//
//                println(maleHash.size)
//                println(femaleHash.size)

//                } catch (e: Exception) {
//
//                    println(e.message)
//                } finally {
//                    tx.success()
//                    tx.close()
//                }


            //            var text = ""
//            try {
//                BufferedReader(FileReader(File("/users/daniel/downloads/unsplash/profiles_male.txt"))).use { br ->
//                    var line = br.readLine()
//
//                    while (line != null) {
//
//                        text+=line
//                        line = br.readLine()
//                    }
//
//                }
//
//            } catch (e: Exception) {
//                println(e.message)
//            }
//            val maleArray = JSONArray(text)
//            val malePics = ArrayList<String>()
//            for (i in 0 until maleArray.length()){
//                malePics.add(maleArray.getJSONObject(i).getString("photo"))
//
//            }
//            text = ""
//            try {
//                BufferedReader(FileReader(File("/users/daniel/downloads/unsplash/profiles_female.txt"))).use { br ->
//                    var line = br.readLine()
//
//                    while (line != null) {
//
//                        text+=line
//                        line = br.readLine()
//                    }
//
//                }
//
//            } catch (e: Exception) {
//                println(e.message)
//            }
//            val femaleArray = JSONArray(text)
//            val femalePics = ArrayList<String>()
//            for (i in 0 until femaleArray.length()){
//                femalePics.add(femaleArray.getJSONObject(i).getString("photo"))
//
//            }
//            val randomPics = Utils.loadTSVLinked("/users/daniel/downloads/unsplash/thumb-v3.txt")
//            val maleRemaining = 1000-malePics.size
//            for (i in 1..maleRemaining){
//                malePics.add(randomPics.pop()[1])
//            }
//            val femaleRemaining = 1000-femalePics.size
//            for (i in 1..femaleRemaining){
//                femalePics.add(randomPics.pop()[1])
//            }
//            println(malePics.size)
//            println(femalePics.size)


//            FileWriter(File("/users/daniel/downloads/unsplash/profiles_male_v2.txt"), true).use { thumbWriter ->
//                malePics.forEach { thumbWriter.appendln(it) }
//            }
//            FileWriter(File("/users/daniel/downloads/unsplash/profiles_female_v2.txt"), true).use { thumbWriter ->
//                femalePics.forEach { thumbWriter.appendln(it) }
//            }
//            println("here")


//            Search executes very fast, on order of less than 10 ms
//            runBlocking {
//                while (!Database.initialised){
//                    delay(1000)
//                }
//                val a = System.currentTimeMillis()
//                val lat = Database.executeTransaction("Test") {
//                    val n  = it.findNode(Label{"Photo" }, "photo_id", "lEVVHMY50wM")
//                    return@executeTransaction n.getProperty("latitude")
//                } as Double
//                val b = System.currentTimeMillis() - a
//                println("$b, $lat")
//                val long  = Database.executeTransaction("Test") {
//                    val n = it.findNode(Label{"Photo" }, "photo_id", "AbdhYqN8nCs")
//                    return@executeTransaction n.getProperty("longitude")
//                } as Double
//                val c = System.currentTimeMillis() - b - a
//                println("$c, $long")
//            }
        }

    }
}