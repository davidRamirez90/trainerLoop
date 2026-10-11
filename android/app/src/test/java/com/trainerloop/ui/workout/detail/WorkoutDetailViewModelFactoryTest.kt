package com.trainerloop.ui.workout.detail

import com.trainerloop.ui.library.BuiltInWorkouts
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutDetailViewModelFactoryTest {
  @Test
  fun `preview uses the athlete FTP rather than the default 250 watts`() {
    val workout = BuiltInWorkouts.all(200).first()
    val viewModel = WorkoutDetailViewModelFactory(workout, 200)
      .create(WorkoutDetailViewModel::class.java)
    assertEquals("88–92%", viewModel.intervals[1].targetFtpPct)
  }
}
