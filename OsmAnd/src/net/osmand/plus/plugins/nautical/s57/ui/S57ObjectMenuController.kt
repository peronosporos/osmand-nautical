package net.osmand.plus.plugins.nautical.s57.ui

import net.osmand.data.LatLon
import net.osmand.data.PointDescription
import net.osmand.plus.OsmandApplication
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

    private val app: OsmandApplication = builder.application

    override fun getRightIconId(): Int = R.drawable.ic_plugin_nautical_map

    override fun isBigRightIcon(): Boolean = true

    override fun addPlainMenuItems(typeStr: String?, pointDescription: PointDescription?, latLon: LatLon?) {
        addMenuItem("Acronym", s57Object.acronym)
        
        s57Object.attributes.forEach { (key, value) ->
            val label = getAttributeLabel(key)
            addMenuItem(label, value)
        }

        // Add geometry info if relevant
        val geo = s57Object.geometries.firstOrNull()
        if (geo is S57Geometry.Point && geo.depth != null) {
            addMenuItem("Depth", String.format(Locale.US, "%.1f m", geo.depth))
        }

        super.addPlainMenuItems(typeStr, pointDescription, latLon)
    }

    private fun addMenuItem(type: String, value: String?) {
        if (!value.isNullOrEmpty()) {
            addPlainMenuItem(0, value, type, null, false, false, null)
        }
    }

    private fun getAttributeLabel(key: String): String {
        return when (key) {
            "OBJNAM", "116" -> "Name"
            "NOBJNM", "111" -> "Name (Local)"
            "INFORM", "102" -> "Information"
            "NINFOM", "112" -> "Information (Local)"
            "VALCO", "157" -> "Value of Contour"
            "DRVAL1", "87" -> "Minimum Depth"
            "DRVAL2", "88" -> "Maximum Depth"
            "HEIGHT", "96" -> "Height"
            "LITCHR", "107" -> "Light Characteristic"
            "SIGPER", "143" -> "Signal Period"
            "SIGGRP", "142" -> "Signal Group"
            "COLOUR", "75" -> "Color"
            "MARSYS", "109" -> "Marks System"
            "CATSEA", "71" -> "Category of Sea Area"
            "VALSOU", "159" -> "Sounding Value"
            "BCNSPHP", "69" -> "Beacon Shape"
            "BOYSHP", "70" -> "Buoy Shape"
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
