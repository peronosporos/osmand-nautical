package net.osmand.plus.plugins.nautical.routing.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.engine.NauticalSafetyManager
import net.osmand.plus.plugins.nautical.grib.parser.GribGridData
import net.osmand.plus.plugins.nautical.grib.parser.GribHeader
import net.osmand.plus.plugins.nautical.grib.parser.TimeStepGrid
import net.osmand.plus.plugins.nautical.routing.model.RoutingRequest
import net.osmand.plus.plugins.nautical.routing.model.Waypoint
import net.osmand.plus.plugins.nautical.s57.S57SpatialIndex
import net.osmand.plus.plugins.nautical.ui.NauticalColorResolver
import net.osmand.plus.plugins.nautical.ui.NauticalSemanticColor
import net.osmand.plus.plugins.nautical.ui.widgets.BaseNauticalBottomSheet
import net.osmand.plus.plugins.nautical.viewmodel.RoutingStatus
import net.osmand.plus.plugins.nautical.viewmodel.RoutingViewModel
import net.osmand.plus.plugins.nautical.viewmodel.displayText
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class WeatherRoutingConfigBottomSheet : BaseNauticalBottomSheet() {

    private var departureOffsetHours: Long = 0L
    private var timeStepHours: Double = 1.0
    private var comfortSpeedWeight: Int = 50
    private var minDepthClearance: Double = 2.5

    override fun createMenuItems(savedInstanceState: Bundle?) {
        addTitleItem(getString(R.string.nautical_weather_routing_title))

        val themedCtx = net.osmand.plus.utils.UiUtilities.getThemedContext(requireContext(), nightMode)
        val customView = LayoutInflater.from(themedCtx).inflate(R.layout.bottom_sheet_weather_routing_config, null)

        val btnDepNow = customView.findViewById<MaterialButton>(R.id.btn_dep_now)
        val btnDep1h = customView.findViewById<MaterialButton>(R.id.btn_dep_1h)
        val btnDep3h = customView.findViewById<MaterialButton>(R.id.btn_dep_3h)
        val btnDep6h = customView.findViewById<MaterialButton>(R.id.btn_dep_6h)
        val depButtons = listOf(btnDepNow, btnDep1h, btnDep3h, btnDep6h)

        val btnStep15m = customView.findViewById<MaterialButton>(R.id.btn_step_15m)
        val btnStep30m = customView.findViewById<MaterialButton>(R.id.btn_step_30m)
        val btnStep1h = customView.findViewById<MaterialButton>(R.id.btn_step_1h)
        val btnStep3h = customView.findViewById<MaterialButton>(R.id.btn_step_3h)
        val stepButtons = listOf(btnStep15m, btnStep30m, btnStep1h, btnStep3h)

        val seekComfortSpeed = customView.findViewById<SeekBar>(R.id.seek_comfort_speed)
        val txtWeightingVal = customView.findViewById<TextView>(R.id.txt_weighting_value)

        val btnDepthMinus = customView.findViewById<MaterialButton>(R.id.btn_depth_minus)
        val btnDepthPlus = customView.findViewById<MaterialButton>(R.id.btn_depth_plus)
        val txtMinDepth = customView.findViewById<TextView>(R.id.txt_min_depth_val)

        val layoutProgress = customView.findViewById<View>(R.id.layout_routing_progress)
        val txtProgressMsg = customView.findViewById<TextView>(R.id.txt_routing_status_msg)
        val btnCalculate = customView.findViewById<MaterialButton>(R.id.btn_calculate_optimal_route)

        val activeColor = ContextCompat.getColor(themedCtx, R.color.active_color_primary_light)

        fun updateDepButtons() {
            depButtons.forEachIndexed { index, btn ->
                val match = when (index) {
                    0 -> departureOffsetHours == 0L
                    1 -> departureOffsetHours == 1L
                    2 -> departureOffsetHours == 3L
                    3 -> departureOffsetHours == 6L
                    else -> false
                }
                if (match) {
                    btn.strokeColor = ColorStateList.valueOf(activeColor)
                    btn.setTextColor(activeColor)
                    btn.strokeWidth = (2 * resources.displayMetrics.density).toInt()
                } else {
                    btn.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(themedCtx, R.color.nautical_chip_idle_stroke))
                    btn.setTextColor(NauticalColorResolver.getColor(themedCtx, NauticalSemanticColor.SECONDARY))
                    btn.strokeWidth = (1 * resources.displayMetrics.density).toInt()
                }
            }
        }

        fun updateStepButtons() {
            stepButtons.forEachIndexed { index, btn ->
                val match = when (index) {
                    0 -> timeStepHours == 0.25
                    1 -> timeStepHours == 0.5
                    2 -> timeStepHours == 1.0
                    3 -> timeStepHours == 3.0
                    else -> false
                }
                if (match) {
                    btn.strokeColor = ColorStateList.valueOf(activeColor)
                    btn.setTextColor(activeColor)
                    btn.strokeWidth = (2 * resources.displayMetrics.density).toInt()
                } else {
                    btn.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(themedCtx, R.color.nautical_chip_idle_stroke))
                    btn.setTextColor(NauticalColorResolver.getColor(themedCtx, NauticalSemanticColor.SECONDARY))
                    btn.strokeWidth = (1 * resources.displayMetrics.density).toInt()
                }
            }
        }

        btnDepNow.setOnClickListener { departureOffsetHours = 0L; updateDepButtons() }
        btnDep1h.setOnClickListener { departureOffsetHours = 1L; updateDepButtons() }
        btnDep3h.setOnClickListener { departureOffsetHours = 3L; updateDepButtons() }
        btnDep6h.setOnClickListener { departureOffsetHours = 6L; updateDepButtons() }

        btnStep15m.setOnClickListener { timeStepHours = 0.25; updateStepButtons() }
        btnStep30m.setOnClickListener { timeStepHours = 0.5; updateStepButtons() }
        btnStep1h.setOnClickListener { timeStepHours = 1.0; updateStepButtons() }
        btnStep3h.setOnClickListener { timeStepHours = 3.0; updateStepButtons() }

        seekComfortSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                comfortSpeedWeight = progress
                txtWeightingVal.text = when {
                    progress < 30 -> getString(R.string.nautical_wr_weight_comfort_fmt, progress)
                    progress > 70 -> getString(R.string.nautical_wr_weight_speed_fmt, progress)
                    else -> getString(R.string.nautical_wr_weight_balanced_fmt, progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        fun updateDepthDisplay() {
            txtMinDepth.text = String.format(Locale.US, "%.1f m", minDepthClearance)
        }

        btnDepthMinus.setOnClickListener {
            if (minDepthClearance > 1.0) {
                minDepthClearance -= 0.5
                updateDepthDisplay()
            }
        }

        btnDepthPlus.setOnClickListener {
            if (minDepthClearance < 20.0) {
                minDepthClearance += 0.5
                updateDepthDisplay()
            }
        }

        val viewModel = ViewModelProvider(requireActivity())[RoutingViewModel::class.java]

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.routingStatus.collectLatest { status ->
                txtProgressMsg.text = status.displayText(app)
                when (status) {
                    RoutingStatus.Done -> {
                        layoutProgress.visibility = View.GONE
                        btnCalculate.isEnabled = true
                        if (isAdded && !parentFragmentManager.isStateSaved) {
                            dismissAllowingStateLoss()
                            PassagePlanBottomSheet.show(parentFragmentManager)
                        }
                    }
                    RoutingStatus.Starting, is RoutingStatus.Calculating -> {
                        layoutProgress.visibility = View.VISIBLE
                        btnCalculate.isEnabled = false
                    }
                    else -> {
                        btnCalculate.isEnabled = true
                    }
                }
            }
        }

        btnCalculate.setOnClickListener {
            layoutProgress.visibility = View.VISIBLE
            btnCalculate.isEnabled = false
            txtProgressMsg.text = getString(R.string.nautical_calculating_weather_route)

            val plugin = NauticalPlugin.getInstance()
            val appCtx = plugin?.application ?: (requireActivity().application as net.osmand.plus.OsmandApplication)
            fun refuseCalc(resId: Int) {
                layoutProgress.visibility = View.GONE
                btnCalculate.isEnabled = true
                appCtx.showToastMessage(resId)
            }
            val ownLoc = plugin?.application?.locationProvider?.lastKnownLocation
            if (ownLoc == null) {
                // Honesty gate: routing from an invented position is refused
                // (previously it silently started at 52.5N 4.5E).
                refuseCalc(R.string.nautical_wr_no_position)
                return@setOnClickListener
            }
            val startWp = Waypoint(ownLoc.latitude, ownLoc.longitude)

            val argLat = arguments?.getDouble(EXTRA_DEST_LAT, 0.0)?.takeIf { it != 0.0 }
                ?: plugin?.application?.settings?.NAUTICAL_TACTICAL_TARGET_LAT?.get()?.takeIf { it != 0.0 }
            val argLon = arguments?.getDouble(EXTRA_DEST_LON, 0.0)?.takeIf { it != 0.0 }
                ?: plugin?.application?.settings?.NAUTICAL_TACTICAL_TARGET_LON?.get()?.takeIf { it != 0.0 }

            if (argLat == null || argLon == null) {
                // Honesty gate: previously this invented a destination half a
                // degree away and confidently routed to it.
                refuseCalc(R.string.nautical_wr_no_destination)
                return@setOnClickListener
            }
            val destWp = Waypoint(argLat, argLon)

            val depTime = System.currentTimeMillis() + departureOffsetHours * 3600000L

            val polarProfile = SailingDependencyContainer.performanceRepository?.activePolarProfile?.value
            if (polarProfile == null) {
                // Honesty gate: routing would claim boat speeds from an
                // invented "Default Cruiser" polar table; refuse instead.
                refuseCalc(R.string.nautical_wr_no_polar)
                return@setOnClickListener
            }

            val request = RoutingRequest(
                start = startWp,
                destination = destWp,
                departureTime = depTime,
                polarProfile = polarProfile,
                timeStepHours = timeStepHours
            )

            // Resolve weather grid: from GRIB repository or synthesized from live wind telemetry
            val memoryGrid = SailingDependencyContainer.gribRepository?.gridData
            val liveState = NauticalPlugin.engine?.getCurrentState()
            val resolvedGrid: GribGridData = if (memoryGrid != null && memoryGrid.timeSteps.isNotEmpty()) {
                memoryGrid
            } else {
                val tws = liveState?.windSpeedTrue ?: liveState?.windSpeedApparent
                val twd = liveState?.windDirectionTrue ?: liveState?.windDirectionApparent
                if (tws == null || twd == null) {
                    // Honesty gate: without a forecast file or a live wind
                    // measurement there is no wind field to route on
                    // (previously it invented a constant 8 m/s breeze).
                    refuseCalc(R.string.nautical_wr_no_wind_data)
                    return@setOnClickListener
                }
                // Uniform grid synthesized from the single live observation.
                val u = (-tws * sin(twd)).toFloat()
                val v = (-tws * cos(twd)).toFloat()
                val minLat = minOf(startWp.latitude, destWp.latitude) - 1.5
                val maxLat = maxOf(startWp.latitude, destWp.latitude) + 1.5
                val minLon = minOf(startWp.longitude, destWp.longitude) - 1.5
                val maxLon = maxOf(startWp.longitude, destWp.longitude) + 1.5
                val latSteps = 15
                val lonSteps = 15
                val totalPoints = latSteps * lonSteps
                val uGrid = FloatArray(totalPoints) { u }
                val vGrid = FloatArray(totalPoints) { v }
                val timeStep = TimeStepGrid(timestamp = System.currentTimeMillis(), uGrid = uGrid, vGrid = vGrid)
                val header = GribHeader(minLat, maxLat, minLon, maxLon, latSteps, lonSteps)
                GribGridData(header, listOf(timeStep))
            }

            val app = plugin?.application ?: (requireActivity().application as net.osmand.plus.OsmandApplication)
            val index = plugin?.s57SpatialIndex ?: S57SpatialIndex(app)
            val safety = plugin?.safetyManager ?: NauticalSafetyManager.getInstance(app)

            viewModel.calculateWeatherRoute(request, resolvedGrid, index, safety, minDepthClearance)
        }

        updateDepButtons()
        updateStepButtons()
        updateDepthDisplay()

        items.add(BaseBottomSheetItem.Builder().setCustomView(customView).create())
    }

    companion object {
        const val TAG = "WeatherRoutingConfigBottomSheet"
        const val EXTRA_DEST_LAT = "extra_dest_lat"
        const val EXTRA_DEST_LON = "extra_dest_lon"

        fun newInstance(destLat: Double? = null, destLon: Double? = null): WeatherRoutingConfigBottomSheet {
            val fragment = WeatherRoutingConfigBottomSheet()
            if (destLat != null && destLon != null) {
                val bundle = Bundle().apply {
                    putDouble(EXTRA_DEST_LAT, destLat)
                    putDouble(EXTRA_DEST_LON, destLon)
                }
                fragment.arguments = bundle
            }
            return fragment
        }

        fun show(fragmentManager: FragmentManager, destLat: Double? = null, destLon: Double? = null) {
            if (fragmentManager.isStateSaved) return
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                newInstance(destLat, destLon).show(fragmentManager, TAG)
            }
        }
    }
}
