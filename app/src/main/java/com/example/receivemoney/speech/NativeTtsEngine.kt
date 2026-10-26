package com.example.receivemoney.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.receivemoney.domain.Gender
import com.example.receivemoney.domain.Language
import com.example.receivemoney.domain.MoneyAnnouncement
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Free mode: the device's built-in TextToSpeech engines. Must be created and used on the main thread.
 *
 * The system default engine is tried first. If it cannot speak the requested language (e.g. Samsung TTS
 * has no Khmer), the other installed engines are asked in turn (e.g. Google Speech Services, which offers
 * Khmer). An engine is only used when it reports the language as available.
 */
class NativeTtsEngine(context: Context) : SpeechEngine {

    private enum class UtteranceResult { DONE, ERROR, STOPPED }

    private class Pending(
        val continuation: CancellableContinuation<UtteranceResult>,
        val onStarted: () -> Unit,
    )

    private class Engine(val tts: TextToSpeech, val initialized: CompletableDeferred<Boolean>) {
        /** Installed voices, read once: [TextToSpeech.getVoices] is a slow IPC call (~100 ms on Google TTS). */
        val voices: List<Voice> by lazy {
            runCatching { tts.voices }.getOrNull().orEmpty()
                .filterNot { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in it.features.orEmpty() }
        }
    }

    private val appContext = context.applicationContext
    private val pending = ConcurrentHashMap<String, Pending>()

    /** Engines keyed by package name; [DEFAULT_ENGINE] is the system default. Created lazily. */
    private val engines = mutableMapOf<String, Engine>()

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            pending[utteranceId]?.onStarted?.invoke()
        }

        override fun onDone(utteranceId: String?) = finish(utteranceId, UtteranceResult.DONE)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finish(utteranceId, UtteranceResult.ERROR)

        override fun onError(utteranceId: String?, errorCode: Int) =
            finish(utteranceId, UtteranceResult.ERROR)

        override fun onStop(utteranceId: String?, interrupted: Boolean) =
            finish(utteranceId, UtteranceResult.STOPPED)
    }

    init {
        // Bind every installed engine now (asynchronously) so neither the first Play nor the first
        // switch to a voice on another engine has to wait for that engine to start.
        val default = engine(DEFAULT_ENGINE)
        otherEnginePackages(default).forEach(::engine)
    }

    private fun otherEnginePackages(default: Engine): List<String> =
        default.tts.engines.map { it.name }.filter { it != default.tts.defaultEngine }

    private fun engine(packageName: String): Engine = engines.getOrPut(packageName) {
        val initialized = CompletableDeferred<Boolean>()
        val listener = TextToSpeech.OnInitListener { status ->
            initialized.complete(status == TextToSpeech.SUCCESS)
        }
        // Initialisation is asynchronous; callers await [Engine.initialized].
        val tts = if (packageName == DEFAULT_ENGINE) {
            TextToSpeech(appContext, listener)
        } else {
            TextToSpeech(appContext, listener, packageName)
        }
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts.setOnUtteranceProgressListener(progressListener)
        Engine(tts, initialized)
    }

    private suspend fun Engine.awaitReady(): Boolean? =
        withTimeoutOrNull(INIT_TIMEOUT_MS) { initialized.await() }

    private sealed interface Resolution {
        class Ready(val tts: TextToSpeech, val notice: SpeechNotice?) : Resolution
        class Failed(val error: SpeechError) : Resolution
    }

    /**
     * Chooses the engine and voice for [language]/[gender] and applies them, ready to speak.
     *  - A voice of the requested gender: used at normal pitch.
     *  - Only a voice of the opposite gender (e.g. the single Khmer voice is female): that voice is
     *    used with its pitch shifted into the requested gender's range, and the caller is told.
     *  - No gender-labelled voice at all: the engine default at normal pitch, and the caller is told.
     */
    private suspend fun resolve(language: Language, gender: Gender): Resolution {
        val default = engine(DEFAULT_ENGINE)
        val defaultReady = default.awaitReady() ?: return Resolution.Failed(SpeechError.ENGINE_INIT_TIMEOUT)
        if (!defaultReady) return Resolution.Failed(SpeechError.ENGINE_UNAVAILABLE)

        val locale = language.locale
        val (engine, voice) = when (val choice = chooseEngine(locale, gender, default)) {
            is EngineChoice.Found -> choice.engine to choice.voice
            EngineChoice.DataMissing -> return Resolution.Failed(SpeechError.LANGUAGE_DATA_MISSING)
            EngineChoice.Unsupported -> return Resolution.Failed(SpeechError.LANGUAGE_UNSUPPORTED)
        }
        val tts = engine.tts

        if (voice != null && tts.setVoice(voice) == TextToSpeech.SUCCESS) {
            tts.setPitch(NORMAL_PITCH)
            return Resolution.Ready(tts, null)
        }

        val oppositeVoice = findVoice(engine, locale, gender.opposite)
        if (oppositeVoice != null && tts.setVoice(oppositeVoice) == TextToSpeech.SUCCESS) {
            tts.setPitch(if (gender == Gender.MALE) PITCH_FEMALE_TO_MALE else PITCH_MALE_TO_FEMALE)
            return Resolution.Ready(tts, SpeechNotice.GENDER_PITCH_SHIFTED)
        }

        tts.setPitch(NORMAL_PITCH)
        val result = tts.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA) return Resolution.Failed(SpeechError.LANGUAGE_DATA_MISSING)
        if (result < TextToSpeech.LANG_AVAILABLE) return Resolution.Failed(SpeechError.LANGUAGE_UNSUPPORTED)
        return Resolution.Ready(tts, SpeechNotice.GENDER_UNAVAILABLE)
    }

    /** Selects the voice ahead of time (no audio) so pressing Play after a Language/Gender change is instant. */
    override suspend fun prepare(language: Language, gender: Gender) {
        val startedAt = SystemClock.elapsedRealtime()
        val resolution = resolve(language, gender)
        if (resolution is Resolution.Ready) {
            // Silent synthesis to a throwaway file makes the engine load the voice model now
            // instead of on the first Play. speak() stops it if Play is pressed meanwhile.
            val warmUpFile = File(appContext.cacheDir, WARM_UP_FILE)
            resolution.tts.synthesizeToFile(WARM_UP_TEXT, Bundle(), warmUpFile, "warm-up-${UUID.randomUUID()}")
            Log.d(TAG, "prepared ${describe(resolution.tts)} voice=$gender/$language in ${SystemClock.elapsedRealtime() - startedAt}ms")
        }
    }

    override suspend fun speak(request: SpeechRequest, onStarted: () -> Unit): SpeechOutcome {
        val startedAt = SystemClock.elapsedRealtime()
        val (tts, notice) = when (val resolution = resolve(request.language, request.gender)) {
            is Resolution.Ready -> resolution.tts to resolution.notice
            is Resolution.Failed -> return SpeechOutcome.Failed(resolution.error)
        }
        Log.d(TAG, "speaking with ${describe(tts)}, resolved in ${SystemClock.elapsedRealtime() - startedAt}ms")

        val text = MoneyAnnouncement.text(request.language, request.amount)
        stop()
        val utteranceId = UUID.randomUUID().toString()
        val result = suspendCancellableCoroutine { cont ->
            pending[utteranceId] = Pending(cont) {
                Log.d(TAG, "audio started afterMs=${SystemClock.elapsedRealtime() - startedAt}")
                onStarted()
            }
            cont.invokeOnCancellation {
                pending.remove(utteranceId)
                tts.stop()
            }
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId) != TextToSpeech.SUCCESS) {
                finish(utteranceId, UtteranceResult.ERROR)
            }
        }
        return when (result) {
            UtteranceResult.DONE -> SpeechOutcome.Completed(notice)
            UtteranceResult.ERROR -> SpeechOutcome.Failed(SpeechError.PLAYBACK)
            UtteranceResult.STOPPED -> SpeechOutcome.Stopped
        }
    }

    private sealed interface EngineChoice {
        /** [voice] is null when no engine has a voice of the requested gender for the language. */
        class Found(val engine: Engine, val voice: Voice?) : EngineChoice
        data object DataMissing : EngineChoice
        data object Unsupported : EngineChoice
    }

    /**
     * Picks the engine and voice. Engines that support [locale] are considered in order (default first).
     *  1. Prefer an engine that has both a male and a female voice, so switching gender keeps the same
     *     engine and the same voice style.
     *  2. Otherwise, the first engine with a voice of [gender].
     *  3. Otherwise, the first engine with a voice of the opposite gender (pitch-shifted by [resolve]).
     *  4. Otherwise, the first supporting engine with its default voice (voice = null).
     */
    private suspend fun chooseEngine(locale: Locale, gender: Gender, default: Engine): EngineChoice {
        var dataMissing = false
        val supporting = mutableListOf<Engine>()
        fun consider(engine: Engine) {
            when (engine.tts.isLanguageAvailable(locale)) {
                TextToSpeech.LANG_MISSING_DATA -> dataMissing = true
                TextToSpeech.LANG_NOT_SUPPORTED -> Unit
                else -> supporting += engine
            }
        }

        consider(default)
        for (packageName in otherEnginePackages(default)) {
            val candidate = engine(packageName)
            if (candidate.awaitReady() == true) consider(candidate)
        }
        if (supporting.isEmpty()) return if (dataMissing) EngineChoice.DataMissing else EngineChoice.Unsupported

        supporting.firstOrNull { e -> Gender.entries.all { findVoice(e, locale, it) != null } }
            ?.let { return EngineChoice.Found(it, findVoice(it, locale, gender)) }
        supporting.firstNotNullOfOrNull { e -> findVoice(e, locale, gender)?.let { EngineChoice.Found(e, it) } }
            ?.let { return it }
        supporting.firstOrNull { e -> findVoice(e, locale, gender.opposite) != null }
            ?.let { return EngineChoice.Found(it, null) }
        return EngineChoice.Found(supporting.first(), null)
    }

    private fun describe(tts: TextToSpeech): String {
        val enginePackage = engines.entries.firstOrNull { it.value.tts === tts }?.key?.ifEmpty { "default" }
        return "engine=$enginePackage"
    }

    /** Returns an installed voice for [locale] whose gender is known to be [gender], if any. */
    private fun findVoice(engine: Engine, locale: Locale, gender: Gender): Voice? {
        return engine.voices
            // Samsung reports 3-letter locales (e.g. eng_USA_f00), so compare ISO-3 codes.
            .filter { iso3Language(it.locale) == iso3Language(locale) }
            .filter { VoiceGenderMatcher.detect(it.name, it.features.orEmpty()) == gender }
            .sortedWith(
                compareByDescending<Voice> { iso3Country(it.locale) == iso3Country(locale) }
                    .thenBy { it.isNetworkConnectionRequired }
                    .thenByDescending { it.quality }
                    .thenBy { it.name }
            )
            .firstOrNull()
    }

    private fun iso3Language(locale: Locale): String =
        runCatching { locale.isO3Language }.getOrDefault(locale.language)

    private fun iso3Country(locale: Locale): String =
        runCatching { locale.isO3Country }.getOrDefault(locale.country)

    private fun finish(utteranceId: String?, result: UtteranceResult) {
        val entry = utteranceId?.let { pending.remove(it) } ?: return
        if (entry.continuation.isActive) entry.continuation.resume(result)
    }

    override fun stop() {
        engines.values.forEach { it.tts.stop() }
    }

    override fun release() {
        pending.clear()
        File(appContext.cacheDir, WARM_UP_FILE).delete()
        engines.values.forEach {
            it.tts.stop()
            it.tts.shutdown()
        }
        engines.clear()
    }

    private companion object {
        const val TAG = "NativeTtsEngine"

        const val NORMAL_PITCH = 1.0f

        // Measured on Google TTS: the female Khmer voice (233 Hz) at 0.6 lands at ~141 Hz, the same as
        // Google's real male English voice; a male voice (140 Hz) at 1.4 lands at ~192 Hz.
        const val PITCH_FEMALE_TO_MALE = 0.6f
        const val PITCH_MALE_TO_FEMALE = 1.4f
        const val WARM_UP_FILE = "tts_warm_up.wav"
        const val WARM_UP_TEXT = "0"
        const val DEFAULT_ENGINE = ""
        const val INIT_TIMEOUT_MS = 5_000L
    }
}
