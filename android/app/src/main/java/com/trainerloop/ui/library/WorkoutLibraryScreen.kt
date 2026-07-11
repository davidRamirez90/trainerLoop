package com.trainerloop.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.app.trainerLoopApp
import com.trainerloop.data.model.Workout
import com.trainerloop.ui.components.pressable
import com.trainerloop.ui.components.WorkoutMiniChart
import com.trainerloop.ui.theme.NumericSmall
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.reducedMotionAware
import androidx.compose.ui.unit.IntSize
import java.util.Locale

/** Momentum-class flourish for the favorite toggle; reduced motion resolves this to a snap. */
private val StarBounceSpec = spring<Float>(dampingRatio = 0.7f, stiffness = 300f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLibraryScreen(
  onWorkoutSelected: (Workout) -> Unit,
  onStartRampTest: () -> Unit,
  viewModel: WorkoutLibraryViewModel = viewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val fastMotionSpec = reducedMotionAware(MotionSpec.fast)
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(uiState.error) {
    uiState.error?.let { message ->
      snackbarHostState.showSnackbar(message)
      viewModel.clearError()
    }
  }

  LaunchedEffect(uiState.snackbarMessage) {
    uiState.snackbarMessage?.let { message ->
      snackbarHostState.showSnackbar(message)
      viewModel.clearSnackbarMessage()
    }
  }

  // Pick up workouts saved by the builder while this ViewModel was alive
  androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refresh() }
  val ftp = remember { context.trainerLoopApp.profileRepository.getProfileSync().ftp }

  val importLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      viewModel.importWorkout(uri)
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0),
    snackbarHost = {
      AnimatedVisibility(
        visible = snackbarHostState.currentSnackbarData != null,
        enter = fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
        exit = fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
      ) {
        SnackbarHost(snackbarHostState)
      }
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = Spacing.lg)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
      Text(
        text = "Workouts",
        style = MaterialTheme.typography.headlineLarge
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (uiState.canSync) {
          Button(onClick = { viewModel.sync() }, enabled = !uiState.isSyncing) {
            AnimatedContent(
              targetState = uiState.isSyncing,
              transitionSpec = {
                fadeIn(animationSpec = fastMotionSpec) togetherWith
                  fadeOut(animationSpec = fastMotionSpec)
              },
              label = "workout-sync-icon"
            ) { syncing ->
              if (syncing) {
                CircularProgressIndicator(
                  modifier = Modifier.size(16.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                  strokeWidth = 2.dp
                )
              } else {
                Icon(Icons.Outlined.Sync, contentDescription = null)
              }
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            AnimatedContent(
              targetState = uiState.isSyncing,
              transitionSpec = {
                fadeIn(animationSpec = fastMotionSpec) togetherWith
                  fadeOut(animationSpec = fastMotionSpec)
              },
              label = "workout-sync-label"
            ) { syncing -> Text(if (syncing) "Syncing…" else "Sync") }
          }
        }
        IconButton(onClick = {
          importLauncher.launch(arrayOf("*/*"))
        }) {
          Icon(
            imageVector = Icons.Outlined.UploadFile,
            contentDescription = "Import workout file"
          )
        }
      }
    }

      Spacer(modifier = Modifier.height(12.dp))

      OutlinedTextField(
        value = uiState.searchQuery,
        onValueChange = viewModel::onSearchQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search workouts") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true
      )

      Spacer(modifier = Modifier.height(12.dp))

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        uiState.categories.forEach { category ->
          val selected = uiState.selectedCategory == category
          val containerColor by animateColorAsState(
            targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            animationSpec = reducedMotionAware(MotionSpec.defaultSpring<Color>()),
            label = "category-chip-color-${category.label}"
          )
          FilterChip(
            selected = selected,
            onClick = { viewModel.onCategorySelected(category) },
            label = { Text(category.label, maxLines = 1, softWrap = false) },
            colors = FilterChipDefaults.filterChipColors(
              containerColor = containerColor,
              selectedContainerColor = containerColor
            )
          )
        }
      }

      Spacer(modifier = Modifier.height(Spacing.xl))

      AnimatedVisibility(
        visible = uiState.isLoading,
        enter = expandVertically(
          animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
        ) + fadeIn(animationSpec = reducedMotionAware(MotionSpec.default)),
        exit = shrinkVertically(
          animationSpec = reducedMotionAware(MotionSpec.defaultSpring<IntSize>())
        ) + fadeOut(animationSpec = reducedMotionAware(MotionSpec.default))
      ) { CircularProgressIndicator() }

      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
      ) {
        item(key = "ftp-ramp-test-card") {
          RampTestCard(onClick = onStartRampTest)
        }
        items(uiState.filteredWorkouts, key = { it.workout.id }) { item ->
          WorkoutCard(
            item = item,
            ftp = ftp,
            isFavorite = item.workout.id in uiState.favoriteIds,
            canDelete = item.workout.id in uiState.deletableIds,
            onClick = { onWorkoutSelected(item.workout) },
            onToggleFavorite = { viewModel.toggleFavorite(item.workout.id) },
            onDuplicate = { viewModel.duplicateWorkout(item.workout) },
            onDelete = { viewModel.deleteWorkout(item.workout.id) }
          )
        }
      }
    }
  }
}

@Composable
private fun RampTestCard(onClick: () -> Unit) {
  val interactionSource = remember { MutableInteractionSource() }

  OutlinedCard(
    onClick = onClick,
    modifier = Modifier
      .fillMaxWidth()
      .pressable(interactionSource),
    interactionSource = interactionSource,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(Spacing.lg),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = Icons.Outlined.MonitorHeart,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(28.dp)
      )
      Spacer(modifier = Modifier.width(Spacing.md))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "FTP Ramp Test",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
        Text(
          text = "Ramp to exhaustion to estimate your FTP",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun WorkoutCard(
  item: WorkoutListItem,
  ftp: Int,
  isFavorite: Boolean,
  canDelete: Boolean,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit
) {
  val workout = item.workout
  val stats = item.stats
  var menuOpen by remember { mutableStateOf(false) }
  val interactionSource = remember { MutableInteractionSource() }
  val starScale by animateFloatAsState(
    targetValue = if (isFavorite) 1.08f else 1f,
    animationSpec = reducedMotionAware(StarBounceSpec),
    label = "favorite-star-bounce"
  )
  val starTint by animateColorAsState(
    targetValue = if (isFavorite) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant,
    animationSpec = reducedMotionAware(MotionSpec.defaultSpring<Color>()),
    label = "favorite-star-color"
  )

  Card(
    onClick = onClick,
    modifier = Modifier
      .fillMaxWidth()
      .pressable(interactionSource),
    interactionSource = interactionSource,
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(Spacing.lg)
    ) {
      WorkoutMiniChart(
        workout = workout,
        ftp = ftp,
        modifier = Modifier.fillMaxWidth(),
        chartHeight = 60.dp
      )

      Spacer(modifier = Modifier.height(8.dp))

      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = workout.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f)
        )
        IconButton(
          onClick = onToggleFavorite,
          modifier = Modifier.graphicsLayer {
            scaleX = starScale
            scaleY = starScale
          }
        ) {
          Icon(
            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
            tint = starTint
          )
        }
        Box {
          IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More")
          }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
              text = { Text("Duplicate") },
              onClick = { menuOpen = false; onDuplicate() }
            )
            if (canDelete) {
              DropdownMenuItem(
                text = { Text("Delete") },
                onClick = { menuOpen = false; onDelete() }
              )
            }
          }
        }
      }
      if (workout.description != null) {
        Text(
          text = workout.description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      val meta = buildList {
        add(formatDuration(stats.durationSec))
        stats.plannedIntensityFactor?.let { intensityFactor ->
          add("IF ${String.format(Locale.ROOT, "%.2f", intensityFactor)}")
        }
        stats.plannedTss?.let { tss -> add("TSS $tss") }
      }.joinToString(" · ")
      Text(
        text = meta,
        style = NumericSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

private fun formatDuration(totalSec: Int): String {
  val h = totalSec / 3600
  val m = (totalSec % 3600) / 60
  return if (h > 0) "${h}h ${m}m" else "${m}m"
}
