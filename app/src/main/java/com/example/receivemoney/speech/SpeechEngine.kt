package com.example.receivemoney.speech

import com.example.receivemoney.domain.Gender
import com.example.receivemoney.domain.Language
import com.example.receivemoney.domain.Money

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

    // Shared
    PLAYBACK,
}

enum class SpeechNotice {
    /** No voice of the requested gender is installed; the opposite-gender voice was played with shifted pitch. */
    GENDER_PITCH_SHIFTED,

    /** No voice of the requested gender is installed and no gender-labelled alternative either; default voice used. */
    GENDER_UNAVAILABLE,
}

sealed interface SpeechOutcome {
    data class Completed(val notice: SpeechNotice? = null) : SpeechOutcome
    data class Failed(val error: SpeechError) : SpeechOutcome

    /** Playback was interrupted by [SpeechEngine.stop]. */
    data object Stopped : SpeechOutcome
}

interface SpeechEngine {
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
