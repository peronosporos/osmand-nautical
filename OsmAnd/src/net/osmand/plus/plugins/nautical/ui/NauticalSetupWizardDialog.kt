package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.discovery.SignalKDiscoveryManager
import net.osmand.plus.plugins.nautical.network.SignalKRestService
import net.osmand.plus.settings.enums.VesselType

class NauticalSetupWizardDialog : BaseMaterialBottomSheetDialogFragment() {

    private var currentStep = 0
    private lateinit var switcher: ViewFlipper
    private lateinit var btnNext: Button
    private lateinit var btnBack: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_nautical_setup_wizard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        switcher = view.findViewById(R.id.step_switcher)
        btnNext = view.findViewById(R.id.btn_next)
        btnBack = view.findViewById(R.id.btn_back)

        val vesselTypeSpinner = view.findViewById<AutoCompleteTextView>(R.id.vessel_type_spinner)
        val vesselDraftEdit = view.findViewById<EditText>(R.id.vessel_draft)
        val vesselAirDraftEdit = view.findViewById<EditText>(R.id.vessel_air_draft)
        val vesselMmsiEdit = view.findViewById<EditText>(R.id.vessel_mmsi)
        val serverIpEdit = view.findViewById<EditText>(R.id.server_ip)
        val serverPortEdit = view.findViewById<EditText>(R.id.server_port)
        val serverSecureSwitch = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.server_secure)
        val authTokenLayout = view.findViewById<View>(R.id.auth_token_layout)
        val authTokenEdit = view.findViewById<EditText>(R.id.auth_token)
        val btnDiscovery = view.findViewById<Button>(R.id.btn_discovery)
        val btnTestConnection = view.findViewById<Button>(R.id.btn_test_connection)

        val nmeaSourceSpinner = view.findViewById<AutoCompleteTextView>(R.id.nmea_source_spinner)
        val nmeaSources = net.osmand.plus.settings.enums.NmeaSource.entries.map { it.name }
        val nmeaAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nmeaSources)
        nmeaSourceSpinner.setAdapter(nmeaAdapter)
        nmeaSourceSpinner.setText(settings.NAUTICAL_NMEA_SOURCE.get().name, false)

        // Setup Vessel Type Spinner
        val types = VesselType.entries.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, types)
        vesselTypeSpinner.setAdapter(adapter)
        vesselTypeSpinner.setText(settings.NAUTICAL_VESSEL_TYPE.get().name, false)

        vesselDraftEdit.setText(settings.NAUTICAL_VESSEL_DRAFT.get().toString())
        vesselAirDraftEdit.setText(settings.NAUTICAL_AIR_DRAFT.get().toString())
        vesselMmsiEdit.setText(settings.NAUTICAL_AIS_OWN_MMSI.get().toString())
        serverIpEdit.setText(settings.NAUTICAL_SERVER_IP.get())
        serverPortEdit.setText(settings.NAUTICAL_SERVER_PORT.get())
        serverSecureSwitch.isChecked = settings.NAUTICAL_USE_SECURE_CONNECTION.get()
        authTokenLayout.visibility = if (serverSecureSwitch.isChecked) View.VISIBLE else View.GONE
        authTokenEdit.setText(settings.NAUTICAL_SIGNAL_K_AUTH_TOKEN.get())

        serverSecureSwitch.setOnCheckedChangeListener { _, isChecked ->
            authTokenLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        view.findViewById<Button>(R.id.btn_optimize_battery).setOnClickListener {
            NauticalPlugin.getInstance()?.let { plugin ->
                settings.NAUTICAL_RECEIVE_IN_BACKGROUND.set(true)
                plugin.checkBatteryOptimization()
            }
        }

        btnNext.setOnClickListener {
            if (currentStep == 0) {
                val draft = vesselDraftEdit.text.toString().toFloatOrNull() ?: 0f
                val mmsiText = vesselMmsiEdit.text.toString()
                val mmsi = mmsiText.toIntOrNull() ?: 0
                if (draft <= 0) {
                    app.showToastMessage(R.string.nautical_error_invalid_draft)
                    return@setOnClickListener
                }
                if (mmsiText.length != 9 || mmsi == 0) {
                    app.showToastMessage(R.string.shared_string_invalid_value) // Or a more specific message if available
                    return@setOnClickListener
                }
            }

            if (currentStep < 2) {
                currentStep++
                switcher.showNext()
                updateButtons()
            } else {
                // Save everything and finish
                val selectedType = VesselType.valueOf(vesselTypeSpinner.text.toString())
                val selectedNmea = net.osmand.plus.settings.enums.NmeaSource.valueOf(nmeaSourceSpinner.text.toString())
                val draft = vesselDraftEdit.text.toString().toFloatOrNull() ?: 2.0f
                val airDraft = vesselAirDraftEdit.text.toString().toFloatOrNull() ?: 15.0f
                val mmsi = vesselMmsiEdit.text.toString().toIntOrNull() ?: 0
                val ip = serverIpEdit.text.toString()
                val port = serverPortEdit.text.toString()
                val secure = serverSecureSwitch.isChecked
                val token = authTokenEdit.text.toString()

                settings.NAUTICAL_VESSEL_TYPE.set(selectedType)
                settings.NAUTICAL_NMEA_SOURCE.set(selectedNmea)
                settings.NAUTICAL_VESSEL_DRAFT.set(draft)
                settings.NAUTICAL_AIR_DRAFT.set(airDraft)
                settings.NAUTICAL_AIS_OWN_MMSI.set(mmsi)
                settings.NAUTICAL_SERVER_IP.set(ip)
                settings.NAUTICAL_SERVER_PORT.set(port)
                settings.NAUTICAL_USE_SECURE_CONNECTION.set(secure)
                settings.NAUTICAL_SIGNAL_K_AUTH_TOKEN.set(token)
                settings.NAUTICAL_SETUP_WIZARD_COMPLETED.set(true)

                val plugin = net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance()
                plugin?.updateNmeaSource()
                plugin?.reconnect()
                dismiss()
            }
        }

        btnBack.setOnClickListener {
            if (currentStep > 0) {
                currentStep--
                switcher.showPrevious()
                updateButtons()
            }
        }

        btnDiscovery.setOnClickListener {
            showDiscoveryDialog(serverIpEdit, serverPortEdit, serverSecureSwitch)
        }

        btnTestConnection.setOnClickListener {
            testConnection(serverIpEdit.text.toString(), serverPortEdit.text.toString(), serverSecureSwitch.isChecked, authTokenEdit.text.toString())
        }
    }

    private fun showDiscoveryDialog(ipEdit: EditText, portEdit: EditText, secureSwitch: com.google.android.material.switchmaterial.SwitchMaterial) {
        val manager = SignalKDiscoveryManager(requireContext())
        manager.startDiscovery()

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(R.string.nautical_discovery_searching)

        val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1)
        builder.setAdapter(adapter) { _, which ->
            val server = manager.discoveredServers.value[which]
            ipEdit.setText(server.host)
            portEdit.setText(server.port.toString())
            secureSwitch.isChecked = server.isWebSocket
            manager.stopDiscovery()
        }

        val dialog = builder.create()
        dialog.setOnDismissListener { manager.stopDiscovery() }
        dialog.show()

        lifecycleScope.launch {
            manager.discoveredServers.collectLatest { servers ->
                app.runInUIThread {
                    adapter.clear()
                    if (servers.isEmpty()) {
                        dialog.setTitle(getString(R.string.nautical_discovery_searching))
                    } else {
                        dialog.setTitle(getString(R.string.nautical_discovery_select_server))
                        servers.forEach { adapter.add("${it.name} (${it.host})") }
                    }
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun testConnection(ip: String, port: String, secure: Boolean, token: String) {
        if (ip.isBlank()) {
            app.showToastMessage(R.string.nautical_server_ip_desc)
            return
        }

        lifecycleScope.launch {
            try {
                val plugin = NauticalPlugin.getInstance() ?: return@launch
                val client = plugin.okHttpClient ?: plugin.application.let {
                    // Create temporary client if plugin hasn't initialized it yet
                    okhttp3.OkHttpClient.Builder()
                        .connectTimeout(java.time.Duration.ofSeconds(5))
                        .build()
                }
                
                val protocol = if (secure) "https" else "http"
                val baseUrl = "$protocol://$ip:$port"
                val restService = SignalKRestService.create(baseUrl, client)
                
                if (restService == null) {
                    app.showToastMessage(getString(R.string.nautical_connection_test_failed, "Invalid URL (check IP/Port)"))
                    return@launch
                }

                val response = try {
                    restService.getVesselSelf()
                } catch (e: Exception) {
                    app.showToastMessage(getString(R.string.nautical_connection_test_failed, e.localizedMessage ?: "Network Error"))
                    return@launch
                }

                if (response.isSuccessful) {
                    app.showToastMessage(R.string.nautical_connection_test_success)
                } else {
                    val errorMsg = when(response.code()) {
                        401 -> "Unauthorized (check Token)"
                        404 -> "Signal K Not Found"
                        else -> "HTTP ${response.code()}"
                    }
                    app.showToastMessage(getString(R.string.nautical_connection_test_failed, errorMsg))
                }
            } catch (e: Exception) {
                app.showToastMessage(getString(R.string.nautical_connection_test_failed, e.message ?: "Unknown Error"))
            }
        }
    }

    private fun updateButtons() {
        btnBack.visibility = if (currentStep == 0) View.GONE else View.VISIBLE
        btnNext.text = if (currentStep == 2) getString(R.string.nautical_wizard_finish) else getString(R.string.nautical_wizard_next)
    }

    companion object {
        const val TAG = "NauticalSetupWizardDialog"
        fun show(fm: androidx.fragment.app.FragmentManager) {
            NauticalSetupWizardDialog().show(fm, TAG)
        }
    }
}
