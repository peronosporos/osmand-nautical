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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexMessage
import net.osmand.plus.plugins.nautical.s57.S57Object
import net.osmand.plus.utils.ColorUtilities
import net.osmand.shared.aistracker.AisObjType
import net.osmand.shared.aistracker.AisObject
import net.osmand.shared.aistracker.AisObjectConstants
import net.osmand.util.MapUtils
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
            setPadding(dpToPx(context, 16f), dpToPx(context, 16f), 
                       dpToPx(context, 16f), dpToPx(context, 24f))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        root.addView(TextView(context).apply {
            text = getString(R.string.nautical_target_picker_title)
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(context, 16f))
        })

        targets.forEach { target ->
            root.addView(createTargetRow(target))
        }

        return root
    }

    private fun createTargetRow(target: Any): View {
        val context = requireContext()
        val plugin = NauticalPlugin.getInstance()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
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
            val iconView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(context, 36f), dpToPx(context, 36f)).apply {
                    marginEnd = dpToPx(context, 12f)
                }
                val iconRes = selectBitmap(target.objectClass)
                val iconColor = selectColor(target.objectClass)
                val drawable = ContextCompat.getDrawable(context, iconRes)?.mutate()
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

            val titleView = TextView(context).apply {
                text = target.shipName ?: "MMSI: ${target.mmsi}"
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            textLayout.addView(titleView)

            val subtitleSb = StringBuilder()
            val shipType = target.getShipTypeString()
            if (shipType.isNotEmpty()) subtitleSb.append(shipType)
            val status = target.getNavStatusString()
            if (status.isNotEmpty()) {
                if (subtitleSb.isNotEmpty()) subtitleSb.append(" • ")
                subtitleSb.append(status)
            }
            val subtitleView = TextView(context).apply {
                text = subtitleSb.toString()
                textSize = 13f
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
                val distNm = MapUtils.getDistance(ownLoc.latitude, ownLoc.longitude, pos.latitude, pos.longitude) / 1852.0
                val bearingDeg = (MapUtils.getBearing(ownLoc.latitude, ownLoc.longitude, pos.latitude, pos.longitude) + 360.0) % 360.0
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
                textSize = 16f
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

    private fun dpToPx(context: Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
