package io.dancmc.instacopy.Routes
import io.dancmc.instacopy.Data.Database
import io.dancmc.instacopy.fail
import io.dancmc.instacopy.success
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.json.JSONObject
import spark.Route

object AdminRoutes {

    val validate = Route { request, response ->
        val userID = request.attribute("user") as String
        Database.executeTransaction {
            val user = it.findNode({"User"}, "user_id", userID)
            if(user==null){
                return@executeTransaction JSONObject().fail(code = Errors.JWT_NOT_VALID, message = "User JWT not valid")
            }else {
                return@executeTransaction  JSONObject().success()
            }
        } as JSONObject? ?: JSONObject().fail(message = "DB Fail")
    }

    val changePassword = Route { request, response ->
//        request.queryParamOrDefault("display")
    }

    val kill = Route { request, response ->
        launch {
            delay(2000)
            System.exit(0)
        }
        return@Route "Shutting down"
    }

}