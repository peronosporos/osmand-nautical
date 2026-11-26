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
        addMenuItem(activity.getString(R.string.nautical_s57_acronym), s57Object.acronym, "ACRONYM")

        // Composite Light Characteristic if available
        val compositeLight = formatCompositeLightCharacteristic(s57Object.attributes)
        if (!compositeLight.isNullOrEmpty()) {
            addMenuItem(activity.getString(R.string.nautical_s57_light_char_full), compositeLight, "LITCHR")
        }

        s57Object.attributes.forEach { (key, rawValue) ->
            val label = getAttributeLabel(key)
            val decodedValue = decodeAttributeValue(key, rawValue)
            addMenuItem(label, decodedValue, key)
        }

        // Add geometry info if relevant
        val geo = s57Object.geometries.firstOrNull()
        if (geo is S57Geometry.Point && geo.depth != null) {
            addMenuItem(activity.getString(R.string.nautical_s57_depth_label), String.format(Locale.US, "%.1f m", geo.depth), "VALSOU")
        }

        super.addPlainMenuItems(typeStr, pointDescription, latLon)
    }

    private fun addMenuItem(type: String, value: String?, key: String = "") {
        if (!value.isNullOrEmpty()) {
            val iconId = when (key.uppercase(Locale.US)) {
                "VALSOU", "DRVAL1", "DRVAL2", "VALDCO", "HEIGHT" -> R.drawable.ic_action_ruler
                "RESTRN", "WATLEV" -> R.drawable.ic_action_alert
                else -> R.drawable.ic_action_info_dark
            }
            addPlainMenuItem(iconId, value, type, null, false, false, null)
        }
    }

    private fun getAttributeLabel(key: String): String {
        val activity = mapActivity ?: return key
        return when (key.uppercase(Locale.US)) {
            "OBJNAM" -> activity.getString(R.string.nautical_s57_name)
            "NOBJNM" -> activity.getString(R.string.nautical_s57_name_local)
            "INFORM" -> activity.getString(R.string.nautical_s57_information)
            "NINFOM" -> activity.getString(R.string.nautical_s57_information_local)
            "VALDCO" -> activity.getString(R.string.nautical_s57_value_of_contour)
            "DRVAL1" -> activity.getString(R.string.nautical_s57_min_depth)
            "DRVAL2" -> activity.getString(R.string.nautical_s57_max_depth)
            "HEIGHT" -> activity.getString(R.string.nautical_s57_height)
            "LITCHR" -> activity.getString(R.string.nautical_s57_light_characteristic)
            "SIGPER" -> activity.getString(R.string.nautical_s57_signal_period)
            "SIGGRP" -> activity.getString(R.string.nautical_s57_signal_group)
            "VALNMR" -> activity.getString(R.string.nautical_s57_nominal_range)
            "COLOUR" -> activity.getString(R.string.nautical_s57_color)
            "COLPAT" -> activity.getString(R.string.nautical_s57_colour_pattern)
            "MARSYS" -> activity.getString(R.string.nautical_s57_marks_system)
            "CATSEA" -> activity.getString(R.string.nautical_s57_category_of_sea_area)
            "VALSOU" -> activity.getString(R.string.nautical_s57_sounding_value)
            "BCNSHP" -> activity.getString(R.string.nautical_s57_beacon_shape)
            "BOYSHP" -> activity.getString(R.string.nautical_s57_buoy_shape)
            "WATLEV" -> activity.getString(R.string.nautical_s57_water_level_effect)
            "CATCAM" -> activity.getString(R.string.nautical_s57_cardinal_mark_category)
            "CATSPM" -> activity.getString(R.string.nautical_s57_special_purpose_category)
            "TOPSHP" -> activity.getString(R.string.nautical_s57_topmark_shape)
            "STATUS" -> activity.getString(R.string.nautical_s57_status)
            "RESTRN" -> activity.getString(R.string.nautical_s57_restriction)
            else -> key
        }
    }

    private fun decodeAttributeValue(key: String, raw: String): String {
        val upperKey = key.uppercase(Locale.US)
        return when (upperKey) {
            "COLOUR" -> decodeColour(raw)
            "LITCHR" -> decodeLightCharacteristic(raw)
            "BOYSHP" -> decodeBuoyShape(raw)
            "BCNSHP" -> decodeBeaconShape(raw)
            "WATLEV" -> decodeWaterLevelEffect(raw)
            "CATCAM" -> decodeCardinalMark(raw)
            "COLPAT" -> decodeColourPattern(raw)
            "SIGPER" -> "$raw s"
            "VALNMR" -> "$raw M"
            "HEIGHT" -> "$raw m"
            "VALSOU" -> "$raw m"
            "VALDCO" -> "$raw m"
            "DRVAL1" -> "$raw m"
            "DRVAL2" -> "$raw m"
            else -> raw
        }
    }

    // Enum decoders (S-57 Ed 3.1 attribute enumerations; verified against
    // the IHO S-57 Appendix A Chapter 2 attribute catalogue). Unknown or
    // deprecated codes pass through their raw value instead of fabricating
    // a meaning.
    private fun decodeColour(raw: String): String {
        val activity = mapActivity ?: return raw
        val parts = raw.split(',', ';', '/').map { it.trim() }
        return parts.mapNotNull { code ->
            when (code) {
                "1" -> activity.getString(R.string.nautical_s57_colour_white)
                "2" -> activity.getString(R.string.nautical_s57_colour_black)
                "3" -> activity.getString(R.string.nautical_s57_colour_red)
                "4" -> activity.getString(R.string.nautical_s57_colour_green)
                "5" -> activity.getString(R.string.nautical_s57_colour_blue)
                "6" -> activity.getString(R.string.nautical_s57_colour_yellow)
                "7" -> activity.getString(R.string.nautical_s57_colour_grey)
                "8" -> activity.getString(R.string.nautical_s57_colour_brown)
                "9" -> activity.getString(R.string.nautical_s57_colour_amber)
                "10" -> activity.getString(R.string.nautical_s57_colour_violet)
                "11" -> activity.getString(R.string.nautical_s57_colour_orange)
                "12" -> activity.getString(R.string.nautical_s57_colour_magenta)
                "13" -> activity.getString(R.string.nautical_s57_colour_pink)
                else -> code.ifEmpty { null }
            }
        }.joinToString(" / ")
    }

    private fun decodeLightCharacteristic(raw: String): String {
        val activity = mapActivity
        val word = when (raw.trim()) {
            "1" -> activity?.getString(R.string.nautical_s57_litchr_fixed)
            "2" -> activity?.getString(R.string.nautical_s57_litchr_flashing)
            "3" -> activity?.getString(R.string.nautical_s57_litchr_long_flashing)
            "4" -> activity?.getString(R.string.nautical_s57_litchr_quick_flashing)
            "5" -> activity?.getString(R.string.nautical_s57_litchr_very_quick_flashing)
            "6" -> activity?.getString(R.string.nautical_s57_litchr_ultra_quick_flashing)
            "7" -> activity?.getString(R.string.nautical_s57_litchr_isophased)
            "8" -> activity?.getString(R.string.nautical_s57_litchr_occulting)
            "9" -> activity?.getString(R.string.nautical_s57_litchr_interrupted_quick)
            "10" -> activity?.getString(R.string.nautical_s57_litchr_interrupted_very_quick)
            "11" -> activity?.getString(R.string.nautical_s57_litchr_interrupted_ultra_quick)
            "12" -> activity?.getString(R.string.nautical_s57_litchr_morse)
            "28" -> activity?.getString(R.string.nautical_s57_litchr_alternating)
            else -> null
        } ?: return raw
        // Compose word + canonical INT-1 abbreviation, e.g. "Flashing (Fl)"
        val abbr = lightAbbreviation(raw.trim())
        return if (abbr != null) "$word ($abbr)" else word
    }

    // Canonical INT-1 abbreviations for the supported LITCHR values
    private fun lightAbbreviation(code: String): String? = when (code) {
        "1" -> "F"
        "2" -> "Fl"
        "3" -> "LFl"
        "4" -> "Q"
        "5" -> "VQ"
        "6" -> "UQ"
        "7" -> "Iso"
        "8" -> "Oc"
        "9" -> "IQ"
        "10" -> "IVQ"
        "11" -> "IUQ"
        "12" -> "Mo"
        "28" -> "Al"
        else -> null
    }

    private fun decodeBuoyShape(raw: String): String {
        val activity = mapActivity ?: return raw
        return when (raw.trim()) {
            "1" -> activity.getString(R.string.nautical_s57_boyshp_conical)
            "2" -> activity.getString(R.string.nautical_s57_boyshp_can)
            "3" -> activity.getString(R.string.nautical_s57_boyshp_spherical)
            "4" -> activity.getString(R.string.nautical_s57_boyshp_pillar)
            "5" -> activity.getString(R.string.nautical_s57_boyshp_spar)
            "6" -> activity.getString(R.string.nautical_s57_boyshp_barrel)
            "7" -> activity.getString(R.string.nautical_s57_boyshp_superbuoy)
            "8" -> activity.getString(R.string.nautical_s57_boyshp_ice_buoy)
            else -> raw
        }
    }

    private fun decodeBeaconShape(raw: String): String {
        val activity = mapActivity ?: return raw
        return when (raw.trim()) {
            "1" -> activity.getString(R.string.nautical_s57_bcnshp_stake)
            "2" -> activity.getString(R.string.nautical_s57_bcnshp_withy)
            "3" -> activity.getString(R.string.nautical_s57_bcnshp_tower)
            "4" -> activity.getString(R.string.nautical_s57_bcnshp_lattice)
            "5" -> activity.getString(R.string.nautical_s57_bcnshp_pile)
            "6" -> activity.getString(R.string.nautical_s57_bcnshp_cairn)
            "7" -> activity.getString(R.string.nautical_s57_bcnshp_buoyant)
            else -> raw
        }
    }

    private fun decodeWaterLevelEffect(raw: String): String {
        val activity = mapActivity ?: return raw
        return when (raw.trim()) {
            "1" -> activity.getString(R.string.nautical_s57_watlev_partly_submerged)
            "2" -> activity.getString(R.string.nautical_s57_watlev_dry)
            "3" -> activity.getString(R.string.nautical_s57_watlev_submerged)
            "4" -> activity.getString(R.string.nautical_s57_watlev_covers_uncovers)
            "5" -> activity.getString(R.string.nautical_s57_watlev_awash)
            "6" -> activity.getString(R.string.nautical_s57_watlev_inundation)
            "7" -> activity.getString(R.string.nautical_s57_watlev_floating)
            else -> raw
        }
    }

    private fun decodeCardinalMark(raw: String): String {
        val activity = mapActivity ?: return raw
        return when (raw.trim()) {
            "1" -> activity.getString(R.string.nautical_s57_catcam_north)
            "2" -> activity.getString(R.string.nautical_s57_catcam_east)
            "3" -> activity.getString(R.string.nautical_s57_catcam_south)
            "4" -> activity.getString(R.string.nautical_s57_catcam_west)
            else -> raw
        }
    }

    private fun decodeColourPattern(raw: String): String {
        // COLPAT (S-57): 1..6. Multi-valued lists are ';'-separated.
        val activity = mapActivity ?: return raw
        return raw.split(';', ',', '/').map { code ->
            when (code.trim()) {
                "1" -> activity.getString(R.string.nautical_s57_colpat_horizontal)
                "2" -> activity.getString(R.string.nautical_s57_colpat_vertical)
                "3" -> activity.getString(R.string.nautical_s57_colpat_diagonal)
                "4" -> activity.getString(R.string.nautical_s57_colpat_squares)
                "5" -> activity.getString(R.string.nautical_s57_colpat_stripes_unknown)
                "6" -> activity.getString(R.string.nautical_s57_colpat_border)
                else -> code.trim().ifEmpty { null }
            }?.trim()
        }.filterNotNull().joinToString(" / ").ifEmpty { raw }
    }

    companion object {
        // Chart-notation abbreviation used when composing the full light
        // characteristic (see decodeLightCharacteristic for display words).
        fun formatCompositeLightCharacteristic(attributes: Map<String, String>): String? {
            val litchr = attributes["LITCHR"] ?: return null
            val siggrp = attributes["SIGGRP"]
            val colour = attributes["COLOUR"]
            val sigper = attributes["SIGPER"]
            val height = attributes["HEIGHT"]
            val valnmr = attributes["VALNMR"]

            val sb = StringBuilder()

            // 1. Character acronym
            // LITCHR (S-57): 1 fixed, 2 flashing, 3 long-flashing,
            // 4 quick-flashing, 5 very quick, 6 ultra quick, 7 isophased,
            // 8 occulting, 9 interrupted quick, 10 interrupted very quick,
            // 11 interrupted ultra quick, 12 morse, 13 fixed/flash,
            // 28 alternating. Unknown codes keep their raw value.
            val chrCode = when (litchr.trim()) {
                "1" -> "F"
                "2" -> "Fl"
                "3" -> "LFl"
                "4" -> "Q"
                "5" -> "VQ"
                "6" -> "UQ"
                "7" -> "Iso"
                "8" -> "Oc"
                "9" -> "IQ"
                "10" -> "IVQ"
                "11" -> "IUQ"
                "12" -> "Mo"
                "13" -> "FFl"
                "28" -> "Al"
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

