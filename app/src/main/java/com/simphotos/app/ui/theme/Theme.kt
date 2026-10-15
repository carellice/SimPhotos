package com.simphotos.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

val Violet = Color(0xFF5B4BFF)
val Orchid = Color(0xFFB14DFF)
val Gold = Color(0xFFFFB627)
val Coral = Color(0xFFFF4F6D)
val Mint = Color(0xFF22C58B)

val BrandGradient = Brush.linearGradient(listOf(Color(0xFF6D5DFF), Orchid))

private val Light = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E2FF),
    onPrimaryContainer = Color(0xFF1B1269),
    secondary = Orchid,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E3FF),
    onSecondaryContainer = Color(0xFF3B0A5C),
    tertiary = Gold,
    error = Coral,
    background = Color(0xFFF7F5FF),
    onBackground = Color(0xFF16141F),
    surface = Color(0xFFF7F5FF),
    onSurface = Color(0xFF16141F),
    surfaceVariant = Color(0xFFE9E6F5),
    onSurfaceVariant = Color(0xFF5C5870),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0EDFB),
    surfaceContainerHigh = Color(0xFFEAE7F6),
    outlineVariant = Color(0xFFDAD6EA),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9D92FF),
    onPrimary = Color(0xFF1B1269),
    primaryContainer = Color(0xFF3A2FB8),
    onPrimaryContainer = Color(0xFFE6E2FF),
    secondary = Color(0xFFD49BFF),
    onSecondary = Color(0xFF3B0A5C),
    secondaryContainer = Color(0xFF5A2380),
    onSecondaryContainer = Color(0xFFF3E3FF),
    tertiary = Gold,
    error = Color(0xFFFF7A90),
    background = Color(0xFF0F0D1A),
    onBackground = Color(0xFFECE9F7),
    surface = Color(0xFF0F0D1A),
    onSurface = Color(0xFFECE9F7),
    surfaceVariant = Color(0xFF2A2738),
    onSurfaceVariant = Color(0xFFB5B0C8),
    surfaceContainerLowest = Color(0xFF0B0914),
    surfaceContainerLow = Color(0xFF171424),
    surfaceContainer = Color(0xFF1C1929),
    surfaceContainerHigh = Color(0xFF242133),
    outlineVariant = Color(0xFF363247),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
)

val NumberStyle = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-1).sp)

@Composable
fun SimPhotosTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
