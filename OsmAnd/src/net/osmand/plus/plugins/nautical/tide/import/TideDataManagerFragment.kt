package net.osmand.plus.plugins.nautical.tide.import

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.network.SignalKTideStation
import net.osmand.plus.plugins.nautical.tide.parser.HarmonicDataParser
import java.io.File
import java.util.Locale

class TideDataManagerFragment : BaseOsmAndFragment() {

    private lateinit var statusText: TextView
    private lateinit var importButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                performImport(uri)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.fragment_tide_data_manager, container, false)

        statusText = view.findViewById(R.id.tide_status_text)
        importButton = view.findViewById(R.id.tide_import_button)
        progressBar = view.findViewById(R.id.tide_progress_bar)
        recyclerView = view.findViewById(R.id.recycler_view)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        importButton.setOnClickListener {
            launchFilePicker()
        }

        updateStatus()
        observeStations()

        return view
    }

    private fun observeStations() {
        val tideManager = NauticalPlugin.getInstance()?.tideManager ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            tideManager.stations.collectLatest { stations ->
                recyclerView.adapter = StationAdapter(stations.values.toList())
            }
        }
    }

    private fun launchFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" 
            val mimeTypes = arrayOf("text/plain", "application/octet-stream", "text/comma-separated-values")
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }
        filePickerLauncher.launch(intent)
    }

    private fun updateStatus() {
        val file = File(app.filesDir, "tides/harmonics.txt")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val stationsCount = withContext(Dispatchers.IO) {
                    if (file.exists()) {
                        val parser = HarmonicDataParser()
                        file.inputStream().use { parser.parse(it) }.size
                    } else 0
                }
                if (stationsCount > 0) {
                    statusText.text = getString(R.string.tide_import_loaded, stationsCount)
                } else {
                    statusText.text = getString(R.string.tide_import_none)
                }
            } catch (_: Exception) {
                statusText.text = getString(R.string.tide_import_error_reading)
            }
        }
    }

    private fun performImport(uri: Uri) {
        importButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        statusText.text = getString(R.string.tide_importing)

        viewLifecycleOwner.lifecycleScope.launch {
            val importer = HarmonicFileImporter(app)
            val result = importer.importHarmonics(uri)

            progressBar.visibility = View.GONE
            importButton.isEnabled = true

            result.onSuccess {
                app.showToastMessage(R.string.tide_import_success)
                updateStatus()
            }.onFailure {
                app.showToastMessage(R.string.tide_import_error)
                updateStatus()
            }
        }
    }

    private inner class StationAdapter(private val stations: List<SignalKTideStation>) : RecyclerView.Adapter<StationViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.list_item_with_descr, parent, false)
            return StationViewHolder(v)
        }
        override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
            val station = stations[position]
            holder.title.text = station.name
            holder.info.text = String.format(Locale.US, "Lat: %.4f, Lon: %.4f", station.position.coordinates[1], station.position.coordinates[0])
            holder.icon.setImageResource(R.drawable.ic_action_marker_dark)
            holder.icon.visibility = View.VISIBLE
            
            holder.itemView.setOnClickListener {
                app.settings.setMapLocationToShow(station.position.coordinates[1], station.position.coordinates[0], 13)
                app.runInUIThread { requireActivity().onBackPressedDispatcher.onBackPressed() }
            }
        }
        override fun getItemCount(): Int = stations.size
    }

    private class StationViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.title)
        val info: TextView = v.findViewById(R.id.description)
        val icon: android.widget.ImageView = v.findViewById(R.id.icon)
    }
}
