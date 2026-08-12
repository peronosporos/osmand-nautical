package net.osmand.plus.plugins.nautical.repository

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState

data class BoatAiAction(
    @SerializedName("type") val type: String,
    @SerializedName("path") val path: String? = null,
    @SerializedName("value") val value: Any? = null
)

data class BoatAiResult(
    val reply: String,
    val actions: List<BoatAiAction> = emptyList()
)

class BoatAiRepository(private val app: OsmandApplication) {

    private val gson = Gson()

    suspend fun sendQuery(query: String, state: MarineState): Result<BoatAiResult> = withContext(Dispatchers.IO) {
        val service = NauticalPlugin.engine?.getRestService() ?: return@withContext Result.failure(Exception("Service not found"))

        try {
            // Use Gson to convert state to a map directly, avoiding dual serialization overhead and type erasure issues
            val stateJsonElement = gson.toJsonTree(state)
            
            val body = mapOf(
                "query" to query,
                "vessel_state" to stateJsonElement
            )

            val response = service.triggerPluginCalculation("signalk-ai-bridge", body)
            if (response.isSuccessful) {
                val respBody = response.body() ?: return@withContext Result.failure(Exception("Empty response body"))
                
                // Robust parsing using Gson from the response map
                val reply = respBody["reply"]?.toString() ?: "No response from AI"
                
                // Improved string cleanup: Only strip quotes if they wrap the entire string and were likely added by toString() on a JsonElement
                val cleanReply = if (reply.startsWith("\"") && reply.endsWith("\"") && reply.length >= 2) {
                    // Use Gson to properly unescape the JSON string instead of manual replace calls
                    try {
                        gson.fromJson(reply, String::class.java)
                    } catch (_: Exception) {
                        reply.substring(1, reply.length - 1).replace("\\n", "\n").replace("\\\"", "\"")
                    }
                } else {
                    reply
                }

                val actions = mutableListOf<BoatAiAction>()
                val actionsRaw = respBody["actions"] as? List<*>
                actionsRaw?.forEach { actionMapRaw ->
                    // Convert raw map items to typed BoatAiAction objects using Gson
                    try {
                        val actionJson = gson.toJsonTree(actionMapRaw)
                        val action = gson.fromJson(actionJson, BoatAiAction::class.java)
                        if (action != null) {
                            actions.add(action)
                        }
                    } catch (_: Exception) {
                        // Skip malformed actions
                    }
                }

                Result.success(BoatAiResult(cleanReply, actions))
            } else {
                val errorMsg = when (response.code()) {
                    401 -> "Unauthorized: Check Signal K token"
                    403 -> "Forbidden: AI bridge plugin restricted"
                    404 -> "AI Bridge plugin not found on server"
                    else -> "Connection error: ${response.code()}"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
