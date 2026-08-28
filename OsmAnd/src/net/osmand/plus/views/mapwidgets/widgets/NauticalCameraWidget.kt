package net.osmand.plus.views.mapwidgets.widgets

import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.network.SignalKChart
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel

/**
 * Provides access to vessel cameras via a floating PIP window.
 */
class NauticalCameraWidget(
    mapActivity: MapActivity,
    widgetType: WidgetType,
    customId: String?,
    panel: WidgetsPanel?
) : SimpleWidget(mapActivity, widgetType, customId, panel) {

    init {
        setIcons(widgetType)
    }

    private var availableCameras: List<SignalKChart> = emptyList()

    override fun updateIcon() {
        val iconId = iconId
        if (iconId != 0) {
            val color = settings.applicationMode.getProfileColor(isNightMode)
            setImageDrawable(iconsCache.getPaintedIcon(iconId, color))
        }
    }

    override fun updateColors(textState: net.osmand.plus.views.layers.MapInfoLayer.TextState) {
        super.updateColors(textState)
        updateIcon()
    }

    override fun updateSimpleWidgetInfo(drawSettings: OsmandMapLayer.DrawSettings?) {
        updateIcon()
        val sub = if (availableCameras.isNotEmpty()) "${availableCameras.size} CAM" else ""
        setText(mapActivity.getString(R.string.nautical_camera), sub)
    }

    override fun setupView(view: View) {
        super.setupView(view)
        refreshCameraList()
    }

    private fun refreshCameraList() {
        val rest = NauticalPlugin.engine?.getRestService() ?: return
        mapActivity.lifecycleScope.launch {
            try {
                val response = rest.getCharts()
                if (response.isSuccessful) {
                    availableCameras = response.body()?.values?.filter {
                        it.identifier.contains("camera", ignoreCase = true) ||
                        it.name?.contains("camera", ignoreCase = true) == true ||
                        it.type?.contains("onvif", ignoreCase = true) == true
                    } ?: emptyList()
                    updateInfo(null)
                }
            } catch (_: Exception) {}
        }
    }

    override fun getOnClickListener(): View.OnClickListener {
        return View.OnClickListener {
            if (availableCameras.isEmpty()) {
                refreshCameraList()
                mapActivity.app.showToastMessage("Searching for cameras...")
            } else {
                showCameraSelector()
            }
        }
    }

    private fun showCameraSelector() {
        val names = availableCameras.map { it.name ?: it.identifier }.toTypedArray()
        AlertDialog.Builder(mapActivity)
            .setTitle(R.string.nautical_camera)
            .setItems(names) { _, which ->
                watchCamera(availableCameras[which])
            }
            .setNeutralButton("Refresh") { _, _ -> refreshCameraList() }
            .show()
    }

    private fun watchCamera(camera: SignalKChart) {
        val ip = app.settings.NAUTICAL_SERVER_IP.get()
        val port = app.settings.NAUTICAL_SERVER_PORT.get()
        val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
        
        // Signal K standard for chart-based cameras often uses the tilejson URL or a proxy path
        val url = "$protocol://$ip:$port/signalk/v1/api/resources/charts/${camera.identifier}/stream"
        
        mapActivity.app.showToastMessage("Connecting to stream: ${camera.name ?: camera.identifier}")
        
        // Task: Open in full-screen WebView/VideoView fragment
        // For now, use browser as placeholder for the external player
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        mapActivity.startActivity(intent)
    }
}
