package net.osmand.plus.plugins.nautical.ui.hud

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.ui.INauticalHudHeader
import java.util.Locale
import kotlin.math.abs

enum class CockpitHudMode {
    PASSAGE_SAIL,
    MOTORING_HARBOR,
    ANCHOR_MOORED
}

class NauticalCockpitHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val cardRoot: MaterialCardView
    private val toggleHudMode: MaterialButtonToggleGroup
    private val btnModePassage: MaterialButton
    private val btnModeMotoring: MaterialButton
    private val btnModeAnchor: MaterialButton
    private val chipVhfChannel: TextView
    private val btnLockMode: MaterialButton
    private val btnRemoteQr: MaterialButton

    private val layoutModePassage: LinearLayout
    private val layoutModeMotoring: LinearLayout
    private val layoutModeAnchor: LinearLayout

    // Mode 1 Views
    private val txtPassageSogVal: TextView
    private val txtPassageCogLbl: TextView
    private val txtPassageTwaVal: TextView
    private val txtPassageTwsLbl: TextView
    private val txtPassageVmgVal: TextView
    private val txtPassageXteVal: TextView
    private val txtPassageLaylineLbl: TextView

    // Mode 2 Views
    private val txtMotorSogVal: TextView
    private val txtMotorRudderVal: TextView
    private val txtMotorDepthVal: TextView
    private val txtMotorTideLbl: TextView
    private val txtMotorHazardVal: TextView

    // Mode 3 Views
    private val txtAnchorDepthVal: TextView
    private val txtAnchorRodeVal: TextView
    private val txtAnchorScopeLbl: TextView
    private val txtAnchorRadiusVal: TextView
    private val txtAnchorBatteryVal: TextView
    private val txtAnchorChafeLbl: TextView

    private var remoteServer: net.osmand.plus.plugins.nautical.server.NauticalRemoteServer? = null

    var currentMode: CockpitHudMode = CockpitHudMode.PASSAGE_SAIL
        private set

    var isModeLocked: Boolean = false
        private set

    private var gestureDetector: GestureDetector? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.nautical_cockpit_hud_strip, this, true)

        cardRoot = findViewById(R.id.card_cockpit_hud)
        toggleHudMode = findViewById(R.id.toggle_hud_mode)
        btnModePassage = findViewById(R.id.btn_mode_passage)
        btnModeMotoring = findViewById(R.id.btn_mode_motoring)
        btnModeAnchor = findViewById(R.id.btn_mode_anchor)
        chipVhfChannel = findViewById(R.id.chip_vhf_channel)
        btnLockMode = findViewById(R.id.btn_lock_mode)
        btnRemoteQr = findViewById(R.id.btn_remote_qr)

        layoutModePassage = findViewById(R.id.layout_mode_passage)
        layoutModeMotoring = findViewById(R.id.layout_mode_motoring)
        layoutModeAnchor = findViewById(R.id.layout_mode_anchor)

        txtPassageSogVal = findViewById(R.id.txt_passage_sog_val)
        txtPassageCogLbl = findViewById(R.id.txt_passage_cog_lbl)
        txtPassageTwaVal = findViewById(R.id.txt_passage_twa_val)
        txtPassageTwsLbl = findViewById(R.id.txt_passage_tws_lbl)
        txtPassageVmgVal = findViewById(R.id.txt_passage_vmg_val)
        txtPassageXteVal = findViewById(R.id.txt_passage_xte_val)
        txtPassageLaylineLbl = findViewById(R.id.txt_passage_layline_lbl)

        txtMotorSogVal = findViewById(R.id.txt_motor_sog_val)
        txtMotorRudderVal = findViewById(R.id.txt_motor_rudder_val)
        txtMotorDepthVal = findViewById(R.id.txt_motor_depth_val)
        txtMotorTideLbl = findViewById(R.id.txt_motor_tide_lbl)
        txtMotorHazardVal = findViewById(R.id.txt_motor_hazard_val)

        txtAnchorDepthVal = findViewById(R.id.txt_anchor_depth_val)
        txtAnchorRodeVal = findViewById(R.id.txt_anchor_rode_val)
        txtAnchorScopeLbl = findViewById(R.id.txt_anchor_scope_lbl)
        txtAnchorRadiusVal = findViewById(R.id.txt_anchor_radius_val)
        txtAnchorBatteryVal = findViewById(R.id.txt_anchor_battery_val)
        txtAnchorChafeLbl = findViewById(R.id.txt_anchor_chafe_lbl)

        setupListeners()
        updateModeLayout(CockpitHudMode.PASSAGE_SAIL)
    }

    private fun setupListeners() {
        toggleHudMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newMode = when (checkedId) {
                    R.id.btn_mode_motoring -> CockpitHudMode.MOTORING_HARBOR
                    R.id.btn_mode_anchor -> CockpitHudMode.ANCHOR_MOORED
                    else -> CockpitHudMode.PASSAGE_SAIL
                }
                updateModeLayout(newMode)
            }
        }

        btnLockMode.setOnClickListener {
            isModeLocked = !isModeLocked
            updateLockIcon()
        }

        btnRemoteQr.setOnClickListener {
            val app = context.applicationContext as? OsmandApplication
            if (app != null) {
                if (remoteServer == null) {
                    remoteServer = net.osmand.plus.plugins.nautical.server.NauticalRemoteServer(app).apply { start() }
                }
                val url = remoteServer?.getServerUrl(context) ?: "http://127.0.0.1:8080"
                NauticalPlugin.hudManager?.get()?.showBanner("REMOTE BERTH MIRROR: $url (Connect crew browser on Wi-Fi)", 12000L, isWarning = false, priority = 3)
            }
        }

        val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (abs(diffX) > abs(diffY) && abs(diffX) > 100 && abs(velocityX) > 100) {
                    if (diffX < 0) {
                        // Swipe Left: Next mode
                        val next = when (currentMode) {
                            CockpitHudMode.PASSAGE_SAIL -> CockpitHudMode.MOTORING_HARBOR
                            CockpitHudMode.MOTORING_HARBOR -> CockpitHudMode.ANCHOR_MOORED
                            CockpitHudMode.ANCHOR_MOORED -> CockpitHudMode.PASSAGE_SAIL
                        }
                        setHudMode(next, force = true)
                    } else {
                        // Swipe Right: Prev mode
                        val prev = when (currentMode) {
                            CockpitHudMode.PASSAGE_SAIL -> CockpitHudMode.ANCHOR_MOORED
                            CockpitHudMode.MOTORING_HARBOR -> CockpitHudMode.PASSAGE_SAIL
                            CockpitHudMode.ANCHOR_MOORED -> CockpitHudMode.MOTORING_HARBOR
                        }
                        setHudMode(prev, force = true)
                    }
                    return true
                }
                return false
            }
        }

        gestureDetector = GestureDetector(context, gestureListener)
        setOnTouchListener { _, event ->
            gestureDetector?.onTouchEvent(event) ?: false
        }
    }

    private fun updateLockIcon() {
        if (isModeLocked) {
            btnLockMode.setIconResource(R.drawable.ic_action_lock)
            btnLockMode.setIconTintResource(R.color.color_alert)
        } else {
            btnLockMode.setIconResource(R.drawable.ic_action_lock_open)
            btnLockMode.setIconTintResource(R.color.icon_color_default_light)
        }
    }

    fun setHudMode(mode: CockpitHudMode, force: Boolean = false) {
        if (isModeLocked && !force) return
        if (currentMode != mode) {
            currentMode = mode
            val buttonId = when (mode) {
                CockpitHudMode.PASSAGE_SAIL -> R.id.btn_mode_passage
                CockpitHudMode.MOTORING_HARBOR -> R.id.btn_mode_motoring
                CockpitHudMode.ANCHOR_MOORED -> R.id.btn_mode_anchor
            }
            if (toggleHudMode.checkedButtonId != buttonId) {
                toggleHudMode.check(buttonId)
            }
            updateModeLayout(mode)
        }
    }

    private fun updateModeLayout(mode: CockpitHudMode) {
        layoutModePassage.visibility = if (mode == CockpitHudMode.PASSAGE_SAIL) View.VISIBLE else View.GONE
        layoutModeMotoring.visibility = if (mode == CockpitHudMode.MOTORING_HARBOR) View.VISIBLE else View.GONE
        layoutModeAnchor.visibility = if (mode == CockpitHudMode.ANCHOR_MOORED) View.VISIBLE else View.GONE
    }

    fun setVhfWorkingChannel(channel: String?) {
        if (!channel.isNullOrEmpty()) {
            chipVhfChannel.text = "VHF $channel"
            chipVhfChannel.visibility = View.VISIBLE
        } else {
            chipVhfChannel.visibility = View.GONE
        }
    }

    fun updateTelemetry(state: MarineState, app: OsmandApplication) {
        val sogKn = (state.speedOverGround ?: 0.0) * 1.94384
        val cogDeg = state.courseOverGroundTrue?.let { Math.toDegrees(it) } ?: 0.0
        val twaDeg = state.trueWindAngle?.let { Math.toDegrees(it) } ?: state.windDirectionApparent?.let { Math.toDegrees(it) } ?: 0.0
        val twsKn = (state.windSpeedTrue ?: state.windSpeedApparent ?: 0.0) * 1.94384
        val depthM = state.depthBelowKeel ?: state.depthBelowTransducer ?: 0.0
        val rudderDeg = state.rudderAngle?.let { Math.toDegrees(it) } ?: 0.0

        // Mode 1 Updates
        txtPassageSogVal.text = String.format(Locale.US, "%.1f kn", sogKn)
        txtPassageCogLbl.text = String.format(Locale.US, "COG: %03.0f°", cogDeg)
        txtPassageTwaVal.text = String.format(Locale.US, "TWA %03.0f°", twaDeg)
        txtPassageTwsLbl.text = String.format(Locale.US, "TWS: %.1f kn", twsKn)

        val polarPct = state.polarTargetSpeed?.let { tgt ->
            if (tgt > 0.1) (state.speedOverGround ?: 0.0) / tgt * 100.0 else 100.0
        } ?: 100.0
        txtPassageVmgVal.text = String.format(Locale.US, "%.0f%%", polarPct)

        val xteM = state.crossTrackErrorMeters ?: 0.0
        txtPassageXteVal.text = String.format(Locale.US, "XTE %.2f", abs(xteM) / 1852.0)

        // Mode 2 Updates
        txtMotorSogVal.text = String.format(Locale.US, "%.1f kn", sogKn)
        txtMotorRudderVal.text = String.format(Locale.US, "%s%.0f°", if (rudderDeg < 0) "P " else if (rudderDeg > 0) "S " else "", abs(rudderDeg))
        txtMotorDepthVal.text = String.format(Locale.US, "%.1f m", depthM)

        val tideH = state.tideHeight ?: app.settings.NAUTICAL_ANCHOR_TIDE_RISE.get().toDouble()
        txtMotorTideLbl.text = String.format(Locale.US, "TIDE: %.1fm", tideH)

        val isNight = NauticalPlugin.isNightVision(app)
        val hasHazard = state.forwardHazards.isNotEmpty()
        txtMotorHazardVal.text = if (hasHazard) "HAZARD!" else "CLEAR"
        txtMotorHazardVal.setTextColor(
            if (hasHazard) 0xFFFF1744.toInt()
            else if (isNight) 0xFFFF8A80.toInt()
            else 0xFF43A047.toInt()
        )

        // Mode 3 Updates
        txtAnchorDepthVal.text = String.format(Locale.US, "%.1f m", depthM)
        val rodeM = app.settings.NAUTICAL_ANCHOR_RODE_DEPLOYED_METERS.get()
        val scope = if (depthM > 0.5) rodeM / depthM else 5.0
        txtAnchorRodeVal.text = String.format(Locale.US, "%.0f m", rodeM)
        txtAnchorScopeLbl.text = String.format(Locale.US, "SCOPE: %.1f:1", scope)

        val swingRadius = app.settings.NAUTICAL_ANCHOR_RADIUS.get()
        txtAnchorRadiusVal.text = String.format(Locale.US, "%.0f m", swingRadius)

        val battSoc = state.batteries.values.firstOrNull()?.stateOfCharge ?: 0.85
        txtAnchorBatteryVal.text = String.format(Locale.US, "%.0f%%", battSoc * 100.0)
        txtAnchorBatteryVal.setTextColor(if (isNight) 0xFFFF8A80.toInt() else 0xFF43A047.toInt())
    }

    override fun setCompactMode(enabled: Boolean) {
        val p = if (enabled) 4 else 8
        cardRoot.setContentPadding(p, p, p, p)
    }

    override fun applyNightVision(enabled: Boolean) {
        if (enabled) {
            cardRoot.setCardBackgroundColor(0xEE120000.toInt())
            cardRoot.strokeColor = 0xFF8B0000.toInt()
            txtPassageSogVal.setTextColor(0xFFFF1744.toInt())
            txtPassageTwaVal.setTextColor(0xFFFF1744.toInt())
            txtPassageVmgVal.setTextColor(0xFFFF8A80.toInt())
            txtPassageXteVal.setTextColor(0xFFFF1744.toInt())
            txtMotorSogVal.setTextColor(0xFFFF1744.toInt())
            txtMotorRudderVal.setTextColor(0xFFFF1744.toInt())
            txtMotorDepthVal.setTextColor(0xFFFF1744.toInt())
            txtAnchorDepthVal.setTextColor(0xFFFF1744.toInt())
            txtAnchorRodeVal.setTextColor(0xFFFF1744.toInt())
            txtAnchorRadiusVal.setTextColor(0xFFFF1744.toInt())
            txtAnchorBatteryVal.setTextColor(0xFFFF8A80.toInt())
            btnModePassage.setTextColor(0xFFFF1744.toInt())
            btnModeMotoring.setTextColor(0xFFFF1744.toInt())
            btnModeAnchor.setTextColor(0xFFFF1744.toInt())
        } else {
            val primaryColor = ContextCompat.getColor(context, R.color.text_color_primary_light)
            cardRoot.setCardBackgroundColor(ContextCompat.getColor(context, R.color.bg_color_light))
            cardRoot.strokeColor = ContextCompat.getColor(context, R.color.divider_color_light)
            txtPassageSogVal.setTextColor(primaryColor)
            txtPassageTwaVal.setTextColor(ContextCompat.getColor(context, R.color.active_color_primary_light))
            txtPassageVmgVal.setTextColor(primaryColor)
            txtPassageXteVal.setTextColor(primaryColor)
            txtMotorSogVal.setTextColor(primaryColor)
            txtMotorRudderVal.setTextColor(ContextCompat.getColor(context, R.color.active_color_primary_light))
            txtMotorDepthVal.setTextColor(primaryColor)
            txtAnchorDepthVal.setTextColor(primaryColor)
            txtAnchorRodeVal.setTextColor(ContextCompat.getColor(context, R.color.active_color_primary_light))
            txtAnchorRadiusVal.setTextColor(primaryColor)
            txtAnchorBatteryVal.setTextColor(0xFF43A047.toInt())
            btnModePassage.setTextColor(primaryColor)
            btnModeMotoring.setTextColor(primaryColor)
            btnModeAnchor.setTextColor(primaryColor)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        remoteServer?.stop()
        remoteServer = null
    }
}
