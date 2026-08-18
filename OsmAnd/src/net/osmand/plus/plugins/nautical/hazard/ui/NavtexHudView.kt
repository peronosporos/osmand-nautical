package net.osmand.plus.plugins.nautical.hazard.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import kotlinx.coroutines.*
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexMessage
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexSubject
import net.osmand.plus.plugins.nautical.hazard.viewmodel.NavtexUiState
import net.osmand.plus.plugins.nautical.ui.INauticalHudHeader
import net.osmand.plus.utils.AndroidUtils
import kotlin.time.Duration.Companion.milliseconds

class NavtexHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private var messages: List<NavtexMessage> = emptyList()
    private var currentIndex = 0
    private var cycleJob: Job? = null
    private val viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val titleView: TextView
    private val badgeView: TextView
    private var isEmergencyState = false
    private var isHighContrastMode = false

    init {
        LayoutInflater.from(context).inflate(R.layout.navtex_hud_ticker, this, true)
        titleView = findViewById(R.id.navtex_ticker_text)
        badgeView = findViewById(R.id.navtex_ticker_badge)
        isVisible = false
        
        setOnClickListener {
            val activity = context as? MapActivity
            activity?.supportFragmentManager?.beginTransaction()
                ?.add(android.R.id.content, NavtexListFragment(), NavtexListFragment.TAG)
                ?.addToBackStack(NavtexListFragment.TAG)
                ?.commit()
        }

        viewScope.launch {
            net.osmand.plus.plugins.nautical.NauticalEventBus.events.collect { event ->
                when (event) {
                    is net.osmand.plus.plugins.nautical.NauticalEvent.MobStateChanged -> {
                        setHighContrastMode(event.active)
                    }
                    is net.osmand.plus.plugins.nautical.NauticalEvent.AlertContrastRequest -> {
                        setHighContrastMode(event.highContrast)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun setHighContrastMode(enabled: Boolean) {
        if (isHighContrastMode == enabled) return
        isHighContrastMode = enabled
        updateDisplay()
        if (enabled) {
            startPulsing()
        } else if (!isEmergencyState) {
            stopPulsing()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopPulsing()
        viewScope.cancel()
    }

    override fun setCompactMode(enabled: Boolean) {
        val p = if (enabled) 6f else 10f
        val px = AndroidUtils.dpToPx(context, p)
        setPadding(px, px, px, px)
        titleView.textSize = if (enabled) 12f else 14f
    }

    override fun isEmergency(): Boolean = isEmergencyState

    fun updateState(state: NavtexUiState) {
        val urgent = state.messages.filter { it.isUrgent }.sortedBy { getPriority(it.subject) }
        
        val newEmergency = urgent.any { 
            it.subject == NavtexSubject.SEARCH_AND_RESCUE || 
            it.subject == NavtexSubject.NAVTEX_WARNING || 
            it.subject == NavtexSubject.NAVIGATIONAL_WARNING_L 
        }

        if (newEmergency != isEmergencyState) {
            isEmergencyState = newEmergency
            if (isEmergencyState) startPulsing() else stopPulsing()
        }

        if (urgent != messages) {
            messages = urgent
            currentIndex = 0
            restartTicker()
            updateDisplay()
        }
        isVisible = messages.isNotEmpty()
    }

    private fun startPulsing() {
        val container = findViewById<android.view.View>(R.id.navtex_ticker_container) ?: return
        container.clearAnimation()
        val anim = android.view.animation.AlphaAnimation(1.0f, 0.4f).apply {
            duration = 800
            repeatMode = android.view.animation.Animation.REVERSE
            repeatCount = android.view.animation.Animation.INFINITE
        }
        container.startAnimation(anim)
    }

    private fun stopPulsing() {
        val container = findViewById<android.view.View>(R.id.navtex_ticker_container) ?: return
        container.clearAnimation()
    }

    private fun restartTicker() {
        cycleJob?.cancel()
        if (messages.size > 1) {
            cycleJob = viewScope.launch {
                while (isActive) {
                    delay(5000.milliseconds)
                    if (messages.isNotEmpty()) {
                        currentIndex = (currentIndex + 1) % messages.size
                        updateDisplay()
                    }
                }
            }
        }
    }

    private fun updateDisplay() {
        if (messages.isEmpty()) {
            isVisible = false
            return
        }
        val msg = messages[currentIndex]
        titleView.text = "${msg.subject.name.replace("_", " ")}: ${msg.id}"
        badgeView.text = "[ ${currentIndex + 1} / ${messages.size} ]"
        badgeView.isVisible = messages.size > 1
        
        val color = if (isHighContrastMode) {
            0xFF000000.toInt()
        } else {
            when (msg.subject) {
                NavtexSubject.SEARCH_AND_RESCUE -> 0xFFB71C1C.toInt()
                else -> 0xFFE65100.toInt()
            }
        }
        
        if (isHighContrastMode) {
            titleView.setTextColor(0xFFFF0000.toInt())
        } else {
            titleView.setTextColor(0xFFFFFFFF.toInt())
        }
        
        findViewById<android.view.View?>(R.id.navtex_ticker_container)?.setBackgroundColor(color)
    }

    private fun getPriority(subject: NavtexSubject): Int = when (subject) {
        NavtexSubject.SEARCH_AND_RESCUE -> 0
        NavtexSubject.NAVTEX_WARNING -> 1
        NavtexSubject.NAVIGATIONAL_WARNING_L -> 1
        NavtexSubject.METEOROLOGICAL_WARNING -> 2
        else -> 3
    }
}
