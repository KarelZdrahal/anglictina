package cz.anglictina.kurz.data

import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

const val DAY_MS = 24L * 60 * 60 * 1000

/** Stav jedné kartičky v systému opakování v rozestupech (zjednodušené SM-2). */
data class CardState(
    var reps: Int = 0,
    var lapses: Int = 0,
    var ease: Double = 2.5,
    var intervalDays: Double = 0.0,
    var due: Long = 0L,
    var lastSeen: Long = 0L,
    var correct: Int = 0,
    var wrong: Int = 0,
) {
    val isNew get() = reps == 0 && lastSeen == 0L
    /** „Umím“ = interval aspoň 21 dní. */
    val mastered get() = intervalDays >= 21.0
    val learning get() = !isNew && !mastered

    fun toJson() = JSONObject().apply {
        put("r", reps); put("l", lapses); put("e", ease); put("i", intervalDays)
        put("d", due); put("s", lastSeen); put("c", correct); put("w", wrong)
    }

    companion object {
        fun fromJson(o: JSONObject) = CardState(
            o.optInt("r"), o.optInt("l"), o.optDouble("e", 2.5), o.optDouble("i", 0.0),
            o.optLong("d"), o.optLong("s"), o.optInt("c"), o.optInt("w")
        )
    }
}

/** Hodnocení odpovědi: 0 = znovu, 1 = těžké, 2 = dobré, 3 = snadné. */
object Srs {
    fun review(s: CardState, grade: Int, now: Long = System.currentTimeMillis()): CardState {
        s.lastSeen = now
        if (grade == 0) {
            s.lapses++; s.wrong++
            s.reps = 0
            s.ease = max(1.3, s.ease - 0.2)
            s.intervalDays = 0.0
            s.due = now + 10 * 60 * 1000L          // za 10 minut (ještě v této nebo příští relaci)
            return s
        }
        s.correct++
        s.reps++
        s.ease = max(1.3, s.ease + when (grade) { 1 -> -0.15; 3 -> 0.15; else -> 0.0 })
        s.intervalDays = when (s.reps) {
            1 -> if (grade == 3) 3.0 else 1.0
            2 -> if (grade == 1) 3.0 else 6.0
            else -> s.intervalDays * s.ease * (if (grade == 1) 0.8 else if (grade == 3) 1.3 else 1.0)
        }
        // zaokrouhlit na celé dny, splatnost brzy ráno
        val days = max(1, s.intervalDays.roundToInt())
        s.due = startOfDay(now) + days * DAY_MS + 3 * 60 * 60 * 1000L
        return s
    }

    fun startOfDay(t: Long): Long {
        val c = java.util.Calendar.getInstance()
        c.timeInMillis = t
        c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
}
