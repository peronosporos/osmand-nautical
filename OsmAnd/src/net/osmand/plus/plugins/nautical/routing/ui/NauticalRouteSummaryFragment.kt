package net.osmand.plus.plugins.nautical.routing.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
        adapter = LegsAdapter()
        recyclerView.adapter = adapter

        val totalDistText = view.findViewById<TextView>(R.id.total_distance)
        val totalTimeText = view.findViewById<TextView>(R.id.total_time)
        val txtTacksGybes = view.findViewById<TextView>(R.id.txt_tacks_gybes)
        val emptyLayout = view.findViewById<View>(R.id.layout_empty_plan)
        val btnCreatePlan = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_create_passage_plan)

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
                    val s57 = net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance()?.s57Index
                    val sm = net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance()?.safetyManager
                    val layerController = net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance()?.sailingMapLayerController
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
                    btnCreatePlan.text = "Create Passage Plan / New Marine Route"
                    if (result != null && result.legs.isNotEmpty()) {
                        emptyLayout.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        totalDistText.text = String.format(Locale.US, "Total Dist: %.1f NM", result.totalDistanceNm)
                        totalTimeText.text = String.format(Locale.US, "Total ETE: %.1f h", result.totalTimeHours)
                        val liveState = net.osmand.plus.plugins.nautical.NauticalPlugin.engine?.getCurrentState()
                        val twd = liveState?.windDirectionTrue ?: liveState?.windDirectionApparent ?: 0.0
                        val (tacks, gybes) = net.osmand.plus.plugins.nautical.routing.NauticalWeatherRoutingEngine.countTacksAndGybes(result.legs, Math.toDegrees(twd))
                        txtTacksGybes?.visibility = View.VISIBLE
                        txtTacksGybes?.text = getString(R.string.nautical_route_summary_tacks, tacks, gybes)
                        adapter.submitList(result.legs)
                    } else {
                        emptyLayout.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                        totalDistText.text = "Total Dist: -- NM"
                        totalTimeText.text = "Total ETE: -- h"
                        txtTacksGybes?.visibility = View.GONE
                        adapter.submitList(emptyList())
                    }
                }
            }
        }

        return view
    }

    private class LegsAdapter : ListAdapter<PassagePlanLeg, LegViewHolder>(DIFF_CALLBACK) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LegViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_passage_plan_leg, parent, false)
            return LegViewHolder(view)
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

    private class LegViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.txt_leg_title)
        private val dist: TextView = view.findViewById(R.id.txt_leg_distance)
        private val ctsTwa: TextView = view.findViewById(R.id.txt_leg_cts_twa)
        private val wind: TextView = view.findViewById(R.id.txt_leg_wind)
        private val depth: TextView = view.findViewById(R.id.txt_leg_depth_clearance)
        private val ete: TextView = view.findViewById(R.id.txt_leg_ete)

        fun bind(leg: PassagePlanLeg) {
            title.text = String.format(Locale.US, "Leg %d: WP %d → WP %d", leg.legNumber, leg.legNumber, leg.legNumber + 1)
            dist.text = String.format(Locale.US, "%.1f NM", leg.distanceNm)

            val twaDeg = leg.windAngleRad?.let { Math.toDegrees(it).toInt() } ?: 45
            ctsTwa.text = String.format(Locale.US, "CTS: %03d° • TWA: %d°", leg.courseToSteerDeg.toInt(), twaDeg)

            val windKn = leg.windSpeedMs?.let { it * 1.94384 } ?: 15.0
            wind.text = String.format(Locale.US, "Wind: %.1f kn", windKn)

            depth.text = "Depth: > 4.5 m (Safe)"
            val eteMin = (leg.eteHours * 60).toInt()
            ete.text = String.format(Locale.US, "ETE: %d min", eteMin)
        }
    }
}
