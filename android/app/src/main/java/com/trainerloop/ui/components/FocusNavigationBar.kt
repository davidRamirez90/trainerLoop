package com.trainerloop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Labeled destinations with a quiet selection mark, shared by routes and fixtures. */
@Composable
fun FocusNavigationBar(selectedRoute: String?, onNavigate: (String) -> Unit) {
  Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
    .navigationBarsPadding().selectableGroup().padding(horizontal = 12.dp, vertical = 8.dp)) {
    listOf("home" to "Today", "workouts" to "Workouts", "history" to "History").forEach { (route, label) ->
      val selected = selectedRoute == route
      Column(
        Modifier.weight(1f).height(64.dp).selectable(selected = selected,
          onClick = { if (!selected) onNavigate(route) }, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
      ) {
        Box(Modifier.width(28.dp).height(4.dp).background(
          if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
          RoundedCornerShape(2.dp)))
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge,
          color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}
