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

        // Composite Light Characteristic if available
        val compositeLight = formatCompositeLightCharacteristic(s57Object.attributes)
        if (!compositeLight.isNullOrEmpty()) {
            addMenuItem("Light Characteristic (Full)", compositeLight)
        }

        s57Object.attributes.forEach { (key, rawValue) ->
            val label = getAttributeLabel(key)
            val decodedValue = decodeAttributeValue(key, rawValue)
            addMenuItem(label, decodedValue)
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
        return when (key.uppercase(Locale.US)) {
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
            "VALNMR", "158" -> "Nominal Range"
            "COLOUR", "75" -> activity.getString(R.string.nautical_s57_color)
            "COLPAT", "76" -> "Colour Pattern"
            "MARSYS", "109" -> activity.getString(R.string.nautical_s57_marks_system)
            "CATSEA", "71" -> activity.getString(R.string.nautical_s57_category_of_sea_area)
            "VALSOU", "159" -> activity.getString(R.string.nautical_s57_sounding_value)
            "BCNSPHP", "69" -> activity.getString(R.string.nautical_s57_beacon_shape)
            "BOYSHP", "70" -> activity.getString(R.string.nautical_s57_buoy_shape)
            "WATLEV", "162" -> "Water Level Effect"
            "CATCAM", "60" -> "Cardinal Mark Category"
            "CATSPM", "67" -> "Special Purpose Category"
            "TOPSHP", "153" -> "Topmark Shape"
            "STATUS", "149" -> "Status"
            "RESTRN", "131" -> "Restriction"
            else -> key
        }
    }

    private fun decodeAttributeValue(key: String, raw: String): String {
        val upperKey = key.uppercase(Locale.US)
        return when (upperKey) {
            "COLOUR", "75" -> decodeColour(raw)
            "LITCHR", "107" -> decodeLightCharacteristic(raw)
            "BOYSHP", "70" -> decodeBuoyShape(raw)
            "BCNSPHP", "69" -> decodeBeaconShape(raw)
            "WATLEV", "162" -> decodeWaterLevelEffect(raw)
            "CATCAM", "60" -> decodeCardinalMark(raw)
            "SIGPER", "143" -> "$raw s"
            "VALNMR", "158" -> "$raw M"
            "HEIGHT", "96" -> "$raw m"
            "VALSOU", "159" -> "$raw m"
            "VALCO", "157" -> "$raw m"
            "DRVAL1", "87" -> "$raw m"
            "DRVAL2", "88" -> "$raw m"
            else -> raw
        }
    }

    companion object {
        fun decodeColour(raw: String): String {
            val parts = raw.split(',', ';', '/').map { it.trim() }
            return parts.mapNotNull { code ->
                when (code) {
                    "1" -> "White"
                    "2" -> "Black"
                    "3" -> "Red"
                    "4" -> "Green"
                    "5" -> "Blue"
                    "6" -> "Yellow"
                    "7" -> "Grey"
                    "8" -> "Brown"
                    "9" -> "Amber"
                    "10" -> "Violet"
                    "11" -> "Orange"
                    "12" -> "Magenta"
                    "13" -> "Pink"
                    else -> code.ifEmpty { null }
                }
            }.joinToString(" / ")
        }

        fun decodeLightCharacteristic(raw: String): String {
            return when (raw.trim()) {
                "1" -> "Fixed (F)"
                "2" -> "Flashing (Fl)"
                "3" -> "Long-flashing (LFl)"
                "4" -> "Quick (Q)"
                "5" -> "Very Quick (VQ)"
                "6" -> "Ultra Quick (UQ)"
                "7" -> "Isophase (Iso)"
                "8" -> "Occulting (Oc)"
                "9" -> "Morse (Mo)"
                "10" -> "Fixed and Flashing (FFl)"
                "11" -> "Alternating (Al)"
                "12" -> "Group Occulting (Oc)"
                else -> raw
            }
        }

        fun decodeBuoyShape(raw: String): String {
            return when (raw.trim()) {
                "1" -> "Conical (Nun/Ogive)"
                "2" -> "Can (Cylindrical)"
                "3" -> "Spherical"
                "4" -> "Pillar"
                "5" -> "Spar (Spindle)"
                "6" -> "Barrel (Tun)"
                "7" -> "Superbuoy"
                "8" -> "Ice Buoy"
                else -> raw
            }
        }

        fun decodeBeaconShape(raw: String): String {
            return when (raw.trim()) {
                "1" -> "Stake / Pole / Post"
                "2" -> "Withy"
                "3" -> "Beacon Tower"
                "4" -> "Lattice Beacon"
                "5" -> "Pile Beacon"
                "6" -> "Cairn"
                "7" -> "Buoyant Beacon"
                else -> raw
            }
        }

        fun decodeWaterLevelEffect(raw: String): String {
            return when (raw.trim()) {
                "1" -> "Partly Submerged"
                "2" -> "Always Dry"
                "3" -> "Always Under Water / Submerged"
                "4" -> "Covers and Uncovers (Dries)"
                "5" -> "Awash"
                "6" -> "Subject to Inundation / Flooding"
                "7" -> "Floating"
                else -> raw
            }
        }

        fun decodeCardinalMark(raw: String): String {
            return when (raw.trim()) {
                "1" -> "North Cardinal"
                "2" -> "East Cardinal"
                "3" -> "South Cardinal"
                "4" -> "West Cardinal"
                else -> raw
            }
        }

        fun formatCompositeLightCharacteristic(attributes: Map<String, String>): String? {
            val litchr = attributes["LITCHR"] ?: attributes["107"] ?: return null
            val siggrp = attributes["SIGGRP"] ?: attributes["142"]
            val colour = attributes["COLOUR"] ?: attributes["75"]
            val sigper = attributes["SIGPER"] ?: attributes["143"]
            val height = attributes["HEIGHT"] ?: attributes["96"]
            val valnmr = attributes["VALNMR"] ?: attributes["158"]

            val sb = StringBuilder()

            // 1. Character acronym
            val chrCode = when (litchr.trim()) {
                "1" -> "F"
                "2" -> "Fl"
                "3" -> "LFl"
                "4" -> "Q"
                "5" -> "VQ"
                "6" -> "UQ"
                "7" -> "Iso"
                "8" -> "Oc"
                "9" -> "Mo"
                "10" -> "FFl"
                "11" -> "Al"
                else -> litchr
            }
            sb.append(chrCode)

            // 2. Signal Group e.g. (2+1) or (3)
            if (!siggrp.isNullOrEmpty()) {
                val cleanGrp = siggrp.trim()
                if (cleanGrp.startsWith("(") && cleanGrp.endsWith(")")) {
                    sb.append(cleanGrp)
                } else if (cleanGrp != "1" && cleanGrp.isNotEmpty()) {
                    sb.append("($cleanGrp)")
                }
            }

            // 3. Colour e.g. W, R, G, Y, Bu
            if (!colour.isNullOrEmpty()) {
                val colCodes = colour.split(',', ';', '/').mapNotNull { c ->
                    when (c.trim()) {
                        "1" -> "W"
                        "2" -> "B"
                        "3" -> "R"
                        "4" -> "G"
                        "5" -> "Bu"
                        "6" -> "Y"
                        "9" -> "Am"
                        "11" -> "Or"
                        else -> null
                    }
                }
                if (colCodes.isNotEmpty()) {
                    sb.append(" ").append(colCodes.joinToString("/"))
                }
            }

            // 4. Signal Period e.g. 10s
            if (!sigper.isNullOrEmpty()) {
                val p = sigper.trim()
                sb.append(" ").append(if (p.endsWith("s", ignoreCase = true)) p else "${p}s")
            }

            // 5. Height e.g. 15m
            if (!height.isNullOrEmpty()) {
                val h = height.trim()
                sb.append(" ").append(if (h.endsWith("m", ignoreCase = true)) h else "${h}m")
            }

            // 6. Range e.g. 12M
            if (!valnmr.isNullOrEmpty()) {
                val r = valnmr.trim()
                sb.append(" ").append(if (r.endsWith("M", ignoreCase = true)) r else "${r}M")
            }

            return sb.toString().trim().ifEmpty { null }
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

