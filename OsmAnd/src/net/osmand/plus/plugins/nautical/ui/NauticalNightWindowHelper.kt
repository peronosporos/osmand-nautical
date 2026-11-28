package net.osmand.plus.plugins.nautical.ui

import android.app.Dialog
import android.graphics.Paint
import android.view.View
import android.view.Window
import android.view.WindowManager
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin

/**
 * Single place that applies the nautical night-vision treatment to windows
 * outside the map activity (bottom sheets, dialogs, the cockpit activity).
 *
 * Bottom sheets and dialogs live in their own window and therefore escape
 * the red color filter that NauticalUiOverlayManager applies to the map
 * activity decor view. Without this helper, opening any sheet at night
 * would blast bright white UI over dark-adapted eyes and reset screen
 * brightness.
 *
 * Uses the same red filter as the map layers and the same 0.2 screen
 * brightness as NauticalUiOverlayManager, so every nautical surface looks
 * identical in night vision mode.
 */
object NauticalNightWindowHelper {

    /**
     * Screen brightness used while night vision is active. Must match the
     * value applied to the map activity window by NauticalUiOverlayManager.
     */
    const val NIGHT_SCREEN_BRIGHTNESS = 0.2f

    /**
     * Applies or clears the night treatment on a dialog window.
     * Idempotent: safe to call in onStart and again after an in-dialog
     * night vision toggle.
     */
    fun applyToDialog(dialog: Dialog?) {
        val window = dialog?.window ?: return
        val app = dialog.context.applicationContext as? OsmandApplication ?: return
        applyToWindow(window, app)
    }

    /**
     * Applies or clears the night treatment on an arbitrary window.
     * Used for dialog windows and for the cockpit activity, which runs
     * outside the map activity window.
     */
    fun applyToWindow(window: Window?, app: OsmandApplication?) {
        if (window == null || app == null) return
        val decorView = window.decorView ?: return
        if (NauticalPlugin.isNightVision(app)) {
            val paint = Paint().apply {
                colorFilter = NauticalPlugin.NIGHT_VISION_FILTER
            }
            decorView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
            val params = window.attributes
            params.screenBrightness = NIGHT_SCREEN_BRIGHTNESS
            window.attributes = params
        } else {
            decorView.setLayerType(View.LAYER_TYPE_NONE, null)
            val params = window.attributes
            params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = params
        }
    }
}
