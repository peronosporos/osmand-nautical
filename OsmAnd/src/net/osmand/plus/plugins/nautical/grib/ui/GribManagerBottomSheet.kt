package net.osmand.plus.plugins.nautical.grib.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class GribManagerBottomSheet : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    companion object {
        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            GribManagerBottomSheet().show(fragmentManager, "GribManagerBottomSheet")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_grib_manager, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        recyclerView = view.findViewById(R.id.grib_files_list)
        recyclerView.layoutManager = LinearLayoutManager(context)

        view.findViewById<View>(R.id.btn_import_grib).setOnClickListener {
            importGrib()
        }

        refreshList()
    }

    private fun refreshList() {
        viewLifecycleOwner.lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                val app = activity?.application as? OsmandApplication
                val gribDir = app?.getAppPath("nautical/grib")
                gribDir?.listFiles()?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
            }
            recyclerView.adapter = GribAdapter(files)
        }
    }

    private fun importGrib() {
        // Here we would launch a file picker
        android.util.Log.d("GribBottomSheet", "Import GRIB requested")
    }

    inner class GribAdapter(private val items: List<File>) : RecyclerView.Adapter<GribViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GribViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
            return GribViewHolder(v)
        }

        override fun onBindViewHolder(holder: GribViewHolder, position: Int) {
            val file = items[position]
            holder.title.text = file.name
            val dateStr = sdf.format(Date(file.lastModified()))
            val sizeKb = file.length() / 1024
            holder.subtitle.text = getString(R.string.grib_file_info_format, dateStr, sizeKb)
            
            holder.itemView.setOnClickListener {
                loadGribFile(file)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private fun loadGribFile(file: File) {
        val repo = net.osmand.plus.plugins.nautical.di.SailingDependencyContainer.gribRepository
        file.inputStream().use { repo?.loadGrib(it) }
        dismiss()
    }

    class GribViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(android.R.id.text1)
        val subtitle: TextView = v.findViewById(android.R.id.text2)
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.let { sheetDialog ->
            sheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { bottomSheet ->
                val behavior = BottomSheetBehavior.from(bottomSheet)
                val metrics = resources.displayMetrics
                behavior.maxHeight = (metrics.heightPixels * 0.6).toInt()
                behavior.peekHeight = (metrics.heightPixels * 0.4).toInt()
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }
}
