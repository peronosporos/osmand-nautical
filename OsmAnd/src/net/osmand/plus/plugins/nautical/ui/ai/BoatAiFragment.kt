package net.osmand.plus.plugins.nautical.ui.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.utils.AndroidUtils

class BoatAiFragment : BaseOsmAndFragment(), RecognitionListener {

    private lateinit var viewModel: BoatAiViewModel
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var editQuery: EditText
    private lateinit var listeningText: TextView
    private lateinit var adapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingIndicator: ProgressBar

    private val recordAudioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            startListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this, BoatAiViewModel.Factory(app)).get(BoatAiViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = themedInflater.inflate(R.layout.nautical_boat_ai_fragment, container, false)
        
        recyclerView = view.findViewById(R.id.recycler_view)
        loadingIndicator = view.findViewById(R.id.loading_indicator)
        listeningText = view.findViewById(R.id.listening_text)
        editQuery = view.findViewById(R.id.edit_query)
        val btnSend: ImageButton = view.findViewById(R.id.btn_send)
        val btnMic: ImageButton = view.findViewById(R.id.btn_mic)
        
        adapter = ChatAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        recyclerView.adapter = adapter

        btnSend.setOnClickListener {
            val query = editQuery.text.toString()
            if (query.isNotEmpty()) {
                viewModel.sendMessage(query)
                editQuery.setText("")
            }
        }

        btnMic.setOnClickListener {
            checkPermissionsAndStartListening()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messages.collect { messages ->
                        adapter.submitList(messages) {
                            if (messages.isNotEmpty()) {
                                recyclerView.scrollToPosition(messages.size - 1)
                            }
                        }
                    }
                }
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
                        btnSend.isEnabled = !isLoading
                        btnMic.isEnabled = !isLoading
                    }
                }
                launch {
                    viewModel.isListening.collect { isListening ->
                        listeningText.visibility = if (isListening) View.VISIBLE else View.GONE
                        btnMic.setImageResource(if (isListening) R.drawable.ic_action_stop else R.drawable.ic_action_micro_dark)
                    }
                }
            }
        }

        viewModel.addWelcomeMessage(getString(R.string.nautical_ai_welcome))

        return view
    }

    private fun checkPermissionsAndStartListening() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            toggleListening()
        }
    }

    private fun toggleListening() {
        if (viewModel.isListening.value) {
            stopListening()
        } else {
            startListening()
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            app.showToastMessage(R.string.nautical_ai_stt_unavailable)
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
            speechRecognizer?.setRecognitionListener(this)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
        }
        speechRecognizer?.startListening(intent)
        viewModel.setListening(true)
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        viewModel.setListening(false)
    }

    override fun onPause() {
        super.onPause()
        stopListening()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    // RecognitionListener implementation
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        viewModel.setListening(false)
    }

    override fun onError(error: Int) {
        viewModel.setListening(false)
        val errorMsg = when (error) {
            SpeechRecognizer.ERROR_NETWORK -> "Network Error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network Timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Service Busy"
            else -> "Voice recognition error ($error)"
        }
        app.showToastMessage(getString(R.string.nautical_ai_stt_error, errorMsg))
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val text = matches[0]
            viewModel.sendMessage(text)
        }
        viewModel.setListening(false)
    }

    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    private class ChatAdapter : ListAdapter<ChatMessage, ChatViewHolder>(MessageDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val layout = if (viewType == 0) R.layout.nautical_chat_item_bot else R.layout.nautical_chat_item_user
            val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
            return ChatViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            val msg = getItem(position)
            holder.text.text = msg.text
            
            val context = holder.itemView.context
            when {
                msg.isError -> {
                    holder.text.setTextColor(ContextCompat.getColor(context, R.color.color_warning))
                }
                msg.isBot -> {
                    // Bot bubbles use standard primary text color
                    holder.text.setTextColor(AndroidUtils.getColorFromAttr(context, android.R.attr.textColorPrimary))
                }
                else -> {
                    // User bubbles on accent background - use primary inverse or white but filtered
                    holder.text.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                }
            }
        }

        override fun getItemViewType(position: Int): Int = if (getItem(position).isBot) 0 else 1
    }

    private class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean = oldItem == newItem
    }

    private class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.text_message)
    }
}
