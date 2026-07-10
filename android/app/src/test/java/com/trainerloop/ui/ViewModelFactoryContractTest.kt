package com.trainerloop.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.trainerloop.ui.devices.DevicesViewModel
import com.trainerloop.ui.history.HistoryViewModel
import com.trainerloop.ui.home.HomeViewModel
import com.trainerloop.ui.library.WorkoutLibraryViewModel
import com.trainerloop.ui.routes.RoutesViewModel
import com.trainerloop.ui.settings.SettingsViewModel
import org.junit.Assert.assertNotNull
import org.junit.Test

class ViewModelFactoryContractTest {

  @Test
  fun `default-factory AndroidViewModels expose an Application-only constructor`() {
    defaultFactoryViewModels.forEach { viewModelClass ->
      val applicationConstructor = viewModelClass.constructors.singleOrNull { constructor ->
        constructor.parameterTypes.contentEquals(arrayOf(Application::class.java))
      }

      assertNotNull(
        "${viewModelClass.name} must have a public (Application) constructor",
        applicationConstructor
      )
    }
  }

  private companion object {
    val defaultFactoryViewModels: List<Class<out AndroidViewModel>> = listOf(
      DevicesViewModel::class.java,
      HistoryViewModel::class.java,
      HomeViewModel::class.java,
      RoutesViewModel::class.java,
      SettingsViewModel::class.java,
      WorkoutLibraryViewModel::class.java
    )
  }
}
