package cz.anglictina.kurz

import cz.anglictina.kurz.data.DAY_MS
import cz.anglictina.kurz.data.Item
import cz.anglictina.kurz.data.Mode
import cz.anglictina.kurz.data.Repository
import cz.anglictina.kurz.data.SessionBuilder
import cz.anglictina.kurz.data.Srs
import org.junit.Assert.assertEquals
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
    /** Opakování se přepíná na novou lekci v den jejího vydání (čtvrtek) a do další lekce zůstává. */
    @Test fun currentLessonFollowsThursdayDates() {
        val repo = Repository(RuntimeEnvironment.getApplication()).also { it.load() }
        val tz = java.util.TimeZone.getDefault()
        fun at(d: String) = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).apply { timeZone = tz }.parse("$d 12:00")!!.time
        assertEquals("L01", repo.currentLesson(at("2026-11-12"))?.id)
        assertEquals("L01", repo.currentLesson(at("2026-11-18"))?.id)
        assertEquals("L02", repo.currentLesson(at("2026-11-19"))?.id)
        assertEquals("L02", repo.currentLesson(at("2026-11-25"))?.id)
        val items = SessionBuilder(repo, Random(1), at("2026-11-20")).lessons(20)
        val l2 = items.count { it.cardId.contains(":L02-") }; val l1 = items.count { it.cardId.contains(":L01-") }
        assertTrue("den po lekci 2: L02 $l2, L01 $l1 z ${items.size}", l2 + l1 == items.size && l2 * 100 / items.size in 72..88 && l1 > 0)
        // u uživatele, který lekci 1 procvičoval (splatná slovíčka a chyby), musí poměr zůstat stejný
        val p = repo.progress
        repo.lessons.first { it.id == "L01" }.words.forEach { Srs.review(p.card("w:" + it.id), 2, at("2026-11-13")) }
        repo.practice.filter { it.lesson == "L01" }.flatMap { it.exercises }.take(150)
            .forEach { Srs.review(p.card("x:" + it.id), 0, at("2026-11-14")) }
        val items2 = SessionBuilder(repo, Random(2), at("2026-11-20")).lessons(20)
        val m2 = items2.count { it.cardId.contains(":L02-") } * 100 / items2.size
        assertTrue("s pokrokem v L01 je podíl L02 jen $m2 %", m2 in 72..88)
    }

    /** Poslech: jedna nahrávka na každých 10 minut opakování (nejméně jedna). */
    @Test fun oneListeningPerTenMinutes() {
        val repo = Repository(RuntimeEnvironment.getApplication()).also { it.load() }
        for ((min, need) in listOf(5 to 1, 10 to 1, 15 to 1, 20 to 2, 30 to 3)) {
            for (seed in 1..3) {
                val items = SessionBuilder(repo, Random(seed), System.currentTimeMillis()).lessons(min)
                val texts = items.filterIsInstance<Item.Drill>().filter { it.ex.type == "listen" }.map { it.ex.text }.distinct()
                assertEquals("$min min: poslechů ${texts.size}", need, texts.size)
            }
        }
    }

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
            val cur = repo.currentLesson(now)!!.id
            val share = items.count { it.cardId.startsWith("w:$cur-") || it.cardId.startsWith("x:$cur-") } * 100 / items.size
            println("den ${day + 1}: podíl aktuální lekce $cur = $share %")
            assertTrue("den ${day + 1}: podíl aktuální lekce $cur je $share % (má být ~80 %)", share in 72..88)
            val drills = items.filterIsInstance<Item.Drill>()
            val words = items.filterIsInstance<Item.Vocab>()
            val rep = drills.count { it.cardId in correctBefore }
            val types = drills.groupingBy { it.ex.type }.eachCount()
            println("den ${day + 1}: ${items.size} položek, vět/textů ${drills.size} (z toho dříve správně zodpovězených $rep), slovíček ${words.size}, typy $types")
            assertTrue("den ${day + 1}: relace je krátká (${items.size})", items.size >= 90)
            assertTrue("den ${day + 1}: málo typů cvičení $types", types.size >= 4)
            assertTrue("den ${day + 1}: příliš mnoho otázek k článkům $types", (types["read"] ?: 0) <= 10)
            if (day < 6) assertTrue("den ${day + 1}: chybí poslech $types", (types["listen"] ?: 0) >= 2)
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
