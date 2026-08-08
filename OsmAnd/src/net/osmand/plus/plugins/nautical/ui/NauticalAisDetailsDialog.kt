package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import net.osmand.plus.R
import net.osmand.plus.base.BaseBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.shared.aistracker.AisObject
import java.util.Locale

class NauticalAisDetailsDialog : BaseBottomSheetDialogFragment() {

    override fun getThemeUsageContext(): net.osmand.plus.settings.enums.ThemeUsageContext {
        return net.osmand.plus.settings.enums.ThemeUsageContext.APP
    }

    private var mmsi: Int = 0

    companion object {
        const val TAG = "NauticalAisDetailsDialog"
        private const val ARG_MMSI = "arg_mmsi"

        fun show(manager: androidx.fragment.app.FragmentManager, mmsi: Int) {
            val fragment = NauticalAisDetailsDialog()
            fragment.arguments = Bundle().apply { putInt(ARG_MMSI, mmsi) }
            fragment.show(manager, TAG)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mmsi = arguments?.getInt(ARG_MMSI) ?: 0
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.dialog_nautical_ais_details, container, false)
        val ais = NauticalPlugin.getAisObject(mmsi)
        if (ais != null) {
            updateView(view, ais)
        } else {
            dismiss()
        }
        return view
    }

    private fun updateView(view: View, ais: AisObject) {
        val ctx = requireContext()
        val unknown = ctx.getString(R.string.shared_string_none)
        val na = ctx.getString(R.string.nautical_not_available)
        view.findViewById<TextView>(R.id.txt_ship_name).text = ais.shipName ?: unknown
        view.findViewById<TextView>(R.id.txt_mmsi).text = ctx.getString(R.string.nautical_ais_details_mmsi, ais.mmsi)
        view.findViewById<TextView>(R.id.txt_callsign).text = ctx.getString(R.string.nautical_ais_details_callsign, ais.callSign ?: na)
        view.findViewById<TextView>(R.id.txt_imo).text = ctx.getString(R.string.nautical_ais_details_imo, if (ais.imo != 0) ais.imo.toString() else na)
        view.findViewById<TextView>(R.id.txt_type).text = ctx.getString(R.string.nautical_ais_details_type, ais.getShipTypeString())
        view.findViewById<TextView>(R.id.txt_status).text = ctx.getString(R.string.nautical_ais_details_status, ais.getNavStatusString())
        
        view.findViewById<TextView>(R.id.txt_destination).text = ctx.getString(R.string.nautical_ais_details_destination, ais.destination ?: unknown)
        
        val etaStr = if (ais.etaMon != 0) {
            String.format(Locale.US, "%02d-%02d %02d:%02d", ais.etaDay, ais.etaMon, ais.etaHour, ais.etaMin)
        } else na
        view.findViewById<TextView>(R.id.txt_eta).text = ctx.getString(R.string.nautical_ais_details_eta, etaStr)
        
        val draughtStr = if (ais.draught > 0) String.format(Locale.US, "%.1f %s", ais.draught, ctx.getString(R.string.nautical_unit_meters_short)) else na
        view.findViewById<TextView>(R.id.txt_draught).text = ctx.getString(R.string.nautical_ais_details_draught, draughtStr)
        
        val pos = ais.position
        val posStr = if (pos != null) String.format(Locale.US, "%.4f, %.4f", pos.latitude, pos.longitude) else unknown
        view.findViewById<TextView>(R.id.txt_position).text = ctx.getString(R.string.nautical_ais_details_position, posStr)
        
        val sogCogStr = String.format(Locale.US, "%.1f kn / %.0f°", ais.sog, ais.cog)
        view.findViewById<TextView>(R.id.txt_sog_cog).text = ctx.getString(R.string.nautical_ais_details_sog_cog, sogCogStr)

        val btnBuddy = view.findViewById<Button>(R.id.btn_toggle_buddy)
        val isBuddy = NauticalPlugin.engine?.getCurrentState()?.aisBuddies?.contains(ais.mmsi) ?: false
        btnBuddy.text = if (isBuddy) ctx.getString(R.string.nautical_remove_from_buddies) else ctx.getString(R.string.nautical_add_to_buddies)
        btnBuddy.setOnClickListener {
            val engine = NauticalPlugin.engine
            val current = engine?.getCurrentState()?.aisBuddies?.toMutableSet() ?: mutableSetOf()
            if (isBuddy) current.remove(ais.mmsi) else current.add(ais.mmsi)
            engine?.sendDelta("navigation.aisBuddies", current.toList())
            dismiss()
        }
    }
}
