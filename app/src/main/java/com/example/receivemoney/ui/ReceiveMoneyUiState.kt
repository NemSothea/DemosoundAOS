package com.example.receivemoney.ui

import androidx.annotation.StringRes
import com.example.receivemoney.domain.AmountError
import com.example.receivemoney.domain.Currency
import com.example.receivemoney.domain.Gender
import com.example.receivemoney.domain.Language
import com.example.receivemoney.domain.SpeechMode
import com.example.receivemoney.domain.Money

/** A user-facing message as a string resource whose format arguments are also string resources. */
data class UiMessage(@StringRes val resId: Int, @StringRes val argResIds: List<Int> = emptyList())

sealed interface PlaybackStatus {
    data class Idle(val message: UiMessage? = null) : PlaybackStatus

    /** Preparing speech (engine start-up or Azure generation). Play is disabled. */
    data object Loading : PlaybackStatus
    data object Speaking : PlaybackStatus
    data class Success(val notice: UiMessage? = null) : PlaybackStatus
    data class Error(val message: UiMessage) : PlaybackStatus
}

data class ReceiveMoneyUiState(
    val language: Language = Language.ENGLISH,
    val gender: Gender = Gender.FEMALE,
    val mode: SpeechMode = SpeechMode.FREE,
    val currency: Currency = Currency.USD,
    val amountInput: String = "",
    val amount: Money? = null,
    val amountError: AmountError? = AmountError.EMPTY,
    val playback: PlaybackStatus = PlaybackStatus.Idle(),
) {
    val canPlay: Boolean get() = amount != null && playback !is PlaybackStatus.Loading
}
