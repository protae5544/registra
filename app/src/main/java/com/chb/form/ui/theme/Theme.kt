package com.chb.form.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.chb.form.R

// UI font: Google Sarabun with real weights (no synthetic bold).
// NOTE: R.font.sarabun_regular (TH Sarabun New) is intentionally left for the PDF renderers.
val Sarabun = FontFamily(
    Font(R.font.sarabun_ui_regular, FontWeight.Normal),
    Font(R.font.sarabun_ui_medium, FontWeight.Medium),
    Font(R.font.sarabun_ui_semibold, FontWeight.SemiBold),
    Font(R.font.sarabun_ui_bold, FontWeight.Bold)
)

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

// Thai needs taller line boxes than Latin so stacked vowels/tone marks are not clipped.
private fun TextStyle.thai(
    size: TextUnit = fontSize,
    line: TextUnit = lineHeight,
    weight: FontWeight? = null
): TextStyle = copy(
    fontFamily = Sarabun,
    fontSize = size,
    lineHeight = line,
    fontWeight = weight ?: fontWeight
)

@Composable
fun ChbTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    val b = Typography()
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            displayLarge = b.displayLarge.thai(),
            displayMedium = b.displayMedium.thai(),
            displaySmall = b.displaySmall.thai(),
            headlineLarge = b.headlineLarge.thai(line = 44.sp, weight = FontWeight.Bold),
            headlineMedium = b.headlineMedium.thai(line = 38.sp, weight = FontWeight.Bold),
            headlineSmall = b.headlineSmall.thai(line = 34.sp, weight = FontWeight.Bold),
            titleLarge = b.titleLarge.thai(line = 30.sp, weight = FontWeight.Bold),
            titleMedium = b.titleMedium.thai(line = 24.sp, weight = FontWeight.SemiBold),
            titleSmall = b.titleSmall.thai(line = 22.sp, weight = FontWeight.SemiBold),
            bodyLarge = b.bodyLarge.thai(line = 26.sp),
            bodyMedium = b.bodyMedium.thai(size = 15.sp, line = 23.sp),
            bodySmall = b.bodySmall.thai(size = 13.sp, line = 19.sp),
            labelLarge = b.labelLarge.thai(line = 22.sp, weight = FontWeight.SemiBold),
            labelMedium = b.labelMedium.thai(size = 13.sp, line = 18.sp, weight = FontWeight.Medium),
            labelSmall = b.labelSmall.thai(size = 12.sp, line = 17.sp, weight = FontWeight.Medium)
        ),
        content = content
    )
}
