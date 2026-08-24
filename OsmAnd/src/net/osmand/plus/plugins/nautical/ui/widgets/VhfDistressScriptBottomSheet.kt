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

    private var isMayday = true
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
        val title = if (isMayday) "MAYDAY Distress Script" else "PAN-PAN Urgency Script"
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
                isMayday = (checkedId == R.id.btn_type_mayday)
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
            app?.showToastMessage(R.string.shared_string_copied_to_clipboard)
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

    private fun updateScript() {
        val app = activity?.application as? OsmandApplication
        val settings = app?.settings
        val state = NauticalPlugin.engine?.getCurrentState()
        val loc = app?.locationProvider?.lastKnownLocation

        val vesselName = settings?.NAUTICAL_VESSEL_NAME?.get()?.takeIf { it.isNotEmpty() } ?: "MY VESSEL"
        val callsign = settings?.NAUTICAL_VESSEL_CALLSIGN?.get()?.takeIf { it.isNotEmpty() } ?: "CALLSIGN"
        val ownMmsi = settings?.NAUTICAL_AIS_OWN_MMSI?.get() ?: 0
        val mmsiStr = if (ownMmsi > 0) ownMmsi.toString() else "MMSI"

        val lat = state?.latitude ?: loc?.latitude ?: 0.0
        val lon = state?.longitude ?: loc?.longitude ?: 0.0
        val formattedPos = formatPositionDdm(lat, lon)
        val timeUtc = utcTimeFormat.format(Date())

        val sb = StringBuilder()

        if (isMayday) {
            sb.append("MAYDAY, MAYDAY, MAYDAY\n")
            sb.append("THIS IS ").append(vesselName.uppercase(Locale.US)).append(", ")
                .append(vesselName.uppercase(Locale.US)).append(", ")
                .append(vesselName.uppercase(Locale.US)).append("\n")
            sb.append("CALLSIGN: ").append(callsign.uppercase(Locale.US))
                .append(" • MMSI: ").append(mmsiStr).append("\n\n")

            sb.append("MAYDAY ").append(vesselName.uppercase(Locale.US)).append("\n")
            sb.append("MY POSITION IS ").append(formattedPos).append(" AT ").append(timeUtc).append("\n\n")
            sb.append("NATURE OF DISTRESS: ").append(natureOfDistress).append("\n")
            sb.append("REQUIRE IMMEDIATE ASSISTANCE\n")
            sb.append(personsOnBoard).append(" PERSONS ON BOARD\n")
            sb.append("OVER")
        } else {
            sb.append("PAN-PAN, PAN-PAN, PAN-PAN\n")
            sb.append("ALL STATIONS, ALL STATIONS, ALL STATIONS\n")
            sb.append("THIS IS ").append(vesselName.uppercase(Locale.US)).append(", ")
                .append(vesselName.uppercase(Locale.US)).append(", ")
                .append(vesselName.uppercase(Locale.US)).append("\n")
            sb.append("CALLSIGN: ").append(callsign.uppercase(Locale.US))
                .append(" • MMSI: ").append(mmsiStr).append("\n\n")

            sb.append("MY POSITION IS ").append(formattedPos).append(" AT ").append(timeUtc).append("\n\n")
            sb.append("URGENT SITUATION: ").append(natureOfDistress).append("\n")
            sb.append("REQUIRE ASSISTANCE / MONITORING\n")
            sb.append(personsOnBoard).append(" PERSONS ON BOARD\n")
            sb.append("OVER")
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
