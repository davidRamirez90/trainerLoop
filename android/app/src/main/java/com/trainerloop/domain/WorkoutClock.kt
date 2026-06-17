package com.trainerloop.domain

import com.trainerloop.data.model.WorkoutSegment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WorkoutClock(
  segments: List<WorkoutSegment>,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : AutoCloseable {
  private val totalDurationSec: Int = segments.sumOf { it.durationSec }
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
  private val mutex = Mutex()
  private var tickJob: Job? = null

  private val _elapsedSec = MutableStateFlow(0)
  val elapsedSec: StateFlow<Int> = _elapsedSec

  private val _activeSec = MutableStateFlow(0)
  val activeSec: StateFlow<Int> = _activeSec

  private val _isRunning = MutableStateFlow(false)
  val isRunning: StateFlow<Boolean> = _isRunning

  private val _isComplete = MutableStateFlow(false)
  val isComplete: StateFlow<Boolean> = _isComplete

  private val _sessionId = MutableStateFlow(0)
  val sessionId: StateFlow<Int> = _sessionId

  fun start() {
    scope.launch {
      mutex.withLock {
        if (_isRunning.value) return@withLock
        tickJob?.cancel()
        _sessionId.value = _sessionId.value + 1
        _elapsedSec.value = 0
        _activeSec.value = 0
        _isComplete.value = false
        _isRunning.value = true
        tickJob = launchTickLoop()
      }
    }
  }

  fun pause() {
    scope.launch {
      mutex.withLock {
        _isRunning.value = false
        tickJob?.cancel()
        tickJob = null
      }
    }
  }

  fun resume() {
    scope.launch {
      mutex.withLock {
        if (_isRunning.value || _isComplete.value) return@withLock
        _isRunning.value = true
        tickJob = launchTickLoop()
      }
    }
  }

  fun stop() {
    scope.launch {
      mutex.withLock {
        _isRunning.value = false
        tickJob?.cancel()
        tickJob = null
        _elapsedSec.value = 0
        _activeSec.value = 0
        _isComplete.value = false
      }
    }
  }

  fun seek(seconds: Int) {
    scope.launch {
      mutex.withLock {
        val clamped = seconds.coerceIn(0, totalDurationSec)
        _elapsedSec.value = clamped
        if (clamped >= totalDurationSec) {
          _isComplete.value = true
          _isRunning.value = false
          tickJob?.cancel()
          tickJob = null
        } else if (_isComplete.value) {
          _isComplete.value = false
        }
      }
    }
  }

  override fun close() {
    scope.cancel()
    tickJob = null
  }

  private fun launchTickLoop(): Job {
    return scope.launch {
      while (isActive && _isRunning.value && !_isComplete.value) {
        delay(1000L)
        mutex.withLock {
          if (!isActive || !_isRunning.value || _isComplete.value) return@withLock
          val nextElapsed = _elapsedSec.value + 1
          _elapsedSec.value = nextElapsed
          _activeSec.value = _activeSec.value + 1
          if (nextElapsed >= totalDurationSec) {
            _isComplete.value = true
            _isRunning.value = false
          }
        }
      }
    }
  }
}
