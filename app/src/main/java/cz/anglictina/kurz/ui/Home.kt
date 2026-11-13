package cz.anglictina.kurz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.anglictina.kurz.data.Progress
import cz.anglictina.kurz.data.Repository
import cz.anglictina.kurz.data.SessionBuilder
import cz.anglictina.kurz.data.SessionKind
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    repo: Repository, version: Int, syncing: Boolean, syncMsg: String?,
    onSync: () -> Unit, onMsgShown: () -> Unit,
    onStart: (SessionKind, Int) -> Unit, onBank: () -> Unit, onLessons: () -> Unit,
    onStats: () -> Unit, onSettings: () -> Unit,
) {
    val s = repo.settings
    val p = repo.progress
    var minutes by remember(version) { mutableIntStateOf(s.minutes) }
    val now = System.currentTimeMillis()
    val todayMin = (p.daySeconds[Progress.today()] ?: 0) / 60
    val streak = p.streak()
    val latest = repo.lessons.lastOrNull()
    val lessonIds = repo.lessons.flatMap { l -> l.words.map { "w:" + it.id } + l.exercises.map { "x:" + it.id } }
    val dueLessons = lessonIds.count { id -> p.peek(id)?.let { !it.isNew && it.due <= now } ?: false }
    val newLessons = lessonIds.count { id -> p.peek(id)?.isNew ?: true }
    val bankIds = repo.allBankWords().map { "w:" + it.id }
    val dueBank = bankIds.count { id -> p.peek(id)?.let { !it.isNew && it.due <= now } ?: false }
    val newBank = remember(version) { SessionBuilder(repo).newAvailable() }

    LaunchedEffect(syncMsg) { if (syncMsg != null) { delay(3500); onMsgShown() } }

    Screen("Angličtina B1+", actions = {
        if (syncing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        else IconButton(onClick = onSync) { Icon(Icons.Default.Refresh, "Aktualizovat") }
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Nastavení") }
    }) {
        Text("Ahoj ${s.userName}!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (syncMsg != null) Text(syncMsg, color = MaterialTheme.colorScheme.primary)

        Section {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Whatshot, null, tint = Orange)
                Text("$streak ${czPlural(streak, "den", "dny", "dní")} v řadě", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("dnes $todayMin / ${s.minutes} min")
            }
            LinearProgressIndicator(progress = { (todayMin / s.minutes.toFloat()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }

        Section("Rozšiřování slovní zásoby (B1+ až B2)", container = MaterialTheme.colorScheme.primaryContainer) {
            Text("Jak dlouho?", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 20, 30).forEach { m ->
                    FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m min") })
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AllInclusive, null)
                Text("K opakování: $dueBank · nových slov k dispozici: $newBank")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onStart(SessionKind.BANK, minutes) }, enabled = repo.packs.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Učit se ($minutes min)") }
                OutlinedButton(onClick = onBank, modifier = Modifier.weight(1f)) { Text("Témata a nastavení") }
            }
        }


        Section("Opakování lekcí (B1)", container = MaterialTheme.colorScheme.secondaryContainer) {
            if (latest != null) Text("Aktuální lekce ${latest.week}: ${latest.titleEn} (${latest.titleCz})")
            else Text("Zatím tu není žádná lekce. Zkus aktualizovat.")
            Text("K opakování: $dueLessons · nových položek: $newLessons", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { onStart(SessionKind.LESSONS, minutes) }, enabled = latest != null, modifier = Modifier.fillMaxWidth()) {
                Text("Začít opakování ($minutes min)")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = onLessons, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.MenuBook, null); Spacer(Modifier.size(6.dp)); Text("Lekce")
            }
            FilledTonalButton(onClick = onStats, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.BarChart, null); Spacer(Modifier.size(6.dp)); Text("Statistiky")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
