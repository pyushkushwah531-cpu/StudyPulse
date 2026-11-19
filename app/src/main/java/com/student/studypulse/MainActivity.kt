package com.student.studypulse

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = remember { getSharedPreferences("studypulse_prefs", Context.MODE_PRIVATE) }
            var isDark by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }

            MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                StudyPulseApp(
                    isDark = isDark,
                    onToggleDark = {
                        isDark = !isDark
                        prefs.edit().putBoolean("dark_mode", isDark).apply()
                    }
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val s = TimerStateHolder.state.value
        if (!isChangingConfigurations && pm.isInteractive && s.isRunning && !s.isBreak && s.strictMode) {
            TimerStateHolder.markFocusBroken()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyPulseApp(isDark: Boolean, onToggleDark: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("studypulse_prefs", Context.MODE_PRIVATE) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        if (prefs.getBoolean("daily_reminder_enabled", true) && !prefs.getBoolean("alarm_init", false)) {
            scheduleDailyReminder(context, 8, 0, true)
            prefs.edit().putBoolean("alarm_init", true).apply()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("StudyPulse", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = onToggleDark) { Icon(if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode, "Theme") }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.NotificationsActive, "Settings") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.Timer, "Focus") }, label = { Text("Focus") })
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.School, "Exams") }, label = { Text("Exams") })
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.NoteAlt, "Notes") }, label = { Text("Notes") })
                NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Default.BarChart, "Stats") }, label = { Text("Stats") })
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (selectedTab) {
                0 -> FocusScreen(prefs)
                1 -> ExamTrackerScreen(prefs)
                2 -> NotesAndFlashcardsScreen(prefs)
                3 -> StatsScreen(prefs)
            }
        }
    }

    if (showSettings) {
        var reminderOn by remember { mutableStateOf(prefs.getBoolean("daily_reminder_enabled", true)) }
        var remHour by remember { mutableIntStateOf(prefs.getInt("reminder_hour", 8)) }
        var remMin by remember { mutableIntStateOf(prefs.getInt("reminder_min", 0)) }
        var vibOn by remember { mutableStateOf(prefs.getBoolean("vibrate_on", true)) }
        var soundOn by remember { mutableStateOf(prefs.getBoolean("sound_on", true)) }

        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Settings & Privacy") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Vibration (Timer end)")
                        Switch(checked = vibOn, onCheckedChange = { vibOn = it; prefs.edit().putBoolean("vibrate_on", it).apply() })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Alert Sound")
                        Switch(checked = soundOn, onCheckedChange = { soundOn = it; prefs.edit().putBoolean("sound_on", it).apply() })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Daily Padhai Reminder")
                        Switch(checked = reminderOn, onCheckedChange = {
                            if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            reminderOn = it; prefs.edit().putBoolean("daily_reminder_enabled", it).apply()
                            scheduleDailyReminder(context, remHour, remMin, it)
                        })
                    }
                    if (reminderOn) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                TimePickerDialog(context, { _, h, m ->
                                    remHour = h; remMin = m
                                    prefs.edit().putInt("reminder_hour", h).putInt("reminder_min", m).apply()
                                    scheduleDailyReminder(context, h, m, true)
                                }, remHour, remMin, false).show()
                            }) { Text(String.format(Locale.getDefault(), "Time: %02d:%02d", remHour, remMin)) }
                            OutlinedButton(onClick = { showDailyReminderNotification(context) }) { Text("Test Alert") }
                        }
                    }
                    HorizontalDivider()
                    Text("StudyPulse v3.0 • 100% Offline\nAapke saare notes, mistakes aur study record sirf aapke phone mein safe rehte hain.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } }
        )
    }
}
// END_OF_FILE
