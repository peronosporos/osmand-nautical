package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.utils.ColorUtilities
import net.osmand.shared.aistracker.AisObject
import net.osmand.shared.aistracker.AisObjectConstants
import java.util.Locale

class AisTargetBottomSheet : BottomSheetDialogFragment() {

    private var aisObject: AisObject? = null

    companion object {
        fun newInstance(aisObject: AisObject): AisTargetBottomSheet {
            val fragment = AisTargetBottomSheet()
            fragment.aisObject = aisObject
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dpToPx(context, 16f),
                dpToPx(context, 16f),
                dpToPx(context, 16f),
                dpToPx(context, 24f),
            )
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val ais = aisObject ?: return root
        val skAis = NauticalPlugin.getAisObject(ais.mmsi)

        // Title
        root.addView(
            TextView(context).apply {
                text = skAis?.shipName ?: ais.shipName ?: ais.mmsi.toString()
                textSize = 20f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, dpToPx(context, 8f))
            },
        )

        // Subtitle / Ship Type
        root.addView(
            TextView(context).apply {
                text = skAis?.getShipTypeString() ?: ais.getShipTypeString()
                textSize = 14f
                setTextColor(ColorUtilities.getSecondaryTextColor(requireActivity().application as OsmandApplication, false))
                setPadding(0, 0, 0, dpToPx(context, 16f))
            },
        )

        // Attributes
        val attributes = mutableListOf<Pair<String, String>>()
        attributes.add(getString(R.string.nautical_ais_mmsi_label) to ais.mmsi.toString())
        (skAis?.callSign ?: ais.callSign)?.let { attributes.add(getString(R.string.nautical_ais_callsign) to it) }
        (skAis?.destination ?: ais.destination)?.let { attributes.add(getString(R.string.nautical_ais_destination) to it) }
        
        skAis?.getNavStatusString()?.let { attributes.add(getString(R.string.nautical_ais_status_label) to it) }
        skAis?.getManIndString()?.let { attributes.add(getString(R.string.nautical_ais_maneuver_label) to it) }

        if (ais.cpa.valid) {
            attributes.add(getString(R.string.nautical_ais_cpa_label) to String.format(Locale.US, "%.2f nm", ais.cpa.cpa))
            attributes.add(getString(R.string.nautical_ais_tcpa_label) to String.format(Locale.US, "%.1f min", ais.cpa.tcpa * 60))
        }

        if (ais.sog != AisObjectConstants.INVALID_SOG) {
            attributes.add(getString(R.string.nautical_ais_sog_label) to String.format(Locale.US, "%.1f kn", ais.sog))
        }
        if (ais.cog != AisObjectConstants.INVALID_COG) {
            attributes.add(getString(R.string.nautical_ais_cog_label) to String.format(Locale.US, "%.0f°", ais.cog))
        }

        attributes.forEach { (label, value) ->
            root.addView(createAttributeRow(label, value))
        }

        val caps = NauticalPlugin.getInstance()?.capabilityManager?.capabilities?.value
        val manager = NauticalPlugin.getInstance()?.aisManager
        val extras = manager?.getAisExtras(ais.mmsi)
        val isBuddy = NauticalPlugin.engine?.getCurrentState()?.aisBuddies?.contains(ais.mmsi) == true

        // Buddy Toggle
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dpToPx(context, 16f), 0, 0)
            gravity = android.view.Gravity.CENTER_VERTICAL
            
            addView(TextView(context).apply {
                text = getString(if (isBuddy) R.string.nautical_remove_from_buddies else R.string.nautical_add_to_buddies)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                textSize = 16f
            })
            
            addView(SwitchMaterial(context).apply {
                isChecked = isBuddy
                setOnCheckedChangeListener { _, isChecked ->
                    toggleBuddy(ais.mmsi, isChecked)
                }
            })
        })

        if (caps?.hasAisPrioritizer == true) {
            root.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dpToPx(context, 16f), 0, 0)
                gravity = android.view.Gravity.CENTER_VERTICAL
                
                addView(TextView(context).apply {
                    text = getString(R.string.nautical_mute_target_alarms)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    textSize = 16f
                })
                
                addView(SwitchMaterial(context).apply {
                    isChecked = extras?.isMuted ?: false
                    setOnCheckedChangeListener { _, isChecked ->
                        muteTarget(ais.mmsi, isChecked)
                    }
                })
            })
        }

        return root
    }

    private fun toggleBuddy(mmsi: Int, add: Boolean) {
        val plugin = NauticalPlugin.getInstance() ?: return
        plugin.pluginScope?.launch {
            val engine = NauticalPlugin.engine
            val rest = engine?.getRestService()
            if (rest != null) {
                try {
                    val currentBuddies = engine.getCurrentState().aisBuddies.toMutableSet()
                    if (add) currentBuddies.add(mmsi) else currentBuddies.remove(mmsi)
                    
                    val body = net.osmand.plus.plugins.nautical.network.SignalKPutBody(value = currentBuddies.toList())
                    val response = rest.putGeneric("communication/aisBuddies", body)
                    if (response.isSuccessful) {
                        plugin.application.showToastMessage(R.string.shared_string_ok)
                    }
                } catch (e: Exception) {
                    net.osmand.PlatformUtil.getLog(AisTargetBottomSheet::class.java).error("Failed to toggle AIS buddy: ${e.message}")
                }
            }
        }
    }

    private fun muteTarget(mmsi: Int, mute: Boolean) {
        val plugin = NauticalPlugin.getInstance() ?: return
        val manager = plugin.aisManager ?: return
        manager.muteAisTarget(mmsi, mute)
        
        plugin.pluginScope?.launch {
            val engine = NauticalPlugin.engine
            val rest = engine?.getRestService()
            if (rest != null) {
                try {
                    // REST call to SignalK plugin
                    val path = "plugins/signalk-ais-target-prioritizer/mute"
                    val body = net.osmand.plus.plugins.nautical.network.SignalKPutBody(value = mapOf("mmsi" to mmsi, "mute" to mute))
                    val response = rest.putGeneric(path, body)
                    if (response.isSuccessful) {
                        plugin.application.showToastMessage(R.string.shared_string_ok)
                    }
                } catch (e: Exception) {
                    net.osmand.PlatformUtil.getLog(AisTargetBottomSheet::class.java).error("Failed to mute AIS target: ${e.message}")
                }
            }
        }
    }

    private fun createAttributeRow(label: String, value: String): View {
        val context = requireContext()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dpToPx(context, 4f), 0, dpToPx(context, 4f))
        }
        
        row.addView(TextView(context).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(ColorUtilities.getSecondaryTextColor(requireActivity().application as OsmandApplication, false))
        })
        
        row.addView(TextView(context).apply {
            text = value
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        
        return row
    }

    private fun dpToPx(context: android.content.Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
