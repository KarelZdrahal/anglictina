package cz.anglictina.kurz

import cz.anglictina.kurz.data.DAY_MS
import cz.anglictina.kurz.data.Item
import cz.anglictina.kurz.data.Mode
import cz.anglictina.kurz.data.Repository
import cz.anglictina.kurz.data.SessionBuilder
import cz.anglictina.kurz.data.Srs
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

/** Týden opakování lekce po 20 minutách denně: věty se nemají opakovat dokola. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeekSimulationTest {
    @Test fun weekOfTwentyMinutesIsVaried() {
        val repo = Repository(RuntimeEnvironment.getApplication()).also { it.load() }
        assertTrue("chybí procvičování k lekci", repo.practice.any { it.lesson == "L01" })
        assertTrue("v opakování nesmí být sady mimo lekce", repo.practice.all { it.lesson.isNotBlank() })
        val base = Srs.startOfDay(System.currentTimeMillis()) + 10 * 3_600_000L
        val rnd = Random(42)
        val correctBefore = HashSet<String>()
        var totalDrills = 0; var repeatedCorrect = 0
        val distinct = HashSet<String>()
        for (day in 0 until 7) {
            val now = base + day * DAY_MS
            val items = SessionBuilder(repo, Random(day), now).lessons(20)
            val drills = items.filterIsInstance<Item.Drill>()
            val words = items.filterIsInstance<Item.Vocab>()
            val rep = drills.count { it.cardId in correctBefore }
            val types = drills.groupingBy { it.ex.type }.eachCount()
            println("den ${day + 1}: ${items.size} položek, vět/textů ${drills.size} (z toho dříve správně zodpovězených $rep), slovíček ${words.size}, typy $types")
            assertTrue("den ${day + 1}: relace je krátká (${items.size})", items.size >= 90)
            assertTrue("den ${day + 1}: málo typů cvičení $types", types.size >= 4)
            totalDrills += drills.size; repeatedCorrect += rep
            drills.forEach { distinct += it.cardId }
            items.forEach { item ->
                if (item is Item.Vocab && item.mode == Mode.INTRO) return@forEach
                val ok = rnd.nextDouble() < 0.85
                Srs.review(repo.progress.card(item.cardId), if (ok) 2 else 0, now + 60_000)
                if (ok && item is Item.Drill) correctBefore += item.cardId
            }
        }
        println("týden: $totalDrills vět/textů, ${distinct.size} různých, opakování již zvládnutých $repeatedCorrect")
        assertTrue("za týden málo různých vět: ${distinct.size}", distinct.size >= 400)
        assertTrue("příliš mnoho opakování týchž vět: $repeatedCorrect z $totalDrills", repeatedCorrect * 10 <= totalDrills)
    }
}
