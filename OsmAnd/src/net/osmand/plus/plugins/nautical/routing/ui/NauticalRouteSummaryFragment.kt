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
        
        val recyclerView = view.findViewById<RecyclerView>(R.id.legs_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = LegsAdapter()
        recyclerView.adapter = adapter

        val totalDistText = view.findViewById<TextView>(R.id.total_distance)
        val totalTimeText = view.findViewById<TextView>(R.id.total_time)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.optimalRoute.collectLatest { result ->
                    if (result != null) {
                        totalDistText.text = String.format(Locale.US, "Total Dist: %.1f NM", result.totalDistanceNm)
                        totalTimeText.text = String.format(Locale.US, "Total ETE: %.1f h", result.totalTimeHours)
                        adapter.submitList(result.legs)
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
        private val number = view.findViewById<TextView>(R.id.leg_number)
        private val dist = view.findViewById<TextView>(R.id.distance)
        private val cts = view.findViewById<TextView>(R.id.cts)
        private val sog = view.findViewById<TextView>(R.id.sog)
        private val ete = view.findViewById<TextView>(R.id.ete)
        private val wind = view.findViewById<TextView>(R.id.wind)

        fun bind(leg: PassagePlanLeg) {
            number.text = leg.legNumber.toString()
            dist.text = String.format(Locale.US, "%.1f", leg.distanceNm)
            cts.text = String.format(Locale.US, "%03d°", leg.courseToSteerDeg.toInt())
            sog.text = String.format(Locale.US, "%.1f", leg.speedOverGroundKn)
            ete.text = String.format(Locale.US, "%.1f", leg.eteHours)
            
            leg.windSpeedMs?.let { ws ->
                wind.text = String.format(Locale.US, "W:%.0f", ws * 1.94384) // To knots
            } ?: run {
                wind.text = ""
            }
        }
    }
}
