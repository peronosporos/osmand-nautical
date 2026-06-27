package net.osmand.plus.plugins.nautical.engine

import java.util.UUID

class SignalKEngine(private val connection: SignalKConnection) {

    var currentState = MarineState()
        private set

    private var stateListener: ((MarineState) -> Unit)? = null

    fun registerListener(listener: (MarineState) -> Unit) {
        this.stateListener = listener
    }

    fun handleIncomingMessage(jsonMessage: String) {
        // Lightweight parsing to avoid massive JSON library overhead during testing
        if (jsonMessage.contains("\"path\":\"navigation.position\"")) {
            val lat = extractJsonValue(jsonMessage, "latitude")?.toDoubleOrNull()
            val lon = extractJsonValue(jsonMessage, "longitude")?.toDoubleOrNull()
            currentState = currentState.copy(latitude = lat, longitude = lon)
        } else if (jsonMessage.contains("\"path\":\"navigation.headingTrue\"")) {
            val heading = extractJsonValue(jsonMessage, "value")?.toDoubleOrNull()
            currentState = currentState.copy(headingTrue = heading)
        } else if (jsonMessage.contains("\"path\":\"steering.autopilot.state\"")) {
            val state = extractJsonValue(jsonMessage, "value") ?: "standby"
            currentState = currentState.copy(autopilotState = state)
        }

        stateListener?.invoke(currentState)
    }

    fun changeAutopilotState(targetState: String) {
        val putRequest = """
        {
          "requestId": "${UUID.randomUUID()}",
          "context": "vessels.self",
          "put": {
            "path": "steering.autopilot.state",
            "value": "$targetState"
          }
        }
        """.trimIndent()

        connection.sendDelta(putRequest)
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val regex = "\"$key\"\\s*:\\s*\"?([^,\"}]+)\"?".toRegex()
        return regex.find(json)?.groupValues?.get(1)?.trim()
    }
}