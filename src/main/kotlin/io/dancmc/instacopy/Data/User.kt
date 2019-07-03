package io.dancmc.instacopy.Data

data class User(
        var userID:String,
        val username:String,
        var passwordHash:String,
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
        val facebookToken:String="",
        val gender:Gender=Gender.UNSPECIFIED
) {

    enum class Gender{MALE{
        override fun toString(): String {
            return "male"
        }
    },FEMALE{
        override fun toString(): String {
            return "female"
        }
    },UNSPECIFIED}


}