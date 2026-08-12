package net.osmand.plus.plugins.nautical.hazard.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexMessage
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexSubject
import net.osmand.plus.plugins.nautical.hazard.viewmodel.NavtexFilters
import java.text.SimpleDateFormat
import java.util.*

class NavtexListFragment : BaseOsmAndFragment() {

    private lateinit var recyclerView: RecyclerView
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.navtex_list_fragment, container, false)
        
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        
        toolbar.inflateMenu(R.menu.navtex_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_filter -> {
                    showFilterDialog()
                    true
                }
                else -> false
            }
        }
        
        recyclerView = view.findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        val plugin = net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance()
        val messages = plugin?.navtexViewModel?.uiState?.value?.messages ?: emptyList()
        
        recyclerView.adapter = NavtexAdapter(messages)
        
        observeUiState()
        
        return view
    }

    private fun observeUiState() {
        val plugin = net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance()
        val viewModel = plugin?.navtexViewModel ?: return
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    (recyclerView.adapter as? NavtexAdapter)?.updateItems(state.messages)
                }
            }
        }
    }

    private fun showFilterDialog() {
        val plugin = net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance()
        val viewModel = plugin?.navtexViewModel ?: return
        val current = viewModel.uiState.value.filters
        
        val items = arrayOf(
            getString(R.string.navtex_only_urgent),
            getString(R.string.navtex_subject_filter) + ": " + (current.subject?.name ?: "All"),
            getString(R.string.navtex_max_distance)
        )

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.navtex_details_group)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> viewModel.setUrgentOnly(!current.onlyUrgent)
                    1 -> showSubjectSelectionDialog(viewModel)
                    2 -> showDistanceSelectionDialog(viewModel)
                }
            }
            .setNeutralButton("Reset All") { _, _ ->
                viewModel.updateFilters(NavtexFilters())
            }
            .show()
    }

    private fun showSubjectSelectionDialog(viewModel: net.osmand.plus.plugins.nautical.hazard.viewmodel.NavtexViewModel) {
        val subjects = NavtexSubject.entries.toTypedArray()
        val names = subjects.map { it.name.replace("_", " ") }.toTypedArray()
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.navtex_subject_filter)
            .setItems(names) { _, which ->
                viewModel.setSubjectFilter(subjects[which])
            }
            .setNeutralButton("Clear Filter") { _, _ ->
                viewModel.setSubjectFilter(null)
            }
            .show()
    }

    private fun showDistanceSelectionDialog(viewModel: net.osmand.plus.plugins.nautical.hazard.viewmodel.NavtexViewModel) {
        val distances = arrayOf("10 km", "50 km", "100 km", "500 km", "Any")
        val values = arrayOf(10.0, 50.0, 100.0, 500.0, null)
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.navtex_max_distance)
            .setItems(distances) { _, which ->
                viewModel.setMaxDistance(values[which])
            }
            .show()
    }

    inner class NavtexAdapter(private var items: List<NavtexMessage>) : RecyclerView.Adapter<NavtexViewHolder>() {
        
        fun updateItems(newItems: List<NavtexMessage>) {
            val diffCallback = NavtexDiffCallback(items, newItems)
            val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(diffCallback)
            items = newItems
            diffResult.dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NavtexViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.navtex_list_item, parent, false)
            return NavtexViewHolder(v)
        }

        override fun onBindViewHolder(holder: NavtexViewHolder, position: Int) {
            val msg = items[position]
            holder.id.text = msg.id
            holder.time.text = sdf.format(Date(msg.timestamp))
            holder.body.text = msg.body
            
            holder.priority.text = msg.subject.name.replace("_", " ")
            val color = when (msg.subject) {
                NavtexSubject.SEARCH_AND_RESCUE -> 0xFFD32F2F.toInt()
                NavtexSubject.METEOROLOGICAL_WARNING -> 0xFFF57C00.toInt()
                else -> 0xFF1976D2.toInt()
            }
            holder.priority.setBackgroundColor(color)
        }

        override fun getItemCount(): Int = items.size
    }

    class NavtexViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val id: TextView = v.findViewById(R.id.msg_id)
        val priority: TextView = v.findViewById(R.id.msg_priority)
        val time: TextView = v.findViewById(R.id.msg_time)
        val body: TextView = v.findViewById(R.id.msg_body)
    }

    class NavtexDiffCallback(private val oldList: List<NavtexMessage>, private val newList: List<NavtexMessage>) : androidx.recyclerview.widget.DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    companion object {
        const val TAG = "NavtexListFragment"
    }
}
