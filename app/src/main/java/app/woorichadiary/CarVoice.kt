package app.woorichadiary

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode
import android.content.Context
import android.util.Log

/** On-device voice. Failure is non-fatal because every line is also shown on screen. */
class CarVoice(context: Context) {
    private val tts: TextToSpeech? = try {
        TextToSpeech(context.applicationContext, Mode.ON_DEVICE).also { it.initialize() }
    } catch (e: Exception) {
        Log.w(TAG, "TTS unavailable", e)
        null
    }

    fun say(text: String) {
        try {
            tts?.speak(text)
        } catch (e: Exception) {
            Log.w(TAG, "speak failed", e)
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "stop failed", e)
        }
    }

    private companion object {
        const val TAG = "CarVoice"
    }
}
