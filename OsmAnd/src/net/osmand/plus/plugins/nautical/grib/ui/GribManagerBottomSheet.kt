package net.osmand.plus.plugins.nautical.grib.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.grib.repository.GribStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class GribManagerBottomSheet : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorMsg: TextView
    private lateinit var isobarStepSpinner: android.widget.Spinner
    private lateinit var waveToSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleImportedUri(it) }
    }

    companion object {
        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            GribManagerBottomSheet().show(fragmentManager, "GribManagerBottomSheet")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val app = (activity?.application as? net.osmand.plus.OsmandApplication)
        val night = app?.daynightHelper?.isNightMode(net.osmand.plus.settings.enums.ThemeUsageContext.APP) ?: false
        val themedCtx = net.osmand.plus.utils.UiUtilities.getThemedContext(requireContext(), night)
        return LayoutInflater.from(themedCtx).inflate(R.layout.bottom_sheet_grib_manager, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        recyclerView = view.findViewById(R.id.grib_files_list)
        progressBar = view.findViewById(R.id.grib_loading_progress)
        errorMsg = view.findViewById(R.id.grib_error_msg)
        isobarStepSpinner = view.findViewById(R.id.isobar_step_spinner)
        waveToSwitch = view.findViewById(R.id.wave_direction_to_switch)
        
        recyclerView.layoutManager = LinearLayoutManager(context)

        view.findViewById<View>(R.id.btn_sync_signalk_grib)?.setOnClickListener {
            val rest = net.osmand.plus.plugins.nautical.NauticalPlugin.engine?.getRestService()
            val repo = SailingDependencyContainer.gribRepository
            if (rest != null && repo != null) {
                repo.fetchFromSignalK(rest, "signalk-grib-weather-provider")
            } else {
                val app = activity?.application as? OsmandApplication
                app?.showToastMessage(R.string.nautical_offline_status)
            }
        }

        view.findViewById<View>(R.id.btn_import_grib)?.setOnClickListener {
            importLauncher.launch("*/*")
        }

        setupSettings()
        observeStatus()
        refreshList()
    }

    private fun setupSettings() {
        val app = activity?.application as? OsmandApplication ?: return
        val settings = app.settings
        
        val steps = arrayOf(1, 2, 4)
        val adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, steps)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        isobarStepSpinner.adapter = adapter
        
        val currentStep = settings.NAUTICAL_GRIB_ISOBAR_STEP.get()
        isobarStepSpinner.setSelection(steps.indexOf(currentStep).coerceAtLeast(0))
        
        isobarStepSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                settings.NAUTICAL_GRIB_ISOBAR_STEP.set(steps[position])
                app.osmandMap?.refreshMap()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        
        waveToSwitch.isChecked = settings.NAUTICAL_GRIB_WAVE_DIRECTION_TO.get()
        waveToSwitch.setOnCheckedChangeListener { _, isChecked ->
            settings.NAUTICAL_GRIB_WAVE_DIRECTION_TO.set(isChecked)
            app.osmandMap?.refreshMap()
        }
    }

    private fun observeStatus() {
        val repo = SailingDependencyContainer.gribRepository ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            repo.status.collect { status ->
                progressBar.visibility = if (status == GribStatus.LOADING) View.VISIBLE else View.GONE
                errorMsg.visibility = if (status == GribStatus.ERROR || status == GribStatus.UNSUPPORTED_EDITION) View.VISIBLE else View.GONE
                
                when (status) {
                    GribStatus.ERROR -> errorMsg.text = getString(R.string.grib_import_error)
                    GribStatus.UNSUPPORTED_EDITION -> errorMsg.text = getString(R.string.nautical_grib_unsupported_edition)
                    GribStatus.READY -> {
                        refreshList()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun refreshList() {
        viewLifecycleOwner.lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                val app = activity?.application as? OsmandApplication
                val gribDir = app?.getAppPath(net.osmand.plus.plugins.nautical.grib.repository.GribRepository.GRIB_DIR)
                gribDir?.listFiles()?.asSequence()?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
            }
            recyclerView.adapter = GribAdapter(files)
        }
    }

    private fun handleImportedUri(uri: android.net.Uri) {
        val app = activity?.application as? OsmandApplication ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val gribDir = app.getAppPath(net.osmand.plus.plugins.nautical.grib.repository.GribRepository.GRIB_DIR)
                if (!gribDir.exists()) gribDir.mkdirs()
                
                val fileName = "imported_${System.currentTimeMillis()}.grb2"
                val destFile = File(gribDir, fileName)
                
                val bytes = context?.contentResolver?.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                    destFile.readBytes()
                } ?: throw Exception("Failed to read imported file")
                
                withContext(Dispatchers.Main) {
                    refreshList()
                    val repo = SailingDependencyContainer.gribRepository
                    repo?.loadGrib(bytes)
                    repo?.gridData?.fileName = destFile.name
                }
            } catch (e: Exception) {
                android.util.Log.e("GribBottomSheet", "Failed to handle imported URI", e)
                withContext(Dispatchers.Main) {
                    errorMsg.visibility = View.VISIBLE
                    errorMsg.text = getString(R.string.grib_import_error)
                }
            }
        }
    }

    inner class GribAdapter(private val items: List<File>) : RecyclerView.Adapter<GribViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GribViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.list_item_grib_file, parent, false)
            return GribViewHolder(v)
        }

        override fun onBindViewHolder(holder: GribViewHolder, position: Int) {
            val file = items[position]
            holder.title.text = file.name
            
            val activeGribFile = SailingDependencyContainer.gribRepository?.gridData?.fileName
            val isActive = file.name == activeGribFile
            holder.activeIndicator.visibility = if (isActive) View.VISIBLE else View.GONE
            holder.itemView.setBackgroundColor(if (isActive) 0x1A2196F3.toInt() else 0)

            holder.metadataJob?.cancel()
            holder.metadataJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val dateStr = sdf.format(Date(file.lastModified()))
                val sizeKb = file.length() / 1024
                val info = getString(R.string.grib_file_info_format, dateStr, sizeKb)
                withContext(Dispatchers.Main) {
                    holder.subtitle.text = info
                }
            }
            
            holder.itemView.setOnClickListener {
                loadGribFile(file)
            }
            
            holder.deleteBtn.setOnClickListener {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(R.string.shared_string_delete)
                    .setMessage(getString(R.string.nautical_delete_grib_confirm, file.name))
                    .setPositiveButton(R.string.shared_string_yes) { _, _ ->
                        file.delete()
                        refreshList()
                    }
                    .setNegativeButton(R.string.shared_string_no, null)
                    .show()
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private fun loadGribFile(file: File) {
        val repo = SailingDependencyContainer.gribRepository
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bytes = file.readBytes()
                withContext(Dispatchers.Main) {
                    repo?.loadGrib(bytes)
                    repo?.gridData?.fileName = file.name
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMsg.visibility = View.VISIBLE
                    errorMsg.text = getString(R.string.grib_import_error)
                }
            }
        }
    }

    class GribViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.grib_file_title)
        val subtitle: TextView = v.findViewById(R.id.grib_file_subtitle)
        val activeIndicator: View = v.findViewById(R.id.grib_active_indicator)
        val deleteBtn: View = v.findViewById(R.id.btn_delete_grib)
        var metadataJob: kotlinx.coroutines.Job? = null
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.let { sheetDialog ->
            sheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { bottomSheet ->
                val behavior = BottomSheetBehavior.from(bottomSheet)
                val metrics = resources.displayMetrics
                behavior.maxHeight = (metrics.heightPixels * 0.8).toInt()
                behavior.peekHeight = (metrics.heightPixels * 0.5).toInt()
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }
}
