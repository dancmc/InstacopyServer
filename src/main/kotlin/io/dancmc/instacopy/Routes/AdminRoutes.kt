package io.dancmc.instacopy.Routes
import io.dancmc.instacopy.Data.Database
import io.dancmc.instacopy.Utils
import io.dancmc.instacopy.fail
import io.dancmc.instacopy.success
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import spark.Route
import kotlin.system.exitProcess

object AdminRoutes {



    val changePassword = Route { request, response ->
        val displayName = request.queryParamOrDefault("display_name", "").toLowerCase()
        val password = request.queryParamOrDefault("password", "")

        Database.executeTransaction {
            val userNode = it.findNode({ "User" }, "display_name", displayName)
            userNode.setProperty("password_hash", Utils.Password.hashPassword(password))
        }

    }

    val kill = Route { request, response ->
        GlobalScope.launch {
            delay(2000)
            exitProcess(0)
        }
        return@Route "Shutting down"
    }

}