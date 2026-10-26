package com.example.receivemoney.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.receivemoney.R
import com.example.receivemoney.domain.Currency
import com.example.receivemoney.domain.Gender
import com.example.receivemoney.domain.Language
import com.example.receivemoney.speech.ClipEngine
import com.example.receivemoney.speech.CompositeSpeechEngine
import com.example.receivemoney.speech.NativeTtsEngine
import com.example.receivemoney.speech.SpeechEngine
import com.example.receivemoney.speech.SpeechError
import com.example.receivemoney.speech.SpeechNotice
import com.example.receivemoney.speech.SpeechOutcome
import com.example.receivemoney.speech.SpeechRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Drives the speech engine (custom recordings with FreeSDK/Native fallback). The amount is a fixed preset per currency. */
class ReceiveMoneyViewModel(
    private val engine: SpeechEngine,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceiveMoneyUiState(sources = engine.sources()))
    val uiState: StateFlow<ReceiveMoneyUiState> = _uiState.asStateFlow()

    private var playJob: Job? = null
    private var prepareJob: Job? = null

    init {
        // Restore selections after the activity is recreated from saved state (e.g. process death).
        val sources = _uiState.value.sources
        val restoredSource = savedState.get<String>(KEY_SOURCE)?.takeIf { id -> sources.any { it.id == id } }
        _uiState.update {
            it.copy(
                sourceId = restoredSource,
                language = savedState.get<String>(KEY_LANGUAGE)?.let(Language::valueOf) ?: it.language,
                gender = savedState.get<String>(KEY_GENDER)?.let(Gender::valueOf) ?: it.gender,
                currency = savedState.get<String>(KEY_CURRENCY)?.let(Currency::valueOf) ?: it.currency,
            )
        }
        engine.selectSource(restoredSource)
        prepareVoice()
    }

    /** Warms up the selected voice in the background. Never speaks. */
    private fun prepareVoice() {
        val state = _uiState.value
        if (state.playback is PlaybackStatus.Loading || state.playback is PlaybackStatus.Speaking) return
        prepareJob?.cancel()
        prepareJob = viewModelScope.launch { engine.prepare(state.language, state.gender) }
    }

    /** Pins a voice source; null returns to automatic selection. Stops anything currently playing. */
    fun onSourceSelected(sourceId: String?) {
        if (sourceId == _uiState.value.sourceId) return
        stopPlayback()
        savedState[KEY_SOURCE] = sourceId
        engine.selectSource(sourceId)
        _uiState.update { it.copy(sourceId = sourceId) }
        prepareVoice()
    }

    fun onLanguageSelected(language: Language) {
        savedState[KEY_LANGUAGE] = language.name
        _uiState.update { it.copy(language = language) }
        prepareVoice()
    }

    fun onGenderSelected(gender: Gender) {
        savedState[KEY_GENDER] = gender.name
        _uiState.update { it.copy(gender = gender) }
        prepareVoice()
    }

    /** Switches the preset amount ($5 or ៛20,000). Never starts speech. */
    fun onCurrencySelected(currency: Currency) {
        savedState[KEY_CURRENCY] = currency.name
        _uiState.update { it.copy(currency = currency) }
    }

    fun onPlayClicked() {
        val state = _uiState.value
        if (!state.canPlay) return
        val request = SpeechRequest(state.language, state.gender, state.amount)

        // Stop the previous preview (and any voice warm-up) before starting a new one.
        prepareJob?.cancel()
        playJob?.cancel()
        engine.stop()
        _uiState.update { it.copy(playback = PlaybackStatus.Loading) }

        playJob = viewModelScope.launch {
            val outcome = engine.speak(request) {
                _uiState.update { it.copy(playback = PlaybackStatus.Speaking) }
            }
            _uiState.update { it.copy(playback = outcome.toStatus(request)) }
        }
    }

    /** Stops playback, e.g. when the screen goes to the background. */
    fun stopPlayback() {
        playJob?.cancel()
        playJob = null
        engine.stop()
        if (_uiState.value.playback is PlaybackStatus.Loading || _uiState.value.playback is PlaybackStatus.Speaking) {
            _uiState.update { it.copy(playback = PlaybackStatus.Idle(UiMessage(R.string.status_stopped))) }
        }
    }

    override fun onCleared() {
        engine.release()
    }

    private fun SpeechOutcome.toStatus(request: SpeechRequest): PlaybackStatus = when (this) {
        is SpeechOutcome.Completed -> PlaybackStatus.Success(
            when (notice) {
                SpeechNotice.GENDER_PITCH_SHIFTED -> UiMessage(
                    R.string.notice_gender_pitch_shifted,
                    listOf(request.gender.labelRes(), request.language.labelRes(), request.gender.opposite.labelRes()),
                )
                SpeechNotice.GENDER_UNAVAILABLE -> UiMessage(
                    R.string.notice_gender_unavailable,
                    listOf(request.gender.labelRes(), request.language.labelRes()),
                )
                SpeechNotice.CLIPS_FALLBACK -> UiMessage(
                    R.string.notice_clips_fallback,
                    listOf(request.gender.labelRes(), request.language.labelRes()),
                )
                null -> null
            }
        )
        SpeechOutcome.Stopped -> PlaybackStatus.Idle(UiMessage(R.string.status_stopped))
        is SpeechOutcome.Failed -> PlaybackStatus.Error(error.toMessage(request))
    }

    private fun SpeechError.toMessage(request: SpeechRequest): UiMessage {
        val language = request.language.labelRes()
        return when (this) {
            SpeechError.ENGINE_UNAVAILABLE -> UiMessage(R.string.error_engine_unavailable)
            SpeechError.ENGINE_INIT_TIMEOUT -> UiMessage(R.string.error_engine_init_timeout)
            SpeechError.LANGUAGE_UNSUPPORTED -> UiMessage(R.string.error_language_unsupported, listOf(language))
            SpeechError.LANGUAGE_DATA_MISSING -> UiMessage(R.string.error_language_data_missing, listOf(language))
            SpeechError.CLIP_MISSING -> UiMessage(R.string.error_clip_missing, listOf(request.gender.labelRes(), language))
            // Only the native engine is used; any other failure is reported as a playback problem.
            SpeechError.NETWORK,
            SpeechError.AZURE_NOT_CONFIGURED,
            SpeechError.AZURE_AUTH_FAILED,
            SpeechError.VOICE_UNAVAILABLE,
            SpeechError.RATE_LIMITED,
            SpeechError.INVALID_REQUEST,
            SpeechError.SERVER,
            SpeechError.PLAYBACK -> UiMessage(R.string.error_playback)
        }
    }

    companion object {
        private const val KEY_SOURCE = "source"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_GENDER = "gender"
        private const val KEY_CURRENCY = "currency"

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                ReceiveMoneyViewModel(
                    CompositeSpeechEngine(clips = ClipEngine(app), native = NativeTtsEngine(app)),
                    createSavedStateHandle(),
                )
            }
        }
    }
}

fun Language.labelRes(): Int = when (this) {
    Language.KHMER -> R.string.language_khmer
    Language.ENGLISH -> R.string.language_english
    Language.KOREAN -> R.string.language_korean
    Language.JAPANESE -> R.string.language_japanese
    Language.CHINESE -> R.string.language_chinese
}

fun Gender.labelRes(): Int = when (this) {
    Gender.MALE -> R.string.gender_male
    Gender.FEMALE -> R.string.gender_female
}

fun Currency.labelRes(): Int = when (this) {
    Currency.USD -> R.string.currency_usd
    Currency.KHR -> R.string.currency_khr
}
