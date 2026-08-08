package net.osmand.plus.views.mapwidgets.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.osmand.plus.R
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.settings.fragments.SettingsScreenType

class NauticalManeuversBottomSheet : BaseMaterialBottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.nautical_maneuvers_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val recyclerView = view.findViewById<RecyclerView>(R.id.maneuvers_list)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        val state = NauticalPlugin.engine?.getCurrentState()
        val upwind = state?.windDirectionApparent?.let { kotlin.math.abs(Math.toDegrees(it)) < 90.0 } ?: true
        
        val options = mutableListOf<Pair<String, String>>()
        if (upwind) {
            options.add("tacking" to getString(R.string.nautical_tack))
        } else {
            options.add("gybing" to getString(R.string.nautical_gybe))
        }
        
        options.add("anchoring" to getString(R.string.nautical_maneuver_anchoring))
        options.add("weighing_anchor" to getString(R.string.nautical_maneuver_weighing_anchor))
        options.add("docking" to getString(R.string.nautical_maneuver_docking))
        options.add("mooring" to getString(R.string.nautical_maneuver_mooring))
        options.add("med_mooring" to getString(R.string.nautical_maneuver_med_mooring))
        options.add("heaving_to" to getString(R.string.nautical_maneuver_heaving_to))
        options.add("slip_exit" to getString(R.string.nautical_maneuver_slip_exit))
        options.add("man_overboard" to getString(R.string.nautical_mob_label))

        recyclerView.adapter = object : RecyclerView.Adapter<ManeuverViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ManeuverViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
                return ManeuverViewHolder(v)
            }

            override fun onBindViewHolder(holder: ManeuverViewHolder, position: Int) {
                val item = options[position]
                (holder.itemView as TextView).text = item.second
                holder.itemView.setOnClickListener {
                    val instance = NauticalPlugin.getInstance()
                    val lat = arguments?.getDouble("lat") ?: 0.0
                    val lon = arguments?.getDouble("lon") ?: 0.0

                    when (item.first) {
                        "weighing_anchor" -> {
                            val aLat = settings.NAUTICAL_ANCHOR_LAT.get()
                            val aLon = settings.NAUTICAL_ANCHOR_LON.get()
                            (instance?.maneuverManager?.getManeuverById("weighing_anchor") as? net.osmand.plus.plugins.nautical.maneuvers.WeighingAnchorManeuver)?.setDropPoint(aLat, aLon)
                        }
                        "mooring" -> {
                            (instance?.maneuverManager?.getManeuverById("mooring") as? net.osmand.plus.plugins.nautical.maneuvers.MooringManeuver)?.setTarget(lat, lon)
                        }
                        "med_mooring" -> {
                            (instance?.maneuverManager?.getManeuverById("med_mooring") as? net.osmand.plus.plugins.nautical.maneuvers.MedMooringManeuver)?.setTarget(lat, lon)
                        }
                    }
                    instance?.maneuverManager?.setActiveManeuver(item.first)
                    dismiss()
                }
            }

            override fun getItemCount(): Int = options.size
        }

        view.findViewById<View>(R.id.btn_passage_plan).setOnClickListener {
            net.osmand.plus.settings.fragments.BaseSettingsFragment.showInstance(requireActivity(), SettingsScreenType.NAUTICAL_PASSAGE_PLAN)
            dismiss()
        }
    }

    private class ManeuverViewHolder(view: View) : RecyclerView.ViewHolder(view)

    companion object {
        fun show(fm: androidx.fragment.app.FragmentManager, lat: Double = 0.0, lon: Double = 0.0) {
            val fragment = NauticalManeuversBottomSheet()
            val args = Bundle()
            args.putDouble("lat", lat)
            args.putDouble("lon", lon)
            fragment.arguments = args
            fragment.show(fm, "maneuvers_sheet")
        }
    }
}
