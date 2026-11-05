@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.conreo.couchytv.premium

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.*
import com.conreo.couchytv.Actions
import com.conreo.couchytv.data.*
import com.conreo.couchytv.ui.*
import kotlinx.coroutines.*
import java.io.File

@Composable
fun PremiumHome(rescanTick: Int) {
    val context = LocalContext.current
    val store = remember { ConfigStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val cfg by store.flow.collectAsState(initial = LauncherConfig())
    var resumeTick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumeTick++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val scanned by produceState(AppRepository.memoryCache.orEmpty(), rescanTick, resumeTick) {
        value = withContext(Dispatchers.Default) { AppRepository.scan(context.applicationContext) }
    }
    val apps by produceState(scanned, scanned, cfg.customIcons, cfg.renamedApps) {
        value = withContext(Dispatchers.IO) {
            scanned.map { app ->
                val icon = cfg.customIcons[app.pkg]?.let { IconFiles.file(context, it) }
                    ?.let { BitmapFactory.decodeFile(it.path) }?.asImageBitmap()
                app.copy(label = cfg.renamedApps[app.pkg] ?: app.label,
                    banner = icon ?: app.banner, icon = icon ?: app.icon)
            }
        }
    }
    fun update(change: (LauncherConfig) -> LauncherConfig) { scope.launch { store.update(change) } }
    var selected by remember { mutableStateOf<AppEntry?>(null) }
    var menu by remember { mutableStateOf<AppEntry?>(null) }
    var upload by remember { mutableStateOf<AppEntry?>(null) }
    var settings by remember { mutableStateOf(false) }
    var legacySettings by remember { mutableStateOf(false) }
    var appPicker by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<AppEntry?>(null) }
    var rename by remember { mutableStateOf<AppEntry?>(null) }
    var categoryPicker by remember { mutableStateOf<AppEntry?>(null) }
    var activeCategory by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf(false) }
    var system by remember { mutableStateOf(false) }
    var backup by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var wallpaperVersion by remember { mutableIntStateOf(0) }
    val visible = remember(apps, cfg.hidden, cfg.homeOrder, activeCategory, cfg.sections, cfg.showHidden) {
        apps.filter { (cfg.showHidden || it.pkg !in cfg.hidden) &&
            (activeCategory == null || activeCategory in AppRepository.sectionsOf(it, cfg)) }
            .sortedBy { cfg.homeOrder.indexOf(it.pkg).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    }
    val favourites = remember(apps, cfg.favourites, cfg.hidden) {
        cfg.favourites.mapNotNull { pkg -> apps.find { it.pkg == pkg && pkg !in cfg.hidden } }.take(6)
            .ifEmpty { apps.filter { it.pkg !in cfg.hidden }.take(6) }
    }
    val accent = ACCENTS[cfg.accent.coerceIn(0, ACCENTS.lastIndex)]
    val foreground = if (cfg.lightMode) Color(0xFF202330) else Color.White
    val wallpaper by produceState<ImageBitmap?>(null, cfg.useCustomWallpaper, wallpaperVersion) {
        value = if (cfg.useCustomWallpaper) withContext(Dispatchers.IO) {
            val f = File(context.filesDir, "wallpaper.jpg")
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, b)
            var sample = 1
            while (b.outWidth / sample > 1920 || b.outHeight / sample > 1080) sample *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        } else null
    }
    LiteTvTheme(accent) {
        Box(Modifier.fillMaxSize().background(if (cfg.lightMode) Color(0xFFDEE2EC) else WALLPAPERS[cfg.wallpaper.coerceIn(0, WALLPAPERS.lastIndex)].colors.first())) {
            if (!cfg.lightMode && !cfg.useCustomWallpaper && !cfg.useVideoWallpaper) Box(Modifier.fillMaxSize().background(WALLPAPERS[cfg.wallpaper.coerceIn(0, WALLPAPERS.lastIndex)].brush()))
            if (cfg.useVideoWallpaper && cfg.videoUri.startsWith("content://")) {
                VideoWallpaper(cfg.videoUri, speed = VIDEO_SPEEDS[cfg.videoSpeed.coerceIn(0, VIDEO_SPEEDS.lastIndex)], coverBrush = Brush.linearGradient(listOf(Color(0xFF090C15), Color(0xFF151C2D))))
            }
            wallpaper?.let { Image(it, null, Modifier.fillMaxSize().blur(cfg.backgroundBlur.dp), contentScale = ContentScale.Crop) }
            val motion = rememberInfiniteTransition(label = "ambient")
            val drift by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(
                listOf(accent.copy(alpha = if (cfg.lightMode) .12f else .20f), Color.Transparent),
                center = Offset(if (cfg.ambientMotion) 500f + drift * 1000 else 800f, 150f), radius = 1400f)))
            Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("Home", color = foreground, fontSize = 24.sp, modifier = Modifier.weight(1f))
                    HomeButton("All Apps") { activeCategory = null }
                    HomeButton("Folders") { categories = true }
                    HomeButton("Settings") { settings = true }
                }
                if (cfg.topShelf) {
                    AnimatedContent(selected ?: favourites.firstOrNull(), label = "topShelf", modifier = Modifier.fillMaxWidth().height(156.dp)) { app ->
                        Row(Modifier.fillMaxSize().padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("TOP SHELF", color = foreground.copy(alpha = .5f), fontSize = 12.sp, letterSpacing = 3.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(app?.label ?: "Make yourself at home", color = foreground, fontSize = 34.sp, maxLines = 1)
                                Spacer(Modifier.height(8.dp))
                                Text("Select to open  •  Hold Select to personalise", color = foreground.copy(alpha = .55f), fontSize = 14.sp)
                            }
                            app?.let { a -> (a.banner ?: a.icon)?.let { Image(it, null, Modifier.width(190.dp).fillMaxHeight().clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop) } }
                        }
                    }
                } else Spacer(Modifier.height(20.dp))
                // The first row is a true favourites shelf; the grid below uses the same artwork and focus model.
                if (activeCategory == null) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        favourites.forEach { app ->
                            HomeTile(app, cfg, accent, cfg.customIcons.containsKey(app.pkg), Modifier.weight(1f),
                                onFocus = { selected = app }, onClick = { Actions.launchApp(context, app.pkg) }, onHold = { menu = app })
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
                LazyVerticalGrid(columns = GridCells.Fixed(cfg.homeColumns.coerceIn(4, 8)), modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy((12 + cfg.spacing * 6).dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    items(visible, key = { it.pkg }) { app ->
                        HomeTile(app, cfg, accent, cfg.customIcons.containsKey(app.pkg), onFocus = { selected = app },
                            onClick = { Actions.launchApp(context, app.pkg) }, onHold = { menu = app })
                    }
                }
                if (scanned.isEmpty()) Text("Discovering your apps…", color = foreground)
            }
            menu?.let { app ->
                ChoiceDialog(app.label, listOf(
                    "Open" to { Actions.launchApp(context, app.pkg); menu = null },
                    "Change Icon · Upload from iPhone" to { menu = null; upload = app },
                    "Restore Original Icon" to { update { it.copy(customIcons = it.customIcons - app.pkg) }; menu = null },
                    "Move App" to { menu = null; moving = app },
                    (if (app.pkg in cfg.favourites) "Remove from Favourites" else "Add to Favourites") to {
                        update { it.copy(favourites = if (app.pkg in it.favourites) it.favourites - app.pkg else it.favourites + app.pkg) }; menu = null
                    },
                    (if (app.pkg in cfg.hidden) "Unhide App" else "Hide App") to {
                        update { it.copy(hidden = if (app.pkg in it.hidden) it.hidden - app.pkg else it.hidden + app.pkg) }; menu = null
                    },
                    "Rename App" to { menu = null; rename = app },
                    "Add to Folder / Category" to { menu = null; categoryPicker = app },
                    "App Info" to { Actions.openAppInfo(context, app.pkg); menu = null }
                ), onDismiss = { menu = null }, suppressHeldSelect = true)
            }
            moving?.let { app ->
                ChoiceDialog("Move ${app.label} before…", visible.filter { it.pkg != app.pkg }.map { target -> target.label to {
                    val order = visible.map { it.pkg }.toMutableList(); order.remove(app.pkg)
                    order.add(order.indexOf(target.pkg).coerceAtLeast(0), app.pkg)
                    update { it.copy(homeOrder = order + it.homeOrder.filter { p -> p !in order }) }; moving = null
                } }, { moving = null })
            }
            rename?.let { app -> NameDialog(app.label, { rename = null }) { name ->
                update { it.copy(renamedApps = it.renamedApps + (app.pkg to name.take(60))) }; rename = null
            } }
            categoryPicker?.let { app ->
                ChoiceDialog("Add ${app.label} to…", cfg.categories.map { cat -> cat.name to {
                    update { it.copy(sections = it.sections + (app.pkg to ((it.sections[app.pkg] ?: setOf(app.autoCategory)) + cat.id))) }; categoryPicker = null
                } }, { categoryPicker = null })
            }
            if (settings) ChoiceDialog("Settings", listOf(
                "Customise Icons" to { settings = false; appPicker = true },
                "Appearance · ${if (cfg.lightMode) "Light" else "Dark"}" to { update { it.copy(lightMode = !it.lightMode) } },
                "Icon Size · ${cfg.iconScale + 1}" to { update { it.copy(iconScale = (it.iconScale + 1) % 5, homeColumns = 8 - (it.iconScale + 1) % 5) } },
                "Columns · ${cfg.homeColumns}" to { update { it.copy(homeColumns = if (it.homeColumns >= 8) 4 else it.homeColumns + 1) } },
                "Top Shelf · ${if (cfg.topShelf) "On" else "Off"}" to { update { it.copy(topShelf = !it.topShelf) } },
                "Ambient Background · ${if (cfg.ambientMotion) "On" else "Off"}" to { update { it.copy(ambientMotion = !it.ambientMotion) } },
                "Background Blur · ${cfg.backgroundBlur}" to { update { it.copy(backgroundBlur = (it.backgroundBlur + 4) % 28) } },
                "Animation · ${cfg.animationMillis} ms" to { update { it.copy(animationMillis = if (it.animationMillis >= 400) 120 else it.animationMillis + 80) } },
                "Wallpapers, Accent, Spacing, Corners & Categories" to { settings = false; legacySettings = true },
                "System · Startup & Home Button" to { settings = false; system = true },
                "Backup & Restore" to { settings = false; backup = true }
            ), { settings = false })
            if (categories) ChoiceDialog("Folders & Categories", listOf("All Apps" to { activeCategory = null; categories = false }) + cfg.categories.map { cat -> cat.name to { activeCategory = cat.id; categories = false } }, { categories = false })
            if (appPicker) ChoiceDialog("Customise Icons · choose an app", apps.map { app -> app.label to { appPicker = false; upload = app } }, { appPicker = false })
            if (legacySettings) SettingsSheet(cfg, apps, store, { legacySettings = false }, { wallpaperVersion++ }, { legacySettings = false })
            if (system) ChoiceDialog("System · Fire OS", listOf(
                "Open Home on Fire" to {
                    val launch = context.packageManager.getLaunchIntentForPackage("io.github.toolicious.homeonfire")
                    if (launch != null) context.startActivity(launch) else message = "Install Home on Fire from github.com/toolicious/home-on-fire/releases/latest. Choose Apple TV Home as its target."
                },
                "Open Accessibility Settings" to { runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }.onFailure { message = "This Fire OS build blocks Accessibility settings. See the installation guide." } },
                "Best-effort Boot Launch · ${if (cfg.startOnBoot) "On" else "Off"}" to {
                    update { it.copy(startOnBoot = !it.startOnBoot) }
                    context.getSharedPreferences("startup", 0).edit().putBoolean("enabled", !cfg.startOnBoot).apply()
                },
                "Restore Amazon Home" to {
                    message = "First turn off Replace Home and Launch on boot in Home on Fire (or disable its Accessibility service). Apple TV Home never disables Amazon services. Long-press Home is Home on Fire's escape."
                    context.getSharedPreferences("startup", 0).edit().putBoolean("enabled", false).apply()
                    update { it.copy(startOnBoot = false) }
                    context.packageManager.getLaunchIntentForPackage("com.amazon.tv.launcher")?.let { context.startActivity(it) }
                },
                "Compatibility & Setup" to { message = "Android-based Fire OS only; Vega OS cannot install this APK. Home on Fire supports Fire OS 7/8 with manual Accessibility permission. Select Apple TV Home as target and enable Launch on boot/wake. Some builds require ADB once. Amazon boot logo and brief launcher flashes cannot be removed. No real Fire Stick testing has been performed." }
            ), { system = false })
            if (backup) BackupDialog(cfg, store, { backup = false }, { wallpaperVersion++ })
            upload?.let { app -> UploadDialog(app, store, { upload = null }) }
            message?.let { text -> ChoiceDialog(text, listOf("OK" to { message = null }), { message = null }) }
        }
    }
}

@Composable
private fun HomeTile(app: AppEntry, cfg: LauncherConfig, accent: Color, custom: Boolean, modifier: Modifier = Modifier,
                     onFocus: () -> Unit, onClick: () -> Unit, onHold: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (focused) 1f else 0f, tween(cfg.animationMillis), label = "lift")
    val radius = (cfg.cornerRadius.coerceIn(0, 4) * 5 + 4).dp
    val shape = RoundedCornerShape(radius)
    Column(modifier.graphicsLayer { scaleX = 1f + lift * .08f; scaleY = scaleX; translationY = -lift * 5.dp.toPx() }) {
        Surface(onClick = onClick, onLongClick = onHold,
            modifier = Modifier.fillMaxWidth().aspectRatio(1.16f).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() },
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(containerColor = app.tile, focusedContainerColor = app.tile),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = .8f)), shape = shape)),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.Black.copy(alpha = .7f), 18.dp))) {
            Box(Modifier.fillMaxSize()) {
                val art = app.banner ?: app.icon
                art?.let { Image(it, null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1f + lift * .025f; scaleY = scaleX; rotationY = lift * 1.5f },
                    contentScale = if (custom || app.banner != null) ContentScale.Crop else ContentScale.Fit) }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .07f + lift * .10f), Color.Transparent, Color.Black.copy(alpha = .10f)))))
            }
        }
        if (cfg.showAppLabels) Text(app.label, maxLines = 1, fontSize = 13.sp, color = if (cfg.lightMode) Color(0xFF303442) else Color.White.copy(alpha = if (focused) 1f else .65f),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
    }
}

@Composable
private fun HomeButton(label: String, action: () -> Unit) {
    Button(onClick = action, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text(label, fontSize = 12.sp) }
}

@Composable
internal fun ChoiceDialog(title: String, choices: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit, suppressHeldSelect: Boolean = false) {
    var released by remember { mutableStateOf(!suppressHeldSelect) }
    // TV surfaces provide native D-pad long-press handling; gate only the initiating release.

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), modifier = Modifier.width(480.dp).heightIn(max = 460.dp).onPreviewKeyEvent { e ->
            val select = e.key == Key.DirectionCenter || e.key == Key.Enter
            if (select && !released) { if (e.type == KeyEventType.KeyUp) released = true; true } else false
        }) {
            Column(Modifier.padding(22.dp)) {
                val focus = remember { FocusRequester() }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item { Text(title, fontSize = 20.sp, modifier = Modifier.padding(bottom = 16.dp)) }
                    items(choices.size) { index ->
                        ListItem(selected = false, onClick = choices[index].second,
                            headlineContent = { Text(choices[index].first, fontSize = 16.sp) },
                            modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier)
                    }
                }
                LaunchedEffect(choices.isEmpty()) { if (choices.isNotEmpty()) runCatching { focus.requestFocus() } }
            }
        }
    }
}

@Composable
private fun NameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.width(400.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("Rename application", fontSize = 22.sp)
                BasicTextField(value, { value = it.take(60) }, textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 20.sp),
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF353944), RoundedCornerShape(8.dp)).padding(14.dp))
                Button(onClick = { if (value.isNotBlank()) onSave(value.trim()) }) { Text("Save") }
            }
        }
    }
}

@Composable
private fun UploadDialog(app: AppEntry, store: ConfigStore, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var server by remember { mutableStateOf<PairingServer?>(null) }
    var status by remember { mutableStateOf("Starting secure local session…") }
    val owner = LocalLifecycleOwner.current
    val saved = remember { kotlinx.coroutines.channels.Channel<Unit>(1) }
    LaunchedEffect(app.pkg) {
        try {
            val started = withContext(Dispatchers.IO) {
                PairingServer { bytes ->
                    val name = IconFiles.save(context, bytes)
                    runBlocking {
                        var old: String? = null
                        store.update { old = it.customIcons[app.pkg]; it.copy(customIcons = it.customIcons + (app.pkg to name)) }
                        old?.let { IconFiles.file(context, it)?.delete() }
                        saved.trySend(Unit)
                    }
                }
            }
            server = started
            status = "Open Safari on the same Wi-Fi.\n\n${started.url}\n\nPairing code: ${started.code}\n\nChoose an image, adjust the crop and select Save. This session closes after one upload or 10 minutes. Back cancels. Local HTTP: use your trusted home Wi-Fi."
            val success = withTimeoutOrNull(10 * 60 * 1000L) { saved.receive(); true } ?: false
            started.close()
            status = if (success) "Icon saved on your Fire TV. Select Done to see the updated icon." else "Session expired. Close this screen to start another upload."
        } catch (e: Exception) { status = e.message ?: "Could not start upload" }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { server?.close(); onDismiss() } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); server?.close() }
    }
    ChoiceDialog("Upload icon · ${app.label}\n\n$status", listOf("Done / Cancel" to onDismiss), onDismiss)
}
