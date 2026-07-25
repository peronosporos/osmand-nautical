package net.osmand.plus.plugins.nautical.maneuvers

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import net.osmand.plus.OsmandApplication
import java.util.Locale

class ManeuverSpeechHelper(
    private val app: OsmandApplication,
    private val manager: ManeuverManager
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(app)
            speechRecognizer?.setRecognitionListener(this)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            // Explicitly enforce offline processing
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onError(error: Int) {
        // Handle error, maybe restart listening if needed
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        matches?.forEach { match ->
            when {
                match.contains("execute", ignoreCase = true) -> manager.execute()
                match.contains("cancel", ignoreCase = true) || match.contains("abort", ignoreCase = true) -> manager.abort()
                match.contains("arm", ignoreCase = true) -> manager.arm()
            }
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
