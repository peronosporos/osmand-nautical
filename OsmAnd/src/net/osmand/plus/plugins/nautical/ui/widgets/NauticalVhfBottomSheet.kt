package net.osmand.plus.plugins.nautical.ui.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.network.NauticalVhfManager
import net.osmand.plus.plugins.nautical.network.VhfStatus
import net.osmand.plus.plugins.nautical.network.VhfTransmission
import net.osmand.plus.plugins.nautical.ui.dialogs.VhfChannelPickerDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NauticalVhfBottomSheet : BaseNauticalBottomSheet() {

    private val transmissionAdapter = TransmissionAdapter { item ->
        NauticalPlugin.getInstance()?.vhfManager?.playReplay(item)
    }

    private var isHighPower = true
    private var isDualWatch = false

    override fun createMenuItems(savedInstanceState: Bundle?) {
        addTitleItem(getString(R.string.nautical_vhf_title))

        val themedCtx = net.osmand.plus.utils.UiUtilities.getThemedContext(requireContext(), nightMode)
        val customView = LayoutInflater.from(themedCtx).inflate(R.layout.bottom_sheet_nautical_vhf, null)

        val txtActiveChannel = customView.findViewById<TextView>(R.id.txt_vhf_active_channel)
        val txtDesignation = customView.findViewById<TextView>(R.id.txt_vhf_channel_designation)
        val txtStatus = customView.findViewById<TextView>(R.id.txt_vhf_reception_status)
        val btnPower = customView.findViewById<MaterialButton>(R.id.btn_vhf_power_toggle)
        val btnDualWatch = customView.findViewById<MaterialButton>(R.id.btn_vhf_dual_watch_toggle)
        val btnLiveAudio = customView.findViewById<MaterialButton>(R.id.btn_toggle_live_audio)
        val btnSelectChannel = customView.findViewById<MaterialButton>(R.id.btn_select_channel)

        val rvTransmissions = customView.findViewById<RecyclerView>(R.id.rv_vhf_transmissions)
        val txtNoTransmissions = customView.findViewById<View>(R.id.txt_no_vhf_transmissions)

        rvTransmissions.layoutManager = LinearLayoutManager(context)
        rvTransmissions.adapter = transmissionAdapter

        // Quick Access Safety Channel buttons
        setupChannelButton(customView.findViewById(R.id.btn_ch_16), "16")
        setupChannelButton(customView.findViewById(R.id.btn_ch_09), "09")
        setupChannelButton(customView.findViewById(R.id.btn_ch_13), "13")
        setupChannelButton(customView.findViewById(R.id.btn_ch_72), "72")
        setupChannelButton(customView.findViewById(R.id.btn_ch_77), "77")
        setupChannelButton(customView.findViewById(R.id.btn_ch_06), "06")

        btnSelectChannel.setOnClickListener {
            val activity = activity as? net.osmand.plus.activities.MapActivity
            if (activity != null) {
                VhfChannelPickerDialog.show(activity.supportFragmentManager)
            }
        }

        btnPower.setOnClickListener {
            isHighPower = !isHighPower
            btnPower.text = if (isHighPower) "25W HIGH" else "1W LOW"
            NauticalPlugin.engine?.sendDelta("communication.vhf.power", if (isHighPower) "25W" else "1W")
        }

        btnDualWatch.setOnClickListener {
            isDualWatch = !isDualWatch
            btnDualWatch.text = if (isDualWatch) "DUAL WATCH: ON (CH 16)" else "DUAL WATCH: OFF"
            NauticalPlugin.engine?.sendDelta("communication.vhf.dualWatch", isDualWatch)
        }

        btnLiveAudio.setOnClickListener {
            NauticalPlugin.getInstance()?.vhfManager?.toggleLiveStream()
        }

        // Live flow observers
        val vhfManager = NauticalPlugin.getInstance()?.vhfManager
        if (vhfManager != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                vhfManager.status.collectLatest { status ->
                    when (status) {
                        VhfStatus.LIVE -> {
                            btnLiveAudio.text = "Stop Audio"
                            txtStatus.text = "Status: Live Audio Stream Active"
                            txtStatus.setTextColor(androidx.core.content.ContextCompat.getColor(themedCtx, R.color.color_ok))
                        }
                        VhfStatus.REPLAYING -> {
                            btnLiveAudio.text = "Live Stream"
                            txtStatus.text = "Status: Replaying Recorded Transmission"
                            txtStatus.setTextColor(androidx.core.content.ContextCompat.getColor(themedCtx, R.color.color_warning))
                        }
                        VhfStatus.IDLE -> {
                            btnLiveAudio.text = "Live Stream"
                            txtStatus.text = "Status: Standby / Ready"
                            txtStatus.setTextColor(androidx.core.content.ContextCompat.getColor(themedCtx, R.color.text_color_secondary_light))
                        }
                    }
                }
            }

            viewLifecycleOwner.lifecycleScope.launch {
                vhfManager.history.collectLatest { list ->
                    transmissionAdapter.submitList(list)
                    txtNoTransmissions.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                val ch = state.vhfChannel ?: "16"
                txtActiveChannel.text = "CH $ch"
                txtDesignation.text = getChannelDesignation(ch)
            }
        }

        items.add(BaseBottomSheetItem.Builder().setCustomView(customView).create())
    }

    private fun setupChannelButton(btn: MaterialButton?, channel: String) {
        btn?.setOnClickListener {
            NauticalPlugin.engine?.sendDelta("communication.vhf.channel", channel)
        }
    }

    private fun getChannelDesignation(channel: String): String {
        return when (channel.trim().uppercase(Locale.US)) {
            "16" -> "International Distress, Safety & Calling"
            "09", "9" -> "Secondary Calling & Commercial / Non-commercial"
            "13" -> "Inter-ship Navigation Safety & Bridge-to-Bridge"
            "06", "6" -> "Inter-ship Safety & Search and Rescue (SAR)"
            "70" -> "Digital Selective Calling (DSC) Alerting"
            "72" -> "Non-Commercial Ship-to-Ship Intercom"
            "77" -> "Port Operations & Ship-to-Ship Commercial"
            "68" -> "Non-Commercial & Marina Operations"
            "12", "14" -> "Port Operations & Vessel Traffic Service (VTS)"
            "22A", "22" -> "Coast Guard Maritime Safety Broadcasts"
            else -> "Marine VHF Channel $channel"
        }
    }

    companion object {
        const val TAG = "NauticalVhfBottomSheet"

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                NauticalVhfBottomSheet().show(fragmentManager, TAG)
            }
        }
    }

    private class TransmissionAdapter(private val onPlay: (VhfTransmission) -> Unit) :
        ListAdapter<VhfTransmission, TransmissionViewHolder>(DiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransmissionViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_vhf_transmission, parent, false)
            return TransmissionViewHolder(view)
        }

        override fun onBindViewHolder(holder: TransmissionViewHolder, position: Int) {
            holder.bind(getItem(position), onPlay)
        }

        private class DiffCallback : DiffUtil.ItemCallback<VhfTransmission>() {
            override fun areItemsTheSame(oldItem: VhfTransmission, newItem: VhfTransmission): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: VhfTransmission, newItem: VhfTransmission): Boolean = oldItem == newItem
        }
    }

    private class TransmissionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtTitle: TextView = view.findViewById(R.id.txt_transmission_title)
        private val txtTime: TextView = view.findViewById(R.id.txt_transmission_time)
        private val btnReplay: MaterialButton = view.findViewById(R.id.btn_replay_audio)
        private val imgPlay: ImageView = view.findViewById(R.id.img_play_icon)
        private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        fun bind(item: VhfTransmission, onPlay: (VhfTransmission) -> Unit) {
            val vessel = item.vesselName ?: "Vessel Broadcast"
            val ch = item.channel?.let { "CH $it" } ?: "VHF"
            txtTitle.text = "$ch • $vessel"
            
            val dateStr = timeFmt.format(Date(item.timestamp))
            val agoSec = (System.currentTimeMillis() - item.timestamp) / 1000
            val agoStr = if (agoSec < 60) "${agoSec}s ago" else "${agoSec / 60}m ago"
            txtTime.text = "$dateStr ($agoStr)"

            btnReplay.setOnClickListener { onPlay(item) }
            imgPlay.setOnClickListener { onPlay(item) }
        }
    }
}
