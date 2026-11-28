package net.osmand.plus.plugins.nautical.ui.checklists

import android.os.Bundle
import net.osmand.plus.plugins.nautical.ui.NauticalNightWindowHelper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
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
    WATCH_HANDOVER;

    companion object {
        fun fromName(raw: String?): ChecklistType? =
            entries.firstOrNull { it.name == raw }
    }
}

/**
 * Pure encode/apply helpers for checklist tick state across a
 * configuration change. A saved array is applied only when its length
 * still matches the list, so a stale bundle (e.g. after checklist items
 * were edited between versions) is ignored rather than half-applied.
 */
object ChecklistSavedState {

    fun encodeChecked(items: List<ChecklistItem>): BooleanArray =
        BooleanArray(items.size) { index -> items[index].isChecked }

    fun applyChecked(items: List<ChecklistItem>, checked: BooleanArray?): Boolean {
        if (checked == null || checked.size != items.size) return false
        items.forEachIndexed { index, item -> item.isChecked = checked[index] }
        return true
    }
}

data class ChecklistItem(
    val title: String,
    var isChecked: Boolean = false
)

class NauticalChecklistDialogFragment : BottomSheetDialogFragment() {

    private var currentType = ChecklistType.PRE_DEPARTURE

    // Built lazily in onCreateView (after attach) so items can be resourced
    // via getString() instead of hardcoded English.
    private lateinit var preDepartureItems: MutableList<ChecklistItem>
    private lateinit var heavyWeatherItems: MutableList<ChecklistItem>
    private lateinit var watchHandoverItems: MutableList<ChecklistItem>

    private fun initChecklistItems() {
        preDepartureItems = mutableListOf(
            ChecklistItem(getString(R.string.nautical_chk_item_bilge_pumps)),
            ChecklistItem(getString(R.string.nautical_chk_item_fluids)),
            ChecklistItem(getString(R.string.nautical_chk_item_strainer)),
            ChecklistItem(getString(R.string.nautical_chk_item_rigging)),
            ChecklistItem(getString(R.string.nautical_chk_item_steering)),
            ChecklistItem(getString(R.string.nautical_chk_item_epirb))
        )
        heavyWeatherItems = mutableListOf(
            ChecklistItem(getString(R.string.nautical_chk_item_hatches_dogged)),
            ChecklistItem(getString(R.string.nautical_chk_item_jacklines_rigged)),
            ChecklistItem(getString(R.string.nautical_chk_item_reefing)),
            ChecklistItem(getString(R.string.nautical_chk_item_lash)),
            ChecklistItem(getString(R.string.nautical_chk_item_bilge_alarms)),
            ChecklistItem(getString(R.string.nautical_chk_item_lifejackets))
        )
        watchHandoverItems = mutableListOf(
            ChecklistItem(getString(R.string.nautical_chk_item_baro_trend)),
            ChecklistItem(getString(R.string.nautical_chk_item_cpa_review)),
            ChecklistItem(getString(R.string.nautical_chk_item_nav_battery)),
            ChecklistItem(getString(R.string.nautical_chk_item_logbook)),
            ChecklistItem(getString(R.string.nautical_chk_item_orders))
        )
    }

    private lateinit var adapter: ChecklistAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var txtProgress: TextView
    private lateinit var txtStatus: TextView

    override fun onStart() {
        super.onStart()
        // Dialog windows escape the map activity red filter; apply the shared
        // night treatment (red filter + dimmed brightness) to this sheet.
        NauticalNightWindowHelper.applyToDialog(dialog)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // The selected checklist and every ticked item survive a rotation;
        // a half-completed pre-departure check no longer resets to zero
        // because the screen turned.
        outState.putString(KEY_TYPE, currentType.name)
        if (::preDepartureItems.isInitialized) {
            outState.putBooleanArray(KEY_CHECKED_PRE_DEPARTURE, ChecklistSavedState.encodeChecked(preDepartureItems))
            outState.putBooleanArray(KEY_CHECKED_HEAVY_WEATHER, ChecklistSavedState.encodeChecked(heavyWeatherItems))
            outState.putBooleanArray(KEY_CHECKED_WATCH_HANDOVER, ChecklistSavedState.encodeChecked(watchHandoverItems))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_nautical_checklist, container, false)
        val app = requireActivity().application as OsmandApplication
        initChecklistItems()

        // Restore the checklist that was open (with its tick marks) before
        // any view is wired, so the adapter and toggle group reflect it.
        savedInstanceState?.let { saved ->
            ChecklistType.fromName(saved.getString(KEY_TYPE))?.let { currentType = it }
            ChecklistSavedState.applyChecked(preDepartureItems, saved.getBooleanArray(KEY_CHECKED_PRE_DEPARTURE))
            ChecklistSavedState.applyChecked(heavyWeatherItems, saved.getBooleanArray(KEY_CHECKED_HEAVY_WEATHER))
            ChecklistSavedState.applyChecked(watchHandoverItems, saved.getBooleanArray(KEY_CHECKED_WATCH_HANDOVER))
        }

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

        toggleGroup.check(
            when (currentType) {
                ChecklistType.PRE_DEPARTURE -> R.id.btn_type_predeparture
                ChecklistType.HEAVY_WEATHER -> R.id.btn_type_heavy_weather
                ChecklistType.WATCH_HANDOVER -> R.id.btn_type_watch_handover
            }
        )
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
            val typeStr = getString(
                when (currentType) {
                    ChecklistType.PRE_DEPARTURE -> R.string.nautical_checklist_type_pre_departure
                    ChecklistType.HEAVY_WEATHER -> R.string.nautical_checklist_type_heavy_weather
                    ChecklistType.WATCH_HANDOVER -> R.string.nautical_checklist_type_watch_handover
                }
            )
            val logEntry = getString(R.string.nautical_checklist_logged_fmt, typeStr, checked, total, timeStr)

            NauticalPlugin.hudManager?.get()?.showBanner(logEntry, 8000L, isWarning = false, priority = 3)
            app.showToastMessage(logEntry)
            dismiss()
        }

        updateProgress()

        val isNightVision = NauticalPlugin.isNightVision(app)
        if (isNightVision) {
            view.setBackgroundColor(ContextCompat.getColor(view.context, R.color.nautical_night_vision_bg))
            view.findViewById<TextView>(R.id.txt_checklist_title)?.setTextColor(ContextCompat.getColor(view.context, R.color.nautical_night_vision_primary))
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
        txtProgress.text = getString(R.string.nautical_checklist_progress_fmt, checked, total, pct)
        if (pct == 100) {
            txtStatus.text = getString(R.string.nautical_checklist_all_verified)
            txtStatus.setTextColor(ContextCompat.getColor(txtStatus.context, R.color.nautical_status_green))
        } else {
            txtStatus.text = getString(R.string.nautical_checklist_incomplete)
            txtStatus.setTextColor(ContextCompat.getColor(txtStatus.context, R.color.nautical_status_red))
        }
    }

    companion object {
        private const val TAG = "NauticalChecklistDialogFragment"
        private const val KEY_TYPE = "checklist_type"
        private const val KEY_CHECKED_PRE_DEPARTURE = "checklist_checked_pre_departure"
        private const val KEY_CHECKED_HEAVY_WEATHER = "checklist_checked_heavy_weather"
        private const val KEY_CHECKED_WATCH_HANDOVER = "checklist_checked_watch_handover"

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
