package net.osmand.plus.plugins.nautical.ui.ai

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin

class BoatAiFragment : BaseOsmAndFragment() {

    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = themedInflater.inflate(R.layout.recyclerview_fragment, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        
        // Add Chat Input Layout to the recyclerview_fragment container
        val root = view as ViewGroup
        val inputView = LayoutInflater.from(requireContext()).inflate(R.layout.search_text_layout, root, false)
        root.addView(inputView)
        
        val editQuery: EditText = inputView.findViewById(R.id.searchEditText)
        val btnSend: ImageButton = inputView.findViewById(R.id.clearButton)
        btnSend.setImageResource(R.drawable.ic_action_remove_dark)
        
        adapter = ChatAdapter(messages)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        btnSend.setOnClickListener {
            val query = editQuery.text.toString()
            if (query.isNotEmpty()) {
                sendToAi(query)
                editQuery.setText("")
            }
        }

        // Welcome message
        addMessage(getString(R.string.nautical_ai_welcome), isBot = true)

        return view
    }

    private fun sendToAi(query: String) {
        addMessage(query, isBot = false)
        
        lifecycleScope.launch {
            val plugin = NauticalPlugin.getInstance() ?: return@launch
            val client = plugin.okHttpClient ?: return@launch
            val ip = app.settings.NAUTICAL_SERVER_IP.get()
            val port = app.settings.NAUTICAL_SERVER_PORT.get()
            val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
            val service = net.osmand.plus.plugins.nautical.network.SignalKRestService.create("$protocol://$ip:$port", client)
            
            try {
                val state = NauticalPlugin.engine?.getCurrentState()
                val contextJson = if (state != null) {
                    kotlinx.serialization.json.Json.encodeToString(state)
                } else "{}"

                val response = withContext(Dispatchers.IO) {
                    service.triggerPluginCalculation("signalk-ai-bridge", mapOf(
                        "query" to query,
                        "vessel_state" to contextJson
                    ))
                }
                if (response.isSuccessful) {
                    val reply = response.body()?.get("reply")?.toString() ?: getString(R.string.nautical_ai_no_text)
                    addMessage(reply, isBot = true)
                } else {
                    addMessage(getString(R.string.nautical_ai_connection_error), isBot = true)
                }
            } catch (_: Exception) {
                addMessage(getString(R.string.nautical_ai_server_error), isBot = true)
            }
        }
    }

    private fun addMessage(text: String, isBot: Boolean) {
        messages.add(ChatMessage(text, isBot))
        adapter.notifyItemInserted(messages.size - 1)
        // Scroll to bottom logic would go here
    }

    private data class ChatMessage(val text: String, val isBot: Boolean)

    private class ChatAdapter(private val items: List<ChatMessage>) : RecyclerView.Adapter<ChatViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val layout = if (viewType == 0) R.layout.list_item_description else R.layout.list_item_with_descr
            val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
            return ChatViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            val msg = items[position]
            holder.text.text = msg.text
            holder.text.setTextColor(if (msg.isBot) Color.BLUE else Color.BLACK)
        }

        override fun getItemViewType(position: Int): Int = if (items[position].isBot) 0 else 1
        override fun getItemCount(): Int = items.size
    }

    private class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.title)
    }
}
