package cz.anglictina.kurz

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import cz.anglictina.kurz.data.Progress
import cz.anglictina.kurz.data.Repository
import cz.anglictina.kurz.data.Srs
import cz.anglictina.kurz.ui.StatsScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun showsKnownWordsLastSessionAndChart() {
        val repo = Repository(RuntimeEnvironment.getApplication()).also { it.load() }
        val p = repo.progress
        val day = 86_400_000L
        val t0 = System.currentTimeMillis() - 3 * day
        // slovo správně ve dvou různých dnech → „umím“
        repo.lessons[0].words.take(3).forEach { w ->
            val c = p.card("w:" + w.id); Srs.review(c, 2, t0); Srs.review(c, 2, t0 + 2 * day)
        }
        p.daySeconds[Progress.today()] = 17 * 60
        p.recordSession(System.currentTimeMillis(), "LESSONS", 1200, 100, 85, 4, listOf("w:" + repo.lessons[0].words[5].id))
        rule.setContent { StatsScreen(repo, onBack = {}) }
        rule.waitForIdle()
        fun has(t: String) = rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()
        assertTrue("chybí poslední procvičování", has("Poslední procvičování"))
        assertTrue("chybí úspěšnost posledního procvičování", has("85 %"))
        assertTrue("chybí chyby", has(repo.lessons[0].words[5].en))
        assertTrue("chybí popisky dní", has("dnes"))
        assertTrue("slovíčka ‚umím‘ nesedí", has("3"))
        assertTrue("chybí osa", has("Čárkovaná čára"))
    }
}
