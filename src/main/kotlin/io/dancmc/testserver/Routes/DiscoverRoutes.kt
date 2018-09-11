package io.dancmc.testserver.Routes

import io.dancmc.testserver.Data.Database
import io.dancmc.testserver.Utils
import io.dancmc.testserver.fail
import io.dancmc.testserver.success
import org.json.JSONArray
import org.json.JSONObject
import spark.Route
import java.util.HashMap

object DiscoverRoutes{

    fun searchUsersQuery(paging: Boolean, displayName: String, page: Int = 0): Pair<String, HashMap<String, Any>> {
        val params = hashMapOf<String, Any>()
        params.put("display_name", displayName)
        params.put("skip", (page-1)*20)
        return Pair("match (u:User)  with u,  apoc.text.jaroWinklerDistance(u.display_name,\$display_name) as distance " +
                "return u.display_name as display_name, u.user_id as user_id, u.profile_name as profile_name " +
                "order by distance desc, display_name asc ${if(paging)"skip \$skip" else ""} limit 20", params)
    }

    val search = Route { request, response ->
        val userID = request.attribute("user") as String
        val displayName = request.queryParamOrDefault("display_name", "")
        val pageNumber = request.queryParamOrDefault("page", "").toIntOrNull()
        val paging = request.queryParams().contains("page")


        if(displayName.isBlank() || paging && (pageNumber==null || pageNumber<1)){
            return@Route JSONObject().fail(message = "Incorrect parameters")
        }

        Database.executeTransaction {
            val query = searchUsersQuery(paging, displayName, pageNumber ?:1)
            val results = it.execute(query.first, query.second)

            val jsonArray = JSONArray()
            Database.processResult(results) {
                val userObject = JSONObject()
                userObject.put("display_name", it["display_name"] as String)
                userObject.put("profile_name", it["profile_name"] as String)
                userObject.put("profile_image", Utils.constructPhotoUrl("profile", it["user_id"] as String))
                jsonArray.put(userObject)
            }
            JSONObject().success().put("users", jsonArray)
        } as JSONObject? ?: JSONObject().fail(message = "DB Failure")



    }

    val suggestUsers = Route { request, response ->


    }

    val suggestPhotoGrid = Route { request, response ->


    }

    val suggestPhotoList = Route { request, response ->


    }
}
