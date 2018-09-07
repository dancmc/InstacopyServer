package io.dancmc.testserver.Routes

import io.dancmc.testserver.Data.User
import io.dancmc.testserver.Utils
import io.dancmc.testserver.fail
import org.json.JSONObject
import spark.Route
import java.util.*

object UserRoutes {
    val register = Route { request, response ->
        val requestJson = JSONObject(request.body())
        val username = requestJson.optString("username", "")
        val password = requestJson.optString("password", "")
        val firstName = requestJson.optString("first_name", "")
        val lastName = requestJson.optString("last_name", "")
        val email = requestJson.optString("email", "")
        val displayName = requestJson.optString("display_name", "")
        if(username.isBlank() || password.isBlank()|| email.isBlank()||lastName.isBlank()||displayName.isBlank()){
            return@Route JSONObject().fail(-1, "Missing field")
        }

        val user = User(userID = UUID.randomUUID().toString(), username = username, passwordHash = Utils.Password.hashPassword(password),
                email = email, emailVerified = false, firstName = firstName, lastName = lastName, active = true,
                isBot = false, displayName = displayName, profileName = "", profileDesc = "", isPrivate = false)

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
    }

    val login = Route { request, response ->


    }

    val getInfo = Route { request, response ->


    }


    val getPhotos = Route { request, response ->


    }

    val follow = Route { request, response ->


    }

    val unfollow = Route { request, response ->


    }

    val approve = Route { request, response ->


    }

    val getFollowers = Route { request, response ->


    }

    val getFollowing = Route { request, response ->


    }

    val updateDetails = Route { request, response ->


    }
}