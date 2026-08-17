package net.osmand.plus.plugins.nautical.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.ConnectionStatus
import net.osmand.plus.plugins.nautical.repository.BoatAiRepository

data class ChatMessage(
    val id: Long = System.nanoTime(),
    val text: String, 
    val isBot: Boolean,
    val isError: Boolean = false
)

class BoatAiViewModel(
    private val app: OsmandApplication,
    private val repository: BoatAiRepository
) : ViewModel() {

    private val actionExecutor = BoatAiActionExecutor()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun addWelcomeMessage(welcomeText: String) {
        if (_messages.value.isEmpty()) {
            _messages.value = listOf(ChatMessage(text = welcomeText, isBot = true))
        }
    }

    fun setListening(listening: Boolean) {
        _isListening.value = listening
    }

    fun sendMessage(query: String) {
        if (query.isBlank()) return

        val userMessage = ChatMessage(text = query, isBot = false)
        _messages.value = _messages.value + userMessage
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val engine = NauticalPlugin.engine
            val state = engine?.getCurrentState() ?: net.osmand.plus.plugins.nautical.engine.MarineState()

            val result = repository.sendQuery(query, state)
            result.onSuccess { boatResult ->
                _messages.value = _messages.value + ChatMessage(text = boatResult.reply, isBot = true)
                
                // Execute actions and provide granular feedback (Item 16)
                var successCount = 0
                boatResult.actions.forEach { action ->
                    if (actionExecutor.execute(action)) {
                        successCount++
                    }
                }
                
                if (boatResult.actions.isNotEmpty()) {
                    val feedback = if (successCount == boatResult.actions.size) {
                        app.getString(R.string.nautical_ai_actions_executed, successCount)
                    } else if (successCount > 0) {
                        app.getString(R.string.nautical_ai_actions_partial, successCount, boatResult.actions.size)
                    } else {
                        app.getString(R.string.nautical_ai_actions_failed)
                    }
                    _messages.value = _messages.value + ChatMessage(text = feedback, isBot = true)
                }
            }.onFailure { e ->
                val errorMsg = e.message ?: app.getString(R.string.nautical_ai_server_error)
                _error.value = errorMsg
                _messages.value = _messages.value + ChatMessage(text = "Error: $errorMsg", isBot = true, isError = true)
            }
            _isLoading.value = false
        }
    }

    class Factory(private val app: OsmandApplication) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BoatAiViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return BoatAiViewModel(app, BoatAiRepository(app)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
