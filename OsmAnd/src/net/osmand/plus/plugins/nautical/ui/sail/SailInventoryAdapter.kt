package net.osmand.plus.plugins.nautical.ui.sail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.engine.Sail
import java.util.Locale

sealed class SailListItem {
    data class CategoryHeader(val titleResId: Int, val title: String) : SailListItem()
    data class SailItem(
        val sail: Sail,
        val category: SailCategory,
        val isMainsail: Boolean = false
    ) : SailListItem()
}

enum class SailCategory {
    MAIN,
    HEADSAIL,
    DOWNWIND
}

class SailInventoryAdapter(
    private val onSailToggle: (Sail) -> Unit,
    private val onReefChange: (sailId: String?, reefs: Int) -> Unit
) : ListAdapter<SailListItem, RecyclerView.ViewHolder>(SailDiffCallback()) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_SAIL = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SailListItem.CategoryHeader -> TYPE_HEADER
            is SailListItem.SailItem -> TYPE_SAIL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_sail_category_header, parent, false)
            CategoryHeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_sail_inventory, parent, false)
            SailItemViewHolder(view, onSailToggle, onReefChange)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SailListItem.CategoryHeader -> (holder as CategoryHeaderViewHolder).bind(item)
            is SailListItem.SailItem -> (holder as SailItemViewHolder).bind(item)
        }
    }

    private class CategoryHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtTitle: TextView = view.findViewById(R.id.txt_category_title)

        fun bind(item: SailListItem.CategoryHeader) {
            txtTitle.text = if (item.titleResId != 0) itemView.context.getString(item.titleResId) else item.title
        }
    }

    private class SailItemViewHolder(
        view: View,
        private val onSailToggle: (Sail) -> Unit,
        private val onReefChange: (sailId: String?, reefs: Int) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        private val iconSail: AppCompatImageView = view.findViewById(R.id.icon_sail)
        private val txtName: TextView = view.findViewById(R.id.txt_sail_name)
        private val txtDesc: TextView = view.findViewById(R.id.txt_sail_desc)
        private val btnToggle: MaterialButton = view.findViewById(R.id.btn_sail_toggle)
        private val layoutReefs: View = view.findViewById(R.id.layout_reef_selector)
        private val toggleGroupReefs: MaterialButtonToggleGroup = view.findViewById(R.id.toggle_group_reefs)
        private val btnReef0: MaterialButton = view.findViewById(R.id.btn_reef_0)
        private val btnReef1: MaterialButton = view.findViewById(R.id.btn_reef_1)
        private val btnReef2: MaterialButton = view.findViewById(R.id.btn_reef_2)
        private val btnReef3: MaterialButton = view.findViewById(R.id.btn_reef_3)

        fun bind(item: SailListItem.SailItem) {
            val sail = item.sail
            val context = itemView.context
            txtName.text = sail.name

            val areaPct = when {
                sail.area != null && sail.area <= 2.5 -> (sail.area * 100).toInt()
                sail.area != null -> sail.area.toInt()
                else -> 100
            }
            txtDesc.text = "${sail.type} • $areaPct% Area"

            val iconRes = when (item.category) {
                SailCategory.MAIN -> R.drawable.ic_action_sail_boat_dark
                SailCategory.HEADSAIL -> R.drawable.ic_action_sail_boat_dark
                SailCategory.DOWNWIND -> R.drawable.ic_action_sail_boat_dark
            }
            iconSail.setImageResource(iconRes)

            if (sail.active) {
                btnToggle.text = context.getString(R.string.nautical_sail_hoisted)
                btnToggle.setBackgroundColor(ContextCompat.getColor(context, R.color.color_ok))
                btnToggle.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                btnToggle.strokeWidth = 0
            } else {
                btnToggle.text = context.getString(R.string.nautical_sail_furled)
                btnToggle.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                btnToggle.setTextColor(ContextCompat.getColor(context, R.color.text_color_secondary))
                btnToggle.strokeWidth = 2
            }

            btnToggle.setOnClickListener {
                onSailToggle(sail)
            }

            if (item.isMainsail && sail.active) {
                layoutReefs.visibility = View.VISIBLE
                val currentReef = sail.reefs ?: 0

                toggleGroupReefs.clearChecked()
                when (currentReef) {
                    0 -> btnReef0.isChecked = true
                    1 -> btnReef1.isChecked = true
                    2 -> btnReef2.isChecked = true
                    3 -> btnReef3.isChecked = true
                    else -> btnReef3.isChecked = true
                }

                toggleGroupReefs.addOnButtonCheckedListener { _, checkedId, isChecked ->
                    if (isChecked) {
                        val newReef = when (checkedId) {
                            R.id.btn_reef_0 -> 0
                            R.id.btn_reef_1 -> 1
                            R.id.btn_reef_2 -> 2
                            R.id.btn_reef_3 -> 3
                            else -> 0
                        }
                        if (newReef != currentReef) {
                            onReefChange(sail.id, newReef)
                        }
                    }
                }
            } else {
                layoutReefs.visibility = View.GONE
            }
        }
    }

    private class SailDiffCallback : DiffUtil.ItemCallback<SailListItem>() {
        override fun areItemsTheSame(oldItem: SailListItem, newItem: SailListItem): Boolean {
            return when {
                oldItem is SailListItem.CategoryHeader && newItem is SailListItem.CategoryHeader ->
                    oldItem.titleResId == newItem.titleResId && oldItem.title == newItem.title
                oldItem is SailListItem.SailItem && newItem is SailListItem.SailItem ->
                    oldItem.sail.id == newItem.sail.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: SailListItem, newItem: SailListItem): Boolean {
            return oldItem == newItem
        }
    }
}
