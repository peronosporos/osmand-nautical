package net.osmand.plus.plugins.nautical.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response
import net.osmand.PlatformUtil
import net.osmand.data.LatLon
import net.osmand.data.PointDescription
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.logbook.data.LogbookEntry
import net.osmand.plus.helpers.TargetPointsHelper
import net.osmand.plus.plugins.nautical.network.SignalKResourceResponse
import net.osmand.plus.plugins.nautical.network.SignalKRoute
import net.osmand.plus.plugins.nautical.network.SignalKRouteFeature
import net.osmand.plus.plugins.nautical.network.SignalKLineString
import net.osmand.plus.plugins.nautical.network.SignalKWaypoint
import net.osmand.plus.plugins.nautical.network.SignalKPointFeature
import net.osmand.plus.plugins.nautical.network.SignalKPoint
import net.osmand.plus.plugins.nautical.network.SignalKNote
import net.osmand.plus.plugins.nautical.network.SignalKChecklist
import net.osmand.plus.plugins.nautical.network.SignalKRestService
import net.osmand.plus.plugins.nautical.utils.TemporalUtils
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * Syncs Signal K Resources (Waypoints, Notes, Routes) with OsmAnd.
 */
class SignalKResourceManager(
    private val app: OsmandApplication,
    private val scope: CoroutineScope,
) {
    private val log = PlatformUtil.getLog(SignalKResourceManager::class.java)
    private var syncJob: Job? = null

    private val targetPointListener = TargetPointsHelper.TargetPointChangedListener {
        checkAndSyncActiveRoute()
    }

    private val knownMarkerIdMap = mutableMapOf<Pair<Double, Double>, String>()

    fun startSync() {
        syncJob?.cancel()
        app.targetPointsHelper.addPointListener(targetPointListener)
        
        // Task 2: Two-way sync for Map Markers
        app.mapMarkersHelper.addListener(
            object : net.osmand.plus.mapmarkers.MapMarkersHelper.MapMarkerChangedListener {
                override fun onMapMarkerChanged(marker: net.osmand.plus.mapmarkers.MapMarker) {
                    // If added and not from server, push it.
                    // Simplified: push all changes as potential additions if they have a name.
                    scope.launch {
                        pushWaypointToServer(marker.point.latitude, marker.point.longitude, marker.onlyName)?.let { id ->
                            knownMarkerIdMap[marker.point.latitude to marker.point.longitude] = id
                        }
                    }
                }

                override fun onMapMarkersChanged() {
                    // TASK-03.6: Detect deletions by comparing local list with last known positions
                    val currentMarkers = app.mapMarkersHelper.mapMarkers
                    val currentPositions = currentMarkers.asSequence().map { it.point.latitude to it.point.longitude }.toSet()
                    
                    val deleted = knownMarkerIdMap.keys.filter { it !in currentPositions }
                    deleted.forEach { pos ->
                        knownMarkerIdMap[pos]?.let { skId ->
                            log.info("Nautical: Marker at $pos removed locally. Syncing deletion to Signal K.")
                            scope.launch { deleteWaypointFromServer(skId) }
                        }
                        knownMarkerIdMap.remove(pos)
                    }
                }
            },
        )

        syncJob = scope.launch {
            while (isActive) {
                try {
                    syncWaypoints()
                    syncNotes()
                    syncRegions()
                    syncWaterwayClosures()
                    syncAvurnavWarnings()
                    syncChartLocker()
                    syncChecklists()
                    syncRoutes()
                } catch (e: Exception) {
                    log.error("Resource sync error: ${e.message}")
                }
                delay(60000.milliseconds) // Sync every 60 seconds
            }
        }
    }

    fun stopSync() {
        app.targetPointsHelper.removePointListener(targetPointListener)
        syncJob?.cancel()
        syncJob = null
    }

    private fun checkAndSyncActiveRoute() {
        val caps = NauticalPlugin.engine?.capabilityManager?.capabilities?.value
        if (caps?.hasNavicoSync == true) {
            scope.launch {
                uploadActiveRouteToSignalK("OsmAnd-Active-Route")
            }
        }
    }

    private suspend fun syncChartLocker() {
        val service = getRestService() ?: return
        try {
            val response = service.getRegions() // Locker often exposes regions for offline sync
            if (response.isSuccessful) {
                val regions = response.body() ?: return
                // Process locker regions specifically if they have "offline: true" meta
                log.info("Nautical: Syncing with Signal K Chart Locker")
                NauticalPlugin.getInstance()?.safetyManager?.updateSignalKRegions(regions)
            }
        } catch (e: Exception) {
            log.error("Chart Locker sync error: ${e.message}")
        }
    }

    private suspend fun syncRoutes() = withContext(Dispatchers.IO) {
        val service = getRestService() ?: return@withContext
        val response = service.getRoutes()
        if (response.isSuccessful) {
            val routes = response.body() ?: return@withContext
            log.info("Synced ${routes.size} routes from Signal K")
            
            // TASK-03.4: Two-way sync - generate local GPX tracks for server routes
            val gpxDir = app.getAppPath("tracks/signalk")
            if (!gpxDir.exists()) gpxDir.mkdirs()

            routes.forEach { (id, skRoute) ->
                val fileName = "sk_${id.replace(":", "_")}.gpx"
                val file = File(gpxDir, fileName)
                if (!file.exists()) {
                    val gpx = net.osmand.shared.gpx.GpxFile("SignalK: ${skRoute.name}")
                    val rte = net.osmand.shared.gpx.primitives.Route()
                    skRoute.feature.geometry.coordinates.forEach { coord ->
                        val pt = net.osmand.shared.gpx.primitives.WptPt()
                        pt.lon = coord[0]
                        pt.lat = coord[1]
                        rte.points.add(pt)
                    }
                    gpx.routes.add(rte)
                    net.osmand.shared.gpx.GpxUtilities.writeGpxFile(net.osmand.plus.shared.SharedUtil.kFile(file), gpx)
                    log.debug("Created local GPX for SignalK route: ${skRoute.name}")
                }
            }
        }
    }

    private suspend fun syncChecklists() {
        val service = getRestService() ?: return
        try {
            val response = service.getChecklists()
            if (response.isSuccessful) {
                val checklists = response.body() ?: return
                log.info("Nautical: Synced ${checklists.size} checklists from path ${SignalKPaths.RESOURCES_CHECKLISTS}")
                NauticalPlugin.engine?.dataBroker?.updateState { it.copy(checklists = checklists) }
            }
        } catch (e: Exception) {
            log.error("Failed to sync checklists: ${e.message}")
        }
    }

    suspend fun uploadActiveRouteToSignalK(name: String) = withContext(Dispatchers.IO) {
        val points = mutableListOf<Pair<Double, Double>>()
        val targetPoints = app.targetPointsHelper
        targetPoints.intermediatePointsNavigation.forEach { points.add(it.latitude to it.longitude) }
        targetPoints.pointToNavigate?.let { points.add(it.latitude to it.longitude) }

        if (points.isEmpty()) return@withContext

        try {
            val restService = getRestService() ?: return@withContext
            val coords = points.map { listOf(it.second, it.first) }
            val skRoute = SignalKRoute(
                name = name,
                description = "Exported from OsmAnd Nautical",
                distance = null,
                feature = SignalKRouteFeature(
                    geometry = SignalKLineString(coordinates = coords)
                )
            )

            val response = restService.createRoute(skRoute)
            if (response.isSuccessful) {
                log.info("Route synchronized successfully to Signal K. ID: ${response.body()?.id}")
            }
        } catch (e: Exception) {
            log.error("Error synchronizing route: ${e.message}")
        }
    }

    private suspend fun syncWaypoints() = withContext(Dispatchers.IO) {
        val service = getRestService() ?: return@withContext
        val response = service.getWaypoints()
        if (response.isSuccessful) {
            val waypoints = response.body() ?: return@withContext
            withContext(Dispatchers.Main) {
                updateOsmandMarkers(waypoints)
            }
        }
    }

    private suspend fun syncNotes() = withContext(Dispatchers.IO) {
        val service = getRestService() ?: return@withContext
        val response = service.getNotes()
        if (response.isSuccessful) {
            val notes = response.body() ?: return@withContext
            val repository = NauticalPlugin.getInstance()?.logbookRepository ?: return@withContext
            
            notes.forEach { (uuid, note) ->
                val timestamp = note.timestamp?.let { 
                    // Simplified parsing - real implementation would use kotlinx-datetime
                    System.currentTimeMillis() 
                } ?: System.currentTimeMillis()
                
                val entry = LogbookEntry(
                    timestamp = timestamp,
                    latitude = note.position?.coordinates?.get(1) ?: 0.0,
                    longitude = note.position?.coordinates?.get(0) ?: 0.0,
                    sog = null, cog = null, heading = null,
                    tws = null, twa = null, twd = null,
                    pressure = null, waterDepth = null, waterTemp = null,
                    batteryVoltage = null, engineHours = null,
                    notes = "[SignalK] ${note.title ?: ""}: ${note.description ?: ""}",
                    serverUuid = uuid
                )
                repository.insertEntry(entry)
            }

            log.info("Synced ${notes.size} notes from SignalK to Logbook")
        }
    }

    private suspend fun syncRegions() {
        val service = getRestService() ?: return
        val response = service.getRegions()
        if (response.isSuccessful) {
            val regions = response.body() ?: return
            log.info("Nautical: Fetched ${regions.size} restricted regions from Signal K")
            // Pass regions to S57 index or safety manager
            NauticalPlugin.getInstance()?.safetyManager?.updateSignalKRegions(regions)
        }
    }

    private suspend fun syncWaterwayClosures() {
        val caps = NauticalPlugin.engine?.capabilityManager?.capabilities?.value
        if (caps?.hasWaterwayAlerts != true) return

        val service = getRestService() ?: return
        try {
            val response = service.getWaterwayClosures()
            if (response.isSuccessful) {
                val closures = response.body() ?: return
                log.info("Nautical: Fetched ${closures.size} waterway closures from Signal K")
                // Mix with other regions for safety checks
                val allRegions = NauticalPlugin.getInstance()?.safetyManager?.getSignalKRegions()?.associateBy { it.toString() }?.toMutableMap() ?: mutableMapOf()
                allRegions.putAll(closures)
                NauticalPlugin.getInstance()?.safetyManager?.updateSignalKRegions(allRegions)
            }
        } catch (e: Exception) {
            log.error("Failed to fetch waterway closures: ${e.message}")
        }
    }

    private suspend fun syncAvurnavWarnings() {
        val service = getRestService() ?: return
        try {
            val response = service.getAvurnavWarnings()
            if (response.isSuccessful) {
                val warnings = response.body() ?: return
                log.info("Nautical: Fetched ${warnings.size} Avurnav warnings from Signal K")
                val allRegions = NauticalPlugin.getInstance()?.safetyManager?.getSignalKRegions()?.associateBy { it.toString() }?.toMutableMap() ?: mutableMapOf()
                allRegions.putAll(warnings)
                NauticalPlugin.getInstance()?.safetyManager?.updateSignalKRegions(allRegions)
            }
        } catch (e: Exception) {
            log.error("Failed to fetch Avurnav warnings: ${e.message}")
        }
    }

    private fun updateOsmandMarkers(waypoints: Map<String, SignalKWaypoint>) {
        val markersHelper = app.getMapMarkersHelper()
        val activeMarkers = markersHelper.mapMarkers
        
        waypoints.forEach { (id, wp) ->
            val lon = wp.feature.geometry.coordinates[0]
            val lat = wp.feature.geometry.coordinates[1]
            val name = wp.name ?: "SignalK Waypoint"
            
            val existing = activeMarkers.find { (it.point.latitude == lat) && (it.point.longitude == lon) }
            if (existing == null) {
                markersHelper.addMapMarker(LatLon(lat, lon), PointDescription(PointDescription.POINT_TYPE_MAP_MARKER, name), name)
            }
            knownMarkerIdMap[lat to lon] = id
        }
    }

    suspend fun pushWaypointToServer(lat: Double, lon: Double, name: String): String? = withContext(Dispatchers.IO) {
        val service = getRestService() ?: return@withContext null
        val wp = SignalKWaypoint(
            name = name,
            description = "Created in OsmAnd",
            feature = SignalKPointFeature(geometry = SignalKPoint(coordinates = listOf(lon, lat)))
        )
        val response = service.createWaypoint(wp)
        if (response.isSuccessful) {
            val id = response.body()?.id
            log.info("Successfully pushed waypoint to Signal K: $id")
            id
        } else null
    }

    suspend fun deleteWaypointFromServer(skId: String) = withContext(Dispatchers.IO) {
        val service = getRestService() ?: return@withContext
        val response = service.deleteWaypoint(skId)
        if (response.isSuccessful) {
            log.info("Successfully deleted waypoint $skId from Signal K")
        }
    }

    suspend fun pushNoteToServer(lat: Double, lon: Double, title: String, text: String, uuid: String? = null) = withContext(Dispatchers.IO) {
        val service = getRestService() ?: return@withContext
        val note = SignalKNote(
            title = title,
            description = text,
            position = SignalKPoint(coordinates = listOf(lon, lat)),
            timestamp = TemporalUtils.formatIso8601(System.currentTimeMillis())
        )
        val response = if (uuid != null) {
            service.updateNote(uuid, note)
        } else {
            service.createNote(note)
        }
        if (response.isSuccessful) {
            if (uuid != null) {
                log.info("Successfully updated note $uuid on Signal K")
            } else {
                @Suppress("UNCHECKED_CAST")
                val res = response as Response<SignalKResourceResponse>
                log.info("Successfully pushed note to Signal K: ${res.body()?.id}")
            }
        }
    }

    suspend fun pushChecklistToServer(id: String, checklist: SignalKChecklist) = withContext(Dispatchers.IO) {
        val service = getRestService() ?: return@withContext
        val response = service.updateChecklist(id, checklist)
        if (response.isSuccessful) {
            log.info("Successfully pushed checklist $id to Signal K")
        }
    }


    private fun getRestService(): SignalKRestService? {
        val plugin = NauticalPlugin.getInstance() ?: return null
        val client = plugin.okHttpClient ?: return null
        val ip = app.settings.NAUTICAL_SERVER_IP.get()
        val port = app.settings.NAUTICAL_SERVER_PORT.get()
        val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
        return SignalKRestService.create("$protocol://$ip:$port", client)
    }
}
