package com.student.studypulse

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.media.RingtoneManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class StudyTask(val id: Long, val subject: String, val title: String, val done: Boolean)
data class RevisionNote(
    val id: Long,
    val subject: String,
    val title: String,
    val content: String,
    val mastered: Boolean = false,
    val isMistake: Boolean = false
)

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("studypulse_prefs", Context.MODE_PRIVATE)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (prefs.getBoolean("daily_reminder_enabled", true)) {
                scheduleDailyReminder(context, prefs.getInt("reminder_hour", 8), prefs.getInt("reminder_min", 0), true)
            }
        } else {
            showDailyReminderNotification(context)
        }
    }
}

fun scheduleDailyReminder(context: Context, hour: Int, minute: Int, enabled: Boolean) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pi = PendingIntent.getBroadcast(
        context, 202, Intent(context, DailyReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    if (!enabled) { am.cancel(pi); return }
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }
    am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
}

fun showDailyReminderNotification(context: Context) {
    postNotification(context, 102, "studypulse_daily", "Daily Study Reminder",
        "StudyPulse: Aaj padhai par focus karein! 📚",
        "Aaj ka target poora karne ke liye bas 25 minute focus session shuru karein 💪")
}

fun sendTimerNotification(context: Context, isBreak: Boolean) {
    postNotification(context, 101, "studypulse_timer", "Study Timer Alerts",
        if (isBreak) "Break Khatam!" else "Focus Session Complete!",
        if (isBreak) "Chaliye wapas padhai shuru karte hain." else "Shabash! Aapne apna focus session poora kiya 🎉")
}

private fun postNotification(context: Context, id: Int, channelId: String, channelName: String, title: String, text: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        mgr.createNotificationChannel(NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH))
    }
    val openIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val n = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(title).setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(openIntent).setAutoCancel(true).build()
    mgr.notify(id, n)
}

fun triggerFinishFeedback(context: Context, vibrateOn: Boolean, soundOn: Boolean) {
    if (vibrateOn) {
        try {
            val pattern = longArrayOf(0, 400, 200, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createWaveform(pattern, -1))
                else { @Suppress("DEPRECATION") v.vibrate(pattern, -1) }
            }
        } catch (_: Exception) {}
    }
    if (soundOn) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.play()
        } catch (_: Exception) {}
    }
}

fun dateKey(daysAgo: Int = 0): String {
    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
}

fun recordStudySession(prefs: SharedPreferences, subject: String, minutes: Int) {
    val dObj = JSONObject(prefs.getString("daily_map", "{}") ?: "{}")
    val today = dateKey(0)
    dObj.put(today, dObj.optInt(today, 0) + minutes)
    val sObj = JSONObject(prefs.getString("subject_map", "{}") ?: "{}")
    sObj.put(subject, sObj.optInt(subject, 0) + minutes)
    prefs.edit().putString("daily_map", dObj.toString()).putString("subject_map", sObj.toString()).apply()
}

fun getTodayMinutes(prefs: SharedPreferences): Int =
    JSONObject(prefs.getString("daily_map", "{}") ?: "{}").optInt(dateKey(0), 0)

fun getStreakDays(prefs: SharedPreferences): Int {
    val dObj = JSONObject(prefs.getString("daily_map", "{}") ?: "{}")
    var offset = if (dObj.optInt(dateKey(0), 0) > 0) 0 else 1
    var streak = 0
    while (dObj.optInt(dateKey(offset), 0) > 0) { streak++; offset++ }
    return streak
}

fun getLast7DaysData(prefs: SharedPreferences): List<Pair<String, Int>> {
    val dObj = JSONObject(prefs.getString("daily_map", "{}") ?: "{}")
    val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
    return (6 downTo 0).map { ago ->
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -ago) }
        val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        dayFmt.format(cal.time) to dObj.optInt(key, 0)
    }
}

fun getSubjectMinutes(prefs: SharedPreferences): List<Pair<String, Int>> {
    val sObj = JSONObject(prefs.getString("subject_map", "{}") ?: "{}")
    val list = mutableListOf<Pair<String, Int>>()
    sObj.keys().forEach { k -> list.add(k to sObj.optInt(k, 0)) }
    return list.sortedByDescending { it.second }
}

fun exportNotesToPdfAndShare(context: Context, notes: List<RevisionNote>) {
    val doc = PdfDocument()
    val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true; color = AndroidColor.BLACK }
    val subPaint = Paint().apply { textSize = 13f; isFakeBoldText = true; color = AndroidColor.rgb(79, 70, 229) }
    val bodyPaint = Paint().apply { textSize = 12f; color = AndroidColor.DKGRAY }
    var pageNum = 1
    var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
    var y = 50f
    page.canvas.drawText("StudyPulse - Revision & Mistake Notes", 40f, y, titlePaint)
    y += 30f
    notes.forEach { n ->
        if (y > 760f) { doc.finishPage(page); pageNum++; page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create()); y = 50f }
        val tag = if (n.isMistake) "[MISTAKE] " else ""
        page.canvas.drawText("$tag[${n.subject}] ${n.title}", 40f, y, subPaint); y += 18f
        n.content.chunked(68).forEach { line ->
            if (y > 780f) { doc.finishPage(page); pageNum++; page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create()); y = 50f }
            page.canvas.drawText(line, 40f, y, bodyPaint); y += 16f
        }
        y += 12f
    }
    doc.finishPage(page)
    val file = File(context.cacheDir, "StudyPulse_Notes.pdf")
    FileOutputStream(file).use { doc.writeTo(it) }
    doc.close()
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share StudyPulse PDF"))
}

fun shareNotesAsText(context: Context, notes: List<RevisionNote>) {
    val sb = StringBuilder("*StudyPulse Notes*\n\n")
    notes.forEachIndexed { i, n ->
        val tag = if (n.isMistake) "[MISTAKE] " else ""
        sb.append("${i + 1}. $tag[${n.subject}] ${n.title}\n${n.content}\n\n")
    }
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, sb.toString()) }
    context.startActivity(Intent.createChooser(intent, "Share Notes"))
}

fun loadTasks(prefs: SharedPreferences): List<StudyTask> {
    val arr = JSONArray(prefs.getString("tasks_json", "[]") ?: "[]")
    return List(arr.length()) { i ->
        arr.getJSONObject(i).let { StudyTask(it.getLong("id"), it.optString("subject", "General"), it.getString("title"), it.getBoolean("done")) }
    }
}

fun saveTasks(prefs: SharedPreferences, tasks: List<StudyTask>) {
    val arr = JSONArray()
    tasks.forEach { arr.put(JSONObject().apply { put("id", it.id); put("subject", it.subject); put("title", it.title); put("done", it.done) }) }
    prefs.edit().putString("tasks_json", arr.toString()).apply()
}

fun loadNotes(prefs: SharedPreferences): List<RevisionNote> {
    val arr = JSONArray(prefs.getString("notes_json", "[]") ?: "[]")
    return List(arr.length()) { i ->
        arr.getJSONObject(i).let {
            RevisionNote(it.getLong("id"), it.optString("subject", "General"), it.optString("title", "Topic"),
                it.getString("content"), it.optBoolean("mastered", false), it.optBoolean("isMistake", false))
        }
    }
}

fun saveNotes(prefs: SharedPreferences, notes: List<RevisionNote>) {
    val arr = JSONArray()
    notes.forEach {
        arr.put(JSONObject().apply {
            put("id", it.id); put("subject", it.subject); put("title", it.title)
            put("content", it.content); put("mastered", it.mastered); put("isMistake", it.isMistake)
        })
    }
    prefs.edit().putString("notes_json", arr.toString()).apply()
}
// END_OF_FILE
