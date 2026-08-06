package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.engine.NauticalSafetyManager
import net.osmand.plus.plugins.nautical.network.SignalKRegion

class NauticalSafetyRegionsFragment : BaseOsmAndFragment() {

    private lateinit var adapter: RegionAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.recyclerview_fragment, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        
        adapter = RegionAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        val regions = NauticalSafetyManager.getInstance(app).getSignalKRegions()
        adapter.submitList(regions)
        val emptyView = view.findViewById<TextView>(R.id.txt_empty_list)
        emptyView?.text = getString(R.string.nautical_no_safety_regions)
        emptyView?.visibility = if (regions.isEmpty()) View.VISIBLE else View.GONE

        return view
    }

    private class RegionAdapter : ListAdapter<SignalKRegion, RegionViewHolder>(DiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RegionViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_icon_and_menu, parent, false)
            return RegionViewHolder(view)
        }

        override fun onBindViewHolder(holder: RegionViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<SignalKRegion>() {
        override fun areItemsTheSame(oldItem: SignalKRegion, newItem: SignalKRegion): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: SignalKRegion, newItem: SignalKRegion): Boolean = oldItem == newItem
    }

    private class RegionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtTitle: TextView = view.findViewById(R.id.title)
        private val txtDesc: TextView = view.findViewById(R.id.description)

        fun bind(region: SignalKRegion) {
            val props = region.feature.properties
            txtTitle.text = props["name"]?.toString() ?: props["title"]?.toString() ?: "Unnamed Area"
            txtDesc.text = props["description"]?.toString() ?: props["category"]?.toString() ?: "Restricted Region"
        }
    }
}
