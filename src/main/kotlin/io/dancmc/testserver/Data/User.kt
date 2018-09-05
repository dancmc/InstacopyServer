package io.dancmc.testserver.Data

data class User(
        val userID:String,
        val username:String,
        val passwordHash:String,
        val email:String,
        val emailVerified:Boolean,
        val firstName:String,
        val lastName:String,
        val active:Boolean,
        val isBot:Boolean,
        var displayName:String,
        var profileName:String,
        var profileDesc:String,
        val isPrivate:Boolean,
        val facebookToken:String=""

) {



}