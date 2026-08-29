package net.osmand.plus.plugins.nautical.export

import android.app.Activity
import android.content.Intent
import androidx.core.content.FileProvider
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.routing.model.OptimalRouteResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PassagePlanExportHelper {

    enum class ExportFormat {
        TEXT,
        GPX
    }

    fun generateSolasPassagePlanText(route: OptimalRouteResult, app: OsmandApplication): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US)
        val dateStr = sdf.format(Date())
        val vesselName = app.settings.getCustomRenderProperty("vesselName", "Vessel").get()
        val vesselType = app.settings.NAUTICAL_VESSEL_TYPE.get().name
        val draft = app.settings.getCustomRenderProperty("vesselDraft", "1.8").get()
        val mastHeight = app.settings.getCustomRenderProperty("mastHeight", "15.0").get()

        val sb = StringBuilder()
        sb.appendLine("================================================================================")
        sb.appendLine("                 SOLAS CHAPTER V PASSAGE PLAN MANIFEST")
        sb.appendLine("================================================================================")
        sb.appendLine("Generated: $dateStr")
        sb.appendLine("Vessel:    $vesselName ($vesselType)")
        sb.appendLine("Draft:     ${draft}m | Air Draft: ${mastHeight}m")
        sb.appendLine("Total Leg Count: ${route.legs.size}")
        sb.appendLine("Total Passage Distance: ${String.format(Locale.US, "%.2f NM", route.totalDistanceNm)}")
        sb.appendLine("Estimated Time Enroute: ${String.format(Locale.US, "%.1f hours", route.totalTimeHours)}")
        sb.appendLine("================================================================================")
        sb.appendLine()
        sb.appendLine("1. WAYPOINT & LEG PASSAGE SCHEDULE")
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine(String.format(Locale.US, "%-4s | %-12s | %-12s | %-6s | %-8s | %-8s | %-8s", "LEG", "FROM", "TO", "COURSE", "DIST(NM)", "SOG(kn)", "ETE(min)"))
        sb.appendLine("--------------------------------------------------------------------------------")

        for (leg in route.legs) {
            val fromStr = String.format(Locale.US, "%.4f,%.4f", leg.from.latitude, leg.from.longitude)
            val toStr = String.format(Locale.US, "%.4f,%.4f", leg.to.latitude, leg.to.longitude)
            sb.appendLine(String.format(
                Locale.US,
                "%-4d | %-12s | %-12s | %03.0f°T  | %-8.2f | %-8.1f | %-8.0f",
                leg.legNumber,
                fromStr,
                toStr,
                leg.courseToSteerDeg,
                leg.distanceNm,
                leg.speedOverGroundKn,
                leg.eteHours * 60.0
            ))
        }
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine()

        sb.appendLine("2. TIDAL WINDOWS & ENVIRONMENTAL CONSTRAINTS")
        sb.appendLine("--------------------------------------------------------------------------------")
        val state = NauticalPlugin.engine?.getCurrentState()
        val tideHeight = state?.tide?.heightNow ?: app.settings.NAUTICAL_ANCHOR_TIDE_RISE.get().toDouble()
        val currentDrift = state?.drift ?: 0.0
        val currentSet = state?.setTrue?.let { Math.toDegrees(it) } ?: 0.0
        sb.appendLine(String.format(Locale.US, "• Current Chart Datum Tide Height: %.2f m", tideHeight))
        sb.appendLine(String.format(Locale.US, "• Ambient Tidal Stream: %.2f kn @ %03.0f°T", currentDrift * 1.94384, currentSet))
        sb.appendLine("• Required Under-Keel Clearance (UKC) Margin: 1.0 m")
        sb.appendLine()

        sb.appendLine("3. CHARTED HAZARDS & MINIMUM CLEARANCES")
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("• Overhead Bridge / Power Cable Safety Margin: +1.5m above mast air draft")
        sb.appendLine("• S-57 Depth Safety Contours: Active safety depth contour monitored along corridor")
        sb.appendLine("• Traffic Separation Schemes: Comply with COLREGS Rule 10 across all lanes")
        sb.appendLine()

        sb.appendLine("4. COMMUNICATIONS & SAFETY FREQUENCIES")
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("• Distress, Safety & Calling:     VHF Channel 16 (156.800 MHz)")
        sb.appendLine("• Digital Selective Calling (DSC): VHF Channel 70 (156.525 MHz)")
        sb.appendLine("• Coast Guard / SAR Working:      VHF Channel 67 / 06")
        sb.appendLine("• Port Operations & Navigation:   VHF Channel 12 / 14 / 68")
        sb.appendLine()

        sb.appendLine("5. FUEL, POWER & CONSUMABLE RESERVES")
        sb.appendLine("--------------------------------------------------------------------------------")
        val estimatedFuelLiters = route.totalTimeHours * 3.5 // Approx 3.5 L/h baseline
        val contingencyFuelLiters = estimatedFuelLiters * 1.25 // 25% safety reserve
        sb.appendLine(String.format(Locale.US, "• Estimated Engine Fuel Required: %.1f Liters", estimatedFuelLiters))
        sb.appendLine(String.format(Locale.US, "• Required Fuel with 25%% Reserve: %.1f Liters", contingencyFuelLiters))
        sb.appendLine("• Minimum Battery Autonomy Buffer: 6.0 Hours")
        sb.appendLine("================================================================================")
        sb.appendLine("                       END OF PASSAGE PLAN MANIFEST")
        sb.appendLine("================================================================================")

        return sb.toString()
    }

    fun generatePassagePlanGpx(route: OptimalRouteResult): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="OsmAnd Nautical Passage Planner" xmlns="http://www.topografix.com/GPX/1/1">""")
        sb.appendLine("  <metadata>")
        sb.appendLine("    <name>SOLAS V Passage Plan</name>")
        sb.appendLine("    <desc>Automated weather-routed passage plan</desc>")
        sb.appendLine("  </metadata>")
        sb.appendLine("  <rte>")
        sb.appendLine("    <name>Passage Route</name>")
        for (leg in route.legs) {
            sb.appendLine(String.format(Locale.US, """    <rtept lat="%.6f" lon="%.6f">""", leg.to.latitude, leg.to.longitude))
            sb.appendLine(String.format(Locale.US, "      <name>WPT %d</name>", leg.legNumber))
            sb.appendLine(String.format(Locale.US, "      <cmt>Leg %d: %.1f NM @ %03.0f°T</cmt>", leg.legNumber, leg.distanceNm, leg.courseToSteerDeg))
            sb.appendLine("    </rtept>")
        }
        sb.appendLine("  </rte>")
        sb.appendLine("</gpx>")
        return sb.toString()
    }

    fun sharePassagePlan(activity: Activity, route: OptimalRouteResult, format: ExportFormat) {
        val app = activity.application as? OsmandApplication ?: return
        val content = if (format == ExportFormat.TEXT) {
            generateSolasPassagePlanText(route, app)
        } else {
            generatePassagePlanGpx(route)
        }

        val filename = if (format == ExportFormat.TEXT) "PassagePlan_${System.currentTimeMillis()}.txt" else "PassagePlan_${System.currentTimeMillis()}.gpx"
        val mimeType = if (format == ExportFormat.TEXT) "text/plain" else "application/gpx+xml"

        val exportDir = File(activity.cacheDir, "passage_plans").apply { mkdirs() }
        val file = File(exportDir, filename)
        file.writeText(content)

        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SOLAS V Passage Plan - ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}")
            putExtra(Intent.EXTRA_TEXT, content)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(shareIntent, "Export Passage Plan"))
    }
}
