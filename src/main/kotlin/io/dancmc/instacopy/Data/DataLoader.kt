package io.dancmc.instacopy.Data

import com.javadocmd.simplelatlng.LatLng
import io.dancmc.instacopy.ImagePuller
import io.dancmc.instacopy.Utils
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class DataLoader {

    companion object {
        val rng = Random()
        val start2013 = 1356958800000
        val start2017Sep = 1505829600000
        val twoMonthsInMs = 1000L*60*60*24*60

        fun execute() {

            val maleUserList = ImagePuller.getUsers(5000, "male",true)
            val femaleUserList = ImagePuller.getUsers(5000, "female",true)
            val locations = Utils.loadTSVLinked("/users/daniel/downloads/unsplash/locations.txt")
            val quoteList = Utils.loadTSVLinked("/users/daniel/downloads/unsplash/quotes.txt")
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
            if(thumbHashMap.size!=smallHashMap.size || thumbHashMap.size!=regularHashMap.size){
                throw Exception("mismatched sizes")
            }

            // deal with photos
            val photoList = ArrayList<Photo>()
            val photoTsvToWrite = ArrayList<String>()


            thumbHashMap.forEach { id, thumbUrl ->

                if(photoList.size>=30000){
                    return@forEach
                }

                val thumbFile = File("/users/daniel/downloads/unsplash/thumb/$id.jpg")
                if(!thumbFile.exists()){
                    return@forEach
                }
                val thumbDimen = Utils.readDimensions(thumbFile)
                val smallFile = File("/users/daniel/downloads/unsplash/small/$id.jpg")
                val smallDimen = Utils.readDimensions(smallFile)
                val regularFile = File("/users/daniel/downloads/unsplash/regular/$id.jpg")
                val regularDimen = Utils.readDimensions(regularFile)

                val photoID = id
                val caption = quoteList.pop()[0]
                val timeStamp = getRandomTimestamp(start2013, System.currentTimeMillis())
                val location = if (rng.nextDouble()<0.33 && locations.size>0) locations.pop() else null
                val locationName = location?.get(2)?:""
                val latlong = LatLng.random()
                val latitude = location?.get(0)?.toDouble()?:latlong.latitude
                val longitude = location?.get(1)?.toDouble()?:latlong.longitude

                val smallUrl = smallHashMap[id]
                val regularUrl = smallHashMap[id]
                photoList.add(Photo(photo_id = photoID, caption = caption, timestamp = timeStamp, location_name = locationName,
                        longitude = longitude, latitude = latitude, thumbSize = thumbDimen, smallSize = smallDimen, regularSize = regularDimen))
                photoTsvToWrite.add("$id\t$caption\t$timeStamp\t$locationName\t$latitude\t$longitude\t$thumbUrl\t$smallUrl\t$regularUrl")
            }
            Utils.write(unifiedPhotoTsv, photoTsvToWrite)

            // deal with users
            val userList = ArrayList<User>()
            val profilePicList = ArrayList<String>()
            for(i in 0 until 1000){
                val male = maleUserList[i]
                male.profileDesc = quoteList.pop()[0]
                userList.add(male)
                profilePicList.add("male\t${male.userID}\t${i+1}")

                val female = femaleUserList[i]
                female.profileDesc = quoteList.pop()[0]
                userList.add(female)
                profilePicList.add("female\t${female.userID}\t${i+1}")
            }
            Utils.write(profilePhotoTsv,profilePicList)

            // deal with users posting photos
            val userUploadMaps = HashMap<String, HashMap<String, Long>>()
            val photoToUserMap = HashMap<String, String>()
            var uploadCount = 0
            val uploadDistribution = createBuckets(30000,2000)
            uploadDistribution.forEachIndexed { index, i ->
                val userUploadMap = HashMap<String,Long>()
                for (j in uploadCount until uploadCount+i){
                    userUploadMap.put(photoList[j].photo_id,photoList[j].timestamp)
                    photoToUserMap.put(photoList[j].photo_id, userList[index].userID)
                }
                userUploadMaps.put(userList[index].userID, userUploadMap)
                uploadCount+=i
            }

            // generate follows
            val followDistribution = createRightSkew(2000)
            val userFollowMaps = HashMap<String, HashMap<String,Long>>()
            followDistribution.forEachIndexed { index, num->
                val followMap = HashMap<String,Long>()
                val probability = num/2000.0
                val sourceID = userList[index].userID
                userList.forEach { target->
                    if(rng.nextDouble()<=probability && target.userID!= sourceID){
                        followMap.put(target.userID, getRandomTimestamp(start2013, System.currentTimeMillis()))
                    }
                }
                userFollowMaps.put(sourceID, followMap)
            }

            // generate likes & comments
            val userMap = HashMap<String,User>()
            val photoMap = HashMap<String,Photo>()
            userList.forEach { userMap.put(it.userID, it) }
            photoList.forEach { photoMap.put(it.photo_id, it) }

            var totalComments = 0
            var wantedComments = 0
            var totalLikes = 0
            val userLikeMap=HashMap<String, HashMap<String, Long>>()
            val userCommentMap=HashMap<String, HashMap<String, Pair<String, Long>>>()
            userList.forEach { user->
                val likeMap = HashMap<String, Long>()
                val commentMap = HashMap<String, Pair<String, Long>>()
                photoList.forEach { photo->
                    val like = getChanceOfLike(user.userID, photoToUserMap, photo.photo_id, userMap, userFollowMaps,photoMap)
                    if(rng.nextDouble()<like.first){
                        likeMap.put(photo.photo_id, like.second)
                        totalLikes++
                        if(rng.nextDouble()<0.0170){
                            if(quoteList.isNotEmpty()) {
                                commentMap.put(photo.photo_id, Pair(quoteList.pop()[0], like.second + 5000))
                                totalComments++
                            }
                            wantedComments++
                        }
                    }
                }
                userLikeMap.put(user.userID, likeMap)
                userCommentMap.put(user.userID, commentMap)
            }



            // add users to database
            Database.addUsers(userList)
            Database.addPhotos(photoList)
            Database.addFollows(userFollowMaps)
            Database.addLikes(userLikeMap)
            Database.addComments(userCommentMap)
            Database.addPosted(userUploadMaps)

            println("Total Likes : $totalLikes")
            println("Total Comments : $totalComments")
            println("Wanted Comments : $wantedComments")
            println("Success!")





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

        fun createRightSkew(totalBuckets:Int):ArrayList<Int>{
            val buckets = ArrayList<Int>(totalBuckets)

            for(i in 1..totalBuckets) {
//                val bucket1 = Math.ceil(Math.max(0.0, (sd * rng.nextGaussian()) + mean)).toInt()
//                val bucket2 = Math.ceil(Math.max(0.0, (sd * rng.nextGaussian()) + mean)).toInt()
//                val bucket = Math.min(bucket1, bucket2)
                var bucket = 1000

                while (bucket>850) {
                    bucket = (inverseGaussian(1.0, 2.0) * 200).roundToInt()
                }
                buckets.add(bucket)
            }


            return buckets
        }

        fun getChanceOfLike(userSourceID:String, photoToUserMap:HashMap<String,String>,photoID:String,
                            userMap:HashMap<String, User>, userFollowMaps:HashMap<String, HashMap<String,Long>>,
                            photoMap:HashMap<String,Photo>):Pair<Double, Long>{
            var baseChance = 0.008
            var increasedChance = 0.4
            val photo = photoMap[photoID]!!
            val userTargetID = photoToUserMap[photo.photo_id]
            val userTarget = userMap[userTargetID]!!

            val sourceFollowsTarget = userTargetID in userFollowMaps[userSourceID]!!
            val followDate = userFollowMaps[userSourceID]?.get(userTargetID)?:-1L

            /**
             * Scenarios :
             * - target is public, user does not follow target : 0.02
             * - target is public, user follows target, upload less than 2 mths before follow date : 0.3
             * - target is public, user follows target, upload more than 2 mths before follow date : 0.02
             * - target is private, user does not follow target : 0
             * - target is private, user follows target, upload less than 2 mths before follow date : 0.3
             */


            if(!userTarget.isPrivate){
                if(sourceFollowsTarget && followDate-photo.timestamp< twoMonthsInMs){
                    return Pair(increasedChance, getRandomTimestamp(Math.max(followDate,photo.timestamp), photo.timestamp+ twoMonthsInMs))
                } else {
                    return Pair(baseChance, getRandomTimestamp(photo.timestamp, photo.timestamp+ twoMonthsInMs))
                }
            }else {
                if(sourceFollowsTarget && followDate-photo.timestamp< twoMonthsInMs){
                    return Pair(increasedChance, getRandomTimestamp(Math.max(followDate,photo.timestamp), photo.timestamp+ twoMonthsInMs))
                } else {
                    return Pair(0.0, -1)
                }
            }
        }

        fun getRandomTimestamp(start:Long, end:Long):Long{
            return ((Math.min(end,System.currentTimeMillis())-start)*rng.nextDouble()).roundToLong()+start
        }

        fun inverseGaussian(mu:Double, lambda:Double):Double{
            val rng = Random()
            val v  = rng.nextGaussian()
            val y = v*v
            val x = mu+(mu*mu*y)/(2*lambda)-(mu/(2*lambda))*Math.sqrt(4*mu*lambda*y+mu*mu*y*y)
            val test = rng.nextDouble()
            if(test<=(mu)/(mu+x)) {
                return x
            } else{
                return (mu*mu)/x
            }

        }


    }
}