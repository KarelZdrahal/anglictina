package cz.anglictina.kurz

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Výslovnost přes systémový hlasový výstup (Google TTS v telefonu). */
class Speech(ctx: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(ctx.applicationContext, this)
    var ready = false; private set

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts.setLanguage(Locale.UK)
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.US)
            tts.setSpeechRate(0.92f)
            ready = true
        }
    }

    /** Naposledy přehraný text (u poslechu se při dalších otázkách nepřehrává znovu sám). */
    var lastText: String = ""; private set

    fun say(text: String, slow: Boolean = false) {
        lastText = text
        if (!ready) return
        val spoken = spokenForm(text)
        tts.setSpeechRate(if (slow) 0.6f else 0.92f)
        tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    companion object {
        /** Slovníkové zkratky se čtou celé: sb → somebody, sth → something, sb's → somebody's … */
        fun spokenForm(text: String): String = text
            .replace(Regex("\\bsb(?=['’]s\\b)", RegexOption.IGNORE_CASE), "somebody")
            .replace(Regex("\\bsth(?=['’]s\\b)", RegexOption.IGNORE_CASE), "something")
            .replace(Regex("\\bsb\\b\\.?", RegexOption.IGNORE_CASE), "somebody")
            .replace(Regex("\\bsth\\b\\.?", RegexOption.IGNORE_CASE), "something")
            .replace(Regex("\\bsw\\b\\.?", RegexOption.IGNORE_CASE), "somewhere")
            .replace(Regex("\\bsmb\\b\\.?", RegexOption.IGNORE_CASE), "somebody")
            .replace(Regex("\\bsmth\\b\\.?", RegexOption.IGNORE_CASE), "something")
    }

    fun shutdown() { tts.stop(); tts.shutdown() }
}
