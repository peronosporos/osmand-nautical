package net.osmand.plus.plugins.nautical.ui.sail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
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
    private val pendingToggles = mutableMapOf<String, Boolean>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.recyclerview_fragment, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        
        adapter = SailAdapter(
            onSailToggle = { sail -> toggleSail(sail) },
            onReefChange = { reefs, sailId -> updateReefs(reefs, sailId) },
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                val items = mutableListOf<Any>()
                
                val globalMax = (state.pathMeta["sails.reefs"]?.get("max") as? Number)?.toInt() ?: 5
                items.add(ReefData(count = state.reefs ?: 0, maxCount = globalMax))
                
                val sails = state.sailInventory.map { sail ->
                    val pending = pendingToggles[sail.id]
                    if (pending != null) {
                        if (pending == sail.active) {
                            pendingToggles.remove(sail.id)
                            sail
                        } else {
                            sail.copy(active = pending)
                        }
                    } else sail
                }
                
                sails.forEach { sail ->
                    items.add(sail)
                    sail.reefs?.let { reefs ->
                        items.add(
                            ReefData(
                                count = reefs,
                                maxCount = sail.maxReefs ?: 5,
                                sailId = sail.id,
                                sailName = sail.name
                            )
                        )
                    }
                }
                adapter.submitList(items)
                
                val emptyView = view.findViewById<TextView>(R.id.txt_empty_list)
                val connected = NauticalPlugin.getInstance()?.isSignalKConnected() == true
                emptyView?.text = if (connected) getString(R.string.shared_string_no_items) else getString(R.string.nautical_server_disconnected)
                emptyView?.visibility = if (state.sailInventory.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        return view
    }

    private fun updateReefs(reefs: Int, sailId: String?) {
        lifecycleScope.launch {
            val path = if (sailId != null) "sails.inventory.$sailId.reefs" else "sails.reefs"
            NauticalPlugin.engine?.sendDelta(path, reefs)
        }
    }

    private fun toggleSail(sail: Sail) {
        val nextState = !sail.active
        pendingToggles[sail.id] = nextState
        // Re-submit current state with the pending toggle for immediate UI response
        NauticalPlugin.engine?.getCurrentState()?.let { state ->
            val items = mutableListOf<Any>()
            items.add(ReefData(state.reefs ?: 0))
            items.addAll(state.sailInventory.map { s ->
                if (s.id == sail.id) s.copy(active = nextState) else s
            })
            adapter.submitList(items)
        }
        
        lifecycleScope.launch {
            NauticalPlugin.engine?.sendDelta("sails.inventory.${sail.id}.active", nextState)
        }
    }

    private data class ReefData(
        val count: Int,
        val maxCount: Int = 5,
        val sailId: String? = null,
        val sailName: String? = null
    )

    private class SailAdapter(
        private val onSailToggle: (Sail) -> Unit,
        private val onReefChange: (Int, String?) -> Unit
    ) : ListAdapter<Any, RecyclerView.ViewHolder>(SailDiffCallback()) {

        override fun getItemViewType(position: Int): Int {
            return if (getItem(position) is ReefData) 0 else 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0) {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.nautical_reefs_control_item, parent, false)
                ReefHeaderViewHolder(view, onReefChange)
            } else {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_title_with_description_icon_switch, parent, false)
                SailViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = getItem(position)
            if ((holder is ReefHeaderViewHolder) && (item is ReefData)) {
                holder.bind(item)
            } else if ((holder is SailViewHolder) && (item is Sail)) {
                holder.bind(item, onSailToggle)
            }
        }
    }

    private class SailDiffCallback : DiffUtil.ItemCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when (oldItem) {
                is ReefData -> newItem is ReefData && (oldItem.sailId == newItem.sailId)
                is Sail -> newItem is Sail && (oldItem.id == newItem.id)
                else -> false
            }
        }
        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when (oldItem) {
                is ReefData -> newItem is ReefData && oldItem == newItem
                is Sail -> newItem is Sail && oldItem == newItem
                else -> false
            }
        }
    }

    private class ReefHeaderViewHolder(view: View, private val onReefChange: (Int, String?) -> Unit) : RecyclerView.ViewHolder(view) {
        private val txtTitle: TextView = view.findViewById(R.id.txt_reefs_title)
        private val txtReefs: TextView = view.findViewById(R.id.txt_reefs_count)
        private val btnMinus: View = view.findViewById(R.id.btn_minus)
        private val btnPlus: View = view.findViewById(R.id.btn_plus)

        fun bind(data: ReefData) {
            txtTitle.text = if (data.sailName != null) {
                "${data.sailName} ${itemView.context.getString(R.string.nautical_reefs)}"
            } else {
                itemView.context.getString(R.string.nautical_reefs)
            }
            txtReefs.text = data.count.toString()
            btnMinus.setOnClickListener { if (data.count > 0) onReefChange(data.count - 1, data.sailId) }
            btnPlus.setOnClickListener { if (data.count < data.maxCount) onReefChange(data.count + 1, data.sailId) }
        }
    }

    private class SailViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.icon_iv)
        private val txtName: TextView = view.findViewById(R.id.title_tv)
        private val txtDesc: TextView = view.findViewById(R.id.state_tv)
        private val switchToggle: SwitchCompat = view.findViewById(R.id.switch_compat)

        fun bind(sail: Sail, onToggle: (Sail) -> Unit) {
            txtName.text = sail.name
            
            val (areaVal, areaUnit) = if (sail.area != null) {
                net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter.formatValue(
                    itemView.context, 
                    NauticalPlugin.getInstance()!!.getSettings(),
                    sail.area,
                    "sails.inventory.area"
                )
            } else "" to ""
            
            txtDesc.text = if (areaVal.isNotEmpty()) {
                "${sail.type} ($areaVal $areaUnit)"
            } else {
                sail.type
            }

            val iconRes = when (sail.type.lowercase()) {
                "mainsail", "main" -> R.drawable.ic_action_sail_boat_dark
                "genoa", "jib" -> R.drawable.ic_action_sail_boat_dark
                "spinnaker", "gennaker", "code0" -> R.drawable.ic_action_sail_boat_dark
                else -> R.drawable.ic_action_sail_boat_dark
            }
            icon.setImageResource(iconRes)

            switchToggle.setOnCheckedChangeListener(null)
            switchToggle.isChecked = sail.active
            switchToggle.setOnCheckedChangeListener { _, _ ->
                onToggle(sail)
            }
        }
    }
}
