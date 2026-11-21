package com.student.studypulse

import android.app.*
import android.content.*
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.media.RingtoneManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

const val FREE_SLOT_LIMIT = 3

data class StudyTask(val id: Long, val subject: String, val title: String, val done: Boolean)
data class RevisionNote(val id: Long, val subject: String, val topic: String, val detail: String, val isMistake: Boolean, val mastered: Boolean)
data class PdfHistoryItem(val uri: String, val name: String, val lastPage: Int)

fun getPdfLimit(prefs: SharedPreferences) = FREE_SLOT_LIMIT + prefs.getInt("extra_pdf_slots", 0)
fun getExamLimit(prefs: SharedPreferences) = FREE_SLOT_LIMIT + prefs.getInt("extra_exam_slots", 0)
fun getNoteLimit(prefs: SharedPreferences) = FREE_SLOT_LIMIT + prefs.getInt("extra_note_slots", 0)

fun addExtraSlots(prefs: SharedPreferences, key: String, bonus: Int): Int {
    val next = prefs.getInt(key, 0) + bonus
    prefs.edit().putInt(key, next).apply()
    return FREE_SLOT_LIMIT + next
}

fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

fun recordStudySession(prefs: SharedPreferences, subject: String, minutes: Int) {
    if (minutes <= 0) return
    val dKey = "min_${todayKey()}"
    val sKey = "sub_$subject"
    prefs.edit()
        .putInt(dKey, prefs.getInt(dKey, 0) + minutes)
        .putInt(sKey, prefs.getInt(sKey, 0) + minutes)
        .apply()
}

fun getTodayMinutes(prefs: SharedPreferences) = prefs.getInt("min_${todayKey()}", 0)

fun loadWeeklyMinutes(prefs: SharedPreferences): List<Pair<String, Int>> {
    val sdfKey = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val sdfLabel = SimpleDateFormat("EEE", Locale.US)
    val out = mutableListOf<Pair<String, Int>>()
    for (i in 6 downTo 0) {
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
        out.add(sdfLabel.format(c.time) to prefs.getInt("min_${sdfKey.format(c.time)}", 0))
    }
    return out
}

fun calcStreak(prefs: SharedPreferences): Int {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    var streak = 0
    for (i in 0..365) {
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
        val m = prefs.getInt("min_${sdf.format(c.time)}", 0)
        if (m > 0) streak++ else if (i > 0) break
    }
    return streak
}

fun loadTasks(prefs: SharedPreferences): List<StudyTask> {
    val arr = try { JSONArray(prefs.getString("tasks_json", "[]")) } catch (_: Exception) { JSONArray() }
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        StudyTask(o.getLong("id"), o.optString("sub", "GS"), o.getString("title"), o.optBoolean("done", false))
    }
}

fun saveTasks(prefs: SharedPreferences, list: List<StudyTask>) {
    val arr = JSONArray()
    list.forEach { t ->
        arr.put(JSONObject().put("id", t.id).put("sub", t.subject).put("title", t.title).put("done", t.done))
    }
    prefs.edit().putString("tasks_json", arr.toString()).apply()
}

fun loadNotes(prefs: SharedPreferences): List<RevisionNote> {
    val arr = try { JSONArray(prefs.getString("notes_json", "[]")) } catch (_: Exception) { JSONArray() }
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        RevisionNote(
            o.getLong("id"), o.optString("sub", "General"), o.getString("topic"),
            o.optString("detail", ""), o.optBoolean("mistake", false), o.optBoolean("mastered", false)
        )
    }
}

fun saveNotes(prefs: SharedPreferences, list: List<RevisionNote>) {
    val arr = JSONArray()
    list.forEach { n ->
        arr.put(JSONObject().put("id", n.id).put("sub", n.subject).put("topic", n.topic)
            .put("detail", n.detail).put("mistake", n.isMistake).put("mastered", n.mastered))
    }
    prefs.edit().putString("notes_json", arr.toString()).apply()
}

fun loadPdfHistory(prefs: SharedPreferences): List<PdfHistoryItem> {
    val arr = try { JSONArray(prefs.getString("pdf_hist_json", "[]")) } catch (_: Exception) { JSONArray() }
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        PdfHistoryItem(o.getString("uri"), o.getString("name"), o.optInt("page", 0))
    }
}

fun savePdfHistory(prefs: SharedPreferences, list: List<PdfHistoryItem>) {
    val arr = JSONArray()
    list.forEach { p -> arr.put(JSONObject().put("uri", p.uri).put("name", p.name).put("page", p.lastPage)) }
    prefs.edit().putString("pdf_hist_json", arr.toString()).apply()
}

@Suppress("DEPRECATION")
fun triggerFinishFeedback(context: Context, vibrateOn: Boolean, soundOn: Boolean) {
    if (vibrateOn) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                } else v.vibrate(500)
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

fun sendTimerNotification(context: Context, isBreak: Boolean) {
    try {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val chId = "studypulse_done"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(NotificationChannel(chId, "Session Alerts", NotificationManager.IMPORTANCE_HIGH))
        }
        val title = if (isBreak) "Break Complete!" else "Focus Session Complete! 🎉"
        val body = if (isBreak) "Ab phir se padhai par focus karein." else "Shabaash! Aapka study session poora hua."
        val pi = PendingIntent.getActivity(
            context, 10, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, chId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(body).setContentIntent(pi).setAutoCancel(true).build()
        mgr.notify(202, n)
    } catch (_: SecurityException) {}
}

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val chId = "studypulse_daily"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mgr.createNotificationChannel(NotificationChannel(chId, "Daily Study Reminder", NotificationManager.IMPORTANCE_DEFAULT))
            }
            val pi = PendingIntent.getActivity(
                context, 20, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(context, chId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("StudyPulse Reminder 📚")
                .setContentText("Aaj padhai par focus karein!")
                .setContentIntent(pi).setAutoCancel(true).build()
            mgr.notify(203, n)
        } catch (_: SecurityException) {}
    }
}

fun scheduleDailyReminder(context: Context, hour: Int, minute: Int) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pi = PendingIntent.getBroadcast(
        context, 301, Intent(context, DailyReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }
    am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
}

fun cancelDailyReminder(context: Context) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pi = PendingIntent.getBroadcast(
        context, 301, Intent(context, DailyReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    am.cancel(pi)
}

fun exportNotesPdfAndShare(context: Context, notes: List<RevisionNote>) {
    try {
        val doc = PdfDocument()
        val paint = Paint().apply { textSize = 14f }
        val bold = Paint().apply { textSize = 16f; isFakeBoldText = true }
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
        var y = 50f
        page.canvas.drawText("StudyPulse - Revision Notes & Mistakes", 40f, y, bold)
        y += 30f
        notes.forEachIndexed { idx, n ->
            if (y > 780f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
                y = 50f
            }
            val tag = if (n.isMistake) "[Mistake] " else ""
            page.canvas.drawText("${idx + 1}. $tag${n.subject} - ${n.topic}", 40f, y, bold)
            y += 20f
            n.detail.chunked(72).forEach { line ->
                if (y > 800f) {
                    doc.finishPage(page); pageNum++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create()); y = 50f
                }
                page.canvas.drawText("   $line", 40f, y, paint)
                y += 18f
            }
            y += 12f
        }
        doc.finishPage(page)
        val file = File(context.cacheDir, "StudyPulse_Notes.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Notes PDF"))
    } catch (_: Exception) {}
}
// END_OF_FILE
