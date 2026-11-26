package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.HorizontalScrollView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.CapabilityManager
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.engine.SignalKDataBroker
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import kotlin.math.roundToInt

class SignalKDiagnosticsFragment : BaseOsmAndFragment() {

    private lateinit var adapter: DiagnosticAdapter
    private val pathUpdateCounts = mutableMapOf<String, Int>()
    private val pathLastUpdate = mutableMapOf<String, Long>()
    private val pathDropouts = mutableMapOf<String, Int>()
    private var lastSampleTime = System.currentTimeMillis()
    private val pathHzRates = mutableMapOf<String, Double>()

    private var selectedCategory: SignalKDataBroker.PacketCategory = SignalKDataBroker.PacketCategory.ALL
    private var isStreamPaused = false
    private val packetBuffer = ArrayDeque<SignalKDataBroker.DiagnosticPacket>(200)
    private var totalPacketCountInSecond = 0
    private var lastPacketSecTime = System.currentTimeMillis()
    private var currentPacketHz = 0.0
    private var txtPacketRate: TextView? = null
    private var txtTerminal: TextView? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            val p = (8 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }

        val isNightVision = NauticalPlugin.isNightVision(app)
        if (isNightVision) {
            root.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.nautical_night_vision_bg))
        }

        // 1. Control Toolbar (Rate Hz, Pause/Resume, Export Log)
        val toolbar = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val tp = (4 * resources.displayMetrics.density).toInt()
            setPadding(tp, tp, tp, tp)
        }

        val rateView = TextView(requireContext()).apply {
            text = getString(R.string.nautical_diag_stream_fmt, 0.0)
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(ContextCompat.getColor(requireContext(), if (isNightVision) R.color.nautical_night_vision_data else R.color.nautical_diag_rate_text))
        }
        txtPacketRate = rateView
        toolbar.addView(rateView)

        val btnPause = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.shared_string_pause)
            minHeight = (48 * resources.displayMetrics.density).toInt()
            setTextColor(ContextCompat.getColor(requireContext(), if (isNightVision) R.color.nautical_night_vision_primary else R.color.nautical_diag_accent))
            setOnClickListener {
                isStreamPaused = !isStreamPaused
                text = getString(if (isStreamPaused) R.string.shared_string_resume else R.string.shared_string_pause)
            }
        }
        toolbar.addView(btnPause)

        val btnExport = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.nautical_diag_export_log)
            minHeight = (48 * resources.displayMetrics.density).toInt()
            setTextColor(ContextCompat.getColor(requireContext(), if (isNightVision) R.color.nautical_night_vision_primary else R.color.nautical_diag_accent))
            setOnClickListener {
                exportDiagnosticLog()
            }
        }
        toolbar.addView(btnExport)
        root.addView(toolbar)

        // 2. Filter Tabs (All, Navigation, Engines/Tanks, Alarms/AIS, Errors)
        val scrollChips = HorizontalScrollView(requireContext()).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val chipGroup = ChipGroup(requireContext()).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }

        val categories = listOf(
            SignalKDataBroker.PacketCategory.ALL to getString(R.string.shared_string_all),
            SignalKDataBroker.PacketCategory.NAVIGATION to getString(R.string.nautical_diag_cat_navigation),
            SignalKDataBroker.PacketCategory.ENGINES_TANKS to getString(R.string.nautical_diag_cat_engines_tanks),
            SignalKDataBroker.PacketCategory.ALARMS_AIS to getString(R.string.nautical_diag_cat_alarms_ais),
            SignalKDataBroker.PacketCategory.ERRORS to getString(R.string.nautical_diag_cat_errors)
        )

        categories.forEachIndexed { index, (cat, title) ->
            val chip = Chip(requireContext()).apply {
                text = title
                isCheckable = true
                isChecked = (index == 0)
                minHeight = (48 * resources.displayMetrics.density).toInt()
                if (isNightVision) {
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.nautical_night_vision_data))
                    setChipBackgroundColorResource(R.color.nautical_night_vision_surface)
                }
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedCategory = cat
                        updateTerminalText()
                    }
                }
            }
            chipGroup.addView(chip)
        }
        scrollChips.addView(chipGroup)
        root.addView(scrollChips)

        // 3. Monospace Live Packet Terminal Window
        val terminal = TextView(requireContext()).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 11f
            setLines(6)
            maxLines = 6
            val bg = if (isNightVision) R.color.nautical_night_vision_surface else R.color.nautical_diag_terminal_bg
            setBackgroundColor(ContextCompat.getColor(requireContext(), bg))
            val fg = if (isNightVision) R.color.nautical_night_vision_data else R.color.nautical_diag_terminal_fg
            setTextColor(ContextCompat.getColor(requireContext(), fg))
            val pad = (6 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            text = getString(R.string.nautical_diag_connecting)
        }
        txtTerminal = terminal
        root.addView(terminal)

        // 4. Telemetry metrics & Server Capability list
        val recyclerView = RecyclerView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            if (isNightVision) {
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.nautical_night_vision_bg))
            }
        }
        root.addView(recyclerView)

        adapter = DiagnosticAdapter(isNightVision) {
            val ip = app.settings.NAUTICAL_SERVER_IP.get()
            val port = app.settings.NAUTICAL_SERVER_PORT.get()
            val url = "http://$ip:$port/admin"
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            requireActivity().startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                updatePathMetrics(state)
                val caps = NauticalPlugin.getInstance()?.capabilityManager?.capabilities?.value
                adapter.submitList(generateDiagnosticItems(caps, state, isNightVision))
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val dataBroker = NauticalPlugin.engine?.dataBroker
            dataBroker?.livePackets?.collect { packet ->
                onNewPacket(packet)
            }
        }

        return root
    }

    private fun onNewPacket(packet: SignalKDataBroker.DiagnosticPacket) {
        totalPacketCountInSecond++
        val now = System.currentTimeMillis()
        if (now - lastPacketSecTime >= 1000L) {
            val dt = (now - lastPacketSecTime) / 1000.0
            currentPacketHz = totalPacketCountInSecond / dt
            totalPacketCountInSecond = 0
            lastPacketSecTime = now
            txtPacketRate?.text = getString(R.string.nautical_diag_stream_fmt, String.format(Locale.US, "%.1f", currentPacketHz).toDouble())
        }

        if (isStreamPaused) return

        if (packetBuffer.size >= 100) {
            packetBuffer.removeFirst()
        }
        packetBuffer.addLast(packet)
        updateTerminalText()
    }

    private fun updateTerminalText() {
        val filtered = packetBuffer.filter {
            selectedCategory == SignalKDataBroker.PacketCategory.ALL || it.category == selectedCategory
        }.takeLast(6)

        val sb = StringBuilder()
        for (p in filtered) {
            val timeStr = timeFormat.format(Date(p.timestamp))
            val prefix = if (p.isError) "[ERR]" else "[NMEA]"
            sb.append(timeStr).append(" ").append(prefix).append(" ").append(p.path).append(" = ").append(p.value).append("\n")
        }
        txtTerminal?.text = if (sb.isNotEmpty()) sb.toString().trimEnd() else getString(R.string.nautical_diag_waiting_packets)
    }

    private fun exportDiagnosticLog() {
        val sb = StringBuilder("=== OsmAnd Nautical Signal K Diagnostic Log ===\n")
        sb.append("Timestamp: ").append(Date().toString()).append("\n\n")
        sb.append("--- Live Packets ---\n")
        for (p in packetBuffer) {
            val timeStr = timeFormat.format(Date(p.timestamp))
            sb.append(timeStr).append(" [").append(p.category.name).append("] ").append(p.path).append(" = ").append(p.value).append("\n")
        }

        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("SignalK Diagnostic Log", sb.toString())
        clipboard?.setPrimaryClip(clip)
        app.showToastMessage(app.getString(net.osmand.plus.R.string.nautical_toast_diag_log_copied))
    }

    private fun updatePathMetrics(state: MarineState) {
        val now = System.currentTimeMillis()
        val dt = (now - lastSampleTime) / 1000.0

        state.timestamps.forEach { (path, ts) ->
            val prevTs = pathLastUpdate[path]
            if (prevTs != null && ts != prevTs) {
                val deltaMs = ts - prevTs
                if (deltaMs > 3000L) {
                    pathDropouts[path] = (pathDropouts[path] ?: 0) + 1
                }
                pathUpdateCounts[path] = (pathUpdateCounts[path] ?: 0) + 1
            }
            pathLastUpdate[path] = ts
        }

        if (dt >= 1.0) {
            pathUpdateCounts.forEach { (path, count) ->
                pathHzRates[path] = count / dt
            }
            pathUpdateCounts.clear()
            lastSampleTime = now
        }
    }

    private fun generateDiagnosticItems(
        caps: CapabilityManager.ServerCapabilityMap?,
        state: MarineState,
        isNightVision: Boolean
    ): List<DiagnosticItem> {
        val items = mutableListOf<DiagnosticItem>()
        val now = System.currentTimeMillis()

        // 1. Real-time Telemetry Path Counters (Hz rate, last update age, dropouts)
        val standardPaths = listOf(
            "navigation.position" to getString(R.string.nautical_diag_path_position),
            "navigation.speedOverGround" to getString(R.string.nautical_diag_path_sog),
            "navigation.courseOverGroundTrue" to getString(R.string.nautical_diag_path_cog),
            "navigation.headingTrue" to getString(R.string.nautical_diag_path_hdg),
            "environment.wind.speedApparent" to getString(R.string.nautical_diag_path_aws),
            "environment.wind.angleApparent" to getString(R.string.nautical_diag_path_awa),
            "environment.depth.belowTransducer" to getString(R.string.nautical_diag_path_dbt),
            "steering.rudderAngle" to getString(R.string.nautical_diag_path_rudder),
            "propulsion.main.revolutions" to getString(R.string.nautical_diag_path_rpm)
        )

        standardPaths.forEach { (path, label) ->
            val ts = state.timestamps[path] ?: pathLastUpdate[path]
            val ageSec = if (ts != null && ts > 0) ((now - ts) / 1000.0).coerceAtLeast(0.0) else -1.0
            val hz = pathHzRates[path] ?: 0.0
            val dropouts = pathDropouts[path] ?: 0
            val isStale = ageSec < 0 || ageSec > 5.0
            val hasData = ageSec >= 0

            val desc = if (hasData) {
                getString(R.string.nautical_diag_rate_fmt, hz, ageSec, dropouts)
            } else {
                getString(R.string.nautical_diag_no_telemetry)
            }

            items.add(
                DiagnosticItem(
                    feature = label,
                    active = !isStale && hasData,
                    guidance = desc,
                    isTelemetryPath = true,
                    isStale = isStale
                )
            )
        }

        // 2. Server Capability Plugins
        if (caps != null) {
            items.add(DiagnosticItem(getString(R.string.nautical_diag_cap_polar), caps.hasPolarPerformance, getString(R.string.nautical_diag_cap_polar_desc)))
            items.add(DiagnosticItem(getString(R.string.nautical_diag_cap_autopilot), caps.hasAutopilot, getString(R.string.nautical_diag_cap_autopilot_desc)))
            items.add(DiagnosticItem(getString(R.string.nautical_diag_cap_logbook), caps.hasLogging, getString(R.string.nautical_diag_cap_logbook_desc)))
            items.add(DiagnosticItem(getString(R.string.nautical_diag_cap_tides), caps.hasSignalKTides, getString(R.string.nautical_diag_cap_tides_desc)))
            items.add(DiagnosticItem(getString(R.string.nautical_diag_cap_grib), caps.hasGrib, getString(R.string.nautical_diag_cap_grib_desc)))
            items.add(DiagnosticItem(getString(R.string.nautical_diag_cap_ais), caps.hasAisPrioritizer, getString(R.string.nautical_diag_cap_ais_desc)))
            items.add(DiagnosticItem(getString(R.string.nautical_diag_cap_digital), caps.hasDigitalSwitching, getString(R.string.nautical_diag_cap_digital_desc)))
            items.add(DiagnosticItem(getString(R.string.nautical_diag_cap_envsensors), caps.hasEnvironmentSensors, getString(R.string.nautical_diag_cap_envsensors_desc)))
            items.add(DiagnosticItem(getString(R.string.nautical_diag_cap_forwardwatch), caps.hasForwardWatch, getString(R.string.nautical_diag_cap_forwardwatch_desc)))
            items.add(DiagnosticItem(getString(R.string.nautical_diag_cap_collision), caps.hasAdvancedSafety, getString(R.string.nautical_diag_cap_collision_desc)))
        }

        return items
    }

    private data class DiagnosticItem(
        val feature: String,
        val active: Boolean,
        val guidance: String,
        val isTelemetryPath: Boolean = false,
        val isStale: Boolean = false
    )

    private class DiagnosticAdapter(
        private val isNightVision: Boolean,
        private val onClick: () -> Unit
    ) : ListAdapter<DiagnosticItem, DiagnosticViewHolder>(
        object : androidx.recyclerview.widget.DiffUtil.ItemCallback<DiagnosticItem>() {
            override fun areItemsTheSame(oldItem: DiagnosticItem, newItem: DiagnosticItem): Boolean = oldItem.feature == newItem.feature
            override fun areContentsTheSame(oldItem: DiagnosticItem, newItem: DiagnosticItem): Boolean = oldItem == newItem
        }
    ) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiagnosticViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_icon_and_menu, parent, false)
            return DiagnosticViewHolder(view)
        }

        override fun onBindViewHolder(holder: DiagnosticViewHolder, position: Int) {
            val item = getItem(position)
            holder.title.text = item.feature
            holder.description.text = if (item.isTelemetryPath) {
                item.guidance
            } else if (item.active) {
                holder.itemView.context.getString(R.string.nautical_diag_active_configured)
            } else {
                item.guidance
            }

            if (isNightVision) {
                val nCtx = holder.itemView.context
                holder.itemView.setBackgroundColor(ContextCompat.getColor(nCtx, R.color.nautical_night_vision_bg))
                holder.title.setTextColor(ContextCompat.getColor(nCtx, R.color.nautical_night_vision_primary))
                if (item.isStale) {
                    holder.description.setTextColor(ContextCompat.getColor(nCtx, R.color.nautical_night_vision_muted))
                    holder.icon.setColorFilter(ContextCompat.getColor(nCtx, R.color.nautical_night_vision_muted))
                } else {
                    holder.description.setTextColor(ContextCompat.getColor(nCtx, R.color.nautical_night_vision_data))
                    holder.icon.setColorFilter(ContextCompat.getColor(nCtx, R.color.nautical_night_vision_primary))
                }
                holder.secondaryIcon.setColorFilter(ContextCompat.getColor(nCtx, R.color.nautical_night_vision_data))
            } else {
                val colorRes = if (item.active) R.color.nautical_status_green else R.color.nautical_status_red
                holder.description.setTextColor(ContextCompat.getColor(holder.itemView.context, colorRes))
                holder.icon.clearColorFilter()
                holder.secondaryIcon.clearColorFilter()
            }

            holder.icon.setImageResource(
                if (item.isTelemetryPath) {
                    if (item.active) R.drawable.ic_action_speed else R.drawable.ic_action_alert
                } else {
                    if (item.active) R.drawable.ic_action_done else R.drawable.ic_action_alert
                }
            )

            if (!item.isTelemetryPath) {
                holder.secondaryIcon.setImageResource(R.drawable.ic_action_settings)
                holder.secondaryIcon.visibility = View.VISIBLE
                holder.secondaryIcon.setOnClickListener { onClick() }
            } else {
                holder.secondaryIcon.visibility = View.GONE
            }
            holder.toggle.visibility = View.GONE
        }
    }

    private class DiagnosticViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.title)
        val description: TextView = view.findViewById(R.id.description)
        val icon: ImageView = view.findViewById(R.id.icon)
        val secondaryIcon: ImageView = view.findViewById(R.id.secondary_icon)
        val toggle: View = view.findViewById(R.id.toggle_item)
    }
}
