package cz.anglictina.kurz.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.anglictina.kurz.Speech
import cz.anglictina.kurz.data.Item
import cz.anglictina.kurz.data.Mode
import cz.anglictina.kurz.data.Progress
import cz.anglictina.kurz.data.Repository
import cz.anglictina.kurz.data.SessionBuilder
import cz.anglictina.kurz.data.SessionKind
import cz.anglictina.kurz.data.Srs
import kotlinx.coroutines.delay

private enum class Phase { ASK, FEEDBACK, DONE }

@Composable
fun SessionScreen(
    repo: Repository, speech: Speech, kind: SessionKind, minutes: Int, extra: Boolean,
    onDone: () -> Unit, onMore: (SessionKind, Int, Boolean) -> Unit,
) {
    val builder = remember { SessionBuilder(repo) }
    val queue = remember {
        mutableStateListOf<Item>().apply {
            addAll(if (kind == SessionKind.LESSONS) builder.lessons(minutes) else builder.bank(minutes, extra))
        }
    }
    var idx by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(if (queue.isEmpty()) Phase.DONE else Phase.ASK) }
    var lastCorrect by remember { mutableStateOf(true) }
    var given by remember { mutableStateOf("") }
    var elapsed by remember { mutableIntStateOf(0) }
    var answered by remember { mutableIntStateOf(0) }
    var correct by remember { mutableIntStateOf(0) }
    var introduced by remember { mutableIntStateOf(0) }
    var saved by remember { mutableIntStateOf(0) }      // už uložené sekundy
    val requeued = remember { HashSet<String>() }
    val limit = minutes * 60

    fun persist() {
        repo.progress.addTime(elapsed - saved, 0); saved = elapsed
        repo.progress.save()
    }

    LaunchedEffect(phase) {
        while (phase != Phase.DONE) { delay(1000); elapsed++ }
    }
    DisposableEffect(Unit) { onDispose { persist() } }

    fun next() {
        given = ""
        idx++
        if (idx >= queue.size || elapsed >= limit) { phase = Phase.DONE; persist() } else phase = Phase.ASK
    }

    fun grade(item: Item, g: Int) {
        val st = repo.progress.card(item.cardId)
        if (kind == SessionKind.BANK && st.isNew) {
            val d = Progress.today(); repo.progress.bankNewByDay[d] = (repo.progress.bankNewByDay[d] ?: 0) + 1
        }
        Srs.review(st, g)
        answered++; repo.progress.dayItems[Progress.today()] = (repo.progress.dayItems[Progress.today()] ?: 0) + 1
        if (g > 0) correct++
        lastCorrect = g > 0
        if (g == 0 && requeued.add(item.cardId)) {
            val again = when (item) {
                is Item.Vocab -> builder.quizFor(item.word, if (kind == SessionKind.BANK) repo.allBankWords() else repo.allLessonWords(), forceChoice = true)
                is Item.Drill -> builder.drillFor(item.ex)
            }
            queue.add(minOf(idx + 4, queue.size), again)
        }
        phase = Phase.FEEDBACK
    }

    fun override(item: Item) {
        val st = repo.progress.card(item.cardId)
        st.wrong = (st.wrong - 1).coerceAtLeast(0); st.lapses = (st.lapses - 1).coerceAtLeast(0)
        Srs.review(st, 1)
        correct++
        val later = (idx + 1 until queue.size).firstOrNull { queue[it].cardId == item.cardId }
        if (later != null) queue.removeAt(later)
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { persist(); onDone() }) { Icon(Icons.Default.Close, "Ukončit") }
                LinearProgressIndicator(
                    progress = { (elapsed / limit.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).height(8.dp)
                )
                Spacer(Modifier.padding(4.dp))
                Text(String.format("%d:%02d", (limit - elapsed).coerceAtLeast(0) / 60, (limit - elapsed).coerceAtLeast(0) % 60), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(12.dp))
            if (phase == Phase.DONE) {
                Summary(kind, minutes, elapsed, answered, correct, introduced, onDone, onMore)
                return@Column
            }
            val item = queue[idx]
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("item|$idx|${item.cardId}"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (item) {
                    is Item.Vocab -> VocabItem(item, phase, lastCorrect, given, { given = it }, speech, repo.settings.speakAuto,
                        onIntroDone = { introduced++; next() }, onGrade = { grade(item, it) })
                    is Item.Drill -> DrillItem(item, phase, lastCorrect, given, { given = it }, onGrade = { grade(item, it) }, onOverride = { override(item) })
                }
            }
            if (phase == Phase.FEEDBACK) {
                LaunchedEffect(idx, lastCorrect) {
                    if (lastCorrect && (item is Item.Vocab && item.mode in setOf(Mode.CHOICE_CZ2EN, Mode.CHOICE_EN2CZ, Mode.EXAMPLE_GAP, Mode.LISTEN) || item is Item.Drill && item.ex.type == "choose")) {
                        delay(1100); if (phase == Phase.FEEDBACK) next()
                    }
                }
                Button(onClick = { next() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Pokračovat") }
            }
        }
    }
}

@Composable
private fun Summary(kind: SessionKind, minutes: Int, elapsed: Int, answered: Int, correct: Int, introduced: Int,
                    onDone: () -> Unit, onMore: (SessionKind, Int, Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (answered == 0) "Nic k procvičení" else "Skvělá práce!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (answered == 0) Text(if (kind == SessionKind.BANK) "Vyber si v nastavení zásoby další témata nebo úrovně, případně aktualizuj obsah." else "Zatím není co opakovat. Zkus aktualizovat lekce.")
        Section {
            StatRow(
                "minut" to "${(elapsed + 30) / 60}",
                "odpovědí" to "$answered",
                "úspěšnost" to (if (answered > 0) "${correct * 100 / answered} %" else "–"),
            )
            if (introduced > 0) Text("Nových slov: $introduced", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Hotovo") }
        OutlinedButton(onClick = { onMore(kind, 5, false) }, modifier = Modifier.fillMaxWidth()) { Text("Ještě 5 minut") }
        if (kind == SessionKind.BANK) OutlinedButton(onClick = { onMore(SessionKind.BANK, minutes, true) }, modifier = Modifier.fillMaxWidth()) {
            Text("Další dávka nových slov")
        }
    }
}

@Composable
private fun Prompt(label: String, text: String, sub: String? = null, onSpeak: (() -> Unit)? = null) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
        if (onSpeak != null) IconButton(onClick = onSpeak) { Icon(Icons.AutoMirrored.Filled.VolumeUp, "Přehrát") }
    }
    if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
}

@Composable
private fun Options(options: List<String>, answer: String, phase: Phase, chosen: String, onChoose: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { o ->
            val isAns = o == answer
            val color = when {
                phase != Phase.FEEDBACK -> null
                isAns -> Good
                o == chosen -> Bad
                else -> null
            }
            OutlinedButton(
                onClick = { if (phase == Phase.ASK) onChoose(o) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("option"),
                border = BorderStroke(if (color != null) 2.dp else 1.dp, color ?: MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = color?.copy(alpha = 0.12f) ?: Color.Transparent)
            ) { Text(o, fontSize = 17.sp, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
private fun WordReveal(w: cz.anglictina.kurz.data.Item.Vocab, speech: Speech) {
    Section {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(w.word.en, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (w.word.ipa.isNotBlank()) Text("/${w.word.ipa}/", color = MaterialTheme.colorScheme.secondary)
                Text(w.word.cz)
            }
            IconButton(onClick = { speech.say(w.word.en) }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, "Přehrát") }
        }
        if (w.word.ex.isNotBlank()) Text(w.word.ex, fontStyle = FontStyle.Italic)
    }
}

@Composable
private fun VocabItem(
    it: Item.Vocab, phase: Phase, lastCorrect: Boolean, given: String, setGiven: (String) -> Unit,
    speech: Speech, autoSpeak: Boolean, onIntroDone: () -> Unit, onGrade: (Int) -> Unit,
) {
    val w = it.word
    var chosen by remember(it) { mutableStateOf("") }
    var revealed by remember(it) { mutableStateOf(false) }
    var hint by remember(it) { mutableIntStateOf(0) }
    LaunchedEffect(it) {
        if (it.mode == Mode.LISTEN || (autoSpeak && it.mode in setOf(Mode.INTRO, Mode.CHOICE_EN2CZ))) { delay(250); speech.say(w.en) }
    }
    LaunchedEffect(phase) { if (phase == Phase.FEEDBACK && autoSpeak && it.mode !in setOf(Mode.CHOICE_EN2CZ, Mode.LISTEN)) speech.say(w.en) }

    when (it.mode) {
        Mode.INTRO -> {
            Text("Nové slovo", style = MaterialTheme.typography.labelLarge, color = Orange)
            WordReveal(it, speech)
            Button(onClick = onIntroDone, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Mám to, dál") }
        }
        Mode.CHOICE_EN2CZ -> {
            Prompt("Co znamená…", w.en, null) { speech.say(w.en) }
            Options(it.options, w.cz, phase, chosen) { o -> chosen = o; onGrade(if (o == w.cz) 2 else 0) }
            if (phase == Phase.FEEDBACK) WordReveal(it, speech)
        }
        Mode.LISTEN -> {
            Prompt("Poslechni si a vyber význam", if (phase == Phase.FEEDBACK) w.en else "🔊", null) { speech.say(w.en) }
            TextButton(onClick = { speech.say(w.en, slow = true) }) { Text("Přehrát pomalu") }
            Options(it.options, w.cz, phase, chosen) { o -> chosen = o; onGrade(if (o == w.cz) 2 else 0) }
            if (phase == Phase.FEEDBACK) WordReveal(it, speech)
        }
        Mode.CHOICE_CZ2EN -> {
            Prompt("Jak se řekne anglicky…", w.cz)
            Options(it.options, w.en, phase, chosen) { o -> chosen = o; onGrade(if (o == w.en) 2 else 0) }
            if (phase == Phase.FEEDBACK) WordReveal(it, speech)
        }
        Mode.EXAMPLE_GAP -> {
            Prompt("Doplň slovo do věty", SessionBuilder.blankExample(w) ?: w.ex, w.cz)
            Options(it.options, w.en, phase, chosen) { o -> chosen = o; onGrade(if (o == w.en) 2 else 0) }
            if (phase == Phase.FEEDBACK) WordReveal(it, speech)
        }
        Mode.TYPE_CZ2EN -> {
            Prompt("Napiš anglicky", w.cz)
            if (hint > 0) Text("Nápověda: " + w.en.take(hint) + "…", color = MaterialTheme.colorScheme.secondary)
            AnswerField(given, setGiven, phase) { onGrade(if (SessionBuilder.matches(given, w.en)) (if (hint > 0) 1 else 2) else 0) }
            if (phase == Phase.ASK) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { hint = (hint + 1).coerceAtMost(w.en.length) }) { Text("Nápověda") }
                Button(onClick = { onGrade(if (SessionBuilder.matches(given, w.en)) (if (hint > 0) 1 else 2) else 0) }, enabled = given.isNotBlank()) { Text("Zkontrolovat") }
                TextButton(onClick = { onGrade(0) }) { Text("Nevím") }
            }
            if (phase == Phase.FEEDBACK) {
                Verdict(lastCorrect, w.en)
                WordReveal(it, speech)
            }
        }
        Mode.FLASH -> {
            Prompt("Víš, co to znamená?", w.en, null) { speech.say(w.en) }
            if (!revealed && phase == Phase.ASK) Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Ukázat odpověď") }
            if (revealed || phase == Phase.FEEDBACK) {
                WordReveal(it, speech)
                if (phase == Phase.ASK) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Znovu" to 0, "Těžké" to 1, "Dobře" to 2, "Snadné" to 3).forEach { (l, g) ->
                        Button(onClick = { onGrade(g) }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = when (g) { 0 -> Bad; 1 -> Orange; 2 -> Good; else -> Blue })) { Text(l, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerField(given: String, setGiven: (String) -> Unit, phase: Phase, onSubmit: () -> Unit) {
    OutlinedTextField(
        value = given, onValueChange = setGiven, modifier = Modifier.fillMaxWidth(),
        enabled = phase == Phase.ASK, singleLine = false, label = { Text("Tvoje odpověď") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (given.isNotBlank() && phase == Phase.ASK) onSubmit() })
    )
}

@Composable
private fun Verdict(ok: Boolean, answer: String) {
    Text(if (ok) "Správně!" else "Správná odpověď:", color = if (ok) Good else Bad, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    if (!ok) Text(answer, style = MaterialTheme.typography.titleLarge)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrillItem(it: Item.Drill, phase: Phase, lastCorrect: Boolean, given: String, setGiven: (String) -> Unit, onGrade: (Int) -> Unit, onOverride: () -> Unit) {
    val e = it.ex
    var chosen by remember(it) { mutableStateOf("") }
    var overridden by remember(it) { mutableStateOf(false) }
    when (e.type) {
        "read" -> {
            Text("Přečti si text", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Text(e.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(12.dp))
            }
            Text(e.prompt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Options(it.options, e.answer, phase, chosen) { o -> chosen = o; onGrade(if (o.equals(e.answer, true)) 2 else 0) }
        }
        "choose" -> {
            Prompt("Vyber správnou možnost", SessionBuilder.promptWithBlank(e.prompt))
            Options(it.options, e.answer, phase, chosen) { o -> chosen = o; onGrade(if (o.equals(e.answer, true)) 2 else 0) }
        }
        "order" -> {
            val picked = remember(it) { mutableStateListOf<Int>() }
            Prompt("Seřaď slova do věty", picked.joinToString(" ") { i -> it.tiles[i] }.ifEmpty { "…" })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                it.tiles.forEachIndexed { i, t ->
                    val used = i in picked
                    OutlinedButton(onClick = { if (phase == Phase.ASK) { if (used) picked.remove(i) else picked.add(i) } },
                        enabled = phase == Phase.ASK, modifier = Modifier.testTag(if (used) "tile-used" else "tile"),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (used) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) { Text(t) }
                }
            }
            if (phase == Phase.ASK) Button(onClick = {
                val s = picked.joinToString(" ") { i -> it.tiles[i] }
                onGrade(if (SessionBuilder.matches(s, e.answer)) 2 else 0)
            }, enabled = picked.size == it.tiles.size, modifier = Modifier.fillMaxWidth()) { Text("Zkontrolovat") }
            if (phase == Phase.FEEDBACK) Verdict(lastCorrect, e.answer)
        }
        else -> {
            val label = when (e.type) { "gap" -> "Doplň správný tvar slovesa"; "error" -> "Oprav chybu ve větě"; "transform" -> "Utvoř opak slova"; "translate" -> "Přelož do angličtiny"; else -> "Odpověz" }
            Prompt(label, e.prompt)
            if (e.type == "gap") Text("Když je mezer víc, napiš odpovědi za sebou (např. „lives is staying“).", style = MaterialTheme.typography.bodySmall)
            if (e.type == "translate") Text("Uznávají se běžné varianty. Když to napíšeš jinak a správně, potvrď to tlačítkem pod odpovědí.", style = MaterialTheme.typography.bodySmall)
            AnswerField(given, setGiven, phase) { onGrade(if (SessionBuilder.matches(given, e.answer)) 2 else 0) }
            if (phase == Phase.ASK) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onGrade(if (SessionBuilder.matches(given, e.answer)) 2 else 0) }, enabled = given.isNotBlank()) { Text("Zkontrolovat") }
                TextButton(onClick = { onGrade(0) }) { Text("Nevím") }
            }
            if (phase == Phase.FEEDBACK) {
                Verdict(lastCorrect || overridden, e.answer)
                if (!lastCorrect && !overridden && given.isNotBlank()) {
                    Text("Tvoje odpověď: $given", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { overridden = true; onOverride() }) { Text("Měl/a jsem to správně (jen jinak napsané)") }
                }
            }
        }
    }
}
