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

    fun say(text: String, slow: Boolean = false) {
        if (!ready) return
        tts.setSpeechRate(if (slow) 0.6f else 0.92f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    fun shutdown() { tts.stop(); tts.shutdown() }
}
