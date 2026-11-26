package com.movtery.zalithlauncher.clubz.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.clubz.ClubZ
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.account.localLogin
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.game.version.multiplayer.ResolvedServerAddress
import com.movtery.zalithlauncher.game.version.multiplayer.pingServer
import com.movtery.zalithlauncher.ui.screens.content.elements.PlayerFace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress

private val LEMAS = listOf(
    "Sobrevive. Construye. No confíes en la IA.",
    "La noche es más larga en la Luna Roja.",
    "Los bandidos no avisan. Tú tampoco.",
    "Chris IA te está mirando.",
    "Engranajes, balas y paciencia.",
)

private data class EstadoServidor(val enLinea: Boolean, val jugadores: Int = 0, val maximo: Int = 0, val ping: Long = 0)

/** Pantalla principal de Club Z (va encima de [ClubZEscena]). */
@Composable
fun ClubZInicio(
    onJugar: () -> Unit,
    onCuentas: () -> Unit,
    onAjustesVersion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalContext.current as? ComponentActivity
    val estado by ClubZ.estado.collectAsStateWithLifecycle()
    val apk by ClubZ.apk.collectAsStateWithLifecycle()
    val cuenta by AccountsManager.currentAccountFlow.collectAsStateWithLifecycle()
    val version by VersionsManager.currentVersion.collectAsStateWithLifecycle()
    var pedirNick by remember { mutableStateOf(false) }

    val servidor by produceServidor()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val ancho = maxWidth
        // Logo + lema arriba a la izquierda
        Titulo(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = ancho * 0.05f, top = 4.dp)
                .width(minOf(ancho * 0.46f, 380.dp))
        )

        // Muelle de juego abajo a la derecha
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = ancho * 0.05f, bottom = 14.dp)
                .width(300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(visible = apk != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
                apk?.let { a ->
                    AvisoApk(
                        bajando = a.bajando, progreso = a.progreso,
                        onActualizar = { activity?.let { ClubZ.instalarApk(it) } }
                    )
                }
            }

            TarjetaJugar(
                nick = cuenta?.username,
                cabeza = { cuenta?.let { PlayerFace(account = it, avatarSize = 38.dp) } },
                estado = estado,
                servidor = servidor,
                puedeJugar = version != null && cuenta != null &&
                        estado.fase in setOf(ClubZ.Fase.LISTO, ClubZ.Fase.SIN_CONEXION, ClubZ.Fase.ERROR),
                onNick = { if (cuenta == null) pedirNick = true else onCuentas() },
                onJugar = { if (cuenta == null) pedirNick = true else onJugar() },
                onReparar = { activity?.let { ClubZ.reparar(it) } },
                onAjustes = onAjustesVersion,
            )
        }
    }

    if (pedirNick) DialogoNick(onListo = { nick -> localLogin(nick, null); pedirNick = false }, onCerrar = { pedirNick = false })
}

@Composable
private fun produceServidor() = androidx.compose.runtime.produceState<EstadoServidor?>(null) {
    val (host, puerto) = ClubZ.SERVIDOR.split(":").let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: 25565) }
    while (true) {
        value = try {
            val r = withContext(Dispatchers.IO) {
                pingServer(ResolvedServerAddress(host, host, puerto, InetSocketAddress(host, puerto)), protocolVersion = 767, timeoutMillis = 8000)
            }
            EstadoServidor(true, r.status.players.online, r.status.players.max, r.pingMs)
        } catch (e: Exception) {
            EstadoServidor(false)
        }
        delay(30_000)
    }
}

// ---------------- Logo y lema ----------------

@Composable
private fun Titulo(modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "titulo")
    val flota by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3500), RepeatMode.Reverse), label = "flota")
    val cursor by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "cursor")
    var lema by remember { mutableStateOf(LEMAS.first()) }
    LaunchedEffect(Unit) { var i = 0; while (true) { delay(9000); i = (i + 1) % LEMAS.size; lema = LEMAS[i] } }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Image(
            painter = painterResource(R.drawable.clubz_logo),
            contentDescription = "Club Z",
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    rotationZ = -3f + flota
                    translationY = -6.dp.toPx() * flota
                }
        )
        Row(
            modifier = Modifier
                .padding(start = 40.dp)
                .rotate(-3f)
                .background(Color(0x9E0D0420))
                .drawBehind { drawRect(CZ.acento, size = Size(3.dp.toPx(), size.height)) }
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(">", color = CZ.acento.copy(alpha = if (cursor < 0.5f) 1f else 0f), style = estiloMono(12.sp))
            Text(" $lema", color = Color(0xFFE6DCFF), style = estiloMono(12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------- Tarjeta de juego ----------------

@Composable
private fun TarjetaJugar(
    nick: String?,
    cabeza: @Composable () -> Unit,
    estado: ClubZ.Estado,
    servidor: EstadoServidor?,
    puedeJugar: Boolean,
    onNick: () -> Unit,
    onJugar: () -> Unit,
    onReparar: () -> Unit,
    onAjustes: () -> Unit,
) {
    val forma = pixelShape(4.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(18.dp, forma)
            .clip(forma)
            .background(Brush.linearGradient(listOf(Color(0xD1240E48), Color(0xDB0E0520)), start = Offset.Zero, end = Offset(600f, 900f)))
            .border(2.dp, CZ.borde, forma)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        // Quién juega
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(38.dp).background(Color(0xFF2B1D12)),
                contentAlignment = Alignment.Center
            ) {
                if (nick != null) cabeza() else Icon(painterResource(R.drawable.ic_add), null, tint = CZ.acento)
            }
            Column(modifier = Modifier.weight(1f).clickable(onClick = onNick)) {
                Text(
                    nick ?: "Elige tu nick",
                    color = CZ.texto, style = TextStyle(fontFamily = CZ.chakra, fontWeight = FontWeight.Bold, fontSize = 17.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (nick == null) "Toca para entrar a Club Z" else estado.texto,
                    color = when (estado.fase) {
                        ClubZ.Fase.ERROR -> CZ.peligro
                        ClubZ.Fase.LISTO -> CZ.tenue
                        else -> CZ.tenue
                    },
                    style = TextStyle(fontFamily = CZ.chakra, fontSize = 12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Progreso de descarga/instalación
        val trabajando = estado.fase in setOf(ClubZ.Fase.REVISANDO, ClubZ.Fase.DESCARGANDO, ClubZ.Fase.INSTALANDO)
        if (trabajando) BarraPixel(estado.progreso)

        // JUGAR
        BotonJugar(
            texto = when {
                nick == null -> "ENTRAR"
                estado.fase == ClubZ.Fase.INSTALANDO -> "INSTALANDO…"
                estado.fase == ClubZ.Fase.DESCARGANDO -> "ACTUALIZANDO…"
                estado.fase == ClubZ.Fase.REVISANDO -> "REVISANDO…"
                else -> "JUGAR"
            },
            activo = puedeJugar || nick == null,
            onClick = onJugar
        )

        // Servidor + acciones
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val enLinea = servidor?.enLinea == true
            val inf = rememberInfiniteTransition(label = "punto")
            val brillo by inf.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "brillo")
            Box(
                Modifier.size(8.dp).alpha(if (enLinea) brillo else 1f)
                    .background(if (servidor == null) CZ.tenue else if (enLinea) CZ.acento else CZ.peligro)
            )
            Text(
                when {
                    servidor == null -> "Consultando…"
                    enLinea -> "${servidor?.jugadores} / ${servidor?.maximo} en línea"
                    else -> "Servidor apagado"
                },
                color = CZ.texto, style = TextStyle(fontFamily = CZ.chakra, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp),
                modifier = Modifier.weight(1f), maxLines = 1
            )
            if (enLinea) Text("${servidor!!.ping} ms", color = CZ.tenue, style = estiloMono(11.sp))
            Chip("REPARAR", onReparar)
            Chip("⚙", onAjustes)
        }
        estado.packVersion?.let {
            Text("pack $it · celular", color = CZ.tenue.copy(alpha = 0.6f), style = estiloMono(10.sp))
        }
    }
}

@Composable
private fun BotonJugar(texto: String, activo: Boolean, onClick: () -> Unit) {
    val forma = pixelShape(4.dp)
    val inf = rememberInfiniteTransition(label = "jugar")
    val brillo by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "brillo")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(forma)
            .background(if (activo) CZ.acento else Color(0xFF3A3352))
            .drawBehind {
                if (activo) {
                    // destello que recorre el botón
                    val x = size.width * (brillo * 1.6f - 0.3f)
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Transparent, Color(0x66FFFFFF), Color.Transparent), startX = x - 60f, endX = x + 60f)
                    )
                    // sombra inferior tipo botón de Minecraft
                    drawRect(Color(0x8C285A00), topLeft = Offset(0f, size.height - 4.dp.toPx()), size = Size(size.width, 4.dp.toPx()))
                }
            }
            .clickable(enabled = activo, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_play_arrow_filled), null,
            tint = if (activo) CZ.sobreAcento else CZ.tenue, modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            texto, color = if (activo) CZ.sobreAcento else CZ.tenue,
            style = TextStyle(fontFamily = CZ.pixel, fontSize = 19.sp, letterSpacing = 1.5.sp)
        )
    }
}

@Composable
private fun BarraPixel(progreso: Float?) {
    val inf = rememberInfiniteTransition(label = "barra")
    val ir by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "ir")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .border(2.dp, CZ.acento.copy(alpha = 0.45f))
            .padding(3.dp)
            .drawBehind {
                if (progreso != null) {
                    drawRect(CZ.acento, size = Size(size.width * progreso.coerceIn(0f, 1f), size.height))
                } else {
                    val w = size.width * 0.3f
                    // avanza a saltos, como la barra del launcher de PC (steps(12))
                    val paso = (ir * 12).toInt() / 12f
                    drawRect(CZ.acento, topLeft = Offset((size.width - w) * paso, 0f), size = Size(w, size.height))
                }
            }
    )
}

@Composable
private fun Chip(texto: String, onClick: () -> Unit) {
    Text(
        texto,
        color = CZ.tenue,
        style = TextStyle(fontFamily = CZ.pixel, fontSize = 10.sp, letterSpacing = 0.8.sp),
        modifier = Modifier
            .border(1.dp, CZ.bordeTenue)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp)
    )
}

@Composable
private fun AvisoApk(bajando: Boolean, progreso: Float, onActualizar: () -> Unit) {
    val forma = pixelShape(3.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(Color(0xE6140829))
            .border(2.dp, CZ.acento.copy(alpha = 0.55f), forma)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("LAUNCHER NUEVO", color = CZ.acento, style = TextStyle(fontFamily = CZ.pixel, fontSize = 12.sp))
            Text(
                if (bajando) "Bajando… ${(progreso * 100).toInt()} %" else "Hay una versión nueva del launcher",
                color = CZ.tenue, style = TextStyle(fontFamily = CZ.chakra, fontSize = 12.sp)
            )
        }
        Text(
            if (bajando) "…" else "ACTUALIZAR",
            color = CZ.sobreAcento,
            style = TextStyle(fontFamily = CZ.pixel, fontSize = 11.sp),
            modifier = Modifier
                .background(CZ.acento)
                .clickable(enabled = !bajando, onClick = onActualizar)
                .padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

// ---------------- Primera vez: nick ----------------

private val NICK_OK = Regex("^[A-Za-z0-9_]{3,16}$")

@Composable
private fun DialogoNick(onListo: (String) -> Unit, onCerrar: () -> Unit) {
    var nick by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val forma = pixelShape(4.dp)
    Dialog(onDismissRequest = onCerrar) {
        Column(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .clip(forma)
                .background(Brush.linearGradient(listOf(Color(0xFF240E48), Color(0xFF0E0520))))
                .border(2.dp, CZ.borde, forma)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("> IDENTIFÍCATE, SUPERVIVIENTE", color = CZ.acento, style = estiloMono(11.sp))
            Text("Entrar a Club Z", color = CZ.texto, style = TextStyle(fontFamily = CZ.chakra, fontWeight = FontWeight.Bold, fontSize = 22.sp))
            OutlinedTextField(
                value = nick,
                onValueChange = { nick = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(16); error = null },
                label = { Text("Tu nick") },
                placeholder = { Text("Steve_123") },
                singleLine = true,
                isError = error != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CZ.acento, unfocusedBorderColor = CZ.bordeTenue,
                    focusedLabelColor = CZ.acento, cursorColor = CZ.acento,
                    focusedTextColor = CZ.texto, unfocusedTextColor = CZ.texto
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                error ?: "3 a 16 letras, números o _. En el servidor te pedirá tu contraseña (/login).",
                color = if (error != null) CZ.peligro else CZ.tenue, style = TextStyle(fontFamily = CZ.chakra, fontSize = 12.sp)
            )
            BotonJugar(texto = "CONTINUAR", activo = true) {
                if (NICK_OK.matches(nick)) onListo(nick) else error = "El nick debe tener de 3 a 16 letras, números o _"
            }
        }
    }
}

private fun estiloMono(tam: androidx.compose.ui.unit.TextUnit) = TextStyle(fontFamily = CZ.mono, fontSize = tam)
