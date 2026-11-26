package cz.anglictina.kurz

import cz.anglictina.kurz.data.Repository
import cz.anglictina.kurz.data.Srs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Modrá čára lekce počítá i procvičovací sady, nejen cvičení z PDF (Karel 26. 11. 2026). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LessonMasteryTest {
    @Test fun practiceSetsCountTowardLessonMastery() {
        val repo = Repository(RuntimeEnvironment.getApplication()).also { it.load() }
        val l = repo.lessons.first { it.id == "L02" }
        assertEquals(0f, repo.lessonMastery(l), 0.0001f)
        val practice = repo.practice.filter { it.lesson == "L02" }.flatMap { it.exercises }
        val total = l.words.size + l.exercises.size + practice.size
        // polovina procvičovacích vět správně
        practice.take(practice.size / 2).forEach { Srs.review(repo.progress.card("x:" + it.id), 2) }
        val half = repo.lessonMastery(l)
        assertEquals((practice.size / 2).toFloat() / total, half, 0.0001f)
        // chybná odpověď se nepočítá
        practice.take(10).forEach { Srs.review(repo.progress.card("x:" + it.id), 0) }
        assertTrue(repo.lessonMastery(l) < half)
        // slovíčko rozpracované = 0,5, s fajfkou (dvakrát správně) = 1
        val w = l.words.first()
        val before = repo.lessonMastery(l)
        Srs.review(repo.progress.card("w:" + w.id), 2)
        assertEquals(before + 0.5f / total, repo.lessonMastery(l), 0.0001f)
        Srs.review(repo.progress.card("w:" + w.id), 2)
        assertEquals(before + 1f / total, repo.lessonMastery(l), 0.0001f)
        // jiná lekce se nemění
        assertEquals(0f, repo.lessonMastery(repo.lessons.first { it.id == "L01" }), 0.0001f)
    }
}
