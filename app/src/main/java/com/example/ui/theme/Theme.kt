package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class SongQUiStyle {
    PLAY_MUSIC_MODERN,
    GOOGLE_PLUS_RETRO
}

private val PlayMusicLightColorScheme = lightColorScheme(
    primary = GpmOrange,
    onPrimary = Color.White,
    primaryContainer = GpmOrangeDeep,
    onPrimaryContainer = Color.White,
    secondary = GPlusBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE8FC),
    onSecondaryContainer = Color(0xFF1A3A6E),
    tertiary = GpmAmber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFECB3),
    onTertiaryContainer = Color(0xFF5D4037),
    background = GPlusCanvasLight,
    onBackground = GPlusTextPrimaryLight,
    surface = GPlusSurfaceLight,
    onSurface = GPlusTextPrimaryLight,
    surfaceVariant = GPlusSurfaceMutedLight,
    onSurfaceVariant = GPlusTextSecondaryLight,
    outline = GPlusBorderLight,
    outlineVariant = GPlusBorderLight,
    error = GPlusRedDark,
    onError = Color.White
)

private val PlayMusicDarkColorScheme = darkColorScheme(
    primary = GpmOrange,
    onPrimary = Color.White,
    primaryContainer = GpmOrangeDeep,
    onPrimaryContainer = Color.White,
    secondary = GPlusBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1F375F),
    onSecondaryContainer = Color(0xFFDCE8FC),
    tertiary = GpmAmber,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF4E342E),
    onTertiaryContainer = Color(0xFFFFE0B2),
    background = GPlusCanvasDark,
    onBackground = GPlusTextPrimaryDark,
    surface = GPlusSurfaceDark,
    onSurface = GPlusTextPrimaryDark,
    surfaceVariant = GPlusSurfaceMutedDark,
    onSurfaceVariant = GPlusTextSecondaryDark,
    outline = GPlusBorderDark,
    outlineVariant = GPlusBorderDark,
    error = Color(0xFFFF6B5B),
    onError = Color.Black
)

private val RetroLightColorScheme = PlayMusicLightColorScheme.copy(
    primary = GPlusRed,
    primaryContainer = GPlusRedDark,
    tertiary = GPlusGreen
)

private val RetroDarkColorScheme = PlayMusicDarkColorScheme.copy(
    primary = GPlusRed,
    primaryContainer = GPlusRedDark,
    tertiary = GPlusGreen
)

val PlayMusicShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

val RetroSquareShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(2.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

@Composable
fun SongQTheme(
    darkTheme: Boolean = false,
    uiStyle: SongQUiStyle = SongQUiStyle.PLAY_MUSIC_MODERN,
    content: @Composable () -> Unit
) {
    val colorScheme = when (uiStyle) {
        SongQUiStyle.PLAY_MUSIC_MODERN -> if (darkTheme) PlayMusicDarkColorScheme else PlayMusicLightColorScheme
        SongQUiStyle.GOOGLE_PLUS_RETRO -> if (darkTheme) RetroDarkColorScheme else RetroLightColorScheme
    }
    val shapes = when (uiStyle) {
        SongQUiStyle.PLAY_MUSIC_MODERN -> PlayMusicShapes
        SongQUiStyle.GOOGLE_PLUS_RETRO -> RetroSquareShapes
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SongQTypography,
        shapes = shapes,
        content = content
    )
}

