package net.osmand.plus.plugins.nautical.ui.checklist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
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
import net.osmand.plus.plugins.nautical.network.SignalKChecklist
import net.osmand.plus.plugins.nautical.network.SignalKChecklistItem

class SailingChecklistFragment : BaseOsmAndFragment() {

    private lateinit var adapter: ChecklistAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.fragment_nautical_checklists, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        
        adapter = ChecklistAdapter { checklist, itemIndex, isChecked ->
            updateChecklistOnServer(checklist, itemIndex, isChecked)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                adapter.submitChecklists(state.checklists.values.toList())
            }
        }

        return view
    }

    private fun updateChecklistOnServer(checklist: SignalKChecklist, itemIndex: Int, isChecked: Boolean) {
        val updatedItems = checklist.items.toMutableList()
        val item = updatedItems[itemIndex]
        updatedItems[itemIndex] = item.copy(state = if (isChecked) "completed" else "pending")
        val updatedChecklist = checklist.copy(items = updatedItems)

        lifecycleScope.launch {
            val plugin = NauticalPlugin.getInstance() ?: return@launch
            val client = plugin.okHttpClient ?: return@launch
            val ip = app.settings.NAUTICAL_SERVER_IP.get()
            val port = app.settings.NAUTICAL_SERVER_PORT.get()
            val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
            val service = net.osmand.plus.plugins.nautical.network.SignalKRestService.create("$protocol://$ip:$port", client) ?: return@launch
            
            try {
                // We need the ID of the checklist. SignalKRestService uses Path("id")
                // Current SignalKChecklist doesn't have ID. 
                // MarineState.checklists is Map<String, SignalKChecklist> where key is ID.
                NauticalPlugin.engine?.getCurrentState()?.checklists?.filterValues { it == checklist }?.keys?.firstOrNull()?.let { id ->
                    service.updateChecklist(id, updatedChecklist)
                }
            } catch (_: Exception) {
                app.showToastMessage("Failed to update checklist")
            }
        }
    }

    private sealed class ChecklistListItem {
        data class Header(val title: String) : ChecklistListItem()
        data class Item(val checklist: SignalKChecklist, val index: Int, val item: SignalKChecklistItem) : ChecklistListItem()
    }

    private class ChecklistAdapter(private val onItemToggle: (SignalKChecklist, Int, Boolean) -> Unit) : ListAdapter<ChecklistListItem, RecyclerView.ViewHolder>(ChecklistDiffCallback()) {

        fun submitChecklists(newList: List<SignalKChecklist>) {
            val flattened = mutableListOf<ChecklistListItem>()
            newList.forEach { checklist ->
                flattened.add(ChecklistListItem.Header(checklist.name))
                checklist.items.forEachIndexed { index, item ->
                    flattened.add(ChecklistListItem.Item(checklist, index, item))
                }
            }
            submitList(flattened)
        }

        override fun getItemViewType(position: Int): Int {
            return if (getItem(position) is ChecklistListItem.Header) 0 else 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0) {
                HeaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_checklist_header, parent, false))
            } else {
                ItemViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_checklist_row, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = getItem(position)) {
                is ChecklistListItem.Header -> {
                    (holder as HeaderViewHolder).title.text = item.title
                }
                is ChecklistListItem.Item -> {
                    val h = holder as ItemViewHolder
                    h.title.text = item.item.title
                    h.checkbox.setOnCheckedChangeListener(null)
                    h.checkbox.isChecked = item.item.state == "completed"
                    h.checkbox.setOnCheckedChangeListener { _, isChecked ->
                        onItemToggle(item.checklist, item.index, isChecked)
                    }
                }
            }
        }
    }

    private class ChecklistDiffCallback : DiffUtil.ItemCallback<ChecklistListItem>() {
        override fun areItemsTheSame(oldItem: ChecklistListItem, newItem: ChecklistListItem): Boolean {
            return when (oldItem) {
                is ChecklistListItem.Header if newItem is ChecklistListItem.Header -> oldItem.title == newItem.title
                is ChecklistListItem.Item if newItem is ChecklistListItem.Item -> {
                    oldItem.checklist.name == newItem.checklist.name && oldItem.index == newItem.index
                }

                else -> false
            }
        }
        override fun areContentsTheSame(oldItem: ChecklistListItem, newItem: ChecklistListItem): Boolean = oldItem == newItem
    }

    private class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.title)
    }

    private class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.title)
        val checkbox: CheckBox = view.findViewById(R.id.checkbox)
    }
}
