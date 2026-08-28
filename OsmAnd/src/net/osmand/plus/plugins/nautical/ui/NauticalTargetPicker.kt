package net.osmand.plus.plugins.nautical.ui

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
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexMessage
import net.osmand.plus.plugins.nautical.s57.S57Object
import net.osmand.plus.settings.fragments.BaseSettingsFragment
import net.osmand.plus.settings.fragments.SettingsScreenType
import net.osmand.plus.utils.ColorUtilities
import net.osmand.shared.aistracker.AisObjType
import net.osmand.shared.aistracker.AisObject
import net.osmand.shared.aistracker.AisObjectConstants
import java.util.Locale

class NauticalTargetPicker : BottomSheetDialogFragment() {

    private var targets: List<Any> = emptyList()

    companion object {
        fun newInstance(targets: List<Any>): NauticalTargetPicker {
            val fragment = NauticalTargetPicker()
            fragment.targets = targets
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = dpToPx(context, 16f)
            setPadding(p, p, p, dpToPx(context, 20f))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Header Title Row
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, 0, 0, dpToPx(context, 12f))
        }

        val titleView = TextView(context).apply {
            text = getString(R.string.nautical_ais_targets_title) + " (${targets.size})"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerLayout.addView(titleView)

        val btnHeaderBuddies = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(context, 36f))
            text = getString(R.string.nautical_ais_buddies_title)
            textSize = 12f
            setIconResource(R.drawable.ic_action_group_list)
            iconSize = dpToPx(context, 16f)
            setOnClickListener {
                dismiss()
                val mapActivity = activity as? net.osmand.plus.activities.MapActivity
                if (mapActivity != null) {
                    NauticalBuddyListFragment.show(mapActivity.supportFragmentManager)
                } else {
                    NauticalBuddyListFragment.show(parentFragmentManager)
                }
            }
        }
        headerLayout.addView(btnHeaderBuddies)
        root.addView(headerLayout)

        // Shortcut Action Buttons (Manage Buddies & Own Vessel Profile)
        val actionsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, 0, 0, dpToPx(context, 12f))
        }

        val btnBuddies = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(context, 44f), 1f).apply {
                marginEnd = dpToPx(context, 8f)
            }
            text = getString(R.string.nautical_manage_buddies)
            textSize = 12f
            setIconResource(R.drawable.ic_action_favorite)
            iconSize = dpToPx(context, 18f)
            setOnClickListener {
                dismiss()
                val mapActivity = activity as? net.osmand.plus.activities.MapActivity
                if (mapActivity != null) {
                    NauticalBuddyListFragment.show(mapActivity.supportFragmentManager)
                } else {
                    NauticalBuddyListFragment.show(parentFragmentManager)
                }
            }
        }
        actionsLayout.addView(btnBuddies)

        val btnOwnVessel = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(context, 44f), 1f)
            text = getString(R.string.nautical_own_vessel_profile)
            textSize = 12f
            setIconResource(R.drawable.ic_action_sail_boat_dark)
            iconSize = dpToPx(context, 18f)
            setOnClickListener {
                dismiss()
                val mapActivity = activity as? net.osmand.plus.activities.MapActivity
                if (mapActivity != null) {
                    BaseSettingsFragment.showInstance(mapActivity, SettingsScreenType.AIS_SETTINGS)
                }
            }
        }
        actionsLayout.addView(btnOwnVessel)
        root.addView(actionsLayout)

        // Sort targets by Range / Distance ascending
        val ownLoc = NauticalPlugin.getInstance()?.application?.locationProvider?.lastKnownLocation
        val sortedTargets = targets.sortedBy { target ->
            val pos = (target as? AisObject)?.position
            if (pos != null && ownLoc != null) {
                val loc = net.osmand.Location("AIS").apply {
                    latitude = pos.latitude
                    longitude = pos.longitude
                }
                ownLoc.distanceTo(loc).toDouble()
            } else {
                Double.MAX_VALUE
            }
        }

        // Scrollable Target List Container
        val scrollView = NestedScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 320f))
        }

        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        sortedTargets.forEach { target ->
            listLayout.addView(createTargetRow(target))
        }
        scrollView.addView(listLayout)
        root.addView(scrollView)

        return root
    }

    private fun createTargetRow(target: Any): View {
        val context = requireContext()
        val plugin = NauticalPlugin.getInstance()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = dpToPx(context, 48f)
            setPadding(0, dpToPx(context, 10f), 0, dpToPx(context, 10f))
            setBackgroundResource(net.osmand.plus.utils.AndroidUtils.resolveAttribute(context, android.R.attr.selectableItemBackground))
            isClickable = true
            setOnClickListener {
                dismiss()
                val mapActivity = activity as? net.osmand.plus.activities.MapActivity ?: return@setOnClickListener
                val arbitrator = NauticalTouchArbitrator(mapActivity)
                arbitrator.showTargetDetails(target)
            }
        }

        if (target is AisObject) {
            val resolvedAis = plugin?.aisManager?.getAisObject(target.mmsi) ?: target
            val themedCtx = androidx.appcompat.view.ContextThemeWrapper(context, R.style.OsmandLightTheme)
            val iconView = ImageView(themedCtx).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(context, 36f), dpToPx(context, 36f)).apply {
                    marginEnd = dpToPx(context, 12f)
                }
                val iconRes = selectBitmap(resolvedAis.objectClass)
                val iconColor = selectColor(resolvedAis.objectClass)
                val drawable = ContextCompat.getDrawable(themedCtx, iconRes)?.mutate()
                if (iconColor != 0) {
                    drawable?.colorFilter = PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
                }
                setImageDrawable(drawable)
                contentDescription = "Target Icon"
            }
            row.addView(iconView)

            val textLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val shipName = resolvedAis.shipName?.trim()
            val titleView = TextView(context).apply {
                text = if (!shipName.isNullOrEmpty()) shipName else "MMSI: ${resolvedAis.mmsi}"
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            textLayout.addView(titleView)

            val subtitleSb = StringBuilder()
            val shipType = resolvedAis.getShipTypeString()
            if (shipType.isNotEmpty()) subtitleSb.append(shipType)
            val status = resolvedAis.getNavStatusString()
            if (status.isNotEmpty()) {
                if (subtitleSb.isNotEmpty()) subtitleSb.append(" • ")
                subtitleSb.append(status)
            }
            val subtitleView = TextView(context).apply {
                text = subtitleSb.toString()
                textSize = 12f
                setTextColor(ColorUtilities.getSecondaryTextColor(requireActivity().application as OsmandApplication, false))
            }
            textLayout.addView(subtitleView)
            row.addView(textLayout)

            // Right side metrics (Range, SOG, CPA)
            val rightLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.END
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            val ownLoc = plugin?.application?.locationProvider?.lastKnownLocation
            val pos = target.position
            if (ownLoc != null && pos != null) {
                val targetLoc = net.osmand.Location("AIS").apply {
                    latitude = pos.latitude
                    longitude = pos.longitude
                }
                val distNm = ownLoc.distanceTo(targetLoc) / 1852.0
                val bearingDeg = (ownLoc.bearingTo(targetLoc) + 360f) % 360f
                val distView = TextView(context).apply {
                    text = String.format(Locale.US, "%.1fnm • %03.0f°", distNm, bearingDeg)
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                rightLayout.addView(distView)
            }

            if (target.sog != AisObjectConstants.INVALID_SOG && target.sog > 0) {
                val sogView = TextView(context).apply {
                    text = String.format(Locale.US, "%.1f kn", target.sog)
                    textSize = 12f
                    setTextColor(ColorUtilities.getSecondaryTextColor(requireActivity().application as OsmandApplication, false))
                }
                rightLayout.addView(sogView)
            }

            if (target.cpa.valid) {
                val isDanger = checkDanger(target, plugin)
                val cpaView = TextView(context).apply {
                    text = String.format(Locale.US, "CPA %.1fnm", target.cpa.cpa)
                    textSize = 11f
                    if (isDanger) {
                        setTextColor(Color.RED)
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    } else {
                        setTextColor(ColorUtilities.getSecondaryTextColor(requireActivity().application as OsmandApplication, false))
                    }
                }
                rightLayout.addView(cpaView)
            }

            row.addView(rightLayout)
        } else {
            val textView = TextView(context).apply {
                val name = when (target) {
                    is NavtexMessage -> getString(R.string.nautical_navtex_target, target.id)
                    is S57Object -> getString(R.string.nautical_s57_target, target.attributes["OBJNAM"] ?: target.acronym)
                    else -> target.toString()
                }
                text = name
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(textView)
        }

        return row
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
            AisObjType.AIS_INVALID -> R.drawable.ic_action_motorboat
            AisObjType.AIS_LANDSTATION -> R.drawable.ic_action_antenna
            AisObjType.AIS_AIRPLANE -> R.drawable.ic_action_aircraft
            AisObjType.AIS_SART -> R.drawable.ic_action_alert
            AisObjType.AIS_ATON -> R.drawable.ic_action_target
            AisObjType.AIS_ATON_VIRTUAL -> R.drawable.ic_action_target
        }
    }

    private fun selectColor(type: AisObjType): Int {
        val app = requireActivity().application as? OsmandApplication
        if (app != null && NauticalPlugin.isNightVision(app)) {
            return 0xFFFF1744.toInt()
        }
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

    private fun dpToPx(context: Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
