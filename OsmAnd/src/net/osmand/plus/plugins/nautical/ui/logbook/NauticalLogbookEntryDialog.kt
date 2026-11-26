package net.osmand.plus.plugins.nautical.ui.logbook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin

class NauticalLogbookEntryDialog : BaseMaterialBottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.dialog_nautical_logbook_entry, container, false)
        val input = root.findViewById<EditText?>(R.id.edit_entry_text)
        
        root.findViewById<View>(R.id.btn_save)?.setOnClickListener {
            val text = input?.text?.toString() ?: ""
            if (text.isNotEmpty()) {
                submitEntry(text)
                dismiss()
            }
        }
        
        return root
    }

    private fun submitEntry(text: String) {
        val engine = NauticalPlugin.engine
        val plugin = NauticalPlugin.getInstance()
        val loc = plugin?.application?.locationProvider?.lastKnownLocation
        
        // Item 5: Fallback for location - use boat position from Signal K if GPS is null
        val lat = loc?.latitude ?: engine?.getCurrentState()?.latitude
        val lon = loc?.longitude ?: engine?.getCurrentState()?.longitude
        
        if (plugin != null && lat != null && lon != null) {
            val state = engine?.getCurrentState()
            val entry = net.osmand.plus.plugins.nautical.logbook.data.LogbookEntry(
                timestamp = System.currentTimeMillis(),
                latitude = lat,
                longitude = lon,
                sog = loc?.speed?.toDouble() ?: state?.speedOverGround,
                cog = loc?.bearing?.let { Math.toRadians(it.toDouble()) } ?: state?.courseOverGroundTrue,
                heading = state?.headingTrue,
                tws = state?.windSpeedTrue,
                twa = state?.trueWindAngle,
                twd = state?.windDirectionTrue,
                pressure = state?.outsidePressure,
                waterDepth = state?.depthBelowTransducer,
                waterTemp = state?.waterTemperature,
                batteryVoltage = state?.batteryVoltage,
                engineHours = state?.engineRunTime?.let { it / 3600.0 },
                sailPlan = plugin.application.settings.NAUTICAL_ACTIVE_SAIL_PLAN.get() ?: "",
                notes = text
            )
            plugin.pluginScope?.launch {
                plugin.logbookRepository?.insertEntry(entry)
            }
        }
        
        engine?.dispatchCommand("LOGBOOK_ENTRY:$text")
    }

    companion object {
        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            NauticalLogbookEntryDialog().show(fragmentManager, "NauticalLogbookEntryDialog")
        }
    }
}
