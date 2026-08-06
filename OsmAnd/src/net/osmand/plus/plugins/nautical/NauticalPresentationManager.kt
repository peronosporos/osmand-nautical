package net.osmand.plus.plugins.nautical

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.ui.HeadingArcView
import net.osmand.plus.plugins.nautical.ui.TacticalHudView

class NauticalPresentationManager(private val app: OsmandApplication) : DisplayManager.DisplayListener {
    private val log = PlatformUtil.getLog(NauticalPresentationManager::class.java)
    private var presentation: NauticalPresentation? = null
    private var lastState: MarineState? = null
    private var isNightMode = false

    private val displayManager: DisplayManager by lazy {
        app.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    fun onResume(activity: MapActivity) {
        displayManager.registerDisplayListener(this, null)
        updatePresentation(activity)
    }

    fun onPause() {
        displayManager.unregisterDisplayListener(this)
        dismissPresentation()
    }

    fun updateState(state: MarineState) {
        lastState = state
        presentation?.updateState(state)
    }

    fun setNightMode(enabled: Boolean) {
        isNightMode = enabled
        presentation?.setNightMode(enabled)
    }

    private fun updatePresentation(activity: MapActivity) {
        val displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        if (displays.isNotEmpty()) {
            val display = displays[0]
            if (presentation?.display?.displayId != display.displayId) {
                dismissPresentation()
                try {
                    val p = NauticalPresentation(activity, display)
                    p.show()
                    presentation = p
                    lastState?.let { p.updateState(it) }
                    p.setNightMode(isNightMode)
                    log.info("Nautical: MFD Presentation started on display ${display.displayId}")
                } catch (e: WindowManager.InvalidDisplayException) {
                    log.error("Nautical: Failed to start presentation - invalid display", e)
                } catch (e: Exception) {
                    log.error("Nautical: Unexpected error starting presentation", e)
                }
            }
        } else {
            dismissPresentation()
        }
    }

    private fun dismissPresentation() {
        try {
            presentation?.dismiss()
        } catch (e: Exception) {
            log.error("Nautical: Error dismissing presentation", e)
        }
        presentation = null
    }

    override fun onDisplayAdded(displayId: Int) {
        app.osmandMap?.mapView?.mapActivity?.let { updatePresentation(it) }
    }

    override fun onDisplayRemoved(displayId: Int) {
        if (presentation?.display?.displayId == displayId) {
            log.info("Nautical: External display $displayId removed. Cleaning up presentation.")
            dismissPresentation()
        }
    }

    override fun onDisplayChanged(displayId: Int) {
        // Handle resolution changes if needed, but Presentation usually handles it via its own context
    }

    private class NauticalPresentation(outerContext: Context, display: Display) :
        Presentation(outerContext, display) {

        private var tacticalHud: TacticalHudView? = null
        private var headingArc: HeadingArcView? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.nautical_mfd_presentation)
            
            tacticalHud = findViewById(R.id.tactical_hud)
            headingArc = findViewById(R.id.heading_arc)
            
            headingArc?.currentMode = "AUTO"
        }

        fun updateState(state: MarineState) {
            tacticalHud?.updateState(state)
            headingArc?.actualHeading = state.headingTrue?.let { Math.toDegrees(it).toInt() }
            headingArc?.targetHeading = state.targetHeading?.let { Math.toDegrees(it).toInt() } ?: 0
            headingArc?.windAngleApparent = state.windDirectionApparent?.let { Math.toDegrees(it).toInt() }
            headingArc?.targetWindAngleApparent = state.targetWindAngleApparent?.let { Math.toDegrees(it).toInt() }
            headingArc?.currentMode = if (state.autopilotState.lowercase() == "wind") "WIND" else "AUTO"
        }

        fun setNightMode(enabled: Boolean) {
            headingArc?.setNightMode(enabled)
            // TacticalHudView can also handle night mode if it uses theme attributes correctly,
            // but we can force it if needed.
        }
    }
}
