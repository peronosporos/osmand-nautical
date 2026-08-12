package net.osmand.plus.plugins.nautical.ui.checklist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentManager
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

class NauticalChecklistFragment : BaseOsmAndFragment() {

    companion object {
        fun show(fragmentManager: FragmentManager) {
            val fragment = NauticalChecklistFragment()
            fragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, fragment, "nautical_checklists")
                .addToBackStack("nautical_checklists")
                .commit()
        }
    }

    private lateinit var adapter: ChecklistAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.fragment_nautical_checklists, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        
        adapter = ChecklistAdapter(
            onItemToggle = { checklistId, checklist, itemIndex, isChecked ->
                updateChecklistOnServer(checklistId, checklist, itemIndex, isChecked)
            },
            onAddItem = { checklistId, checklist ->
                showAddItemDialog(checklistId, checklist)
            },
            onDeleteChecklist = { checklistId ->
                showDeleteChecklistDialog(checklistId)
            },
            onDeleteItem = { checklistId, checklist, itemIndex ->
                showDeleteItemDialog(checklistId, checklist, itemIndex)
            },
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        view.findViewById<View>(R.id.fab_add_checklist).setOnClickListener {
            showAddChecklistDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                adapter.submitChecklists(state.checklists)
            }
        }

        return view
    }

    private fun showAddChecklistDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.nautical_checklist_name)
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.nautical_new_checklist)
            .setView(input)
            .setPositiveButton(R.string.shared_string_add) { _, _ ->
                val name = input.text.toString()
                if (name.isNotEmpty()) {
                    val newChecklist = SignalKChecklist(name = name, description = "", items = emptyList())
                    lifecycleScope.launch {
                        NauticalPlugin.engine?.resourceManager?.createChecklist(newChecklist)
                        app.showToastMessage(R.string.nautical_checklist_created)
                    }
                }
            }
            .setNegativeButton(R.string.shared_string_cancel, null)
            .show()
    }

    private fun showAddItemDialog(checklistId: String, checklist: SignalKChecklist) {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.nautical_item_title)
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.nautical_add_item)
            .setView(input)
            .setPositiveButton(R.string.shared_string_add) { _, _ ->
                val title = input.text.toString()
                if (title.isNotEmpty()) {
                    val newItem = SignalKChecklistItem(title = title, state = "pending")
                    val updatedChecklist = checklist.copy(items = checklist.items + newItem)
                    lifecycleScope.launch {
                        NauticalPlugin.engine?.resourceManager?.pushChecklistToServer(checklistId, updatedChecklist)
                        app.showToastMessage(R.string.nautical_checklist_item_added)
                    }
                }
            }
            .setNegativeButton(R.string.shared_string_cancel, null)
            .show()
    }

    private fun showDeleteChecklistDialog(checklistId: String) {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.nautical_delete_checklist_confirm)
            .setPositiveButton(R.string.shared_string_delete) { _, _ ->
                lifecycleScope.launch {
                    NauticalPlugin.engine?.resourceManager?.deleteChecklistFromServer(checklistId)
                    app.showToastMessage(R.string.nautical_checklist_deleted)
                }
            }
            .setNegativeButton(R.string.shared_string_cancel, null)
            .show()
    }

    private fun showDeleteItemDialog(checklistId: String, checklist: SignalKChecklist, itemIndex: Int) {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.nautical_delete_item_confirm)
            .setPositiveButton(R.string.shared_string_delete) { _, _ ->
                val updatedItems = checklist.items.toMutableList()
                updatedItems.removeAt(itemIndex)
                val updatedChecklist = checklist.copy(items = updatedItems)
                lifecycleScope.launch {
                    NauticalPlugin.engine?.resourceManager?.pushChecklistToServer(checklistId, updatedChecklist)
                }
            }
            .setNegativeButton(R.string.shared_string_cancel, null)
            .show()
    }

    private fun updateChecklistOnServer(checklistId: String, checklist: SignalKChecklist, itemIndex: Int, isChecked: Boolean) {
        val updatedItems = checklist.items.toMutableList()
        val item = updatedItems[itemIndex]
        val newState = if (isChecked) "completed" else "pending"
        
        if (item.state == newState) return

        updatedItems[itemIndex] = item.copy(state = newState)
        val updatedChecklist = checklist.copy(items = updatedItems)

        lifecycleScope.launch {
            try {
                NauticalPlugin.engine?.resourceManager?.pushChecklistToServer(checklistId, updatedChecklist)
            } catch (_: Exception) {
                app.showToastMessage(R.string.nautical_checklist_sync_failed)
            }
        }
    }

    private sealed class ChecklistListItem {
        data class Header(val id: String, val title: String, val checklist: SignalKChecklist) : ChecklistListItem()
        data class Item(val checklistId: String, val checklist: SignalKChecklist, val index: Int, val item: SignalKChecklistItem) : ChecklistListItem()
    }

    private class ChecklistAdapter(
        private val onItemToggle: (String, SignalKChecklist, Int, Boolean) -> Unit,
        private val onAddItem: (String, SignalKChecklist) -> Unit,
        private val onDeleteChecklist: (String) -> Unit,
        private val onDeleteItem: (String, SignalKChecklist, Int) -> Unit
    ) : ListAdapter<ChecklistListItem, RecyclerView.ViewHolder>(ChecklistDiffCallback()) {

        fun submitChecklists(newMap: Map<String, SignalKChecklist>) {
            val flattened = mutableListOf<ChecklistListItem>()
            newMap.forEach { (id, checklist) ->
                flattened.add(ChecklistListItem.Header(id, checklist.name, checklist))
                checklist.items.forEachIndexed { index, item ->
                    flattened.add(ChecklistListItem.Item(id, checklist, index, item))
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
                    val h = holder as HeaderViewHolder
                    h.title.text = item.title
                    h.btnAdd.setOnClickListener { onAddItem(item.id, item.checklist) }
                    h.btnDelete.setOnClickListener { onDeleteChecklist(item.id) }
                }
                is ChecklistListItem.Item -> {
                    val h = holder as ItemViewHolder
                    h.title.text = item.item.title
                    
                    // Avoid recursion and recycling issues
                    h.checkbox.setOnCheckedChangeListener(null)
                    h.checkbox.isChecked = item.item.state == "completed"
                    h.checkbox.setOnCheckedChangeListener { _, isChecked ->
                        onItemToggle(item.checklistId, item.checklist, item.index, isChecked)
                    }
                    h.btnDelete.setOnClickListener { onDeleteItem(item.checklistId, item.checklist, item.index) }
                }
            }
        }
    }

    private class ChecklistDiffCallback : DiffUtil.ItemCallback<ChecklistListItem>() {
        override fun areItemsTheSame(oldItem: ChecklistListItem, newItem: ChecklistListItem): Boolean {
            return when (oldItem) {
                is ChecklistListItem.Header -> newItem is ChecklistListItem.Header && oldItem.id == newItem.id
                is ChecklistListItem.Item -> newItem is ChecklistListItem.Item && oldItem.checklistId == newItem.checklistId && oldItem.index == newItem.index
            }
        }
        override fun areContentsTheSame(oldItem: ChecklistListItem, newItem: ChecklistListItem): Boolean = oldItem == newItem
    }

    private class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.title)
        val btnAdd: ImageView = view.findViewById(R.id.btn_add_item)
        val btnDelete: ImageView = view.findViewById(R.id.btn_delete_checklist)
    }

    private class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.title)
        val checkbox: CheckBox = view.findViewById(R.id.checkbox)
        val btnDelete: ImageView = view.findViewById(R.id.btn_delete_item)
    }
}
