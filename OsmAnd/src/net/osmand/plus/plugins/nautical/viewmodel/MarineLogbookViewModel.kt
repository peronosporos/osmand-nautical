package net.osmand.plus.plugins.nautical.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import net.osmand.plus.plugins.nautical.logbook.data.LogbookEntry
import net.osmand.plus.plugins.nautical.logbook.data.MarineLogbookRepository

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
}
