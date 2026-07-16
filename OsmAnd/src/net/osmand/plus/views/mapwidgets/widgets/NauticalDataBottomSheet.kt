package net.osmand.plus.views.mapwidgets.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.views.mapwidgets.WidgetType

class NauticalDataBottomSheet : BottomSheetDialogFragment() {

    private var type: WidgetType? = null
    private var graph: NauticalGraphView? = null
    private var myListener: ((MarineState) -> Unit)? = null

    companion object {
        private const val WIDGET_TYPE = "widget_type"

        @JvmStatic
        fun newInstance(type: WidgetType): NauticalDataBottomSheet {
            val fragment = NauticalDataBottomSheet()
            val args = Bundle()
            args.putSerializable(WIDGET_TYPE, type)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        type = if (android.os.Build.VERSION.SDK_INT >= 33) {
            arguments?.getSerializable(WIDGET_TYPE, WidgetType::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getSerializable(WIDGET_TYPE) as? WidgetType
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_nautical_data, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val titleView = view.findViewById<TextView>(R.id.graph_title)
        graph = view.findViewById(R.id.graph_view)

        if (type == null) {
            dismiss()
            return
        }

        titleView?.text = when (type) {
            WidgetType.NAUTICAL_DEPTH -> getString(R.string.nautical_title_depth)
            WidgetType.NAUTICAL_WIND -> getString(R.string.nautical_title_wind)
            WidgetType.NAUTICAL_VMG -> getString(R.string.nautical_title_vmg)
            WidgetType.NAUTICAL_COG -> getString(R.string.nautical_widget_cog_label)
            else -> getString(R.string.nautical_title_telemetry)
        }
    }

    override fun onStart() {
        super.onStart()

        myListener = { _ ->
            view?.post {
                if (!isAdded) return@post
                updateGraphData()
            }
        }

        myListener?.let { NauticalPlugin.engine?.registerListener(it) }
    }

    override fun onStop() {
        myListener?.let { NauticalPlugin.engine?.unregisterListener(it) }
        myListener = null
        super.onStop()
    }

    private fun updateGraphData() {
        val engine = NauticalPlugin.engine ?: return
        val g = graph ?: return

        when (type) {
            WidgetType.NAUTICAL_DEPTH -> g.setData(engine.getDepthHistory(), getString(R.string.nautical_unit_meters))
            WidgetType.NAUTICAL_WIND -> g.setData(engine.getWindHistory(), getString(R.string.nautical_unit_knots))
            WidgetType.NAUTICAL_VMG -> g.setData(engine.getVmgHistory().map { it * 1.94384 }, getString(R.string.nautical_unit_knots))
            WidgetType.NAUTICAL_COG -> g.setData(engine.getCogHistory().map { Math.toDegrees(it) }, "°")
            else -> {}
        }
    }
}
