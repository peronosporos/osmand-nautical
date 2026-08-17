package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
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
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.CapabilityManager
import net.osmand.plus.plugins.nautical.engine.ConnectionStatus
import net.osmand.plus.plugins.nautical.network.PolarProfile
import net.osmand.plus.plugins.nautical.network.SignalKPluginInfo
import net.osmand.plus.plugins.nautical.network.SignalKPutBody

class SignalKOrchestratorFragment : BaseOsmAndFragment() {

    private lateinit var adapter: OrchestratorAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.recyclerview_fragment, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = OrchestratorAdapter()
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.getInstance()?.capabilityManager?.capabilities?.collectLatest { caps ->
                refreshData(caps)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineState?.collectLatest {
                adapter.notifyItemChanged(0)
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

        private val TYPE_DIAGNOSTICS_HEADER = 0
        private val TYPE_CAPABILITIES_SUMMARY = 1
        private val TYPE_PLUGIN_ITEM = 2

        private var plugins = emptyList<SignalKPluginInfo>()
        private var capabilities: CapabilityManager.ServerCapabilityMap = CapabilityManager.ServerCapabilityMap()
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

        override fun getItemViewType(position: Int): Int {
            return when (position) {
                0 -> TYPE_DIAGNOSTICS_HEADER
                1 -> TYPE_CAPABILITIES_SUMMARY
                else -> TYPE_PLUGIN_ITEM
            }
        }

        override fun getItemCount(): Int = 2 + plugins.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                TYPE_DIAGNOSTICS_HEADER -> {
                    val v = inflater.inflate(R.layout.list_item_with_descr, parent, false)
                    val container = v.findViewById<LinearLayout>(R.id.description).parent as LinearLayout
                    val actionLayout = LinearLayout(v.context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 16, 0, 8)
                    }
                    container.addView(actionLayout)
                    DiagnosticsHeaderViewHolder(v, actionLayout)
                }
                TYPE_CAPABILITIES_SUMMARY -> {
                    val v = inflater.inflate(R.layout.list_item_with_descr, parent, false)
                    CapabilitiesViewHolder(v)
                }
                else -> {
                    val v = inflater.inflate(R.layout.list_item_with_descr, parent, false)
                    val container = v.findViewById<LinearLayout>(R.id.description).parent as LinearLayout
                    val actionLayout = LinearLayout(v.context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        isVisible = false
                        setPadding(0, 12, 0, 4)
                    }
                    container.addView(actionLayout)
                    PluginViewHolder(v, actionLayout)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is DiagnosticsHeaderViewHolder -> bindHeader(holder)
                is CapabilitiesViewHolder -> bindCapabilities(holder)
                is PluginViewHolder -> {
                    val pluginIndex = position - 2
                    if (pluginIndex in plugins.indices) {
                        bindPlugin(holder, plugins[pluginIndex])
                    }
                }
            }
        }

        private fun bindHeader(vh: DiagnosticsHeaderViewHolder) {
            val app = getApp() ?: return
            val ip = app.settings.NAUTICAL_SERVER_IP.get()
            val port = app.settings.NAUTICAL_SERVER_PORT.get()
            val status = NauticalPlugin.engine?.getCurrentState()?.connectionStatus ?: ConnectionStatus.DISCONNECTED

            vh.title.text = app.getString(R.string.nautical_diag_server_info)

            val statusText = when (status) {
                ConnectionStatus.CONNECTED -> "CONNECTED (WebSocket & REST)"
                ConnectionStatus.CONNECTING -> "CONNECTING..."
                ConnectionStatus.DISCONNECTED -> "DISCONNECTED"
                ConnectionStatus.ERROR -> "CONNECTION ERROR"
            }
            vh.desc.text = "Target: $ip:$port\nStatus: $statusText"

            val colorRes = when (status) {
                ConnectionStatus.CONNECTED -> R.color.nautical_status_green
                ConnectionStatus.CONNECTING -> R.color.nautical_color_amber
                else -> R.color.nautical_status_red
            }
            vh.desc.setTextColor(ContextCompat.getColor(vh.itemView.context, colorRes))

            vh.actionContainer.removeAllViews()
            val btnProbe = Button(vh.itemView.context, null, android.R.attr.buttonStyleSmall).apply {
                text = app.getString(R.string.nautical_diag_probe_btn)
                setOnClickListener {
                    lifecycleScope.launch {
                        val capMgr = NauticalPlugin.getInstance()?.capabilityManager
                        val engine = NauticalPlugin.engine
                        val rest = engine?.getRestService()
                        if (capMgr != null && rest != null) {
                            capMgr.probe(rest)
                            app.showToastMessage(R.string.nautical_diag_orchestrate_success)
                        } else {
                            app.showToastMessage(R.string.nautical_diag_orchestrate_fail)
                        }
                        refreshData(capMgr?.capabilities?.value ?: CapabilityManager.ServerCapabilityMap())
                    }
                }
            }
            vh.actionContainer.addView(btnProbe)
        }

        private fun bindCapabilities(vh: CapabilitiesViewHolder) {
            val app = getApp() ?: return
            vh.title.text = app.getString(R.string.nautical_diag_capabilities_title)

            val activeList = mutableListOf<String>()
            if (capabilities.hasPolarPerformance) activeList.add("• Polar VMG")
            if (capabilities.hasAutopilot) activeList.add("• Autopilot Bridge")
            if (capabilities.hasLogging) activeList.add("• Marine Logbook")
            if (capabilities.hasSignalKTides) activeList.add("• Tides & Currents")
            if (capabilities.hasGrib) activeList.add("• GRIB Weather Provider")
            if (capabilities.hasAisPrioritizer) activeList.add("• AIS Target Prioritizer")
            if (capabilities.hasDigitalSwitching) activeList.add("• Digital Switching")
            if (capabilities.hasAdvancedSafety) activeList.add("• CPA/TCPA Collision Risk")
            if (capabilities.hasForwardWatch) activeList.add("• Forward Hazard Watch")

            if (activeList.isNotEmpty()) {
                vh.desc.text = "Active Capabilities (${activeList.size}):\n" + activeList.joinToString("\n")
                vh.desc.setTextColor(ContextCompat.getColor(vh.itemView.context, R.color.nautical_status_green))
            } else {
                vh.desc.text = "No specialized server plugins detected yet. Tap Probe to scan server."
                vh.desc.setTextColor(ContextCompat.getColor(vh.itemView.context, R.color.nautical_status_red))
            }
        }

        private fun bindPlugin(vh: PluginViewHolder, plugin: SignalKPluginInfo) {
            vh.title.text = plugin.name

            val status = if (plugin.enabled) "ACTIVE" else "DISABLED"
            val guidance = getGuidance(plugin.id)
            vh.desc.text = if (plugin.enabled) "$status • v${plugin.version}\n$guidance" else "$status\n$guidance"

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
            "signalk-polar-performance" -> "Provides VMG and target boat speed metrics."
            "winga-weather-routing" -> "Optimal weather-aware routing engine."
            "signalk-routeiq" -> "Vessel-aware safety routing (draft/clearance)."
            "signalk-tides" -> "Tidal height and current predictions."
            "signalk-polar-recorder" -> "Automatic data capture for boat polars."
            "signalk-grib-weather-provider" -> "GRIB weather download and distribution."
            else -> "Enhance vessel capabilities by enabling this plugin."
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
            if (names.isEmpty()) {
                getApp()?.showToastMessage("No polars available on server")
                return
            }
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
                refreshData(capabilities)
            }
        }
    }

    private class DiagnosticsHeaderViewHolder(v: View, val actionContainer: LinearLayout) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.title)
        val desc: TextView = v.findViewById(R.id.description)
    }

    private class CapabilitiesViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.title)
        val desc: TextView = v.findViewById(R.id.description)
    }

    private class PluginViewHolder(v: View, val actionContainer: LinearLayout) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.title)
        val desc: TextView = v.findViewById(R.id.description)
    }
}
