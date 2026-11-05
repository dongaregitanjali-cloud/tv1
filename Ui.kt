package com.example.tvlauncher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.random.Random

val LocalFont = compositionLocalOf<FontFamily> { FontFamily.Default }

@Composable
fun T(
    text: String,
    size: TextUnit = 16.sp,
    color: Color = Color.White,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(text, modifier = modifier, color = color, fontSize = size, fontWeight = weight,
        fontFamily = LocalFont.current, maxLines = maxLines)
}

private val springy = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)

// ---------------------------------------------------------------- building blocks
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: AppItem,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    onKey: (KeyEvent) -> Boolean = { false },
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, springy, label = "tile")
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .onPreviewKeyEvent(onKey)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(shape).background(Color(0xFF1A1F25))
                .then(if (focused) Modifier.border(2.dp, Color.White.copy(alpha = 0.85f), shape) else Modifier)
        ) {
            Image(app.banner, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (focused) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.10f)))
        }
        T(app.label, 13.sp, Color.White.copy(alpha = if (focused) 1f else 0.55f),
            modifier = Modifier.padding(top = 8.dp), maxLines = 1)
    }
}

@Composable
fun Glass(text: String, modifier: Modifier = Modifier, small: Boolean = false, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (f) 1.08f else 1f, spring(0.6f, Spring.StiffnessMedium), label = "glass")
    Box(
        modifier
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = if (f) 0.28f else 0.10f))
            .onFocusChanged { f = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 18.dp, vertical = if (small) 6.dp else 10.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, if (small) 13.sp else 16.sp, Color.White.copy(alpha = if (f) 1f else 0.8f), maxLines = 1) }
}

@Composable
fun SideItem(icon: ImageVector, desc: String, selected: Boolean, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (f) 1.15f else 1f, spring(0.55f, Spring.StiffnessMedium), label = "side")
    Box(
        Modifier.size(48.dp)
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (f) 0.25f else if (selected) 0.12f else 0f))
            .onFocusChanged { f = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, Modifier.size(24.dp), tint = Color.White.copy(alpha = if (f || selected) 1f else 0.6f)) }
}

@Composable
fun Sidebar(a: MainActivity, current: Screen) {
    Column(
        Modifier.fillMaxHeight().width(88.dp).padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SideItem(Icons.Filled.Home, "Home", current == Screen.Home) { a.screen = Screen.Home }
        SideItem(Icons.Filled.Star, "AI assistant", current == Screen.Ai) { a.screen = Screen.Ai }
        SideItem(Icons.Filled.Lock, "Standby", false) { a.screen = Screen.Saver }
        SideItem(Icons.Filled.Settings, "System settings", false) { a.openSystemSettings() }
    }
}

// ---------------------------------------------------------------- HUD
@Composable
fun Hud(a: MainActivity) {
    val t = a.now
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            a.hdmi.forEach { h -> Glass(h.label, small = true) { a.openHdmi(h.id) } }
            if (a.store.profiles().size > 1) Glass(a.profileName, small = true) { a.cycleProfile() }
            if (a.network.isNotEmpty()) T(a.network, 14.sp, Color.White.copy(alpha = 0.65f))
            a.weather?.let { T("${it.temp}° ${it.label}", 14.sp, Color.White.copy(alpha = 0.65f)) }
            T(t.format(DateTimeFormatter.ofPattern("EEE, MMM d")), 14.sp, Color.White.copy(alpha = 0.65f))
            T(t.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)), 26.sp, weight = FontWeight.Medium)
        }
        if (a.config.showStats && a.stats.isNotEmpty())
            T(a.stats, 12.sp, Color.White.copy(alpha = 0.4f), modifier = Modifier.padding(top = 4.dp))
    }
}

// ---------------------------------------------------------------- Home
@Composable
fun HomeScreen(a: MainActivity) {
    val cfg = a.config
    val visible = remember(a.apps, cfg.hidden) { a.apps.filter { it.pkg !in cfg.hidden } }
    val dock = remember(visible, cfg.dock, a.recents) {
        if (cfg.dock.isNotEmpty()) cfg.dock.mapNotNull { p -> visible.find { it.pkg == p } }
        else (a.recents.mapNotNull { p -> visible.find { it.pkg == p } } + visible).distinctBy { it.pkg }.take(8)
    }
    var focusedApp by remember { mutableStateOf<AppItem?>(null) }
    var gridOpen by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppItem?>(null) }
    val dockFirst = remember { FocusRequester() }
    val gridFirst = remember { FocusRequester() }
    val night = a.isNight()
    val tint by animateColorAsState(focusedApp?.tint ?: Color.Black, tween(700), label = "tint")

    BackHandler(gridOpen) { gridOpen = false }
    LaunchedEffect(dock.isNotEmpty()) { delay(100); if (!gridOpen) runCatching { dockFirst.requestFocus() } }
    LaunchedEffect(gridOpen) { delay(80); runCatching { if (gridOpen) gridFirst.requestFocus() else dockFirst.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        a.wallpaper?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = if (night) 0.22f else 0.55f)
        }
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(tint.copy(alpha = if (night) 0.20f else 0.42f), Color.Black))))

        Row(Modifier.fillMaxSize()) {
            Sidebar(a, Screen.Home)
            Column(Modifier.weight(1f).padding(end = 48.dp, top = 28.dp)) {
                Hud(a)
                AnimatedVisibility(!gridOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    val shelf = focusedApp ?: dock.firstOrNull()
                    Row(Modifier.fillMaxWidth().height(180.dp).padding(start = 16.dp, top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (shelf != null) {
                            Image(shelf.banner, null,
                                Modifier.height(130.dp).aspectRatio(16 / 9f).clip(RoundedCornerShape(18.dp)),
                                contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(24.dp))
                            Column {
                                T(shelf.label, 34.sp, weight = FontWeight.Medium, maxLines = 1)
                                T("OK to open   Hold OK for options   Down for all apps", 14.sp, Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    itemsIndexed(dock, key = { _, x -> x.pkg }) { i, app ->
                        AppTile(
                            app,
                            Modifier.width(170.dp).then(if (i == 0) Modifier.focusRequester(dockFirst) else Modifier),
                            onFocus = { focusedApp = app },
                            onKey = { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) { gridOpen = true; true } else false },
                            onClick = { a.launch(app.pkg) },
                            onLongClick = { menuApp = app },
                        )
                    }
                }
                AnimatedVisibility(gridOpen, Modifier.weight(1f),
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()) {
                    val cols = cfg.tilesPerRow.coerceIn(3, 10)
                    LazyVerticalGrid(
                        GridCells.Fixed(cols),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        itemsIndexed(visible, key = { _, x -> x.pkg }) { i, app ->
                            AppTile(
                                app,
                                if (i == 0) Modifier.focusRequester(gridFirst) else Modifier,
                                onFocus = { focusedApp = app },
                                onKey = { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp && i < cols) { gridOpen = false; true } else false },
                                onClick = { a.launch(app.pkg) },
                                onLongClick = { menuApp = app },
                            )
                        }
                    }
                }
            }
        }
    }

    menuApp?.let { app ->
        AppMenu(a, app, inDock = dock.any { it.pkg == app.pkg }, dockPkgs = dock.map { it.pkg }) { menuApp = null }
    }
}

@Composable
fun AppMenu(a: MainActivity, app: AppItem, inDock: Boolean, dockPkgs: List<String>, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(400.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xF01A1F25)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            T(app.label, 22.sp, weight = FontWeight.Medium, maxLines = 1)
            Glass("Open", Modifier.fillMaxWidth().focusRequester(first)) { a.launch(app.pkg); onDismiss() }
            Glass("App info (force stop, clear cache)", Modifier.fillMaxWidth()) { a.appInfo(app.pkg); onDismiss() }
            Glass(if (inDock) "Remove from dock" else "Pin to dock", Modifier.fillMaxWidth()) { a.togglePin(app.pkg, dockPkgs); onDismiss() }
            Glass("Hide from launcher", Modifier.fillMaxWidth()) { a.hide(app.pkg); onDismiss() }
            Glass("Uninstall", Modifier.fillMaxWidth()) { a.uninstall(app.pkg); onDismiss() }
        }
    }
}

// ---------------------------------------------------------------- Screensaver / standby
@Composable
fun SaverScreen(a: MainActivity) {
    var tx by remember { mutableStateOf(0.30f) }
    var ty by remember { mutableStateOf(0.30f) }
    LaunchedEffect(Unit) {
        while (true) { delay(45_000); tx = Random.nextFloat() * 0.6f; ty = Random.nextFloat() * 0.7f }
    }
    val x by animateFloatAsState(tx, tween(40_000, easing = LinearEasing), label = "x")
    val y by animateFloatAsState(ty, tween(40_000, easing = LinearEasing), label = "y")
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.offset(x = maxWidth * x, y = maxHeight * y)) {
            T(a.now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)), 72.sp, Color.White.copy(alpha = 0.6f), FontWeight.Medium)
            T(a.now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), 18.sp, Color.White.copy(alpha = 0.4f))
        }
    }
}

// ---------------------------------------------------------------- AI assistant (Gemini)
@Composable
fun AiScreen(a: MainActivity) {
    val scope = rememberCoroutineScope()
    val msgs = remember { mutableStateListOf<ChatMsg>() }
    var input by remember { mutableStateOf("") }
    var imageMode by remember { mutableStateOf(false) }
    var attach by remember { mutableStateOf<Attachment?>(null) }
    var busy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun send() {
        val p = input.trim()
        if (p.isEmpty() || busy) return
        if (a.config.geminiKey.isBlank()) {
            msgs.add(ChatMsg(false, "Add your Gemini API key as \"geminiKey\" in config.json, then reopen this screen."))
            return
        }
        val hist = msgs.toList()
        val att = attach
        msgs.add(ChatMsg(true, p, attachment = att?.name))
        input = ""; attach = null; busy = true
        scope.launch {
            try {
                if (imageMode) {
                    val (t, img) = Gemini.image(a.config, p, att)
                    msgs.add(ChatMsg(false, t, img))
                } else msgs.add(ChatMsg(false, Gemini.chat(a.config, hist, p, att)))
            } catch (e: Exception) { msgs.add(ChatMsg(false, "Error: ${e.message}")) }
            busy = false
        }
    }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.lastIndex) }

    Row(Modifier.fillMaxSize().background(Color(0xFF07090C))) {
        Sidebar(a, Screen.Ai)
        Column(Modifier.weight(1f).padding(end = 48.dp, top = 28.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                T("Assistant", 28.sp, weight = FontWeight.Medium)
                T(if (imageMode) "Image mode" else "Chat mode", 14.sp, Color.White.copy(alpha = 0.5f))
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 14.dp), state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(msgs) { m ->
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start) {
                        Column(
                            Modifier.widthIn(max = 720.dp).clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = if (m.fromUser) 0.16f else 0.07f)).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            m.attachment?.let { T("Attached: $it", 12.sp, Color.White.copy(alpha = 0.5f)) }
                            if (m.text.isNotBlank()) T(m.text, 17.sp)
                            m.image?.let { Image(it, null, Modifier.heightIn(max = 260.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit) }
                        }
                    }
                }
                if (busy) item { T("Thinking…", 15.sp, Color.White.copy(alpha = 0.5f)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicTextField(
                    value = input, onValueChange = { input = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontFamily = LocalFont.current),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.10f)).padding(14.dp)) {
                            if (input.isEmpty()) T(if (imageMode) "Describe an image to create" else "Ask anything", 18.sp, Color.White.copy(alpha = 0.35f))
                            inner()
                        }
                    },
                )
                Glass(attach?.let { "File: ${it.name.take(14)}" } ?: "Attach") {
                    if (attach != null) attach = null else a.pickFile { attach = it }
                }
                Glass(if (imageMode) "Mode: image" else "Mode: chat") { imageMode = !imageMode }
                Glass("Send") { send() }
            }
        }
    }
}
