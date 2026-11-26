package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.engine.MarineState

class WatchScheduleHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val txtWatch: TextView
    private val txtNext: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.nautical_watch_schedule_hud, this, true)
        txtWatch = findViewById(R.id.txt_current_watch)
        txtNext = findViewById(R.id.txt_next_change)
        orientation = VERTICAL
    }

    fun updateState(state: MarineState) {
        // Assuming path communication.crew.watch.current and communication.crew.watch.nextChange
        val na = context.getString(R.string.nautical_not_available)
        // No watch data yet means exactly that: show n/a rather than
        // inventing an "OFF" status that no data source ever reported.
        val currentWatch = state.pathMeta["communication.crew.watch.current"]?.get("value")?.toString() ?: na
        val nextChangeStr = state.pathMeta["communication.crew.watch.nextChange"]?.get("value")?.toString() ?: na
        
        txtWatch.text = context.getString(R.string.nautical_watch_label, currentWatch)
        txtNext.text = context.getString(R.string.nautical_next_change_label, nextChangeStr)
    }
}
