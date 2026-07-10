package com.trainerloop.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionSpecTest {

  @Test
  fun `motion specs expose the fixed spring values`() {
    assertSpring(MotionSpec.default, dampingRatio = 1f, stiffness = 300f)
    assertSpring(MotionSpec.momentum, dampingRatio = 0.8f, stiffness = 300f)
    assertSpring(MotionSpec.fast, dampingRatio = 1f, stiffness = 700f)
  }

  @Test
  fun `reduced motion resolves to snap`() {
    val resolved = resolveSpec(reduced = true, spec = MotionSpec.default)

    assertTrue(resolved is androidx.compose.animation.core.SnapSpec<*>)
    assertEquals(snap<Float>(), resolved)
  }

  private fun assertSpring(
    spec: SpringSpec<Float>,
    dampingRatio: Float,
    stiffness: Float
  ) {
    assertEquals(dampingRatio, spec.dampingRatio)
    assertEquals(stiffness, spec.stiffness)
  }
}
