package com.student.studypulse

import android.Manifest
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class StudyTask(val id: Long, val title: String, val done: Boolean)
data class RevisionNote(val id: Long, val subject: String, val content: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                StudyPulseApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyPulseApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("studypulse_prefs", Context.MODE_PRIVATE) }
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("StudyPulse", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Timer, "Focus") },
                    label = { Text("Focus") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.School, "Exams") },
                    label = { Text("Exams") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.NoteAlt, "Notes") },
                    label = { Text("Notes") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (selectedTab) {
                0 -> FocusScreen(prefs)
                1 -> ExamTrackerScreen(prefs)
                2 -> NotesVaultScreen(prefs)
            }
        }
    }
}

@Composable
fun FocusScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    var selectedMinutes by remember { mutableIntStateOf(25) }
    var isBreakMode by remember { mutableStateOf(false) }
    var timeLeftSeconds by remember { mutableIntStateOf(25 * 60) }
    var isRunning by remember { mutableStateOf(false) }
    var todayMinutes by remember { mutableIntStateOf(prefs.getInt("today_minutes", 0)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(isRunning) {
        while (isRunning && timeLeftSeconds > 0) {
            delay(1000L)
            timeLeftSeconds -= 1
            if (timeLeftSeconds == 0) {
                isRunning = false
                if (!isBreakMode) {
                    todayMinutes += selectedMinutes
                    prefs.edit().putInt("today_minutes", todayMinutes).apply()
                }
                sendTimerNotification(context, isBreakMode)
            }
        }
    }

    val total = (if (isBreakMode) 5 else selectedMinutes) * 60
    val progress = if (total > 0) timeLeftSeconds.toFloat() / total.toFloat() else 0f

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Aaj Ki Padhai", style = MaterialTheme.typography.labelLarge)
                    Text("$todayMinutes Minutes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 25, 45).forEach { m ->
                FilterChip(
                    selected = !isBreakMode && selectedMinutes == m,
                    onClick = { isRunning = false; isBreakMode = false; selectedMinutes = m; timeLeftSeconds = m * 60 },
                    label = { Text("${m}m") }
                )
            }
            FilterChip(
                selected = isBreakMode,
                onClick = { isRunning = false; isBreakMode = true; timeLeftSeconds = 5 * 60 },
                label = { Text("5m Break") }
            )
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(210.dp)) {
            CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), strokeWidth = 12.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(String.format(Locale.getDefault(), "%02d:%02d", timeLeftSeconds / 60, timeLeftSeconds % 60), fontSize = 42.sp, fontWeight = FontWeight.Bold)
                Text(if (isBreakMode) "Break Time" else "Deep Focus")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                isRunning = !isRunning
            }) {
                Icon(if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text(if (isRunning) "Pause" else "Start Focus")
            }
            OutlinedButton(onClick = {
                isRunning = false
                timeLeftSeconds = (if (isBreakMode) 5 else selectedMinutes) * 60
            }) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text("Reset")
            }
        }
    }
}

@Composable
fun ExamTrackerScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    var examName by remember { mutableStateOf(prefs.getString("exam_name", "Final Exam") ?: "Final Exam") }
    var examDate by remember { mutableLongStateOf(prefs.getLong("exam_date", System.currentTimeMillis() + 30L * 86400000L)) }
    var tasks by remember { mutableStateOf(loadTasks(prefs)) }
    var newTask by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }

    val daysLeft = ((examDate - System.currentTimeMillis()) / 86400000L).coerceAtLeast(0L)
    val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(examDate))

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(examName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("$daysLeft Days Left ($dateStr)", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDialog = true }) { Text("Edit Exam") }
                    OutlinedButton(onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = examDate }
                        DatePickerDialog(context, { _, y, m, d ->
                            examDate = Calendar.getInstance().apply { set(y, m, d, 23, 59) }.timeInMillis
                            prefs.edit().putLong("exam_date", examDate).apply()
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    }) { Text("Pick Date") }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = newTask, onValueChange = { newTask = it }, label = { Text("Add Study Task") }, modifier = Modifier.weight(1f), singleLine = true)
            IconButton(onClick = {
                val t = newTask.trim()
                if (t.isNotEmpty()) {
                    tasks = listOf(StudyTask(System.currentTimeMillis(), t, false)) + tasks
                    saveTasks(prefs, tasks)
                    newTask = ""
                }
            }) { Icon(Icons.Default.AddCircle, "Add", modifier = Modifier.size(32.dp)) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasks, key = { it.id }) { task ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = task.done, onCheckedChange = { c ->
                            tasks = tasks.map { if (it.id == task.id) it.copy(done = c) else it }
                            saveTasks(prefs, tasks)
                        })
                        Text(task.title, modifier = Modifier.weight(1f), textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None)
                        IconButton(onClick = { tasks = tasks.filterNot { it.id == task.id }; saveTasks(prefs, tasks) }) {
                            Icon(Icons.Default.Delete, "Delete")
                        }
                    }
                }
            }
        }
    }
    if (showDialog) {
        var temp by remember { mutableStateOf(examName) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Exam Ka Naam") },
            text = { OutlinedTextField(value = temp, onValueChange = { temp = it }, label = { Text("Exam Name") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (temp.isNotBlank()) { examName = temp.trim(); prefs.edit().putString("exam_name", examName).apply() }
                    showDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun NotesVaultScreen(prefs: SharedPreferences) {
    var notes by remember { mutableStateOf(loadNotes(prefs)) }
    var showAdd by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (notes.isEmpty()) {
            Text("Abhi koi note nahi hai. + dabakar jodein!", modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(notes, key = { it.id }) { note ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(note.subject, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { notes = notes.filterNot { it.id == note.id }; saveNotes(prefs, notes) }) {
                                    Icon(Icons.Default.Delete, "Delete")
                                }
                            }
                            Text(note.content)
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd)) {
            Icon(Icons.Default.Add, "Add")
        }
    }
    if (showAdd) {
        var subject by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, singleLine = true)
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Important Point") }, minLines = 3)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (content.isNotBlank()) {
                        val sub = if (subject.isBlank()) "General" else subject.trim()
                        notes = listOf(RevisionNote(System.currentTimeMillis(), sub, content.trim())) + notes
                        saveNotes(prefs, notes)
                        showAdd = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}

private fun sendTimerNotification(context: Context, isBreak: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(NotificationChannel("studypulse_timer", "Timer", NotificationManager.IMPORTANCE_HIGH))
    }
    val n = NotificationCompat.Builder(context, "studypulse_timer")
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(if (isBreak) "Break Khatam!" else "Focus Session Complete!")
        .setContentText(if (isBreak) "Wapas padhai shuru karein." else "Shabash! Session poora hua.")
        .setAutoCancel(true).build()
    manager.notify(101, n)
}

private fun loadTasks(prefs: SharedPreferences): List<StudyTask> {
    val arr = JSONArray(prefs.getString("tasks_json", "[]") ?: "[]")
    return List(arr.length()) { i -> arr.getJSONObject(i).let { StudyTask(it.getLong("id"), it.getString("title"), it.getBoolean("done")) } }
}

private fun saveTasks(prefs: SharedPreferences, tasks: List<StudyTask>) {
    val arr = JSONArray()
    tasks.forEach { arr.put(JSONObject().apply { put("id", it.id); put("title", it.title); put("done", it.done) }) }
    prefs.edit().putString("tasks_json", arr.toString()).apply()
}

private fun loadNotes(prefs: SharedPreferences): List<RevisionNote> {
    val arr = JSONArray(prefs.getString("notes_json", "[]") ?: "[]")
    return List(arr.length()) { i -> arr.getJSONObject(i).let { RevisionNote(it.getLong("id"), it.getString("subject"), it.getString("content")) } }
}

private fun saveNotes(prefs: SharedPreferences, notes: List<RevisionNote>) {
    val arr = JSONArray()
    notes.forEach { arr.put(JSONObject().apply { put("id", it.id); put("subject", it.subject); put("content", it.content) }) }
    prefs.edit().putString("notes_json", arr.toString()).apply()
}
// END_OF_FILE
