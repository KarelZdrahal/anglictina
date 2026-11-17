package cz.anglictina.kurz.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.anglictina.kurz.data.CardState
import cz.anglictina.kurz.data.Progress
import cz.anglictina.kurz.data.Repository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/** Slovíčko „umím“: aspoň dvakrát po sobě správně v různých dnech (další opakování za 6+ dní). */
fun CardState.knownWord() = mastered
/** Věta / cvičení „zvládnuto“: naposledy zodpovězeno správně. */
fun CardState.solved() = !isNew && reps >= 1

@Composable
fun StatsScreen(repo: Repository, onBack: () -> Unit) {
    val p = repo.progress
    val cal = Calendar.getInstance()
    val days = (13 downTo 0).map { back ->
        val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, -back)
        Triple(c.get(Calendar.DAY_OF_MONTH), (p.daySeconds[Progress.fmt(c.time)] ?: 0) / 60f, back == 0)
    }
    val totalMin = p.daySeconds.values.sum() / 60
    val activeDays = p.daySeconds.values.count { it >= 180 }
    val lw = repo.allLessonWords().map { "w:" + it.id }
    val bw = repo.allBankWords().map { "w:" + it.id }
    val lessonIds = repo.lessons.map { it.id }.toSet()
    val drillList = repo.lessons.flatMap { it.exercises } + repo.practice.filter { it.lesson in lessonIds }.flatMap { it.exercises }
    val drills = drillList.map { "x:" + it.id }
    fun wordCounts(ids: List<String>) = Triple(
        ids.count { p.peek(it)?.knownWord() == true },
        ids.count { p.peek(it)?.let { c -> !c.isNew && !c.knownWord() } == true },
        ids.count { p.peek(it)?.isNew ?: true })
    val all = p.cards.values
    val acc = all.sumOf { it.correct }.let { c -> val t = c + all.sumOf { it.wrong }; if (t == 0) "–" else "${c * 100 / t} %" }
    val words = (repo.allLessonWords() + repo.allBankWords()).associateBy { "w:" + it.id }
    val drillById = drillList.associateBy { "x:" + it.id }
    val hardest = p.cards.filter { it.key.startsWith("w:") && it.value.wrong > 0 }.entries
        .sortedByDescending { it.value.wrong - it.value.correct * 0.3 }.take(10).mapNotNull { words[it.key] }

    Screen("Statistiky", onBack = onBack) {
        Section {
            StatRow("dní v řadě" to "${p.streak()}", "aktivních dní" to "$activeDays", "minut celkem" to "$totalMin")
            StatRow("úspěšnost" to acc, "kartiček" to "${all.size}")
        }

        p.last?.let { l ->
            val ans = l.optInt("answered"); val ok = l.optInt("correct")
            val whenTxt = SimpleDateFormat("d. M. yyyy, H:mm", Locale("cs")).format(Date(l.optLong("start")))
            val kind = if (l.optString("kind") == "BANK") "Rozšiřování slovní zásoby" else "Opakování lekcí"
            Section("Poslední procvičování") {
                Text("$kind · $whenTxt", style = MaterialTheme.typography.bodyMedium)
                StatRow("minut" to "${(l.optInt("sec") + 30) / 60}", "odpovědí" to "$ans",
                    "úspěšnost" to (if (ans > 0) "${ok * 100 / ans} %" else "–"), "nových slov" to "${l.optInt("new")}")
                val wrong = l.optJSONArray("wrong")
                if (wrong != null && wrong.length() > 0) {
                    Text("Chyby:", style = MaterialTheme.typography.labelLarge)
                    for (i in 0 until wrong.length()) {
                        val id = wrong.getString(i)
                        val line = words[id]?.let { "${it.en} – ${it.cz}" }
                            ?: drillById[id]?.let { d -> (if (d.type == "read") d.prompt else d.prompt.replace(Regex("\\s*\\(([^()]*/[^()]*)\\)"), " (…)")) + " → " + d.answer.substringBefore(" | ") }
                        if (line != null) Text("• $line", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Section("Posledních 14 dní (minuty denně)") { DayChart(days, repo.settings.minutes) }

        val (lm, ll, ln) = wordCounts(lw)
        val (bm, bl, bn) = wordCounts(bw)
        val dSolved = drills.count { p.peek(it)?.solved() == true }
        val dWrong = drills.count { p.peek(it)?.let { c -> !c.isNew && !c.solved() } == true }
        val dNew = drills.count { p.peek(it)?.isNew ?: true }
        Section("Slovíčka z lekcí") { StatRow("umím" to "$lm", "učím se" to "$ll", "nová" to "$ln") }
        Section("Gramatika a cvičení z lekcí") { StatRow("zvládnuto" to "$dSolved", "k procvičení" to "$dWrong", "zatím neviděno" to "$dNew") }
        Section("Neomezená zásoba") { StatRow("umím" to "$bm", "učím se" to "$bl", "nová" to "$bn") }
        if (hardest.isNotEmpty()) Section("Nejtěžší slova") {
            hardest.forEach { Text("${it.en} – ${it.cz}") }
        }
        Text("Slovo „umím“ = aspoň dvakrát po sobě správně v různých dnech. Cvičení „zvládnuto“ = naposledy správně, " +
            "„k procvičení“ = naposledy chybně (vrátí se v opakování).", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
    }
}

/** Sloupcový graf s osou minut (vlevo), mřížkou, hodnotami nad sloupci a dny v měsíci pod nimi. */
@Composable
private fun DayChart(days: List<Triple<Int, Float, Boolean>>, goal: Int) {
    val maxVal = maxOf(days.maxOf { it.second }, goal.toFloat(), 5f)
    val step = when { maxVal <= 10 -> 2; maxVal <= 30 -> 5; maxVal <= 60 -> 10; else -> 20 }
    val top = (ceil(maxVal * 1.12f / step) * step).toInt()     // rezerva nad nejvyšším sloupcem pro popisek
    val ticks = (0..top step step).toList()
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val goalColor = MaterialTheme.colorScheme.tertiary
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val density = LocalDensity.current
    val labelPx = with(density) { 10.sp.toPx() }
    val chartH = 160.dp
    Row(Modifier.fillMaxWidth()) {
        // osa Y
        Box(Modifier.width(28.dp).height(chartH)) {
            ticks.forEach { t ->
                val frac = 1f - t / top.toFloat()
                Text("$t", fontSize = 10.sp, textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(end = 4.dp)
                        .align(Alignment.TopStart).padding(top = (chartH.value * frac - 7).coerceIn(0f, chartH.value - 14f).dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height(chartH)) {
                val w = size.width / days.size
                val paint = android.graphics.Paint().apply { color = textColor; textSize = labelPx; textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true }
                ticks.forEach { t ->
                    val y = size.height * (1f - t / top.toFloat())
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                val gy = size.height * (1f - goal / top.toFloat())
                drawLine(goalColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                days.forEachIndexed { i, (_, m, _) ->
                    if (m <= 0f) return@forEachIndexed
                    val h = size.height * (m / top)
                    drawRoundRect(primary, Offset(i * w + w * 0.18f, size.height - h), Size(w * 0.64f, h), CornerRadius(4f))
                    val v = Math.round(m)
                    if (v > 0) drawContext.canvas.nativeCanvas.drawText("$v", i * w + w / 2, (size.height - h - 4f).coerceAtLeast(labelPx), paint)
                }
            }
            // osa X: dny v měsíci
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                days.forEach { (d, _, today) ->
                    Text(if (today) "dnes" else "$d", fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1)
                }
            }
        }
    }
    Text("Čárkovaná čára = denní cíl ($goal min).", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
}
