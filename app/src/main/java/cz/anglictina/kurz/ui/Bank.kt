package cz.anglictina.kurz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cz.anglictina.kurz.data.Progress
import cz.anglictina.kurz.data.Repository

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BankScreen(repo: Repository, version: Int, onBack: () -> Unit, onStart: (Int, Boolean) -> Unit) {
    val s = repo.settings
    val p = repo.progress
    var levels by remember { mutableStateOf(s.bankLevels) }
    var packs by remember { mutableStateOf(s.bankPacks) }
    var perDay by remember { mutableFloatStateOf(s.bankNewPerDay.toFloat()) }
    fun saveAll() { s.bankLevels = levels; s.bankPacks = packs; s.bankNewPerDay = perDay.toInt() }

    val all = repo.allBankWords()
    val mastered = all.count { p.peek("w:" + it.id)?.mastered == true }
    val learning = all.count { p.peek("w:" + it.id)?.learning == true }
    val todayNew = p.bankNewByDay[Progress.today()] ?: 0

    Screen("Slovní zásoba bez omezení", onBack = { saveAll(); onBack() }) {
        Section {
            StatRow("umím" to "$mastered", "učím se" to "$learning", "celkem slov" to "${all.size}")
            Text("Dnes nových: $todayNew / ${perDay.toInt()}", style = MaterialTheme.typography.bodySmall)
        }
        Section("Kolik nových slov denně") {
            Text("${perDay.toInt()} slov", style = MaterialTheme.typography.titleMedium)
            Slider(perDay, { perDay = it }, valueRange = 5f..40f, steps = 6)
            Text("Po splnění denní dávky můžeš kdykoli pokračovat tlačítkem „Další dávka“. Učit se dá donekonečna.", style = MaterialTheme.typography.bodySmall)
        }
        Section("Úroveň") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("B1", "B2", "C1").forEach { l ->
                    FilterChip(selected = l in levels, onClick = { levels = if (l in levels) levels - l else levels + l }, label = { Text(l) })
                }
            }
        }
        Section("Témata") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (packs.isEmpty()) "Všechna témata" else "Vybráno: ${packs.size}", Modifier.weight(1f))
                TextButton(onClick = { packs = emptySet() }) { Text("Vše") }
            }
            repo.packs.forEach { pk ->
                val m = pk.words.count { p.peek("w:" + it.id)?.mastered == true }
                val l = pk.words.count { p.peek("w:" + it.id)?.learning == true }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = packs.isEmpty() || pk.id in packs, onCheckedChange = { c ->
                        val base = if (packs.isEmpty()) repo.packs.map { it.id }.toSet() else packs
                        packs = (if (c) base + pk.id else base - pk.id).let { if (it.size == repo.packs.size) emptySet() else it }
                    })
                    Column(Modifier.weight(1f)) {
                        Text("${pk.topicCz} · ${pk.topic}")
                        LinearProgressIndicator(progress = { if (pk.words.isEmpty()) 0f else (m + l * 0.4f) / pk.words.size }, modifier = Modifier.fillMaxWidth())
                        Text("${pk.words.size} slov · umím $m · učím se $l", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Button(onClick = { saveAll(); onStart(s.minutes, false) }, modifier = Modifier.fillMaxWidth()) { Text("Začít (${s.minutes} min)") }
        OutlinedButton(onClick = { saveAll(); onStart(s.minutes, true) }, modifier = Modifier.fillMaxWidth()) { Text("Další dávka nových slov") }
        Spacer(Modifier.height(24.dp))
    }
}
