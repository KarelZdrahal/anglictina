package cz.anglictina.kurz.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.anglictina.kurz.data.Progress
import cz.anglictina.kurz.data.Repository
import java.util.Calendar

@Composable
fun StatsScreen(repo: Repository, onBack: () -> Unit) {
    val p = repo.progress
    val days = (13 downTo 0).map { back ->
        val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, -back)
        Progress.fmt(c.time) to (p.daySeconds[Progress.fmt(c.time)] ?: 0) / 60f
    }
    val totalMin = p.daySeconds.values.sum() / 60
    val activeDays = p.daySeconds.values.count { it >= 180 }
    val lw = repo.allLessonWords().map { "w:" + it.id }
    val bw = repo.allBankWords().map { "w:" + it.id }
    val drills = repo.lessons.flatMap { l -> l.exercises.map { "x:" + it.id } }
    fun counts(ids: List<String>) = Triple(ids.count { p.peek(it)?.mastered == true }, ids.count { p.peek(it)?.learning == true }, ids.count { p.peek(it)?.isNew ?: true })
    val all = p.cards.values
    val acc = all.sumOf { it.correct }.let { c -> val t = c + all.sumOf { it.wrong }; if (t == 0) "–" else "${c * 100 / t} %" }
    val words = (repo.allLessonWords() + repo.allBankWords()).associateBy { "w:" + it.id }
    val hardest = p.cards.filter { it.key.startsWith("w:") && it.value.wrong > 0 }.entries
        .sortedByDescending { it.value.wrong - it.value.correct * 0.3 }.take(10).mapNotNull { words[it.key] }
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant

    Screen("Statistiky", onBack = onBack) {
        Section {
            StatRow("dní v řadě" to "${p.streak()}", "aktivních dní" to "$activeDays", "minut celkem" to "$totalMin")
            StatRow("úspěšnost" to acc, "kartiček" to "${all.size}")
        }
        Section("Posledních 14 dní (minuty)") {
            val max = (days.maxOf { it.second }).coerceAtLeast(repo.settings.minutes.toFloat())
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val bw2 = size.width / days.size
                days.forEachIndexed { i, (_, m) ->
                    val h = size.height * (m / max)
                    drawRoundRect(track, Offset(i * bw2 + bw2 * 0.15f, 0f), Size(bw2 * 0.7f, size.height), CornerRadius(6f))
                    drawRoundRect(primary, Offset(i * bw2 + bw2 * 0.15f, size.height - h), Size(bw2 * 0.7f, h), CornerRadius(6f))
                }
            }
            Row { Text(days.first().first.substring(5), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall); Text("dnes", style = MaterialTheme.typography.labelSmall) }
        }
        val (lm, ll, ln) = counts(lw)
        val (dm, dl, dn) = counts(drills)
        val (bm, bl, bn) = counts(bw)
        Section("Slovíčka z lekcí") { StatRow("umím" to "$lm", "učím se" to "$ll", "nová" to "$ln") }
        Section("Gramatika z lekcí") { StatRow("umím" to "$dm", "učím se" to "$dl", "nová" to "$dn") }
        Section("Neomezená zásoba") { StatRow("umím" to "$bm", "učím se" to "$bl", "nová" to "$bn") }
        if (hardest.isNotEmpty()) Section("Nejtěžší slova") {
            hardest.forEach { Text("${it.en} – ${it.cz}") }
        }
        Text("„Umím“ znamená, že se slovo nebo cvičení vrací až za 3 týdny a déle.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
    }
}
