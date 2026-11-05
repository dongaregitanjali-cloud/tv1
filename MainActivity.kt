package com.example.tvlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Typeface
import android.media.tv.TvContract
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime

enum class Screen { Home, Ai, Saver }

@Immutable
data class AppItem(val label: String, val pkg: String, val banner: ImageBitmap, val tint: Color)

class MainActivity : ComponentActivity() {

    lateinit var store: ConfigStore
    private val prefs by lazy { getSharedPreferences("launcher", Context.MODE_PRIVATE) }

    var config by mutableStateOf(AppConfig())
    var apps by mutableStateOf<List<AppItem>>(emptyList())
    var recents by mutableStateOf<List<String>>(emptyList())
    var now by mutableStateOf(LocalDateTime.now())
    var weather by mutableStateOf<Weather?>(null)
    var network by mutableStateOf("")
    var hdmi by mutableStateOf<List<HdmiInput>>(emptyList())
    var stats by mutableStateOf("")
    var wallpaper by mutableStateOf<ImageBitmap?>(null)
    var font by mutableStateOf<FontFamily>(FontFamily.Default)
    var screen by mutableStateOf(Screen.Home)
    var profileName by mutableStateOf("default")

    private var lastInput = SystemClock.elapsedRealtime()
    private var pendingAttach: ((Attachment?) -> Unit)? = null

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val cb = pendingAttach
        pendingAttach = null
        if (uri != null && cb != null) {
            lifecycleScope.launch {
                val att = withContext(Dispatchers.IO) {
                    try {
                        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes == null || bytes.size > 12_000_000) null
                        else Attachment(uri.lastPathSegment ?: "file", contentResolver.getType(uri) ?: "application/octet-stream", bytes)
                    } catch (e: Exception) { null }
                }
                if (att == null) toast("Couldn't read that file (max 12 MB)")
                cb(att)
            }
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ConfigStore(this)
        applyConfig(store.load(), force = true)
        recents = (prefs.getString("recents", "") ?: "").split(",").filter { it.isNotBlank() }

        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalFont provides font) {
                    BackHandler(enabled = screen == Screen.Ai) { screen = Screen.Home }
                    when (screen) {
                        Screen.Home -> HomeScreen(this@MainActivity)
                        Screen.Ai -> AiScreen(this@MainActivity)
                        Screen.Saver -> SaverScreen(this@MainActivity)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        refreshApps()
        startLoop()
    }

    override fun onStart() {
        super.onStart()
        lastInput = SystemClock.elapsedRealtime()
        applyConfig(store.load())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        screen = Screen.Home
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    // Any key wakes the screensaver; the key itself is swallowed.
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        lastInput = SystemClock.elapsedRealtime()
        if (screen == Screen.Saver) {
            if (e.action == KeyEvent.ACTION_UP) screen = Screen.Home
            return true
        }
        return super.dispatchKeyEvent(e)
    }

    // ---------- background refresh: runs ONLY while the launcher is on screen ----------
    private fun startLoop() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var lastWeather = 0L
                while (true) {
                    now = LocalDateTime.now()
                    network = networkName(this@MainActivity)
                    hdmi = hdmiInputs(this@MainActivity)
                    if (config.showStats) stats = systemStats(this@MainActivity)
                    val t = SystemClock.elapsedRealtime()
                    val due = if (weather == null) t - lastWeather > 60_000L else t - lastWeather > 30 * 60_000L
                    if (due) {
                        lastWeather = t
                        weather = withContext(Dispatchers.IO) { fetchWeather(config) } ?: weather
                    }
                    if (screen == Screen.Home && config.saverMinutes > 0 &&
                        t - lastInput > config.saverMinutes * 60_000L
                    ) screen = Screen.Saver
                    delay(if (config.showStats) 5_000L else 15_000L)
                }
            }
        }
    }

    fun isNight(): Boolean {
        if (!config.autoDark) return false
        val t = now.toLocalTime()
        val rise = weather?.sunrise ?: LocalTime.of(7, 0)
        val set = weather?.sunset ?: LocalTime.of(19, 0)
        return t.isBefore(rise) || t.isAfter(set)
    }

    // ---------- config / profiles ----------
    private fun applyConfig(c: AppConfig, force: Boolean = false) {
        val old = config
        config = c
        profileName = store.profile
        if (force || c.fontFile != old.fontFile) font = loadFont(c.fontFile)
        if (force || c.wallpaper != old.wallpaper) {
            lifecycleScope.launch { wallpaper = withContext(Dispatchers.IO) { decodeWallpaper(c.wallpaper) } }
        }
    }

    fun cycleProfile() {
        val list = store.profiles()
        if (list.size < 2) { toast("Only one profile. Push config.<name>.json to add more"); return }
        store.profile = list[(list.indexOf(store.profile) + 1) % list.size]
        applyConfig(store.load(), force = true)
    }

    private fun loadFont(name: String?): FontFamily {
        if (name.isNullOrBlank()) return FontFamily.Default
        return try {
            val f = File(store.dir, name)
            if (f.exists()) FontFamily(Typeface.createFromFile(f)) else FontFamily.Default
        } catch (e: Exception) { FontFamily.Default }
    }

    private fun decodeWallpaper(path: String?): ImageBitmap? {
        if (path.isNullOrBlank()) return null
        val f = File(path).let { if (it.isAbsolute) it else File(store.dir, path) }
        if (!f.exists()) return null
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            var s = 1
            while (o.outWidth / (s * 2) >= 1920) s *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
        } catch (e: Exception) { null }
    }

    // ---------- apps ----------
    private fun refreshApps() {
        lifecycleScope.launch { apps = withContext(Dispatchers.Default) { loadApps() } }
    }

    private fun loadApps(): List<AppItem> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { ri ->
                val (img, tint) = render(ri)
                AppItem(ri.loadLabel(pm).toString(), ri.activityInfo.packageName, img, tint)
            }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    // 256x144 card; square icons are centred (not stretched). Average colour drives the background tint.
    private fun render(ri: ResolveInfo): Pair<ImageBitmap, Color> {
        val pm = packageManager
        val bmp = Bitmap.createBitmap(256, 144, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val banner = ri.activityInfo.loadBanner(pm)
        if (banner != null) {
            banner.setBounds(0, 0, 256, 144); banner.draw(c)
        } else {
            c.drawColor(0xFF20262D.toInt())
            val ic = ri.loadIcon(pm)
            ic.setBounds(80, 24, 176, 120); ic.draw(c)
        }
        val px = Bitmap.createScaledBitmap(bmp, 1, 1, true).getPixel(0, 0)
        return Pair(bmp.asImageBitmap(), Color(px))
    }

    fun launch(pkg: String) {
        packageManager.getLeanbackLaunchIntentForPackage(pkg)?.let {
            startActivity(it)
            val list = (listOf(pkg) + recents.filter { r -> r != pkg }).take(12)
            recents = list
            prefs.edit().putString("recents", list.joinToString(",")).apply()
        }
    }

    // ---------- quick actions ----------
    fun appInfo(pkg: String) = startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
    fun uninstall(pkg: String) = startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
    fun openSystemSettings() = try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e: Exception) { toast("Settings unavailable") }

    fun hide(pkg: String) {
        store.edit { j -> val l = j.stringList("hidden"); if (pkg !in l) l.add(pkg); j.put("hidden", JSONArray(l)) }
        config = store.load()
    }

    fun togglePin(pkg: String, allInDock: List<String>) {
        store.edit { j ->
            val l = j.stringList("dock")
            if (l.isEmpty()) l.addAll(allInDock)
            if (pkg in l) l.remove(pkg) else l.add(pkg)
            j.put("dock", JSONArray(l))
        }
        config = store.load()
    }

    fun openHdmi(id: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id))) }
        catch (e: Exception) { toast("Couldn't open that input") }
    }

    fun pickFile(cb: (Attachment?) -> Unit) {
        pendingAttach = cb
        try { picker.launch("*/*") } catch (e: Exception) { pendingAttach = null; toast("No file picker on this TV"); cb(null) }
    }

    fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
