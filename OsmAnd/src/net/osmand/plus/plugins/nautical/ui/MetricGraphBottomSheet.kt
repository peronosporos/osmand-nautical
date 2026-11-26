package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.telemetry.AngleSide
import net.osmand.plus.plugins.nautical.telemetry.MetricStats
import net.osmand.plus.plugins.nautical.telemetry.TelemetryRegistry
import net.osmand.plus.plugins.nautical.telemetry.TelemetrySample
import net.osmand.plus.plugins.nautical.ui.widgets.NauticalMenuBottomSheetDialogFragment
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class TelemetryChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var samples: List<TelemetrySample> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var isDepth: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var unit: String = ""

    // Data line color: set by the sheet from the metric's category palette
    // (same palette as the telemetry tiles). Defaults to navigation blue.
    var dataColor: Int = ContextCompat.getColor(context, R.color.nautical_metric_cat_nav)
        set(value) {
            field = value
            invalidate()
        }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
    }

    private val path = Path()
    private val fillPath = Path()
    private var lastPlotH = 0f
    private var lastTop = 0f
    private var depthShader: LinearGradient? = null
    private var normalShader: LinearGradient? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Theme-aware colors (grid/labels follow the active display mode)
        gridPaint.color = NauticalColorResolver.getColor(context, NauticalSemanticColor.GRID)
        textPaint.color = NauticalColorResolver.getColor(context, NauticalSemanticColor.SECONDARY)

        val paddingLeft = 50f
        val paddingRight = 20f
        val paddingTop = 30f
        val paddingBottom = 40f

        val plotW = w - paddingLeft - paddingRight
        val plotH = h - paddingTop - paddingBottom
        if (plotW <= 0 || plotH <= 0) return

        // Draw background grid lines
        for (i in 0..4) {
            val y = paddingTop + (plotH / 4f) * i
            canvas.drawLine(paddingLeft, y, w - paddingRight, y, gridPaint)
        }

        if (samples.size < 2) {
            val msg = context.getString(R.string.nautical_no_historical_data)
            val textWidth = textPaint.measureText(msg)
            canvas.drawText(msg, (w - textWidth) / 2f, h / 2f, textPaint)
            return
        }

        var minVal = Double.MAX_VALUE
        var maxVal = -Double.MAX_VALUE
        for (s in samples) {
            if (s.value < minVal) minVal = s.value
            if (s.value > maxVal) maxVal = s.value
        }

        if (minVal == maxVal) {
            minVal -= 1.0
            maxVal += 1.0
        }

        // Add 5% headroom
        val range = maxVal - minVal
        val yMin = if (isDepth) max(0.0, minVal - range * 0.05) else minVal - range * 0.05
        val yMax = maxVal + range * 0.05
        val ySpan = yMax - yMin

        val minTime = samples.first().timestamp
        val maxTime = samples.last().timestamp
        val timeSpan = max(1L, maxTime - minTime).toFloat()

        // Draw Y scale labels
        val topLabel = if (isDepth) String.format(Locale.US, "%.1f %s", yMin, unit) else String.format(Locale.US, "%.1f %s", yMax, unit)
        val bottomLabel = if (isDepth) String.format(Locale.US, "%.1f %s", yMax, unit) else String.format(Locale.US, "%.1f %s", yMin, unit)
        canvas.drawText(topLabel, 10f, paddingTop + 20f, textPaint)
        canvas.drawText(bottomLabel, 10f, h - paddingBottom - 5f, textPaint)

        path.reset()
        fillPath.reset()

        var firstX = 0f
        var firstY = 0f
        var lastX = 0f

        for (i in samples.indices) {
            val s = samples[i]
            val x = paddingLeft + ((s.timestamp - minTime).toFloat() / timeSpan) * plotW
            val normY = ((s.value - yMin) / ySpan).toFloat().coerceIn(0f, 1f)
            val y = if (isDepth) {
                // Inverted Y: 0m at top
                paddingTop + normY * plotH
            } else {
                paddingTop + (1f - normY) * plotH
            }

            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, y)
                firstX = x
                firstY = y
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            lastX = x
        }

        if (plotH != lastPlotH || paddingTop != lastTop) {
            lastPlotH = plotH
            lastTop = paddingTop
            val depth = ContextCompat.getColor(context, R.color.nautical_chart_depth)
            depthShader = LinearGradient(
                0f, paddingTop, 0f, paddingTop + plotH,
                Color.argb(140, Color.red(depth), Color.green(depth), Color.blue(depth)),
                Color.argb(40, Color.red(depth), Color.green(depth), Color.blue(depth)),
                Shader.TileMode.CLAMP
            )
            normalShader = LinearGradient(
                0f, paddingTop, 0f, paddingTop + plotH,
                Color.argb(120, Color.red(dataColor), Color.green(dataColor), Color.blue(dataColor)),
                Color.argb(20, Color.red(dataColor), Color.green(dataColor), Color.blue(dataColor)),
                Shader.TileMode.CLAMP
            )
        }

        // Complete fill path
        if (isDepth) {
            // Fill below seabed curve (acoustic bottom profile)
            fillPath.lineTo(lastX, paddingTop + plotH)
            fillPath.lineTo(firstX, paddingTop + plotH)
            fillPath.close()

            fillPaint.shader = depthShader
            linePaint.color = ContextCompat.getColor(context, R.color.nautical_chart_depth)
        } else {
            fillPath.lineTo(lastX, paddingTop + plotH)
            fillPath.lineTo(firstX, paddingTop + plotH)
            fillPath.close()

            fillPaint.shader = normalShader
            linePaint.color = dataColor
        }

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)
    }
}

class MetricGraphBottomSheet : NauticalMenuBottomSheetDialogFragment() {

    private var metricKey: String = ""
    private var selectedDurationMs: Long = 300_000L // Default 5 min
    private var chartView: TelemetryChartView? = null
    private var txtLiveVal: TextView? = null
    private var txtLiveUnit: TextView? = null
    private var txtStatMin: TextView? = null
    private var txtStatAvg: TextView? = null
    private var txtStatMax: TextView? = null
    private var txtTendencyBanner: TextView? = null

    companion object {
        const val TAG = "nautical_metric_graph"
        const val KEY_METRIC = "key_metric"

        fun show(manager: FragmentManager, metricKey: String) {
            if (manager.isStateSaved) return
            if (manager.findFragmentByTag(TAG) == null) {
                val fragment = MetricGraphBottomSheet()
                val args = Bundle().apply {
                    putString(KEY_METRIC, metricKey)
                }
                fragment.arguments = args
                fragment.show(manager, TAG)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        metricKey = arguments?.getString(KEY_METRIC) ?: ""
    }

    override fun createMenuItems(savedInstanceState: Bundle?) {
        val app = requireContext().applicationContext as OsmandApplication
        val metricDef = TelemetryRegistry.getMetric(metricKey)

        val themedCtx = net.osmand.plus.utils.UiUtilities.getThemedContext(requireContext(), nightMode)
        val layout = LayoutInflater.from(themedCtx)
            .inflate(R.layout.bottom_sheet_nautical_metric_graph, null)

        val txtTitle = layout.findViewById<TextView>(R.id.txt_metric_title)
        val txtCategory = layout.findViewById<TextView>(R.id.txt_category_badge)
        txtLiveVal = layout.findViewById(R.id.txt_live_value)
        txtLiveUnit = layout.findViewById(R.id.txt_live_unit)
        txtStatMin = layout.findViewById(R.id.txt_stat_min)
        txtStatAvg = layout.findViewById(R.id.txt_stat_avg)
        txtStatMax = layout.findViewById(R.id.txt_stat_max)
        txtTendencyBanner = layout.findViewById(R.id.txt_tendency_banner)

        if (metricDef != null) {
            txtTitle.text = getString(metricDef.titleRes)
            txtCategory.text = getString(metricDef.category.titleRes)
        } else {
            txtTitle.text = metricKey
            txtCategory.text = ""
        }

        val chartContainer = layout.findViewById<FrameLayout>(R.id.chart_container)
        chartView = TelemetryChartView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isDepth = metricDef?.isDepth == true
            dataColor = ContextCompat.getColor(requireContext(), when (metricDef?.category) {
                net.osmand.plus.plugins.nautical.telemetry.MetricCategory.NAVIGATION -> R.color.nautical_metric_cat_nav
                net.osmand.plus.plugins.nautical.telemetry.MetricCategory.WIND -> R.color.nautical_metric_cat_wind
                net.osmand.plus.plugins.nautical.telemetry.MetricCategory.ENVIRONMENT -> R.color.nautical_metric_cat_env
                net.osmand.plus.plugins.nautical.telemetry.MetricCategory.VESSEL -> R.color.nautical_metric_cat_vessel
                net.osmand.plus.plugins.nautical.telemetry.MetricCategory.POWER -> R.color.nautical_metric_cat_power
                else -> R.color.nautical_metric_cat_nav
            })
        }
        chartContainer.addView(chartView)

        // Time Range Chips
        val chip5m = layout.findViewById<TextView>(R.id.chip_range_5m)
        val chip30m = layout.findViewById<TextView>(R.id.chip_range_30m)
        val chip6h = layout.findViewById<TextView>(R.id.chip_range_6h)
        val chip24h = layout.findViewById<TextView>(R.id.chip_range_24h)

        val chips = listOf(chip5m, chip30m, chip6h, chip24h)

        val chipActiveColor = net.osmand.plus.utils.AndroidUtils.getColorFromAttr(requireContext(), R.attr.active_color_primary)
        val chipBgColor = net.osmand.plus.utils.AndroidUtils.getColorFromAttr(requireContext(), R.attr.card_and_list_background_basic)
        val chipTextColor = net.osmand.plus.utils.AndroidUtils.getColorFromAttr(requireContext(), android.R.attr.textColorSecondary)

        fun selectChip(selected: TextView, duration: Long) {
            selectedDurationMs = duration
            for (c in chips) {
                c.setBackgroundResource(R.drawable.btn_active_light)
                if (c == selected) {
                    c.backgroundTintList = android.content.res.ColorStateList.valueOf(chipActiveColor)
                    c.setTextColor(Color.WHITE)
                } else {
                    c.backgroundTintList = android.content.res.ColorStateList.valueOf(chipBgColor)
                    c.setTextColor(chipTextColor)
                }
            }
            refreshData()
        }

        chip5m.setOnClickListener { selectChip(chip5m, 300_000L) }
        chip30m.setOnClickListener { selectChip(chip30m, 1_800_000L) }
        chip6h.setOnClickListener { selectChip(chip6h, 21_600_000L) }
        chip24h.setOnClickListener { selectChip(chip24h, 86_400_000L) }

        selectChip(chip5m, 300_000L)

        items.add(BaseBottomSheetItem.Builder().setCustomView(layout).create())

        observeLiveData()
    }

    private fun observeLiveData() {
        val filterEngine = NauticalPlugin.getInstance()?.telemetryFilterEngine
        if (filterEngine != null) {
            lifecycleScope.launch {
                filterEngine.filteredMetrics.collectLatest { metrics ->
                    val state = metrics[metricKey]
                    if (state != null) {
                        txtLiveVal?.text = state.formatted.primaryText
                        txtLiveUnit?.text = state.formatted.unitText
                        if (state.formatted.isPortStarboard && state.formatted.angleSide != AngleSide.NONE) {
                            val color = if (state.formatted.angleSide == AngleSide.STARBOARD) Color.rgb(0, 180, 0) else Color.RED
                            txtLiveVal?.setTextColor(color)
                        }
                    }
                    refreshData()
                }
            }
        }
    }

    private fun refreshData() {
        val metricDef = TelemetryRegistry.getMetric(metricKey) ?: return
        val stats = metricDef.ringBuffer.getStats(selectedDurationMs)
        val samples = metricDef.ringBuffer.getSamples(selectedDurationMs)

        chartView?.unit = when {
            metricDef.isDepth -> "m"
            metricDef.isPressure -> "hPa"
            else -> ""
        }
        chartView?.samples = samples

        val app = requireContext().applicationContext as OsmandApplication
        if (stats.sampleCount > 0) {
            val minFormatted = metricDef.formatter(app, app.settings, stats.min, null)
            val avgFormatted = metricDef.formatter(app, app.settings, stats.avg, null)
            val maxFormatted = metricDef.formatter(app, app.settings, stats.max, null)

            txtStatMin?.text = "${minFormatted.primaryText} ${minFormatted.unitText}"
            txtStatAvg?.text = "${avgFormatted.primaryText} ${avgFormatted.unitText}"
            txtStatMax?.text = "${maxFormatted.primaryText} ${maxFormatted.unitText}"

            if (metricDef.isPressure) {
                txtTendencyBanner?.visibility = View.VISIBLE
                val tendency3h = stats.ratePerHour * 3.0
                val desc = when {
                    tendency3h > 1.5 -> getString(R.string.nautical_pressure_rising_fast)
                    tendency3h > 0.3 -> getString(R.string.nautical_pressure_rising)
                    tendency3h < -1.5 -> getString(R.string.nautical_pressure_falling_fast)
                    tendency3h < -0.3 -> getString(R.string.nautical_pressure_falling)
                    else -> getString(R.string.nautical_pressure_steady)
                }
                txtTendencyBanner?.text = getString(
                    R.string.nautical_tendency_label,
                    String.format(Locale.US, "%s (%+.1f hPa/3h)", desc, tendency3h)
                )
            } else {
                txtTendencyBanner?.visibility = View.GONE
            }
        } else {
            txtStatMin?.text = "---"
            txtStatAvg?.text = "---"
            txtStatMax?.text = "---"
            txtTendencyBanner?.visibility = View.GONE
        }
    }
}
