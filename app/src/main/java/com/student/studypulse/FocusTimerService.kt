package com.student.studypulse

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.Random

data class TimerUiState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isBreak: Boolean = false,
    val strictMode: Boolean = true,
    val focusBroken: Boolean = false,
    val focusMusicOn: Boolean = false,
    val subject: String = "Maths",
    val totalMinutes: Int = 25,
    val remainingSeconds: Int = 25 * 60
)

object TimerStateHolder {
    private val _state = MutableStateFlow(TimerUiState())
    val state = _state.asStateFlow()

    fun updateState(block: (TimerUiState) -> TimerUiState) = _state.update(block)
    fun markFocusBroken() = _state.update { it.copy(focusBroken = true) }
}

class FocusTimerService : Service() {
    companion object {
        const val ACTION_START = "START"
        const val ACTION_PAUSE = "PAUSE"
        const val ACTION_RESUME = "RESUME"
        const val ACTION_STOP = "STOP"
        const val ACTION_TOGGLE_MUSIC = "TOGGLE_MUSIC"
        const val EXTRA_MINUTES = "MINUTES"
        const val EXTRA_SUBJECT = "SUBJECT"
        const val EXTRA_BREAK = "IS_BREAK"
        const val EXTRA_STRICT = "STRICT"
        const val EXTRA_MUSIC = "MUSIC"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tickerJob: Job? = null
    private var endElapsedMillis: Long = 0L
    @Volatile private var soundRunning = false
    private var soundThread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val mins = intent.getIntExtra(EXTRA_MINUTES, 25)
                val sub = intent.getStringExtra(EXTRA_SUBJECT) ?: "Maths"
                val brk = intent.getBooleanExtra(EXTRA_BREAK, false)
                val strict = intent.getBooleanExtra(EXTRA_STRICT, true)
                val music = intent.getBooleanExtra(EXTRA_MUSIC, false)
                TimerStateHolder.updateState {
                    TimerUiState(
                        isRunning = true, isPaused = false, isBreak = brk,
                        strictMode = strict, focusBroken = false, focusMusicOn = music,
                        subject = sub, totalMinutes = mins, remainingSeconds = mins * 60
                    )
                }
                endElapsedMillis = SystemClock.elapsedRealtime() + mins * 60_000L
                startForegroundCompat()
                startTicker()
                if (music && !brk) startFocusNoise() else stopFocusNoise()
            }
            ACTION_PAUSE -> {
                tickerJob?.cancel()
                stopFocusNoise()
                TimerStateHolder.updateState { it.copy(isRunning = false, isPaused = true) }
                updateOngoingNotification()
            }
            ACTION_RESUME -> {
                val rem = TimerStateHolder.state.value.remainingSeconds
                if (rem > 0) {
                    endElapsedMillis = SystemClock.elapsedRealtime() + rem * 1000L
                    TimerStateHolder.updateState { it.copy(isRunning = true, isPaused = false) }
                    startForegroundCompat()
                    startTicker()
                    val s = TimerStateHolder.state.value
                    if (s.focusMusicOn && !s.isBreak) startFocusNoise()
                }
            }
            ACTION_TOGGLE_MUSIC -> {
                val next = !TimerStateHolder.state.value.focusMusicOn
                TimerStateHolder.updateState { it.copy(focusMusicOn = next) }
                val s = TimerStateHolder.state.value
                if (next && s.isRunning && !s.isBreak) startFocusNoise() else stopFocusNoise()
            }
            ACTION_STOP -> stopServiceCleanly()
        }
        return START_NOT_STICKY
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val rem = ((endElapsedMillis - SystemClock.elapsedRealtime() + 999L) / 1000L).toInt().coerceAtLeast(0)
                TimerStateHolder.updateState { it.copy(remainingSeconds = rem) }
                updateOngoingNotification()
                if (rem <= 0) {
                    onTimerFinished()
                    break
                }
                delay(1000L)
            }
        }
    }

    private fun onTimerFinished() {
        stopFocusNoise()
        val s = TimerStateHolder.state.value
        val prefs = getSharedPreferences("studypulse_prefs", Context.MODE_PRIVATE)
        if (!s.isBreak) {
            recordStudySession(prefs, s.subject, s.totalMinutes)
            if (s.strictMode && !s.focusBroken) {
                prefs.edit().putInt("pure_focus_count", prefs.getInt("pure_focus_count", 0) + 1).apply()
            }
        }
        triggerFinishFeedback(this, prefs.getBoolean("vibrate_on", true), prefs.getBoolean("sound_on", true))
        sendTimerNotification(this, s.isBreak)
        TimerStateHolder.updateState {
            it.copy(isRunning = false, isPaused = false, remainingSeconds = it.totalMinutes * 60)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopServiceCleanly() {
        tickerJob?.cancel()
        stopFocusNoise()
        TimerStateHolder.updateState {
            it.copy(isRunning = false, isPaused = false, focusBroken = false, remainingSeconds = it.totalMinutes * 60)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundCompat() {
        val n = buildOngoingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(201, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(201, n)
        }
    }

    private fun updateOngoingNotification() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(201, buildOngoingNotification())
    }

    private fun buildOngoingNotification(): Notification {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val chId = "studypulse_live_timer"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(chId, "Live Study Timer", NotificationManager.IMPORTANCE_LOW)
            mgr.createNotificationChannel(ch)
        }
        val s = TimerStateHolder.state.value
        val timeText = String.format(Locale.getDefault(), "%02d:%02d", s.remainingSeconds / 60, s.remainingSeconds % 60)
        val openPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleAction = if (s.isRunning) ACTION_PAUSE else ACTION_RESUME
        val toggleLabel = if (s.isRunning) "Pause" else "Resume"
        val togglePi = PendingIntent.getService(
            this, 1, Intent(this, FocusTimerService::class.java).apply { action = toggleAction },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 2, Intent(this, FocusTimerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (s.isBreak) "Break Time • $timeText" else "${s.subject} Focus • $timeText"
        val sub = if (s.isPaused) "Timer Paused (Resume dabakar chalu karein)" else if (s.strictMode) "Strict Mode ON 📵" else "StudyPulse active"
        return NotificationCompat.Builder(this, chId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(sub)
            .setContentIntent(openPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, toggleLabel, togglePi)
            .addAction(0, "Stop", stopPi)
            .build()
    }

    private fun startFocusNoise() {
        if (soundRunning) return
        soundRunning = true
        soundThread = Thread {
            try {
                val rate = 22050
                val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(2048)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(minBuf).setTransferMode(AudioTrack.MODE_STREAM).build()
                val buf = ShortArray(1024)
                val rnd = Random()
                var last = 0f
                track.play()
                while (soundRunning) {
                    for (i in buf.indices) {
                        val w = rnd.nextFloat() * 2f - 1f
                        last = (last + 0.02f * w) / 1.02f
                        buf[i] = (last * 10000f).toInt().coerceIn(-30000, 30000).toShort()
                    }
                    track.write(buf, 0, buf.size)
                }
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }.apply { start() }
    }

    private fun stopFocusNoise() {
        soundRunning = false
        soundThread = null
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        stopFocusNoise()
        scope.cancel()
        super.onDestroy()
    }
}
// END_OF_FILE
