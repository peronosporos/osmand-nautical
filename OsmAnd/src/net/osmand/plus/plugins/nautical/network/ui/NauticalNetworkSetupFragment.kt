package net.osmand.plus.plugins.nautical.network.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.discovery.DiscoveredServer
import net.osmand.plus.plugins.nautical.discovery.SignalKDiscoveryManager
import java.util.Locale

class NauticalNetworkSetupFragment : BaseOsmAndFragment() {

    private lateinit var discoveryManager: SignalKDiscoveryManager
    private lateinit var serversAdapter: DiscoveredServersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        discoveryManager = SignalKDiscoveryManager(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.fragment_nautical_network_setup, container, false)

        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar?.title = ""
        view.findViewById<View>(R.id.close_button)?.setOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }
        view.findViewById<TextView>(R.id.toolbar_title)?.text = "Marine Network Setup"

        val btnVictron = view.findViewById<MaterialButton>(R.id.btn_preset_victron)
        val btnYachtDevices = view.findViewById<MaterialButton>(R.id.btn_preset_yacht_devices)
        val btnNavico = view.findViewById<MaterialButton>(R.id.btn_preset_navico)
        val btnRaymarine = view.findViewById<MaterialButton>(R.id.btn_preset_raymarine)

        val btnScan = view.findViewById<MaterialButton>(R.id.btn_scan_network)
        val progressScan = view.findViewById<ProgressBar>(R.id.progress_scan)
        val txtScanStatus = view.findViewById<TextView>(R.id.txt_scan_status)
        val txtActiveConnection = view.findViewById<TextView>(R.id.txt_active_connection)

        val currentHost = app.settings.NAUTICAL_SERVER_IP.get()
        txtActiveConnection?.text = "Active Target: $currentHost"

        fun applyTargetAndConnect(host: String, label: String) {
            app.settings.NAUTICAL_SERVER_IP.set(host)
            txtActiveConnection?.text = "Active Target: $host ($label)"
            NauticalPlugin.getInstance()?.reconnect()
            app.showToastMessage("Configured and connecting to $label ($host)")
        }

        // Preset 1: Victron Energy Venus OS
        btnVictron?.setOnClickListener {
            applyTargetAndConnect("192.168.8.1:3000", "Victron Venus OS")
        }

        // Preset 2: Yacht Devices NMEA 2000 Gateway
        btnYachtDevices?.setOnClickListener {
            applyTargetAndConnect("192.168.4.1:10110", "Yacht Devices Gateway")
        }

        // Preset 3: Navico GoFree
        btnNavico?.setOnClickListener {
            applyTargetAndConnect("192.168.0.1:10110", "Navico GoFree Bridge")
        }

        // Preset 4: Raymarine SeaTalkhs / Axiom
        btnRaymarine?.setOnClickListener {
            applyTargetAndConnect("192.168.0.1:2000", "Raymarine SeaTalkhs")
        }

        // Discovery Scanner
        val rvServers = view.findViewById<RecyclerView>(R.id.rv_discovered_servers)
        rvServers?.layoutManager = LinearLayoutManager(context)
        serversAdapter = DiscoveredServersAdapter { server ->
            val hostStr = "${server.host}:${server.port}"
            applyTargetAndConnect(hostStr, server.name)
        }
        rvServers?.adapter = serversAdapter

        var isScanning = false
        btnScan?.setOnClickListener {
            if (!isScanning) {
                isScanning = true
                progressScan?.visibility = View.VISIBLE
                txtScanStatus?.text = "Scanning local subnet via mDNS/ZeroConf..."
                btnScan.text = "STOP SCAN"
                discoveryManager.startDiscovery()
            } else {
                isScanning = false
                progressScan?.visibility = View.GONE
                txtScanStatus?.text = "Scan stopped."
                btnScan.text = "SCAN LOCAL NETWORK"
                discoveryManager.stopDiscovery()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                discoveryManager.discoveredServers.collectLatest { list ->
                    serversAdapter.submitList(list)
                    if (list.isNotEmpty()) {
                        txtScanStatus?.text = "Discovered ${list.size} marine service(s) on network."
                    }
                }
            }
        }

        val isNightVision = NauticalPlugin.isNightVision(app)
        if (isNightVision) {
            view.setBackgroundColor(0xEE120000.toInt())
            btnVictron?.setTextColor(0xFFFF1744.toInt())
            btnYachtDevices?.setTextColor(0xFFFF1744.toInt())
            btnNavico?.setTextColor(0xFFFF1744.toInt())
            btnRaymarine?.setTextColor(0xFFFF1744.toInt())
            btnScan?.backgroundTintList = ColorStateList.valueOf(0xFF8B0000.toInt())
            btnScan?.setTextColor(0xFFFF1744.toInt())
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        discoveryManager.stopDiscovery()
    }

    private class DiscoveredServersAdapter(
        private val onServerClick: (DiscoveredServer) -> Unit
    ) : RecyclerView.Adapter<DiscoveredServersAdapter.ViewHolder>() {

        private var items = listOf<DiscoveredServer>()

        fun submitList(list: List<DiscoveredServer>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
            v.minimumHeight = (48 * parent.context.resources.displayMetrics.density).toInt()
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.text1.text = item.name
            holder.text2.text = "${item.host}:${item.port} (${if (item.isWebSocket) "WebSocket" else "HTTP/TCP"})"
            holder.itemView.setOnClickListener { onServerClick(item) }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val text1: TextView = v.findViewById(android.R.id.text1)
            val text2: TextView = v.findViewById(android.R.id.text2)
        }
    }
}
