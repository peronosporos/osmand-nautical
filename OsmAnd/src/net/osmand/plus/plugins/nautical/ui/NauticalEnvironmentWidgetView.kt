package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.engine.MarineState
import java.util.Locale

class NauticalEnvironmentWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val humidityTxt: TextView
    private val pressureTxt: TextView
    private val waterTempTxt: TextView
    private val moonTxt: TextView
    private val sunTxt: TextView

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.nautical_environment_hud, this, true)
        humidityTxt = findViewById(R.id.env_humidity)
        pressureTxt = findViewById(R.id.env_pressure)
        waterTempTxt = findViewById(R.id.env_water_temp)
        moonTxt = findViewById(R.id.env_moon)
        sunTxt = findViewById(R.id.env_sun)
        
        setBackgroundResource(R.drawable.bg_nautical_hud_panel)
        val p = dpToPx()
        setPadding(p, p, p, p)
    }

    fun updateState(state: MarineState) {
        humidityTxt.text = context.getString(R.string.nautical_humidity_label, String.format(Locale.US, "%.0f%%", (state.outsideHumidity ?: 0.0) * 100.0))
        
        val (pVal, pUnit) = net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter.formatValue(context, (context.applicationContext as net.osmand.plus.OsmandApplication).settings, state.outsidePressure, "pressure")
        pressureTxt.text = context.getString(R.string.nautical_pressure_label, "$pVal $pUnit")

        val (tVal, tUnit) = net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter.formatValue(context, (context.applicationContext as net.osmand.plus.OsmandApplication).settings, state.waterTemperature, "temperature")
        waterTempTxt.text = context.getString(R.string.nautical_water_temp_label, "$tVal$tUnit")

        moonTxt.text = context.getString(R.string.nautical_moon_phase_label, String.format(Locale.US, "%.0f%%", (state.moonPhase ?: 0.0) * 100.0))
        sunTxt.text = context.getString(R.string.nautical_sunlight_label, state.sunlightMode ?: context.getString(R.string.n_a))
        
        pressureTxt.visibility = if (state.outsidePressure != null) VISIBLE else GONE
        waterTempTxt.visibility = if (state.waterTemperature != null) VISIBLE else GONE
    }

    override fun setCompactMode(enabled: Boolean) {
        // Implement if needed
    }

    override fun isEmergency(): Boolean = false

    private fun dpToPx(dp: Int = 8): Int = (dp * resources.displayMetrics.density).toInt()
}
