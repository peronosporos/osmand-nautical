package net.osmand.plus.plugins.nautical.ui.widgets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.data.LatLon
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.shared.extensions.toDegrees
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * Standard IMO / GMDSS Maritime VHF Radio Distress & Urgency Script Generator.
 * Formats MAYDAY and PAN-PAN voice recitations with live GPS coordinates,
 * UTC timestamps, vessel identifiers, nature of distress, and POB.
 * Features 1-tap Clipboard Copy and Text-To-Speech practice recitation.
 */
class VhfDistressScriptBottomSheet : BaseNauticalBottomSheet() {

    enum class CallType {
        MAYDAY, PAN_PAN, SECURITE
    }

    private var callType = CallType.MAYDAY
    private var natureOfDistress = "SINKING / FLOODING"
    private var personsOnBoard = 4
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private lateinit var scriptTextView: TextView
    private lateinit var txtPobCount: TextView
    private lateinit var btnSpeakScript: MaterialButton
    private lateinit var btnCopyScript: MaterialButton

    private val utcTimeFormat = SimpleDateFormat("HH:mm 'UTC'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun createMenuItems(savedInstanceState: Bundle?) {
        val title = when (callType) {
            CallType.MAYDAY -> "MAYDAY Distress Script"
            CallType.PAN_PAN -> "PAN-PAN Urgency Script"
            CallType.SECURITE -> "SECURITE Safety Script"
        }
        addTitleItem(title)

        val themedCtx = net.osmand.plus.utils.UiUtilities.getThemedContext(requireContext(), nightMode)
        val customView = LayoutInflater.from(themedCtx).inflate(R.layout.bottom_sheet_vhf_distress_script, null)

        val toggleCallType = customView.findViewById<MaterialButtonToggleGroup>(R.id.toggle_call_type)
        val chipGroupNature = customView.findViewById<ChipGroup>(R.id.chip_group_distress_nature)
        scriptTextView = customView.findViewById(R.id.txt_distress_script_body)
        txtPobCount = customView.findViewById(R.id.txt_pob_count)
        val btnPobMinus = customView.findViewById<View>(R.id.btn_pob_minus)
        val btnPobPlus = customView.findViewById<View>(R.id.btn_pob_plus)
        btnSpeakScript = customView.findViewById(R.id.btn_speak_distress_script)
        btnCopyScript = customView.findViewById(R.id.btn_copy_distress_script)

        // Initialize TTS
        initTextToSpeech()

        toggleCallType.check(R.id.btn_type_mayday)
        toggleCallType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                callType = when (checkedId) {
                    R.id.btn_type_mayday -> CallType.MAYDAY
                    R.id.btn_type_pan_pan -> CallType.PAN_PAN
                    R.id.btn_type_securite -> CallType.SECURITE
                    else -> CallType.MAYDAY
                }
                updateScript()
            }
        }

        chipGroupNature.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<Chip>(checkedIds[0])
                natureOfDistress = chip?.text?.toString()?.uppercase(Locale.US) ?: "SINKING"
                updateScript()
            }
        }

        btnPobMinus.setOnClickListener {
            if (personsOnBoard > 1) {
                personsOnBoard--
                txtPobCount.text = personsOnBoard.toString()
                updateScript()
            }
        }

        btnPobPlus.setOnClickListener {
            if (personsOnBoard < 99) {
                personsOnBoard++
                txtPobCount.text = personsOnBoard.toString()
                updateScript()
            }
        }

        btnCopyScript.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("VHF Distress Script", scriptTextView.text)
            clipboard?.setPrimaryClip(clip)
            val app = activity?.application as? OsmandApplication
            app?.showToastMessage(R.string.copied_to_clipboard)
        }

        btnSpeakScript.setOnClickListener {
            speakCurrentScript()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest {
                updateScript()
            }
        }

        txtPobCount.text = personsOnBoard.toString()

        val app = activity?.application as? OsmandApplication
        val isNightVision = app?.let { NauticalPlugin.isNightVision(it) } ?: false
        if (isNightVision) {
            customView.setBackgroundColor(0xEE120000.toInt())
            customView.findViewById<View>(R.id.drag_handle)?.setBackgroundColor(0x80FF1744.toInt())
            customView.findViewById<TextView>(R.id.txt_nature_label)?.setTextColor(0xFFFF1744.toInt())
            customView.findViewById<TextView>(R.id.txt_pob_label)?.setTextColor(0xFFFF8A80.toInt())
            txtPobCount.setTextColor(0xFFFF1744.toInt())
            customView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_distress_script)?.apply {
                setCardBackgroundColor(0xEE120000.toInt())
                strokeColor = 0xFFFF1744.toInt()
            }
            scriptTextView.setTextColor(0xFFFF1744.toInt())
            btnCopyScript.setTextColor(0xFFFF1744.toInt())
            btnCopyScript.strokeColor = android.content.res.ColorStateList.valueOf(0xFFFF1744.toInt())
            btnSpeakScript.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFB71C1C.toInt())
        }

        updateScript()

        items.add(BaseBottomSheetItem.Builder().setCustomView(customView).create())
    }

    private fun initTextToSpeech() {
        tts = TextToSpeech(requireContext().applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isTtsReady = true
            }
        }
    }

    private fun speakCurrentScript() {
        if (!isTtsReady || tts == null) {
            initTextToSpeech()
            return
        }
        val text = scriptTextView.text.toString()
            .replace("/", ", ")
            .replace("•", ", ")
            .replace("OVER", "Over.")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vhf_distress_tts")
    }

    private fun toNatoPhonetic(text: String): String {
        val natoMap = mapOf(
            'A' to "Alpha", 'B' to "Bravo", 'C' to "Charlie", 'D' to "Delta", 'E' to "Echo",
            'F' to "Foxtrot", 'G' to "Golf", 'H' to "Hotel", 'I' to "India", 'J' to "Juliett",
            'K' to "Kilo", 'L' to "Lima", 'M' to "Mike", 'N' to "November", 'O' to "Oscar",
            'P' to "Papa", 'Q' to "Quebec", 'R' to "Romeo", 'S' to "Sierra", 'T' to "Tango",
            'U' to "Uniform", 'V' to "Victor", 'W' to "Whiskey", 'X' to "X-ray", 'Y' to "Yankee",
            'Z' to "Zulu", '0' to "Zero", '1' to "One", '2' to "Two", '3' to "Three",
            '4' to "Four", '5' to "Five", '6' to "Six", '7' to "Seven", '8' to "Eight", '9' to "Nine"
        )
        return text.uppercase(Locale.US)
            .filter { it.isLetterOrDigit() }
            .map { char -> natoMap[char] ?: char.toString() }
            .joinToString(" ")
    }

    private fun getNearestLandmarkReference(lat: Double, lon: Double): String? {
        val app = activity?.application as? OsmandApplication ?: return null
        val dbHelper = net.osmand.plus.plugins.nautical.s57.S57SqliteHelper(app)
        val degRadius = 0.2 // ~12 NM
        val features = try {
            dbHelper.queryFeatures(lat - degRadius, lat + degRadius, lon - degRadius, lon + degRadius, listOf("LNDARE", "ISLAND", "SEAMRK", "LIGHTS", "BCNCAR"), limit = 30)
        } catch (e: Exception) {
            emptyList()
        }

        var nearestDist = Double.MAX_VALUE
        var nearestName: String? = null
        var nearestBearing = 0.0

        for (f in features) {
            val name = f.attributes["OBJNAM"] ?: f.attributes["NOBJNM"] ?: f.attributes["name"]
            if (!name.isNullOrBlank()) {
                val p = when (val g = f.geometries.firstOrNull()) {
                    is net.osmand.plus.plugins.nautical.s57.S57Geometry.Point -> g.position
                    is net.osmand.plus.plugins.nautical.s57.S57Geometry.Line -> g.nodes.firstOrNull()
                    is net.osmand.plus.plugins.nautical.s57.S57Geometry.Area -> g.boundaries.firstOrNull()?.firstOrNull()
                    else -> null
                } ?: continue

                val distM = net.osmand.util.MapUtils.getDistance(lat, lon, p.latitude, p.longitude)
                if (distM < nearestDist) {
                    nearestDist = distM
                    nearestName = name
                    nearestBearing = (net.osmand.shared.util.KMapUtils.getBearing(p.latitude, p.longitude, lat, lon).toDegrees() + 360.0) % 360.0
                }
            }
        }

        return if (nearestName != null && nearestDist < 50000.0) {
            val distNm = nearestDist / 1852.0
            String.format(Locale.US, "BEARING %.0f° TRUE, %.1f NM FROM %s", nearestBearing, distNm, nearestName.uppercase(Locale.US))
        } else {
            null
        }
    }

    private fun updateScript() {
        val app = activity?.application as? OsmandApplication
        val settings = app?.settings
        val state = NauticalPlugin.engine?.getCurrentState()
        val loc = app?.locationProvider?.lastKnownLocation

        val vesselName = state?.vesselName?.takeIf { it.isNotEmpty() } ?: "MY VESSEL"
        val callsign = (state?.vesselCallsign ?: state?.vesselCallSign)?.takeIf { it.isNotEmpty() } ?: "CALLSIGN"
        val ownMmsi = state?.vesselMmsi ?: settings?.NAUTICAL_AIS_OWN_MMSI?.get() ?: 0
        val mmsiStr = if (ownMmsi > 0) ownMmsi.toString() else "MMSI"

        val lat = state?.latitude ?: loc?.latitude ?: 0.0
        val lon = state?.longitude ?: loc?.longitude ?: 0.0
        val formattedPos = formatPositionDdm(lat, lon)
        val timeUtc = utcTimeFormat.format(Date())
        val landmarkRef = if (lat != 0.0 && lon != 0.0) getNearestLandmarkReference(lat, lon) else null

        val sb = StringBuilder()

        val upperVesselName = vesselName.uppercase(Locale.US)
        val upperCallsign = callsign.uppercase(Locale.US)
        val phoneticName = toNatoPhonetic(upperVesselName)
        val phoneticCallsign = if (upperCallsign != "CALLSIGN") toNatoPhonetic(upperCallsign) else ""

        when (callType) {
            CallType.MAYDAY -> {
                sb.append("MAYDAY, MAYDAY, MAYDAY\n")
                sb.append("THIS IS ").append(upperVesselName).append(", ")
                    .append(upperVesselName).append(", ")
                    .append(upperVesselName).append("\n")
                if (phoneticName.isNotEmpty()) {
                    sb.append("PHONETIC: ").append(phoneticName).append("\n")
                }
                sb.append("CALLSIGN: ").append(upperCallsign)
                if (phoneticCallsign.isNotEmpty()) {
                    sb.append(" [").append(phoneticCallsign).append("]")
                }
                sb.append("\nMMSI: ").append(mmsiStr).append("\n\n")

                sb.append("MAYDAY ").append(upperVesselName).append("\n")
                sb.append("MY POSITION IS:\n").append(formattedPos).append("\nAT ").append(timeUtc)
                if (landmarkRef != null) {
                    sb.append("\n(").append(landmarkRef).append(")")
                }
                sb.append("\n\nNATURE OF DISTRESS: ").append(natureOfDistress).append("\n")
                sb.append("REQUIRE IMMEDIATE ASSISTANCE\n")
                sb.append(personsOnBoard).append(" PERSONS ON BOARD\n")
                sb.append("OVER")
            }
            CallType.PAN_PAN -> {
                sb.append("PAN-PAN, PAN-PAN, PAN-PAN\n")
                sb.append("ALL STATIONS, ALL STATIONS, ALL STATIONS\n")
                sb.append("THIS IS ").append(upperVesselName).append(", ")
                    .append(upperVesselName).append(", ")
                    .append(upperVesselName).append("\n")
                if (phoneticName.isNotEmpty()) {
                    sb.append("PHONETIC: ").append(phoneticName).append("\n")
                }
                sb.append("CALLSIGN: ").append(upperCallsign)
                if (phoneticCallsign.isNotEmpty()) {
                    sb.append(" [").append(phoneticCallsign).append("]")
                }
                sb.append("\nMMSI: ").append(mmsiStr).append("\n\n")

                sb.append("MY POSITION IS:\n").append(formattedPos).append("\nAT ").append(timeUtc)
                if (landmarkRef != null) {
                    sb.append("\n(").append(landmarkRef).append(")")
                }
                sb.append("\n\nURGENT SITUATION: ").append(natureOfDistress).append("\n")
                sb.append("REQUIRE ASSISTANCE / MONITORING\n")
                sb.append(personsOnBoard).append(" PERSONS ON BOARD\n")
                sb.append("OVER")
            }
            CallType.SECURITE -> {
                sb.append("SECURITE, SECURITE, SECURITE\n")
                sb.append("ALL STATIONS, ALL STATIONS, ALL STATIONS\n")
                sb.append("THIS IS ").append(upperVesselName).append(", ")
                    .append(upperVesselName).append(", ")
                    .append(upperVesselName).append("\n")
                if (phoneticName.isNotEmpty()) {
                    sb.append("PHONETIC: ").append(phoneticName).append("\n")
                }
                sb.append("CALLSIGN: ").append(upperCallsign).append(" • MMSI: ").append(mmsiStr).append("\n\n")

                sb.append("NAVIGATIONAL / SAFETY HAZARD REPORT:\n")
                sb.append("POSITION: ").append(formattedPos).append(" AT ").append(timeUtc)
                if (landmarkRef != null) {
                    sb.append("\n(").append(landmarkRef).append(")")
                }
                sb.append("\n\nHAZARD / OBSERVATION: ").append(natureOfDistress).append("\n")
                sb.append("ALL VESSELS IN VICINITY PLEASE KEEP SHARP LOOKOUT\n")
                sb.append("OVER")
            }
        }

        scriptTextView.text = sb.toString()
    }

    private fun formatPositionDdm(lat: Double, lon: Double): String {
        if (lat == 0.0 && lon == 0.0) return "UNKNOWN POSITION (NO GPS FIX)"

        val latHem = if (lat >= 0) "N" else "S"
        val absLat = abs(lat)
        val latDeg = absLat.toInt()
        val latMin = (absLat - latDeg) * 60.0

        val lonHem = if (lon >= 0) "E" else "W"
        val absLon = abs(lon)
        val lonDeg = absLon.toInt()
        val lonMin = (absLon - lonDeg) * 60.0

        return String.format(Locale.US, "%02d° %06.3f' %s, %03d° %06.3f' %s", latDeg, latMin, latHem, lonDeg, lonMin, lonHem)
    }

    override fun onDestroyView() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
        super.onDestroyView()
    }

    companion object {
        const val TAG = "VhfDistressScriptBottomSheet"

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.isStateSaved) return
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                VhfDistressScriptBottomSheet().show(fragmentManager, TAG)
            }
        }
    }
}
