package com.example.receivemoney.ui

import androidx.lifecycle.SavedStateHandle
import com.example.receivemoney.R
import com.example.receivemoney.domain.Currency
import com.example.receivemoney.domain.Gender
import com.example.receivemoney.domain.Language
import com.example.receivemoney.domain.Money
import com.example.receivemoney.speech.SpeechEngine
import com.example.receivemoney.speech.SpeechError
import com.example.receivemoney.speech.SpeechNotice
import com.example.receivemoney.speech.SpeechOutcome
import com.example.receivemoney.speech.SpeechRequest
import com.example.receivemoney.speech.VoiceSource
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
import org.junit.Assert.assertNull
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
        val selectedSources = mutableListOf<String?>()
        val sources = listOf(VoiceSource("com.samsung.SMT", "Samsung TTS"), VoiceSource("com.google.tts", "Speech Services by Google"))

        override fun sources() = sources

        override fun selectSource(sourceId: String?) {
            selectedSources += sourceId
        }

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

    private lateinit var engine: FakeEngine
    private lateinit var vm: ReceiveMoneyViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        engine = FakeEngine()
        vm = ReceiveMoneyViewModel(engine)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val state get() = vm.uiState.value

    @Test
    fun `defaults are khmer female 5 dollars automatic source`() {
        assertEquals(Language.KHMER, state.language)
        assertEquals(Gender.FEMALE, state.gender)
        assertEquals(Currency.USD, state.currency)
        assertEquals(Money.usd(500), state.amount)
        assertNull(state.sourceId)
        assertEquals(engine.sources, state.sources)
        assertTrue("Play is available without any input", state.canPlay)
    }

    @Test
    fun `play uses selected language gender and preset amount`() = runTest {
        vm.onLanguageSelected(Language.JAPANESE)
        vm.onGenderSelected(Gender.MALE)
        vm.onPlayClicked()
        assertEquals(listOf(SpeechRequest(Language.JAPANESE, Gender.MALE, Money.usd(500))), engine.requests)
        assertEquals(PlaybackStatus.Speaking, state.playback)

        engine.nextOutcome.complete(SpeechOutcome.Completed())
        assertEquals(PlaybackStatus.Success(null), state.playback)
    }

    @Test
    fun `currency switches the preset without speaking`() {
        vm.onCurrencySelected(Currency.KHR)
        assertEquals(Money.khr(20_000), state.amount)
        assertTrue(engine.requests.isEmpty())
        vm.onPlayClicked()
        assertEquals(Money.khr(20_000), engine.requests.single().amount)
    }

    @Test
    fun `selecting a source pins it and stops playback`() {
        vm.onPlayClicked()
        vm.onSourceSelected("com.google.tts")
        assertEquals("com.google.tts", state.sourceId)
        assertEquals("com.google.tts", engine.selectedSources.last())
        assertEquals(1, engine.cancelledCount)
        assertTrue(engine.requests.size == 1)

        vm.onSourceSelected(null)
        assertNull(state.sourceId)
        assertNull(engine.selectedSources.last())
    }

    @Test
    fun `rapid repeated play cancels the previous preview`() {
        vm.onPlayClicked()
        engine.nextOutcome = CompletableDeferred()
        vm.onPlayClicked()
        vm.onPlayClicked()
        assertEquals(3, engine.requests.size)
        assertEquals(2, engine.cancelledCount)
    }

    @Test
    fun `missing voice shows a settings hint and keeps play available`() {
        vm.onLanguageSelected(Language.KOREAN)
        vm.onPlayClicked()
        engine.nextOutcome.complete(SpeechOutcome.Failed(SpeechError.LANGUAGE_UNSUPPORTED))
        assertEquals(
            PlaybackStatus.Error(UiMessage(R.string.error_language_unsupported, listOf(R.string.language_korean))),
            state.playback,
        )
        assertTrue("Play stays available after an error", state.canPlay)

        vm.onPlayClicked()
        engine.nextOutcome = CompletableDeferred()
        vm.onPlayClicked()
        engine.nextOutcome.complete(SpeechOutcome.Failed(SpeechError.LANGUAGE_DATA_MISSING))
        assertEquals(
            PlaybackStatus.Error(UiMessage(R.string.error_language_data_missing, listOf(R.string.language_korean))),
            state.playback,
        )
    }

    @Test
    fun `pitch shifted fallback is reported`() {
        vm.onGenderSelected(Gender.MALE)
        vm.onPlayClicked()
        engine.nextOutcome.complete(SpeechOutcome.Completed(SpeechNotice.GENDER_PITCH_SHIFTED))
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
        vm.onLanguageSelected(Language.ENGLISH)
        vm.onGenderSelected(Gender.MALE)
        vm.onPlayClicked()
        engine.nextOutcome.complete(SpeechOutcome.Completed(SpeechNotice.GENDER_UNAVAILABLE))
        assertEquals(
            PlaybackStatus.Success(
                UiMessage(R.string.notice_gender_unavailable, listOf(R.string.gender_male, R.string.language_english))
            ),
            state.playback,
        )
    }

    @Test
    fun `play is disabled while loading`() {
        val slow = object : SpeechEngine {
            val gate = CompletableDeferred<SpeechOutcome>()
            var calls = 0
            override suspend fun speak(request: SpeechRequest, onStarted: () -> Unit): SpeechOutcome {
                calls++
                return gate.await() // never calls onStarted: engine still starting
            }
            override fun stop() = Unit
            override fun release() = Unit
        }
        val vm = ReceiveMoneyViewModel(slow)
        vm.onPlayClicked()
        assertEquals(PlaybackStatus.Loading, vm.uiState.value.playback)
        assertFalse(vm.uiState.value.canPlay)
        vm.onPlayClicked()
        assertEquals("No duplicate request", 1, slow.calls)
    }

    @Test
    fun `selections survive recreation from saved state`() {
        val handle = SavedStateHandle()
        val first = ReceiveMoneyViewModel(engine, handle)
        first.onSourceSelected("com.google.tts")
        first.onLanguageSelected(Language.CHINESE)
        first.onGenderSelected(Gender.MALE)
        first.onCurrencySelected(Currency.KHR)

        val restored = ReceiveMoneyViewModel(engine, handle)
        restored.onPlayClicked()

        assertEquals("com.google.tts", restored.uiState.value.sourceId)
        assertEquals("com.google.tts", engine.selectedSources.last())
        assertEquals(listOf(SpeechRequest(Language.CHINESE, Gender.MALE, Money.khr(20_000))), engine.requests)
    }

    @Test
    fun `a saved source that is no longer installed falls back to automatic`() {
        val handle = SavedStateHandle(mapOf("source" to "com.gone.tts"))
        val vm = ReceiveMoneyViewModel(engine, handle)
        assertNull(vm.uiState.value.sourceId)
        assertNull(engine.selectedSources.last())
    }

    @Test
    fun `changing voice prepares it without speaking`() {
        vm.onGenderSelected(Gender.MALE)
        vm.onLanguageSelected(Language.KOREAN)
        assertEquals(Language.KOREAN to Gender.MALE, engine.prepared.last())
        assertTrue(engine.requests.isEmpty())
    }

    @Test
    fun `stop playback reports stopped and releases on clear`() {
        vm.onPlayClicked()
        vm.stopPlayback()
        assertEquals(PlaybackStatus.Idle(UiMessage(R.string.status_stopped)), state.playback)
        assertTrue(engine.stopCount > 0)
    }
}
