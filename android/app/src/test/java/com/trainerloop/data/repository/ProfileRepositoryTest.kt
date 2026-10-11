package com.trainerloop.data.repository

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileRepositoryTest {
  @Test
  fun `invalid saved and updated profile values cannot break training calculations`() = runTest {
    val context = mockk<Context>()
    val prefs = mockk<SharedPreferences>()
    val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    every { context.getSharedPreferences(any(), any()) } returns prefs
    every { prefs.getString(any(), any()) } answers { secondArg() }
    every { prefs.getInt(any(), any()) } answers { secondArg() }
    every { prefs.getFloat(any(), any()) } answers { secondArg() }
    every { prefs.getBoolean(any(), any()) } answers { secondArg() }
    every { prefs.getInt("ftp", any()) } returns 0
    every { prefs.getFloat("weight_kg", any()) } returns Float.NaN
    every { prefs.edit() } returns editor
    every { editor.putString(any(), any()) } returns editor
    every { editor.putInt(any(), any()) } returns editor
    every { editor.putFloat(any(), any()) } returns editor
    every { editor.putBoolean(any(), any()) } returns editor

    val repository = ProfileRepository(context)
    assertEquals(1, repository.getProfileSync().ftp)
    assertEquals(75.0, repository.getProfileSync().weightKg, 0.0)
    repository.updateProfile {
      it.copy(ftp = -10, weightKg = Double.POSITIVE_INFINITY, ergBiasPct = 50,
        rollingResistanceCrr = Double.NaN, dragAreaCda = Double.NEGATIVE_INFINITY)
    }
    val profile = repository.getProfileSync()
    assertEquals(1, profile.ftp)
    assertEquals(75.0, profile.weightKg, 0.0)
    assertEquals(20, profile.ergBiasPct)
    assertEquals(0.005, profile.rollingResistanceCrr, 0.0)
    assertEquals(0.32, profile.dragAreaCda, 0.0)
  }
}
