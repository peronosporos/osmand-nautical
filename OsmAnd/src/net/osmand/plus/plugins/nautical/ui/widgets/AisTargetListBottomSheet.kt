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
import java.util.Locale

class AisTargetListBottomSheet : BaseBottomSheetDialogFragment() {

    override fun getThemeUsageContext(): ThemeUsageContext {
        return ThemeUsageContext.APP
    }

    private var currentSortMode: AisSortMode = AisSortMode.THREAT_CPA
    private lateinit var adapter: TargetSummaryAdapter
    private var txtTitle: TextView? = null
    private var layoutEmpty: View? = null
    private var rvTargets: RecyclerView? = null
    private var toggleGroup: MaterialButtonToggleGroup? = null

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

        view.findViewById<ImageButton>(R.id.btn_close)?.setOnClickListener {
            dismiss()
        }

        adapter = TargetSummaryAdapter(
            onRowClick = { target ->
                dismiss()
                if (MarineStateConstants.isValidLat(target.lat) && MarineStateConstants.isValidLon(target.lon)) {
                    val mapActivity = activity as? MapActivity
                    mapActivity?.mapView?.setLatLon(target.lat, target.lon)
                    if ((mapActivity?.mapView?.zoom ?: 0) < 14) {
                        mapActivity?.mapView?.setIntZoom(15)
                    }
                }
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
        val targets = plugin.aisManager?.getActiveTargets(currentSortMode) ?: emptyList()

        adapter.submitList(targets)
        val count = targets.size
        txtTitle?.text = if (count > 0) {
            "${getString(R.string.nautical_ais_targets_title)} ($count)"
        } else {
            getString(R.string.nautical_ais_targets_title)
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

            when {
                isDanger -> {
                    txtThreatBadge.text = context.getString(R.string.nautical_threat_danger)
                    txtThreatBadge.setTextColor(Color.WHITE)
                    val dangerColor = ContextCompat.getColor(context, R.color.nautical_status_red)
                    setBadgeBackground(txtThreatBadge, dangerColor)
                    txtCpaTcpa.setTextColor(dangerColor)
                }
                isCaution -> {
                    txtThreatBadge.text = context.getString(R.string.nautical_threat_caution)
                    txtThreatBadge.setTextColor(Color.WHITE)
                    val cautionColor = ContextCompat.getColor(context, R.color.nautical_status_orange)
                    setBadgeBackground(txtThreatBadge, cautionColor)
                    txtCpaTcpa.setTextColor(cautionColor)
                }
                else -> {
                    txtThreatBadge.text = context.getString(R.string.nautical_threat_safe)
                    txtThreatBadge.setTextColor(Color.WHITE)
                    val safeColor = ContextCompat.getColor(context, R.color.nautical_status_green)
                    setBadgeBackground(txtThreatBadge, safeColor)
                    txtCpaTcpa.setTextColor(AndroidUtils.getColorFromAttr(context, android.R.attr.textColorPrimary))
                }
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
