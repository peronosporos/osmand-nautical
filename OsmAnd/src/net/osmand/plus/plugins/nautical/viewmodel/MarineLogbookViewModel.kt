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

    sealed class UiEvent {
        data class ShowToast(val text: String) : UiEvent()
        data class ShowToastRes(val resId: Int, val formatArgs: Array<out Any> = emptyArray()) : UiEvent()
    }

    val logEntries: StateFlow<List<LogbookEntry>> = repository.logEntries

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
        refresh()
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
