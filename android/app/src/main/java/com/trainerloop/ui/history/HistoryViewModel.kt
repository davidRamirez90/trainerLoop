package com.trainerloop.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainerloop.data.model.SessionSummary
import com.trainerloop.data.repository.SessionRepository
import com.trainerloop.data.source.local.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

  private val sessionRepository: SessionRepository =
    SessionRepository.create(AppDatabase.getInstance(application))

  private val _sessions = MutableStateFlow<List<SessionSummary>>(emptyList())
  val sessions: StateFlow<List<SessionSummary>> = _sessions.asStateFlow()

  init {
    viewModelScope.launch {
      sessionRepository.summaries().collect { _sessions.value = it }
    }
  }
}
