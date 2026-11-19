package com.student.studypulse

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = remember { getSharedPreferences("studypulse_prefs", Context.MODE_PRIVATE) }
            var isDark by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
            var showSplash by rememberSaveable { mutableStateOf(true) }

            MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                if (showSplash) {
                    SplashScreen(onDone = { showSplash = false })
                } else {
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

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val scaleAnim = remember { Animatable(0.6f) }
    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scaleAnim.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        alphaAnim.animateTo(1f, animationSpec = tween(700))
        delay(2300L)
        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF312E81), Color(0xFF4F46E5)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(scaleAnim.value).alpha(alphaAnim.value)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_app_icon),
                contentDescription = "StudyPulse Logo",
                modifier = Modifier.size(110.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "StudyPulse",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                color = Color.White
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Focus • Track • Master",
                fontSize = 14.sp,
                letterSpacing = 1.5.sp,
                color = Color(0xFFE0E7FF)
            )
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator(
                color = Color(0xFFFBBF24),
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp)
            )
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
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.PictureAsPdf, "PDF") }, label = { Text("PDF") })
                NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Default.NoteAlt, "Notes") }, label = { Text("Notes") })
                NavigationBarItem(selected = selectedTab == 4, onClick = { selectedTab = 4 }, icon = { Icon(Icons.Default.BarChart, "Stats") }, label = { Text("Stats") })
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (selectedTab) {
                0 -> FocusScreen(prefs)
                1 -> ExamTrackerScreen(prefs)
                2 -> PdfReaderScreen(prefs)
                3 -> NotesAndFlashcardsScreen(prefs)
                4 -> StatsScreen(prefs)
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
                    Text("StudyPulse v4.0 • 100% Offline\nAapke saare PDFs, notes, mistakes aur study record sirf aapke phone mein safe rehte hain.", style = MaterialTheme.typography.bodySmall)
                    TextButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://pyushkushwah531-cpu.github.io/StudyPulse/")))
                            } catch (_: Exception) {}
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("View Privacy Policy")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } }
        )
    }
}
// END_OF_FILE
