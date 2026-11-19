package com.student.studypulse

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FocusScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    val timerState by TimerStateHolder.state.collectAsState()
    val subjects = listOf("Maths", "Physics", "Chemistry", "GK/GS", "English", "Coding")
    var selectedSubject by remember { mutableStateOf(prefs.getString("last_subject", "Maths") ?: "Maths") }
    var selectedMinutes by remember { mutableIntStateOf(25) }
    var isBreakMode by remember { mutableStateOf(false) }
    var strictMode by remember { mutableStateOf(true) }
    var focusMusicOn by remember { mutableStateOf(false) }
    var dailyGoal by remember { mutableIntStateOf(prefs.getInt("daily_goal", 120)) }
    var showGoalDialog by remember { mutableStateOf(false) }

    val activeSession = timerState.isRunning || timerState.isPaused
    val displaySeconds = if (activeSession) timerState.remainingSeconds else (if (isBreakMode) 5 else selectedMinutes) * 60
    val totalSeconds = (if (activeSession) timerState.totalMinutes else (if (isBreakMode) 5 else selectedMinutes)) * 60
    val progress = if (totalSeconds > 0) displaySeconds.toFloat() / totalSeconds.toFloat() else 0f
    val todayMinutes = getTodayMinutes(prefs)
    val streak = getStreakDays(prefs)
    val medals = prefs.getInt("pure_focus_count", 0)
    val goalProgress = (todayMinutes.toFloat() / dailyGoal.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(modifier = Modifier.fillMaxWidth().clickable { showGoalDialog = true }) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Aaj: $todayMinutes / $dailyGoal min", fontWeight = FontWeight.SemiBold)
                    Text("🔥 $streak Streak  •  🏅 $medals", fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(progress = { goalProgress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
            }
        }

        if (timerState.focusBroken) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                Text("⚠️ Focus Broken! Session ke beech aap doosre app par gaye the.", modifier = Modifier.padding(10.dp), fontSize = 13.sp)
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(subjects) { sub ->
                FilterChip(selected = selectedSubject == sub, enabled = !activeSession, onClick = {
                    selectedSubject = sub; prefs.edit().putString("last_subject", sub).apply()
                }, label = { Text(sub) })
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 25, 45).forEach { m ->
                FilterChip(selected = !isBreakMode && selectedMinutes == m, enabled = !activeSession, onClick = {
                    isBreakMode = false; selectedMinutes = m
                }, label = { Text("${m}m") })
            }
            FilterChip(selected = isBreakMode, enabled = !activeSession, onClick = { isBreakMode = true }, label = { Text("5m Break") })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = if (activeSession) timerState.strictMode else strictMode, enabled = !activeSession,
                onClick = { strictMode = !strictMode },
                leadingIcon = { Icon(Icons.Default.Shield, null, Modifier.size(16.dp)) },
                label = { Text("Strict 📵") })
            FilterChip(selected = if (activeSession) timerState.focusMusicOn else focusMusicOn,
                onClick = {
                    if (activeSession) {
                        ContextCompat.startForegroundService(context, Intent(context, FocusTimerService::class.java).apply {
                            action = FocusTimerService.ACTION_TOGGLE_MUSIC
                        })
                    } else focusMusicOn = !focusMusicOn
                },
                leadingIcon = { Icon(Icons.Default.MusicNote, null, Modifier.size(16.dp)) },
                label = { Text("Focus Music 🎧") })
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(185.dp)) {
            CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), strokeWidth = 11.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(String.format(Locale.getDefault(), "%02d:%02d", displaySeconds / 60, displaySeconds % 60), fontSize = 38.sp, fontWeight = FontWeight.Bold)
                Text(if (activeSession && timerState.isBreak || !activeSession && isBreakMode) "Break Time" else selectedSubject)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                val i = Intent(context, FocusTimerService::class.java)
                if (!activeSession) {
                    i.action = FocusTimerService.ACTION_START
                    i.putExtra(FocusTimerService.EXTRA_MINUTES, if (isBreakMode) 5 else selectedMinutes)
                    i.putExtra(FocusTimerService.EXTRA_SUBJECT, selectedSubject)
                    i.putExtra(FocusTimerService.EXTRA_BREAK, isBreakMode)
                    i.putExtra(FocusTimerService.EXTRA_STRICT, strictMode)
                    i.putExtra(FocusTimerService.EXTRA_MUSIC, focusMusicOn)
                } else {
                    i.action = if (timerState.isRunning) FocusTimerService.ACTION_PAUSE else FocusTimerService.ACTION_RESUME
                }
                ContextCompat.startForegroundService(context, i)
            }) {
                Icon(if (timerState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text(if (timerState.isRunning) "Pause" else if (timerState.isPaused) "Resume" else "Start Focus")
            }
            if (activeSession) {
                OutlinedButton(onClick = {
                    context.startService(Intent(context, FocusTimerService::class.java).apply { action = FocusTimerService.ACTION_STOP })
                }) {
                    Icon(Icons.Default.Stop, null); Spacer(Modifier.width(4.dp)); Text("Stop")
                }
            }
        }
    }

    if (showGoalDialog) {
        var tempGoal by remember { mutableStateOf(dailyGoal.toString()) }
        AlertDialog(onDismissRequest = { showGoalDialog = false }, title = { Text("Daily Goal (Minutes)") },
            text = { OutlinedTextField(value = tempGoal, onValueChange = { tempGoal = it.filter(Char::isDigit) }, label = { Text("e.g., 120") }) },
            confirmButton = { TextButton(onClick = { tempGoal.toIntOrNull()?.takeIf { it > 0 }?.let { dailyGoal = it; prefs.edit().putInt("daily_goal", it).apply() }; showGoalDialog = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showGoalDialog = false }) { Text("Cancel") } })
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
            OutlinedTextField(value = taskSubject, onValueChange = { taskSubject = it }, label = { Text("Sub") }, modifier = Modifier.width(88.dp), singleLine = true)
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
                        IconButton(onClick = { tasks = tasks.filterNot { it.id == task.id }; saveTasks(prefs, tasks) }) { Icon(Icons.Default.Delete, "Delete") }
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
    var tabMode by remember { mutableIntStateOf(0) }
    var cardIndex by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    BackHandler(enabled = tabMode != 0) {
        tabMode = 0
    }

    val filtered = if (tabMode == 1) notes.filter { it.isMistake } else notes

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = tabMode == 0, onClick = { tabMode = 0 }, label = { Text("All") })
                    FilterChip(selected = tabMode == 1, onClick = { tabMode = 1 }, label = { Text("Mistakes ⚠️") })
                    FilterChip(selected = tabMode == 2, onClick = { tabMode = 2; flipped = false }, label = { Text("Cards") })
                }
                if (filtered.isNotEmpty()) {
                    Row {
                        IconButton(onClick = { exportNotesToPdfAndShare(context, filtered) }) { Icon(Icons.Default.PictureAsPdf, "PDF") }
                        IconButton(onClick = { shareNotesAsText(context, filtered) }) { Icon(Icons.Default.Share, "Share") }
                    }
                }
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (tabMode == 1) "Koi Mistake note nahi hai!" else "Koi note nahi hai. + dabakar jodein!")
                }
            } else if (tabMode != 2) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { note ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("${if (note.isMistake) "⚠️ " else ""}${note.subject} • ${note.title}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                        modifier = Modifier.fillMaxWidth().height(220.dp).clickable { flipped = !flipped }
                    ) {
                        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(if (flipped) "Answer / Formula" else "${if (card.isMistake) "⚠️ Mistake • " else ""}${card.subject}", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(10.dp))
                            Text(if (flipped) card.content else card.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            Text(if (flipped) "(Tap to see Question)" else "(Tap to Flip)", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { cardIndex = (safeIdx - 1 + notes.size) % notes.size; flipped = false }) { Text("Prev") }
                        Button(onClick = {
                            notes = notes.map { if (it.id == card.id) it.copy(mastered = !it.mastered) else it }; saveNotes(prefs, notes)
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
        var isMistake by remember { mutableStateOf(tabMode == 1) }
        val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == Activity.RESULT_OK) {
                val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (!spoken.isNullOrBlank()) content = if (content.isBlank()) spoken else "$content $spoken"
            }
        }
        AlertDialog(onDismissRequest = { showAdd = false }, title = { Text("New Note / Mistake") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, singleLine = true)
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Topic / Question") }, singleLine = true)
                    OutlinedTextField(
                        value = content, onValueChange = { content = it }, label = { Text("Answer / Mistake Detail") }, minLines = 3,
                        trailingIcon = {
                            IconButton(onClick = {
                                try {
                                    val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Apna note bolein...")
                                    }
                                    speechLauncher.launch(i)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Voice typing available nahi hai", Toast.LENGTH_SHORT).show()
                                }
                            }) { Icon(Icons.Default.Mic, "Voice Note") }
                        }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isMistake, onCheckedChange = { isMistake = it })
                        Text("Mark as Mistake ⚠️")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (content.isNotBlank()) {
                        notes = listOf(RevisionNote(System.currentTimeMillis(), subject.ifBlank { "General" }.trim(),
                            title.ifBlank { "Revision Point" }.trim(), content.trim(), false, isMistake)) + notes
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
    val medals = remember(prefs) { prefs.getInt("pure_focus_count", 0) }
    val totalWeek = weekData.sumOf { it.second }
    val maxDay = (weekData.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    val maxSub = (subData.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) { Text("7 Days", style = MaterialTheme.typography.labelMedium); Text("${totalWeek}m", fontWeight = FontWeight.Bold, fontSize = 20.sp) } }
                Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) { Text("Streak", style = MaterialTheme.typography.labelMedium); Text("$streak 🔥", fontWeight = FontWeight.Bold, fontSize = 20.sp) } }
                Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) { Text("Pure Focus", style = MaterialTheme.typography.labelMedium); Text("$medals 🏅", fontWeight = FontWeight.Bold, fontSize = 20.sp) } }
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(sub, fontWeight = FontWeight.Medium); Text("$mins min") }
                    LinearProgressIndicator(progress = { mins.toFloat() / maxSub.toFloat() }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}
// END_OF_FILE
