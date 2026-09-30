package com.chb.form.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.chb.form.R

val Sarabun = FontFamily(Font(R.font.sarabun_regular))

// Warm Cream & Pistachio Color Palette
val CreamBg = Color(0xFFF9F6F0)
val CreamSurface = Color(0xFFFFFDF9)
val CreamMuted = Color(0xFFF1EBE0)
val Pistachio = Color(0xFF7FA873)
val PistachioDark = Color(0xFF496B3E)
val PistachioLight = Color(0xFFE6EFE3)
val PistachioContainer = Color(0xFFD6E7D1)
val PistachioOnContainer = Color(0xFF1E3517)
val NeoBlack = Color(0xFF1F241C)
val NeoBorder = Color(0xFF22271E)
val NeoMutedText = Color(0xFF5F685B)
val NeoError = Color(0xFFC0392B)
val NeoErrorContainer = Color(0xFFFFECE9)
val NeoWarning = Color(0xFFD97706)
val NeoWarningContainer = Color(0xFFFEF3C7)

private val LightColorScheme = lightColorScheme(
    primary = PistachioDark,
    onPrimary = Color.White,
    primaryContainer = PistachioContainer,
    onPrimaryContainer = PistachioOnContainer,
    secondary = Pistachio,
    onSecondary = NeoBlack,
    secondaryContainer = PistachioLight,
    onSecondaryContainer = PistachioDark,
    background = CreamBg,
    onBackground = NeoBlack,
    surface = CreamSurface,
    onSurface = NeoBlack,
    surfaceVariant = CreamMuted,
    onSurfaceVariant = NeoMutedText,
    outline = NeoBorder,
    outlineVariant = Color(0xFFD5CEBF),
    error = NeoError,
    onError = Color.White,
    errorContainer = NeoErrorContainer,
    onErrorContainer = NeoBlack
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9DC492),
    onPrimary = Color(0xFF12280D),
    primaryContainer = Color(0xFF2C4524),
    onPrimaryContainer = Color(0xFFD6E7D1),
    secondary = Pistachio,
    onSecondary = Color(0xFF12280D),
    secondaryContainer = Color(0xFF253321),
    onSecondaryContainer = Color(0xFFE5EFE2),
    background = Color(0xFF161A14),
    onBackground = Color(0xFFEFECE6),
    surface = Color(0xFF1F241C),
    onSurface = Color(0xFFEFECE6),
    surfaceVariant = Color(0xFF2B3227),
    onSurfaceVariant = Color(0xFFB5BFB0),
    outline = Color(0xFF424D3E),
    outlineVariant = Color(0xFF2E352A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun ChbTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    val base = Typography()
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            headlineMedium = base.headlineMedium.copy(fontFamily = Sarabun, fontWeight = FontWeight.Bold),
            titleLarge = base.titleLarge.copy(fontFamily = Sarabun, fontWeight = FontWeight.Bold),
            titleMedium = base.titleMedium.copy(fontFamily = Sarabun, fontWeight = FontWeight.SemiBold),
            titleSmall = base.titleSmall.copy(fontFamily = Sarabun, fontWeight = FontWeight.SemiBold),
            bodyLarge = base.bodyLarge.copy(fontFamily = Sarabun),
            bodyMedium = base.bodyMedium.copy(fontFamily = Sarabun),
            bodySmall = base.bodySmall.copy(fontFamily = Sarabun),
            labelLarge = base.labelLarge.copy(fontFamily = Sarabun, fontWeight = FontWeight.SemiBold),
            labelMedium = base.labelMedium.copy(fontFamily = Sarabun, fontWeight = FontWeight.Medium),
            labelSmall = base.labelSmall.copy(fontFamily = Sarabun, fontWeight = FontWeight.Medium)
        ),
        content = content
    )
}
