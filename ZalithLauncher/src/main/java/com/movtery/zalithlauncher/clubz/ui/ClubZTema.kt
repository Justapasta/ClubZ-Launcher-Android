package com.movtery.zalithlauncher.clubz.ui

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R

/** Club Z: mismos colores y fuentes que el launcher de PC (app/ui/style.css). */
object CZ {
    val acento = Color(0xFFA6FF3D)
    val acentoOsc = Color(0xFF5FAE12)
    val morado = Color(0xFF7B2CFF)
    val texto = Color(0xFFF1EAFF)
    val tenue = Color(0xFFB7A6D6)
    val fondo = Color(0xFF0D0420)
    val panel = Color(0xFF140829)
    val panel2 = Color(0xFF24104A)
    val borde = Color(0x38A6FF3D)
    val bordeTenue = Color(0x29BEA0FF)
    val peligro = Color(0xFFFF8A7A)
    val sobreAcento = Color(0xFF0C1803)

    val chakra = FontFamily(
        Font(R.font.clubz_chakra_medium, FontWeight.Medium),
        Font(R.font.clubz_chakra_medium, FontWeight.Normal),
        Font(R.font.clubz_chakra_semibold, FontWeight.SemiBold),
        Font(R.font.clubz_chakra_bold, FontWeight.Bold),
    )
    val pixel = FontFamily(Font(R.font.clubz_silkscreen))
    val mono = FontFamily(Font(R.font.clubz_mono))
}

/** Esquinas escalonadas tipo pixel, como .tarjeta-jugar del launcher de PC. */
fun pixelShape(paso: Dp = 4.dp): Shape = PixelShape(paso)

private class PixelShape(private val paso: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = with(density) { paso.toPx() }
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, 2 * p); lineTo(p, 2 * p); lineTo(p, p); lineTo(2 * p, p); lineTo(2 * p, 0f)
            lineTo(w - 2 * p, 0f); lineTo(w - 2 * p, p); lineTo(w - p, p); lineTo(w - p, 2 * p); lineTo(w, 2 * p)
            lineTo(w, h - 2 * p); lineTo(w - p, h - 2 * p); lineTo(w - p, h - p); lineTo(w - 2 * p, h - p); lineTo(w - 2 * p, h)
            lineTo(2 * p, h); lineTo(2 * p, h - p); lineTo(p, h - p); lineTo(p, h - 2 * p); lineTo(0f, h - 2 * p)
            close()
        }
        return Outline.Generic(path)
    }
}

/** Esquema de colores de todo el launcher (Ajustes, cuentas, etc.). Siempre oscuro, como el de PC. */
val clubZEsquema = darkColorScheme(
    primary = CZ.acento,
    onPrimary = CZ.sobreAcento,
    primaryContainer = Color(0xFF2E4A0E),
    onPrimaryContainer = Color(0xFFD9FFB0),
    inversePrimary = CZ.acentoOsc,
    secondary = Color(0xFFC59BFF),
    onSecondary = Color(0xFF2A0866),
    secondaryContainer = Color(0xFF3A1C6E),
    onSecondaryContainer = Color(0xFFEBDDFF),
    tertiary = Color(0xFFFFB86B),
    onTertiary = Color(0xFF3D2000),
    tertiaryContainer = Color(0xFF5A3410),
    onTertiaryContainer = Color(0xFFFFDDB8),
    background = CZ.fondo,
    onBackground = CZ.texto,
    surface = CZ.fondo,
    onSurface = CZ.texto,
    surfaceVariant = Color(0xFF2A1A47),
    onSurfaceVariant = CZ.tenue,
    surfaceTint = CZ.acento,
    inverseSurface = CZ.texto,
    inverseOnSurface = CZ.fondo,
    error = CZ.peligro,
    onError = Color(0xFF3B0A04),
    errorContainer = Color(0xFF6B1E14),
    onErrorContainer = Color(0xFFFFDAD4),
    outline = Color(0xFF6E5A92),
    outlineVariant = Color(0xFF3B2A5C),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2C1A4E),
    surfaceDim = CZ.fondo,
    surfaceContainerLowest = Color(0xFF090216),
    surfaceContainerLow = Color(0xFF140829),
    surfaceContainer = Color(0xFF1A0C33),
    surfaceContainerHigh = Color(0xFF22123F),
    surfaceContainerHighest = Color(0xFF2A1A4B),
)

/** Tipografía del launcher: Chakra Petch (la del launcher de PC). */
val clubZTipografia: Typography = Typography().let { t ->
    fun TextStyle.cz() = copy(fontFamily = CZ.chakra)
    Typography(
        displayLarge = t.displayLarge.cz(), displayMedium = t.displayMedium.cz(), displaySmall = t.displaySmall.cz(),
        headlineLarge = t.headlineLarge.cz(), headlineMedium = t.headlineMedium.cz(), headlineSmall = t.headlineSmall.cz(),
        titleLarge = t.titleLarge.cz().copy(fontWeight = FontWeight.Bold), titleMedium = t.titleMedium.cz().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.cz().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.cz(), bodyMedium = t.bodyMedium.cz(), bodySmall = t.bodySmall.cz(),
        labelLarge = t.labelLarge.cz().copy(fontWeight = FontWeight.SemiBold), labelMedium = t.labelMedium.cz(), labelSmall = t.labelSmall.cz(),
    )
}
