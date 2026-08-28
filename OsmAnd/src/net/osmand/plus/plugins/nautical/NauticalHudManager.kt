package net.osmand.plus.plugins.nautical

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.*
import java.util.concurrent.PriorityBlockingQueue
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.utils.AndroidUtils
import net.osmand.plus.plugins.nautical.ui.INauticalHudHeader

class NauticalHudManager(val activity: MapActivity) {
    private var nauticalHudContainer: LinearLayout? = null
    private var topBarListener: View.OnLayoutChangeListener? = null
    private var topWidgetsListener: View.OnLayoutChangeListener? = null
    private val wearOsManager = NauticalPlugin.getWearOsManager(activity)
    
    // View Caching
    private var cachedTopBar: View? = null
    private var cachedTopWidgets: View? = null
    private var cachedCompass: View? = null
    private var cachedZoomIn: View? = null
    private var cachedZoomOut: View? = null

    private var lastLayoutUpdateTime = 0L

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
                
                cachedTopBar = activity.findViewById(R.id.widget_top_bar)
                cachedTopWidgets = activity.findViewById(R.id.top_widgets_panel)
                
                cachedTopBar?.addOnLayoutChangeListener(listener)
                cachedTopWidgets?.addOnLayoutChangeListener(listener)
                
                topBarListener = listener
                topWidgetsListener = listener
            }
            mapHudLayout.addView(nauticalHudContainer)
        }
        return nauticalHudContainer
    }

    private data class HeaderRecord(val view: View, val priority: Int)
    private val registeredHeaders = mutableListOf<HeaderRecord>()
    private var activeHeader: View? = null

    fun addHeader(header: View, priority: Int = 100) {
        getOrCreateContainer() ?: return
        registeredHeaders.removeAll { it.view == header }
        registeredHeaders.add(HeaderRecord(header, priority))
        registeredHeaders.sortBy { it.priority }
        arbitrateHeaders()
        updateLayout()
    }

    fun removeHeader(header: View?) {
        if (header == null) return
        registeredHeaders.removeAll { it.view == header }
        if (activeHeader == header) {
            nauticalHudContainer?.removeView(header)
            activeHeader = null
        }
        arbitrateHeaders()
        updateLayout()
    }

    fun removeAllHeaders() {
        registeredHeaders.clear()
        activeHeader = null
        nauticalHudContainer?.removeAllViews()
        updateLayout()
    }

    private fun arbitrateHeaders() {
        val container = getOrCreateContainer() ?: return
        val isNight = NauticalPlugin.isNightVision(activity.app)
        
        val highest = registeredHeaders.firstOrNull()?.view
        
        if (activeHeader != highest) {
            activeHeader?.let { container.removeView(it) }
            activeHeader = highest
            if (highest != null) {
                if (highest.parent != null) {
                    (highest.parent as? ViewGroup)?.removeView(highest)
                }
                container.addView(highest, 0)
            }
        }
        
        registeredHeaders.forEach { record ->
            (record.view as? INauticalHudHeader)?.applyNightVision(isNight)
            try {
                val method = record.view.javaClass.getMethod("applyNightVisionTheme", Boolean::class.javaPrimitiveType)
                method.invoke(record.view, isNight)
            } catch (_: Exception) {}
            try {
                val method = record.view.javaClass.getMethod("setNightVision", Boolean::class.javaPrimitiveType)
                method.invoke(record.view, isNight)
            } catch (_: Exception) {}
        }
    }

    private val bannerQueue = PriorityBlockingQueue<BannerRequest>()
    private var isDisplayingBanner = false
    private var currentBannerText: String? = null

    private data class BannerRequest(
        val text: String,
        val durationMs: Long,
        val label: String?,
        val isWarning: Boolean,
        val onConfirm: (() -> Unit)?,
        val secondaryLabel: String?,
        val onSecondaryConfirm: (() -> Unit)?,
        val priority: Int = 100,
        val timestamp: Long = System.currentTimeMillis()
    ) : Comparable<BannerRequest> {
        override fun compareTo(other: BannerRequest): Int {
            // Priority 1: Lower value = higher priority (consistent with addHeader)
            if (this.priority != other.priority) {
                return this.priority.compareTo(other.priority)
            }
            // Priority 2: Warnings/Emergencies first
            if (this.isWarning != other.isWarning) {
                return if (this.isWarning) -1 else 1
            }
            // Priority 3: Older items first within same category
            return this.timestamp.compareTo(other.timestamp)
        }
    }

    fun showBanner(
        text: String,
        durationMs: Long,
        label: String? = null,
        isWarning: Boolean = false,
        onConfirm: (() -> Unit)? = null,
        secondaryLabel: String? = null,
        onSecondaryConfirm: (() -> Unit)? = null,
        priority: Int = 100
    ) {
        // Priority check: if text is same as current or queued, ignore to avoid duplicates and clutter
        if (currentBannerText == text || bannerQueue.any { it.text == text }) return

        val request = BannerRequest(text, durationMs, label, isWarning, onConfirm, secondaryLabel, onSecondaryConfirm, priority)
        bannerQueue.add(request)
        activity.runOnUiThread { processNextBanner() }
    }

    private fun processNextBanner() {
        if (isDisplayingBanner || bannerQueue.isEmpty()) return
        val next = bannerQueue.poll() ?: return
        
        getOrCreateContainer() ?: return
        isDisplayingBanner = true
        currentBannerText = next.text
        
        val banner = net.osmand.plus.plugins.nautical.ui.NauticalHudBannerView(activity).apply {
            setMessage(next.text, next.isWarning)
            if (next.onConfirm != null) {
                setConfirmAction(next.label ?: activity.getString(R.string.shared_string_ok), next.onConfirm)
            }
            if (next.onSecondaryConfirm != null) {
                setSecondaryConfirmAction(next.secondaryLabel ?: activity.getString(R.string.shared_string_cancel), next.onSecondaryConfirm)
            }
            onDismiss = {
                removeHeader(this)
                isDisplayingBanner = false
                currentBannerText = null
                processNextBanner()
            }
        }
        addHeader(banner, priority = next.priority)
        banner.show(next.durationMs)
    }

    fun hideBanner() {
        val container = nauticalHudContainer ?: return
        var removed = false
        for (i in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(i)
            if (child is net.osmand.plus.plugins.nautical.ui.NauticalHudBannerView) {
                removeHeader(child)
                removed = true
            }
        }
        if (removed) {
            isDisplayingBanner = false
            currentBannerText = null
            processNextBanner()
        }
    }

    fun updateLayout() {
        val container = nauticalHudContainer ?: return
        
        // Task: Throttling frequent HUD layout updates (max 2Hz)
        val now = System.currentTimeMillis()
        if (now - lastLayoutUpdateTime < 500) return
        lastLayoutUpdateTime = now

        val mapHudLayout = container.parent as? ViewGroup ?: return
        
        val isWatch = wearOsManager.isWatchMode()
        val isRound = wearOsManager.isScreenRound()
        
        // Task: UI Collision Avoidance on Smartwatches
        activity.runOnUiThread {
            if (cachedCompass == null) cachedCompass = activity.findViewById(R.id.map_compass_button)
            if (cachedZoomIn == null) cachedZoomIn = activity.findViewById(R.id.map_zoom_in_button)
            if (cachedZoomOut == null) cachedZoomOut = activity.findViewById(R.id.map_zoom_out_button)
            
            if (isWatch && container.isNotEmpty() && container.getChildAt(0).isVisible) {
                // Hide standard buttons to prevent overlap and touch confusion on tiny screens
                cachedCompass?.visibility = View.GONE
                cachedZoomIn?.visibility = View.GONE
                cachedZoomOut?.visibility = View.GONE
            } else if (isWatch) {
                cachedCompass?.visibility = View.VISIBLE
                cachedZoomIn?.visibility = View.VISIBLE
                cachedZoomOut?.visibility = View.VISIBLE
            }
        }

        val loc = IntArray(2)
        mapHudLayout.getLocationOnScreen(loc)
        val parentTop = loc[1]
        
        var topOffset = 0
        if (!isWatch) {
            // Robust Spatial Arbitration: Account for standard widgets using screen coordinates
            val topBar = cachedTopBar ?: activity.findViewById<View>(R.id.widget_top_bar).also { cachedTopBar = it }
            if (topBar?.isVisible == true) {
                topBar.getLocationOnScreen(loc)
                topOffset = maxOf(topOffset, loc[1] + topBar.height - parentTop)
            }
            
            val topWidgets = cachedTopWidgets ?: activity.findViewById<View>(R.id.top_widgets_panel).also { cachedTopWidgets = it }
            if (topWidgets?.isVisible == true) {
                topWidgets.getLocationOnScreen(loc)
                topOffset = maxOf(topOffset, loc[1] + topWidgets.height - parentTop)
            }
            
            // Core controls to avoid obscuring
            val compass = cachedCompass ?: activity.findViewById<View>(R.id.map_compass_button).also { cachedCompass = it }
            val zoomIn = cachedZoomIn ?: activity.findViewById<View>(R.id.map_zoom_in_button).also { cachedZoomIn = it }
            val zoomOut = cachedZoomOut ?: activity.findViewById<View>(R.id.map_zoom_out_button).also { cachedZoomOut = it }
            
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
            // Center-weighted layout for round watches to avoid clipping
            val screenHeight = activity.resources.displayMetrics.heightPixels
            topOffset = if (isRound) {
                (screenHeight * 0.2f).toInt() // Position at 20% down for round bands
            } else {
                AndroidUtils.dpToPx(activity, 16f)
            }
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
        registeredHeaders.clear()
        activeHeader = null
        nauticalHudContainer = null
        topBarListener = null
        topWidgetsListener = null
    }
}
