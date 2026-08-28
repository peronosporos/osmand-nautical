package net.osmand.plus.plugins.nautical.ui.anchor

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import net.osmand.plus.plugins.nautical.maneuvers.WeighingAnchorManeuver
import net.osmand.plus.plugins.nautical.ui.INauticalHudHeader
import net.osmand.plus.utils.AndroidUtils

class AnchorWatchHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val txtRadius: TextView
    private val txtRode: TextView
    private val btnWeigh: MaterialButton
    private val btnAdjustScope: MaterialButton
    private val btnResetAnchorPos: MaterialButton
    private val imgIcon: ImageView
    private val dividerView: View
    private val container: View

    private val nightBackgroundDrawable: GradientDrawable by lazy {
        GradientDrawable().apply {
            setColor(0xEE120000.toInt())
            setStroke((1.5f * resources.displayMetrics.density).toInt(), 0xFFFF1744.toInt())
            cornerRadius = 8f * resources.displayMetrics.density
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.nautical_anchor_watch_hud, this, true)
        container = findViewById(R.id.anchor_hud_container)
        txtRadius = findViewById(R.id.txt_anchor_radius)
        txtRode = findViewById(R.id.txt_rode_deployed)
        btnWeigh = findViewById(R.id.btn_weigh_anchor)
        btnAdjustScope = findViewById(R.id.btn_adjust_scope)
        btnResetAnchorPos = findViewById(R.id.btn_reset_anchor_pos)
        imgIcon = findViewById(R.id.img_anchor_icon)
        dividerView = findViewById(R.id.divider_anchor_hud)
        
        setBackgroundResource(R.drawable.bg_nautical_hud_panel)
        
        btnWeigh.setOnClickListener {
            val plugin = NauticalPlugin.getInstance() ?: return@setOnClickListener
            val lat = plugin.application.settings.NAUTICAL_ANCHOR_LAT.get()
            val lon = plugin.application.settings.NAUTICAL_ANCHOR_LON.get()
            if (lat != 0.0) {
                (plugin.maneuverManager?.getManeuverById("weighing_anchor") as? WeighingAnchorManeuver)?.let {
                    it.setDropPoint(lat, lon)
                    plugin.maneuverManager?.setActiveManeuver("weighing_anchor")
                }
            }
        }

        btnAdjustScope.setOnClickListener {
            val act = context as? androidx.fragment.app.FragmentActivity ?: return@setOnClickListener
            AnchorWatchDialogFragment().show(act.supportFragmentManager, "AnchorWatchDialog")
        }

        btnResetAnchorPos.setOnClickListener {
            val plugin = NauticalPlugin.getInstance() ?: return@setOnClickListener
            val state = NauticalPlugin.engine?.getCurrentState()
            val lat = state?.latitude ?: plugin.application.settings.NAUTICAL_ANCHOR_LAT.get()
            val lon = state?.longitude ?: plugin.application.settings.NAUTICAL_ANCHOR_LON.get()
            if (lat != 0.0 && lon != 0.0) {
                plugin.anchorWatchdog?.setAnchor(lat, lon)
                plugin.application.showToastMessage(R.string.nautical_anchor_set)
            }
        }
    }

    fun update() {
        val plugin = NauticalPlugin.getInstance() ?: return
        val app = plugin.application
        val state = NauticalPlugin.engine?.getCurrentState() ?: return
        val caps = plugin.capabilityManager?.capabilities?.value ?: return
        
        val isNight = NauticalPlugin.isNightVision(app)
        applyNightVisionTheme(isNight)

        val anchorLat = app.settings.NAUTICAL_ANCHOR_LAT.get()
        val radius = if (anchorLat != 0.0) app.settings.NAUTICAL_ANCHOR_RADIUS.get() else null

        if (radius != null || (caps.hasChainCounter && state.rodeDeployed != null)) {
            isVisible = true
            
            val shallowQuad = plugin.anchorWatchdog?.shallowHazardQuadrant?.value
            val isChafeActive = plugin.anchorWatchdog?.isChafeAdvisoryActive?.value == true
            val isOverload = plugin.anchorWatchdog?.isWindlassOverload?.value == true
            val surgeCycles = plugin.anchorWatchdog?.waveSurgeCycles?.value ?: 0
            val isWindShiftRisk = plugin.anchorWatchdog?.isWindShiftBreakoutRisk?.value == true
            val windShiftDelta = plugin.anchorWatchdog?.windShiftDeltaDeg?.value ?: 0.0

            val radiusStr = if (isOverload) {
                txtRadius.setTextColor(0xFFFF1744.toInt())
                "⚠ ANCHOR SNAGGED / OVERLOAD"
            } else if (isWindShiftRisk) {
                txtRadius.setTextColor(0xFFFF1744.toInt())
                "⚠ HIGH BREAKOUT RISK: Rapid ${windShiftDelta.toInt()}° Wind Shift"
            } else if (isChafeActive) {
                txtRadius.setTextColor(0xFFFF1744.toInt())
                "⚠ CHAFE ADVISORY ($surgeCycles cycles)"
            } else if (shallowQuad != null) {
                txtRadius.setTextColor(0xFFFF1744.toInt())
                "Shallow water in $shallowQuad swing quadrant"
            } else {
                radius?.let {
                    val (v, u) = SignalKUnitConverter.formatValue(app, app.settings, it.toDouble(), "Radius")
                    "Radius: $v $u"
                } ?: "Not Set"
            }
            txtRadius.text = radiusStr

            if (isOverload) {
                btnResetAnchorPos.text = "Pay Out 2m"
                btnResetAnchorPos.setOnClickListener {
                    NauticalPlugin.electrical?.payoutRodeMeters(2.0)
                    plugin.anchorWatchdog?.clearWindlassOverload()
                    update()
                }
            } else if (isChafeActive) {
                btnResetAnchorPos.text = "Reset Chafe"
                btnResetAnchorPos.setOnClickListener {
                    plugin.anchorWatchdog?.resetChafeCycleCounter()
                    update()
                }
            }
            
            val isHighLoad = plugin.anchorWatchdog?.isHighRodeLoad?.value == true
            val tension = plugin.anchorWatchdog?.rodeTensionKg?.value ?: 0.0

            val depth = (state.depthBelowTransducer ?: state.depthBelowKeel ?: app.settings.NAUTICAL_ANCHOR_DEPTH.get().toDouble()).coerceAtLeast(1.0)
            val tideRise = app.settings.NAUTICAL_ANCHOR_TIDE_RISE.get().toDouble()
            val freeboard = app.settings.NAUTICAL_ANCHOR_FREEBOARD.get().toDouble()
            val targetScope = app.settings.NAUTICAL_ANCHOR_SCOPE_RATIO.get().toDouble()
            val minRodeRequired = (depth + tideRise + freeboard) * targetScope
            val deployed = state.rodeDeployed

            val rodeStr = if (deployed != null) {
                val ft = (deployed * 3.28084).toInt()
                val isScopeOk = deployed >= minRodeRequired
                val scopeBadge = if (isScopeOk) "[SCOPE OK]" else "[INSUFFICIENT RODE]"
                if (isHighLoad) {
                    "Rode: ${deployed.toInt()}m/${ft}ft $scopeBadge ⚠ HIGH LOAD (${tension.toInt()}kg)"
                } else {
                    "Rode: ${deployed.toInt()}m/${ft}ft $scopeBadge"
                }
            } else if (isHighLoad) {
                "⚠ HIGH RODE LOAD (${tension.toInt()}kg)"
            } else {
                "Rode: ---"
            }
            txtRode.text = rodeStr
            if (isHighLoad || (deployed != null && deployed < minRodeRequired)) {
                txtRode.setTextColor(0xFFFF1744.toInt())
            } else if (deployed != null && deployed >= minRodeRequired) {
                txtRode.setTextColor(if (isNight) 0xFFFF8A80.toInt() else 0xFF2E7D32.toInt())
            } else if (!isNight) {
                txtRode.setTextColor(AndroidUtils.getColorFromAttr(context, R.attr.text_color_primary_v2))
            }

            // Only show weigh anchor button if anchor is set and not already weighing
            val mm = plugin.maneuverManager
            btnWeigh.isVisible = anchorLat != 0.0 && mm?.activeManeuver !is WeighingAnchorManeuver
        } else {
            isVisible = false
        }
    }

    private fun applyNightVisionTheme(isNight: Boolean) {
        if (isNight) {
            background = nightBackgroundDrawable
            imgIcon.setColorFilter(0xFFFF1744.toInt())
            txtRadius.setTextColor(0xFFFF1744.toInt())
            txtRode.setTextColor(0xFFFF1744.toInt())
            dividerView.setBackgroundColor(0x40FF1744.toInt())
            btnWeigh.setTextColor(0xFFFF8A80.toInt())
            btnAdjustScope.setTextColor(0xFFFF8A80.toInt())
            btnResetAnchorPos.setTextColor(0xFFFF8A80.toInt())
        } else {
            setBackgroundResource(R.drawable.bg_nautical_hud_panel)
            val primaryColor = AndroidUtils.getColorFromAttr(context, R.attr.text_color_primary_v2)
            imgIcon.setColorFilter(primaryColor)
            txtRadius.setTextColor(primaryColor)
            txtRode.setTextColor(primaryColor)
            dividerView.setBackgroundColor(AndroidUtils.getColorFromAttr(context, R.attr.divider_color))
            val btnColor = AndroidUtils.getColorFromAttr(context, R.attr.active_color_primary_v2)
            btnWeigh.setTextColor(btnColor)
            btnAdjustScope.setTextColor(btnColor)
            btnResetAnchorPos.setTextColor(btnColor)
        }
    }

    override fun applyNightVision(enabled: Boolean) {
        applyNightVisionTheme(enabled)
    }

    override fun setCompactMode(enabled: Boolean) {
        val p = if (enabled) 2 else 8
        val px = (p * resources.displayMetrics.density).toInt()
        setPadding(px, px, px, px)
    }
}
