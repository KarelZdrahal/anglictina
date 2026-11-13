package cz.anglictina.kurz

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
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

/** Hraje relaci jako uživatel a hlídá, že se fronta posouvá (žádné zacyklení). */
class Player(private val rule: ComposeTestRule, private val rnd: Random) {
    private fun currentTag(): String? {
        val m = SemanticsMatcher("item tag") { n -> n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("item|") == true }
        return rule.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.TestTag)
    }
    fun has(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    fun click(text: String) { rule.onAllNodesWithText(text, substring = true)[0].performClick(); rule.mainClock.advanceTimeBy(50) }
    private fun dump() = runCatching { rule.onRoot(useUnmergedTree = true).printToString() }.getOrDefault("?")

    /** Vrací seznam zobrazených položek. */
    fun play(maxSteps: Int = 400): List<String> {
        val seen = mutableListOf<String>()
        var sameInRow = 0
        var last: String? = null
        for (step in 0 until maxSteps) {
            rule.mainClock.advanceTimeBy(300)
            rule.waitForIdle()
            if (has("Skvělá práce") || has("Nic k procvičení")) { if (seen.isEmpty()) println("KONEC HNED (krok $step):\n${dump()}"); break }
            val tag = currentTag() ?: error("na obrazovce není položka (krok $step)\n${dump()}")
            if (tag == last) sameInRow++ else { sameInRow = 0; seen += tag }
            last = tag
            assertTrue("relace se zasekla na $tag; průběh: ${seen.takeLast(8)}\n${dump()}", sameInRow < 12)
            when {
                has("Mám to, dál") -> click("Mám to, dál")
                has("Pokračovat") -> click("Pokračovat")
                has("Ukázat odpověď") -> click("Ukázat odpověď")
                has("Snadné") -> click("Dobře")
                rule.onAllNodesWithTag("option").fetchSemanticsNodes().isNotEmpty() -> {
                    val opts = rule.onAllNodesWithTag("option")
                    opts[rnd.nextInt(opts.fetchSemanticsNodes().size)].performClick()
                }
                rule.onAllNodesWithTag("tile").fetchSemanticsNodes().isNotEmpty() -> {
                    while (rule.onAllNodesWithTag("tile").fetchSemanticsNodes().isNotEmpty()) {
                        rule.onAllNodesWithTag("tile")[0].performClick(); rule.mainClock.advanceTimeBy(100); rule.waitForIdle()
                    }
                    click("Zkontrolovat")
                }
                has("Nevím") -> {
                    if (rnd.nextBoolean()) click("Nevím") else {
                        rule.onAllNodes(hasSetTextAction())[0].performTextInput("abc"); rule.waitForIdle(); click("Zkontrolovat")
                    }
                }
                else -> error("nevím, co dělat na kroku $step ($tag)\n${dump()}")
            }
        }
        return seen
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionFlowTest {
    @get:Rule val rule = createComposeRule()

    private fun run(kind: SessionKind, rnd: Random) {
        val ctx = RuntimeEnvironment.getApplication()
        val repo = Repository(ctx).also { it.load() }
        assertTrue("chybí data", repo.packs.isNotEmpty() && repo.lessons.isNotEmpty() && repo.practice.isNotEmpty())
        rule.mainClock.autoAdvance = false
        rule.setContent { SessionScreen(repo, Speech(ctx), kind, 5, false, onDone = {}, onMore = { _, _, _ -> }) }
        val seen = Player(rule, rnd).play()
        println("$kind: prošlo ${seen.size} položek: ${seen.take(40)}")
        assertTrue("relace prošla jen ${seen.size} položek", seen.size >= 15)
    }

    @Test fun bankSessionAdvances() = run(SessionKind.BANK, Random(1))
    @Test fun bankSessionAdvances2() = run(SessionKind.BANK, Random(7))
    @Test fun lessonSessionAdvances() = run(SessionKind.LESSONS, Random(3))
    @Test fun lessonSessionAdvances2() = run(SessionKind.LESSONS, Random(11))
}

/** Celá aplikace: úvodní obrazovka → „Učit se“ → relace → „Ještě 5 minut“ → opakování lekcí. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppFlowTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun fullApp() {
        val ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("name", "Test").commit()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        rule.mainClock.autoAdvance = false
        ActivityScenario.launch(MainActivity::class.java).use {
            val p = Player(rule, Random(5))
            rule.mainClock.advanceTimeBy(2000); rule.waitForIdle()
            p.click("Učit se")
            val a = p.play(); println("APP bank: ${a.size} položek: ${a.take(30)}")
            assertTrue("bank: jen ${a.size}", a.size >= 15)
            p.click("Ještě 5 minut"); rule.mainClock.advanceTimeBy(1000); rule.waitForIdle()
            val b = p.play(); println("APP bank +5: ${b.size} položek: ${b.take(30)}")
            assertTrue("bank +5: jen ${b.size}", b.size >= 10)
            p.click("Hotovo"); rule.mainClock.advanceTimeBy(1000); rule.waitForIdle()
            p.click("Začít opakování")
            val c = p.play(); println("APP lekce: ${c.size} položek: ${c.take(30)}")
            assertTrue("lekce: jen ${c.size}", c.size >= 15)
        }
    }
}
