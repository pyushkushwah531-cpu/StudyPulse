package com.student.studypulse

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val ts by TimerStateHolder.state.collectAsState()
    var selectedSubject by remember { mutableStateOf(ts.subject) }
    var selectedMinutes by remember { mutableIntStateOf(25) }
    var isBreak by remember { mutableStateOf(false) }
    var strictMode by remember { mutableStateOf(true) }
    var musicOn by remember { mutableStateOf(false) }
    var todayMins by remember { mutableIntStateOf(getTodayMinutes(prefs)) }
    var streak by remember { mutableIntStateOf(calcStreak(prefs)) }
    var medals by remember { mutableIntStateOf(prefs.getInt("pure_focus_count", 0)) }
    val goal = prefs.getInt("daily_goal", 120).coerceAtLeast(1)

    LaunchedEffect(ts.isRunning, ts.remainingSeconds) {
        if (!ts.isRunning) {
            todayMins = getTodayMinutes(prefs)
            streak = calcStreak(prefs)
            medals = prefs.getInt("pure_focus_count", 0)
        }
    }

    val subjects = listOf("Maths", "Physics", "Chemistry", "GK/GS", "English")
    val active = ts.isRunning || ts.isPaused
    val displaySecs = if (active) ts.remainingSeconds else (if (isBreak) 5 else selectedMinutes) * 60
    val totalSecs = if (active) (ts.totalMinutes * 60).coerceAtLeast(1) else (if (isBreak) 5 else selectedMinutes) * 60
    val progress = (displaySecs.toFloat() / totalSecs).coerceIn(0f, 1f)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Aaj: $todayMins / $goal min", fontWeight = FontWeight.Bold)
                    Text("🔥 $streak Streak • 🏅 $medals", fontWeight = FontWeight.SemiBold)
                }
                LinearProgressIndicator(progress = { (todayMins.toFloat() / goal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp))
            }
        }

        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            subjects.forEach { sub ->
                FilterChip(selected = (if (active) ts.subject else selectedSubject) == sub, onClick = { if (!active) selectedSubject = sub }, label = { Text(sub) })
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 25, 45).forEach { m ->
                FilterChip(selected = !isBreak && selectedMinutes == m, onClick = { if (!active) { isBreak = false; selectedMinutes = m } }, label = { Text("${m}m") })
            }
            FilterChip(selected = isBreak, onClick = { if (!active) isBreak = true }, label = { Text("5m Break") })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = if (active) ts.strictMode else strictMode, onClick = { if (!active) strictMode = !strictMode }, label = { Text("🛡️ Strict 📵") })
            FilterChip(selected = if (active) ts.focusMusicOn else musicOn, onClick = {
                if (active) context.startService(Intent(context, FocusTimerService::class.java).apply { action = FocusTimerService.ACTION_TOGGLE_MUSIC })
                else musicOn = !musicOn
            }, label = { Text("🎵 Focus Music 🎧") })
        }

        Box(modifier = Modifier.size(210.dp).padding(8.dp), contentAlignment = Alignment.Center) {
            val primary = MaterialTheme.colorScheme.primary
            val track = MaterialTheme.colorScheme.surfaceVariant
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(track, 0f, 360f, false, style = Stroke(24f, cap = StrokeCap.Round))
                drawArc(if (ts.focusBroken) Color.Red else primary, -90f, 360f * progress, false, style = Stroke(24f, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(String.format(Locale.getDefault(), "%02d:%02d", displaySecs / 60, displaySecs % 60), fontSize = 38.sp, fontWeight = FontWeight.Bold)
                Text(if (ts.focusBroken) "Focus Broken ⚠️" else if (isBreak) "Break Time" else selectedSubject, color = if (ts.focusBroken) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (!active) {
            Button(
                onClick = {
                    val i = Intent(context, FocusTimerService::class.java).apply {
                        action = FocusTimerService.ACTION_START
                        putExtra(FocusTimerService.EXTRA_MINUTES, if (isBreak) 5 else selectedMinutes)
                        putExtra(FocusTimerService.EXTRA_SUBJECT, selectedSubject)
                        putExtra(FocusTimerService.EXTRA_BREAK, isBreak)
                        putExtra(FocusTimerService.EXTRA_STRICT, strictMode)
                        putExtra(FocusTimerService.EXTRA_MUSIC, musicOn)
                    }
                    ContextCompat.startForegroundService(context, i)
                },
                shape = RoundedCornerShape(28.dp), modifier = Modifier.height(50.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (isBreak) "Start Break" else "Start Focus", fontWeight = FontWeight.Bold)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    val act = if (ts.isRunning) FocusTimerService.ACTION_PAUSE else FocusTimerService.ACTION_RESUME
                    context.startService(Intent(context, FocusTimerService::class.java).apply { action = act })
                }) { Text(if (ts.isRunning) "Pause" else "Resume") }
                OutlinedButton(onClick = {
                    context.startService(Intent(context, FocusTimerService::class.java).apply { action = FocusTimerService.ACTION_STOP })
                }) { Text("Stop") }
            }
        }
    }
}

@Composable
fun ExamTrackerScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    var examName by remember { mutableStateOf(prefs.getString("exam_name", "SSC CHSL") ?: "SSC CHSL") }
    var examDateMs by remember { mutableLongStateOf(prefs.getLong("exam_date_ms", System.currentTimeMillis() + 30L * 86_400_000L)) }
    var tasks by remember { mutableStateOf(loadTasks(prefs)) }
    var examLimit by remember { mutableIntStateOf(getExamLimit(prefs)) }
    var subInput by remember { mutableStateOf("GS") }
    var titleInput by remember { mutableStateOf("") }
    var showEditExam by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }

    val daysLeft = ((examDateMs - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
    val dateLabel = remember(examDateMs) { SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(examDateMs)) }
    val doneCount = tasks.count { it.done }
    val pct = if (tasks.isEmpty()) 0 else (doneCount * 100 / tasks.size)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(examName, fontWeight = FontWeight.Bold)
                Text("$daysLeft Days Left ($dateLabel)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Syllabus Completed: $doneCount/${tasks.size} ($pct%)", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { if (tasks.isEmpty()) 0f else doneCount.toFloat() / tasks.size }, modifier = Modifier.fillMaxWidth().height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { showEditExam = true }) { Text("Edit Exam") }
                    OutlinedButton(onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = examDateMs }
                        DatePickerDialog(context, { _, y, m, d ->
                            val picked = Calendar.getInstance().apply { set(y, m, d, 9, 0) }.timeInMillis
                            examDateMs = picked; prefs.edit().putLong("exam_date_ms", picked).apply()
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                    }) { Text("Pick Date") }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Chapters (${tasks.size}/$examLimit Slots)", fontWeight = FontWeight.Bold)
            TextButton(onClick = { showUnlock = true }) { Text("🔓 +2 Slots") }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = subInput, onValueChange = { subInput = it }, label = { Text("Sub") }, modifier = Modifier.width(86.dp), singleLine = true)
            OutlinedTextField(value = titleInput, onValueChange = { titleInput = it }, label = { Text("Chapter / Task") }, modifier = Modifier.weight(1f), singleLine = true)
            FilledIconButton(onClick = {
                if (titleInput.isBlank()) return@FilledIconButton
                if (tasks.size >= examLimit) { showUnlock = true; return@FilledIconButton }
                val updated = listOf(StudyTask(System.currentTimeMillis(), subInput.ifBlank { "GS" }, titleInput.trim(), false)) + tasks
                tasks = updated; saveTasks(prefs, updated); titleInput = ""
            }) { Icon(Icons.Default.Add, contentDescription = "Add") }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasks, key = { it.id }) { t ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = t.done, onCheckedChange = { chk ->
                            val updated = tasks.map { if (it.id == t.id) it.copy(done = chk) else it }
                            tasks = updated; saveTasks(prefs, updated)
                        })
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t.subject, style = MaterialTheme.typography.labelSmall)
                            Text(t.title, fontWeight = FontWeight.SemiBold, textDecoration = if (t.done) TextDecoration.LineThrough else null)
                        }
                        IconButton(onClick = { val u = tasks.filterNot { it.id == t.id }; tasks = u; saveTasks(prefs, u) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }

    if (showEditExam) {
        var nameInput by remember { mutableStateOf(examName) }
        AlertDialog(
            onDismissRequest = { showEditExam = false },
            title = { Text("Exam Name Badlen") },
            text = { OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, label = { Text("Exam Name") }, singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    examName = nameInput.ifBlank { "SSC CHSL" }
                    prefs.edit().putString("exam_name", examName).apply()
                    showEditExam = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditExam = false }) { Text("Cancel") } }
        )
    }

    if (showUnlock) {
        RewardUnlockDialog(
            title = "Exam Task Limit Poori Hui",
            message = "Pehle 3 Exam Tasks free hain. 1 chhota video Ad dekh kar +2 Slots hamesha ke liye kholen!",
            onDismiss = { showUnlock = false },
            onUnlocked = { bonus ->
                examLimit = addExtraSlots(prefs, "extra_exam_slots", bonus)
                showUnlock = false
                if (titleInput.isNotBlank()) {
                    val u = listOf(StudyTask(System.currentTimeMillis(), subInput.ifBlank { "GS" }, titleInput.trim(), false)) + tasks
                    tasks = u; saveTasks(prefs, u); titleInput = ""
                }
            }
        )
    }
}

@Composable
fun NotesAndFlashcardsScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    var notes by remember { mutableStateOf(loadNotes(prefs)) }
    var noteLimit by remember { mutableIntStateOf(getNoteLimit(prefs)) }
    var filterMode by remember { mutableIntStateOf(0) } // 0=All, 1=Mistakes, 2=Cards
    var showAdd by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }
    var cardIndex by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }

    BackHandler(enabled = filterMode != 0) { filterMode = 0; flipped = false }

    val shown = if (filterMode == 1) notes.filter { it.isMistake } else notes

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = filterMode == 0, onClick = { filterMode = 0 }, label = { Text("All") })
                    FilterChip(selected = filterMode == 1, onClick = { filterMode = 1 }, label = { Text("Mistakes ⚠️") })
                    FilterChip(selected = filterMode == 2, onClick = { filterMode = 2; flipped = false }, label = { Text("Cards") })
                }
                Row {
                    IconButton(onClick = {
                        if (notes.isEmpty()) Toast.makeText(context, "Pehle kuch Notes add karein", Toast.LENGTH_SHORT).show()
                        else exportNotesPdfAndShare(context, notes)
                    }) { Text("📄", fontSize = 18.sp) }
                    IconButton(onClick = {
                        val txt = notes.joinToString("\n\n") { "${it.subject} - ${it.topic}\n${it.detail}" }
                        if (txt.isNotBlank()) context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, txt) }, "Share Notes"))
                    }) { Icon(Icons.Default.Share, contentDescription = "Share") }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Notes (${notes.size}/$noteLimit Slots)", fontWeight = FontWeight.Bold)
                TextButton(onClick = { showUnlock = true }) { Text("🔓 +2 Slots") }
            }

            if (filterMode == 2) {
                if (notes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Flashcards ke liye pehle Note banayein") }
                } else {
                    val card = notes[cardIndex.coerceIn(0, notes.lastIndex)]
                    Card(modifier = Modifier.fillMaxWidth().height(230.dp).clickable { flipped = !flipped }, shape = RoundedCornerShape(18.dp)) {
                        Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(card.subject, style = MaterialTheme.typography.labelLarge)
                                Text(if (flipped) card.detail else card.topic, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text(if (flipped) "(Answer)" else "Tap to flip 🔄", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        OutlinedButton(onClick = { cardIndex = (cardIndex - 1 + notes.size) % notes.size; flipped = false }) { Text("Prev") }
                        Button(onClick = { cardIndex = (cardIndex + 1) % notes.size; flipped = false }) { Text("Next ➡️") }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 72.dp)) {
                    items(shown, key = { it.id }) { n ->
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${if (n.isMistake) "⚠️ " else ""}${n.subject} • ${n.topic}", fontWeight = FontWeight.Bold)
                                    Text(n.detail)
                                }
                                IconButton(onClick = { val u = notes.filterNot { it.id == n.id }; notes = u; saveNotes(prefs, u) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { if (notes.size >= noteLimit) showUnlock = true else showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Add Note") }
    }

    if (showAdd) {
        var sub by remember { mutableStateOf("") }
        var topic by remember { mutableStateOf("") }
        var detail by remember { mutableStateOf("") }
        var mistake by remember { mutableStateOf(filterMode == 1) }
        val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == Activity.RESULT_OK) {
                val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (!spoken.isNullOrBlank()) detail = if (detail.isBlank()) spoken else "$detail $spoken"
            }
        }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New Note / Mistake") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sub, onValueChange = { sub = it }, label = { Text("Subject") }, singleLine = true)
                    OutlinedTextField(value = topic, onValueChange = { topic = it }, label = { Text("Topic / Question") }, singleLine = true)
                    OutlinedTextField(
                        value = detail, onValueChange = { detail = it }, label = { Text("Answer / Mistake Detail") }, minLines = 2,
                        trailingIcon = {
                            IconButton(onClick = {
                                try {
                                    voiceLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    })
                                } catch (_: Exception) { Toast.makeText(context, "Voice typing uplabdh nahi", Toast.LENGTH_SHORT).show() }
                            }) { Text("🎤") }
                        }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = mistake, onCheckedChange = { mistake = it })
                        Text("Mark as Mistake ⚠️")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (topic.isNotBlank() || detail.isNotBlank()) {
                        val u = listOf(RevisionNote(System.currentTimeMillis(), sub.ifBlank { "General" }, topic.ifBlank { "Note" }, detail, mistake, false)) + notes
                        notes = u; saveNotes(prefs, u)
                    }
                    showAdd = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }

    if (showUnlock) {
        RewardUnlockDialog(
            title = "Notes Limit Poori Hui",
            message = "Pehle 3 Notes bilkul free hain. 1 chhota video Ad dekh kar +2 Note Slots hamesha ke liye kholen!",
            onDismiss = { showUnlock = false },
            onUnlocked = { bonus ->
                noteLimit = addExtraSlots(prefs, "extra_note_slots", bonus)
                showUnlock = false
                showAdd = true
            }
        )
    }
}
@Composable
fun StatsScreen(prefs: SharedPreferences) {
    val weekly = remember { loadWeeklyMinutes(prefs) }
    val maxVal = (weekly.maxOfOrNull { it.second } ?: 0).coerceAtLeast(30)
    val totalWeek = weekly.sumOf { it.second }
    val streak = remember { calcStreak(prefs) }
    val medals = prefs.getInt("pure_focus_count", 0)
    val subs = listOf("Maths", "Physics", "Chemistry", "GK/GS", "English")

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("7-Day Study Graph ($totalWeek min)", fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                    weekly.forEach { (day, min) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Text("${min}m", fontSize = 11.sp)
                            Box(modifier = Modifier.width(24.dp).height(((min.toFloat() / maxVal) * 100f).coerceAtLeast(6f).dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)))
                            Spacer(Modifier.height(4.dp))
                            Text(day, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🔥 $streak", fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Day Streak") }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🏅 $medals", fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Pure Focus") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Subject Breakdown", fontWeight = FontWeight.Bold)
                subs.forEach { s ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s); Text("${prefs.getInt("sub_$s", 0)} min", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
// END_OF_FILE
