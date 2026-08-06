package net.osmand.plus.plugins.nautical.s57.ui

import net.osmand.data.LatLon
import net.osmand.data.PointDescription
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.mapcontextmenu.MenuBuilder
import net.osmand.plus.mapcontextmenu.MenuController
import net.osmand.plus.plugins.nautical.s57.S57Object
import net.osmand.plus.plugins.nautical.s57.S57Geometry
import java.util.Locale

class S57ObjectMenuController(
    mapActivity: MapActivity,
    pointDescription: PointDescription,
    private var s57Object: S57Object
) : MenuController(MenuBuilder(mapActivity), pointDescription, mapActivity) {

    override fun getRightIconId(): Int = R.drawable.ic_plugin_nautical_map

    override fun isBigRightIcon(): Boolean = true

    override fun addPlainMenuItems(typeStr: String?, pointDescription: PointDescription?, latLon: LatLon?) {
        val activity = mapActivity ?: return
        addMenuItem(activity.getString(R.string.nautical_s57_acronym), s57Object.acronym)
        
        s57Object.attributes.forEach { (key, value) ->
            val label = getAttributeLabel(key)
            addMenuItem(label, value)
        }

        // Add geometry info if relevant
        val geo = s57Object.geometries.firstOrNull()
        if (geo is S57Geometry.Point && geo.depth != null) {
            addMenuItem(activity.getString(R.string.nautical_s57_depth_label), String.format(Locale.US, "%.1f m", geo.depth))
        }

        super.addPlainMenuItems(typeStr, pointDescription, latLon)
    }

    private fun addMenuItem(type: String, value: String?) {
        if (!value.isNullOrEmpty()) {
            addPlainMenuItem(0, value, type, null, false, false, null)
        }
    }

    private fun getAttributeLabel(key: String): String {
        val activity = mapActivity ?: return key
        return when (key) {
            "OBJNAM", "116" -> activity.getString(R.string.nautical_s57_name)
            "NOBJNM", "111" -> activity.getString(R.string.nautical_s57_name_local)
            "INFORM", "102" -> activity.getString(R.string.nautical_s57_information)
            "NINFOM", "112" -> activity.getString(R.string.nautical_s57_information_local)
            "VALCO", "157" -> activity.getString(R.string.nautical_s57_value_of_contour)
            "DRVAL1", "87" -> activity.getString(R.string.nautical_s57_min_depth)
            "DRVAL2", "88" -> activity.getString(R.string.nautical_s57_max_depth)
            "HEIGHT", "96" -> activity.getString(R.string.nautical_s57_height)
            "LITCHR", "107" -> activity.getString(R.string.nautical_s57_light_characteristic)
            "SIGPER", "143" -> activity.getString(R.string.nautical_s57_signal_period)
            "SIGGRP", "142" -> activity.getString(R.string.nautical_s57_signal_group)
            "COLOUR", "75" -> activity.getString(R.string.nautical_s57_color)
            "MARSYS", "109" -> activity.getString(R.string.nautical_s57_marks_system)
            "CATSEA", "71" -> activity.getString(R.string.nautical_s57_category_of_sea_area)
            "VALSOU", "159" -> activity.getString(R.string.nautical_s57_sounding_value)
            "BCNSPHP", "69" -> activity.getString(R.string.nautical_s57_beacon_shape)
            "BOYSHP", "70" -> activity.getString(R.string.nautical_s57_buoy_shape)
            else -> key
        }
    }



    override fun setObject(`object`: Any?) {
        if (`object` is S57Object) {
            this.s57Object = `object`
        }
    }

    override fun getObject(): Any = s57Object

    override fun getTypeStr(): String = "S-57 ${s57Object.acronym}"

    override fun needStreetName(): Boolean = false
}
