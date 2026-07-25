package net.osmand.plus.plugins.nautical.replay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NmeaReplayViewModel(app) as T
            }
        }
        viewModel = ViewModelProvider(this, factory).get(NmeaReplayViewModel::class.java)

        val txtFilename = view.findViewById<TextView>(R.id.txt_filename)
        val seekPlayback = view.findViewById<SeekBar>(R.id.seek_playback)
        val btnPlayPause = view.findViewById<MaterialButton>(R.id.btn_play_pause)
        val btnStop = view.findViewById<MaterialButton>(R.id.btn_stop)
        val toggleSpeed = view.findViewById<MaterialButtonToggleGroup>(R.id.toggle_speed)
        val btnSelectFile = view.findViewById<MaterialButton>(R.id.btn_select_file)

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

        // Observe ViewModel State
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.engineState.collectLatest { state ->
                when (state) {
                    NmeaPlaybackEngine.PlaybackState.PLAYING -> {
                        btnPlayPause.setText(R.string.nautical_replay_btn_pause)
                        btnPlayPause.setIconResource(R.drawable.ic_pause)
                    }
                    else -> {
                        btnPlayPause.setText(R.string.nautical_replay_btn_play)
                        btnPlayPause.setIconResource(R.drawable.ic_action_play_dark)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.progress.collectLatest { progress ->
                seekPlayback.progress = (progress * 1000).toInt()
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
