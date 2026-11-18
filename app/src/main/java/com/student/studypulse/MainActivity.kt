package com.student.studypulse

import android.Manifest
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
                title = {
                    Text("StudyPulse", fontWeight = FontWeight.Bold)
                },
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
                    icon = { Icon(Icons.Default.Timer, contentDescription = "Focus") },
                    label = { Text("Focus") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.School, contentDescription = "Exams") },
                    label = { Text("Exams") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.NoteAlt, contentDescription = "Notes") },
                    label = { Text("Notes") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when (selectedTab) {
                0 -> FocusScreen(prefs)
                1 -> ExamTrackerScreen(prefs)
                2 -> NotesVaultScreen(prefs)
            }
        }
    }
}

@Composable
fun FocusScreen(prefs: android.content.SharedPreferences) {
    val context = LocalContext.current
    var selectedMinutes by remember { mutableIntStateOf(25) }
    var isBreakMode by remember { mutableStateOf(false) }
    var timeLeftSeconds by remember { mutableIntStateOf(25 * 60) }
    var isRunning by remember { mutableStateOf(false) }
    var todayMinutes by remember { mutableIntStateOf(prefs.getInt("today_minutes", 0)) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

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

    val totalSeconds = (if (isBreakMode) 5 else selectedMinutes) * 60
    val progress = if (totalSeconds > 0) timeLeftSeconds.toFloat() / totalSeconds.toFloat() else 0f
    val mins = timeLeftSeconds / 60
    val secs = timeLeftSeconds % 60

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Aaj Ki Padhai", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "$todayMinutes Minutes",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 25, 45).forEach { m ->
                FilterChip(
                    selected = !isBreakMode && selectedMinutes == m,
                    onClick = {
                        isRunning = false
                        isBreakMode = false
                        selectedMinutes = m
                        timeLeftSeconds = m * 60
                    },
                    label = { Text("${m}m Study") }
                )
            }
            FilterChip(
                selected = isBreakMode,
                onClick = {
                    isRunning = false
                    isBreakMode = true
                    timeLeftSeconds = 5 * 60
                },
                label = { Text("5m Break") }
            )
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 12.dp
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format(Locale.getDefault(), "%02d:%02d", mins, secs),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isBreakMode) "Break Time" else "Deep Focus",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    isRunning = !isRunning
                }
            ) {
                Icon(
                    if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(Modifier.width(6.dp))
                Text(if (isRunning) "Pause" else "Start Focus")
            }

            OutlinedButton(
                onClick = {
                    isRunning = false
                    timeLeftSeconds = (if (isBreakMode) 5 else selectedMinutes) * 60
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Reset")
            }
        }
    }
}

@Composable
fun ExamTrackerScreen(prefs: android.content.SharedPreferences) {
    val context = LocalContext.current
    var examName by remember {
        mutableStateOf(prefs.getString("exam_name", "Final Board Exam") ?: "Final Board Exam")
    }
    var examDateMillis by remember {
        mutableLongStateOf(
            prefs.getLong(
                "exam_date",
                System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
            )
        )
    }
    var tasks by remember { mutableStateOf(loadTasks(prefs)) }
    var newTaskTitle by remember { mutableStateOf("") }
    var showExamDialog by remember { mutableStateOf(false) }

    val daysLeft = ((examDateMillis - System.currentTimeMillis()) / (1000L * 60 * 60 * 24))
        .coerceAtLeast(0L)
    val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(examDateMillis))

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(examName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("$daysLeft Days Left ($dateStr)", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showExamDialog = true }) {
                        Text("Edit Exam")
                    }
                    OutlinedButton(onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = examDateMillis }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val picked = Calendar.getInstance().apply { set(y, m, d, 23, 59) }
                                examDateMillis = picked.timeInMillis
                                prefs.edit().putLong("exam_date", examDateMillis).apply()
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) {
                        Text("Pick Date")
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newTaskTitle,
                onValueChange = { newTaskTitle = it },
                label = { Text("Add Chapter / Study Task") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            IconButton(
                onClick = {
                    val trimmed = newTaskTitle.trim()
                    if (trimmed.isNotEmpty()) {
                        tasks = listOf(StudyTask(System.currentTimeMillis(), trimmed, false)) + tasks
                        saveTasks(prefs, tasks)
                        newTaskTitle = ""
                    }
                }
            ) {
                Icon(Icons.Default.AddCircle, contentDescription = "Add", modifier = Modifier.size(32.dp))
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasks, key = { it.id }) { task ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = task.done,
                            onCheckedChange = { checked ->
                                tasks = tasks.map {
                                    if (it.id == task.id) it.copy(done = checked) else it
                                }
                                saveTasks(prefs, tasks)
                            }
                        )
                        Text(
                            text = task.title,
                            modifier = Modifier.weight(1f),
                            textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None
                        )
                        IconButton(onClick = {
                            tasks = tasks.filterNot { it.id == task.id }
                            saveTasks(prefs, tasks)
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }

    if (showExamDialog) {
        var tempName by remember { mutableStateOf(examName) }
        AlertDialog(
            onDismissRequest = { showExamDialog = false },
            title = { Text("Exam Ka Naam") },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("e.g., SSC / NEET / 12th Board") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (tempName.isNotBlank()) {
                        examName = tempName.trim()
                        prefs.edit().putString("exam_name", examName).apply()
                    }
                    showExamDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showExamDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun NotesVaultScreen(prefs: android.content.SharedPreferences) {
    var notes by remember { mutableStateOf(loadNotes(prefs)) }
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (notes.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.NoteAlt, contentDescription = null, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(8.dp))
                Text("Abhi koi offline note nahi hai. + dabakar jodein!")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(notes, key = { it.id }) { note ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    note.subject,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = {
                                    notes = notes.filterNot { it.id == note.id }
                                    saveNotes(prefs, notes)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                            Text(note.content, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Note")
        }
    }

    if (showAddDialog) {
        var subject by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Revision Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject (e.g., Physics / GK)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Formula ya Important Point") },
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (content.isNotBlank()) {
                        val sub = if (subject.isBlank()) "General" else subject.trim()
                        notes = listOf(RevisionNote(System.currentTimeMillis(), sub, content.trim())) + notes
                        saveNotes(prefs, notes)
                        showAddDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun sendTimerNotification(context: Context, isBreak: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED
    ) return

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "studypulse_timer"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "Study Timer Alerts",
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(if (isBreak) "Break Khatam!" else "Focus Session Complete!")
        .setContentText(
         
