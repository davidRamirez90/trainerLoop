package com.trainerloop.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TrainerLoopTopBar(
  title: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  firstAction: (@Composable () -> Unit)? = null,
  secondAction: (@Composable () -> Unit)? = null,
  windowInsets: WindowInsets = TopAppBarDefaults.windowInsets
) {
  TopAppBar(
    title = title,
    modifier = modifier,
    navigationIcon = {
      if (onBack != null) {
        IconButton(onClick = onBack) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back"
          )
        }
      }
    },
    actions = {
      firstAction?.invoke()
      secondAction?.invoke()
    },
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = MaterialTheme.colorScheme.background,
      scrolledContainerColor = MaterialTheme.colorScheme.surface
    ),
    windowInsets = windowInsets
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainerLoopTopBar(
  title: String,
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  firstAction: (@Composable () -> Unit)? = null,
  secondAction: (@Composable () -> Unit)? = null,
  windowInsets: WindowInsets = TopAppBarDefaults.windowInsets
) {
  TrainerLoopTopBar(
    title = { androidx.compose.material3.Text(title) },
    modifier = modifier,
    onBack = onBack,
    firstAction = firstAction,
    secondAction = secondAction,
    windowInsets = windowInsets
  )
}
