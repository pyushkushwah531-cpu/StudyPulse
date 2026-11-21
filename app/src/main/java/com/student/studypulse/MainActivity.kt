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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdManager.init(this)
        setContent { StudyPulseApp { finish() } }
    }

    override fun onStop() {
        super.onStop()
        val s = TimerStateHolder.state.value
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (s.isRunning && !s.isBreak && s.strictMode && pm.isInteractive && !AdManager.isShowingFullscreenAd) {
            TimerStateHolder.markFocusBroken()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyPulseApp(onExitApp: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("studypulse_prefs", Context.MODE_PRIVATE) }
    var darkTheme by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    var showSplash by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var lastBackPress by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) { delay(1800L); showSplash = false }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(showSplash) {
        if (!showSplash && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
        if (showSplash) {
            SplashScreen()
        } else {
            BackHandler(enabled = selectedTab != 0) { selectedTab = 0 }
            BackHandler(enabled = selectedTab == 0) {
                val now = System.currentTimeMillis()
                if (now - lastBackPress < 2000L) onExitApp()
                else {
                    lastBackPress = now
                    Toast.makeText(context, "Bahar nikalne ke liye dobara Back dabayein", Toast.LENGTH_SHORT).show()
                }
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("StudyPulse", fontWeight = FontWeight.Bold) },
                        actions = {
                            IconButton(onClick = {
                                darkTheme = !darkTheme
                                prefs.edit().putBoolean("dark_mode", darkTheme).apply()
                            }) { Text(if (darkTheme) "☀️" else "🌙", fontSize = 18.sp) }
                            IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Notifications, contentDescription = "Settings") }
                        }
                    )
                },
                bottomBar = {
                    Column {
                        AdBannerBar()
                        NavigationBar {
                            val tabs = listOf("Focus" to "⏱️", "Exams" to "🎓", "PDF" to "📄", "Notes" to "📝", "Stats" to "📊")
                            tabs.forEachIndexed { idx, (label, emoji) ->
                                NavigationBarItem(
                                    selected = selectedTab == idx,
                                    onClick = { selectedTab = idx },
                                    icon = { Text(emoji, fontSize = 18.sp) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
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
                SettingsAndPrivacyDialog(prefs = prefs, onDismiss = { showSettings = false })
            }
        }
    }
}

@Composable
fun SplashScreen() {
    val scale = remember { Animatable(0.75f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, animationSpec = tween(700)) }
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF3F3D9E)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(scale.value)) {
            Image(painter = painterResource(id = R.drawable.ic_app_icon), contentDescription = "Logo", modifier = Modifier.size(110.dp))
            Spacer(Modifier.height(14.dp))
            Text("StudyPulse", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Focus • PDF • Notes", color = Color(0xFFE0E0FF), fontSize = 14.sp)
        }
    }
}

@Composable
fun SettingsAndPrivacyDialog(prefs: android.content.SharedPreferences, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var goal by remember { mutableIntStateOf(prefs.getInt("daily_goal", 120)) }
    var vibrateOn by remember { mutableStateOf(prefs.getBoolean("vibrate_on", true)) }
    var soundOn by remember { mutableStateOf(prefs.getBoolean("sound_on", true)) }
    var reminderOn by remember { mutableStateOf(prefs.getBoolean("reminder_on", false)) }
    var remHour by remember { mutableIntStateOf(prefs.getInt("rem_hour", 19)) }
    var remMin by remember { mutableIntStateOf(prefs.getInt("rem_min", 0)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings & Privacy", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Daily Goal: $goal min", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(60, 120, 180, 240).forEach { g ->
                        FilterChip(selected = goal == g, onClick = { goal = g; prefs.edit().putInt("daily_goal", g).apply() }, label = { Text("${g}m") })
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Vibration Alert"); Switch(checked = vibrateOn, onCheckedChange = { vibrateOn = it; prefs.edit().putBoolean("vibrate_on", it).apply() })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Sound Alert"); Switch(checked = soundOn, onCheckedChange = { soundOn = it; prefs.edit().putBoolean("sound_on", it).apply() })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Daily Reminder (${String.format("%02d:%02d", remHour, remMin)})")
                    Switch(checked = reminderOn, onCheckedChange = { on ->
                        reminderOn = on; prefs.edit().putBoolean("reminder_on", on).apply()
                        if (on) {
                            TimePickerDialog(context, { _, h, m ->
                                remHour = h; remMin = m
                                prefs.edit().putInt("rem_hour", h).putInt("rem_min", m).apply()
                                scheduleDailyReminder(context, h, m)
                            }, remHour, remMin, true).show()
                        } else cancelDailyReminder(context)
                    })
                }
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://pyushkushwah531-cpu.github.io/StudyPulse/")))
                }) { Text("View Privacy Policy 🔗") }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}
// END_OF_FILE
