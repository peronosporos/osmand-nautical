package net.osmand.plus.plugins.nautical

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.utils.AndroidUtils
import net.osmand.plus.plugins.nautical.ui.INauticalHudHeader

class NauticalHudManager(private val activity: MapActivity) {
    private var nauticalHudContainer: LinearLayout? = null
    private var topBarListener: View.OnLayoutChangeListener? = null
    private var topWidgetsListener: View.OnLayoutChangeListener? = null
    private val wearOsManager = WearOsNauticalManager(activity)

    fun getOrCreateContainer(): ViewGroup? {
        if (nauticalHudContainer == null || nauticalHudContainer?.context != activity) {
            nauticalHudContainer?.let { hud ->
                (hud.parent as? ViewGroup)?.removeView(hud)
            }
            val mapHudLayout = activity.findViewById<ViewGroup>(R.id.map_hud_layout) ?: return null
            nauticalHudContainer = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.TOP
                }
                
                val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    updateLayout()
                }
                val topBar = activity.findViewById<View>(R.id.widget_top_bar)
                val topWidgets = activity.findViewById<View>(R.id.top_widgets_panel)
                
                topBar?.addOnLayoutChangeListener(listener)
                topWidgets?.addOnLayoutChangeListener(listener)
                
                topBarListener = listener
                topWidgetsListener = listener
            }
            mapHudLayout.addView(nauticalHudContainer)
        }
        return nauticalHudContainer
    }

    fun addHeader(header: View, priority: Int = 100) {
        val container = getOrCreateContainer() ?: return
        if (priority == 0) {
            container.addView(header, 0)
        } else {
            container.addView(header)
        }
        updateLayout()
    }

    fun removeHeader(header: View?) {
        header?.let {
            nauticalHudContainer?.removeView(it)
            updateLayout()
        }
    }

    fun removeAllHeaders() {
        nauticalHudContainer?.removeAllViews()
        updateLayout()
    }

    fun showBanner(text: String, durationMs: Long, label: String? = null, isWarning: Boolean = false, onConfirm: (() -> Unit)? = null) {
        val banner = net.osmand.plus.plugins.nautical.ui.NauticalHudBannerView(activity).apply {
            setMessage(text, isWarning)
            if (onConfirm != null) {
                setConfirmAction(label ?: activity.getString(R.string.shared_string_ok), onConfirm)
            }
            onDismiss = {
                removeHeader(this)
            }
        }
        addHeader(banner, priority = 0) // Show at the top
        banner.show(durationMs)
    }

    fun hideBanner() {
        val container = nauticalHudContainer ?: return
        for (i in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(i)
            if (child is net.osmand.plus.plugins.nautical.ui.NauticalHudBannerView) {
                removeHeader(child)
            }
        }
    }

    fun updateLayout() {
        val container = nauticalHudContainer ?: return
        val mapHudLayout = container.parent as? ViewGroup ?: return
        
        val isWatch = wearOsManager.isWatchMode()
        
        val loc = IntArray(2)
        mapHudLayout.getLocationOnScreen(loc)
        val parentTop = loc[1]
        
        var topOffset = 0
        if (!isWatch) {
            // Robust Spatial Arbitration: Account for standard widgets using screen coordinates
            val topBar = activity.findViewById<View>(R.id.widget_top_bar)
            if (topBar?.isVisible == true) {
                topBar.getLocationOnScreen(loc)
                topOffset = maxOf(topOffset, loc[1] + topBar.height - parentTop)
            }
            
            val topWidgets = activity.findViewById<View>(R.id.top_widgets_panel)
            if (topWidgets?.isVisible == true) {
                topWidgets.getLocationOnScreen(loc)
                topOffset = maxOf(topOffset, loc[1] + topWidgets.height - parentTop)
            }
            
            // Core controls to avoid obscuring
            val compass = activity.findViewById<View>(R.id.map_compass_button)
            val zoomIn = activity.findViewById<View>(R.id.map_zoom_in_button)
            val zoomOut = activity.findViewById<View>(R.id.map_zoom_out_button)
            
            val coreControls = listOfNotNull(compass, zoomIn, zoomOut)
            var maxCoreBottom = 0
            
            coreControls.forEach { view ->
                if (view.isVisible) {
                    view.getLocationOnScreen(loc)
                    // Only care about controls in the upper half of the screen
                    if (loc[1] < activity.window.decorView.height / 2) {
                        maxCoreBottom = maxOf(maxCoreBottom, loc[1] + view.height - parentTop)
                    }
                }
            }
            
            if (maxCoreBottom > topOffset) {
                topOffset = maxCoreBottom
            }

            // Priority Shift for Emergency Navtex (Type A, D, L)
            var hasEmergency = false
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child.isVisible && (child as? INauticalHudHeader)?.isEmergency() == true) {
                    hasEmergency = true
                    break
                }
            }
            
            if (hasEmergency) {
                topOffset += AndroidUtils.dpToPx(activity, 48f)
            }
            
            if (topOffset > 0) {
                topOffset += AndroidUtils.dpToPx(activity, 4f)
            }
            
            // Respect System Insets
            val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
            val systemBars = insets?.getInsets(WindowInsetsCompat.Type.systemBars())
            if (systemBars != null) {
                topOffset = maxOf(topOffset, systemBars.top - parentTop)
            }
        } else {
            topOffset = AndroidUtils.dpToPx(activity, 16f)
        }

        val params = container.layoutParams as? android.widget.FrameLayout.LayoutParams
        if (params != null && params.topMargin != topOffset) {
            params.topMargin = topOffset
            container.layoutParams = params
        }
        
        // Compact mode: count visible children
        var visibleCount = 0
        for (i in 0 until container.childCount) {
            if (container.getChildAt(i).isVisible) {
                visibleCount++
            }
        }
        
        // If multiple headers are active, use compact styling
        // EXCEPT on Watch where we always want the simplified MFD
        val useCompact = visibleCount > 1 && !isWatch
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is INauticalHudHeader) {
                 child.setCompactMode(useCompact)
            }
        }
    }

    fun setVisible(visible: Boolean) {
        nauticalHudContainer?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun onDestroy() {
        val topBar = activity.findViewById<View>(R.id.widget_top_bar)
        val topWidgets = activity.findViewById<View>(R.id.top_widgets_panel)
        
        topBarListener?.let { topBar?.removeOnLayoutChangeListener(it) }
        topWidgetsListener?.let { topWidgets?.removeOnLayoutChangeListener(it) }
        
        nauticalHudContainer?.let { hud ->
            (hud.parent as? ViewGroup)?.removeView(hud)
        }
        nauticalHudContainer = null
        topBarListener = null
        topWidgetsListener = null
    }
}
