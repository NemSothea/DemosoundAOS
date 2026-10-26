package com.example.receivemoney

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.receivemoney.databinding.ActivityMainBinding
import com.example.receivemoney.domain.AmountError
import com.example.receivemoney.domain.Currency
import com.example.receivemoney.domain.Gender
import com.example.receivemoney.domain.Language
import com.example.receivemoney.domain.SpeechMode
import com.example.receivemoney.ui.PlaybackStatus
import com.example.receivemoney.ui.ReceiveMoneyUiState
import com.example.receivemoney.ui.ReceiveMoneyViewModel
import com.example.receivemoney.ui.UiMessage
import com.example.receivemoney.ui.labelRes
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ReceiveMoneyViewModel by viewModels { ReceiveMoneyViewModel.Factory }

    private val languages = Language.entries
    private val genders = Gender.entries
    private val currencies = Currency.entries

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        ViewCompat.setAccessibilityHeading(binding.title, true)
        setUpDropdowns()
        binding.amountInput.doAfterTextChanged { viewModel.onAmountChanged(it?.toString().orEmpty()) }
        binding.playButton.setOnClickListener { viewModel.onPlayClicked() }
        binding.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.onModeSelected(if (checkedId == R.id.paidButton) SpeechMode.PAID else SpeechMode.FREE)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Keep speaking across rotation, but not when the user leaves the screen.
        if (!isChangingConfigurations) viewModel.stopPlayback()
    }

    private fun setUpDropdowns() {
        binding.languageDropdown.setAdapter(
            ArrayAdapter(this, R.layout.item_dropdown, languages.map { getString(it.labelRes()) })
        )
        binding.languageDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.onLanguageSelected(languages[position])
        }
        binding.genderDropdown.setAdapter(
            ArrayAdapter(this, R.layout.item_dropdown, genders.map { getString(it.labelRes()) })
        )
        binding.genderDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.onGenderSelected(genders[position])
        }
        binding.currencyDropdown.setAdapter(
            ArrayAdapter(this, R.layout.item_dropdown, currencies.map { getString(it.labelRes()) })
        )
        binding.currencyDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.onCurrencySelected(currencies[position])
        }
    }

    private fun render(state: ReceiveMoneyUiState) = with(binding) {
        // setText(..., false) keeps the dropdown from filtering its own items.
        languageDropdown.setText(getString(state.language.labelRes()), false)
        genderDropdown.setText(getString(state.gender.labelRes()), false)
        currencyDropdown.setText(getString(state.currency.labelRes()), false)
        if (amountInput.text?.toString() != state.amountInput) amountInput.setText(state.amountInput)

        val isUsd = state.currency == Currency.USD
        amountLayout.prefixText = getString(if (isUsd) R.string.currency_prefix_usd else R.string.currency_prefix_khr)
        amountLayout.placeholderText = getString(if (isUsd) R.string.amount_hint_usd else R.string.amount_hint_khr)
        amountLayout.helperText = getString(if (isUsd) R.string.amount_helper_usd else R.string.amount_helper_khr)
        amountLayout.error = state.amountError?.let { amountErrorText(it, state.currency) }

        val modeButtonId = if (state.mode == SpeechMode.PAID) R.id.paidButton else R.id.freeButton
        if (modeToggle.checkedButtonId != modeButtonId) modeToggle.check(modeButtonId)
        modeDescription.setText(
            if (state.mode == SpeechMode.PAID) R.string.mode_paid_description else R.string.mode_free_description
        )

        playButton.isEnabled = state.canPlay
        val playback = state.playback
        progress.isVisible = playback is PlaybackStatus.Loading || playback is PlaybackStatus.Speaking
        progress.isIndeterminate = true

        val (text, colorAttr) = when (playback) {
            is PlaybackStatus.Idle -> (playback.message?.let(::resolve) ?: getString(R.string.status_ready)) to
                com.google.android.material.R.attr.colorOnSurfaceVariant
            PlaybackStatus.Loading -> getString(
                if (state.mode == SpeechMode.PAID) R.string.status_generating else R.string.status_preparing
            ) to com.google.android.material.R.attr.colorOnSurfaceVariant
            PlaybackStatus.Speaking -> getString(R.string.status_speaking) to
                com.google.android.material.R.attr.colorPrimary
            is PlaybackStatus.Success -> listOfNotNull(
                getString(R.string.status_success),
                playback.notice?.let(::resolve),
            ).joinToString("\n") to com.google.android.material.R.attr.colorPrimary
            is PlaybackStatus.Error -> resolve(playback.message) to
                com.google.android.material.R.attr.colorError
        }
        statusText.text = text
        statusText.setTextColor(MaterialColors.getColor(statusText, colorAttr))
    }

    /** Hides the "empty" error so a blank field only disables Play instead of showing red text. */
    private fun amountErrorText(error: AmountError, currency: Currency): String? = when (error) {
        AmountError.EMPTY -> null
        AmountError.MALFORMED -> getString(R.string.amount_error_malformed)
        AmountError.NEGATIVE -> getString(R.string.amount_error_negative)
        AmountError.ZERO -> getString(R.string.amount_error_zero)
        AmountError.TOO_MANY_DECIMALS -> getString(
            if (currency == Currency.USD) R.string.amount_error_decimals_usd else R.string.amount_error_decimals_khr
        )
        AmountError.TOO_LARGE -> getString(
            if (currency == Currency.USD) R.string.amount_error_too_large_usd else R.string.amount_error_too_large_khr
        )
    }

    private fun resolve(message: UiMessage): String =
        getString(message.resId, *message.argResIds.map { getString(it) }.toTypedArray())
}
