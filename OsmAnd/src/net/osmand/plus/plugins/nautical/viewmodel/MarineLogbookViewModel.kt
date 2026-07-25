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
import java.io.File

class MarineLogbookViewModel(
    private val repository: MarineLogbookRepository
) : ViewModel() {

    enum class ExportFormat { CSV, GPX }

    val logEntries: StateFlow<List<LogbookEntry>> = repository.logEntries

    private val _exportTrigger = MutableSharedFlow<ExportFormat>()
    val exportTrigger: SharedFlow<ExportFormat> = _exportTrigger.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.refreshEntries()
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
