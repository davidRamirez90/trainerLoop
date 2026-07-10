package com.trainerloop.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val NumericDisplay = TextStyle(
  fontWeight = FontWeight.Bold,
  fontSize = 64.sp,
  lineHeight = 68.sp,
  fontFeatureSettings = "tnum"
)

val NumericLarge = TextStyle(
  fontWeight = FontWeight.SemiBold,
  fontSize = 40.sp,
  lineHeight = 44.sp,
  fontFeatureSettings = "tnum"
)

val NumericMedium = TextStyle(
  fontWeight = FontWeight.Medium,
  fontSize = 24.sp,
  lineHeight = 28.sp,
  fontFeatureSettings = "tnum"
)

val NumericSmall = TextStyle(
  fontWeight = FontWeight.Medium,
  fontSize = 16.sp,
  lineHeight = 22.sp,
  fontFeatureSettings = "tnum"
)

val Typography = Typography(
  displayLarge = TextStyle(
    fontWeight = FontWeight.ExtraBold,
    fontSize = 34.sp,
    lineHeight = 40.sp,
    letterSpacing = (-0.5).sp
  ),
  headlineLarge = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 26.sp,
    lineHeight = 32.sp,
    letterSpacing = (-0.25).sp
  ),
  headlineMedium = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 21.sp,
    lineHeight = 27.sp
  ),
  titleLarge = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 24.sp
  ),
  titleMedium = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 22.sp
  ),
  bodyLarge = TextStyle(
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.15.sp
  ),
  bodyMedium = TextStyle(
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.1.sp
  ),
  labelLarge = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.1.sp
  ),
  labelMedium = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.4.sp
  ),
  labelSmall = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.6.sp
  )
)
