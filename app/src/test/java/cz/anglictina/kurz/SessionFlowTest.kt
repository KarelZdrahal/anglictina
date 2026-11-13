package cz.anglictina.kurz

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import cz.anglictina.kurz.data.Repository
import cz.anglictina.kurz.data.SessionKind
import cz.anglictina.kurz.ui.SessionScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

/** Projde celou relaci jako uživatel a hlídá, že se fronta posouvá (žádné zacyklení). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionFlowTest {
    @get:Rule val rule = createComposeRule()

    private fun currentTag(): String? {
        val m = SemanticsMatcher("item tag") { n -> n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("item|") == true }
        return rule.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.TestTag)
    }

    private fun has(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    private fun click(text: String) = rule.onAllNodesWithText(text, substring = true)[0].performClick()

    private fun run(kind: SessionKind, rnd: Random) {
        val ctx = RuntimeEnvironment.getApplication()
        val repo = Repository(ctx).also { it.load() }
        assertTrue("chybí data", repo.packs.isNotEmpty() && repo.lessons.isNotEmpty())
        val speech = Speech(ctx)
        rule.mainClock.autoAdvance = false
        rule.setContent { SessionScreen(repo, speech, kind, 5, false, onDone = {}, onMore = { _, _, _ -> }) }
        val seen = mutableListOf<String>()
        var sameInRow = 0
        var last: String? = null
        for (step in 0 until 400) {
            rule.mainClock.advanceTimeBy(300)
            rule.waitForIdle()
            if (has("Skvělá práce") || has("Nic k procvičení")) break
            val tag = currentTag() ?: error("na obrazovce není položka (krok $step)")
            if (tag == last) sameInRow++ else { sameInRow = 0; seen += tag }
            last = tag
            assertTrue("relace se zasekla na $tag; průběh: ${seen.takeLast(8)}", sameInRow < 12)
            when {
                has("Mám to, dál") -> click("Mám to, dál")
                has("Pokračovat") -> click("Pokračovat")
                has("Ukázat odpověď") -> click("Ukázat odpověď")
                has("Dobře") -> click("Dobře")
                rule.onAllNodesWithTag("option").fetchSemanticsNodes().isNotEmpty() -> {
                    val opts = rule.onAllNodesWithTag("option")
                    opts[rnd.nextInt(opts.fetchSemanticsNodes().size)].performClick()
                }
                has("Nevím") -> {
                    if (rnd.nextBoolean()) click("Nevím") else {
                        rule.onAllNodes(hasSetTextAction())[0].performTextInput("abc"); rule.waitForIdle(); click("Zkontrolovat")
                    }
                }
                has("Seřaď slova") -> {
                    while (rule.onAllNodesWithTag("tile").fetchSemanticsNodes().isNotEmpty()) {
                        rule.onAllNodesWithTag("tile")[0].performClick(); rule.waitForIdle()
                    }
                    click("Zkontrolovat")
                }
                else -> error("nevím, co dělat na kroku $step ($tag)")
            }
        }
        println("$kind: prošlo ${seen.size} položek, poslední: ${seen.takeLast(3)}")
        assertTrue("relace prošla jen ${seen.size} položek: $seen", seen.size >= 15)
    }

    @Test fun bankSessionAdvances() = run(SessionKind.BANK, Random(1))
    @Test fun bankSessionAdvances2() = run(SessionKind.BANK, Random(7))
    @Test fun lessonSessionAdvances() = run(SessionKind.LESSONS, Random(3))
}
