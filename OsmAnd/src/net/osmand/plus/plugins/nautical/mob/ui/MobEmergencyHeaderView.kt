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
import net.osmand.plus.plugins.nautical.ui.INauticalHudHeader
import net.osmand.plus.utils.AndroidUtils
import java.util.concurrent.TimeUnit

/**
 * High-visibility emergency header for MOB scenarios.
 * Displays real-time metrics and safety-interlocked controls.
 */
class MobEmergencyHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val distanceView: TextView
    private val bearingView: TextView
    private val etaView: TextView
    private val silenceButton: Button
    private val cancelMobButton: Button
    private val heaveToButton: Button
    private val motorReturnButton: Button
    private val holdHeadingButton: Button
    private val sarPatternsButton: Button
    private val maneuversButton: Button
    private val mobIcon: android.widget.ImageView

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
        heaveToButton = findViewById(R.id.btn_heave_to)
        motorReturnButton = findViewById(R.id.btn_motor_return)
        holdHeadingButton = findViewById(R.id.btn_hold_heading)
        sarPatternsButton = findViewById(R.id.btn_mob_sar_patterns)
        maneuversButton = findViewById(R.id.btn_mob_maneuvers)
        mobIcon = findViewById(R.id.mob_icon)

        setupListeners()
        isVisible = false
    }

    override fun setCompactMode(enabled: Boolean) {
        etaView.isVisible = !enabled
        val p = if (enabled) 4f else 12f
        val px = AndroidUtils.dpToPx(context, p)
        setPadding(px, px, px, px)
    }

    fun setViewModel(vm: MobViewModel) {
        this.viewModel = vm
    }

    private fun setupListeners() {
        silenceButton.setOnClickListener {
            viewModel?.silenceAlarm()
        }

        heaveToButton.setOnClickListener { viewModel?.requestHeaveTo() }
        motorReturnButton.setOnClickListener { viewModel?.requestMotorReturn() }
        holdHeadingButton.setOnClickListener { viewModel?.requestHoldHeading() }

        sarPatternsButton.setOnClickListener {
            val fm = (context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager
            if (fm != null) {
                net.osmand.plus.plugins.nautical.ui.NauticalSarConfigDialog.newInstance(true)
                    .show(fm, "SarConfig")
            }
        }

        maneuversButton.setOnClickListener {
            showManeuversMenu()
        }

        // Long-press logic for CANCEL MOB (2 seconds) for safety
        // Single-tap allowed if already RESOLVED
        cancelMobButton.setOnTouchListener { v, event ->
            val currentState = viewModel?.uiState?.value?.state
            if (currentState == net.osmand.plus.plugins.nautical.mob.engine.MobState.RESOLVED) {
                if (event.action == MotionEvent.ACTION_UP) {
                    viewModel?.clearMob()
                    v.performClick()
                }
                return@setOnTouchListener true
            }

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

    private fun showManeuversMenu() {
        val options = arrayOf<CharSequence>(
            context.getString(R.string.nautical_anderson_turn),
            context.getString(R.string.nautical_williamson_turn),
            context.getString(R.string.nautical_scharnow_turn)
        )

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(R.string.nautical_sar_maneuvers)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel?.requestAndersonTurn()
                    1 -> viewModel?.requestWilliamsonTurn()
                    2 -> viewModel?.requestScharnowTurn()
                }
            }
            .show()
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
            findViewById<android.view.View>(R.id.mob_tactical_buttons).isVisible = false
        } else {
            cancelMobButton.setText(R.string.mob_btn_cancel)
            silenceButton.isVisible = true
            
            val tacticalContainer = findViewById<android.view.View>(R.id.mob_tactical_buttons)
            tacticalContainer.isVisible = true
            
            motorReturnButton.isEnabled = state.isMotoring
            heaveToButton.isEnabled = !state.isMotoring && state.isUpwind
        }
    }
}
