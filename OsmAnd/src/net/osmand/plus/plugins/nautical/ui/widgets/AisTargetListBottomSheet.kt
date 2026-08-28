package net.osmand.plus.plugins.nautical.ui.widgets

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.base.BaseBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.AisSortMode
import net.osmand.plus.plugins.nautical.engine.AisTargetSummary
import net.osmand.plus.plugins.nautical.engine.MarineStateConstants
import net.osmand.plus.plugins.nautical.ui.NauticalAisDetailsDialog
import net.osmand.plus.settings.enums.ThemeUsageContext
import net.osmand.plus.utils.AndroidUtils
import net.osmand.shared.util.KMapUtils
import java.util.Locale

class AisTargetListBottomSheet : BaseBottomSheetDialogFragment() {

    override fun getThemeUsageContext(): ThemeUsageContext {
        return ThemeUsageContext.APP
    }

    private var currentSortMode: AisSortMode = AisSortMode.THREAT_CPA
    private var searchQuery: String = ""
    private lateinit var adapter: TargetSummaryAdapter
    private var txtTitle: TextView? = null
    private var layoutEmpty: View? = null
    private var rvTargets: RecyclerView? = null
    private var toggleGroup: MaterialButtonToggleGroup? = null
    private var editSearch: com.google.android.material.textfield.TextInputEditText? = null
    private var cardMobEmergency: com.google.android.material.card.MaterialCardView? = null
    private var txtMobEmergencyTitle: TextView? = null
    private var btnMobSetSarCourse: MaterialButton? = null
    private var sliderPredictiveHorizon: com.google.android.material.slider.Slider? = null
    private var txtPredictiveTime: TextView? = null

    companion object {
        const val TAG = "AisTargetListBottomSheet"

        @JvmStatic
        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.isStateSaved) return
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                AisTargetListBottomSheet().show(fragmentManager, TAG)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.bottom_sheet_ais_target_list, container, false)

        txtTitle = view.findViewById(R.id.txt_title)
        layoutEmpty = view.findViewById(R.id.layout_empty_targets)
        rvTargets = view.findViewById(R.id.rv_targets)
        toggleGroup = view.findViewById(R.id.toggle_sort_mode)
        editSearch = view.findViewById(R.id.edit_search_ais)
        cardMobEmergency = view.findViewById(R.id.card_mob_emergency)
        txtMobEmergencyTitle = view.findViewById(R.id.txt_mob_emergency_title)
        btnMobSetSarCourse = view.findViewById(R.id.btn_mob_set_sar_course)
        sliderPredictiveHorizon = view.findViewById(R.id.slider_predictive_horizon)
        txtPredictiveTime = view.findViewById(R.id.txt_predictive_time)
        val btnToggleVector = view.findViewById<MaterialButton>(R.id.btn_toggle_vector_mode)

        val aisLayer = NauticalPlugin.getInstance()?.aisAisLayer
        val currentHorizon = aisLayer?.predictiveHorizonMinutes ?: 0
        sliderPredictiveHorizon?.value = currentHorizon.toFloat()
        txtPredictiveTime?.text = if (currentHorizon > 0) "Forward Horizon: T +${currentHorizon}m" else "Forward Horizon: Real-time (T +0m)"

        sliderPredictiveHorizon?.addOnChangeListener { _, value, _ ->
            val minutes = value.toInt()
            txtPredictiveTime?.text = if (minutes > 0) "Forward Horizon: T +${minutes}m" else "Forward Horizon: Real-time (T +0m)"
            NauticalPlugin.getInstance()?.aisAisLayer?.predictiveHorizonMinutes = minutes
            val mapActivity = activity as? MapActivity
            mapActivity?.mapView?.refreshMap()
        }

        val updateVectorBtn = {
            val isRel = aisLayer?.isRelativeMotionVectorMode == true
            btnToggleVector?.text = if (isRel) "MOTION VECTORS: RELATIVE" else "MOTION VECTORS: TRUE"
            btnToggleVector?.setIconResource(if (isRel) R.drawable.ic_action_direction_movement else R.drawable.ic_action_navigation_outlined)
        }
        updateVectorBtn()

        btnToggleVector?.setOnClickListener {
            if (aisLayer != null) {
                aisLayer.isRelativeMotionVectorMode = !aisLayer.isRelativeMotionVectorMode
                updateVectorBtn()
                val mapActivity = activity as? MapActivity
                mapActivity?.mapView?.refreshMap()
            }
        }

        val isNightVision = app?.let { NauticalPlugin.isNightVision(it) } ?: false
        if (isNightVision) {
            view.setBackgroundColor(0xEE120000.toInt())
            view.findViewById<View>(R.id.drag_handle)?.setBackgroundColor(0x80FF1744.toInt())
            txtTitle?.setTextColor(0xFFFF1744.toInt())
            editSearch?.setTextColor(0xFFFF1744.toInt())
            editSearch?.setHintTextColor(0x80FF1744.toInt())
            txtPredictiveTime?.setTextColor(0xFFFF8A80.toInt())
            sliderPredictiveHorizon?.thumbTintList = ColorStateList.valueOf(0xFFFF1744.toInt())
            sliderPredictiveHorizon?.trackActiveTintList = ColorStateList.valueOf(0xFFFF1744.toInt())
            btnToggleVector?.setTextColor(0xFFFF1744.toInt())
            btnToggleVector?.iconTint = ColorStateList.valueOf(0xFFFF1744.toInt())
        }

        view.findViewById<ImageButton>(R.id.btn_close)?.setOnClickListener {
            dismiss()
        }

        editSearch?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                refreshTargets()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        adapter = TargetSummaryAdapter(
            onRowClick = { target ->
                dismiss()
                val mapActivity = activity as? MapActivity
                if (MarineStateConstants.isValidLat(target.lat) && MarineStateConstants.isValidLon(target.lon)) {
                    mapActivity?.mapView?.setLatLon(target.lat, target.lon)
                    if ((mapActivity?.mapView?.zoom ?: 0) < 14) {
                        mapActivity?.mapView?.setIntZoom(15)
                    }
                }
                val plugin = NauticalPlugin.getInstance()
                plugin?.aisAisLayer?.setFollowedTarget(target.mmsi)
                mapActivity?.mapView?.refreshMap()
            },
            onRowLongClick = { target ->
                if (!parentFragmentManager.isStateSaved) {
                    NauticalAisDetailsDialog.show(parentFragmentManager, target.mmsi)
                }
            },
            onMuteClick = { target ->
                val plugin = NauticalPlugin.getInstance()
                val isNowMuted = plugin?.aisManager?.toggleMute(target.mmsi) ?: !target.isMuted
                val msg = if (isNowMuted) {
                    getString(R.string.nautical_target_muted, target.name)
                } else {
                    getString(R.string.nautical_target_unmuted, target.name)
                }
                app?.showToastMessage(msg)
                refreshTargets()
            }
        )

        rvTargets?.layoutManager = LinearLayoutManager(requireContext())
        rvTargets?.adapter = adapter

        // Setup Sort Toggle Group
        toggleGroup?.check(R.id.btn_sort_threat)
        updateToggleButtonsAppearance(R.id.btn_sort_threat)

        toggleGroup?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentSortMode = when (checkedId) {
                    R.id.btn_sort_threat -> AisSortMode.THREAT_CPA
                    R.id.btn_sort_nearest -> AisSortMode.DISTANCE
                    R.id.btn_sort_name -> AisSortMode.NAME_ALPHA
                    else -> AisSortMode.THREAT_CPA
                }
                updateToggleButtonsAppearance(checkedId)
                refreshTargets()
            }
        }

        // Live Real-Time Updates
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    val plugin = NauticalPlugin.getInstance()
                    plugin?.aisManager?.aisEvents?.collect {
                        refreshTargets()
                    }
                }
                launch {
                    while (isActive) {
                        refreshTargets()
                        delay(2000L)
                    }
                }
            }
        }

        refreshTargets()
        return view
    }

    private fun updateToggleButtonsAppearance(checkedId: Int) {
        val group = toggleGroup ?: return
        val activeColor = AndroidUtils.getColorFromAttr(requireContext(), R.attr.active_color_primary)
        val defaultIconColor = AndroidUtils.getColorFromAttr(requireContext(), R.attr.icon_color_primary)
        val defaultStrokeColor = AndroidUtils.getColorFromAttr(requireContext(), R.attr.active_color_primary)

        val btnThreat = group.findViewById<MaterialButton>(R.id.btn_sort_threat)
        val btnNearest = group.findViewById<MaterialButton>(R.id.btn_sort_nearest)
        val btnName = group.findViewById<MaterialButton>(R.id.btn_sort_name)

        val buttons = listOfNotNull(btnThreat, btnNearest, btnName)
        for (btn in buttons) {
            val isChecked = btn.id == checkedId
            if (isChecked) {
                btn.backgroundTintList = ColorStateList.valueOf(activeColor)
                btn.iconTint = ColorStateList.valueOf(Color.WHITE)
                btn.setTextColor(Color.WHITE)
            } else {
                btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                btn.iconTint = ColorStateList.valueOf(defaultIconColor)
                btn.setTextColor(AndroidUtils.getColorFromAttr(requireContext(), android.R.attr.textColorPrimary))
            }
            btn.strokeColor = ColorStateList.valueOf(defaultStrokeColor)
        }
    }

    private fun refreshTargets() {
        val plugin = NauticalPlugin.getInstance() ?: return
        val allTargets = plugin.aisManager?.getActiveTargets(currentSortMode) ?: emptyList()
        val targets = if (searchQuery.isEmpty()) {
            allTargets
        } else {
            allTargets.filter { target ->
                target.name.contains(searchQuery, ignoreCase = true) ||
                target.mmsi.toString().contains(searchQuery) ||
                (target.callSign?.contains(searchQuery, ignoreCase = true) == true)
            }
        }

        adapter.submitList(targets)
        val count = targets.size
        txtTitle?.text = if (count > 0) {
            "${getString(R.string.nautical_ais_targets_title)} ($count)"
        } else {
            getString(R.string.nautical_ais_targets_title)
        }

        val mobTarget = allTargets.firstOrNull { it.mmsi in 970000000..974999999 }
        val ownLoc = app?.locationProvider?.lastKnownLocation
        if (mobTarget != null && ownLoc != null && MarineStateConstants.isValidLat(mobTarget.lat) && MarineStateConstants.isValidLon(mobTarget.lon)) {
            cardMobEmergency?.visibility = View.VISIBLE
            val bearing = KMapUtils.getBearing(ownLoc.latitude, ownLoc.longitude, mobTarget.lat, mobTarget.lon)
            val distNm = net.osmand.util.MapUtils.getDistance(ownLoc.latitude, ownLoc.longitude, mobTarget.lat, mobTarget.lon) / 1852.0
            txtMobEmergencyTitle?.text = String.format(Locale.US, "AIS-MOB ACTIVATED: Bearing %.0f° / Distance %.1f NM", bearing, distNm)
            btnMobSetSarCourse?.setOnClickListener {
                dismiss()
                val mapActivity = activity as? MapActivity
                mapActivity?.mapView?.setLatLon(mobTarget.lat, mobTarget.lon)
                mapActivity?.mapView?.setIntZoom(16)
                NauticalPlugin.autopilot?.commandHeading(bearing)
                app?.showToastMessage("Direct SAR course set to ${mobTarget.name}")
            }
        } else {
            cardMobEmergency?.visibility = View.GONE
        }

        if (targets.isEmpty()) {
            layoutEmpty?.visibility = View.VISIBLE
            rvTargets?.visibility = View.GONE
        } else {
            layoutEmpty?.visibility = View.GONE
            rvTargets?.visibility = View.VISIBLE
        }
    }

    private class TargetSummaryAdapter(
        private val onRowClick: (AisTargetSummary) -> Unit,
        private val onRowLongClick: (AisTargetSummary) -> Unit,
        private val onMuteClick: (AisTargetSummary) -> Unit
    ) : ListAdapter<AisTargetSummary, TargetViewHolder>(TargetDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TargetViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_ais_target_card, parent, false)
            return TargetViewHolder(view)
        }

        override fun onBindViewHolder(holder: TargetViewHolder, position: Int) {
            holder.bind(getItem(position), onRowClick, onRowLongClick, onMuteClick)
        }
    }

    private class TargetDiffCallback : DiffUtil.ItemCallback<AisTargetSummary>() {
        override fun areItemsTheSame(oldItem: AisTargetSummary, newItem: AisTargetSummary): Boolean =
            oldItem.mmsi == newItem.mmsi

        override fun areContentsTheSame(oldItem: AisTargetSummary, newItem: AisTargetSummary): Boolean =
            oldItem == newItem
    }

    private class TargetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtThreatBadge: TextView = view.findViewById(R.id.txt_threat_badge)
        private val txtVesselName: TextView = view.findViewById(R.id.txt_vessel_name)
        private val txtVesselSubtext: TextView = view.findViewById(R.id.txt_vessel_subtext)
        private val btnMute: ImageButton = view.findViewById(R.id.btn_mute)
        private val txtRangeBearing: TextView = view.findViewById(R.id.txt_range_bearing)
        private val txtSogCog: TextView = view.findViewById(R.id.txt_sog_cog)
        private val txtCpaTcpa: TextView = view.findViewById(R.id.txt_cpa_tcpa)

        fun bind(
            item: AisTargetSummary,
            onRowClick: (AisTargetSummary) -> Unit,
            onRowLongClick: (AisTargetSummary) -> Unit,
            onMuteClick: (AisTargetSummary) -> Unit
        ) {
            val context = itemView.context

            // 1. Vessel Name & Identifiers
            txtVesselName.text = item.name

            val subtextSb = StringBuilder("MMSI: ${item.mmsi}")
            if (!item.callSign.isNullOrEmpty()) {
                subtextSb.append(" • Call: ").append(item.callSign)
            }
            if (item.type.isNotEmpty()) {
                subtextSb.append(" • ").append(item.type)
            }
            txtVesselSubtext.text = subtextSb.toString()

            // 2. Threat Status Indicator Badge
            val isThreatCpa = (item.cpaNm ?: Double.MAX_VALUE) < 1.0 && (item.tcpaSec ?: -1.0) > 0
            val isDanger = item.isDangerous || isThreatCpa
            val isCaution = !isDanger && (((item.cpaNm ?: Double.MAX_VALUE) < 2.0 && (item.tcpaSec ?: -1.0) > 0) || (item.sogKnots > 0.5 && item.rangeMeters < 3704.0))

            val app = context.applicationContext as? net.osmand.plus.OsmandApplication
            val isNightVision = app?.let { NauticalPlugin.isNightVision(it) } ?: false

            when {
                isDanger -> {
                    txtThreatBadge.text = context.getString(R.string.nautical_threat_danger)
                    txtThreatBadge.setTextColor(Color.WHITE)
                    val dangerColor = if (isNightVision) 0xFFFF1744.toInt() else ContextCompat.getColor(context, R.color.nautical_status_red)
                    setBadgeBackground(txtThreatBadge, dangerColor)
                    txtCpaTcpa.setTextColor(dangerColor)
                }
                isCaution -> {
                    txtThreatBadge.text = context.getString(R.string.nautical_threat_caution)
                    txtThreatBadge.setTextColor(if (isNightVision) 0xEE120000.toInt() else Color.WHITE)
                    val cautionColor = if (isNightVision) 0xFFFF8A80.toInt() else ContextCompat.getColor(context, R.color.nautical_status_orange)
                    setBadgeBackground(txtThreatBadge, cautionColor)
                    txtCpaTcpa.setTextColor(cautionColor)
                }
                else -> {
                    txtThreatBadge.text = context.getString(R.string.nautical_threat_safe)
                    txtThreatBadge.setTextColor(if (isNightVision) 0xFFFF8A80.toInt() else Color.WHITE)
                    val safeColor = if (isNightVision) 0x80B71C1C.toInt() else ContextCompat.getColor(context, R.color.nautical_status_green)
                    setBadgeBackground(txtThreatBadge, safeColor)
                    txtCpaTcpa.setTextColor(if (isNightVision) 0xFFFF8A80.toInt() else AndroidUtils.getColorFromAttr(context, android.R.attr.textColorPrimary))
                }
            }

            if (isNightVision) {
                (itemView as? androidx.cardview.widget.CardView)?.setCardBackgroundColor(0xEE120000.toInt())
                txtVesselName.setTextColor(0xFFFF1744.toInt())
                txtVesselSubtext.setTextColor(0xFFFF8A80.toInt())
                txtRangeBearing.setTextColor(0xFFFF8A80.toInt())
                txtSogCog.setTextColor(0xFFFF8A80.toInt())
            }

            // 3. Range & Bearing from Own Vessel
            if (item.rangeMeters < Double.MAX_VALUE / 2) {
                val rangeNm = item.rangeMeters / 1852.0
                txtRangeBearing.text = String.format(Locale.US, "%.2f NM • %03.0f°", rangeNm, item.bearingDeg)
            } else {
                txtRangeBearing.text = "-- • --"
            }

            // 4. SOG & COG
            txtSogCog.text = String.format(Locale.US, "%.1f kn • %03.0f°", item.sogKnots, item.cogDeg)

            // 5. Live CPA & TCPA Readouts (e.g., "CPA: 0.3 NM in 4m 12s")
            val cpaNm = item.cpaNm
            val tcpaSec = item.tcpaSec
            if (cpaNm != null) {
                val cpaStr = String.format(Locale.US, "%.1f NM", cpaNm)
                if (tcpaSec != null && tcpaSec > 0) {
                    val tcpaStr = when {
                        tcpaSec < 60 -> "${tcpaSec.toInt()}s"
                        tcpaSec < 3600 -> {
                            val m = (tcpaSec / 60).toInt()
                            val s = (tcpaSec % 60).toInt()
                            if (s > 0) "${m}m ${s}s" else "${m}m"
                        }
                        else -> {
                            val h = (tcpaSec / 3600).toInt()
                            val m = ((tcpaSec % 3600) / 60).toInt()
                            if (m > 0) "${h}h ${m}m" else "${h}h"
                        }
                    }
                    txtCpaTcpa.text = "CPA: $cpaStr in $tcpaStr"
                } else if (tcpaSec != null && tcpaSec <= 0) {
                    txtCpaTcpa.text = "CPA: $cpaStr (Past)"
                } else {
                    txtCpaTcpa.text = "CPA: $cpaStr"
                }
            } else {
                txtCpaTcpa.text = "CPA: --"
            }

            // 6. Quick Mute Toggle Button
            if (item.isMuted) {
                btnMute.setImageResource(R.drawable.ic_action_volume_mute)
                btnMute.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.color_warning))
            } else {
                btnMute.setImageResource(R.drawable.ic_action_volume_up)
                btnMute.imageTintList = ColorStateList.valueOf(AndroidUtils.getColorFromAttr(context, R.attr.icon_color_secondary))
            }

            btnMute.setOnClickListener {
                onMuteClick(item)
            }

            // 7. Row Click & Long Press Actions
            itemView.setOnClickListener {
                onRowClick(item)
            }

            itemView.setOnLongClickListener {
                onRowLongClick(item)
                true
            }
        }

        private fun setBadgeBackground(view: View, color: Int) {
            val density = view.context.resources.displayMetrics.density
            val radiusPx = 4f * density
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
                setColor(color)
            }
            view.background = drawable
        }
    }
}
