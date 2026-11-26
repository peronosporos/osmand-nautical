package net.osmand.plus.plugins.nautical.routing.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.routing.model.PassagePlanLeg
import net.osmand.plus.plugins.nautical.ui.NauticalColorResolver
import net.osmand.plus.plugins.nautical.ui.NauticalSemanticColor
import net.osmand.plus.plugins.nautical.viewmodel.RoutingViewModel
import java.util.Locale

/**
 * Displays a leg-by-leg breakdown of the calculated nautical route.
 */
class NauticalRouteSummaryFragment : BaseOsmAndFragment() {

    private lateinit var viewModel: RoutingViewModel
    private lateinit var adapter: LegsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[RoutingViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.fragment_nautical_route_summary, container, false)

        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar?.title = ""
        view.findViewById<View>(R.id.close_button)?.setOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }
        view.findViewById<TextView>(R.id.toolbar_title)?.text = getString(R.string.nautical_route_summary_title)
        
        val recyclerView = view.findViewById<RecyclerView>(R.id.legs_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        val vesselDraft: Float = app.settings.NAUTICAL_VESSEL_DRAFT.get()
        val safetyMargin: Float = app.settings.NAUTICAL_SAFETY_MARGIN.get()
        adapter = LegsAdapter(vesselDraft, safetyMargin)
        recyclerView.adapter = adapter

        val totalDistText = view.findViewById<TextView>(R.id.total_distance)
        val totalTimeText = view.findViewById<TextView>(R.id.total_time)
        val txtTacksGybes = view.findViewById<TextView>(R.id.txt_tacks_gybes)
        val emptyLayout = view.findViewById<View>(R.id.layout_empty_plan)
        val btnCreatePlan = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_create_passage_plan)

        // Hazard Warning Card
        val cardHazardWarning = view.findViewById<View>(R.id.card_route_hazard_warning)
        val layoutHazardHeader = view.findViewById<View>(R.id.layout_hazard_header)
        val btnExpandHazard = view.findViewById<android.widget.ImageView>(R.id.btn_expand_hazard)
        val layoutHazardDetails = view.findViewById<View>(R.id.layout_hazard_details)
        val txtHazardDetails = view.findViewById<TextView>(R.id.txt_hazard_details_text)

        var isHazardExpanded = false
        fun toggleHazardExpanded() {
            isHazardExpanded = !isHazardExpanded
            layoutHazardDetails?.visibility = if (isHazardExpanded) View.VISIBLE else View.GONE
            btnExpandHazard?.setImageResource(if (isHazardExpanded) R.drawable.ic_action_arrow_drop_up else R.drawable.ic_action_arrow_drop_down)
        }

        layoutHazardHeader?.setOnClickListener { toggleHazardExpanded() }
        btnExpandHazard?.setOnClickListener { toggleHazardExpanded() }

        btnCreatePlan.setOnClickListener {
            val currentRoute = viewModel.optimalRoute.value
            if (currentRoute != null && currentRoute.legs.isNotEmpty()) {
                val fm = activity?.supportFragmentManager ?: parentFragmentManager
                PassagePlanBottomSheet.show(fm)
                activity?.onBackPressedDispatcher?.onBackPressed()
            } else {
                val mapActivity = activity as? net.osmand.plus.activities.MapActivity
                val targetPoints = app.targetPointsHelper
                val dest = targetPoints.pointToNavigate
                if (dest != null && mapActivity != null) {
                    btnCreatePlan.isEnabled = false
                    btnCreatePlan.text = getString(R.string.nautical_calculating_weather_route)
                    val routingEngine = net.osmand.plus.plugins.nautical.routing.NauticalWeatherRoutingEngine(app)
                    val plugin = net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance()
                    val s57 = plugin?.s57SpatialIndex
                    val sm = plugin?.safetyManager
                    val layerController = plugin?.layerManager?.layerController
                    routingEngine.calculateAndRenderWeatherRoute(
                        destLat = dest.latitude,
                        destLon = dest.longitude,
                        mapActivity = mapActivity,
                        routingViewModel = viewModel,
                        safetyManager = sm,
                        s57SpatialIndex = s57,
                        layerController = layerController,
                        scope = viewLifecycleOwner.lifecycleScope
                    )
                } else {
                    val fm = activity?.supportFragmentManager ?: parentFragmentManager
                    PassagePlanBottomSheet.show(fm)
                    activity?.onBackPressedDispatcher?.onBackPressed()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.optimalRoute.collectLatest { result ->
                    btnCreatePlan.isEnabled = true
                    btnCreatePlan.text = getString(R.string.nautical_route_create_plan)
                    if (result != null && result.legs.isNotEmpty()) {
                        emptyLayout.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        totalDistText.text = getString(R.string.nautical_route_total_dist_fmt, String.format(Locale.US, "%.1f", result.totalDistanceNm))
                        totalTimeText.text = getString(R.string.nautical_route_total_ete_fmt, String.format(Locale.US, "%.1f", result.totalTimeHours))
                        val liveState = net.osmand.plus.plugins.nautical.NauticalPlugin.engine?.getCurrentState()
                        val twd = liveState?.windDirectionTrue ?: liveState?.windDirectionApparent
                        if (twd != null) {
                            // Counted against real live wind only; without wind
                            // the row would be a fabricated tally.
                            val (tacks, gybes) = net.osmand.plus.plugins.nautical.routing.NauticalWeatherRoutingEngine.countTacksAndGybes(result.legs, Math.toDegrees(twd))
                            txtTacksGybes?.visibility = View.VISIBLE
                            txtTacksGybes?.text = getString(R.string.nautical_route_summary_tacks, tacks, gybes)
                        } else {
                            txtTacksGybes?.visibility = View.GONE
                        }
                        adapter.submitList(result.legs)

                        // Check for real shoal hazards along the route legs: a leg is
                        // flagged only when a nearby chart feature (sounding or depth
                        // area) is shoaler than the vessel's required clearance
                        // (draft + safety margin). The previous check fired on ANY
                        // sounding in the vicinity, a constant false alarm in
                        // charted waters.
                        val plugin = net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance()
                        val minDepthSafety = app.settings.NAUTICAL_SAFETY_MARGIN.get().toDouble()
                        val requiredClearanceM = vesselDraft + minDepthSafety
                        val shallowLegs = result.legs.filter { leg ->
                            val s57 = plugin?.s57SpatialIndex
                            val nearSoundings = s57?.queryFeatures(
                                leg.to.latitude - 0.005, leg.to.latitude + 0.005,
                                leg.to.longitude - 0.005, leg.to.longitude + 0.005,
                                listOf("SOUNDG", "DEPARE")
                            )
                            nearSoundings?.any { isShoalFeature(it, requiredClearanceM) } ?: false
                        }

                        if (shallowLegs.isNotEmpty()) {
                            cardHazardWarning?.visibility = View.VISIBLE
                            val legsListStr = shallowLegs.joinToString(", ") { getString(R.string.nautical_route_hazard_leg_fmt, it.legNumber) }
                            txtHazardDetails?.text = getString(
                                R.string.nautical_route_shallow_hazard_fmt,
                                legsListStr,
                                String.format(Locale.US, "%.1f", requiredClearanceM)
                            )
                        } else {
                            cardHazardWarning?.visibility = View.GONE
                        }
                    } else {
                        emptyLayout.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                        cardHazardWarning?.visibility = View.GONE
                        totalDistText.text = getString(R.string.nautical_route_total_dist_empty)
                        totalTimeText.text = getString(R.string.nautical_route_total_ete_empty)
                        txtTacksGybes?.visibility = View.GONE
                        adapter.submitList(emptyList())
                    }
                }
            }
        }

        val btnExportPlan = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_export_passage_plan)
        btnExportPlan?.setOnClickListener {
            val route = viewModel.optimalRoute.value
            val act = activity
            if (route != null && route.legs.isNotEmpty() && act != null) {
                net.osmand.plus.plugins.nautical.export.PassagePlanExportHelper.sharePassagePlan(
                    act,
                    route,
                    net.osmand.plus.plugins.nautical.export.PassagePlanExportHelper.ExportFormat.TEXT
                )
            } else {
                app.showToastMessage(R.string.nautical_no_active_route)
            }
        }

        val isNightVision = net.osmand.plus.plugins.nautical.NauticalPlugin.isNightVision(app)
        if (isNightVision) {
            val nightPrimary = ContextCompat.getColor(view.context, R.color.nautical_night_vision_primary)
            view.setBackgroundColor(ContextCompat.getColor(view.context, R.color.nautical_night_vision_bg))
            btnExportPlan?.setTextColor(nightPrimary)
            btnExportPlan?.iconTint = android.content.res.ColorStateList.valueOf(nightPrimary)
        }

        return view
    }

    /**
     * True when a nearby S-57 chart feature is shoaler than the required
     * clearance: DEPARE via its DRVAL1 (shallowest depth of the area), or a
     * SOUNDG point/multi-point depth. Raw chart data only, no fallback.
     */
    private fun isShoalFeature(obj: net.osmand.plus.plugins.nautical.s57.S57Object, thresholdM: Double): Boolean {
        return if (obj.acronym == "DEPARE") {
            val drval1 = obj.attributes["DRVAL1"]?.toDoubleOrNull() ?: return false
            drval1 < thresholdM
        } else {
            obj.geometries.any { geom ->
                when (geom) {
                    is net.osmand.plus.plugins.nautical.s57.S57Geometry.Point -> geom.depth?.let { it < thresholdM } == true
                    is net.osmand.plus.plugins.nautical.s57.S57Geometry.MultiPoint -> geom.depths.any { it < thresholdM }
                    else -> false
                }
            }
        }
    }

    private class LegsAdapter(
        private val vesselDraftMeters: Float,
        private val safetyMarginMeters: Float
    ) : ListAdapter<PassagePlanLeg, LegViewHolder>(DIFF_CALLBACK) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LegViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_passage_plan_leg, parent, false)
            return LegViewHolder(view, vesselDraftMeters, safetyMarginMeters)
        }

        override fun onBindViewHolder(holder: LegViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        companion object {
            private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<PassagePlanLeg>() {
                override fun areItemsTheSame(oldItem: PassagePlanLeg, newItem: PassagePlanLeg): Boolean {
                    return oldItem.legNumber == newItem.legNumber
                }

                override fun areContentsTheSame(oldItem: PassagePlanLeg, newItem: PassagePlanLeg): Boolean {
                    return oldItem == newItem
                }
            }
        }
    }

    private class LegViewHolder(
        view: View,
        private val vesselDraftMeters: Float,
        private val safetyMarginMeters: Float
    ) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.txt_leg_title)
        private val dist: TextView = view.findViewById(R.id.txt_leg_distance)
        private val ctsTwa: TextView = view.findViewById(R.id.txt_leg_cts_twa)
        private val wind: TextView = view.findViewById(R.id.txt_leg_wind)
        private val depth: TextView = view.findViewById(R.id.txt_leg_depth_clearance)
        private val tidalDrift: TextView? = view.findViewById(R.id.txt_leg_tidal_drift)
        private val ete: TextView = view.findViewById(R.id.txt_leg_ete)

        fun bind(leg: PassagePlanLeg) {
            title.text = title.context.getString(R.string.nautical_leg_title_fmt, leg.legNumber, leg.legNumber, leg.legNumber + 1)
            dist.text = String.format(Locale.US, "%.1f NM", leg.distanceNm)

            // Same honest pattern as the passage plan sheet: real per-leg
            // forecast data, CTS-only / hidden rows / localized n/a otherwise
            // (previously this fabricated TWA 45, wind 15 kn and an
            // unconditional depth verdict on every leg).
            val ctsDeg = leg.courseToSteerDeg.toInt()
            val twaDeg = leg.windAngleRad?.let { Math.toDegrees(it).toInt() }
            ctsTwa.text = if (twaDeg != null) {
                ctsTwa.context.getString(R.string.nautical_leg_cts_twa_fmt, ctsDeg, twaDeg)
            } else {
                ctsTwa.context.getString(R.string.nautical_leg_cts_only_fmt, ctsDeg)
            }

            val windKn = leg.windSpeedMs?.let { it * 1.94384 }
            if (windKn == null) {
                wind.visibility = View.GONE
            } else {
                wind.visibility = View.VISIBLE
                wind.text = wind.context.getString(R.string.nautical_leg_wind_fmt, String.format(Locale.US, "%.1f", windKn))
            }

            val legMinDepth = leg.minDepthMeters
            if (legMinDepth == null) {
                depth.text = depth.context.getString(
                    R.string.nautical_pp_depth_na_fmt,
                    depth.context.getString(R.string.nautical_not_available)
                )
                depth.setTextColor(
                    NauticalColorResolver.getColor(itemView.context, NauticalSemanticColor.SECONDARY)
                )
            } else {
                val isSafe = legMinDepth >= (vesselDraftMeters + safetyMarginMeters)
                depth.text = depth.context.getString(
                    R.string.nautical_pp_depth_fmt,
                    String.format(Locale.US, "%.1f", legMinDepth),
                    depth.context.getString(if (isSafe) R.string.nautical_depth_safe else R.string.nautical_depth_shallow_warning)
                )
                depth.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        if (isSafe) R.color.nautical_status_green else R.color.nautical_status_red
                    )
                )
            }

            // Inwardly integrate current tidal stream vectors (set and drift from MarineState.tidalCurrentSpeed / tidalCurrentDirection)
            val liveState = net.osmand.plus.plugins.nautical.NauticalPlugin.engine?.getCurrentState()
            val driftMps = liveState?.drift
            val setRad = liveState?.setTrue

            var effectiveEteHours = leg.eteHours
            if (driftMps != null && setRad != null && driftMps > 0.05) {
                val driftKn = driftMps * 1.94384
                val setDeg = Math.toDegrees(setRad)
                val relAngleRad = Math.toRadians(setDeg - leg.courseToSteerDeg)
                val currentProjKn = driftKn * kotlin.math.cos(relAngleRad)
                val effectiveSog = (leg.speedOverGroundKn + currentProjKn).coerceAtLeast(0.5)
                effectiveEteHours = leg.distanceNm / effectiveSog

                tidalDrift?.visibility = View.VISIBLE
                val sign = if (currentProjKn >= 0) "+" else ""
                tidalDrift?.text = tidalDrift!!.context.getString(R.string.nautical_current_fmt, sign, String.format(Locale.US, "%.1f", currentProjKn))
            } else {
                tidalDrift?.visibility = View.GONE
            }

            val eteMin = (effectiveEteHours * 60).toInt()
            ete.text = ete.context.getString(R.string.nautical_leg_ete_fmt, eteMin)
        }
    }
}
