package com.example.receivemoney.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.receivemoney.BuildConfig
import com.example.receivemoney.R
import com.example.receivemoney.audio.AudioPlayer
import com.example.receivemoney.data.AzureConfig
import com.example.receivemoney.data.AzureSpeechRepository
import com.example.receivemoney.domain.AmountParseResult
import com.example.receivemoney.domain.AmountParser
import com.example.receivemoney.domain.Currency
import com.example.receivemoney.domain.Gender
import com.example.receivemoney.domain.Language
import com.example.receivemoney.domain.SpeechMode
import com.example.receivemoney.speech.AzureSpeechEngine
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

class ReceiveMoneyViewModel(
    private val engines: Map<SpeechMode, SpeechEngine>,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceiveMoneyUiState())
    val uiState: StateFlow<ReceiveMoneyUiState> = _uiState.asStateFlow()

    private var playJob: Job? = null
    private var prepareJob: Job? = null

    init {
        // Restore selections after the activity is recreated from saved state (e.g. process death),
        // so they stay consistent with the amount text the system restores in the EditText.
        _uiState.update {
            it.copy(
                language = savedState.get<String>(KEY_LANGUAGE)?.let(Language::valueOf) ?: it.language,
                gender = savedState.get<String>(KEY_GENDER)?.let(Gender::valueOf) ?: it.gender,
                mode = savedState.get<String>(KEY_MODE)?.let(SpeechMode::valueOf) ?: it.mode,
                currency = savedState.get<String>(KEY_CURRENCY)?.let(Currency::valueOf) ?: it.currency,
            )
        }
        savedState.get<String>(KEY_AMOUNT)?.let(::onAmountChanged)
        prepareVoice()
    }

    /** Warms up the selected voice in the background. Never speaks and never calls a paid service. */
    private fun prepareVoice() {
        val state = _uiState.value
        if (state.playback is PlaybackStatus.Loading || state.playback is PlaybackStatus.Speaking) return
        prepareJob?.cancel()
        prepareJob = viewModelScope.launch { engines[state.mode]?.prepare(state.language, state.gender) }
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

    /** Changing currency re-validates the current input (e.g. "5.75" is valid USD but not KHR). */
    fun onCurrencySelected(currency: Currency) {
        savedState[KEY_CURRENCY] = currency.name
        _uiState.update { it.copy(currency = currency) }
        onAmountChanged(_uiState.value.amountInput)
    }

    fun onAmountChanged(input: String) {
        savedState[KEY_AMOUNT] = input
        val parsed = AmountParser.parse(input, _uiState.value.currency)
        _uiState.update {
            it.copy(
                amountInput = input,
                amount = (parsed as? AmountParseResult.Valid)?.amount,
                amountError = (parsed as? AmountParseResult.Invalid)?.error,
            )
        }
    }

    /** Selects an engine. Never starts speech; stops anything currently playing. */
    fun onModeSelected(mode: SpeechMode) {
        if (mode == _uiState.value.mode) return
        stopPlayback()
        savedState[KEY_MODE] = mode.name
        val message = when (mode) {
            SpeechMode.FREE -> UiMessage(R.string.mode_free_selected)
            SpeechMode.PAID -> UiMessage(R.string.mode_paid_selected)
        }
        _uiState.update { it.copy(mode = mode, playback = PlaybackStatus.Idle(message)) }
        prepareVoice()
    }

    fun onPlayClicked() {
        val state = _uiState.value
        val amount = state.amount ?: return
        if (!state.canPlay) return
        val engine = engines.getValue(state.mode)
        val request = SpeechRequest(state.language, state.gender, amount)

        // Stop the previous preview (and any voice warm-up) before starting a new one.
        prepareJob?.cancel()
        playJob?.cancel()
        engines.values.forEach { it.stop() }
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
        engines.values.forEach { it.stop() }
        if (_uiState.value.playback is PlaybackStatus.Loading || _uiState.value.playback is PlaybackStatus.Speaking) {
            _uiState.update { it.copy(playback = PlaybackStatus.Idle(UiMessage(R.string.status_stopped))) }
        }
    }

    override fun onCleared() {
        engines.values.forEach { it.release() }
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
            SpeechError.NETWORK -> UiMessage(R.string.error_network)
            SpeechError.AZURE_NOT_CONFIGURED -> UiMessage(R.string.error_azure_not_configured)
            SpeechError.AZURE_AUTH_FAILED -> UiMessage(R.string.error_azure_auth_failed)
            SpeechError.VOICE_UNAVAILABLE -> UiMessage(
                R.string.error_voice_unavailable,
                listOf(request.gender.labelRes(), language),
            )
            SpeechError.RATE_LIMITED -> UiMessage(R.string.error_rate_limited)
            SpeechError.INVALID_REQUEST -> UiMessage(R.string.error_invalid_request)
            SpeechError.SERVER -> UiMessage(R.string.error_server)
            SpeechError.PLAYBACK -> UiMessage(R.string.error_playback)
        }
    }

    companion object {
        private const val KEY_LANGUAGE = "language"
        private const val KEY_GENDER = "gender"
        private const val KEY_MODE = "mode"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_AMOUNT = "amount"

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                ReceiveMoneyViewModel(
                    mapOf(
                        SpeechMode.FREE to NativeTtsEngine(app),
                        SpeechMode.PAID to AzureSpeechEngine(
                            repository = AzureSpeechRepository.create(
                                AzureConfig(
                                    key = BuildConfig.AZURE_SPEECH_KEY,
                                    region = BuildConfig.AZURE_SPEECH_REGION,
                                    voices = mapOf(
                                        (Language.ENGLISH to Gender.FEMALE) to BuildConfig.AZURE_VOICE_EN_FEMALE,
                                        (Language.ENGLISH to Gender.MALE) to BuildConfig.AZURE_VOICE_EN_MALE,
                                        (Language.KHMER to Gender.FEMALE) to BuildConfig.AZURE_VOICE_KM_FEMALE,
                                        (Language.KHMER to Gender.MALE) to BuildConfig.AZURE_VOICE_KM_MALE,
                                    ),
                                )
                            ),
                            player = AudioPlayer(),
                            cacheDir = app.cacheDir,
                        ),
                    ),
                    createSavedStateHandle(),
                )
            }
        }
    }
}

fun Language.labelRes(): Int = when (this) {
    Language.KHMER -> R.string.language_khmer
    Language.ENGLISH -> R.string.language_english
}

fun Gender.labelRes(): Int = when (this) {
    Gender.MALE -> R.string.gender_male
    Gender.FEMALE -> R.string.gender_female
}

fun Currency.labelRes(): Int = when (this) {
    Currency.USD -> R.string.currency_usd
    Currency.KHR -> R.string.currency_khr
}
