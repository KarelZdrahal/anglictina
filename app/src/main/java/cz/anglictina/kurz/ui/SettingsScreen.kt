package cz.anglictina.kurz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cz.anglictina.kurz.BuildConfig
import cz.anglictina.kurz.data.Repository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(repo: Repository, onBack: () -> Unit, onSync: () -> Unit) {
    val s = repo.settings
    var name by remember { mutableStateOf(s.userName) }
    var minutes by remember { mutableFloatStateOf(s.minutes.toFloat()) }
    var dir by remember { mutableStateOf(s.direction) }
    var speak by remember { mutableStateOf(s.speakAuto) }
    var rem by remember { mutableStateOf(s.reminderOn) }
    var hour by remember { mutableIntStateOf(s.reminderHour) }
    var minute by remember { mutableIntStateOf(s.reminderMinute) }
    var url by remember { mutableStateOf(s.dataUrl) }
    fun save() {
        s.userName = name.trim().ifBlank { s.userName }; s.minutes = minutes.toInt(); s.direction = dir; s.speakAuto = speak
        s.reminderOn = rem; s.reminderHour = hour; s.reminderMinute = minute; s.dataUrl = url.trim().ifBlank { BuildConfig.DATA_URL }
    }

    Screen("Nastavení", onBack = { save(); onBack() }) {
        Section("Profil") {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Jméno") })
        }
        Section("Denní cíl") {
            Text("${minutes.toInt()} minut denně", style = MaterialTheme.typography.titleMedium)
            Slider(minutes, { minutes = it }, valueRange = 5f..30f, steps = 4)
        }
        Section("Procvičování slovíček") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("mix" to "Obojí", "en2cz" to "EN → CZ", "cz2en" to "CZ → EN").forEach { (k, l) ->
                    FilterChip(selected = dir == k, onClick = { dir = k }, label = { Text(l) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Automaticky přehrávat výslovnost", Modifier.weight(1f)); Switch(speak, { speak = it })
            }
        }
        Section("Připomínka") {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Denní připomínka", Modifier.weight(1f)); Switch(rem, { rem = it }) }
            if (rem) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { hour = (hour + 23) % 24 }) { Text("−") }
                    Text(String.format("%02d:%02d", hour, minute), style = MaterialTheme.typography.headlineSmall)
                    OutlinedButton(onClick = { hour = (hour + 1) % 24 }) { Text("+") }
                    OutlinedButton(onClick = { minute = (minute + 15) % 60 }) { Text("+15 min") }
                }
                Text("Připomene se jen tehdy, když ten den ještě neproběhlo opakování.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Section("Obsah kurzu") {
            val last = if (s.lastSync == 0L) "zatím nikdy" else SimpleDateFormat("d. M. yyyy H:mm", Locale.getDefault()).format(Date(s.lastSync))
            Text("Poslední aktualizace: $last")
            Text("Lekcí: ${repo.lessons.size} · témat slovní zásoby: ${repo.packs.size} · slov v zásobě: ${repo.allBankWords().size}", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { save(); onSync() }) { Text("Aktualizovat teď") }
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Adresa dat (pokročilé)") }, singleLine = true)
        }
        Text("Verze ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
    }
}
