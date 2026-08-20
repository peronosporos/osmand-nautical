package net.osmand.plus.plugins.nautical.ui.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.osmand.plus.R
import net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.settings.enums.VesselType
import net.osmand.plus.settings.fragments.SettingsScreenType

class NauticalManeuversBottomSheet : BaseNauticalBottomSheet() {

    override fun createMenuItems(savedInstanceState: Bundle?) {
        addTitleItem(getString(R.string.nautical_maneuver_menu))

        val themedCtx = net.osmand.plus.utils.UiUtilities.getThemedContext(requireContext(), nightMode)
        val customView = LayoutInflater.from(themedCtx).inflate(R.layout.nautical_maneuvers_bottom_sheet, null)
        
        val recyclerView = customView.findViewById<RecyclerView>(R.id.maneuvers_list)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        val btnExecute = customView.findViewById<android.widget.Button>(R.id.btn_execute)
        val txtInstructions = customView.findViewById<TextView>(R.id.txt_maneuver_instructions)
        
        setupParams(customView)

        val state = NauticalPlugin.engine?.getCurrentState()
        val awaDeg = state?.windDirectionApparent?.let { kotlin.math.abs(Math.toDegrees(it)) } ?: 0.0
        val isProa = settings.NAUTICAL_VESSEL_TYPE.get() == VesselType.PROA
        
        val options = mutableListOf<ManeuverOption>()
        if (isProa) {
            options.add(ManeuverOption("shunting", getString(R.string.nautical_shunt), R.drawable.ic_action_sail_boat_dark))
        } else {
            // Beam Reach logic (Item 10 fix): Show both if near 90 deg AWA
            if (awaDeg < 110.0) {
                options.add(ManeuverOption("tacking", getString(R.string.nautical_tack), R.drawable.ic_action_sail_boat_dark))
            }
            if (awaDeg > 70.0) {
                options.add(ManeuverOption("gybing", getString(R.string.nautical_gybe), R.drawable.ic_action_sail_boat_dark))
            }
        }
        
        options.add(ManeuverOption("anchoring", getString(R.string.nautical_maneuver_anchoring), R.drawable.ic_action_anchor))
        if (settings.NAUTICAL_ANCHOR_LAT.get() != 0.0) {
            options.add(ManeuverOption("weighing_anchor", getString(R.string.nautical_maneuver_weighing_anchor), R.drawable.ic_action_anchor))
        }
        options.add(ManeuverOption("docking", getString(R.string.nautical_maneuver_docking), R.drawable.ic_action_building))
        options.add(ManeuverOption("mooring", getString(R.string.nautical_maneuver_mooring), R.drawable.ic_action_anchor))
        options.add(ManeuverOption("med_mooring", getString(R.string.nautical_maneuver_med_mooring), R.drawable.ic_action_anchor))
        options.add(ManeuverOption("slip_exit", getString(R.string.nautical_maneuver_slip_exit), R.drawable.ic_action_building))
        options.add(ManeuverOption("heaving_to", getString(R.string.nautical_maneuver_heaving_to), R.drawable.ic_action_sail_boat_dark))

        recyclerView.adapter = ManeuverAdapter(options) { item ->
            val instance = NauticalPlugin.getInstance()
            val lat = arguments?.getDouble("lat") ?: 0.0
            val lon = arguments?.getDouble("lon") ?: 0.0
            
            // Item 12 Fix: Transition to Armed state within the sheet instead of immediate dismissal
            when (item.id) {
                "weighing_anchor" -> {
                    val aLat = settings.NAUTICAL_ANCHOR_LAT.get()
                    val aLon = settings.NAUTICAL_ANCHOR_LON.get()
                    (instance?.maneuverManager?.getManeuverById("weighing_anchor") as? net.osmand.plus.plugins.nautical.maneuvers.WeighingAnchorManeuver)?.setDropPoint(aLat, aLon)
                }
                "mooring" -> (instance?.maneuverManager?.getManeuverById("mooring") as? net.osmand.plus.plugins.nautical.maneuvers.MooringManeuver)?.setTarget(lat, lon)
                "med_mooring" -> (instance?.maneuverManager?.getManeuverById("med_mooring") as? net.osmand.plus.plugins.nautical.maneuvers.MedMooringManeuver)?.setTarget(lat, lon)
                "docking" -> {
                    (instance?.maneuverManager?.getManeuverById("docking") as? net.osmand.plus.plugins.nautical.maneuvers.DockingManeuver)?.let { dm ->
                        dm.setTarget(lat, lon)
                        dm.vesselLength = settings.NAUTICAL_MED_MOORING_VESSEL_LENGTH.get()
                    }
                }
            }
            instance?.maneuverManager?.setActiveManeuver(item.id)
            
            // UI Update for Armed state
            btnExecute.visibility = View.VISIBLE
            txtInstructions.visibility = View.VISIBLE
            txtInstructions.text = getString(R.string.nautical_maneuver_armed_instructions, item.name)
            
            // Re-run setupParams to update labels for armed state
            setupParams(customView)
        }

        btnExecute.setOnClickListener {
            NauticalPlugin.getInstance()?.maneuverManager?.execute()
            dismiss()
        }

        customView.findViewById<View>(R.id.btn_passage_plan).setOnClickListener {
            net.osmand.plus.settings.fragments.BaseSettingsFragment.showInstance(requireActivity(), SettingsScreenType.NAUTICAL_PASSAGE_PLAN)
            dismiss()
        }

        items.add(BaseBottomSheetItem.Builder().setCustomView(customView).create())
    }

    private fun setupParams(view: View) {
        val txtLength = view.findViewById<TextView>(R.id.txt_vessel_length)
        val txtScope = view.findViewById<TextView>(R.id.txt_anchor_scope)
        val txtDepth = view.findViewById<TextView>(R.id.txt_anchor_depth)
        val txtTide = view.findViewById<TextView>(R.id.txt_anchor_tide)
        val txtBow = view.findViewById<TextView>(R.id.txt_bow_offset)
        val txtCalculatedRode = view.findViewById<TextView>(R.id.txt_calculated_rode)

        fun updateLabels() {
            txtLength.text = getString(R.string.nautical_vessel_length_format, settings.NAUTICAL_MED_MOORING_VESSEL_LENGTH.get())
            txtScope.text = getString(R.string.nautical_anchor_scope_format, settings.NAUTICAL_ANCHOR_SCOPE_RATIO.get().toInt())
            
            val depth = settings.NAUTICAL_ANCHOR_DEPTH.get().toDouble()
            val tide = settings.NAUTICAL_ANCHOR_TIDE_RISE.get().toDouble()
            val freeboard = settings.NAUTICAL_ANCHOR_FREEBOARD.get().toDouble()
            val scope = settings.NAUTICAL_ANCHOR_SCOPE_RATIO.get().toDouble()
            
            txtDepth.text = getString(R.string.nautical_vessel_length_format, depth.toFloat())
            txtTide.text = getString(R.string.nautical_vessel_length_format, tide.toFloat())
            txtBow.text = getString(R.string.nautical_vessel_length_format, settings.NAUTICAL_ANCHOR_BOW_OFFSET.get())
            
            val rode = (depth + tide + freeboard) * scope
            txtCalculatedRode.text = getString(R.string.nautical_calculated_rode, rode)

            val mm = NauticalPlugin.getInstance()?.maneuverManager
            if (mm?.state == net.osmand.plus.plugins.nautical.maneuvers.ManeuverState.ARMED) {
                (mm.activeManeuver as? net.osmand.plus.plugins.nautical.maneuvers.MedMooringManeuver)?.let { mmm ->
                    val state = NauticalPlugin.engine?.getCurrentState()
                    val curDepth = (state?.depthBelowTransducer ?: 5.0) + (state?.depthSurfaceToTransducer ?: 1.0)
                    val dropDistance = (curDepth * settings.NAUTICAL_MED_MOORING_SCOPE.get()) + settings.NAUTICAL_MED_MOORING_VESSEL_LENGTH.get()
                    mmm.speak(getString(R.string.nautical_med_mooring_armed_msg, dropDistance.toInt()))
                }
                (mm.activeManeuver as? net.osmand.plus.plugins.nautical.maneuvers.DockingManeuver)?.let { dm ->
                    dm.vesselLength = settings.NAUTICAL_MED_MOORING_VESSEL_LENGTH.get()
                }
            }
        }

        updateLabels()

        view.findViewById<View>(R.id.btn_length_minus)?.setOnClickListener {
            val current = settings.NAUTICAL_MED_MOORING_VESSEL_LENGTH.get()
            if (current > 1.0f) {
                settings.NAUTICAL_MED_MOORING_VESSEL_LENGTH.set((current - 0.5f).coerceAtLeast(1.0f))
                updateLabels()
            }
        }
        view.findViewById<View>(R.id.btn_length_plus)?.setOnClickListener {
            val current = settings.NAUTICAL_MED_MOORING_VESSEL_LENGTH.get()
            settings.NAUTICAL_MED_MOORING_VESSEL_LENGTH.set(current + 0.5f)
            updateLabels()
        }
        view.findViewById<View>(R.id.btn_scope_minus)?.setOnClickListener {
            val current = settings.NAUTICAL_ANCHOR_SCOPE_RATIO.get()
            if (current > 1.0f) {
                settings.NAUTICAL_ANCHOR_SCOPE_RATIO.set((current - 1.0f).coerceAtLeast(1.0f))
                updateLabels()
            }
        }
        view.findViewById<View>(R.id.btn_scope_plus)?.setOnClickListener {
            val current = settings.NAUTICAL_ANCHOR_SCOPE_RATIO.get()
            settings.NAUTICAL_ANCHOR_SCOPE_RATIO.set(current + 1.0f)
            updateLabels()
        }
        
        view.findViewById<View>(R.id.btn_depth_minus)?.setOnClickListener {
            val current = settings.NAUTICAL_ANCHOR_DEPTH.get()
            if (current > 0.5f) {
                settings.NAUTICAL_ANCHOR_DEPTH.set((current - 0.5f).coerceAtLeast(0.5f))
                updateLabels()
            }
        }
        view.findViewById<View>(R.id.btn_depth_plus)?.setOnClickListener {
            val current = settings.NAUTICAL_ANCHOR_DEPTH.get()
            settings.NAUTICAL_ANCHOR_DEPTH.set(current + 0.5f)
            updateLabels()
        }
        
        view.findViewById<View>(R.id.btn_tide_minus)?.setOnClickListener {
            val current = settings.NAUTICAL_ANCHOR_TIDE_RISE.get()
            settings.NAUTICAL_ANCHOR_TIDE_RISE.set(current - 0.5f)
            updateLabels()
        }
        view.findViewById<View>(R.id.btn_tide_plus)?.setOnClickListener {
            val current = settings.NAUTICAL_ANCHOR_TIDE_RISE.get()
            settings.NAUTICAL_ANCHOR_TIDE_RISE.set(current + 0.5f)
            updateLabels()
        }

        // Item 17: GPS Bow Offset logic
        view.findViewById<View>(R.id.btn_bow_minus)?.setOnClickListener {
            val current = settings.NAUTICAL_ANCHOR_BOW_OFFSET.get()
            if (current > 0.0f) {
                settings.NAUTICAL_ANCHOR_BOW_OFFSET.set((current - 0.5f).coerceAtLeast(0.0f))
                updateLabels()
            }
        }
        view.findViewById<View>(R.id.btn_bow_plus)?.setOnClickListener {
            val current = settings.NAUTICAL_ANCHOR_BOW_OFFSET.get()
            settings.NAUTICAL_ANCHOR_BOW_OFFSET.set(current + 0.5f)
            updateLabels()
        }
    }

    private data class ManeuverOption(val id: String, val name: String, val icon: Int)

    private class ManeuverAdapter(private val items: List<ManeuverOption>, private val onClick: (ManeuverOption) -> Unit) : RecyclerView.Adapter<ManeuverViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ManeuverViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_maneuver, parent, false)
            return ManeuverViewHolder(v)
        }
        override fun onBindViewHolder(holder: ManeuverViewHolder, position: Int) {
            val item = items[position]
            holder.name.text = item.name
            holder.icon.setImageResource(item.icon)
            holder.itemView.setOnClickListener { onClick(item) }
        }
        override fun getItemCount(): Int = items.size
    }

    private class ManeuverViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.maneuver_name)
        val icon: ImageView = view.findViewById(R.id.maneuver_icon)
    }

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
