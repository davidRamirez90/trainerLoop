package com.trainerloop.ui.library

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutImportContractTest {

  @Test
  fun `rejects imports larger than the byte cap before parsing`() = runTest {
    val context = mockk<Context>()
    val resolver = mockk<ContentResolver>()
    val uri = mockk<Uri>()
    every { context.contentResolver } returns resolver
    every { resolver.openInputStream(uri) } returns ByteArrayInputStream(
      ByteArray(WorkoutImportHelper.MAX_IMPORT_BYTES + 1)
    )

    val result = WorkoutImportHelper.importWorkout(context, uri, ftp = 250)

    assertNull(result)
  }
}
