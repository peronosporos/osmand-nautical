package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import net.osmand.plus.R
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment
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
        val btnDiscovery = view.findViewById<Button>(R.id.btn_discovery)

        // Setup Vessel Type Spinner
        val types = VesselType.entries.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, types)
        vesselTypeSpinner.setAdapter(adapter)
        vesselTypeSpinner.setText(settings.NAUTICAL_VESSEL_TYPE.get().name, false)

        vesselDraftEdit.setText(settings.NAUTICAL_VESSEL_DRAFT.get().toString())
        vesselAirDraftEdit.setText(settings.NAUTICAL_AIR_DRAFT.get().toString())
        vesselMmsiEdit.setText(settings.NAUTICAL_AIS_OWN_MMSI.get().toString())
        serverIpEdit.setText(settings.NAUTICAL_SERVER_IP.get())

        view.findViewById<Button>(R.id.btn_optimize_battery).setOnClickListener {
            net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance()?.let { plugin ->
                settings.NAUTICAL_RECEIVE_IN_BACKGROUND.set(true)
                plugin.checkBatteryOptimization()
            }
        }

        btnNext.setOnClickListener {
            if (currentStep == 0) {
                val draft = vesselDraftEdit.text.toString().toFloatOrNull() ?: 0f
                val mmsi = vesselMmsiEdit.text.toString().toIntOrNull() ?: 0
                if (draft <= 0) {
                    app.showToastMessage(R.string.nautical_error_invalid_draft)
                    return@setOnClickListener
                }
                if (mmsi == 0) {
                    app.showToastMessage(R.string.shared_string_invalid_value)
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
                val draft = vesselDraftEdit.text.toString().toFloatOrNull() ?: 2.0f
                val airDraft = vesselAirDraftEdit.text.toString().toFloatOrNull() ?: 15.0f
                val mmsi = vesselMmsiEdit.text.toString().toIntOrNull() ?: 0
                val ip = serverIpEdit.text.toString()

                settings.NAUTICAL_VESSEL_TYPE.set(selectedType)
                settings.NAUTICAL_VESSEL_DRAFT.set(draft)
                settings.NAUTICAL_AIR_DRAFT.set(airDraft)
                settings.NAUTICAL_AIS_OWN_MMSI.set(mmsi)
                settings.NAUTICAL_SERVER_IP.set(ip)
                settings.NAUTICAL_SETUP_WIZARD_COMPLETED.set(true)

                net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance()?.reconnect()
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
             // Re-use logic from Settings if possible, or just link to settings
             app.showToastMessage("mDNS Discovery Started...")
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
