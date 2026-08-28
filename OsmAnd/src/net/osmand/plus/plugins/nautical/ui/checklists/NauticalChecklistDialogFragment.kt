package net.osmand.plus.plugins.nautical.ui.checklists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ChecklistType {
    PRE_DEPARTURE,
    HEAVY_WEATHER,
    WATCH_HANDOVER
}

data class ChecklistItem(
    val title: String,
    var isChecked: Boolean = false
)

class NauticalChecklistDialogFragment : BottomSheetDialogFragment() {

    private var currentType = ChecklistType.PRE_DEPARTURE
    private val preDepartureItems = mutableListOf(
        ChecklistItem("Bilges inspected & pumps operational"),
        ChecklistItem("Engine oil, coolant & transmission fluid checked"),
        ChecklistItem("Raw water strainer clear & seacocks open"),
        ChecklistItem("Standing & running rigging inspected"),
        ChecklistItem("Steering gear & emergency tiller verified"),
        ChecklistItem("EPIRB, flares & safety gear within expiry")
    )

    private val heavyWeatherItems = mutableListOf(
        ChecklistItem("Companionway boards & deck hatches dogged shut"),
        ChecklistItem("Jacklines rigged on port & starboard decks"),
        ChecklistItem("Reefing lines reeved & storm sails prepared"),
        ChecklistItem("Loose cabin gear & galley items lashed down"),
        ChecklistItem("Bilge high-water alarms tested & operational"),
        ChecklistItem("Lifejackets & tethers donned by all watchkeepers")
    )

    private val watchHandoverItems = mutableListOf(
        ChecklistItem("3-hour Barometer trend & squall risks noted"),
        ChecklistItem("AIS & Radar targets / closest CPA reviewed"),
        ChecklistItem("Navigation lights, battery SOC & engine temps checked"),
        ChecklistItem("Logbook updated with lat/lon, log & weather observations"),
        ChecklistItem("Standing orders & course changes acknowledged")
    )

    private lateinit var adapter: ChecklistAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var txtProgress: TextView
    private lateinit var txtStatus: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_nautical_checklist, container, false)
        val app = requireActivity().application as OsmandApplication

        view.findViewById<View>(R.id.btn_close_checklist)?.setOnClickListener { dismiss() }

        val toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.toggle_checklist_type)
        progressBar = view.findViewById(R.id.progress_bar_checklist)
        txtProgress = view.findViewById(R.id.txt_checklist_progress)
        txtStatus = view.findViewById(R.id.txt_checklist_status)

        val rv = view.findViewById<RecyclerView>(R.id.rv_checklist_items)
        rv.layoutManager = LinearLayoutManager(context)
        adapter = ChecklistAdapter(getCurrentItems()) {
            updateProgress()
        }
        rv.adapter = adapter

        toggleGroup.check(R.id.btn_type_predeparture)
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentType = when (checkedId) {
                    R.id.btn_type_heavy_weather -> ChecklistType.HEAVY_WEATHER
                    R.id.btn_type_watch_handover -> ChecklistType.WATCH_HANDOVER
                    else -> ChecklistType.PRE_DEPARTURE
                }
                adapter.setItems(getCurrentItems())
                updateProgress()
            }
        }

        view.findViewById<MaterialButton>(R.id.btn_reset_checklist)?.setOnClickListener {
            getCurrentItems().forEach { it.isChecked = false }
            adapter.notifyDataSetChanged()
            updateProgress()
        }

        view.findViewById<MaterialButton>(R.id.btn_log_checklist)?.setOnClickListener {
            val total = getCurrentItems().size
            val checked = getCurrentItems().count { it.isChecked }
            val timeStr = SimpleDateFormat("HH:mm 'UTC'", Locale.US).format(Date())
            val typeStr = currentType.name.replace('_', ' ')
            val logEntry = "CHECKLIST LOGGED: $typeStr ($checked/$total complete at $timeStr)"
            
            NauticalPlugin.hudManager?.get()?.showBanner(logEntry, 8000L, isWarning = false, priority = 3)
            app.showToastMessage(logEntry)
            dismiss()
        }

        updateProgress()

        val isNightVision = NauticalPlugin.isNightVision(app)
        if (isNightVision) {
            view.setBackgroundColor(0xEE120000.toInt())
            view.findViewById<TextView>(R.id.txt_checklist_title)?.setTextColor(0xFFFF1744.toInt())
        }

        return view
    }

    private fun getCurrentItems(): MutableList<ChecklistItem> {
        return when (currentType) {
            ChecklistType.PRE_DEPARTURE -> preDepartureItems
            ChecklistType.HEAVY_WEATHER -> heavyWeatherItems
            ChecklistType.WATCH_HANDOVER -> watchHandoverItems
        }
    }

    private fun updateProgress() {
        val items = getCurrentItems()
        val total = items.size
        val checked = items.count { it.isChecked }
        val pct = if (total > 0) (checked * 100) / total else 0

        progressBar.progress = pct
        txtProgress.text = "Progress: $checked / $total ($pct%)"
        if (pct == 100) {
            txtStatus.text = "ALL VERIFIED"
            txtStatus.setTextColor(0xFF43A047.toInt())
        } else {
            txtStatus.text = "INCOMPLETE"
            txtStatus.setTextColor(0xFFE53935.toInt())
        }
    }

    companion object {
        private const val TAG = "NauticalChecklistDialogFragment"

        fun show(fragmentManager: FragmentManager) {
            NauticalChecklistDialogFragment().show(fragmentManager, TAG)
        }
    }

    private class ChecklistAdapter(
        private var items: List<ChecklistItem>,
        private val onItemChecked: () -> Unit
    ) : RecyclerView.Adapter<ChecklistAdapter.ViewHolder>() {

        fun setItems(newItems: List<ChecklistItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val cb = CheckBox(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                minHeight = (48 * resources.displayMetrics.density).toInt()
                textSize = 13f
            }
            return ViewHolder(cb)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.checkBox.text = item.title
            holder.checkBox.setOnCheckedChangeListener(null)
            holder.checkBox.isChecked = item.isChecked
            holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                item.isChecked = isChecked
                onItemChecked()
            }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(val checkBox: CheckBox) : RecyclerView.ViewHolder(checkBox)
    }
}
