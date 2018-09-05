package io.dancmc.testserver.Data

import com.javadocmd.simplelatlng.LatLng
import io.dancmc.testserver.ImagePuller
import io.dancmc.testserver.Utils
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.roundToLong

class DataLoader {

    companion object {

        fun execute() {
            val rng = Random()
            val start2013 = 1356958800000
            val msTillNow =System.currentTimeMillis() - start2013
            val maleUserList = ImagePuller.getUsers(5000, "male")
            val maleFaces = ImagePuller.getFaces("male", 1000)
            val femaleUserList = ImagePuller.getUsers(5000, "female")
            val femaleFaces = ImagePuller.getFaces("female", 1000)
            val locations = ArrayList<String>()
            locations.addAll(maleUserList.second)
            locations.addAll(femaleUserList.second)
            val quoteList = Utils.loadTSV("/users/daniel/downloads/unsplash/quotes.txt")
            val unifiedPhotoTsv = "/users/daniel/downloads/unsplash/photos.txt"
            val profilePhotoTsv = "/users/daniel/downloads/unsplash/profile_pics.txt"

            val thumbPhotoList = Utils.loadTSV("/users/daniel/downloads/unsplash/thumb-v2.txt")
            val smallPhotoList = Utils.loadTSV("/users/daniel/downloads/unsplash/small-v2.txt")
            val regularPhotoList = Utils.loadTSV("/users/daniel/downloads/unsplash/regular-v2.txt")

            val thumbHashMap = HashMap<String, String>()
            thumbPhotoList.forEach { thumbHashMap.put(it[0], it[1]) }
            val smallHashMap = HashMap<String, String>()
            smallPhotoList.forEach { smallHashMap.put(it[0], it[1]) }
            val regularHashMap = HashMap<String, String>()
            regularPhotoList.forEach { regularHashMap.put(it[0], it[1]) }

            // deal with photos
            val photoList = ArrayList<Photo>()
            val photoTsvToWrite = ArrayList<String>()
            thumbHashMap.forEach { id, thumbUrl ->
                val photoID = id
                val caption = quoteList.removeAt(quoteList.lastIndex)[0]
                val timeStamp = (rng.nextDouble()*msTillNow).roundToLong()+start2013
                val locationName = if (rng.nextDouble()<0.33 && locations.size>0) locations.removeAt(locations.lastIndex) else ""
                val latlong = LatLng.random()
                val latitude = latlong.latitude
                val longitude = latlong.longitude

                val smallUrl = smallHashMap[id]
                val regularUrl = smallHashMap[id]
                photoList.add(Photo(photo_id = photoID, caption = caption, timestamp = timeStamp, location_name = locationName, longitude = longitude, latitude = latitude))
                photoTsvToWrite.add("$id\t$caption\t$timeStamp\t$locationName\t$latitude\t$longitude\t$thumbUrl\t$smallUrl\t$regularUrl")
            }
            Utils.write(unifiedPhotoTsv, photoTsvToWrite)

            // deal with users
            val userList = ArrayList<User>()
            val profilePicList = ArrayList<String>()
            for(i in 0 until 1000){
                val male = maleUserList.first[i]
                val malePic = maleFaces[i]
                userList.add(male)
                profilePicList.add("${male.userID}\t$malePic")
                val female = femaleUserList.first[i]
                val femalePic = femaleFaces[i]
                userList.add(female)
                profilePicList.add("${female.userID}\t$femalePic")
            }
            Utils.write(profilePhotoTsv,profilePicList)

            // deal with users posting photos
            val userUploadLists = ArrayList<Pair<String, ArrayList<String>>>()
            var uploadCount = 0
            val uploadDistribution = createBuckets(30000,2000)
            uploadDistribution.forEachIndexed { index, i ->
                val userUploadList = ArrayList<String>()
                for (j in uploadCount until uploadCount+i){
                    userUploadList.add(photoList[j].photo_id)
                }
                userUploadLists.add(Pair(userList[index].userID, userUploadList))
                uploadCount+=i
            }

            // generate follows

            


            // add users to database


            // add photos to database



            // add POSTED relationships





        }

        fun loadUsers() {

        }


        fun createBuckets(toDistribute: Int, totalBuckets: Int): ArrayList<Int> {

            val buckets = ArrayList<Int>(totalBuckets)
            val rng = Random()
            val mean = toDistribute / totalBuckets.toDouble() * 0.96
            val sd = mean / 3.0

            var total = 0

            while (total > toDistribute || total < Math.floor(toDistribute * 0.999)) {
                total = 0
                buckets.clear()
                for (i in 1..totalBuckets) {
                    val bucket = Math.ceil(Math.max(0.0, (sd * rng.nextGaussian()) + mean)).toInt()
                    buckets.add(bucket)
                    total += bucket
                }
            }

            while (total < toDistribute) {
                for (i in 0 until totalBuckets) {
                    if (total < toDistribute && rng.nextDouble() > 0.3) {
                        total++
                        buckets[i]++
                    }
                }
            }

            println(buckets.sum())
            return buckets

        }


    }
}