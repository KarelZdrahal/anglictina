package cz.anglictina.kurz.ui

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.text.Html
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cz.anglictina.kurz.Speech
import cz.anglictina.kurz.data.Repository

@Composable
fun LessonsScreen(repo: Repository, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val p = repo.progress
    Screen("Lekce", onBack = onBack) {
        if (repo.lessons.isEmpty()) Text("Zatím žádné lekce.")
        repo.lessons.reversed().forEach { l ->
            val ids = l.words.map { "w:" + it.id } + l.exercises.map { "x:" + it.id }
            val m = ids.count { p.peek(it)?.mastered == true }
            val lr = ids.count { p.peek(it)?.learning == true }
            Section {
                Column(Modifier.fillMaxWidth().clickable { onOpen(l.id) }) {
                    Text("Lekce ${l.week} · ${l.date}", style = MaterialTheme.typography.labelMedium)
                    Text(l.titleEn, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(l.titleCz)
                    Text(l.grammarFocus, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { if (ids.isEmpty()) 0f else (m + lr * 0.4f) / ids.size }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun LessonDetailScreen(repo: Repository, speech: Speech, id: String, onBack: () -> Unit) {
    val l = repo.lessons.firstOrNull { it.id == id } ?: run { Text("Lekce nenalezena"); return }
    val ctx = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    Screen("Lekce ${l.week}", onBack = onBack) {
        Text(l.titleEn, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(l.titleCz)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (l.audio.isNotBlank()) FilledTonalButton(onClick = {
                if (playing) { player?.stop(); player?.release(); player = null; playing = false }
                else {
                    val mp = MediaPlayer()
                    mp.setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    runCatching {
                        mp.setDataSource(repo.absoluteUrl(l.audio))
                        mp.setOnPreparedListener { it.start(); playing = true }
                        mp.setOnCompletionListener { playing = false }
                        mp.prepareAsync()
                        player = mp
                    }
                }
            }) { Icon(if (playing) Icons.Default.Stop else Icons.Default.Headphones, null); Spacer(Modifier.padding(3.dp)); Text(if (playing) "Zastavit" else "Poslech") }
            if (l.pdf.isNotBlank()) FilledTonalButton(onClick = {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repo.absoluteUrl(l.pdf)))) }
            }) { Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.padding(3.dp)); Text("PDF lekce") }
        }
        if (l.grammarHtml.isNotBlank()) Section("Gramatika: ${l.grammarFocus}") {
            AndroidView(factory = { c -> TextView(c).apply { setTextColor(textColor); textSize = 15f } },
                update = { it.text = Html.fromHtml(l.grammarHtml, Html.FROM_HTML_MODE_COMPACT) })
        }
        Section("Slovíčka (${l.words.size})") {
            l.words.forEach { w ->
                val st = repo.progress.peek("w:" + w.id)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(w.en + (if (st?.mastered == true) "  ✓" else ""), fontWeight = FontWeight.SemiBold)
                        Text(w.cz, style = MaterialTheme.typography.bodySmall)
                        if (w.ex.isNotBlank()) Text(w.ex, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                    }
                    IconButton(onClick = { speech.say(w.en) }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, "Přehrát") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
