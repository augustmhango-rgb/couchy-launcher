package com.conreo.couchytv.premium

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.conreo.couchytv.data.ConfigStore
import com.conreo.couchytv.data.LauncherConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.io.ByteArrayOutputStream
import java.io.File

/** Portable backups include private artwork. URI grants are limited to a single user-selected document. */
@Composable
internal fun BackupDialog(cfg: LauncherConfig, store: ConfigStore, onDismiss: () -> Unit, onRestored: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Save a ZIP to a document provider or USB drive. Restoring replaces this launcher's preferences and icons. It never changes Couchy.") }
    val json = remember { Json { ignoreUnknownKeys = true; encodeDefaults = true } }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            status = runCatching {
                withContext(Dispatchers.IO) {
                    ZipOutputStream(requireNotNull(context.contentResolver.openOutputStream(uri))).use { zip ->
                        zip.putNextEntry(ZipEntry("config.json")); zip.write(json.encodeToString(cfg).toByteArray()); zip.closeEntry()
                        cfg.customIcons.values.distinct().forEach { name ->
                            IconFiles.file(context, name)?.takeIf { it.exists() }?.let { file ->
                                zip.putNextEntry(ZipEntry("icons/$name")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                            }
                        }
                        File(context.filesDir, "wallpaper.jpg").takeIf { it.exists() && it.length() < 16 * 1024 * 1024 }?.let { file ->
                            zip.putNextEntry(ZipEntry("wallpaper.jpg")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                        }
                    }
                }; "Backup saved. Local video files are not included; choose them again after restoring on another device."
            }.getOrElse { "Backup failed: ${it.message}. This Fire OS may not provide a document picker." }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            status = runCatching {
                withContext(Dispatchers.IO) {
                    val entries = mutableMapOf<String, ByteArray>()
                    var total = 0
                    ZipInputStream(requireNotNull(context.contentResolver.openInputStream(uri))).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            require(entries.size < 250 && !entries.containsKey(entry.name)) { "Too many or duplicate backup entries" }
                            require(entry.name == "config.json" || entry.name == "wallpaper.jpg" ||
                                (entry.name.startsWith("icons/") && IconFiles.file(context, entry.name.removePrefix("icons/")) != null)) { "Invalid backup path" }
                            val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                            while (true) { val n = zip.read(buffer); if (n < 0) break; total += n
                                require(total <= 48 * 1024 * 1024 && out.size() + n <= 16 * 1024 * 1024) { "Backup too large" }; out.write(buffer, 0, n) }
                            entries[entry.name] = out.toByteArray()
                        }
                    }
                    val restored = json.decodeFromString<LauncherConfig>(requireNotNull(entries["config.json"]).toString(Charsets.UTF_8))
                    val remapped = restored.customIcons.mapValues { (_, name) -> IconFiles.save(context, requireNotNull(entries["icons/$name"])) }
                    entries["wallpaper.jpg"]?.let { File(context.filesDir, "wallpaper.jpg").writeBytes(it) }
                    store.update { restored.copy(customIcons = remapped, startOnBoot = false, useVideoWallpaper = false, useBuiltinAerials = false) }
                    context.getSharedPreferences("startup", 0).edit().putBoolean("enabled", false).apply()
                }; onRestored(); "Backup restored. Startup remains off until you enable it."
            }.getOrElse { "Restore failed: ${it.message}" }
        }
    }
    ChoiceDialog("Backup & Restore\n\n$status", listOf(
        "Save Backup" to { runCatching { export.launch("apple-tv-home-backup.zip") }.onFailure { status = "Document picker unavailable on this device" } },
        "Restore Backup (replaces settings)" to { runCatching { restore.launch(arrayOf("application/zip", "application/octet-stream")) }.onFailure { status = "Document picker unavailable on this device" } }
    ), onDismiss)
}
