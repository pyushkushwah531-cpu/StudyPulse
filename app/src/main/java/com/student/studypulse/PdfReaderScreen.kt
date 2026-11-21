package com.student.studypulse

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PdfReaderScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(loadPdfHistory(prefs)) }
    var pdfLimit by remember { mutableIntStateOf(getPdfLimit(prefs)) }
    var activeUri by remember { mutableStateOf<Uri?>(null) }
    var activeName by remember { mutableStateOf("") }
    var currentPage by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var nightMode by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offX by remember { mutableFloatStateOf(0f) }
    var offY by remember { mutableFloatStateOf(0f) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showQuickNote by remember { mutableStateOf(false) }

    BackHandler(enabled = activeUri != null) { activeUri = null }

    fun openPdfItem(uri: Uri, name: String, startPage: Int) {
        activeUri = uri
        activeName = name
        currentPage = startPage
        scale = 1f; offX = 0f; offY = 0f
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            var name = "Document.pdf"
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) name = c.getString(idx) ?: name
            }
            val existing = history.find { it.uri == uri.toString() }
            if (existing != null) {
                openPdfItem(uri, existing.name, existing.lastPage)
            } else {
                val updated = listOf(PdfHistoryItem(uri.toString(), name, 0)) + history
                history = updated
                savePdfHistory(prefs, updated)
                openPdfItem(uri, name, 0)
            }
        }
    }

    LaunchedEffect(activeUri, currentPage) {
        val uri = activeUri ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        totalPages = renderer.pageCount
                        val safePage = currentPage.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
                        if (safePage != currentPage) currentPage = safePage
                        if (renderer.pageCount > 0) {
                            renderer.openPage(safePage).use { page ->
                                val bmp = Bitmap.createBitmap((page.width * 2).coerceAtLeast(100), (page.height * 2).coerceAtLeast(100), Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(AndroidColor.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                pageBitmap = bmp.asImageBitmap()
                            }
                            val updated = history.map { if (it.uri == uri.toString()) it.copy(lastPage = safePage) else it }
                            history = updated
                            savePdfHistory(prefs, updated)
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "PDF nahi khul paya, dubara select karein", Toast.LENGTH_SHORT).show()
                    activeUri = null
                }
            }
        }
    }

    if (activeUri == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    if (history.size >= pdfLimit) showUnlockDialog = true
                    else picker.launch(arrayOf("application/pdf"))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp)
            ) {
                Icon(Icons.Default.List, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open PDF From Phone", fontWeight = FontWeight.SemiBold)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Recent PDFs (${history.size}/$pdfLimit Slots)", fontWeight = FontWeight.Bold)
                TextButton(onClick = { showUnlockDialog = true }) { Text("🔓 +2 Slots") }
            }

            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Abhi koi PDF nahi khola gaya hai 📄", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(history, key = { it.uri }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { openPdfItem(Uri.parse(item.uri), item.name, item.lastPage) },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("📄", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                    Text("Last read: Page ${item.lastPage + 1}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = {
                                    val updated = history.filterNot { it.uri == item.uri }
                                    history = updated; savePdfHistory(prefs, updated)
                                }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { activeUri = null }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                Text(activeName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                IconButton(onClick = { nightMode = !nightMode }) { Text(if (nightMode) "☀️" else "🌙") }
                IconButton(onClick = { showQuickNote = true }) { Icon(Icons.Default.Add, contentDescription = "Quick Note") }
            }

            val invertFilter = remember { ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(-1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f, 0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f))) }
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()
                    .background(if (nightMode) Color.Black else Color(0xFFEDEDED))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            if (scale > 1f) { offX += pan.x; offY += pan.y } else { offX = 0f; offY = 0f }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                pageBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp, contentDescription = "PDF Page",
                        colorFilter = if (nightMode) invertFilter else null,
                        modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offX, translationY = offY)
                    )
                } ?: CircularProgressIndicator()
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(enabled = currentPage > 0, onClick = { currentPage--; scale = 1f; offX = 0f; offY = 0f }) { Text("Prev") }
                TextButton(onClick = { showJumpDialog = true }) {
                    Text("Page ${currentPage + 1} / ${totalPages.coerceAtLeast(1)}", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(enabled = currentPage < totalPages - 1, onClick = { currentPage++; scale = 1f; offX = 0f; offY = 0f }) { Text("Next") }
            }
        }
    }

    if (showUnlockDialog) {
        RewardUnlockDialog(
            title = "PDF Limit Poori Hui",
            message = "Pehle 3 PDFs bilkul free hain. 1 chhota video Ad dekh kar +2 PDF Slots hamesha ke liye kholen!",
            onDismiss = { showUnlockDialog = false },
            onUnlocked = { bonus ->
                pdfLimit = addExtraSlots(prefs, "extra_pdf_slots", bonus)
                showUnlockDialog = false
                if (activeUri == null) picker.launch(arrayOf("application/pdf"))
            }
        )
    }

    if (showJumpDialog) {
        var pageInput by remember { mutableStateOf("${currentPage + 1}") }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Page") },
            text = { OutlinedTextField(value = pageInput, onValueChange = { pageInput = it.filter(Char::isDigit) }, label = { Text("Page (1 - $totalPages)") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val p = (pageInput.toIntOrNull() ?: 1) - 1
                    currentPage = p.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
                    showJumpDialog = false
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { showJumpDialog = false }) { Text("Cancel") } }
        )
    }

    if (showQuickNote) {
        var topic by remember { mutableStateOf("$activeName (P.${currentPage + 1})") }
        var detail by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showQuickNote = false },
            title = { Text("Quick Note Save Karein") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = topic, onValueChange = { topic = it }, label = { Text("Topic") }, singleLine = true)
                    OutlinedTextField(value = detail, onValueChange = { detail = it }, label = { Text("Important Point") }, minLines = 2)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (detail.isNotBlank()) {
                        val list = loadNotes(prefs) + RevisionNote(System.currentTimeMillis(), "PDF Note", topic, detail, false, false)
                        saveNotes(prefs, list)
                        Toast.makeText(context, "Note Save ho gaya ✅", Toast.LENGTH_SHORT).show()
                    }
                    showQuickNote = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showQuickNote = false }) { Text("Cancel") } }
        )
    }
}
// END_OF_FILE
