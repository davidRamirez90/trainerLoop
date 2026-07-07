package com.trainerloop.ui.routes

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.data.repository.RouteRepository
import com.trainerloop.data.repository.RouteSummary
import com.trainerloop.data.source.local.AppDatabase
import com.trainerloop.domain.parser.GpxParseException
import com.trainerloop.domain.parser.GpxParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Single Application ctor — required by the default viewModel() factory
// (reflection can't see Kotlin default args), same as SettingsViewModel.
class RoutesViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = RouteRepository.create(AppDatabase.getInstance(application))

  val routes: StateFlow<List<RouteSummary>> = repository.summaries()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val _importError = MutableStateFlow<String?>(null)
  val importError: StateFlow<String?> = _importError.asStateFlow()

  fun importGpx(uri: Uri) {
    viewModelScope.launch {
      try {
        val route = withContext(Dispatchers.IO) {
          getApplication<Application>().contentResolver.openInputStream(uri)?.use {
            GpxParser.parse(it)
          } ?: throw GpxParseException("Could not open the selected file")
        }
        val fileName = withContext(Dispatchers.IO) { queryDisplayName(uri) }
        repository.save(route, fileName?.substringBeforeLast('.'))
        _importError.value = null
      } catch (e: GpxParseException) {
        _importError.value = e.message
      } catch (e: Exception) {
        _importError.value = "Import failed: ${e.message}"
      }
    }
  }

  fun deleteRoute(id: String) {
    viewModelScope.launch { repository.deleteById(id) }
  }

  fun clearError() {
    _importError.value = null
  }

  private fun queryDisplayName(uri: Uri): String? =
    getApplication<Application>().contentResolver
      .query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
      }
}
