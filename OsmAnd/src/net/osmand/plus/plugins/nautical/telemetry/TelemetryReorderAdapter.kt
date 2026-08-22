package net.osmand.plus.plugins.nautical.telemetry

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import net.osmand.plus.R
import java.util.Collections

data class TelemetryItemConfig(
    val key: String,
    var isVisible: Boolean = true
)

class TelemetryReorderAdapter(
    private val items: MutableList<TelemetryItemConfig>,
    private val onStartDragListener: (RecyclerView.ViewHolder) -> Unit,
    private val onItemsChangedListener: () -> Unit
) : RecyclerView.Adapter<TelemetryReorderAdapter.ViewHolder>() {

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val dragHandle: ImageView = view.findViewById(R.id.btn_drag_handle)
        val metricName: TextView = view.findViewById(R.id.txt_metric_name)
        val metricCategory: TextView = view.findViewById(R.id.txt_metric_category)
        val visibilityToggle: ImageView = view.findViewById(R.id.btn_visibility_toggle)
        val deleteButton: ImageView = view.findViewById(R.id.btn_delete_metric)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_nautical_telemetry_reorder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val metricDef = TelemetryRegistry.getMetric(item.key)

        if (metricDef != null) {
            holder.metricName.text = holder.view.context.getString(metricDef.titleRes)
            holder.metricCategory.text = holder.view.context.getString(metricDef.category.titleRes)
        } else {
            holder.metricName.text = item.key
            holder.metricCategory.text = ""
        }

        updateVisibilityUi(holder, item.isVisible)

        holder.visibilityToggle.setOnClickListener {
            item.isVisible = !item.isVisible
            updateVisibilityUi(holder, item.isVisible)
            onItemsChangedListener()
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }

        holder.deleteButton.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION && currentPos < items.size) {
                items.removeAt(currentPos)
                notifyItemRemoved(currentPos)
                onItemsChangedListener()
                it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }

        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onStartDragListener(holder)
            }
            false
        }
    }

    private fun updateVisibilityUi(holder: ViewHolder, isVisible: Boolean) {
        if (isVisible) {
            holder.visibilityToggle.setImageResource(R.drawable.ic_show_on_map)
            holder.visibilityToggle.alpha = 1.0f
            holder.metricName.alpha = 1.0f
            holder.metricCategory.alpha = 1.0f
            holder.visibilityToggle.contentDescription = holder.view.context.getString(R.string.shared_string_hide)
        } else {
            holder.visibilityToggle.setImageResource(R.drawable.ic_action_hide)
            holder.visibilityToggle.alpha = 0.4f
            holder.metricName.alpha = 0.45f
            holder.metricCategory.alpha = 0.45f
            holder.visibilityToggle.contentDescription = holder.view.context.getString(R.string.shared_string_show)
        }
    }

    override fun getItemCount(): Int = items.size

    fun moveItem(from: Int, to: Int) {
        if (from < to) {
            for (i in from until to) {
                Collections.swap(items, i, i + 1)
            }
        } else {
            for (i in from downTo to + 1) {
                Collections.swap(items, i, i - 1)
            }
        }
        notifyItemMoved(from, to)
        onItemsChangedListener()
    }

    fun addItem(key: String) {
        if (items.none { it.key == key }) {
            items.add(TelemetryItemConfig(key, isVisible = true))
            notifyItemInserted(items.size - 1)
            onItemsChangedListener()
        }
    }

    fun setItems(newItems: List<TelemetryItemConfig>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
        onItemsChangedListener()
    }

    fun getItems(): List<TelemetryItemConfig> = items
}
