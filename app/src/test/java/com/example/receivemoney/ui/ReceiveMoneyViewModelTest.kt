package com.example.receivemoney.ui

import androidx.lifecycle.SavedStateHandle
import com.example.receivemoney.R
import com.example.receivemoney.domain.AmountError
import com.example.receivemoney.domain.Gender
import com.example.receivemoney.domain.Language
import com.example.receivemoney.domain.SpeechMode
import com.example.receivemoney.domain.Currency
import com.example.receivemoney.domain.Money
import com.example.receivemoney.speech.SpeechEngine
import com.example.receivemoney.speech.SpeechError
import com.example.receivemoney.speech.SpeechNotice
import com.example.receivemoney.speech.SpeechOutcome
import com.example.receivemoney.speech.SpeechRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveMoneyViewModelTest {

    /** Records requests; each speak() waits for [nextOutcome] to complete. */
    private class FakeEngine : SpeechEngine {
        val requests = mutableListOf<SpeechRequest>()
        var nextOutcome = CompletableDeferred<SpeechOutcome>()
        var stopCount = 0
        var released = false
        var cancelledCount = 0
        val prepared = mutableListOf<Pair<Language, Gender>>()

        override suspend fun prepare(language: Language, gender: Gender) {
            prepared += language to gender
        }

        override suspend fun speak(request: SpeechRequest, onStarted: () -> Unit): SpeechOutcome {
            requests += request
            onStarted()
            try {
                return nextOutcome.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelledCount++
                throw e
            }
        }

        override fun stop() {
            stopCount++
        }

        override fun release() {
            released = true
        }
    }

    private lateinit var free: FakeEngine
    private lateinit var paid: FakeEngine
    private lateinit var vm: ReceiveMoneyViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        free = FakeEngine()
        paid = FakeEngine()
        vm = ReceiveMoneyViewModel(mapOf(SpeechMode.FREE to free, SpeechMode.PAID to paid))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val state get() = vm.uiState.value

    @Test
    fun `play is disabled until amount is valid`() {
        assertFalse(state.canPlay)
        vm.onAmountChanged("abc")
        assertEquals(AmountError.MALFORMED, state.amountError)
        assertFalse(state.canPlay)
        vm.onAmountChanged("-1")
        assertFalse(state.canPlay)
        vm.onAmountChanged("5.75")
        assertEquals(Money.usd(575), state.amount)
        assertTrue(state.canPlay)
    }

    @Test
    fun `invalid amount does not reach any engine`() {
        vm.onAmountChanged("5.755")
        vm.onPlayClicked()
        assertTrue(free.requests.isEmpty())
        assertTrue(paid.requests.isEmpty())
    }

    @Test
    fun `play uses selected language gender and amount`() = runTest {
        vm.onLanguageSelected(Language.KHMER)
        vm.onGenderSelected(Gender.MALE)
        vm.onAmountChanged("5.75")
        vm.onPlayClicked()
        assertEquals(listOf(SpeechRequest(Language.KHMER, Gender.MALE, Money.usd(575))), free.requests)
        assertEquals(PlaybackStatus.Speaking, state.playback)

        free.nextOutcome.complete(SpeechOutcome.Completed())
        assertEquals(PlaybackStatus.Success(null), state.playback)
    }

    @Test
    fun `switching mode never triggers speech`() {
        vm.onAmountChanged("1")
        vm.onModeSelected(SpeechMode.PAID)
        vm.onModeSelected(SpeechMode.FREE)
        vm.onModeSelected(SpeechMode.PAID)
        assertTrue(free.requests.isEmpty())
        assertTrue(paid.requests.isEmpty())
        assertEquals(SpeechMode.PAID, state.mode)
        assertEquals(PlaybackStatus.Idle(UiMessage(R.string.mode_paid_selected)), state.playback)
    }

    @Test
    fun `play uses the currently selected mode`() {
        vm.onAmountChanged("2")
        vm.onModeSelected(SpeechMode.PAID)
        vm.onPlayClicked()
        assertEquals(1, paid.requests.size)
        assertTrue(free.requests.isEmpty())
    }

    @Test
    fun `switching mode while playing stops playback`() {
        vm.onAmountChanged("2")
        vm.onPlayClicked()
        vm.onModeSelected(SpeechMode.PAID)
        assertEquals(1, free.cancelledCount)
        assertTrue(free.stopCount > 0)
        assertTrue(paid.requests.isEmpty())
    }

    @Test
    fun `rapid repeated play cancels the previous preview`() {
        vm.onAmountChanged("2")
        vm.onPlayClicked()
        free.nextOutcome = CompletableDeferred()
        vm.onPlayClicked()
        vm.onPlayClicked()
        assertEquals(3, free.requests.size)
        assertEquals(2, free.cancelledCount)
    }

    @Test
    fun `unsupported language shows error`() {
        vm.onLanguageSelected(Language.KHMER)
        vm.onAmountChanged("3")
        vm.onPlayClicked()
        free.nextOutcome.complete(SpeechOutcome.Failed(SpeechError.LANGUAGE_UNSUPPORTED))
        assertEquals(
            PlaybackStatus.Error(UiMessage(R.string.error_language_unsupported, listOf(R.string.language_khmer))),
            state.playback,
        )
        assertTrue("Play stays available after an error", state.canPlay)
    }

    @Test
    fun `pitch shifted fallback is reported`() {
        vm.onLanguageSelected(Language.KHMER)
        vm.onGenderSelected(Gender.MALE)
        vm.onAmountChanged("3")
        vm.onPlayClicked()
        free.nextOutcome.complete(SpeechOutcome.Completed(SpeechNotice.GENDER_PITCH_SHIFTED))
        assertEquals(
            PlaybackStatus.Success(
                UiMessage(
                    R.string.notice_gender_pitch_shifted,
                    listOf(R.string.gender_male, R.string.language_khmer, R.string.gender_female),
                )
            ),
            state.playback,
        )
    }

    @Test
    fun `gender fallback is reported`() {
        vm.onGenderSelected(Gender.MALE)
        vm.onAmountChanged("3")
        vm.onPlayClicked()
        free.nextOutcome.complete(SpeechOutcome.Completed(SpeechNotice.GENDER_UNAVAILABLE))
        assertEquals(
            PlaybackStatus.Success(
                UiMessage(R.string.notice_gender_unavailable, listOf(R.string.gender_male, R.string.language_english))
            ),
            state.playback,
        )
    }

    @Test
    fun `missing azure configuration shows error`() {
        vm.onModeSelected(SpeechMode.PAID)
        vm.onAmountChanged("3")
        vm.onPlayClicked()
        paid.nextOutcome.complete(SpeechOutcome.Failed(SpeechError.AZURE_NOT_CONFIGURED))
        assertEquals(PlaybackStatus.Error(UiMessage(R.string.error_azure_not_configured)), state.playback)
    }

    @Test
    fun `play is disabled while loading`() {
        val slow = object : SpeechEngine {
            val gate = CompletableDeferred<SpeechOutcome>()
            var calls = 0
            override suspend fun speak(request: SpeechRequest, onStarted: () -> Unit): SpeechOutcome {
                calls++
                return gate.await() // never calls onStarted: still generating
            }
            override fun stop() = Unit
            override fun release() = Unit
        }
        val vm = ReceiveMoneyViewModel(mapOf(SpeechMode.FREE to free, SpeechMode.PAID to slow))
        vm.onModeSelected(SpeechMode.PAID)
        vm.onAmountChanged("3")
        vm.onPlayClicked()
        assertEquals(PlaybackStatus.Loading, vm.uiState.value.playback)
        assertFalse(vm.uiState.value.canPlay)
        vm.onPlayClicked()
        assertEquals("No duplicate billable request", 1, slow.calls)
    }

    @Test
    fun `selections survive recreation from saved state`() {
        val handle = SavedStateHandle()
        val first = ReceiveMoneyViewModel(mapOf(SpeechMode.FREE to free, SpeechMode.PAID to paid), handle)
        first.onLanguageSelected(Language.KHMER)
        first.onGenderSelected(Gender.MALE)
        first.onModeSelected(SpeechMode.PAID)
        first.onAmountChanged("5.75")

        val restored = ReceiveMoneyViewModel(mapOf(SpeechMode.FREE to free, SpeechMode.PAID to paid), handle)
        restored.onPlayClicked()

        assertEquals(SpeechMode.PAID, restored.uiState.value.mode)
        assertEquals(listOf(SpeechRequest(Language.KHMER, Gender.MALE, Money.usd(575))), paid.requests)
    }

    @Test
    fun `currency change revalidates amount and is sent to the engine`() {
        vm.onAmountChanged("5.75")
        assertTrue(state.canPlay)

        vm.onCurrencySelected(Currency.KHR)
        assertEquals(AmountError.TOO_MANY_DECIMALS, state.amountError)
        assertFalse(state.canPlay)

        vm.onAmountChanged("10000")
        vm.onPlayClicked()
        assertEquals(Money.khr(10_000), free.requests.single().amount)
    }

    @Test
    fun `changing voice prepares it without speaking`() {
        vm.onAmountChanged("5")
        vm.onGenderSelected(Gender.MALE)
        vm.onLanguageSelected(Language.KHMER)
        assertEquals(Language.KHMER to Gender.MALE, free.prepared.last())
        assertTrue(free.requests.isEmpty())
    }

    @Test
    fun `only the selected mode's engine is prepared`() {
        vm.onModeSelected(SpeechMode.PAID)
        vm.onGenderSelected(Gender.MALE)
        assertEquals(Language.ENGLISH to Gender.MALE, paid.prepared.last())
        assertTrue(paid.requests.isEmpty())
    }
}
