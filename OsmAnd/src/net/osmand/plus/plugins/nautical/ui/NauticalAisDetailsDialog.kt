package net.osmand.plus.plugins.nautical.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.base.BaseBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.NauticalAisManager
import net.osmand.plus.plugins.nautical.ui.widgets.NauticalVhfBottomSheet
import net.osmand.shared.aistracker.AisObjType
import net.osmand.shared.aistracker.AisObject
import net.osmand.shared.aistracker.AisObjectConstants
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
            if (manager.isStateSaved) return
            if (manager.findFragmentByTag(TAG) == null) {
                val fragment = NauticalAisDetailsDialog()
                fragment.arguments = Bundle().apply { putInt(ARG_MMSI, mmsi) }
                fragment.show(manager, TAG)
            }
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

            viewLifecycleOwner.lifecycleScope.launch {
                NauticalPlugin.getInstance()?.aisManager?.aisEvents?.filter {
                    (it is NauticalAisManager.AisEvent.Updated) && it.obj.mmsi == mmsi
                }?.collect { event ->
                    updateView(view, (event as NauticalAisManager.AisEvent.Updated).obj)
                }
            }
        } else {
            dismiss()
        }
        return view
    }

    private fun updateView(view: View, ais: AisObject) {
        val base = runCatching { requireActivity() }.getOrNull() ?: requireContext()
        val themedCtx = androidx.appcompat.view.ContextThemeWrapper(base, R.style.OsmandLightTheme)
        val plugin = NauticalPlugin.getInstance()
        val aisManager = plugin?.aisManager
        val unknown = getString(R.string.shared_string_none)
        val na = getString(R.string.nautical_not_available)

        // Header: Icon, Name, MMSI, Call Sign, IMO
        val imgIcon = view.findViewById<ImageView>(R.id.img_vessel_icon)
        val iconRes = selectBitmap(ais.objectClass)
        val iconColor = selectColor(ais.objectClass)
        val iconDrawable = ContextCompat.getDrawable(themedCtx, iconRes)?.mutate()
        if (iconColor != 0) {
            iconDrawable?.colorFilter = PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
        }
        imgIcon?.setImageDrawable(iconDrawable)

        val shipName = ais.shipName?.trim()
        view.findViewById<TextView>(R.id.txt_ship_name).text = if (!shipName.isNullOrEmpty()) shipName else "MMSI: ${ais.mmsi}"

        val isBuddy = aisManager?.isBuddy(ais.mmsi) ?: false
        view.findViewById<ImageView>(R.id.img_buddy_star)?.visibility = if (isBuddy) View.VISIBLE else View.GONE

        val country = getMidCountry(ais.mmsi)
        val mmsiSb = StringBuilder("MMSI: ${ais.mmsi}")
        if (!ais.callSign.isNullOrEmpty()) {
            mmsiSb.append(" • Call: ").append(ais.callSign)
        }
        if (!country.isNullOrEmpty()) {
            mmsiSb.append(" • Flag: ").append(country)
        }
        if (ais.imo != 0) {
            mmsiSb.append(" • IMO: ").append(ais.imo)
        }
        view.findViewById<TextView>(R.id.txt_mmsi_callsign).text = mmsiSb.toString()

        val shipType = ais.getShipTypeString()
        val isClassB = ais.msgTypes.any { it in setOf(18, 19, 24) }
        view.findViewById<TextView>(R.id.txt_ship_type_badge).text = if (shipType.isNotEmpty()) shipType else "Class ${if (isClassB) "B" else "A"}"

        // Threat / CPA Warning
        val cpaWarningLayout = view.findViewById<LinearLayout>(R.id.layout_cpa_warning)
        val txtCpaWarning = view.findViewById<TextView>(R.id.txt_cpa_warning_text)
        val isDanger = checkDanger(ais, plugin)
        if (isDanger && ais.cpa.valid) {
            cpaWarningLayout.visibility = View.VISIBLE
            val tcpaMin = ais.cpa.tcpa * 60.0
            txtCpaWarning.text = String.format(Locale.US, "COLLISION WARNING: CPA %.2f nm in %.1f min", ais.cpa.cpa, tcpaMin)
        } else {
            cpaWarningLayout.visibility = View.GONE
        }

        // Metrics Grid
        val sogStr = if (ais.sog != AisObjectConstants.INVALID_SOG) String.format(Locale.US, "SOG: %.1f kn", ais.sog) else "SOG: $na"
        view.findViewById<TextView>(R.id.txt_sog).text = sogStr

        val cogStr = if (ais.cog != AisObjectConstants.INVALID_COG) String.format(Locale.US, "COG: %.0f°", ais.cog) else "COG: $na"
        view.findViewById<TextView>(R.id.txt_cog).text = cogStr

        val hdgStr = if (ais.heading != AisObjectConstants.INVALID_HEADING) String.format(Locale.US, "HDG: %d°", ais.heading) else "HDG: $na"
        view.findViewById<TextView>(R.id.txt_heading).text = hdgStr

        val rotStr = if (ais.rot != AisObjectConstants.INVALID_ROT && ais.rot != 0.0) String.format(Locale.US, "ROT: %.1f°/m", ais.rot) else "ROT: $na"
        view.findViewById<TextView>(R.id.txt_rot).text = rotStr

        // Range and Bearing from own ship
        val ownLoc = plugin?.application?.locationProvider?.lastKnownLocation
        val pos = ais.position
        if (ownLoc != null && pos != null) {
            val targetLoc = net.osmand.Location("AIS").apply {
                latitude = pos.latitude
                longitude = pos.longitude
            }
            val distNm = ownLoc.distanceTo(targetLoc) / 1852.0
            val bearingDeg = (ownLoc.bearingTo(targetLoc) + 360f) % 360f
            view.findViewById<TextView>(R.id.txt_range_bearing).text = String.format(Locale.US, "Range: %.2f nm • %03.0f°", distNm, bearingDeg)
        } else {
            view.findViewById<TextView>(R.id.txt_range_bearing).text = "Range: $na"
        }

        val cpaTcpaStr = if (ais.cpa.valid) {
            String.format(Locale.US, "CPA: %.2f nm (%.1fm)", ais.cpa.cpa, ais.cpa.tcpa * 60.0)
        } else {
            "CPA: $na"
        }
        val txtCpa = view.findViewById<TextView>(R.id.txt_cpa_tcpa)
        txtCpa.text = cpaTcpaStr
        if (isDanger && ais.cpa.valid) {
            txtCpa.setTextColor(Color.RED)
        } else {
            txtCpa.setTextColor(net.osmand.plus.utils.AndroidUtils.getColorFromAttr(requireContext(), android.R.attr.textColorPrimary))
        }

        view.findViewById<TextView>(R.id.txt_status).text = "Status: ${ais.getNavStatusString()}"

        val totalLen = ais.dimensionToBow + ais.dimensionToStern
        val totalBeam = ais.dimensionToPort + ais.dimensionToStarboard
        val dimStr = if (totalLen > 0 && totalBeam > 0) "${totalLen}m x ${totalBeam}m" else na
        view.findViewById<TextView>(R.id.txt_dimensions).text = "Dim: $dimStr"

        val draughtStr = if (ais.draught > 0) String.format(Locale.US, "%.1f m", ais.draught) else na
        view.findViewById<TextView>(R.id.txt_draught).text = "Draught: $draughtStr"

        view.findViewById<TextView>(R.id.txt_destination).text = "Dest: ${ais.destination ?: unknown}"

        val etaStr = if (ais.etaMon != 0) {
            String.format(Locale.US, "%02d-%02d %02d:%02d", ais.etaMon, ais.etaDay, ais.etaHour, ais.etaMin)
        } else na
        view.findViewById<TextView>(R.id.txt_eta).text = "ETA: $etaStr"

        val posStr = if (pos != null) String.format(Locale.US, "%.4f, %.4f", pos.latitude, pos.longitude) else unknown
        view.findViewById<TextView>(R.id.txt_position).text = "Pos: $posStr"

        // Actions
        view.findViewById<MaterialButton>(R.id.btn_show_on_map).setOnClickListener {
            if (pos != null) {
                val activity = activity as? MapActivity
                activity?.mapView?.setLatLon(pos.latitude, pos.longitude)
                if ((activity?.mapView?.zoom ?: 0) < 14) {
                    activity?.mapView?.setIntZoom(15)
                }
                plugin?.aisAisLayer?.setFollowedTarget(null)
            }
            dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btn_follow_target).setOnClickListener {
            plugin?.aisAisLayer?.setFollowedTarget(ais.mmsi)
            if (pos != null) {
                val activity = activity as? MapActivity
                activity?.mapView?.setLatLon(pos.latitude, pos.longitude)
                if ((activity?.mapView?.zoom ?: 0) < 14) {
                    activity?.mapView?.setIntZoom(15)
                }
            }
            dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btn_vhf_callout).setOnClickListener {
            val activity = activity as? MapActivity
            if (activity != null) {
                NauticalPlugin.engine?.sendDelta("communication.vhf.channel", "16")
                NauticalPlugin.engine?.sendDelta("communication.vhf.dscTarget", ais.mmsi.toString())
                NauticalVhfBottomSheet.show(activity.supportFragmentManager)
            }
            dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btn_copy_mmsi).setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("AIS MMSI", ais.mmsi.toString())
            clipboard?.setPrimaryClip(clip)
            plugin?.application?.showToastMessage(R.string.nautical_mmsi_copied, ais.mmsi)
        }

        val btnTrack = view.findViewById<MaterialButton>(R.id.btn_toggle_track)
        val isTrackOn = aisManager?.isTrackEnabled(ais.mmsi) ?: false
        btnTrack.text = if (isTrackOn) getString(R.string.nautical_hide_track) else getString(R.string.nautical_show_track)
        btnTrack.setOnClickListener {
            val newTrackState = aisManager?.toggleTrack(ais.mmsi) ?: false
            btnTrack.text = if (newTrackState) getString(R.string.nautical_hide_track) else getString(R.string.nautical_show_track)
        }

        val btnBuddy = view.findViewById<MaterialButton>(R.id.btn_toggle_buddy)
        btnBuddy.text = if (isBuddy) getString(R.string.nautical_remove_from_buddies) else getString(R.string.nautical_add_to_buddies)
        btnBuddy.setIconResource(R.drawable.ic_action_favorite)
        btnBuddy.setOnClickListener {
            val newBuddyState = aisManager?.toggleBuddy(ais.mmsi) ?: false
            btnBuddy.text = if (newBuddyState) getString(R.string.nautical_remove_from_buddies) else getString(R.string.nautical_add_to_buddies)
            btnBuddy.setIconResource(R.drawable.ic_action_favorite)
            view.findViewById<ImageView>(R.id.img_buddy_star)?.visibility = if (newBuddyState) View.VISIBLE else View.GONE

            val vesselName = ais.shipName?.trim().takeIf { !it.isNullOrEmpty() } ?: "MMSI ${ais.mmsi}"
            if (newBuddyState) {
                plugin?.application?.showToastMessage(getString(R.string.nautical_added_to_buddies, vesselName))
            } else {
                plugin?.application?.showToastMessage(getString(R.string.nautical_removed_from_buddies, vesselName))
            }
        }

        view.findViewById<MaterialButton>(R.id.btn_view_buddies)?.setOnClickListener {
            val activity = activity as? MapActivity
            if (activity != null) {
                NauticalBuddyListFragment.show(activity.supportFragmentManager)
            }
            dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btn_set_cpa_alarm).setOnClickListener {
            val ctx = requireContext()
            val input = android.widget.EditText(ctx).apply {
                hint = "Distance in NM"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            AlertDialog.Builder(ctx)
                .setTitle(R.string.nautical_set_cpa_alarm)
                .setMessage(R.string.nautical_cpa_alarm_prompt)
                .setView(input)
                .setPositiveButton(R.string.shared_string_save) { _, _ ->
                    val dist = input.text.toString().toDoubleOrNull()
                    if (dist != null && dist > 0.0) {
                        plugin?.aisManager?.setCustomCpaThreshold(ais.mmsi, dist)
                        plugin?.application?.showToastMessage(getString(R.string.nautical_cpa_alarm_set, dist))
                    }
                }
                .setNegativeButton(R.string.shared_string_cancel, null)
                .show()
        }
    }

    private fun getMidCountry(mmsi: Int): String? {
        val mid = mmsi.toString().take(3).toIntOrNull() ?: return null
        return when (mid) {
            in 201..279 -> "Europe"
            in 301..379 -> "North/Central America"
            in 401..479 -> "Asia"
            in 501..579 -> "Oceania"
            in 601..679 -> "Africa"
            in 701..779 -> "South America"
            else -> null
        }
    }

    private fun checkDanger(ais: AisObject, plugin: NauticalPlugin?): Boolean {
        val extras = plugin?.aisManager?.getAisExtras(ais.mmsi)
        if (extras?.hasCpaWarning == true) return true
        if (!ais.cpa.valid || !ais.isMovable() || plugin == null) return false
        val cpaDist = plugin.aisCpaWarningDistance.get().toDouble()
        val cpaTime = plugin.aisCpaWarningTime.get().toDouble()
        val tcpaSec = ais.cpa.tcpa * 3600.0
        return (ais.cpa.tcpa > 0 && ais.cpa.cpa <= cpaDist && tcpaSec <= cpaTime && ais.cpa.t1 >= 0 && ais.cpa.t2 >= 0)
    }

    private fun selectBitmap(type: AisObjType): Int {
        return when (type) {
            AisObjType.AIS_VESSEL,
            AisObjType.AIS_VESSEL_SPORT,
            AisObjType.AIS_VESSEL_FAST,
            AisObjType.AIS_VESSEL_PASSENGER,
            AisObjType.AIS_VESSEL_FREIGHT,
            AisObjType.AIS_VESSEL_COMMERCIAL,
            AisObjType.AIS_VESSEL_AUTHORITIES,
            AisObjType.AIS_VESSEL_SAR,
            AisObjType.AIS_VESSEL_OTHER,
            AisObjType.AIS_INVALID -> R.drawable.mm_ais_vessel
            AisObjType.AIS_LANDSTATION -> R.drawable.mm_ais_land
            AisObjType.AIS_AIRPLANE -> R.drawable.mm_ais_plane
            AisObjType.AIS_SART -> R.drawable.mm_ais_sar
            AisObjType.AIS_ATON -> R.drawable.mm_ais_aton
            AisObjType.AIS_ATON_VIRTUAL -> R.drawable.mm_ais_aton_virt
        }
    }

    private fun selectColor(type: AisObjType): Int {
        return when (type) {
            AisObjType.AIS_VESSEL -> Color.GREEN
            AisObjType.AIS_VESSEL_SPORT -> Color.YELLOW
            AisObjType.AIS_VESSEL_FAST -> Color.BLUE
            AisObjType.AIS_VESSEL_PASSENGER -> Color.CYAN
            AisObjType.AIS_VESSEL_FREIGHT -> Color.GRAY
            AisObjType.AIS_VESSEL_COMMERCIAL -> Color.LTGRAY
            AisObjType.AIS_VESSEL_AUTHORITIES -> Color.argb(0xff, 0x55, 0x6b, 0x2f)
            AisObjType.AIS_VESSEL_SAR -> Color.argb(0xff, 0xfa, 0x80, 0x72)
            AisObjType.AIS_VESSEL_OTHER -> Color.argb(0xff, 0x00, 0xbf, 0xff)
            AisObjType.AIS_LANDSTATION -> Color.argb(0xff, 0x8b, 0x45, 0x13)
            AisObjType.AIS_AIRPLANE -> Color.argb(0xff, 0x93, 0x70, 0xdb)
            AisObjType.AIS_SART -> Color.RED
            AisObjType.AIS_ATON, AisObjType.AIS_ATON_VIRTUAL -> Color.argb(0xff, 0xff, 0xa5, 0x00)
            else -> 0
        }
    }
}
