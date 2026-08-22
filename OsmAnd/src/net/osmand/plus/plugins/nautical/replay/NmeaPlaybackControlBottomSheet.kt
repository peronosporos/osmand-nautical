package net.osmand.plus.plugins.nautical.replay

import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment

/**
 * Bottom sheet UI for NMEA replay controls.
 */
class NmeaPlaybackControlBottomSheet : BaseMaterialBottomSheetDialogFragment() {

    private lateinit var viewModel: NmeaReplayViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_nmea_replay, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as OsmandApplication
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {
                val savedStateHandle = extras.createSavedStateHandle()
                return NmeaReplayViewModel(app, savedStateHandle) as T
            }
        }
        viewModel = ViewModelProvider(this, factory)[NmeaReplayViewModel::class.java]

        setupUI(view)
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    private fun setupUI(view: View) {
        val seekPlayback = view.findViewById<SeekBar>(R.id.seek_playback)
        seekPlayback.max = 1000 // Standardize resolution

        val btnPlayPause = view.findViewById<MaterialButton>(R.id.btn_play_pause)
        val btnStop = view.findViewById<MaterialButton>(R.id.btn_stop)
        val toggleSpeed = view.findViewById<MaterialButtonToggleGroup>(R.id.toggle_speed)
        val btnSelectFile = view.findViewById<MaterialButton>(R.id.btn_select_file)

        // SYNC Speed Toggle
        when (viewModel.playbackSpeed.value) {
            1.0f -> toggleSpeed.check(R.id.btn_speed_1x)
            2.0f -> toggleSpeed.check(R.id.btn_speed_2x)
            5.0f -> toggleSpeed.check(R.id.btn_speed_5x)
        }

        btnPlayPause.setOnClickListener { viewModel.togglePlayback() }
        btnStop.setOnClickListener { viewModel.stopPlayback() }

        btnSelectFile.setOnClickListener {
            showFileSelectionDialog()
        }

        toggleSpeed.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val speed = when (checkedId) {
                    R.id.btn_speed_1x -> 1.0f
                    R.id.btn_speed_2x -> 2.0f
                    R.id.btn_speed_5x -> 5.0f
                    else -> 1.0f
                }
                viewModel.setSpeed(speed)
            }
        }

        seekPlayback.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) viewModel.seekTo(progress / 1000f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun observeViewModel() {
        val view = view ?: return
        val btnPlayPause = view.findViewById<MaterialButton>(R.id.btn_play_pause)
        val seekPlayback = view.findViewById<SeekBar>(R.id.seek_playback)
        val txtFilename = view.findViewById<TextView>(R.id.txt_filename)
        val txtProgressPct = view.findViewById<TextView>(R.id.txt_progress_pct)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.engineState.collectLatest { state ->
                updatePlayPauseButton(btnPlayPause, state)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.progress.collectLatest { progress ->
                val progressInt = (progress * 1000).toInt()
                seekPlayback.progress = progressInt
                val pct = (progress * 100).toInt()
                txtProgressPct?.text = "$pct%"
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.recorder.currentFile.collectLatest { file ->
                if (file != null) {
                    txtFilename.text = getString(R.string.nautical_replay_recording_format, file)
                } else {
                    txtFilename.text = getString(R.string.nautical_replay_select_file)
                }
            }
        }
    }

    private fun updatePlayPauseButton(btn: MaterialButton, state: NmeaPlaybackEngine.PlaybackState) {
        if (state == NmeaPlaybackEngine.PlaybackState.PLAYING) {
            btn.setIconResource(R.drawable.ic_pause)
            btn.contentDescription = getString(R.string.nautical_replay_btn_pause)
        } else {
            btn.setIconResource(R.drawable.ic_action_play_dark)
            btn.contentDescription = getString(R.string.nautical_replay_btn_play)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyTheme()
    }

    private fun applyTheme() {
        val view = view ?: return
        val context = requireContext()
        val typedValue = TypedValue()
        
        context.theme.resolveAttribute(R.attr.colorOnSurface, typedValue, true)
        val textColor = typedValue.data
        
        view.findViewById<TextView>(R.id.txt_filename)?.setTextColor(textColor)
        
        context.theme.resolveAttribute(R.attr.colorPrimary, typedValue, true)
        
        view.findViewById<MaterialButton>(R.id.btn_stop)?.setIconTintResource(typedValue.resourceId)
        view.findViewById<MaterialButton>(R.id.btn_play_pause)?.setIconTintResource(typedValue.resourceId)
    }

    private fun showFileSelectionDialog() {
        val files = viewModel.getReplayFiles()
        if (files.isEmpty()) {
            app.showToastMessage(R.string.logbook_empty)
            return
        }

        val names = files.map { it.name }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.nautical_replay_select_file)
            .setItems(names) { _, which ->
                viewModel.startPlayback(files[which])
                view?.findViewById<TextView>(R.id.txt_filename)?.text = files[which].name
            }
            .show()
    }

    companion object {
        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            NmeaPlaybackControlBottomSheet().show(fragmentManager, "NmeaPlaybackControlBottomSheet")
        }
    }
}
