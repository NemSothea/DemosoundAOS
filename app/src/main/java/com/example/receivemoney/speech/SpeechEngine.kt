package com.example.receivemoney.speech

import com.example.receivemoney.domain.Gender
import com.example.receivemoney.domain.Language
import com.example.receivemoney.domain.Money

/** An installed voice source (a text-to-speech engine) the user can pin, e.g. "Speech Services by Google". */
data class VoiceSource(val id: String, val label: String)

data class SpeechRequest(
    val language: Language,
    val gender: Gender,
    val amount: Money,
)

enum class SpeechError {
    // Native TTS
    ENGINE_UNAVAILABLE,
    ENGINE_INIT_TIMEOUT,
    LANGUAGE_UNSUPPORTED,
    LANGUAGE_DATA_MISSING,

    // Azure / backend
    NETWORK,
    AZURE_NOT_CONFIGURED,
    AZURE_AUTH_FAILED,
    VOICE_UNAVAILABLE,
    RATE_LIMITED,
    INVALID_REQUEST,
    SERVER,

    // Custom recordings
    CLIP_MISSING,

    // Shared
    PLAYBACK,
}

enum class SpeechNotice {
    /** No voice of the requested gender is installed; the opposite-gender voice was played with shifted pitch. */
    GENDER_PITCH_SHIFTED,

    /** No voice of the requested gender is installed and no gender-labelled alternative either; default voice used. */
    GENDER_UNAVAILABLE,

    /** No custom recording for this language/gender; the device engine spoke instead. */
    CLIPS_FALLBACK,
}

sealed interface SpeechOutcome {
    data class Completed(val notice: SpeechNotice? = null) : SpeechOutcome
    data class Failed(val error: SpeechError) : SpeechOutcome

    /** Playback was interrupted by [SpeechEngine.stop]. */
    data object Stopped : SpeechOutcome
}

interface SpeechEngine {
    /** Voice sources the user may pin, in display order. Empty when the engine has one fixed source. */
    fun sources(): List<VoiceSource> = emptyList()

    /** Restricts speech to the source with [sourceId]; null returns to automatic selection. */
    fun selectSource(sourceId: String?) = Unit

    /**
     * Speaks the announcement and suspends until playback ends.
     * [onStarted] is invoked once audio actually begins. Cancelling the caller stops playback.
     */
    suspend fun speak(request: SpeechRequest, onStarted: () -> Unit): SpeechOutcome

    /**
     * Optionally warms up the voice for [language]/[gender] without producing audio or cost,
     * so a later [speak] starts faster. Default: nothing to prepare.
     */
    suspend fun prepare(language: Language, gender: Gender) = Unit

    /** Stops any ongoing playback. Safe to call when idle. */
    fun stop()

    /** Releases engine resources. The engine must not be used afterwards. */
    fun release()
}
