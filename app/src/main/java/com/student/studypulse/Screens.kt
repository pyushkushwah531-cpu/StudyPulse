package com.student.studypulse

import android.Manifest
import android.app.DatePickerDialog
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FocusScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    val subjects = listOf("Maths", "Physics", "Chemistry", "GK/GS", "English", "Coding")
    var selectedSubject by remember { mutableStateOf(prefs.getString("last_subject", "Maths") ?: "Maths") }
    var selectedMinutes by remember { mutableIntStateOf(25) }
    var isBreakMode by remember { mutableStateOf(false) }
    var timeLeftSeconds by remember { mutableIntStateOf(25 * 60) }
    var isRunning by remember { mutableStateOf(false) }
    var todayMinutes by remember { mutableIntStateOf(getTodayMinutes(prefs)) }
    var dailyGoal by remember { mutableIntStateOf(prefs.getInt("daily_goal", 120)) }
    var streak by remember { mutableIntStateOf(getStreakDays(prefs)) }
    var showGoalDialog by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(isRunning) {
        while (isRunning && timeLeftSeconds > 0) {
            delay(1000L)
            timeLeftSeconds -= 1
            if (timeLeftSeconds == 0) {
                isRunning = false
                if (!isBreakMode) {
                    recordStudySession(prefs, selectedSubject, selectedMinutes)
                    todayMinutes = getTodayMinutes(prefs)
                    streak = getStreakDays(prefs)
                }
                triggerFinishFeedback(context, prefs.getBoolean("vibrate_on", true), prefs.getBoolean("sound_on", true))
                sendTimerNotification(context, isBreakMode)
            }
        }
    }

    val total = (if (isBreakMode) 5 else selectedMinutes) * 60
    val progress = if (total > 0) timeLeftSeconds.toFloat() / total.toFloat() else 0f
    val goalProgress = (todayMinutes.toFloat() / dailyGoal.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(modifier = Modifier.fillMaxWidth().clickable { showGoalDialog = true }) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Aaj: $todayMinutes / $dailyGoal min (Tap to edit)", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.primary)
                        Text(" $streak Day Streak", fontWeight = FontWeight.Bold)
                    }
                }
                LinearProgressIndicator(progress = { goalProgress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(subjects) { sub ->
                FilterChip(selected = selectedSubject == sub, onClick = {
                    selectedSubject = sub; prefs.edit().putString("last_subject", sub).apply()
                }, label = { Text(sub) })
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 25, 45).forEach { m ->
                FilterChip(selected = !isBreakMode && selectedMinutes == m, onClick = {
                    isRunning = false; isBreakMode = false; selectedMinutes = m; timeLeftSeconds = m * 60
                }, label = { Text("${m}m") })
            }
            FilterChip(selected = isBreakMode, onClick = {
                isRunning = false; isBreakMode = true; timeLeftSeconds = 5 * 60
            }, label = { Text("5m Break") })
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
            CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), strokeWidth = 12.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(String.format(Locale.getDefault(), "%02d:%02d", timeLeftSeconds / 60, timeLeftSeconds % 60), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Text(if (isBreakMode) "Break Time" else selectedSubject, style = MaterialTheme.typography.bodyMedium)
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
                isRunning = false; timeLeftSeconds = (if (isBreakMode) 5 else selectedMinutes) * 60
            }) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Reset")
            }
        }
    }

    if (showGoalDialog) {
        var tempGoal by remember { mutableStateOf(dailyGoal.toString()) }
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text("Daily Study Goal (Minutes)") },
            text = { OutlinedTextField(value = tempGoal, onValueChange = { tempGoal = it.filter(Char::isDigit) }, label = { Text("e.g., 120") }) },
            confirmButton = {
                TextButton(onClick = {
                    val g = tempGoal.toIntOrNull()
                    if (g != null && g > 0) { dailyGoal = g; prefs.edit().putInt("daily_goal", g).apply() }
                    showGoalDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showGoalDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ExamTrackerScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    var examName by remember { mutableStateOf(prefs.getString("exam_name", "Final Exam") ?: "Final Exam") }
    var examDate by remember { mutableLongStateOf(prefs.getLong("exam_date", System.currentTimeMillis() + 30L * 86400000L)) }
    var tasks by remember { mutableStateOf(loadTasks(prefs)) }
    var newTask by remember { mutableStateOf("") }
    var taskSubject by remember { mutableStateOf("Maths") }
    var showDialog by remember { mutableStateOf(false) }

    val daysLeft = ((examDate - System.currentTimeMillis()) / 86400000L).coerceAtLeast(0L)
    val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(examDate))
    val doneCount = tasks.count { it.done }
    val syllabusPct = if (tasks.isEmpty()) 0 else (doneCount * 100) / tasks.size

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(examName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("$daysLeft Days Left ($dateStr)", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text("Syllabus Completed: $doneCount/${tasks.size} ($syllabusPct%)", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { syllabusPct / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(8.dp).clip(RoundedCornerShape(4.dp)))
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = taskSubject, onValueChange = { taskSubject = it }, label = { Text("Sub") }, modifier = Modifier.width(90.dp), singleLine = true)
            OutlinedTextField(value = newTask, onValueChange = { newTask = it }, label = { Text("Chapter / Task") }, modifier = Modifier.weight(1f), singleLine = true)
            IconButton(onClick = {
                val t = newTask.trim()
                if (t.isNotEmpty()) {
                    tasks = listOf(StudyTask(System.currentTimeMillis(), taskSubject.ifBlank { "General" }, t, false)) + tasks
                    saveTasks(prefs, tasks); newTask = ""
                }
            }) { Icon(Icons.Default.AddCircle, "Add", modifier = Modifier.size(32.dp)) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(tasks, key = { it.id }) { task ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = task.done, onCheckedChange = { c ->
                            tasks = tasks.map { if (it.id == task.id) it.copy(done = c) else it }; saveTasks(prefs, tasks)
                        })
                        Column(modifier = Modifier.weight(1f)) {
                            Text(task.subject, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(task.title, textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None)
                        }
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
        AlertDialog(onDismissRequest = { showDialog = false }, title = { Text("Exam Ka Naam") },
            text = { OutlinedTextField(value = temp, onValueChange = { temp = it }, label = { Text("Exam Name") }) },
            confirmButton = { TextButton(onClick = { if (temp.isNotBlank()) { examName = temp.trim(); prefs.edit().putString("exam_name", examName).apply() }; showDialog = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } })
    }
}

@Composable
fun NotesAndFlashcardsScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    var notes by remember { mutableStateOf(loadNotes(prefs)) }
    var flashcardMode by remember { mutableStateOf(false) }
    var cardIndex by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !flashcardMode, onClick = { flashcardMode = false }, label = { Text("Notes") })
                    FilterChip(selected = flashcardMode, onClick = { flashcardMode = true; flipped = false }, label = { Text("Flashcards") })
                }
                if (notes.isNotEmpty()) {
                    Row {
                        IconButton(onClick = { exportNotesToPdfAndShare(context, notes) }) { Icon(Icons.Default.PictureAsPdf, "PDF") }
                        IconButton(onClick = { shareNotesAsText(context, notes) }) { Icon(Icons.Default.Share, "Share") }
                    }
                }
            }
            if (notes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Koi note nahi hai. + dabakar jodein!") }
            } else if (!flashcardMode) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(notes, key = { it.id }) { note ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("${note.subject} • ${note.title}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { notes = notes.filterNot { it.id == note.id }; saveNotes(prefs, notes) }) { Icon(Icons.Default.Delete, "Delete") }
                                }
                                Text(note.content)
                            }
                        }
                    }
                }
            } else {
                val safeIdx = cardIndex.coerceIn(0, notes.lastIndex)
                val card = notes[safeIdx]
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Card ${safeIdx + 1} / ${notes.size}  ${if (card.mastered) "✅ Mastered" else ""}")
                    Spacer(Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (flipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth().height(230.dp).clickable { flipped = !flipped }
                    ) {
                        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(if (flipped) "Answer / Formula" else card.subject, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(10.dp))
                            Text(if (flipped) card.content else card.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(14.dp))
                            Text(if (flipped) "(Tap to see Question)" else "(Tap to Flip)", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { cardIndex = (safeIdx - 1 + notes.size) % notes.size; flipped = false }) { Text("Prev") }
                        Button(onClick = {
                            notes = notes.map { if (it.id == card.id) it.copy(mastered = !it.mastered) else it }
                            saveNotes(prefs, notes)
                        }) { Text(if (card.mastered) "Repeat 🔁" else "Yaad Hai ✅") }
                        OutlinedButton(onClick = { cardIndex = (safeIdx + 1) % notes.size; flipped = false }) { Text("Next") }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd)) { Icon(Icons.Default.Add, "Add") }
    }

    if (showAdd) {
        var subject by remember { mutableStateOf("") }
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showAdd = false }, title = { Text("New Note / Flashcard") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject (e.g., Physics)") }, singleLine = true)
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Topic / Question (Front)") }, singleLine = true)
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Answer / Formula (Back)") }, minLines = 3)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (content.isNotBlank()) {
                        notes = listOf(RevisionNote(System.currentTimeMillis(), subject.ifBlank { "General" }.trim(), title.ifBlank { "Revision Point" }.trim(), content.trim())) + notes
                        saveNotes(prefs, notes); showAdd = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } })
    }
}

@Composable
fun StatsScreen(prefs: SharedPreferences) {
    val weekData = remember(prefs) { getLast7DaysData(prefs) }
    val subData = remember(prefs) { getSubjectMinutes(prefs) }
    val streak = remember(prefs) { getStreakDays(prefs) }
    val totalWeek = weekData.sumOf { it.second }
    val maxDay = (weekData.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    val maxSub = (subData.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Last 7 Days", style = MaterialTheme.typography.labelMedium)
                        Text("$totalWeek min", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Study Streak", style = MaterialTheme.typography.labelMedium)
                        Text("$streak Days 🔥", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Weekly Study Graph", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                        weekData.forEach { (day, mins) ->
                            val frac = (mins.toFloat() / maxDay.toFloat()).coerceIn(0.06f, 1f)
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.fillMaxHeight()) {
                                Text("${mins}m", fontSize = 10.sp)
                                Box(Modifier.width(24.dp).fillMaxHeight(frac * 0.72f).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary))
                                Text(day, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        item { Text("Subject-wise Time", fontWeight = FontWeight.Bold) }
        if (subData.isEmpty()) {
            item { Text("Abhi koi focus session complete nahi hua hai.") }
        } else {
            items(subData) { (sub, mins) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(sub, fontWeight = FontWeight.Medium); Text("$mins min")
                    }
                    LinearProgressIndicator(progress = { mins.toFloat() / maxSub.toFloat() }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}
// END_OF_FILE
    
