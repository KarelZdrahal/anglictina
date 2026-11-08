package cz.anglictina.kurz.data

import org.json.JSONArray
import org.json.JSONObject

/** Slovíčko – z lekce nebo z neomezené zásoby. */
data class Word(
    val id: String,
    val en: String,
    val cz: String,
    val ipa: String = "",
    val ex: String = "",
    val level: String = "",
    val source: String = "",   // L01, pack id…
    val extension: Boolean = false,
)

/** Gramatické / lexikální cvičení z lekce. */
data class Exercise(
    val id: String,
    val type: String,          // choose | gap | order | error | transform
    val prompt: String,
    val answer: String,
    val options: List<String> = emptyList(),
    val hint: String = "",
    val lesson: String = "",
)

data class Lesson(
    val id: String,
    val week: Int,
    val date: String,
    val titleEn: String,
    val titleCz: String,
    val grammarFocus: String,
    val grammarHtml: String,
    val audio: String,
    val pdf: String,
    val words: List<Word>,
    val exercises: List<Exercise>,
)

data class BankPack(
    val id: String,
    val topic: String,
    val topicCz: String,
    val level: String,
    val words: List<Word>,
)

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { getString(it) }

fun parseLesson(o: JSONObject): Lesson {
    val id = o.getString("id")
    return Lesson(
        id = id,
        week = o.optInt("week"),
        date = o.optString("date"),
        titleEn = o.optString("title_en"),
        titleCz = o.optString("title_cz"),
        grammarFocus = o.optString("grammar_focus"),
        grammarHtml = o.optString("grammar_html"),
        audio = o.optString("audio"),
        pdf = o.optString("pdf"),
        words = o.optJSONArray("vocab").objects().map {
            Word(
                id = it.getString("id"), en = it.getString("en"), cz = it.getString("cz"),
                ipa = it.optString("ipa"), ex = it.optString("ex"), level = o.optString("level", "B1"),
                source = id, extension = it.optBoolean("ext", false)
            )
        },
        exercises = o.optJSONArray("exercises").objects().map {
            Exercise(
                id = it.getString("id"), type = it.getString("type"), prompt = it.getString("q"),
                answer = it.getString("a"), options = it.optJSONArray("options").strings(),
                hint = it.optString("hint"), lesson = id
            )
        },
    )
}

fun parsePack(o: JSONObject): BankPack {
    val id = o.getString("id")
    val level = o.optString("level")
    return BankPack(
        id = id,
        topic = o.optString("topic"),
        topicCz = o.optString("topic_cz"),
        level = level,
        words = o.optJSONArray("words").objects().map {
            Word(
                id = it.getString("id"), en = it.getString("en"), cz = it.getString("cz"),
                ipa = it.optString("ipa"), ex = it.optString("ex"),
                level = it.optString("level", level), source = id
            )
        },
    )
}
