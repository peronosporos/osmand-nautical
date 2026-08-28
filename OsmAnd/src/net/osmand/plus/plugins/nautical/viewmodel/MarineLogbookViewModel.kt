package net.osmand.plus.plugins.nautical.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.logbook.data.LogbookEntry
import net.osmand.plus.plugins.nautical.logbook.data.MarineLogbookRepository
import net.osmand.plus.plugins.nautical.network.SignalKRestService

class MarineLogbookViewModel(
    private val repository: MarineLogbookRepository
) : ViewModel() {

    enum class ExportFormat { CSV, GPX }

    data class LogbookSummaryMetrics(
        val totalDistanceNm: Double = 0.0,
        val avgSogKnots: Double = 0.0,
        val maxSogKnots: Double = 0.0,
        val portTackPercent: Double = 50.0,
        val starboardTackPercent: Double = 50.0,
        val enginePercent: Double = 0.0,
        val sailPercent: Double = 100.0,
        val totalEntries: Int = 0
    )

    sealed class UiEvent {
        data class ShowToast(val text: String) : UiEvent()
        data class ShowToastRes(val resId: Int, val formatArgs: Array<out Any> = emptyArray()) : UiEvent()
    }

    val logEntries: StateFlow<List<LogbookEntry>> = repository.logEntries

    private val _summaryMetrics = MutableStateFlow(LogbookSummaryMetrics())
    val summaryMetrics: StateFlow<LogbookSummaryMetrics> = _summaryMetrics.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _exportTrigger = MutableSharedFlow<ExportFormat>()
    val exportTrigger: SharedFlow<ExportFormat> = _exportTrigger.asSharedFlow()

    private var currentOffset = 0
    private val pageSize = 100
    private var isLoading = false

    init {
        viewModelScope.launch {
            logEntries.collect { entries ->
                _summaryMetrics.value = calculateSummaryMetrics(entries)
            }
        }
        refresh()
    }

    companion object {
        fun calculateSummaryMetrics(entries: List<LogbookEntry>): LogbookSummaryMetrics {
            if (entries.isEmpty()) return LogbookSummaryMetrics()

            var totalDistMeters = 0.0
            val sorted = entries.sortedBy { it.timestamp }
            for (i in 0 until sorted.size - 1) {
                val p1 = sorted[i]
                val p2 = sorted[i + 1]
                if (p1.latitude != 0.0 && p1.longitude != 0.0 && p2.latitude != 0.0 && p2.longitude != 0.0) {
                    totalDistMeters += net.osmand.util.MapUtils.getDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
                }
            }
            val totalDistNm = totalDistMeters / 1852.0

            val sogListKnots = entries.mapNotNull { it.sog?.let { sogMs -> sogMs * 1.94384 } }
            val avgSog = if (sogListKnots.isNotEmpty()) sogListKnots.average() else 0.0
            val maxSog = if (sogListKnots.isNotEmpty()) sogListKnots.maxOrNull() ?: 0.0 else 0.0

            val twaEntries = entries.mapNotNull { it.twa }
            val portCount = twaEntries.count {
                val deg = (Math.toDegrees(it) + 360.0) % 360.0
                deg > 180.0
            }
            val portPct = if (twaEntries.isNotEmpty()) (portCount.toDouble() / twaEntries.size) * 100.0 else 50.0
            val stbdPct = if (twaEntries.isNotEmpty()) 100.0 - portPct else 50.0

            val engineCount = entries.count { it.engineHours != null || it.sailPlan.contains("engine", ignoreCase = true) || it.sailPlan.contains("motor", ignoreCase = true) }
            val sailCount = entries.count { it.sailPlan.contains("sail", ignoreCase = true) || it.sailPlan.contains("main", ignoreCase = true) || it.sailPlan.contains("jib", ignoreCase = true) || it.sailPlan.contains("genoa", ignoreCase = true) || it.twa != null }
            val totalCategorized = engineCount + sailCount
            val engPct = if (totalCategorized > 0) (engineCount.toDouble() / totalCategorized) * 100.0 else 0.0
            val slPct = if (totalCategorized > 0) (sailCount.toDouble() / totalCategorized) * 100.0 else 100.0

            return LogbookSummaryMetrics(
                totalDistanceNm = totalDistNm,
                avgSogKnots = avgSog,
                maxSogKnots = maxSog,
                portTackPercent = portPct,
                starboardTackPercent = stbdPct,
                enginePercent = engPct,
                sailPercent = slPct,
                totalEntries = entries.size
            )
        }
    }

    fun refresh() {
        if (isLoading) return
        viewModelScope.launch {
            isLoading = true
            currentOffset = 0
            repository.refreshEntries(limit = pageSize, offset = 0, append = false)
            isLoading = false
        }
    }

    fun loadNextPage() {
        if (isLoading) return
        viewModelScope.launch {
            isLoading = true
            val success = repository.refreshEntries(limit = pageSize, offset = currentOffset + pageSize, append = true)
            if (success) {
                 currentOffset += pageSize
            }
            isLoading = false
        }
    }

    fun updateEntryDetails(entryId: Long, sailPlan: String, notes: String) {
        viewModelScope.launch {
            repository.updateEntryDetails(entryId, sailPlan, notes)
        }
    }

    fun requestExport(format: ExportFormat = ExportFormat.CSV) {
        viewModelScope.launch {
            _exportTrigger.emit(format)
        }
    }

    suspend fun getFullLogbookForExport(): List<LogbookEntry> {
        return repository.getAllEntriesForExport()
    }

    fun syncWithServer() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            val plugin = NauticalPlugin.getInstance() ?: run { _isSyncing.value = false; return@launch }
            val ip = plugin.application.settings.NAUTICAL_SERVER_IP.get()
            val port = plugin.application.settings.NAUTICAL_SERVER_PORT.get()
            val protocol = if (plugin.application.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
            val client = plugin.okHttpClient ?: return@launch
            val service = SignalKRestService.create("$protocol://$ip:$port", client) ?: return@launch

            try {
                val response = withContext(Dispatchers.IO) { service.getLogbook() }
                if (response.isSuccessful && response.body() != null) {
                    val serverEntries = response.body()!!
                    val entriesToInsert = mutableListOf<LogbookEntry>()
                    
                    withContext(Dispatchers.IO) {
                        serverEntries.forEach { (uuid, serverEntry) ->
                            val localEntry = repository.getEntryByUuid(uuid)
                            if (localEntry == null) {
                                val ts = net.osmand.plus.plugins.nautical.utils.TemporalUtils.parseIso8601(serverEntry.timestamp)
                                entriesToInsert.add(LogbookEntry(
                                    timestamp = if (ts > 0) ts else System.currentTimeMillis(),
                                    latitude = serverEntry.position?.coordinates?.getOrNull(1) ?: 0.0,
                                    longitude = serverEntry.position?.coordinates?.getOrNull(0) ?: 0.0,
                                    sog = null,
                                    cog = null,
                                    heading = null,
                                    tws = null,
                                    twa = null,
                                    twd = null,
                                    pressure = null,
                                    waterDepth = null,
                                    waterTemp = null,
                                    batteryVoltage = null,
                                    engineHours = null,
                                    sailPlan = serverEntry.category ?: "",
                                    notes = serverEntry.description ?: serverEntry.title ?: "",
                                    serverUuid = uuid
                                ))
                            } else {
                                // Smart Merge: Preserve local telemetry if server only has position/category/notes
                                val updated = localEntry.copy(
                                    sailPlan = if (serverEntry.category?.isNotEmpty() == true) serverEntry.category else localEntry.sailPlan,
                                    notes = if (serverEntry.description?.isNotEmpty() == true) serverEntry.description else localEntry.notes
                                )
                                if (updated != localEntry) {
                                    // Item 3: Don't push back to server during sync
                                    repository.updateEntryDetails(updated.id, updated.sailPlan, updated.notes, pushToServer = false)
                                }
                            }
                        }
                        if (entriesToInsert.isNotEmpty()) {
                            repository.insertEntriesSync(entriesToInsert)
                        }
                    }
                    _uiEvents.emit(UiEvent.ShowToastRes(net.osmand.plus.R.string.nautical_logbook_sync_success, arrayOf(serverEntries.size)))
                    refresh()
                } else {
                    _uiEvents.emit(UiEvent.ShowToastRes(net.osmand.plus.R.string.nautical_logbook_sync_failed))
                }
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowToastRes(net.osmand.plus.R.string.nautical_logbook_sync_error, arrayOf(e.message ?: "Unknown")))
            } finally {
                _isSyncing.value = false
            }
        }
    }
}
