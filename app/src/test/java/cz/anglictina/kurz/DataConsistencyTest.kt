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
                val blanks = Regex("_{2,}").findAll(SessionBuilder.promptWithBlank(e.prompt)).count()
                val parts = opts.map { it.split(" / ").size }.toSet()
                if (blanks > 0 && parts != setOf(blanks)) bad += "${e.id}: mezer $blanks, ale možnosti vyplní $parts: ${e.prompt}"
            }
        }
        assertTrue("chybné úlohy (${bad.size}):\n" + bad.take(20).joinToString("\n"), bad.isEmpty())
    }
}
