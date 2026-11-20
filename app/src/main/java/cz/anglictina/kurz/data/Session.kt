package cz.anglictina.kurz.data

import java.text.Normalizer
import kotlin.random.Random

enum class Mode { INTRO, FLASH, CHOICE_EN2CZ, CHOICE_CZ2EN, TYPE_CZ2EN, LISTEN, EXAMPLE_GAP }

sealed class Item {
    abstract val cardId: String
    data class Vocab(val word: Word, val mode: Mode, val options: List<String> = emptyList()) : Item() {
        override val cardId get() = "w:" + word.id
    }
    data class Drill(val ex: Exercise, val options: List<String> = emptyList(), val tiles: List<String> = emptyList()) : Item() {
        override val cardId get() = "x:" + ex.id
    }
}

enum class SessionKind { LESSONS, BANK }

/** Sestavuje frontu položek pro jednu relaci. */
class SessionBuilder(private val repo: Repository, private val rnd: Random = Random.Default,
                     private val now: Long = System.currentTimeMillis()) {
    private val p = repo.progress

    private fun due(id: String) = p.peek(id)?.let { !it.isNew && it.due <= now } ?: false
    private fun isNew(id: String) = p.peek(id)?.isNew ?: true
    private fun weakness(id: String): Double = p.peek(id)?.let { (it.wrong + 1.0) / (it.correct + 1.0) / it.ease } ?: 1.0

    /**
     * Opakování lekcí: jen látka aktuální lekce (slovíčka, cvičení z lekce a k ní připravené sady procvičování).
     * Slovíčka se opakují v rozestupech; u vět a textů má přednost NOVÁ varianta téhož jevu – správně
     * zodpovězená věta se znovu ukáže až po vyčerpání nových, špatně zodpovězená se vrací (než se zvládne).
     */
    fun lessons(minutes: Int): List<Item> {
        val target = minutes * 5 + 4                     // cca 12 s na položku
        // jen aktuální (poslední vydaná) lekce – po čtvrteční lekci se opakování přepne na ni
        val lessons = listOfNotNull(repo.currentLesson(now))
        if (lessons.isEmpty()) return emptyList()
        val ids = lessons.map { it.id }.toSet()
        val order = lessons.map { it.id }.reversed()     // nejnovější lekce první
        val words = lessons.flatMap { it.words }
        val practice = repo.practice.filter { it.lesson in ids }
        val drills = lessons.flatMap { it.exercises } + practice.flatMap { it.exercises }
        val out = mutableListOf<Item>()
        val used = HashSet<String>()
        var texts = 0                                    // nové články v této relaci (nejvýš jeden)
        var listens = 0                                  // nové poslechy v této relaci (nejvýš dva)
        fun addWord(w: Word) { if (used.add("w:" + w.id)) out += quizFor(w, words) }
        fun addDrill(e: Exercise) {
            if ((e.type == "read" || e.type == "listen") && e.text.isNotBlank()) {
                if ("x:" + e.id in used) return
                if (isNew("x:" + e.id)) {
                    if (e.type == "read") { if (texts >= 1) return; texts++ }
                    else { if (listens >= 2) return; listens++ }
                }
                // otázky k jednomu textu / poslechu jdou za sebou
                drills.filter { it.type == e.type && it.text == e.text && (it.id == e.id || isNew("x:" + it.id) || retry(it.id)) }
                    .sortedBy { it.id }
                    .forEach { if (used.add("x:" + it.id)) out += drillFor(it) }
            } else if (used.add("x:" + e.id)) out += drillFor(e)
        }

        // 1) splatná slovíčka a chybně zodpovězené věty (nejvýš polovina relace)
        val dueW = words.filter { due("w:" + it.id) }.sortedBy { p.peek("w:" + it.id)!!.due }
        val retryX = drills.filter { retry(it.id) }.sortedBy { p.peek("x:" + it.id)!!.due }
        val cap = target / 2
        for (it in interleave(dueW, retryX, 2, 2)) {
            if (out.size >= cap) break
            when (it) { is Word -> addWord(it); is Exercise -> addDrill(it) }
        }

        // 2) nová slovíčka z lekce (s představením)
        val newWords = order.flatMap { id -> lessons.first { it.id == id }.words }.filter { isNew("w:" + it.id) }
        val wordBudget = minOf(12, (target * 0.2).toInt())
        val pending = ArrayDeque<Item>()

        // 3) nové věty/texty: nejnovější lekce první, pestře – střídat sady i typy cvičení
        val newDrills = order.flatMap { lid ->
            val groups = (lessons.first { it.id == lid }.exercises.let { listOf(it) } +
                practice.filter { it.lesson == lid }.flatMap { pk -> pk.exercises.groupBy { it.type }.values })
                .map { g -> g.filter { isNew("x:" + it.id) }.shuffled(rnd).iterator() }
            buildList { while (groups.any { it.hasNext() }) groups.shuffled(rnd).forEach { g -> if (g.hasNext()) add(g.next()) } }
        }
        val nw = newWords.iterator(); val nd = newDrills.iterator()
        var introduced = 0; var k = 0
        val known = words.filter { !isNew("w:" + it.id) && "w:" + it.id !in used }
            .sortedByDescending { weakness("w:" + it.id) + rnd.nextDouble() * 0.5 }.iterator()
        while (out.size < target && (nd.hasNext() || nw.hasNext())) {
            if (nw.hasNext() && introduced < wordBudget && k % 3 == 0) {
                val w = nw.next()
                if (used.add("w:" + w.id)) { out += Item.Vocab(w, Mode.INTRO); introduced++; pending += quizFor(w, words, forceChoice = true) }
            } else if (nd.hasNext()) {
                addDrill(nd.next())
            } else if (nw.hasNext()) {
                val w = nw.next(); if (used.add("w:" + w.id)) { out += Item.Vocab(w, Mode.INTRO); pending += quizFor(w, words, forceChoice = true) }
            }
            // slovíčka lekce průběžně (každá 5. položka), aby nezmizela
            if (k % 5 == 4 && known.hasNext()) addWord(known.next())
            if (pending.size > 1) out += pending.removeFirst()
            k++
        }
        out += pending

        // 4) všechno nové už bylo: nejdéle neviděné položky (nejslabší napřed)
        if (out.size < target) {
            val rest = (words.map { "w:" + it.id to it } + drills.map { "x:" + it.id to it })
                .filter { it.first !in used }
                .sortedBy { (p.peek(it.first)?.lastSeen ?: 0L) - (weakness(it.first) * 86_400_000L).toLong() }
            for ((_, v) in rest) { when (v) { is Word -> addWord(v); is Exercise -> addDrill(v) }; if (out.size >= target) break }
        }
        return finish(out)
    }

    /** Věta/text zodpovězené naposledy špatně a už splatné. */
    private fun retry(id: String) = p.peek("x:" + id)?.let { !it.isNew && it.reps == 0 && it.due <= now } ?: false

    /** Neomezená slovní zásoba: splatná slova + nová dávka. */
    fun bank(minutes: Int, extraBatch: Boolean): List<Item> {
        val s = repo.settings
        val target = minutes * 5 + 4
        val all = repo.allBankWords()
        if (all.isEmpty()) return emptyList()
        val out = mutableListOf<Item>()
        val dueW = all.filter { due("w:" + it.id) }.sortedBy { p.peek("w:" + it.id)!!.due }
        dueW.take(target).forEach { out += quizFor(it, all) }

        val packs = s.bankPacks
        val levels = s.bankLevels
        val pool = repo.packs.filter { packs.isEmpty() || it.id in packs }.flatMap { it.words }
            .filter { levels.isEmpty() || levelKey(it.level) in levels }
            .filter { isNew("w:" + it.id) }
        val today = Progress.today()
        val doneToday = p.bankNewByDay[today] ?: 0
        val allowance = if (extraBatch) s.bankNewPerDay else (s.bankNewPerDay - doneToday).coerceAtLeast(0)
        val room = (target - out.size).coerceAtLeast(0)
        val n = minOf(allowance, (room + 1) / 2, pool.size).let { if (extraBatch) minOf(allowance, pool.size) else it }
        val newOnes = pool.shuffled(rnd).sortedBy { levelRank(it.level) }.take(n)
        val pending = ArrayDeque<Item>()
        newOnes.forEach { w ->
            out += Item.Vocab(w, Mode.INTRO)
            pending += quizFor(w, all, forceChoice = true)
            if (pending.size > 2) out += pending.removeFirst()
        }
        out += pending
        // doplnit slabšími známými slovy
        if (out.size < target) {
            val used = out.map { it.cardId }.toHashSet()
            all.filter { "w:" + it.id !in used && !isNew("w:" + it.id) }
                .sortedByDescending { weakness("w:" + it.id) + rnd.nextDouble() * 0.3 }
                .take(target - out.size).forEach { out += quizFor(it, all) }
        }
        // Když není co opakovat (např. denní dávka je vyčerpaná a známých slov je málo),
        // nepouštět krátkou relaci s jedním slovem dokola – doplnit další nová slova.
        if (out.size < target) {
            val used = out.map { it.cardId }.toHashSet()
            val more = pool.filter { "w:" + it.id !in used }.shuffled(rnd).sortedBy { levelRank(it.level) }
                .take((target - out.size + 1) / 2)
            val pend = ArrayDeque<Item>()
            more.forEach { w ->
                out += Item.Vocab(w, Mode.INTRO)
                pend += quizFor(w, all, forceChoice = true)
                if (pend.size > 2) out += pend.removeFirst()
            }
            out += pend
        }
        return out
    }

    fun newAvailable(): Int {
        val s = repo.settings
        return repo.packs.filter { s.bankPacks.isEmpty() || it.id in s.bankPacks }.flatMap { it.words }
            .count { (s.bankLevels.isEmpty() || levelKey(it.level) in s.bankLevels) && isNew("w:" + it.id) }
    }

    private fun levelKey(l: String) = if (l.startsWith("A")) "B1" else l.take(2)

    private fun levelRank(l: String) = when (l.take(2)) { "A2" -> 0; "B1" -> 1; "B2" -> 2; "C1" -> 3; else -> 2 }

    private fun finish(out: MutableList<Item>) = out

    private fun <A, B> interleave(a: List<A>, b: List<B>, na: Int, nb: Int): List<Any> {
        val r = mutableListOf<Any>(); val ia = a.iterator(); val ib = b.iterator()
        while (ia.hasNext() || ib.hasNext()) {
            repeat(na) { if (ia.hasNext()) r += ia.next() as Any }
            repeat(nb) { if (ib.hasNext()) r += ib.next() as Any }
        }
        return r
    }

    fun quizFor(w: Word, pool: List<Word>, forceChoice: Boolean = false): Item.Vocab {
        val st = p.peek("w:" + w.id)
        val dir = repo.settings.direction
        val level = st?.reps ?: 0
        val modes = when {
            forceChoice -> listOf(Mode.CHOICE_EN2CZ, Mode.CHOICE_CZ2EN, Mode.LISTEN)
            level <= 1 -> listOf(Mode.CHOICE_EN2CZ, Mode.CHOICE_CZ2EN, Mode.LISTEN, Mode.EXAMPLE_GAP)
            else -> listOf(Mode.TYPE_CZ2EN, Mode.FLASH, Mode.EXAMPLE_GAP, Mode.CHOICE_CZ2EN, Mode.LISTEN)
        }.filter {
            when (dir) {
                "en2cz" -> it != Mode.CHOICE_CZ2EN && it != Mode.TYPE_CZ2EN
                "cz2en" -> it != Mode.CHOICE_EN2CZ && it != Mode.LISTEN
                else -> true
            }
        }.filter { it != Mode.EXAMPLE_GAP || blankExample(w) != null }
        val m = modes.ifEmpty { listOf(Mode.FLASH) }.random(rnd)
        val others = pool.filter { it.id != w.id && it.en != w.en }.shuffled(rnd).take(3)
        val opts = when (m) {
            Mode.CHOICE_EN2CZ, Mode.LISTEN -> (others.map { it.cz } + w.cz).shuffled(rnd)
            Mode.CHOICE_CZ2EN, Mode.EXAMPLE_GAP -> (others.map { it.en } + w.en).shuffled(rnd)
            else -> emptyList()
        }
        return Item.Vocab(w, m, opts)
    }

    fun drillFor(e: Exercise): Item.Drill = when (e.type) {
        "choose", "read", "listen" -> Item.Drill(e, e.options.ifEmpty { parseOptions(e.prompt) }.shuffled(rnd))
        "order" -> Item.Drill(e, tiles = e.prompt.split("/").map { it.trim() }.filter { it.isNotEmpty() }.shuffled(rnd))
        else -> Item.Drill(e)
    }

    companion object {
        private val optRe = Regex("\\(([^()]*/[^()]*)\\)")
        fun parseOptions(q: String): List<String> = optRe.find(q)?.groupValues?.get(1)?.split("/")?.map { it.trim() } ?: emptyList()
        fun promptWithBlank(q: String): String = optRe.replace(q, "_____")

        /** Příkladová věta s vynechaným slovem (nebo null, když slovo ve větě není). */
        fun blankExample(w: Word): String? {
            if (w.ex.isBlank()) return null
            val key = w.en.removePrefix("a ").removePrefix("an ").removePrefix("to ").substringBefore(" sb").substringBefore(" sth").trim()
            if (key.length < 3) return null
            val stem = if (key.length > 5 && !key.contains(" ")) key.dropLast(1) else key
            val re = Regex("(?i)\\b" + Regex.escape(stem) + "[\\w-]*")
            return if (re.containsMatchIn(w.ex)) re.replaceFirst(w.ex, "_____") else null
        }

        private val contractions = listOf(
            "won't" to "will not", "can't" to "cannot", "shan't" to "shall not", "n't" to " not",
            "'m" to " am", "'re" to " are", "'ve" to " have", "'ll" to " will", "'d" to " would",
            "he's" to "he is", "she's" to "she is", "it's" to "it is", "that's" to "that is", "there's" to "there is",
            "what's" to "what is", "who's" to "who is", "where's" to "where is", "let's" to "let us"
        )

        fun normalize(s: String): String {
            var t = s.lowercase().replace('’', '\'').replace('‘', '\'').replace("…", " ").replace("...", " ")
            t = Normalizer.normalize(t, Normalizer.Form.NFC)
            for ((a, b) in contractions) t = t.replace(a, b)
            t = t.replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim()
            return t
        }

        /** Odpověď je správně, když se po normalizaci shoduje s některou variantou. */
        fun matches(given: String, answer: String): Boolean {
            val g = normalize(given)
            if (g.isEmpty()) return false
            val variants = answer.split(" | ", ";").map { normalize(it) }
            if (variants.any { it == g }) return true
            // více mezer v jedné odpovědi („lives / is staying“) – stačí vyjmenovat bez lomítek
            val joined = normalize(answer.replace("/", " "))
            return joined == g
        }
    }
}
