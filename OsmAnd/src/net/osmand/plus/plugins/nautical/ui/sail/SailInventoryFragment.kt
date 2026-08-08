package net.osmand.plus.plugins.nautical.ui.sail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.Sail

class SailInventoryFragment : BaseOsmAndFragment() {

    private lateinit var adapter: SailAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.recyclerview_fragment, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        
        adapter = SailAdapter(
            onSailToggle = { sail -> toggleSail(sail) },
            onReefChange = { reefs -> updateReefs(reefs) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                val items = mutableListOf<Any>()
                items.add(ReefData(state.reefs ?: 0))
                items.addAll(state.sailInventory)
                adapter.submitList(items)
                
                val emptyView = view.findViewById<TextView>(R.id.txt_empty_list)
                val connected = NauticalPlugin.getInstance()?.isSignalKConnected() == true
                emptyView?.text = if (connected) getString(R.string.shared_string_no_items) else "Server Disconnected"
                emptyView?.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        return view
    }

    private fun updateReefs(reefs: Int) {
        lifecycleScope.launch {
            NauticalPlugin.engine?.sendDelta("sails.reefs", reefs)
        }
    }

    private fun toggleSail(sail: Sail) {
        lifecycleScope.launch {
            val nextState = !sail.active
            NauticalPlugin.engine?.sendDelta("sails.inventory.${sail.id}.active", nextState)
        }
    }

    private data class ReefData(val count: Int)

    private class SailAdapter(
        private val onSailToggle: (Sail) -> Unit,
        private val onReefChange: (Int) -> Unit
    ) : ListAdapter<Any, RecyclerView.ViewHolder>(SailDiffCallback()) {

        override fun getItemViewType(position: Int): Int {
            return if (getItem(position) is ReefData) 0 else 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0) {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.nautical_reefs_control_item, parent, false)
                ReefHeaderViewHolder(view, onReefChange)
            } else {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_icon_and_switch, parent, false)
                SailViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = getItem(position)
            if (holder is ReefHeaderViewHolder && item is ReefData) {
                holder.bind(item)
            } else if (holder is SailViewHolder && item is Sail) {
                holder.bind(item, onSailToggle)
            }
        }
    }

    private class SailDiffCallback : DiffUtil.ItemCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when (oldItem) {
                is ReefData if newItem is ReefData -> true
                is Sail if newItem is Sail -> oldItem.id == newItem.id
                else -> false
            }
        }
        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when (oldItem) {
                is ReefData if newItem is ReefData -> oldItem.count == newItem.count
                is Sail if newItem is Sail -> oldItem == newItem
                else -> false
            }
        }
    }

    private class ReefHeaderViewHolder(view: View, private val onReefChange: (Int) -> Unit) : RecyclerView.ViewHolder(view) {
        private val txtReefs: TextView = view.findViewById(R.id.txt_reefs_count)
        private val btnMinus: View = view.findViewById(R.id.btn_minus)
        private val btnPlus: View = view.findViewById(R.id.btn_plus)

        fun bind(data: ReefData) {
            txtReefs.text = data.count.toString()
            btnMinus.setOnClickListener { if (data.count > 0) onReefChange(data.count - 1) }
            btnPlus.setOnClickListener { if (data.count < 5) onReefChange(data.count + 1) }
        }
    }

    private class SailViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtName: TextView = view.findViewById(R.id.title)
        private val txtDesc: TextView = view.findViewById(R.id.description)
        private val switchToggle: androidx.appcompat.widget.SwitchCompat = view.findViewById(R.id.switch_toggle)

        fun bind(sail: Sail, onToggle: (Sail) -> Unit) {
            txtName.text = sail.name
            txtDesc.text = if (sail.area != null) {
                itemView.context.getString(R.string.nautical_sail_desc_with_area, sail.type, sail.area.toString())
            } else {
                sail.type
            }
            switchToggle.setOnCheckedChangeListener(null)
            switchToggle.isChecked = sail.active
            switchToggle.setOnCheckedChangeListener { _, _ ->
                onToggle(sail)
            }
        }
    }
}
