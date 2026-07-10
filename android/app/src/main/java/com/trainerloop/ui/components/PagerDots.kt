package com.trainerloop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trainerloop.ui.theme.MotionSpec
import com.trainerloop.ui.theme.Spacing
import com.trainerloop.ui.theme.reducedMotionAware

@Composable
fun PagerDots(
  pageTitles: List<String>,
  currentPage: Int,
  onPageSelected: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = Spacing.xs),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    pageTitles.forEachIndexed { index, title ->
      val selected = currentPage == index
      val dotWidth by animateDpAsState(
        targetValue = if (selected) 24.dp else 8.dp,
        animationSpec = reducedMotionAware(MotionSpec.defaultSpring<Dp>()),
        label = "Pager dot width"
      )
      val dotColor by animateColorAsState(
        targetValue = if (selected) {
          MaterialTheme.colorScheme.primary
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        },
        animationSpec = reducedMotionAware(MotionSpec.defaultSpring<Color>()),
        label = "Pager dot color"
      )

      Box(
        modifier = Modifier
          .size(width = 48.dp, height = 32.dp)
          .clickable(
            onClickLabel = "Show $title page",
            onClick = { onPageSelected(index) },
            role = Role.Tab
          ),
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier
            .size(width = dotWidth, height = 8.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(dotColor)
        )
      }
    }
  }
}
