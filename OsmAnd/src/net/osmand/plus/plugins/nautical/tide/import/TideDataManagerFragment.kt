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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.tide.parser.HarmonicDataParser
import java.io.File

/**
 * Fragment for managing harmonic tide data ingestion from device storage.
 */
class TideDataManagerFragment : BaseOsmAndFragment() {

    private lateinit var statusText: TextView
    private lateinit var importButton: Button
    private lateinit var progressBar: ProgressBar

    // Launcher for the system document picker (Storage Access Framework)
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                performImport(uri)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Use the themed inflater provided by BaseOsmAndFragment to ensure consistent UI
        val view = themedInflater.inflate(R.layout.fragment_tide_data_manager, container, false)

        statusText = view.findViewById(R.id.tide_status_text)
        importButton = view.findViewById(R.id.tide_import_button)
        progressBar = view.findViewById(R.id.tide_progress_bar)

        importButton.setOnClickListener {
            launchFilePicker()
        }

        updateStatus()

        return view
    }

    private fun launchFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // XTide files are usually text-based, but we allow all types as a fallback
            type = "*/*" 
            val mimeTypes = arrayOf("text/plain", "application/octet-stream", "text/comma-separated-values")
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }
        filePickerLauncher.launch(intent)
    }

    /**
     * Checks the status of currently loaded harmonic data and updates the UI.
     */
    private fun updateStatus() {
        val file = File(app.filesDir, "tides/harmonics.txt")
        if (file.exists()) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val stationsCount = withContext(Dispatchers.IO) {
                        val parser = HarmonicDataParser()
                        file.inputStream().use { parser.parse(it) }.size
                    }
                    statusText.text = getString(R.string.tide_import_loaded, stationsCount)
                } catch (e: Exception) {
                    statusText.text = getString(R.string.tide_import_error_reading)
                }
            }
        } else {
            statusText.text = getString(R.string.tide_import_none)
        }
    }

    /**
     * Executes the import process in the background.
     */
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
}
