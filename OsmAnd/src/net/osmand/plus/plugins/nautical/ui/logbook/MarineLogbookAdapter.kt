package net.osmand.plus.plugins.nautical.ui.logbook

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.logbook.data.LogbookEntry
import java.text.SimpleDateFormat
import java.util.*

class MarineLogbookAdapter : ListAdapter<LogbookEntry, MarineLogbookAdapter.LogbookViewHolder>(DiffCallback) {

    var onEntryClickListener: ((LogbookEntry) -> Unit)? = null

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogbookViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_logbook_entry, parent, false)
        return LogbookViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogbookViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LogbookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timestamp: TextView = itemView.findViewById(R.id.timestamp)
        private val position: TextView = itemView.findViewById(R.id.position)
        private val navData: TextView = itemView.findViewById(R.id.navigation_data)
        private val windData: TextView = itemView.findViewById(R.id.wind_data)
        private val vesselState: TextView = itemView.findViewById(R.id.vessel_state)
        private val notes: TextView = itemView.findViewById(R.id.notes)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onEntryClickListener?.invoke(getItem(position))
                }
            }
        }

        fun bind(entry: LogbookEntry) {
            val context = itemView.context
            timestamp.text = dateFormat.format(Date(entry.timestamp))
            position.text = String.format(Locale.US, "%.4f, %.4f", entry.latitude, entry.longitude)

            val sogKn = entry.sog?.let { it * 1.94384 }
            val cogDeg = entry.cog?.let { Math.toDegrees(it) }
            navData.text = context.getString(
                R.string.logbook_format_nav_data,
                context.getString(R.string.logbook_header_sog),
                sogKn?.let { String.format(Locale.US, "%.1fkn", it) } ?: "--",
                cogDeg?.let { String.format(Locale.US, "%03.0f°", it) } ?: "--"
            )

            val twsKn = entry.tws?.let { it * 1.94384 }
            val twaDeg = entry.twa?.let { Math.toDegrees(it) }
            windData.text = context.getString(
                R.string.logbook_format_wind_data,
                twsKn?.let { String.format(Locale.US, "%.1fkn", it) } ?: "--",
                twaDeg?.let { String.format(Locale.US, "%1.0f°", it) } ?: "--"
            )

            val sailPlan = entry.sailPlan.ifEmpty { "--" }
            val engineHours = entry.engineHours?.let { String.format(Locale.US, "%.1fh", it) } ?: "--"
            vesselState.text = context.getString(
                R.string.logbook_format_vessel_state,
                context.getString(R.string.nautical_sail_plan),
                sailPlan,
                context.getString(R.string.nautical_engine_state),
                engineHours
            )

            if (entry.notes.isNotEmpty()) {
                notes.text = entry.notes
                notes.visibility = View.VISIBLE
            } else {
                notes.visibility = View.GONE
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<LogbookEntry>() {
        override fun areItemsTheSame(oldItem: LogbookEntry, newItem: LogbookEntry): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: LogbookEntry, newItem: LogbookEntry): Boolean = oldItem == newItem
    }
}
