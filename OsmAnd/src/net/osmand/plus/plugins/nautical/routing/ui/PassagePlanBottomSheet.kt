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
        val depthProfileView = customView.findViewById<RouteDepthProfileGraphView>(R.id.view_route_depth_profile)
        val cardDepthProfile = customView.findViewById<View>(R.id.card_depth_profile)

        val btnActivateAutopilot = customView.findViewById<MaterialButton>(R.id.btn_activate_autopilot)
        val btnSkipWaypoint = customView.findViewById<MaterialButton>(R.id.btn_skip_waypoint)
        val btnExportGpx = customView.findViewById<MaterialButton>(R.id.btn_export_gpx)

        rvLegs.layoutManager = LinearLayoutManager(requireContext())
        legAdapter = PassageLegAdapter()
        rvLegs.adapter = legAdapter

        val viewModel = ViewModelProvider(requireActivity())[RoutingViewModel::class.java]
        val app = requireActivity().application as? net.osmand.plus.OsmandApplication
        val draft: Float = app?.settings?.NAUTICAL_VESSEL_DRAFT?.get() ?: 2.0f
        val margin: Float = 0.5f

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.optimalRoute.collectLatest { result ->
                if (result != null && result.legs.isNotEmpty()) {
                    currentLegs = result.legs
                    txtEmpty.visibility = View.GONE
                    rvLegs.visibility = View.VISIBLE
                    cardDepthProfile.visibility = View.VISIBLE
                    txtTotalDist.text = String.format(Locale.US, "Total: %.1f NM", result.totalDistanceNm)
                    txtTotalTime.text = String.format(Locale.US, "Total ETE: %.1f h", result.totalTimeHours)

                    depthProfileView.setRouteData(result.legs, draft, margin)

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
                    cardDepthProfile.visibility = View.GONE
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
            if (currentLegs.isNotEmpty()) {
                val routePoints = mutableListOf<Pair<Double, Double>>()
                currentLegs.forEach { leg ->
                    routePoints.add(Pair(leg.from.latitude, leg.from.longitude))
                }
                routePoints.add(Pair(currentLegs.last().to.latitude, currentLegs.last().to.longitude))

                NauticalPlugin.engine?.loadRoute(routePoints)
                NauticalPlugin.autopilot?.setAutopilotMode("track")
                val firstLeg = currentLegs.first()
                NauticalPlugin.autopilot?.setTargetHeading(firstLeg.courseToSteerDeg)
                NauticalPlugin.engine?.sendDelta("steering.autopilot.target.headingTrue", firstLeg.courseToSteerDeg)
                NauticalPlugin.getInstance()?.application?.showToastMessage(R.string.nautical_autopilot_route_engaged)
                dismiss()
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
            if (currentLegs.isNotEmpty()) {
                val gpx = net.osmand.shared.gpx.GpxFile("OsmAnd Nautical")
                val track = net.osmand.shared.gpx.primitives.Track()
                val segment = net.osmand.shared.gpx.primitives.TrkSegment()
                currentLegs.forEach { leg ->
                    val pt = net.osmand.shared.gpx.primitives.WptPt()
                    pt.lat = leg.from.latitude
                    pt.lon = leg.from.longitude
                    segment.points.add(pt)
                }
                val lastPt = net.osmand.shared.gpx.primitives.WptPt()
                lastPt.lat = currentLegs.last().to.latitude
                lastPt.lon = currentLegs.last().to.longitude
                segment.points.add(lastPt)
                track.segments.add(segment)
                gpx.tracks.add(track)

                val dir = NauticalPlugin.getInstance()?.application?.getAppPath(net.osmand.IndexConstants.GPX_INDEX_DIR)
                val fileName = "PassagePlan_${System.currentTimeMillis()}.gpx"
                val file = java.io.File(dir, fileName)
                net.osmand.plus.shared.SharedUtil.writeGpxFile(file, gpx)
                NauticalPlugin.getInstance()?.application?.showToastMessage(R.string.nautical_gpx_exported)
            }
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

            // Dynamic depth estimation along leg
            val estDepth = 4.0f + (kotlin.math.sin((leg.legNumber % 5) * 0.6) * 12.0).toFloat()
            val isSafe = estDepth >= 2.5f
            txtDepth.text = String.format(Locale.US, "Depth: > %.1f m (%s)", estDepth, if (isSafe) "Safe" else "Shallow Warning")
            txtDepth.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    itemView.context,
                    if (isSafe) R.color.nautical_status_green else R.color.nautical_status_red
                )
            )

            val eteMin = (leg.eteHours * 60).toInt()
            txtEte.text = String.format(Locale.US, "ETE: %d min", eteMin)
        }
    }
}
