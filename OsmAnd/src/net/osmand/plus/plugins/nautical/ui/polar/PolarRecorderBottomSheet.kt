package net.osmand.plus.plugins.nautical.ui.polar

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.ui.editor.PolarCurveCanvasView
import net.osmand.plus.plugins.nautical.ui.widgets.BaseNauticalBottomSheet
import java.util.Locale
import kotlin.math.abs

class PolarRecorderBottomSheet : BaseNauticalBottomSheet() {

    private var isRecording = false
    private val recordedScatterPoints = mutableListOf<Pair<Double, Double>>()
    private var showScatterOverlay = true

    override fun createMenuItems(savedInstanceState: Bundle?) {
        addTitleItem(getString(R.string.nautical_polar_recorder_title))

        val themedCtx = net.osmand.plus.utils.UiUtilities.getThemedContext(requireContext(), nightMode)
        val customView = LayoutInflater.from(themedCtx).inflate(R.layout.bottom_sheet_polar_recorder, null)

        val txtStatus = customView.findViewById<TextView>(R.id.txt_polar_rec_status)
        val txtSampleCount = customView.findViewById<TextView>(R.id.txt_polar_sample_count)
        val txtActiveProfile = customView.findViewById<TextView>(R.id.txt_polar_active_profile)
        val polarCanvas = customView.findViewById<PolarCurveCanvasView>(R.id.polar_canvas_view)
        val switchScatter = customView.findViewById<SwitchCompat>(R.id.switch_scatter_overlay)
        val btnToggleRec = customView.findViewById<MaterialButton>(R.id.btn_toggle_recording)
        val btnStopAndSave = customView.findViewById<MaterialButton>(R.id.btn_stop_and_save)
        val btnDownload = customView.findViewById<MaterialButton>(R.id.btn_download_server_polar)

        val activeColor = ContextCompat.getColor(themedCtx, R.color.nautical_status_green)
        val idleColor = ContextCompat.getColor(themedCtx, R.color.text_color_secondary_light)

        fun updateRecordingUi() {
            if (isRecording) {
                txtStatus.text = getString(R.string.nautical_polar_rec_status_recording)
                txtStatus.setTextColor(activeColor)
                btnToggleRec.text = getString(R.string.nautical_polar_stop_recording)
                btnToggleRec.setIconResource(R.drawable.ic_action_rec_stop)
                btnToggleRec.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(themedCtx, R.color.color_warning))
            } else {
                txtStatus.text = getString(R.string.nautical_polar_rec_status_idle)
                txtStatus.setTextColor(idleColor)
                btnToggleRec.text = getString(R.string.nautical_polar_start_recording)
                btnToggleRec.setIconResource(R.drawable.ic_action_rec_start)
                btnToggleRec.backgroundTintList = null
            }
            txtSampleCount.text = getString(R.string.nautical_polar_samples_count, recordedScatterPoints.size)
            if (showScatterOverlay) {
                polarCanvas.rawPoints = recordedScatterPoints.toList()
            } else {
                polarCanvas.rawPoints = emptyList()
            }
        }

        switchScatter.setOnCheckedChangeListener { _, isChecked ->
            showScatterOverlay = isChecked
            polarCanvas.rawPoints = if (isChecked) recordedScatterPoints.toList() else emptyList()
        }

        btnToggleRec.setOnClickListener {
            isRecording = !isRecording
            updateRecordingUi()
            val msg = if (isRecording) "Polar performance recording started" else "Recording paused"
            NauticalPlugin.getInstance()?.application?.showToastMessage(msg)
        }

        btnStopAndSave.setOnClickListener {
            isRecording = false
            updateRecordingUi()
            NauticalPlugin.getInstance()?.application?.showToastMessage(R.string.nautical_polar_saved_success)
        }

        btnDownload.setOnClickListener {
            SailingDependencyContainer.performanceRepository?.fetchPolars()
            NauticalPlugin.getInstance()?.application?.showToastMessage("Syncing active polars from Signal K server...")
        }

        // Observe active polar profile for canvas curve
        val repo = SailingDependencyContainer.performanceRepository
        if (repo != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                repo.activePolarProfile.collectLatest { profile ->
                    if (profile != null) {
                        txtActiveProfile.text = getString(R.string.nautical_polar_active_profile_label, profile.name)
                        val curvePoints = mutableListOf<Pair<Double, Double>>()
                        val twsList = profile.tws
                        val twaList = profile.twa
                        val speeds = profile.speeds
                        if (twsList != null && twaList != null && speeds != null && speeds.isNotEmpty()) {
                            val nominalTws = 16.0
                            val twsIdx = twsList.indexOfFirst { it >= nominalTws }.let { if (it <= 0) 0 else it.coerceAtMost(speeds.size - 1) }
                            val speedRow = speeds[twsIdx]
                            twaList.forEachIndexed { i, angleDeg ->
                                if (i < speedRow.size) {
                                    curvePoints.add(Pair(angleDeg, speedRow[i]))
                                }
                            }
                        }
                        polarCanvas.smoothedPoints = curvePoints
                    } else {
                        txtActiveProfile.text = "Profile: Standard Monohull"
                    }
                }
            }
        }

        // Live telemetry point recording
        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                if (isRecording) {
                    val twa = state.windDirectionApparent?.let { Math.toDegrees(it) }
                    val sog = state.speedOverGround?.let { it * 1.94384 } // m/s to knots
                    if (twa != null && sog != null && sog > 0.5) {
                        val boundedTwa = abs(twa % 360.0).let { if (it > 180.0) 360.0 - it else it }
                        recordedScatterPoints.add(Pair(boundedTwa, sog))
                        if (recordedScatterPoints.size > 500) {
                            recordedScatterPoints.removeAt(0)
                        }
                        txtSampleCount.text = getString(R.string.nautical_polar_samples_count, recordedScatterPoints.size)
                        if (showScatterOverlay) {
                            polarCanvas.rawPoints = recordedScatterPoints.toList()
                        }
                    }
                }
            }
        }

        updateRecordingUi()
        items.add(BaseBottomSheetItem.Builder().setCustomView(customView).create())
    }

    companion object {
        const val TAG = "PolarRecorderBottomSheet"

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.isStateSaved) return
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                PolarRecorderBottomSheet().show(fragmentManager, TAG)
            }
        }
    }
}
