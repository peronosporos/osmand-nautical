package net.osmand.plus.plugins.nautical.ui

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
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.NotificationState
import net.osmand.plus.plugins.nautical.engine.SignalKNotification

class NauticalNotificationsFragment : BaseOsmAndFragment() {

    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.recyclerview_fragment, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        
        adapter = NotificationAdapter { path ->
            NauticalPlugin.engine?.acknowledgeNotification(path)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                val notifications = state.notifications.entries.sortedByDescending { it.value.state }.toList()
                adapter.submitList(notifications)
                val emptyView = view.findViewById<TextView>(R.id.txt_empty_list)
                val connected = NauticalPlugin.getInstance()?.isSignalKConnected() == true
                if (!connected) {
                    emptyView?.text = getString(R.string.nautical_notifications_disconnected)
                } else if (notifications.isEmpty()) {
                    emptyView?.text = getString(R.string.nautical_notifications_no_alarms)
                }
                emptyView?.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        return view
    }

    private class NotificationAdapter(private val onAcknowledge: (String) -> Unit) : 
        ListAdapter<Map.Entry<String, SignalKNotification>, NotificationViewHolder>(DiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_notification, parent, false)
            return NotificationViewHolder(view)
        }

        override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
            holder.bind(getItem(position), onAcknowledge)
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Map.Entry<String, SignalKNotification>>() {
        override fun areItemsTheSame(oldItem: Map.Entry<String, SignalKNotification>, newItem: Map.Entry<String, SignalKNotification>): Boolean = oldItem.key == newItem.key
        override fun areContentsTheSame(oldItem: Map.Entry<String, SignalKNotification>, newItem: Map.Entry<String, SignalKNotification>): Boolean = oldItem.value == newItem.value
    }

    private class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtMessage: TextView = view.findViewById(R.id.txt_notif_message)
        private val txtState: TextView = view.findViewById(R.id.txt_notif_state)
        private val btnAck: MaterialButton = view.findViewById(R.id.btn_acknowledge)

        fun bind(entry: Map.Entry<String, SignalKNotification>, onAcknowledge: (String) -> Unit) {
            val path = entry.key
            val notif = entry.value
            val ctx = itemView.context
            txtMessage.text = notif.message
            txtState.text = when (notif.state) {
                NotificationState.EMERGENCY -> ctx.getString(R.string.nautical_notification_state_emergency)
                NotificationState.ALARM -> ctx.getString(R.string.nautical_notification_state_alarm)
                NotificationState.WARN, NotificationState.ALERT -> ctx.getString(R.string.nautical_notification_state_warning)
                else -> ctx.getString(R.string.shared_string_normal)
            }

            val app = ctx.applicationContext as? OsmandApplication
            val isNight = app?.let { NauticalPlugin.isNightVision(it) } ?: false
            val color = when (notif.state) {
                NotificationState.EMERGENCY -> ContextCompat.getColor(ctx, if (isNight) R.color.nautical_night_vision_primary else R.color.nautical_status_red)
                NotificationState.ALARM -> ContextCompat.getColor(ctx, if (isNight) R.color.nautical_night_vision_primary else R.color.nautical_status_orange)
                NotificationState.WARN -> ContextCompat.getColor(ctx, if (isNight) R.color.nautical_night_vision_data else R.color.nautical_status_yellow)
                NotificationState.ALERT -> ContextCompat.getColor(ctx, if (isNight) R.color.nautical_night_vision_data else R.color.nautical_status_blue)
                else -> if (isNight) ContextCompat.getColor(ctx, R.color.nautical_night_vision_muted)
                    else net.osmand.plus.utils.AndroidUtils.getColorFromAttr(ctx, android.R.attr.textColorSecondary)
            }
            txtState.setTextColor(color)
            
            btnAck.setOnClickListener { onAcknowledge(path) }
            btnAck.visibility = if (notif.state != NotificationState.NORMAL) View.VISIBLE else View.GONE
        }
    }
}
