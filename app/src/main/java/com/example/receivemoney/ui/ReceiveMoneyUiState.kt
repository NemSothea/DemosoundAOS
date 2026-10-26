package com.example.receivemoney.ui

import androidx.annotation.StringRes
import com.example.receivemoney.domain.Currency
import com.example.receivemoney.domain.Gender
import com.example.receivemoney.domain.Language
import com.example.receivemoney.domain.Money
import com.example.receivemoney.domain.PresetAmounts
import com.example.receivemoney.speech.VoiceSource

/** A user-facing message as a string resource whose format arguments are also string resources. */
data class UiMessage(@StringRes val resId: Int, @StringRes val argResIds: List<Int> = emptyList())

sealed interface PlaybackStatus {
    data class Idle(val message: UiMessage? = null) : PlaybackStatus

    /** Preparing speech (engine start-up). Play is disabled. */
    data object Loading : PlaybackStatus
    data object Speaking : PlaybackStatus
    data class Success(val notice: UiMessage? = null) : PlaybackStatus
    data class Error(val message: UiMessage) : PlaybackStatus
}

data class ReceiveMoneyUiState(
    /** Installed voice sources, in display order. */
    val sources: List<VoiceSource> = emptyList(),
    /** Pinned voice source, or null for automatic selection. */
    val sourceId: String? = null,
    val language: Language = Language.KHMER,
    val gender: Gender = Gender.FEMALE,
    val currency: Currency = Currency.USD,
    val playback: PlaybackStatus = PlaybackStatus.Idle(),
) {
    /** The fixed demo amount for the selected currency; there is no amount input. */
    val amount: Money get() = PresetAmounts.forCurrency(currency)
    val canPlay: Boolean get() = playback !is PlaybackStatus.Loading
}
