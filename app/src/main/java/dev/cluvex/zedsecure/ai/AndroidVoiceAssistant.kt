package dev.cluvex.zedsecure.ai

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Optional Android speech adapter. It only transcribes user speech and speaks text supplied
 * by the selected AI provider; it has no access to source files or arbitrary system commands.
 */
object AndroidVoiceAssistant {
    @Volatile private var activeRecognizer: SpeechRecognizer? = null
    @Volatile private var activeTts: TextToSpeech? = null

    suspend fun listenOnce(context: Context): String = withContext(Dispatchers.Main.immediate) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val activity = context as? Activity
            if (activity != null) {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO_PERMISSION)
                "MIC_PERMISSION_REQUIRED: grant microphone permission, then tap Voice Call again."
            } else {
                "MIC_PERMISSION_REQUIRED: microphone permission is required for voice input."
            }
        } else if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            "VOICE_ERROR: no speech recognition service is available on this device."
        } else {
            suspendCancellableCoroutine { continuation ->
                val recognizer = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
                if (recognizer == null) {
                    continuation.resume("VOICE_ERROR: speech recognition could not start.")
                    return@suspendCancellableCoroutine
                }
                activeRecognizer = recognizer
                var finished = false
                fun finish(result: String) {
                    if (finished) return
                    finished = true
                    if (activeRecognizer === recognizer) activeRecognizer = null
                    runCatching { recognizer.destroy() }
                    if (continuation.isActive) continuation.resume(result)
                }
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull().orEmpty()
                        finish(text)
                    }
                    override fun onError(error: Int) {
                        if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        ) finish("")
                        else finish("VOICE_ERROR: speech recognition failed (code $error).")
                    }
                })
                continuation.invokeOnCancellation { runCatching { recognizer.destroy() } }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                runCatching { recognizer.startListening(intent) }
                    .onFailure { finish("VOICE_ERROR: ${it.message ?: "could not start listening"}.") }
            }
        }
    }

    suspend fun speak(context: Context, text: String): String = withContext(Dispatchers.Main.immediate) {
        if (text.isBlank()) return@withContext "nothing to speak"
        suspendCancellableCoroutine { continuation ->
            var tts: TextToSpeech? = null
            var finished = false
            fun finish(result: String) {
                if (finished) return
                finished = true
                if (activeTts === tts) activeTts = null
                tts?.shutdown()
                if (continuation.isActive) continuation.resume(result)
            }
            tts = TextToSpeech(context.applicationContext) { status ->
                val engine = tts
                activeTts = engine
                if (status != TextToSpeech.SUCCESS || engine == null) {
                    finish("VOICE_ERROR: text-to-speech is unavailable.")
                } else {
                    engine.language = Locale.getDefault()
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) = finish("spoken")
                        override fun onStop(utteranceId: String?, interrupted: Boolean) = finish("stopped")
                        @Deprecated("Deprecated by Android")
                        override fun onError(utteranceId: String?) = finish("VOICE_ERROR: speech playback failed.")
                        override fun onError(utteranceId: String?, errorCode: Int) =
                            finish("VOICE_ERROR: speech playback failed (code $errorCode).")
                    })
                    val result = engine.speak(text.take(6000), TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
                    if (result == TextToSpeech.ERROR) finish("VOICE_ERROR: text-to-speech rejected the response.")
                }
            }
            continuation.invokeOnCancellation { tts?.shutdown() }
        }
    }

    fun stopSpeaking() {
        runCatching { activeRecognizer?.cancel() }
        runCatching { activeTts?.stop() }
    }

    private const val REQUEST_AUDIO_PERMISSION = 8211
}
