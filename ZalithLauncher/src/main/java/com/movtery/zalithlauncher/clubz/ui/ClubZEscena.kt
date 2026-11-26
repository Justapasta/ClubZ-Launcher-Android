package com.movtery.zalithlauncher.clubz.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.movtery.zalithlauncher.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Escena del launcher de PC (app/ui/escena.js) en Compose: cielo, colinas, zombi, marco de pasto,
 * luna que late, niebla, esporas que suben. El parallax sigue la inclinación del celular.
 */
@Composable
fun ClubZEscena(modifier: Modifier = Modifier, animar: Boolean = true) {
    val cielo = ImageBitmap.imageResource(R.drawable.clubz_capa1)
    val colinas = ImageBitmap.imageResource(R.drawable.clubz_capa2)
    val marco = ImageBitmap.imageResource(R.drawable.clubz_capa3)

    var t by remember { mutableFloatStateOf(0f) }        // segundos
    var cx by remember { mutableFloatStateOf(0f) }       // parallax suavizado -1..1
    var cy by remember { mutableFloatStateOf(0f) }
    val inclinacion = rememberInclinacion()
    val zombi = remember { ZombiEstado() }
    val esporas = remember { mutableListOf<Espora>() }

    LaunchedEffect(animar) {
        if (!animar) return@LaunchedEffect
        var antes = withFrameNanos { it }
        while (true) {
            val ahora = withFrameNanos { it }
            val dt = min(0.05f, (ahora - antes) / 1e9f)
            antes = ahora
            t += dt
            val vaiven = sin(t / 5.2f) * 0.18f
            val k = min(1f, dt * 3f)
            cx += (inclinacion.x + vaiven - cx) * k
            cy += (inclinacion.y - cy) * k
            zombi.paso(dt)
            esporas.forEach { it.paso(dt) }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        // Escenario 16:9 que siempre cubre la pantalla con margen para el parallax (como .escenario)
        val ew = max(w * 1.08f, h * 1.08f * 16f / 9f)
        val eh = ew * 9f / 16f
        val ex = (w - ew) / 2f
        val ey = h - eh + h * 0.02f

        if (esporas.isEmpty() && w > 0) {
            val n = ((w * h) / 60000f).roundToInt().coerceIn(18, 70)
            repeat(n) { esporas += Espora(w, h, true) }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Color(0xFF12052A))
            // Capa 1: cielo, respira despacio
            val respira = 1f + 0.025f * ((sin(t / 26f * 3.14159f) + 1f) / 2f)
            capa(0.22f, cx, cy) {
                withTransform({ scale(respira, respira, pivot = Offset(ex + ew * 0.85f, ey + eh * 0.18f)) }) {
                    imagen(cielo, ex, ey, ew, eh)
                }
                // brillo de la luna
                val pulso = (sin(t * 1.047f) + 1f) / 2f
                val r = ew * 0.09f * (1f + 0.08f * pulso)
                drawCircle(
                    Brush.radialGradient(
                        0f to Color(0x61E4AAFF).copy(alpha = 0.38f * (1f - 0.45f * pulso)),
                        0.35f to Color(0x24A050FF),
                        0.68f to Color.Transparent,
                        center = Offset(ex + ew * 0.854f, ey + eh * 0.183f), radius = r
                    ), radius = r, center = Offset(ex + ew * 0.854f, ey + eh * 0.183f)
                )
            }
            // Capa 2: colinas + niebla morada
            capa(0.5f, cx, cy) {
                imagen(colinas, ex, ey, ew, eh)
                niebla(ex, ey + eh * 0.60f, ew, eh * 0.26f, t, 48f, listOf(Color(0x599646FF), Color(0x4D7832E6)), 0.55f)
            }
            // Capa 3: zombi en el sendero + niebla verde
            capa(0.78f, cx, cy) {
                val rx = ex + ew * 0.41f
                val rw = ew * 0.31f
                val zh = eh * 0.12f
                val zw = zh * 17f / 25f
                val zx = rx + zombi.x * (rw - zw)
                val zy = ey + eh * (1f - 0.074f) - zh
                dibujarZombi(zx, zy, zw, zh, zombi.frame, zombi.dir)
                niebla(ex, ey + eh * 0.80f, ew, eh * 0.18f, t, 34f, listOf(Color(0x1FA6FF3D), Color(0x478C3CFF)), 0.7f)
            }
            // Capa 4: marco de pasto
            capa(1f, cx, cy) { imagen(marco, ex, ey, ew, eh) }

            // Esporas
            esporas.forEachIndexed { i, p ->
                if (p.y < -10f) esporas[i] = Espora(size.width, size.height, false)
                val x = p.x + sin(p.fase) * p.amp - cx * 34f * p.prof
                val y = p.y - cy * 14f * p.prof
                val s = sin(p.fase * 2.3f)
                val a = (p.vida * (0.45f + 0.55f * s * s) * min(1f, (size.height - p.y) / 120f)).coerceIn(0f, 1f)
                drawRect(p.color.copy(alpha = a * 0.25f), Offset(x - p.t, y - p.t), Size(p.t * 3, p.t * 3))
                drawRect(p.color.copy(alpha = a), Offset(x, y), Size(p.t, p.t))
            }

            // Velo: oscurece arriba y en los bordes para que se lea el texto (como .velo)
            drawRect(Brush.verticalGradient(0f to Color(0xA60A021A), 0.16f to Color.Transparent))
            drawRect(
                Brush.radialGradient(
                    0.6f to Color.Transparent, 1f to Color(0x8C060010),
                    center = Offset(size.width / 2, size.height / 2), radius = max(size.width, size.height) * 0.75f
                )
            )
            drawRect(
                Brush.radialGradient(
                    0f to Color(0x8C0A021A), 1f to Color.Transparent,
                    center = Offset(size.width * 0.24f, size.height * 0.30f), radius = size.width * 0.42f
                )
            )
        }
    }
}

private fun DrawScope.capa(prof: Float, cx: Float, cy: Float, dibujo: DrawScope.() -> Unit) {
    val d = density
    withTransform({ translate(-cx * 26f * d * prof, -cy * 12f * d * prof) }) { dibujo() }
}

private fun DrawScope.imagen(img: ImageBitmap, x: Float, y: Float, w: Float, h: Float) {
    drawImage(
        img,
        srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()), dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        filterQuality = FilterQuality.None
    )
}

private fun DrawScope.niebla(x: Float, y: Float, w: Float, h: Float, t: Float, periodo: Float, colores: List<Color>, alfa: Float) {
    val deriva = sin(t / periodo * 3.14159f) * w * 0.06f
    val puntos = listOf(0.2f, 0.55f, 0.85f)
    puntos.forEachIndexed { i, px ->
        val c = Offset(x + w * px + deriva, y + h * 0.6f)
        val r = w * 0.2f
        drawOval(
            Brush.radialGradient(0f to colores[i % colores.size].copy(alpha = colores[i % colores.size].alpha * alfa), 1f to Color.Transparent, center = c, radius = r),
            topLeft = Offset(c.x - r, c.y - h * 0.5f), size = Size(r * 2, h)
        )
    }
}

// ---------------- Zombi (pixel art propio, 17x25, igual que el launcher de PC) ----------------

private val PAL = mapOf(
    'h' to Color(0xFF2A2618), 's' to Color(0xFF7FAE5C), 'S' to Color(0xFF5A8442), 'd' to Color(0xFF3E5E2E),
    'e' to Color(0xFFFF4040), 'E' to Color(0xFFFFD0D0), 'm' to Color(0xFF22301A), 'w' to Color(0xFF8E2F3A),
    't' to Color(0xFF3C7F8F), 'T' to Color(0xFF28596A), 'p' to Color(0xFF4B3A7D), 'P' to Color(0xFF33285A),
    'b' to Color(0xFF241C2C), 'k' to Color(0xFFC7FF7A),
)
private val CABEZA = listOf(
    "....hhhhhhh......", "...hhhhhhhhh.....", "...hsshssssh.....", "...ssssssEes.....", "...sSsssssss.....",
    "...ssdssssss.....", "...sssswmmms.....", "....sssmkmss.....", ".....dsssss......",
)
private val TORSO_A = listOf(
    ".....ttttt.......", "....tttttTTTsssss", "....ttttTTTSSssdS", "....tTtttt.......",
    "....ttTwtt.......", "....ttt.tt.......", "....tttttt.......", "....pppppp.......",
)
private val TORSO_B = listOf(
    ".....ttttt.......", "....tttttTTT.....", "....ttttTTTsssss.", "....tTttttSSssdS.",
    "....ttTwtt.......", "....ttt.tt.......", "....tttttt.......", "....pppppp.......",
)
private val PIERNAS_A = listOf(
    "....ppp.ppp......", "...ppp...ppp.....", "...pPp...pPp.....", "..ppp.....ppp....",
    "..pPp.....pPp....", "..ss.......ss....", ".bbbb......bbbb..", ".................",
)
private val PIERNAS_B = listOf(
    "....pppppp.......", ".....pppP........", ".....ppPp........", ".....pppp........",
    ".....pPpp........", ".....ssss........", ".....bbbbbb......", ".................",
)
private val FRAMES = listOf(
    CABEZA + TORSO_A + PIERNAS_A,
    (listOf(".................") + CABEZA + TORSO_B + PIERNAS_B).take(25),
)

private fun DrawScope.dibujarZombi(x: Float, y: Float, w: Float, h: Float, frame: Int, dir: Int) {
    val px = w / 17f
    val py = h / 25f
    val filas = FRAMES[frame]
    for (fy in filas.indices) {
        val fila = filas[fy]
        for (fx in fila.indices) {
            val c = PAL[fila[fx]] ?: continue
            val col = if (dir > 0) fx else 16 - fx
            drawRect(c, Offset(x + col * px, y + fy * py), Size(px + 0.6f, py + 0.6f))
        }
    }
}

private class ZombiEstado {
    var x = 0.15f
    var dir = 1
    var pausa = 0f
    var frame = 0
    var reloj = 0f
    fun paso(dt: Float) {
        if (pausa > 0) { pausa -= dt; frame = 0; return }
        x += dir * 0.03f * dt
        if (x > 1f || x < 0f) { x = x.coerceIn(0f, 1f); dir = -dir; pausa = 1.5f + Random.nextFloat() * 2f }
        else if (Random.nextFloat() < dt * 0.06f) pausa = 1f + Random.nextFloat() * 2.5f
        reloj += dt
        if (reloj > 0.32f) { reloj = 0f; frame = frame xor 1 }
    }
}

// ---------------- Esporas ----------------

private class Espora(w: Float, h: Float, inicio: Boolean) {
    val x = Random.nextFloat() * w
    var y = if (inicio) Random.nextFloat() * h else h + 10f
    val t = (if (Random.nextFloat() < 0.8f) 2f else 3f) * 2.2f
    val vy = (6f + Random.nextFloat() * 14f) * 2.2f
    var fase = Random.nextFloat() * 6.28f
    val amp = (6f + Random.nextFloat() * 18f) * 2f
    val prof = 0.4f + Random.nextFloat() * 0.6f
    val color = if (Random.nextFloat() < 0.55f) Color(0xFFA6FF3D) else Color(0xFFC48CFF)
    val vida = 0.4f + Random.nextFloat() * 0.6f
    fun paso(dt: Float) { y -= vy * dt; fase += dt * 0.9f }
}

// ---------------- Inclinación del celular para el parallax ----------------

private class Inclinacion { var x by mutableFloatStateOf(0f); var y by mutableFloatStateOf(0f) }

@Composable
private fun rememberInclinacion(): Inclinacion {
    val ctx = LocalContext.current
    val inc = remember { Inclinacion() }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var base: FloatArray? = null
        val oyente = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                @Suppress("DEPRECATION")
                val rot = (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
                // ejes de pantalla según la rotación (el launcher va acostado)
                val (sx, sy) = when (rot) {
                    Surface.ROTATION_90 -> e.values[1] to e.values[0]
                    Surface.ROTATION_270 -> -e.values[1] to -e.values[0]
                    Surface.ROTATION_180 -> -e.values[0] to e.values[1]
                    else -> e.values[0] to -e.values[1]
                }
                val b = base ?: floatArrayOf(sx, sy).also { base = it }
                // la posición "neutra" se adapta despacio a cómo lo sostiene el jugador
                b[0] += (sx - b[0]) * 0.01f
                b[1] += (sy - b[1]) * 0.01f
                inc.x = ((sx - b[0]) / 3.5f).coerceIn(-1f, 1f)
                inc.y = (-(sy - b[1]) / 3.5f).coerceIn(-1f, 1f)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(oyente, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(oyente) }
    }
    return inc
}
