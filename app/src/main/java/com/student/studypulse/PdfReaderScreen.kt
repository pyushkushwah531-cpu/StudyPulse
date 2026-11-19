package com.student.studypulse

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

data class RecentPdf(val uri: String, val name: String, val lastPage: Int)

@Composable
fun PdfReaderScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    var recentPdfs by remember { mutableStateOf(loadRecentPdfs(prefs)) }
    var activeUri by remember { mutableStateOf<Uri?>(null) }
    var activeName by remember { mutableStateOf("") }
    var pageIndex by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pdfNightMode by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showQuickNote by remember { mutableStateOf(false) }

    val openPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            val name = queryFileName(context, uri)
            val savedPage = recentPdfs.firstOrNull { it.uri == uri.toString() }?.lastPage ?: 0
            activeUri = uri
            activeName = name
            pageIndex = savedPage
            scale = 1f
            offset = Offset.Zero
            recentPdfs = upsertRecentPdf(prefs, uri.toString(), name, savedPage)
        }
    }

    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }

    DisposableEffect(activeUri) {
        val uri = activeUri
        if (uri != null) {
            try {
                val fd = context.contentResolver.openFileDescriptor(uri, "r")
                if (fd != null) {
                    val r = PdfRenderer(fd)
                    pfd = fd
                    renderer = r
                    pageCount = r.pageCount
                    if (pageIndex >= pageCount) pageIndex = (pageCount - 1).coerceAtLeast(0)
                }
            } catch (_: Exception) {
                Toast.makeText(context, "PDF open nahi ho payi", Toast.LENGTH_SHORT).show()
                activeUri = null
            }
        }
        onDispose {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            renderer = null
            pfd = null
            pageCount = 0
            currentBitmap = null
        }
    }

    LaunchedEffect(renderer, pageIndex) {
        val r = renderer ?: return@LaunchedEffect
        if (pageIndex in 0 until r.pageCount) {
            val page = r.openPage(pageIndex)
            val bmp = Bitmap.createBitmap((page.width * 2).coerceAtLeast(1), (page.height * 2).coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawColor(AndroidColor.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            currentBitmap = bmp
            activeUri?.let { recentPdfs = upsertRecentPdf(prefs, it.toString(), activeName, pageIndex) }
        }
    }

    if (activeUri == null) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.FolderOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("Open PDF From Phone", fontWeight = FontWeight.Bold)
            }

            Text("Recent PDFs", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            if (recentPdfs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Koi PDF open nahi ki hai.\nUpar button dabakar apni book ya notes PDF kholein!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recentPdfs, key = { it.uri }) { item ->
                        Card(modifier = Modifier.fillMaxWidth().clickable {
                            activeUri = Uri.parse(item.uri)
                            activeName = item.name
                            pageIndex = item.lastPage
                            scale = 1f
                            offset = Offset.Zero
                        }) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("Last read: Page ${item.lastPage + 1}", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { recentPdfs = removeRecentPdf(prefs, item.uri) }) {
                                    Icon(Icons.Default.Delete, "Remove")
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        val invertFilter = remember {
            ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 240f,
                0f, -1f, 0f, 0f, 240f,
                0f, 0f, -1f, 0f, 240f,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { activeUri = null }) { Icon(Icons.Default.ArrowBack, "Back") }
                Text(activeName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = { pdfNightMode = !pdfNightMode }) {
                    Icon(if (pdfNightMode) Icons.Default.LightMode else Icons.Default.DarkMode, "PDF Night Mode")
                }
                IconButton(onClick = { showQuickNote = true }) { Icon(Icons.Default.NoteAdd, "Quick Note") }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (pdfNightMode) Color(0xFF121212) else Color(0xFFE5E7EB))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 4f)
                            scale = newScale
                            offset = if (newScale > 1f) offset + pan else Offset.Zero
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                currentBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "PDF Page",
                        contentScale = ContentScale.Fit,
                        colorFilter = if (pdfNightMode) invertFilter else null,
                        modifier = Modifier.fillMaxSize().graphicsLayer(
                            scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y
                        )
                    )
                } ?: CircularProgressIndicator()
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(enabled = pageIndex > 0, onClick = { pageIndex--; scale = 1f; offset = Offset.Zero }) {
                    Text("Prev")
                }
                TextButton(onClick = { showJumpDialog = true }) {
                    Text("Page ${pageIndex + 1} / $pageCount", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                OutlinedButton(enabled = pageIndex < pageCount - 1, onClick = { pageIndex++; scale = 1f; offset = Offset.Zero }) {
                    Text("Next")
                }
            }
        }
    }

    if (showJumpDialog && pageCount > 0) {
        var pageInput by remember { mutableStateOf((pageIndex + 1).toString()) }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Go To Page (1 - $pageCount)") },
            text = { OutlinedTextField(value = pageInput, onValueChange = { pageInput = it.filter(Char::isDigit) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    pageInput.toIntOrNull()?.let { p ->
                        pageIndex = (p - 1).coerceIn(0, pageCount - 1)
                        scale = 1f; offset = Offset.Zero
                    }
                    showJumpDialog = false
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { showJumpDialog = false }) { Text("Cancel") } }
        )
    }

    if (showQuickNote) {
        var noteText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showQuickNote = false },
            title = { Text("Save Note (Page ${pageIndex + 1})") },
            text = { OutlinedTextField(value = noteText, onValueChange = { noteText = it }, label = { Text("Important point likhein") }, minLines = 3) },
            confirmButton = {
                TextButton(onClick = {
                    if (noteText.isNotBlank()) {
                        val existing = loadNotes(prefs)
                        val newNote = RevisionNote(System.currentTimeMillis(), "PDF", "$activeName (P.${pageIndex + 1})", noteText.trim())
                        saveNotes(prefs, listOf(newNote) + existing)
                        Toast.makeText(context, "Notes mein save ho gaya!", Toast.LENGTH_SHORT).show()
                        showQuickNote = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showQuickNote = false }) { Text("Cancel") } }
        )
    }
}

private fun queryFileName(context: android.content.Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) return c.getString(idx) ?: "Document.pdf"
    }
    return uri.lastPathSegment ?: "Document.pdf"
}

private fun loadRecentPdfs(prefs: SharedPreferences): List<RecentPdf> {
    val arr = JSONArray(prefs.getString("recent_pdfs", "[]") ?: "[]")
    return List(arr.length()) { i ->
        arr.getJSONObject(i).let { RecentPdf(it.getString("uri"), it.getString("name"), it.optInt("lastPage", 0)) }
    }
}

private fun upsertRecentPdf(prefs: SharedPreferences, uri: String, name: String, page: Int): List<RecentPdf> {
    val list = (listOf(RecentPdf(uri, name, page)) + loadRecentPdfs(prefs).filterNot { it.uri == uri }).take(10)
    saveRecentPdfs(prefs, list)
    return list
}

private fun removeRecentPdf(prefs: SharedPreferences, uri: String): List<RecentPdf> {
    val list = loadRecentPdfs(prefs).filterNot { it.uri == uri }
    saveRecentPdfs(prefs, list)
    return list
}

private fun saveRecentPdfs(prefs: SharedPreferences, list: List<RecentPdf>) {
    val arr = JSONArray()
    list.forEach { arr.put(JSONObject().apply { put("uri", it.uri); put("name", it.name); put("lastPage", it.lastPage) }) }
    prefs.edit().putString("recent_pdfs", arr.toString()).apply()
}
// END_OF_FILE
