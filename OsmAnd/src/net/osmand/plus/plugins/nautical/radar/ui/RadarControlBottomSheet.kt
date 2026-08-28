package net.osmand.plus.plugins.nautical.radar.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.base.BaseBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.settings.enums.ThemeUsageContext
import java.util.Locale

class RadarControlBottomSheet : BaseBottomSheetDialogFragment() {

    override fun getThemeUsageContext(): ThemeUsageContext = ThemeUsageContext.APP

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val app = context.applicationContext as OsmandApplication
        val isNight = NauticalPlugin.isNightVision(app)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            if (isNight) {
                setBackgroundColor(0xEE120000.toInt())
            }
        }

        // Title
        val titleView = TextView(context).apply {
            text = "Marine Radar Controls"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(if (isNight) 0xFFFF1744.toInt() else 0xFF212121.toInt())
            val bPad = (8 * resources.displayMetrics.density).toInt()
            setPadding(0, 0, 0, bPad)
        }
        root.addView(titleView)

        // 1. Gain Control (Auto / Manual)
        val autoGain = app.settings.NAUTICAL_RADAR_AUTO_GAIN.get()
        val gainVal = app.settings.NAUTICAL_RADAR_GAIN.get()

        val gainRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val gainLabel = TextView(context).apply {
            text = "Gain: ${if (autoGain) "AUTO ($gainVal%)" else "$gainVal%"}"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(if (isNight) 0xFFFF8A80.toInt() else 0xFF424242.toInt())
        }
        gainRow.addView(gainLabel)

        val btnAutoGain = MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = if (autoGain) "MANUAL" else "AUTO"
            minHeight = (48 * resources.displayMetrics.density).toInt()
            setTextColor(if (isNight) 0xFFFF1744.toInt() else 0xFF00E5FF.toInt())
        }
        gainRow.addView(btnAutoGain)
        root.addView(gainRow)

        val sliderGain = Slider(context).apply {
            valueFrom = 0f
            valueTo = 100f
            stepSize = 1f
            value = gainVal.toFloat()
            isEnabled = !autoGain
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (48 * resources.displayMetrics.density).toInt())
            if (isNight) {
                thumbTintList = android.content.res.ColorStateList.valueOf(0xFFFF1744.toInt())
                trackActiveTintList = android.content.res.ColorStateList.valueOf(0xFFFF1744.toInt())
                trackInactiveTintList = android.content.res.ColorStateList.valueOf(0x40FF1744.toInt())
            }
            addOnChangeListener { _, value, _ ->
                val v = value.toInt()
                app.settings.NAUTICAL_RADAR_GAIN.set(v)
                gainLabel.text = "Gain: $v%"
                app.osmandMap?.refreshMap()
            }
        }
        root.addView(sliderGain)

        btnAutoGain.setOnClickListener {
            val nextAuto = !app.settings.NAUTICAL_RADAR_AUTO_GAIN.get()
            app.settings.NAUTICAL_RADAR_AUTO_GAIN.set(nextAuto)
            btnAutoGain.text = if (nextAuto) "MANUAL" else "AUTO"
            sliderGain.isEnabled = !nextAuto
            val currentG = app.settings.NAUTICAL_RADAR_GAIN.get()
            gainLabel.text = "Gain: ${if (nextAuto) "AUTO ($currentG%)" else "$currentG%"}"
            app.osmandMap?.refreshMap()
        }

        // 2. Sea Clutter (0-100%)
        val seaVal = app.settings.NAUTICAL_RADAR_SEA_CLUTTER.get()
        val seaLabel = TextView(context).apply {
            text = "Sea Clutter: $seaVal%"
            setTextColor(if (isNight) 0xFFFF8A80.toInt() else 0xFF424242.toInt())
        }
        root.addView(seaLabel)

        val sliderSea = Slider(context).apply {
            valueFrom = 0f
            valueTo = 100f
            stepSize = 1f
            value = seaVal.toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (48 * resources.displayMetrics.density).toInt())
            if (isNight) {
                thumbTintList = android.content.res.ColorStateList.valueOf(0xFFFF1744.toInt())
                trackActiveTintList = android.content.res.ColorStateList.valueOf(0xFFFF1744.toInt())
                trackInactiveTintList = android.content.res.ColorStateList.valueOf(0x40FF1744.toInt())
            }
            addOnChangeListener { _, value, _ ->
                val v = value.toInt()
                app.settings.NAUTICAL_RADAR_SEA_CLUTTER.set(v)
                seaLabel.text = "Sea Clutter: $v%"
                app.osmandMap?.refreshMap()
            }
        }
        root.addView(sliderSea)

        // 3. Rain Clutter (0-100%)
        val rainVal = app.settings.NAUTICAL_RADAR_RAIN_CLUTTER.get()
        val rainLabel = TextView(context).apply {
            text = "Rain Clutter: $rainVal%"
            setTextColor(if (isNight) 0xFFFF8A80.toInt() else 0xFF424242.toInt())
        }
        root.addView(rainLabel)

        val sliderRain = Slider(context).apply {
            valueFrom = 0f
            valueTo = 100f
            stepSize = 1f
            value = rainVal.toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (48 * resources.displayMetrics.density).toInt())
            if (isNight) {
                thumbTintList = android.content.res.ColorStateList.valueOf(0xFFFF1744.toInt())
                trackActiveTintList = android.content.res.ColorStateList.valueOf(0xFFFF1744.toInt())
                trackInactiveTintList = android.content.res.ColorStateList.valueOf(0x40FF1744.toInt())
            }
            addOnChangeListener { _, value, _ ->
                val v = value.toInt()
                app.settings.NAUTICAL_RADAR_RAIN_CLUTTER.set(v)
                rainLabel.text = "Rain Clutter: $v%"
                app.osmandMap?.refreshMap()
            }
        }
        root.addView(sliderRain)

        // 4. Layer Opacity (20-100%)
        val opacityVal = app.settings.NAUTICAL_RADAR_OPACITY.get()
        val opacityLabel = TextView(context).apply {
            text = "Layer Opacity: $opacityVal%"
            setTextColor(if (isNight) 0xFFFF8A80.toInt() else 0xFF424242.toInt())
        }
        root.addView(opacityLabel)

        val sliderOpacity = Slider(context).apply {
            valueFrom = 20f
            valueTo = 100f
            stepSize = 5f
            value = opacityVal.toFloat().coerceIn(20f, 100f)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (48 * resources.displayMetrics.density).toInt())
            if (isNight) {
                thumbTintList = android.content.res.ColorStateList.valueOf(0xFFFF1744.toInt())
                trackActiveTintList = android.content.res.ColorStateList.valueOf(0xFFFF1744.toInt())
                trackInactiveTintList = android.content.res.ColorStateList.valueOf(0x40FF1744.toInt())
            }
            addOnChangeListener { _, value, _ ->
                val v = value.toInt()
                app.settings.NAUTICAL_RADAR_OPACITY.set(v)
                opacityLabel.text = "Layer Opacity: $v%"
                app.osmandMap?.refreshMap()
            }
        }
        root.addView(sliderOpacity)

        return root
    }

    companion object {
        const val TAG = "RadarControlBottomSheet"

        fun show(fm: FragmentManager) {
            if (fm.findFragmentByTag(TAG) == null) {
                RadarControlBottomSheet().show(fm, TAG)
            }
        }
    }
}
