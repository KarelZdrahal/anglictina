package cz.anglictina.kurz

import cz.anglictina.kurz.data.Repository
import cz.anglictina.kurz.data.SessionBuilder
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Výběr z možností: každá možnost musí vyplnit všechny mezery a správná odpověď musí být mezi možnostmi. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataConsistencyTest {
    @Test fun choiceExercisesFitTheirBlanks() {
        val repo = Repository(RuntimeEnvironment.getApplication()).also { it.load() }
        val b = SessionBuilder(repo)
        val all = repo.lessons.flatMap { it.exercises } + repo.practice.flatMap { it.exercises }
        val bad = mutableListOf<String>()
        for (e in all.filter { it.type == "choose" || it.type == "read" || it.type == "listen" }) {
            val opts = b.drillFor(e).options
            if (opts.size < 2) bad += "${e.id}: málo možností $opts"
            if (opts.none { it.equals(e.answer, true) }) bad += "${e.id}: odpověď '${e.answer}' není mezi $opts"
            if (e.type == "choose") {
                // jedna úloha = jedna mezera (víc mezer s volbou jen k jedné z nich nedává smysl)
                val groups = Regex("\\(([^()]*/[^()]*)\\)").findAll(e.prompt).count()
                if (groups > 1) bad += "${e.id}: $groups skupin možností v jedné větě: ${e.prompt}"
                if (e.options.isNotEmpty() && e.options.none { it.equals(e.answer, true) }) bad += "${e.id}: odpověď chybí v uložených možnostech"
                val blanks = Regex("_{2,}").findAll(SessionBuilder.promptWithBlank(e.prompt)).count()
                val parts = opts.map { it.split(" / ").size }.toSet()
                if (blanks > 0 && parts != setOf(blanks)) bad += "${e.id}: mezer $blanks, ale možnosti vyplní $parts: ${e.prompt}"
            }
        }
        for (e in all.filter { it.type == "gap" || it.type == "error" || it.type == "translate" || it.type == "transform" }) {
            val blanks = Regex("_{2,}").findAll(e.prompt).count()
            val variants = e.answer.split(" | ")
            // alternativy se oddělují „ | “, ne lomítkem; závorky v odpovědi by se musely psát doslova
            if (variants.any { it.contains("(") }) bad += "${e.id}: závorka v odpovědi '${e.answer}'"
            if (blanks <= 1 && variants.any { it.contains(" / ") }) bad += "${e.id}: lomítko v odpovědi '${e.answer}'"
            if (blanks > 1 && variants.none { v -> v.split(" / ", " … ").size == blanks || v.split(" ").size >= blanks })
                bad += "${e.id}: $blanks mezer, odpověď '${e.answer}'"
        }
        assertTrue("chybné úlohy (${bad.size}):\n" + bad.take(20).joinToString("\n"), bad.isEmpty())
    }
}
