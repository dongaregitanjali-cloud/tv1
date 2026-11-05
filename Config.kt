package com.example.tvlauncher

import android.content.Context
import org.json.JSONObject
import java.io.File

data class AppConfig(
    val dock: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val wallpaper: String? = null,
    val tilesPerRow: Int = 6,
    val autoDark: Boolean = true,
    val showStats: Boolean = false,
    val latitude: Double = 40.71,
    val longitude: Double = -74.0,
    val fahrenheit: Boolean = false,
    val saverMinutes: Int = 5,
    val fontFile: String? = null,
    val geminiKey: String = "",
    val chatModel: String = "gemini-2.5-flash",
    val imageModel: String = "gemini-2.5-flash-image",
)

fun JSONObject.stringList(key: String): MutableList<String> {
    val a = optJSONArray(key) ?: return mutableListOf()
    return MutableList(a.length()) { a.getString(it) }
}

const val DEFAULT_CONFIG = """{
  "dock": [],
  "hidden": [],
  "wallpaper": "",
  "fontFile": "",
  "tilesPerRow": 6,
  "autoDark": true,
  "showStats": false,
  "screensaverMinutes": 5,
  "weather": { "lat": 40.71, "lon": -74.0, "fahrenheit": false },
  "geminiKey": "",
  "chatModel": "gemini-2.5-flash",
  "imageModel": "gemini-2.5-flash-image"
}"""

/** Config lives in /sdcard/Android/data/<pkg>/files/ so `adb push` can replace it. Profiles = config.<name>.json */
class ConfigStore(private val ctx: Context) {
    val dir: File get() = (ctx.getExternalFilesDir(null) ?: ctx.filesDir).also { it.mkdirs() }
    private val prefs = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var profile: String
        get() = prefs.getString("profile", "default") ?: "default"
        set(v) { prefs.edit().putString("profile", v).apply() }

    fun profiles(): List<String> {
        val names = dir.listFiles()?.mapNotNull { f ->
            when {
                f.name == "config.json" -> "default"
                f.name.startsWith("config.") && f.name.endsWith(".json") ->
                    f.name.removePrefix("config.").removeSuffix(".json")
                else -> null
            }
        } ?: emptyList()
        return names.sorted()
    }

    private fun file(): File {
        var f = File(dir, if (profile == "default") "config.json" else "config.$profile.json")
        if (!f.exists() && profile != "default") { profile = "default"; f = File(dir, "config.json") }
        return f
    }

    private fun loadJson(): JSONObject {
        val f = file()
        if (!f.exists()) f.writeText(DEFAULT_CONFIG)
        return try { JSONObject(f.readText()) } catch (e: Exception) { JSONObject() }
    }

    fun edit(block: (JSONObject) -> Unit) {
        val j = loadJson(); block(j); file().writeText(j.toString(2))
    }

    fun load(): AppConfig {
        val j = loadJson()
        val w = j.optJSONObject("weather")
        return AppConfig(
            dock = j.stringList("dock"),
            hidden = j.stringList("hidden").toSet(),
            wallpaper = j.optString("wallpaper", "").ifBlank { null },
            tilesPerRow = j.optInt("tilesPerRow", 6),
            autoDark = j.optBoolean("autoDark", true),
            showStats = j.optBoolean("showStats", false),
            latitude = w?.optDouble("lat", 40.71) ?: 40.71,
            longitude = w?.optDouble("lon", -74.0) ?: -74.0,
            fahrenheit = w?.optBoolean("fahrenheit", false) ?: false,
            saverMinutes = j.optInt("screensaverMinutes", 5),
            fontFile = j.optString("fontFile", "").ifBlank { null },
            geminiKey = j.optString("geminiKey", ""),
            chatModel = j.optString("chatModel", "gemini-2.5-flash"),
            imageModel = j.optString("imageModel", "gemini-2.5-flash-image"),
        )
    }
}
