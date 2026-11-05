package com.example.tvlauncher

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Environment
import android.os.StatFs
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt

// ---------------- Weather (Open-Meteo, no API key) ----------------
data class Weather(val temp: Int, val label: String, val sunrise: LocalTime, val sunset: LocalTime)

private fun weatherLabel(code: Int) = when (code) {
    0 -> "Clear"; 1, 2, 3 -> "Cloudy"; 45, 48 -> "Fog"
    in 51..67 -> "Rain"; in 71..77 -> "Snow"; in 80..82 -> "Showers"
    in 95..99 -> "Storm"; else -> "—"
}

fun fetchWeather(c: AppConfig): Weather? = try {
    val unit = if (c.fahrenheit) "fahrenheit" else "celsius"
    val url = URL(
        "https://api.open-meteo.com/v1/forecast?latitude=${c.latitude}&longitude=${c.longitude}" +
            "&current=temperature_2m,weather_code&daily=sunrise,sunset&forecast_days=1" +
            "&timezone=auto&temperature_unit=$unit"
    )
    val conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 8000 }
    val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
    val cur = j.getJSONObject("current")
    val daily = j.getJSONObject("daily")
    Weather(
        cur.getDouble("temperature_2m").roundToInt(),
        weatherLabel(cur.getInt("weather_code")),
        LocalDateTime.parse(daily.getJSONArray("sunrise").getString(0)).toLocalTime(),
        LocalDateTime.parse(daily.getJSONArray("sunset").getString(0)).toLocalTime(),
    )
} catch (e: Exception) { null }

// ---------------- Network / HDMI / stats ----------------
fun networkName(ctx: Context): String = try {
    val cm = ctx.getSystemService(ConnectivityManager::class.java)
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    when {
        caps == null -> "Offline"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
            val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)
            @Suppress("DEPRECATION")
            val ssid = wm?.connectionInfo?.ssid?.trim('"')
            if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") "Wi-Fi" else ssid
        }
        else -> "Online"
    }
} catch (e: Exception) { "" }

data class HdmiInput(val id: String, val label: String)

fun hdmiInputs(ctx: Context): List<HdmiInput> = try {
    val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
    tim.tvInputList.filter { it.type == TvInputInfo.TYPE_HDMI }
        .map { HdmiInput(it.id, (it.loadCustomLabel(ctx) ?: it.loadLabel(ctx)).toString()) }
} catch (e: Exception) { emptyList() }

fun systemStats(ctx: Context): String = try {
    val am = ctx.getSystemService(ActivityManager::class.java)
    val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    val sf = StatFs(Environment.getDataDirectory().path)
    fun gb(b: Long) = String.format("%.1f", b / 1073741824.0)
    "RAM ${gb(mi.availMem)}/${gb(mi.totalMem)} GB free   Storage ${gb(sf.availableBytes)}/${gb(sf.totalBytes)} GB free"
} catch (e: Exception) { "" }

// ---------------- Gemini ----------------
class Attachment(val name: String, val mime: String, val bytes: ByteArray)
class ChatMsg(val fromUser: Boolean, val text: String, val image: ImageBitmap? = null, val attachment: String? = null)

object Gemini {
    private fun call(key: String, model: String, body: JSONObject): JSONObject {
        val conn = (URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 15000; readTimeout = 120000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", key)
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            val msg = try { JSONObject(text).getJSONObject("error").getString("message") } catch (e: Exception) { "HTTP $code" }
            throw Exception(msg)
        }
        return JSONObject(text)
    }

    private fun parts(prompt: String, att: Attachment?): JSONArray {
        val p = JSONArray().put(JSONObject().put("text", prompt))
        if (att != null) p.put(JSONObject().put("inline_data", JSONObject()
            .put("mime_type", att.mime)
            .put("data", Base64.encodeToString(att.bytes, Base64.NO_WRAP))))
        return p
    }

    private fun candidateParts(res: JSONObject): JSONArray =
        res.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()

    suspend fun chat(cfg: AppConfig, history: List<ChatMsg>, prompt: String, att: Attachment?): String =
        withContext(Dispatchers.IO) {
            val contents = JSONArray()
            history.filter { it.text.isNotBlank() && !it.text.startsWith("Error:") }.forEach { m ->
                contents.put(JSONObject().put("role", if (m.fromUser) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", m.text))))
            }
            contents.put(JSONObject().put("role", "user").put("parts", parts(prompt, att)))
            val res = call(cfg.geminiKey, cfg.chatModel, JSONObject().put("contents", contents))
            val ps = candidateParts(res)
            val sb = StringBuilder()
            for (i in 0 until ps.length()) sb.append(ps.getJSONObject(i).optString("text", ""))
            sb.toString().ifBlank { "(no reply)" }
        }

    suspend fun image(cfg: AppConfig, prompt: String, att: Attachment?): Pair<String, ImageBitmap?> =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts(prompt, att))))
                .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("TEXT").put("IMAGE")))
            val ps = candidateParts(call(cfg.geminiKey, cfg.imageModel, body))
            var text = ""; var img: Bitmap? = null
            for (i in 0 until ps.length()) {
                val part = ps.getJSONObject(i)
                text += part.optString("text", "")
                val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                if (inline != null) {
                    val bytes = Base64.decode(inline.getString("data"), Base64.DEFAULT)
                    img = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }
            Pair(text.ifBlank { if (img == null) "(no image returned)" else "" }, img?.asImageBitmap())
        }
}
