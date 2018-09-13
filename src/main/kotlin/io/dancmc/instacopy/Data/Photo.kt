package io.dancmc.instacopy.Data

data class Photo(
        val photo_id:String,
        var caption:String,
        val timestamp:Long,
        var latitude:Double,
        var longitude:Double,
        var location_name:String=""
) {
}