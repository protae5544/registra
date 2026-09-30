package com.chb.form.ui.theme
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.chb.form.R
val Sarabun = FontFamily(Font(R.font.sarabun_regular))
private val Light = lightColorScheme( primary = Color(0xFF00629D), onPrimary = Color.White, primaryContainer = Color(0xFFCFE5FF), onPrimaryContainer = Color(0xFF001D33) )
private val Dark = darkColorScheme( primary = Color(0xFF99CBFF), onPrimary = Color(0xFF003354), primaryContainer = Color(0xFF004A77), onPrimaryContainer = Color(0xFFCFE5FF) )
@Composable
fun ChbTheme(content: @Composable () -> Unit) { val dark = isSystemInDarkTheme()
val ctx = LocalContext.current
val colors = when { Build.VERSION.SDK_INT >= 31 -> if (dark)
dynamicDarkColorScheme(ctx) else
dynamicLightColorScheme(ctx)
dark -> Dark
else -> Light
}
val base = Typography()
MaterialTheme( colorScheme = colors, typography = Typography( titleLarge = base.titleLarge.copy(fontFamily = Sarabun), titleMedium = base.titleMedium.copy(fontFamily = Sarabun), bodyLarge = base.bodyLarge.copy(fontFamily = Sarabun), bodyMedium = base.bodyMedium.copy(fontFamily = Sarabun), bodySmall = base.bodySmall.copy(fontFamily = Sarabun), labelLarge = base.labelLarge.copy(fontFamily = Sarabun), labelSmall = base.labelSmall.copy(fontFamily = Sarabun) ), content = content ) }
