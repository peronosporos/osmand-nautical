package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.CapabilityManager
import net.osmand.plus.plugins.nautical.network.PolarProfile
import net.osmand.plus.plugins.nautical.network.SignalKPluginInfo
import net.osmand.plus.plugins.nautical.network.SignalKPutBody
import android.webkit.WebView
import android.webkit.WebViewClient

class SignalKOrchestratorFragment : BaseOsmAndFragment() {

    private lateinit var adapter: OrchestratorAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.recyclerview_fragment, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = OrchestratorAdapter()
        recyclerView.adapter = adapter
        
        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.getInstance()?.capabilityManager?.capabilities?.collectLatest { caps ->
                refreshData(caps)
            }
        }
        return view
    }

    private fun refreshData(caps: CapabilityManager.ServerCapabilityMap) {
        lifecycleScope.launch {
            val engine = NauticalPlugin.engine
            val restService = engine?.getRestService()
            
            val pluginsResponse = restService?.getPlugins()
            val plugins = if (pluginsResponse?.isSuccessful == true) pluginsResponse.body() ?: emptyList() else emptyList()
            
            adapter.setData(plugins, caps)
        }
    }

    private inner class OrchestratorAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var plugins = emptyList<SignalKPluginInfo>()
        private var capabilities: CapabilityManager.ServerCapabilityMap? = null
        private var availablePolars = emptyMap<String, PolarProfile>()

        fun setData(pluginList: List<SignalKPluginInfo>, caps: CapabilityManager.ServerCapabilityMap) {
            plugins = pluginList
            capabilities = caps
            
            if (availablePolars.isEmpty() && caps.hasPolarPerformance) {
                lifecycleScope.launch {
                    val response = NauticalPlugin.engine?.getRestService()?.getPolars()
                    if (response?.isSuccessful == true) {
                        availablePolars = response.body() ?: emptyMap()
                        notifyDataSetChanged()
                    }
                }
            }
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int = 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.list_item_with_descr, parent, false)
            val container = v.findViewById<LinearLayout>(R.id.description).parent as LinearLayout
            val actionLayout = LinearLayout(v.context).apply {
                orientation = LinearLayout.HORIZONTAL
                isVisible = false
            }
            container.addView(actionLayout)
            return OrchestratorViewHolder(v, actionLayout)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val plugin = plugins[position]
            val vh = holder as OrchestratorViewHolder
            vh.title.text = plugin.name
            
            val status = if (plugin.enabled) "ACTIVE" else "DISABLED"
            val guidance = getGuidance(plugin.id)
            vh.desc.text = if (plugin.enabled) "$status • v${plugin.version}" else guidance
            
            val colorRes = if (plugin.enabled) R.color.nautical_status_green else R.color.nautical_status_red
            vh.desc.setTextColor(ContextCompat.getColor(vh.itemView.context, colorRes))

            vh.actionContainer.removeAllViews()
            if (plugin.enabled) {
                vh.actionContainer.isVisible = true
                addActions(vh.actionContainer, plugin)
            } else {
                vh.actionContainer.isVisible = false
            }

            vh.itemView.setOnClickListener {
                handlePluginClick(plugin)
            }
        }

        private fun getGuidance(pluginId: String): String = when (pluginId) {
            "signalk-polar-performance" -> "Provides VMG and target speed metrics."
            "winga-weather-routing" -> "Optimal weather-aware routing."
            "signalk-routeiq" -> "Vessel-aware safety routing (draft/clearance)."
            "signalk-tides" -> "Tidal height and current predictions."
            "signalk-polar-recorder" -> "Automatic data capture for boat polars."
            else -> "Enhance your vessel capabilities by enabling this plugin."
        }

        private fun addActions(container: LinearLayout, plugin: SignalKPluginInfo) {
            val ctx = container.context
            
            val btnConfig = Button(ctx, null, android.R.attr.buttonStyleSmall).apply {
                text = "CONFIGURE"
                setOnClickListener { openPluginConfig(plugin) }
            }
            container.addView(btnConfig)

            if (plugin.id == "signalk-polar-performance") {
                val btnPolar = Button(ctx, null, android.R.attr.buttonStyleSmall).apply {
                    text = "SELECT POLAR"
                    setOnClickListener { showPolarSelector() }
                }
                container.addView(btnPolar)
            }
        }

        private fun openPluginConfig(plugin: SignalKPluginInfo) {
            val app = getApp() ?: return
            val ip = app.settings.NAUTICAL_SERVER_IP.get()
            val port = app.settings.NAUTICAL_SERVER_PORT.get()
            val url = "http://$ip:$port/admin/#/plugins/${plugin.id}"
            
            AlertDialog.Builder(requireContext())
                .setTitle(plugin.name)
                .setView(LayoutInflater.from(context).inflate(R.layout.mapillary_web_view, null).apply {
                    val webView = findViewById<WebView>(R.id.webView)
                    webView.webViewClient = WebViewClient()
                    webView.settings.javaScriptEnabled = true
                    webView.loadUrl(url)
                })
                .setPositiveButton("Close", null)
                .show()
        }

        private fun showPolarSelector() {
            val names = availablePolars.keys.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Select Active Polar")
                .setItems(names) { _, which ->
                    val selected = names[which]
                    lifecycleScope.launch {
                        NauticalPlugin.engine?.getRestService()?.putValue("performance.activePolar", SignalKPutBody(selected))
                    }
                }
                .show()
        }

        private fun handlePluginClick(plugin: SignalKPluginInfo) {
            lifecycleScope.launch {
                val restService = NauticalPlugin.engine?.getRestService()
                when (plugin.id) {
                    "winga-weather-routing" -> {
                        restService?.triggerPluginCalculation(plugin.id, emptyMap())
                    }
                    "signalk-polar-recorder" -> {
                        restService?.triggerPluginAction(plugin.id, "toggleRecording", emptyMap())
                    }
                }
                refreshData(capabilities ?: CapabilityManager.ServerCapabilityMap())
            }
        }

        override fun getItemCount() = plugins.size
    }

    private class OrchestratorViewHolder(v: View, val actionContainer: LinearLayout) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.title)
        val desc: TextView = v.findViewById(R.id.description)
    }
}
