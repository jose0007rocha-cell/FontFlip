package app.fontflip.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext

@Composable
fun FontFlipTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    // A fonte do sistema pode ser larga (monoespaçada) e o texto do Android pode estar ampliado:
    // limita a escala a 1.1 e usa tamanhos um pouco menores para a tela não estourar.
    val density = LocalDensity.current
    val base = Typography()
    val compact = base.copy(
        headlineSmall = base.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = base.titleMedium.copy(fontSize = 17.sp, lineHeight = 23.sp),
        titleSmall = base.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
        bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = base.labelLarge.copy(fontSize = 15.sp),
        labelMedium = base.labelMedium.copy(fontSize = 12.sp),
    )
    CompositionLocalProvider(LocalDensity provides Density(density.density, minOf(density.fontScale, 1.1f))) {
        MaterialTheme(colorScheme = scheme, typography = compact, content = content)
    }
}
