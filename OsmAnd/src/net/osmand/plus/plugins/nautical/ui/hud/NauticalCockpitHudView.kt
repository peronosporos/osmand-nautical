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
import net.osmand.plus.plugins.nautical.ui.NauticalColorResolver
import net.osmand.plus.plugins.nautical.ui.NauticalSemanticColor
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

    // Persisted across sessions so the strip reopens in the mode the
    // skipper actually uses; auto-switching still works inside a session.
    private fun persistMode(mode: CockpitHudMode) {
        (context.applicationContext as? OsmandApplication)
            ?.settings?.NAUTICAL_COCKPIT_LAST_HUD_MODE?.set(mode.name)
    }

    private fun restorePersistedMode() {
        val app = context.applicationContext as? OsmandApplication ?: return
        val saved = app.settings.NAUTICAL_COCKPIT_LAST_HUD_MODE.get()
        val mode = CockpitHudMode.entries.firstOrNull { it.name == saved }
            ?: CockpitHudMode.PASSAGE_SAIL
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
        restorePersistedMode()
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
                persistMode(newMode)
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
                NauticalPlugin.hudManager?.get()?.showBanner(app.getString(net.osmand.plus.R.string.nautical_banner_remote_berth_mirror, url), 12000L, isWarning = false, priority = 3)
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
            chipVhfChannel.text = chipVhfChannel.context.getString(R.string.nautical_vhf_working_ch_fmt, channel)
            chipVhfChannel.visibility = View.VISIBLE
        } else {
            chipVhfChannel.visibility = View.GONE
        }
    }

    fun updateTelemetry(state: MarineState, app: OsmandApplication) {
        val na = context.getString(R.string.nautical_not_available)
        val knFmt = context.getString(R.string.nautical_hud_speed_kn_fmt)
        val depthFmt = context.getString(R.string.nautical_hud_depth_m_fmt)
        val distFmt = context.getString(R.string.nautical_hud_dist_m_fmt)

        // No fabricated zeroes: an absent sensor shows n/a, never 0.0 that
        // would read as a real measurement.
        val sogKn = state.speedOverGround?.let { it * 1.94384 }
        val sogTxt = sogKn?.let { String.format(Locale.US, knFmt, it) } ?: na
        val cogTxt = state.courseOverGroundTrue?.let {
            String.format(Locale.US, context.getString(R.string.nautical_hud_cog_fmt), Math.toDegrees(it))
        } ?: na
        // TWA only from a real true-wind-angle source: apparent wind
        // direction is a different quantity and must not be dressed up as TWA.
        val twaTxt = state.trueWindAngle?.let {
            String.format(Locale.US, context.getString(R.string.nautical_hud_twa_fmt), Math.toDegrees(it))
        } ?: na
        val twsTxt = state.windSpeedTrue?.let {
            String.format(Locale.US, context.getString(R.string.nautical_hud_tws_kn_fmt), it * 1.94384)
        } ?: na
        val depthM = state.depthBelowKeel ?: state.depthBelowTransducer
        val depthTxt = depthM?.let { String.format(Locale.US, depthFmt, it) } ?: na

        // Mode 1 Updates
        txtPassageSogVal.text = sogTxt
        txtPassageCogLbl.text = cogTxt
        txtPassageTwaVal.text = twaTxt
        txtPassageTwsLbl.text = twsTxt

        // Polar performance percentage needs both a polar target and a
        // measured speed; anything else is honestly n/a.
        val polarPct: Double? = state.polarTargetSpeed?.takeIf { it > 0.1 }
            ?.let { tgt -> sogKn?.let { it / tgt * 100.0 } }
        txtPassageVmgVal.text = polarPct?.let {
            String.format(Locale.US, context.getString(R.string.nautical_hud_polar_pct_fmt), it)
        } ?: na

        val xteTxt = state.xteMeters?.let {
            String.format(Locale.US, context.getString(R.string.nautical_hud_xte_nm_fmt), abs(it) / 1852.0)
        } ?: na
        txtPassageXteVal.text = xteTxt

        // Mode 2 Updates
        txtMotorSogVal.text = sogTxt
        val rudderTxt = state.rudderAngle?.let { rudderDeg ->
            val side = if (rudderDeg < 0) "P " else if (rudderDeg > 0) "S " else ""
            String.format(Locale.US, context.getString(R.string.nautical_hud_rudder_deg_fmt), side, abs(rudderDeg))
        } ?: na
        txtMotorRudderVal.text = rudderTxt
        txtMotorDepthVal.text = depthTxt

        // Tide height only from live tide data: falling back to the anchor
        // rise setting would display a configured number as a measurement.
        txtMotorTideLbl.text = state.tide?.heightNow?.let {
            String.format(Locale.US, context.getString(R.string.nautical_hud_tide_m_fmt), it)
        } ?: na

        val hasHazard = state.forwardHazards.isNotEmpty()
        txtMotorHazardVal.text = if (hasHazard) {
            context.getString(R.string.nautical_hud_hazard_wrn)
        } else {
            context.getString(R.string.nautical_hud_all_clear)
        }
        txtMotorHazardVal.setTextColor(NauticalColorResolver.getColor(context, NauticalSemanticColor.STATUS_ERROR))
        val hazardOkColor = NauticalColorResolver.getColor(context, NauticalSemanticColor.STATUS_OK)
        if (!hasHazard) {
            txtMotorHazardVal.setTextColor(hazardOkColor)
        }

        // Mode 3 Updates
        txtAnchorDepthVal.text = depthTxt
        val rodeM = app.settings.NAUTICAL_ANCHOR_RODE_DEPLOYED_METERS.get().toDouble()
        txtAnchorRodeVal.text = String.format(Locale.US, distFmt, rodeM)
        // Scope is only meaningful with measured depth under the keel and
        // a deployed rode: an invented 5:1 would defeat its whole purpose.
        val scopeTxt = depthM?.takeIf { it > 0.5 }?.let {
            String.format(Locale.US, context.getString(R.string.nautical_hud_scope_fmt), rodeM / it)
        } ?: na
        txtAnchorScopeLbl.text = scopeTxt

        val swingRadius = app.settings.NAUTICAL_ANCHOR_RADIUS.get()
        txtAnchorRadiusVal.text = String.format(Locale.US, distFmt, swingRadius.toDouble())

        // Battery state of charge if the battery bank actually reports one;
        // the old 0.85 default painted a fabricated 85% on the anchor strip.
        val battSoc = state.batteries.values.firstOrNull()?.stateOfCharge
        txtAnchorBatteryVal.text = battSoc?.let {
            String.format(Locale.US, context.getString(R.string.nautical_hud_polar_pct_fmt), it * 100.0)
        } ?: na
        txtAnchorBatteryVal.setTextColor(
            when {
                battSoc == null -> NauticalColorResolver.getColor(context, NauticalSemanticColor.SECONDARY)
                battSoc >= 0.5 -> NauticalColorResolver.getColor(context, NauticalSemanticColor.STATUS_OK)
                battSoc >= 0.25 -> NauticalColorResolver.getColor(context, NauticalSemanticColor.STATUS_WARNING)
                else -> NauticalColorResolver.getColor(context, NauticalSemanticColor.STATUS_ERROR)
            }
        )
    }

    override fun setCompactMode(enabled: Boolean) {
        val p = if (enabled) 4 else 8
        cardRoot.setContentPadding(p, p, p, p)
    }

    private fun resolveThemeColor(attrResId: Int, fallbackResId: Int): Int {
        val typedArray = context.obtainStyledAttributes(intArrayOf(attrResId))
        val color = typedArray.getColor(0, ContextCompat.getColor(context, fallbackResId))
        typedArray.recycle()
        return color
    }

    override fun applyNightVision(enabled: Boolean) {
        if (enabled) {
            // Monochromatic red night-vision palette (see NauticalSemanticColor
            // DARK notes): values bright red, secondary labels dimmer.
            cardRoot.setCardBackgroundColor(0xEE120000.toInt())
            cardRoot.strokeColor = 0xFF8B0000.toInt()
            txtPassageSogVal.setTextColor(0xFFFF1744.toInt())
            txtPassageCogLbl.setTextColor(0xFFFF8A80.toInt())
            txtPassageTwaVal.setTextColor(0xFFFF1744.toInt())
            txtPassageTwsLbl.setTextColor(0xFFFF8A80.toInt())
            txtPassageVmgVal.setTextColor(0xFFFF8A80.toInt())
            txtPassageXteVal.setTextColor(0xFFFF1744.toInt())
            txtMotorSogVal.setTextColor(0xFFFF1744.toInt())
            txtMotorRudderVal.setTextColor(0xFFFF1744.toInt())
            txtMotorDepthVal.setTextColor(0xFFFF1744.toInt())
            txtMotorTideLbl.setTextColor(0xFFFF8A80.toInt())
            txtMotorHazardVal.setTextColor(0xFFFF1744.toInt())
            txtAnchorDepthVal.setTextColor(0xFFFF1744.toInt())
            txtAnchorRodeVal.setTextColor(0xFFFF1744.toInt())
            txtAnchorScopeLbl.setTextColor(0xFFFF8A80.toInt())
            txtAnchorRadiusVal.setTextColor(0xFFFF1744.toInt())
            txtAnchorBatteryVal.setTextColor(0xFFFF8A80.toInt())
            txtAnchorChafeLbl.setTextColor(0xFFFF8A80.toInt())
            btnModePassage.setTextColor(0xFFFF1744.toInt())
            btnModeMotoring.setTextColor(0xFFFF1744.toInt())
            btnModeAnchor.setTextColor(0xFFFF1744.toInt())
        } else {
            // Day/night app theme: route through the shared semantic palette
            // so a dark app theme gets dark colors instead of forced *_light.
            val primaryColor = NauticalColorResolver.getColor(context, NauticalSemanticColor.PRIMARY)
            val accentColor = NauticalColorResolver.getColor(context, NauticalSemanticColor.ACCENT)
            val okColor = NauticalColorResolver.getColor(context, NauticalSemanticColor.STATUS_OK)
            cardRoot.setCardBackgroundColor(
                resolveThemeColor(R.attr.card_and_list_background_basic, R.color.bg_color_light)
            )
            cardRoot.strokeColor = resolveThemeColor(R.attr.divider_color, R.color.divider_color_light)
            txtPassageSogVal.setTextColor(primaryColor)
            txtPassageCogLbl.setTextColor(NauticalColorResolver.getColor(context, NauticalSemanticColor.SECONDARY))
            txtPassageTwaVal.setTextColor(accentColor)
            txtPassageTwsLbl.setTextColor(NauticalColorResolver.getColor(context, NauticalSemanticColor.SECONDARY))
            txtPassageVmgVal.setTextColor(primaryColor)
            txtPassageXteVal.setTextColor(primaryColor)
            txtMotorSogVal.setTextColor(primaryColor)
            txtMotorRudderVal.setTextColor(accentColor)
            txtMotorDepthVal.setTextColor(primaryColor)
            txtMotorTideLbl.setTextColor(NauticalColorResolver.getColor(context, NauticalSemanticColor.SECONDARY))
            txtMotorHazardVal.setTextColor(okColor)
            txtAnchorDepthVal.setTextColor(primaryColor)
            txtAnchorRodeVal.setTextColor(accentColor)
            txtAnchorScopeLbl.setTextColor(NauticalColorResolver.getColor(context, NauticalSemanticColor.SECONDARY))
            txtAnchorRadiusVal.setTextColor(primaryColor)
            txtAnchorBatteryVal.setTextColor(okColor)
            txtAnchorChafeLbl.setTextColor(NauticalColorResolver.getColor(context, NauticalSemanticColor.SECONDARY))
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
