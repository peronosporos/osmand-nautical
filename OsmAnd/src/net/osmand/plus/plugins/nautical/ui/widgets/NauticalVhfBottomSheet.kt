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
        val btnDistressScript = customView.findViewById<MaterialButton>(R.id.btn_vhf_distress_script)

        btnDistressScript?.setOnClickListener {
            VhfDistressScriptBottomSheet.show(parentFragmentManager)
        }

        val rvTransmissions = customView.findViewById<RecyclerView>(R.id.rv_vhf_transmissions)
        val txtNoTransmissions = customView.findViewById<View>(R.id.txt_no_vhf_transmissions)

        rvTransmissions.layoutManager = LinearLayoutManager(context)
        rvTransmissions.adapter = transmissionAdapter

        // Quick Access Safety Channel buttons (Row 1: 16, 09, 13; Row 2: 06, 72, 77)
        setupChannelButton(customView.findViewById(R.id.btn_ch_16), "16")
        setupChannelButton(customView.findViewById(R.id.btn_ch_09), "09")
        setupChannelButton(customView.findViewById(R.id.btn_ch_13), "13")
        setupChannelButton(customView.findViewById(R.id.btn_ch_06), "06")
        setupChannelButton(customView.findViewById(R.id.btn_ch_72), "72")
        setupChannelButton(customView.findViewById(R.id.btn_ch_77), "77")

        btnSelectChannel.setOnClickListener {
            val items = ALL_VHF_CHANNELS.map { "CH ${it.first} • ${it.second}" }.toTypedArray()
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.nautical_vhf_title)
                .setItems(items) { _, which ->
                    val (num, _) = ALL_VHF_CHANNELS[which]
                    NauticalPlugin.engine?.sendDelta("communication.vhf.channel", num)
                    txtActiveChannel.text = "CH $num"
                    txtDesignation.text = getChannelDesignation(num)
                }
                .setNegativeButton(R.string.shared_string_cancel, null)
                .show()
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
        val clean = channel.trim().uppercase(Locale.US)
        val match = ALL_VHF_CHANNELS.find { it.first == clean || it.first.trimStart('0') == clean }
        return match?.second ?: when (clean) {
            "22A" -> "Coast Guard Maritime Safety Broadcasts"
            else -> "Marine VHF Channel $channel"
        }
    }

    companion object {
        const val TAG = "NauticalVhfBottomSheet"

        private val ALL_VHF_CHANNELS = arrayOf(
            "01" to "Port Operations & Commercial",
            "02" to "Public Correspondence & Port Ops",
            "03" to "Public Correspondence & Port Ops",
            "04" to "Public Correspondence & Port Ops",
            "05" to "Port Operations & VTS",
            "06" to "Inter-ship Safety & SAR",
            "07" to "Commercial Inter-ship",
            "08" to "Commercial Inter-ship (Safety)",
            "09" to "Secondary Calling & Commercial / Non-Commercial",
            "10" to "Port Operations & Commercial",
            "11" to "Port Operations & VTS",
            "12" to "Port Operations & Traffic",
            "13" to "Navigation Safety / Bridge-to-Bridge",
            "14" to "Port Operations & Traffic",
            "15" to "Environmental & On-board Comms (1W)",
            "16" to "International Distress, Safety & Calling",
            "17" to "State & Local Govt / On-board Comms (1W)",
            "18" to "Commercial & Port Operations",
            "19" to "Commercial & Coast Guard",
            "20" to "Port Operations & Coast Guard",
            "21" to "Coast Guard Maritime Safety",
            "22" to "Coast Guard Safety Broadcasts",
            "23" to "Coast Guard & Public Correspondence",
            "24" to "Public Correspondence & Marina",
            "25" to "Public Correspondence & Marina",
            "26" to "Public Correspondence & Marina",
            "27" to "Public Correspondence & Marina",
            "28" to "Public Correspondence & Marina",
            "60" to "Public Correspondence & Port Ops",
            "61" to "Public Correspondence & Port Ops",
            "62" to "Public Correspondence & Port Ops",
            "63" to "Port Operations & Traffic",
            "64" to "Public Correspondence & Port Ops",
            "65" to "Port Operations & Traffic",
            "66" to "Port Operations & Traffic",
            "67" to "Bridge-to-Bridge & Inter-ship SAR",
            "68" to "Non-Commercial Working & Marina",
            "69" to "Non-Commercial Working",
            "70" to "Digital Selective Calling (DSC Alerting)",
            "71" to "Port Operations & Non-Commercial",
            "72" to "Non-Commercial Ship-to-Ship Intercom",
            "73" to "Port Operations & Inter-ship",
            "74" to "Port Operations",
            "75" to "Navigation Safety (Low Power 1W)",
            "76" to "Navigation Safety (Low Power 1W)",
            "77" to "Port Operations & Ship-to-Ship Commercial",
            "78" to "Non-Commercial Working",
            "79" to "Commercial Working",
            "80" to "Commercial & Marina Operations",
            "81" to "Environmental & Government",
            "82" to "Public Correspondence & Marina",
            "83" to "Coast Guard Working",
            "84" to "Public Correspondence & Marina",
            "85" to "Public Correspondence & Marina",
            "86" to "Public Correspondence & Marina",
            "87" to "Public Correspondence & Port Ops",
            "88" to "Commercial Inter-ship"
        )

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.isStateSaved) return
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
        private val txtBadge: TextView? = view.findViewById(R.id.txt_transmission_badge)
        private val txtTitle: TextView = view.findViewById(R.id.txt_transmission_title)
        private val txtTime: TextView = view.findViewById(R.id.txt_transmission_time)
        private val txtDuration: TextView? = view.findViewById(R.id.txt_transmission_duration)
        private val txtTranscription: TextView? = view.findViewById(R.id.txt_transmission_transcription)
        private val progressReplay: View? = view.findViewById(R.id.progress_audio_replay)
        private val imgPlay: ImageView = view.findViewById(R.id.img_play_icon)
        private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        fun bind(item: VhfTransmission, onPlay: (VhfTransmission) -> Unit) {
            val ch = item.channel?.let { "CH $it" } ?: "VHF"
            txtBadge?.text = ch
            val vessel = item.vesselName ?: "Vessel Broadcast"
            txtTitle.text = vessel
            
            val dateStr = timeFmt.format(Date(item.timestamp))
            val agoSec = (System.currentTimeMillis() - item.timestamp) / 1000
            val agoStr = if (agoSec < 60) "${agoSec}s ago" else "${agoSec / 60}m ago"
            txtTime.text = "$dateStr ($agoStr)"

            if (item.durationSec > 0) {
                txtDuration?.visibility = View.VISIBLE
                val min = item.durationSec / 60
                val sec = item.durationSec % 60
                txtDuration?.text = String.format(Locale.US, "%d:%02d", min, sec)
            } else {
                txtDuration?.visibility = View.GONE
            }

            if (!item.transcription.isNullOrEmpty()) {
                txtTranscription?.visibility = View.VISIBLE
                txtTranscription?.text = item.transcription
            } else {
                txtTranscription?.visibility = View.GONE
            }

            imgPlay.setOnClickListener {
                progressReplay?.visibility = View.VISIBLE
                onPlay(item)
            }
            itemView.setOnClickListener {
                progressReplay?.visibility = View.VISIBLE
                onPlay(item)
            }
        }
    }
}
