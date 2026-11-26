package com.movtery.zalithlauncher.clubz

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.viewmodel.ModpackImportOperation
import com.movtery.zalithlauncher.viewmodel.ModpackImportViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * CLUB Z (cambio sobre Zalith Launcher 2, GPL-3.0): al abrir el launcher revisa el modpack de celular de Club Z
 * (MANIFIESTO en el VPS: {"version": n, "mrpack": url, "servidor": "ip:puerto"}). Si falta o hay uno nuevo, lo baja y
 * lo instala con el importador de modpacks del propio launcher (versión "Club Z", con NeoForge), y deja la versión
 * elegida con entrada directa al servidor (serverIp) y la marca de celular (-Dclubz.movil=true, la lee Club Z Core).
 */
object ClubZ {
    private const val TAG = "ClubZ"
    const val MANIFIESTO = "http://194.163.149.70:8090/launcher/movil/clubz.json"
    const val VERSION = "Club Z"
    const val SERVIDOR = "194.163.149.70:25566"
    private const val PREFS = "clubz"

    fun revisar(activity: ComponentActivity, importador: ModpackImportViewModel) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject(leer(MANIFIESTO))
                val version = json.getInt("version")
                val url = json.getString("mrpack")
                val servidor = json.optString("servidor", SERVIDOR)
                val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                VersionsManager.waitForRefresh()
                val existe = VersionsManager.isVersionExists(VERSION)
                if (existe && prefs.getInt("version", 0) >= version) {
                    configurar(servidor)
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, if (existe) "Club Z: descargando actualización..." else "Club Z: descargando el modpack...", Toast.LENGTH_LONG).show()
                }
                val archivo = File(activity.cacheDir, "clubz.mrpack")
                bajar(url, archivo)
                withContext(Dispatchers.Main) {
                    if (existe) VersionsManager.versions.value.firstOrNull { it.getVersionName() == VERSION }?.let { VersionsManager.deleteVersion(it) }
                    importador.nombreAutomatico = VERSION
                    importador.import(activity, Uri.fromFile(archivo), onStop = {
                        importador.nombreAutomatico = null
                        if (importador.importOperation is ModpackImportOperation.Finished) {
                            prefs.edit().putInt("version", version).apply()
                            activity.lifecycleScope.launch(Dispatchers.IO) {
                                VersionsManager.waitForRefresh()
                                configurar(servidor)
                            }
                        }
                    })
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo revisar el modpack de Club Z", e)
            }
        }
    }

    /** Versión Club Z elegida, con entrada directa al servidor y la marca de celular. */
    private fun configurar(servidor: String) {
        val v = VersionsManager.versions.value.firstOrNull { it.getVersionName() == VERSION } ?: return
        val c = v.getVersionConfig()
        var cambio = false
        if (c.serverIp != servidor) { c.serverIp = servidor; cambio = true }
        if (!c.jvmArgs.contains("clubz.movil")) { c.jvmArgs = (c.jvmArgs + " -Dclubz.movil=true").trim(); cambio = true }
        if (cambio) c.save()
        VersionsManager.saveCurrentVersion(VERSION)
    }

    private fun conexion(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("User-Agent", "ClubZ-Launcher-Android")
        }

    private fun leer(url: String): String = conexion(url).inputStream.use { it.readBytes().toString(Charsets.UTF_8) }

    private fun bajar(url: String, destino: File) {
        val tmp = File(destino.parentFile, destino.name + ".tmp")
        conexion(url).inputStream.use { entrada -> tmp.outputStream().use { entrada.copyTo(it) } }
        if (destino.exists()) destino.delete()
        tmp.renameTo(destino)
    }
}
