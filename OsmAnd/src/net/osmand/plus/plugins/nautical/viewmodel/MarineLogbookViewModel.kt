package net.osmand.plus.plugins.nautical.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    val logEntries: StateFlow<List<LogbookEntry>> = repository.logEntries

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
            currentOffset += pageSize
            repository.refreshEntries(limit = pageSize, offset = currentOffset, append = true)
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

    fun syncWithServer() {
        viewModelScope.launch {
            val plugin = NauticalPlugin.getInstance() ?: return@launch
            val ip = plugin.application.settings.NAUTICAL_SERVER_IP.get()
            val port = plugin.application.settings.NAUTICAL_SERVER_PORT.get()
            val protocol = if (plugin.application.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
            val client = plugin.okHttpClient ?: return@launch
            val service = SignalKRestService.create("$protocol://$ip:$port", client) ?: return@launch

            try {
                val response = withContext(Dispatchers.IO) { service.getLogbook() }
                if (response.isSuccessful && response.body() != null) {
                    val serverEntries = response.body()!!
                    withContext(Dispatchers.IO) {
                        serverEntries.forEach { (uuid, entry) ->
                            val ts = net.osmand.plus.plugins.nautical.utils.TemporalUtils.parseIso8601(entry.timestamp)
                            repository.insertEntrySync(LogbookEntry(
                                timestamp = if (ts > 0) ts else System.currentTimeMillis(),
                                latitude = entry.position?.coordinates?.getOrNull(1) ?: 0.0,
                                longitude = entry.position?.coordinates?.getOrNull(0) ?: 0.0,
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
                                sailPlan = entry.category ?: "",
                                notes = entry.description ?: entry.title ?: "",
                                serverUuid = uuid
                            ))
                        }
                    }
                    refresh()
                }
            } catch (_: Exception) {}
        }
    }
}
