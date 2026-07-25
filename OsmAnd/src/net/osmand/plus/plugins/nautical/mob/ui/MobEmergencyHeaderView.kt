package net.osmand.plus.plugins.nautical.mob.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.mob.viewmodel.MobUiState
import net.osmand.plus.plugins.nautical.mob.viewmodel.MobViewModel
import java.util.concurrent.TimeUnit

/**
 * High-visibility emergency header for MOB scenarios.
 * Displays real-time metrics and safety-interlocked controls.
 */
class MobEmergencyHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val distanceView: TextView
    private val bearingView: TextView
    private val etaView: TextView
    private val silenceButton: Button
    private val cancelMobButton: Button

    private var viewModel: MobViewModel? = null
    private val handler = Handler(Looper.getMainLooper())
    private var cancelRunnable: Runnable? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.mob_emergency_hud, this, true)
        
        distanceView = findViewById(R.id.mob_distance)
        bearingView = findViewById(R.id.mob_bearing)
        etaView = findViewById(R.id.mob_eta)
        silenceButton = findViewById(R.id.btn_silence)
        cancelMobButton = findViewById(R.id.btn_cancel_mob)

        setupListeners()
        isVisible = false
    }

    fun setViewModel(vm: MobViewModel) {
        this.viewModel = vm
    }

    private fun setupListeners() {
        silenceButton.setOnClickListener {
            viewModel?.silenceAlarm()
        }

        // Long-press logic for CANCEL MOB (2 seconds)
        cancelMobButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    cancelRunnable = Runnable {
                        viewModel?.clearMob()
                    }
                    handler.postDelayed(cancelRunnable!!, 2000)
                    v.isPressed = true
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelRunnable?.let { handler.removeCallbacks(it) }
                    v.isPressed = false
                    true
                }
                else -> false
            }
        }
    }

    fun updateState(state: MobUiState) {
        isVisible = state.isMobActive || state.state == net.osmand.plus.plugins.nautical.mob.engine.MobState.RESOLVED
        
        if (!isVisible) return

        state.distanceMeters?.let {
            distanceView.text = context.getString(R.string.mob_distance_label, it)
        }
        
        state.bearingDegrees?.let {
            bearingView.text = context.getString(R.string.mob_bearing_label, it)
        }
        
        state.etaSeconds?.let {
            if (it.isInfinite()) {
                etaView.text = "--:--"
            } else {
                val mins = TimeUnit.SECONDS.toMinutes(it.toLong())
                val secs = it.toLong() % 60
                etaView.text = String.format("%02d:%02d", mins, secs)
            }
        }

        // If RESOLVED, change text or style?
        if (state.state == net.osmand.plus.plugins.nautical.mob.engine.MobState.RESOLVED) {
            cancelMobButton.text = context.getString(R.string.nautical_ack_clear)
            silenceButton.isVisible = false
        } else {
            cancelMobButton.setText(R.string.mob_btn_cancel)
            silenceButton.isVisible = true
        }
    }
}
