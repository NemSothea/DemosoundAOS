package com.example.receivemoney.speech

import com.example.receivemoney.domain.Gender
import com.example.receivemoney.domain.Language

/**
 * Routes each request to the custom recordings ([clips]) or the device engine ([native]).
 *
 *  - Automatic (no pinned source): custom recordings when every clip for the request exists,
 *    otherwise the device engine, and the caller is told ([SpeechNotice.CLIPS_FALLBACK]).
 *  - [CUSTOM_SOURCE] pinned: custom recordings only; a missing clip is an error.
 *  - Any other source id: the device engine, pinned to that id.
 */
class CompositeSpeechEngine(
    private val clips: ClipSpeechEngine,
    private val native: SpeechEngine,
) : SpeechEngine {

    private enum class Route { AUTO, CUSTOM, NATIVE }

    @Volatile private var route = Route.AUTO

    override fun sources(): List<VoiceSource> =
        listOf(VoiceSource(CUSTOM_SOURCE, CUSTOM_LABEL)) + native.sources()

    override fun selectSource(sourceId: String?) {
        route = when (sourceId) {
            null -> Route.AUTO
            CUSTOM_SOURCE -> Route.CUSTOM
            else -> Route.NATIVE
        }
        native.selectSource(sourceId.takeIf { route == Route.NATIVE })
    }

    override suspend fun prepare(language: Language, gender: Gender) {
        when (route) {
            Route.CUSTOM -> clips.prepare(language, gender)
            Route.NATIVE -> native.prepare(language, gender)
            Route.AUTO -> { clips.prepare(language, gender); native.prepare(language, gender) }
        }
    }

    override suspend fun speak(request: SpeechRequest, onStarted: () -> Unit): SpeechOutcome = when (route) {
        Route.CUSTOM -> if (clips.available(request)) clips.speak(request, onStarted) else SpeechOutcome.Failed(SpeechError.CLIP_MISSING)
        Route.NATIVE -> native.speak(request, onStarted)
        Route.AUTO -> if (clips.available(request)) {
            clips.speak(request, onStarted)
        } else {
            when (val outcome = native.speak(request, onStarted)) {
                is SpeechOutcome.Completed -> SpeechOutcome.Completed(outcome.notice ?: SpeechNotice.CLIPS_FALLBACK)
                else -> outcome
            }
        }
    }

    override fun stop() {
        clips.stop()
        native.stop()
    }

    override fun release() {
        clips.release()
        native.release()
    }

    companion object {
        const val CUSTOM_SOURCE = "custom"
        const val CUSTOM_LABEL = "Custom recordings"
    }
}
