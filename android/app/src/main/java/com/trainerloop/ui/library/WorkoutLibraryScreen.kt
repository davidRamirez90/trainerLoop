package com.trainerloop.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainerloop.app.trainerLoopApp
import com.trainerloop.data.model.Workout
import com.trainerloop.ui.components.TrainerLoopCard
import com.trainerloop.ui.components.TrainerLoopTopBar
import com.trainerloop.ui.components.WorkoutMiniChart
import com.trainerloop.ui.components.pressable
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.NumericSmall
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.reducedMotionAware
import com.trainerloop.ui.theme.trainerLoopColors
import java.util.Locale

private val StarBounceSpec = spring<Float>(dampingRatio = 0.7f, stiffness = 300f)

@Composable
fun WorkoutLibraryScreen(
  onWorkoutSelected: (Workout) -> Unit,
  onStartRampTest: () -> Unit,
  viewModel: WorkoutLibraryViewModel = viewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val ftp = remember { context.trainerLoopApp.profileRepository.getProfileSync().ftp }

  LaunchedEffect(uiState.error) {
    uiState.error?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.clearError()
    }
  }
  LaunchedEffect(uiState.snackbarMessage) {
    uiState.snackbarMessage?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.clearSnackbarMessage()
    }
  }
  LaunchedEffect(Unit) { viewModel.refresh() }

  val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let(viewModel::importWorkout)
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0),
    topBar = {
      TrainerLoopTopBar(
        title = "Workouts",
        windowInsets = WindowInsets(0),
        firstAction = {
          if (uiState.canSync) {
            SyncPill(
              syncing = uiState.isSyncing,
              enabled = !uiState.isSyncing,
              onClick = viewModel::sync
            )
          }
        },
        secondAction = {
          IconButton(
            onClick = { importLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.size(48.dp)
          ) {
            Icon(Icons.Outlined.UploadFile, contentDescription = "Import workout file")
          }
        }
      )
    },
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
        .padding(horizontal = Spacing.screenMargin),
      verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
      OutlinedTextField(
        value = uiState.searchQuery,
        onValueChange = viewModel::onSearchQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search workouts") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.controlGap)
      ) {
        uiState.categories.forEach { category ->
          val selected = uiState.selectedCategory == category
          FilterChip(
            selected = selected,
            onClick = { viewModel.onCategorySelected(category) },
            label = { Text(category.label, maxLines = 1, softWrap = false) },
            colors = FilterChipDefaults.filterChipColors(
              containerColor = MaterialTheme.colorScheme.surface,
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
          )
        }
      }

      AnimatedVisibility(visible = uiState.isLoading) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
      }

      LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(top = Spacing.md, bottom = Spacing.sectionGap),
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
private fun SyncPill(syncing: Boolean, enabled: Boolean, onClick: () -> Unit) {
  val interactionSource = remember { MutableInteractionSource() }
  Surface(
    onClick = onClick,
    modifier = Modifier
      .heightIn(min = 48.dp)
      .pressable(interactionSource),
    enabled = enabled,
    shape = CircleShape,
    color = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    interactionSource = interactionSource
  ) {
    Row(
      modifier = Modifier.padding(horizontal = Spacing.md),
      horizontalArrangement = Arrangement.spacedBy(Spacing.controlGap),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (syncing) {
        CircularProgressIndicator(
          modifier = Modifier.size(16.dp),
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          strokeWidth = 2.dp
        )
      } else {
        Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
      }
      Text(if (syncing) "Syncing…" else "Sync", style = MaterialTheme.typography.labelLarge)
    }
  }
}

@Composable
private fun RampTestCard(onClick: () -> Unit) {
  val interactionSource = remember { MutableInteractionSource() }
  val semantic = MaterialTheme.trainerLoopColors
  Card(
    onClick = onClick,
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .pressable(interactionSource),
    interactionSource = interactionSource,
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = semantic.coach)
  ) {
    Row(
      modifier = Modifier.padding(Spacing.cardPadding),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        Icons.Outlined.MonitorHeart,
        contentDescription = null,
        tint = semantic.onCoach,
        modifier = Modifier.size(28.dp)
      )
      Spacer(modifier = Modifier.width(Spacing.md))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          "FTP Ramp Test",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = semantic.onCoach
        )
        Text(
          "Assessment · Estimate your FTP",
          style = MaterialTheme.typography.bodySmall,
          color = semantic.onCoach,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Icon(Icons.Default.ChevronRight, contentDescription = null, tint = semantic.onCoach)
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
  val starScale by animateFloatAsState(
    targetValue = if (isFavorite) 1.08f else 1f,
    animationSpec = reducedMotionAware(StarBounceSpec),
    label = "favorite-star-bounce"
  )

  TrainerLoopCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
    WorkoutMiniChart(
      workout = workout,
      ftp = ftp,
      modifier = Modifier.fillMaxWidth(),
      chartHeight = 64.dp
    )
    Spacer(modifier = Modifier.height(Spacing.controlGap))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        workout.name,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.weight(1f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
      IconButton(
        onClick = onToggleFavorite,
        modifier = Modifier
          .size(48.dp)
          .graphicsLayer {
            scaleX = starScale
            scaleY = starScale
          }
      ) {
        Icon(
          if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
          contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
          tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Box {
        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
          Icon(Icons.Default.MoreVert, contentDescription = "More")
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
          DropdownMenuItem(
            text = { Text("Duplicate") },
            onClick = {
              menuOpen = false
              onDuplicate()
            }
          )
          if (canDelete) {
            DropdownMenuItem(
              text = { Text("Delete") },
              onClick = {
                menuOpen = false
                onDelete()
              }
            )
          }
        }
      }
    }
    workout.description?.let { description ->
      Text(
        description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
    }
    val meta = buildList {
      add(formatDuration(stats.durationSec))
      stats.plannedIntensityFactor?.let { add("IF ${String.format(Locale.ROOT, "%.2f", it)}") }
      stats.plannedTss?.let { add("TSS $it") }
    }.joinToString(" · ")
    Text(
      meta,
      style = NumericSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

private fun formatDuration(totalSec: Int): String {
  val hours = totalSec / 3600
  val minutes = (totalSec % 3600) / 60
  return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
