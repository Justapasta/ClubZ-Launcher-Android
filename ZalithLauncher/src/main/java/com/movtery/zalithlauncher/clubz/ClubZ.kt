package com.movtery.zalithlauncher.clubz

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.setting.RendererSettings
import com.movtery.zalithlauncher.viewmodel.ModpackImportOperation
import com.movtery.zalithlauncher.viewmodel.ModpackImportViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * CLUB Z (cambio sobre Zalith Launcher 2, GPL-3.0).
 *
 * Al abrir el launcher lee el MANIFIESTO del VPS:
 *   {"version": n, "mrpack": url, "servidor": "ip:puerto", "modpack": "3.0.x", "apk_version": n, "apk": url}
 * - Primera vez (o cambió NeoForge/Minecraft): instala el .mrpack con el importador del launcher (versión "Club Z").
 * - Pack nuevo con el mismo NeoForge: SINCRONIZA solo los archivos que cambiaron (sha1) y borra los mods que ya no van.
 * - APK nuevo: avisa en la pantalla principal y lo instala con un toque.
 * - Ajusta la versión al celular: renderer (MobileGlues o Zink), RAM, resolución, rendimiento sostenido.
 */
object ClubZ {
    private const val TAG = "ClubZ"
    const val MANIFIESTO = "http://194.163.149.70:8090/launcher/movil/clubz.json"
    const val VERSION = "Club Z"
    const val SERVIDOR = "194.163.149.70:25566"
    private const val PREFS = "clubz"

    private const val MOBILEGLUES = "acaf8100-bbaf-4d68-b30d-38bf42fcd640"
    private const val ZINK = "0fa435e2-46df-45c9-906c-b29606aaef00"

    // ---------------- Estado para la pantalla principal ----------------

    data class Estado(
        val fase: Fase = Fase.REVISANDO,
        val texto: String = "Revisando actualizaciones…",
        /** 0..1, o null = barra indeterminada */
        val progreso: Float? = null,
        val packVersion: String? = null,
    )

    enum class Fase { REVISANDO, DESCARGANDO, INSTALANDO, LISTO, SIN_CONEXION, ERROR }

    data class ApkNuevo(val version: Int, val url: String, var bajando: Boolean = false, val progreso: Float = 0f)

    data class Celular(val ramMb: Int, val gles: Int, val vulkan: Boolean, val nivel: Nivel) {
        val glesTexto get() = "${gles shr 16}.${gles and 0xFFFF}"
    }

    enum class Nivel { BAJO, MEDIO, ALTO }

    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado

    private val _apk = MutableStateFlow<ApkNuevo?>(null)
    val apk: StateFlow<ApkNuevo?> = _apk

    private val _celular = MutableStateFlow<Celular?>(null)
    val celular: StateFlow<Celular?> = _celular

    @Volatile private var trabajando = false
    private var importador: ModpackImportViewModel? = null

    private fun fijar(fase: Fase, texto: String, progreso: Float? = null) {
        _estado.value = _estado.value.copy(fase = fase, texto = texto, progreso = progreso)
    }

    // ---------------- Entrada ----------------

    fun revisar(activity: ComponentActivity, importador: ModpackImportViewModel) {
        this.importador = importador
        detectarCelular(activity)
        actualizar(activity, forzarVerificacion = false)
    }

    /** Botón "Reparar": vuelve a comprobar cada archivo (sin usar la caché de sha1) y baja lo dañado. */
    fun reparar(activity: ComponentActivity) = actualizar(activity, forzarVerificacion = true)

    private fun actualizar(activity: ComponentActivity, forzarVerificacion: Boolean) {
        if (trabajando) return
        trabajando = true
        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                fijar(Fase.REVISANDO, "Revisando actualizaciones…")
                val json = try {
                    JSONObject(leer(MANIFIESTO))
                } catch (e: Exception) {
                    Log.w(TAG, "Sin manifiesto", e)
                    VersionsManager.waitForRefresh()
                    if (VersionsManager.isVersionExists(VERSION)) {
                        configurar(activity, SERVIDOR)
                        fijar(Fase.SIN_CONEXION, "Sin conexión con Club Z: se juega con lo que ya tienes")
                    } else {
                        fijar(Fase.SIN_CONEXION, "Sin conexión: conéctate a internet para bajar Club Z")
                    }
                    return@launch
                }
                val version = json.getInt("version")
                val url = json.getString("mrpack")
                val servidor = json.optString("servidor", SERVIDOR)
                _estado.value = _estado.value.copy(packVersion = json.optString("modpack").ifBlank { null })
                revisarApk(activity, json)

                val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                VersionsManager.waitForRefresh()
                val existe = VersionsManager.isVersionExists(VERSION)
                val tengo = prefs.getInt("version", 0)

                if (existe && tengo >= version && !forzarVerificacion) {
                    configurar(activity, servidor)
                    fijar(Fase.LISTO, "Listo para jugar")
                    return@launch
                }

                fijar(Fase.DESCARGANDO, "Bajando la lista del modpack…")
                val archivo = File(activity.cacheDir, "clubz.mrpack")
                bajar(url, archivo)
                val indice = ZipFile(archivo).use { z ->
                    JSONObject(z.getInputStream(z.getEntry("modrinth.index.json")).bufferedReader().readText())
                }
                val deps = indice.getJSONObject("dependencies")
                val base = deps.optString("minecraft") + "|" + deps.optString("neoforge")
                val v = VersionsManager.versions.value.firstOrNull { it.getVersionName() == VERSION }

                // Instalaciones del APK viejo no guardaban "base": se asume la del pack (mismo NeoForge desde el inicio)
                if (existe && prefs.getString("base", null) == null) prefs.edit().putString("base", base).apply()
                if (existe && v != null && prefs.getString("base", null) == base) {
                    // Mismo Minecraft y NeoForge: solo lo que cambió
                    sincronizar(activity, archivo, indice, v.getGameDir(), forzarVerificacion)
                    prefs.edit().putInt("version", version).apply()
                    configurar(activity, servidor)
                    fijar(Fase.LISTO, if (forzarVerificacion) "Archivos verificados: listo para jugar" else "Actualizado: listo para jugar")
                } else {
                    instalarCompleto(activity, archivo, existe, version, base, servidor)
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo actualizar Club Z", e)
                fijar(Fase.ERROR, "Error al actualizar: ${e.message ?: e.javaClass.simpleName}. Toca REPARAR.")
            } finally {
                trabajando = false
            }
        }
    }

    // ---------------- Instalación completa (primera vez o cambio de NeoForge) ----------------

    private suspend fun instalarCompleto(
        activity: ComponentActivity, archivo: File, existe: Boolean, version: Int, base: String, servidor: String
    ) {
        val imp = importador ?: return
        fijar(Fase.INSTALANDO, if (existe) "Instalando la nueva versión de Club Z…" else "Instalando Club Z por primera vez (tarda unos minutos)…")
        withContext(Dispatchers.Main) {
            if (existe) VersionsManager.versions.value.firstOrNull { it.getVersionName() == VERSION }?.let { VersionsManager.deleteVersion(it) }
            imp.nombreAutomatico = VERSION
            imp.import(activity, Uri.fromFile(archivo), onStop = {
                imp.nombreAutomatico = null
                if (imp.importOperation is ModpackImportOperation.Finished) {
                    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putInt("version", version).putString("base", base).apply()
                    activity.lifecycleScope.launch(Dispatchers.IO) {
                        VersionsManager.waitForRefresh()
                        configurar(activity, servidor)
                        fijar(Fase.LISTO, "Club Z instalado: listo para jugar")
                    }
                } else {
                    fijar(Fase.ERROR, "La instalación no terminó. Toca REPARAR para intentar de nuevo.")
                }
            })
        }
    }

    // ---------------- Sincronización incremental ----------------

    private fun sincronizar(ctx: Context, archivo: File, indice: JSONObject, juego: File, forzar: Boolean) {
        val cacheFile = File(juego, ".clubz_sha1.json")
        val cache = if (!forzar && cacheFile.exists()) runCatching { JSONObject(cacheFile.readText()) }.getOrNull() ?: JSONObject() else JSONObject()
        val nuevaCache = JSONObject()
        val files = indice.getJSONArray("files")
        val pendientes = mutableListOf<Triple<String, String, Long>>() // ruta, url, tamaño
        val rutas = HashSet<String>()

        fijar(Fase.DESCARGANDO, "Comparando archivos…", 0f)
        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            val ruta = f.getString("path")
            if (ruta.contains("..")) continue
            rutas += ruta
            val sha1 = f.getJSONObject("hashes").getString("sha1")
            val local = File(juego, ruta)
            val ok = local.isFile && run {
                val clave = "${local.length()}:${local.lastModified()}"
                val c = cache.optJSONObject(ruta)
                val actual = if (c != null && c.optString("k") == clave) c.optString("s") else sha1De(local)
                nuevaCache.put(ruta, JSONObject().put("k", "${local.length()}:${local.lastModified()}").put("s", actual))
                actual == sha1
            }
            if (!ok) pendientes += Triple(ruta, f.getJSONArray("downloads").getString(0), f.optLong("fileSize", 0))
            if (i % 20 == 0) fijar(Fase.DESCARGANDO, "Comparando archivos…", i.toFloat() / files.length())
        }

        // Mods que ya no están en el pack (el server no los acepta): fuera
        File(juego, "mods").listFiles()?.forEach { m ->
            if (m.isFile && m.name.endsWith(".jar") && "mods/${m.name}" !in rutas) m.delete()
        }

        val total = pendientes.sumOf { it.third }.coerceAtLeast(1)
        var hecho = 0L
        pendientes.forEachIndexed { n, (ruta, url, tam) ->
            val destino = File(juego, ruta).apply { parentFile?.mkdirs() }
            fijar(Fase.DESCARGANDO, "Bajando ${n + 1} de ${pendientes.size}: ${destino.name}", hecho.toFloat() / total)
            bajar(url, destino) { leidos -> fijar(Fase.DESCARGANDO, "Bajando ${n + 1} de ${pendientes.size}: ${destino.name}", (hecho + leidos).toFloat() / total) }
            hecho += tam
            nuevaCache.put(ruta, JSONObject().put("k", "${destino.length()}:${destino.lastModified()}").put("s", sha1De(destino)))
        }

        // overrides/ del .mrpack
        ZipFile(archivo).use { z ->
            z.entries().asSequence().filter { !it.isDirectory && it.name.startsWith("overrides/") && !it.name.contains("..") }.forEach { e ->
                val destino = File(juego, e.name.removePrefix("overrides/")).apply { parentFile?.mkdirs() }
                // options.txt solo la primera vez: no se pisan los ajustes que el jugador cambió
                if (destino.name == "options.txt" && destino.exists()) return@forEach
                z.getInputStream(e).use { inp -> destino.outputStream().use { inp.copyTo(it) } }
            }
        }
        cacheFile.writeText(nuevaCache.toString())
        Log.i(TAG, "Sincronizado: ${pendientes.size} archivos bajados")
    }

    private fun sha1De(f: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        f.inputStream().use { inp ->
            val b = ByteArray(1 shl 16)
            while (true) { val n = inp.read(b); if (n < 0) break; md.update(b, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // ---------------- Celular: detectar y ajustar ----------------

    private fun detectarCelular(ctx: Context) {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val ram = (mi.totalMem / (1024 * 1024)).toInt().takeIf { it > 0 } ?: ramTotalMb()
        val gles = am.deviceConfigurationInfo.reqGlEsVersion
        val vulkan = ctx.packageManager.hasSystemFeature("android.hardware.vulkan.version")
        val nivel = when {
            ram < 5000 -> Nivel.BAJO
            ram < 7500 -> Nivel.MEDIO
            else -> Nivel.ALTO
        }
        _celular.value = Celular(ram, gles, vulkan, nivel)

        // Ajustes globales una sola vez (después el jugador los puede cambiar en Ajustes)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("ajustes_iniciales_2", false)) {
            runCatching {
                // Resolución interna: los celulares tienen pantallas enormes; dibujar menos píxeles = muchos más FPS
                RendererSettings.resolutionRatio.save(when (nivel) { Nivel.BAJO -> 55; Nivel.MEDIO -> 70; Nivel.ALTO -> 85 })
                RendererSettings.sustainedPerformance.save(true)
                RendererSettings.renderer.save(rendererPara(gles, vulkan))
            }
            prefs.edit().putBoolean("ajustes_iniciales_2", true).apply()
        }
    }

    /** MobileGlues necesita OpenGL ES 3.x; si el celular no lo tiene pero sí Vulkan, Zink. */
    private fun rendererPara(gles: Int, vulkan: Boolean) = if (gles < 0x30000 && vulkan) ZINK else MOBILEGLUES

    /** Versión Club Z elegida, con entrada directa al servidor, marca de celular, renderer y RAM. */
    private fun configurar(ctx: Context, servidor: String) {
        val v = VersionsManager.versions.value.firstOrNull { it.getVersionName() == VERSION } ?: return
        val cel = _celular.value
        val c = v.getVersionConfig()
        var cambio = false
        if (c.serverIp != servidor) { c.serverIp = servidor; cambio = true }
        var args = c.jvmArgs
        if (!args.contains("clubz.movil")) args = "$args -Dclubz.movil=true"
        // GC con pausas cortas (menos tirones)
        if (!args.contains("UseG1GC")) args = "$args -XX:+UseG1GC -XX:MaxGCPauseMillis=40 -XX:+UseStringDeduplication"
        if (args.trim() != c.jvmArgs) { c.jvmArgs = args.trim(); cambio = true }
        val renderer = rendererPara(cel?.gles ?: 0x30002, cel?.vulkan ?: false)
        if (c.renderer != renderer) { c.renderer = renderer; cambio = true }
        // RAM: ~140 mods. Mitad de la RAM del celular, entre 2 y 4 GB (con menos de 2 GB el juego se queda sin memoria)
        val total = cel?.ramMb ?: ramTotalMb()
        if (total > 0) {
            val ram = (total / 2).coerceIn(2048, 4096)
            if (c.ramAllocation < 2048 || c.ramAllocation > total - 1024) { c.ramAllocation = ram; cambio = true }
        }
        if (cambio) c.save()
        VersionsManager.saveCurrentVersion(VERSION)
    }

    private fun ramTotalMb(): Int = try {
        File("/proc/meminfo").readLines().first { it.startsWith("MemTotal") }
            .filter { it.isDigit() }.toLong().div(1024).toInt()
    } catch (e: Exception) { -1 }

    // ---------------- APK nuevo ----------------

    private fun versionApk(ctx: Context): Long = runCatching {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }.getOrDefault(0L)

    private fun revisarApk(ctx: Context, json: JSONObject) {
        val nueva = json.optInt("apk_version", 0)
        val url = json.optString("apk")
        _apk.value = if (nueva > versionApk(ctx) && url.isNotBlank()) ApkNuevo(nueva, url) else null
    }

    fun instalarApk(activity: ComponentActivity) {
        val info = _apk.value ?: return
        if (info.bajando) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            // Android pide permiso para instalar apps desde el launcher (una sola vez)
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            return
        }
        _apk.value = info.copy(bajando = true, progreso = 0f)
        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val destino = File(activity.cacheDir, "ClubZ-Celular.apk")
                var total = 1L
                bajar(info.url, destino, alAbrir = { total = it.coerceAtLeast(1) }) { leidos ->
                    _apk.value = info.copy(bajando = true, progreso = leidos.toFloat() / total)
                }
                val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.provider", destino)
                withContext(Dispatchers.Main) {
                    activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
                _apk.value = info.copy(bajando = false, progreso = 1f)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo bajar el APK", e)
                _apk.value = info.copy(bajando = false, progreso = 0f)
            }
        }
    }

    // ---------------- Red ----------------

    private fun conexion(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("User-Agent", "ClubZ-Launcher-Android")
        }

    private fun leer(url: String): String = conexion(url).inputStream.use { it.readBytes().toString(Charsets.UTF_8) }

    /** Baja a un .tmp y lo mueve al final (un corte de internet no deja archivos a medias). Reintenta 3 veces. */
    private fun bajar(url: String, destino: File, alAbrir: (Long) -> Unit = {}, progreso: (Long) -> Unit = {}) {
        val tmp = File(destino.parentFile, destino.name + ".tmp")
        var ultimo: Exception? = null
        repeat(3) { intento ->
            try {
                val con = conexion(url)
                alAbrir(con.contentLengthLong)
                con.inputStream.use { entrada ->
                    tmp.outputStream().use { salida ->
                        val b = ByteArray(1 shl 16)
                        var leidos = 0L
                        var aviso = 0L
                        while (true) {
                            val n = entrada.read(b)
                            if (n < 0) break
                            salida.write(b, 0, n)
                            leidos += n
                            if (leidos - aviso > 256 * 1024) { aviso = leidos; progreso(leidos) }
                        }
                    }
                }
                if (destino.exists()) destino.delete()
                if (!tmp.renameTo(destino)) { tmp.copyTo(destino, true); tmp.delete() }
                return
            } catch (e: Exception) {
                ultimo = e
                Log.w(TAG, "Descarga falló (intento ${intento + 1}): $url", e)
                Thread.sleep(1500L * (intento + 1))
            }
        }
        tmp.delete()
        throw ultimo ?: IllegalStateException("No se pudo bajar $url")
    }
}
