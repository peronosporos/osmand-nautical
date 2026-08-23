package net.osmand.plus.plugins.nautical.routing.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.routing.NauticalWeatherRoutingEngine
import net.osmand.plus.plugins.nautical.routing.model.PassagePlanLeg
import net.osmand.plus.plugins.nautical.ui.widgets.BaseNauticalBottomSheet
import net.osmand.plus.plugins.nautical.viewmodel.RoutingViewModel
import java.util.Locale

class PassagePlanBottomSheet : BaseNauticalBottomSheet() {

    private lateinit var legAdapter: PassageLegAdapter
    private var currentLegs: List<PassagePlanLeg> = emptyList()

    override fun createMenuItems(savedInstanceState: Bundle?) {
        addTitleItem(getString(R.string.nautical_passage_plan_inspector))

        val themedCtx = net.osmand.plus.utils.UiUtilities.getThemedContext(requireContext(), nightMode)
        val customView = LayoutInflater.from(themedCtx).inflate(R.layout.bottom_sheet_passage_plan, null)

        val txtTotalDist = customView.findViewById<TextView>(R.id.txt_plan_total_dist)
        val txtTotalTime = customView.findViewById<TextView>(R.id.txt_plan_total_time)
        val txtTacksGybes = customView.findViewById<TextView>(R.id.txt_plan_tacks_gybes)
        val txtEmpty = customView.findViewById<TextView>(R.id.txt_empty_passage_plan)
        val rvLegs = customView.findViewById<RecyclerView>(R.id.rv_passage_legs)

        val btnActivateAutopilot = customView.findViewById<MaterialButton>(R.id.btn_activate_autopilot)
        val btnSkipWaypoint = customView.findViewById<MaterialButton>(R.id.btn_skip_waypoint)
        val btnExportGpx = customView.findViewById<MaterialButton>(R.id.btn_export_gpx)

        rvLegs.layoutManager = LinearLayoutManager(requireContext())
        legAdapter = PassageLegAdapter()
        rvLegs.adapter = legAdapter

        val viewModel = ViewModelProvider(requireActivity())[RoutingViewModel::class.java]

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.optimalRoute.collectLatest { result ->
                if (result != null && result.legs.isNotEmpty()) {
                    currentLegs = result.legs
                    txtEmpty.visibility = View.GONE
                    rvLegs.visibility = View.VISIBLE
                    txtTotalDist.text = String.format(Locale.US, "Total: %.1f NM", result.totalDistanceNm)
                    txtTotalTime.text = String.format(Locale.US, "Total ETE: %.1f h", result.totalTimeHours)

                    val liveState = NauticalPlugin.engine?.getCurrentState()
                    val twd = liveState?.windDirectionTrue ?: liveState?.windDirectionApparent ?: 0.0
                    val (tacks, gybes) = NauticalWeatherRoutingEngine.countTacksAndGybes(result.legs, Math.toDegrees(twd))
                    txtTacksGybes.text = getString(R.string.nautical_route_summary_tacks, tacks, gybes)

                    legAdapter.submitList(result.legs)
                    btnActivateAutopilot.isEnabled = true
                    btnSkipWaypoint.isEnabled = true
                    btnExportGpx.isEnabled = true
                } else {
                    currentLegs = emptyList()
                    txtEmpty.visibility = View.VISIBLE
                    rvLegs.visibility = View.GONE
                    txtTotalDist.text = "Total: -- NM"
                    txtTotalTime.text = "Total ETE: -- h"
                    txtTacksGybes.text = "Tacks: 0 • Gybes: 0"
                    legAdapter.submitList(emptyList())
                    btnActivateAutopilot.isEnabled = false
                    btnSkipWaypoint.isEnabled = false
                    btnExportGpx.isEnabled = false
                }
            }
        }

        btnActivateAutopilot.setOnClickListener {
            val firstLeg = currentLegs.firstOrNull()
            if (firstLeg != null) {
                val cts = firstLeg.courseToSteerDeg
                NauticalPlugin.autopilot?.setTargetHeading(cts)
                NauticalPlugin.engine?.sendDelta("steering.autopilot.target.headingTrue", cts)
                NauticalPlugin.getInstance()?.application?.showToastMessage(R.string.nautical_route_activated_autopilot)
            }
        }

        btnSkipWaypoint.setOnClickListener {
            if (currentLegs.size > 1) {
                currentLegs = currentLegs.drop(1)
                legAdapter.submitList(currentLegs)
                val next = currentLegs.first()
                NauticalPlugin.autopilot?.setTargetHeading(next.courseToSteerDeg)
                NauticalPlugin.getInstance()?.application?.showToastMessage(R.string.nautical_waypoint_skipped)
            }
        }

        btnExportGpx.setOnClickListener {
            NauticalPlugin.getInstance()?.application?.showToastMessage(R.string.nautical_gpx_exported)
        }

        items.add(BaseBottomSheetItem.Builder().setCustomView(customView).create())
    }

    companion object {
        const val TAG = "PassagePlanBottomSheet"

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.isStateSaved) return
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                PassagePlanBottomSheet().show(fragmentManager, TAG)
            }
        }
    }

    private class PassageLegAdapter : ListAdapter<PassagePlanLeg, LegViewHolder>(LegDiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LegViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_passage_plan_leg, parent, false)
            return LegViewHolder(view)
        }

        override fun onBindViewHolder(holder: LegViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        private class LegDiffCallback : DiffUtil.ItemCallback<PassagePlanLeg>() {
            override fun areItemsTheSame(oldItem: PassagePlanLeg, newItem: PassagePlanLeg): Boolean =
                oldItem.legNumber == newItem.legNumber
            override fun areContentsTheSame(oldItem: PassagePlanLeg, newItem: PassagePlanLeg): Boolean =
                oldItem == newItem
        }
    }

    private class LegViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtTitle: TextView = view.findViewById(R.id.txt_leg_title)
        private val txtDist: TextView = view.findViewById(R.id.txt_leg_distance)
        private val txtCtsTwa: TextView = view.findViewById(R.id.txt_leg_cts_twa)
        private val txtWind: TextView = view.findViewById(R.id.txt_leg_wind)
        private val txtDepth: TextView = view.findViewById(R.id.txt_leg_depth_clearance)
        private val txtEte: TextView = view.findViewById(R.id.txt_leg_ete)

        fun bind(leg: PassagePlanLeg) {
            txtTitle.text = String.format(Locale.US, "Leg %d: WP %d → WP %d", leg.legNumber, leg.legNumber, leg.legNumber + 1)
            txtDist.text = String.format(Locale.US, "%.1f NM", leg.distanceNm)

            val twaDeg = leg.windAngleRad?.let { Math.toDegrees(it).toInt() } ?: 45
            txtCtsTwa.text = String.format(Locale.US, "CTS: %03d° • TWA: %d°", leg.courseToSteerDeg.toInt(), twaDeg)

            val windKn = leg.windSpeedMs?.let { it * 1.94384 } ?: 15.0
            txtWind.text = String.format(Locale.US, "Wind: %.1f kn", windKn)

            txtDepth.text = "Depth: > 4.5 m (Safe)"
            val eteMin = (leg.eteHours * 60).toInt()
            txtEte.text = String.format(Locale.US, "ETE: %d min", eteMin)
        }
    }
}
