package com.example.receivemoney

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.receivemoney.databinding.ActivityMainBinding
import com.example.receivemoney.domain.Currency
import com.example.receivemoney.domain.Gender
import com.example.receivemoney.domain.Language
import com.example.receivemoney.domain.Money
import com.example.receivemoney.ui.PlaybackStatus
import com.example.receivemoney.ui.ReceiveMoneyUiState
import com.example.receivemoney.ui.ReceiveMoneyViewModel
import com.example.receivemoney.ui.UiMessage
import com.example.receivemoney.ui.labelRes
import com.example.receivemoney.speech.CompositeSpeechEngine
import com.example.receivemoney.speech.PlaceholderClipGenerator
import android.widget.Toast
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ReceiveMoneyViewModel by viewModels { ReceiveMoneyViewModel.Factory }

    private val languages = Language.entries

    /** Dropdown rows: "Automatic" first, then one row per installed engine. */
    private val sources get() = viewModel.uiState.value.sources

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
        binding.genderToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) viewModel.onGenderSelected(if (checkedId == R.id.maleButton) Gender.MALE else Gender.FEMALE)
        }
        binding.currencyToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) viewModel.onCurrencySelected(if (checkedId == R.id.khrButton) Currency.KHR else Currency.USD)
        }
        binding.playButton.setOnClickListener { viewModel.onPlayClicked() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }

        // Developer hook: `adb shell am start -n com.example.receivemoney/.MainActivity --ez generate_placeholder_clips true`
        // synthesizes every catalog word with the phone's engine into files/voice/, to prove the
        // clip player before real recordings exist. Not reachable from the UI.
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_GENERATE_CLIPS, false)) {
            lifecycleScope.launch {
                val result = PlaceholderClipGenerator(this@MainActivity).generateAll { progress ->
                    binding.statusText.text = getString(R.string.status_generating_clips, progress)
                }
                Toast.makeText(this@MainActivity, result, Toast.LENGTH_LONG).show()
                binding.statusText.text = result
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Keep speaking across rotation, but not when the user leaves the screen.
        if (!isChangingConfigurations) viewModel.stopPlayback()
    }

    private fun setUpDropdowns() {
        binding.sourceDropdown.setAdapter(
            ArrayAdapter(this, R.layout.item_dropdown, listOf(sourceLabel(null)) + sources.map { sourceLabel(it.id) })
        )
        binding.sourceDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.onSourceSelected(if (position == 0) null else sources[position - 1].id)
        }
        binding.languageDropdown.setAdapter(
            ArrayAdapter(this, R.layout.item_dropdown, languages.map { getString(it.labelRes()) })
        )
        binding.languageDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.onLanguageSelected(languages[position])
        }
    }

    private fun sourceLabel(sourceId: String?): String {
        val source = sources.firstOrNull { it.id == sourceId } ?: return getString(R.string.source_auto)
        if (source.id == CompositeSpeechEngine.CUSTOM_SOURCE) return getString(R.string.source_custom)
        return getString(R.string.source_native, source.label)
    }

    private fun render(state: ReceiveMoneyUiState) = with(binding) {
        // setText(..., false) keeps the dropdown from filtering its own items.
        sourceDropdown.setText(sourceLabel(state.sourceId), false)
        languageDropdown.setText(getString(state.language.labelRes()), false)

        val genderId = if (state.gender == Gender.MALE) R.id.maleButton else R.id.femaleButton
        if (genderToggle.checkedButtonId != genderId) genderToggle.check(genderId)
        val currencyId = if (state.currency == Currency.KHR) R.id.khrButton else R.id.usdButton
        if (currencyToggle.checkedButtonId != currencyId) currencyToggle.check(currencyId)
        amountText.text = formatAmount(state.amount)

        playButton.isEnabled = state.canPlay
        val playback = state.playback
        progress.isVisible = playback is PlaybackStatus.Loading || playback is PlaybackStatus.Speaking
        progress.isIndeterminate = true

        val (text, colorAttr) = when (playback) {
            is PlaybackStatus.Idle -> (playback.message?.let(::resolve) ?: getString(R.string.status_ready)) to
                com.google.android.material.R.attr.colorOnSurfaceVariant
            PlaybackStatus.Loading -> getString(R.string.status_preparing) to
                com.google.android.material.R.attr.colorOnSurfaceVariant
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

    /** "$5" or "៛20,000". */
    private fun formatAmount(amount: Money): String {
        val prefix = getString(if (amount.currency == Currency.USD) R.string.currency_prefix_usd else R.string.currency_prefix_khr)
        val major = String.format(Locale.US, "%,d", amount.major)
        val minor = if (amount.minor > 0) "." + amount.minor.toString().padStart(amount.currency.decimals, '0') else ""
        return prefix + major + minor
    }

    private fun resolve(message: UiMessage): String =
        getString(message.resId, *message.argResIds.map { getString(it) }.toTypedArray())

    private companion object {
        const val EXTRA_GENERATE_CLIPS = "generate_placeholder_clips"
    }
}
