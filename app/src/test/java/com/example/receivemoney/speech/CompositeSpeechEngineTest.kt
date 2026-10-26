package com.example.receivemoney.speech

import com.example.receivemoney.domain.Gender
import com.example.receivemoney.domain.Language
import com.example.receivemoney.domain.Money
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeSpeechEngineTest {

    private open class Fake(private val outcome: SpeechOutcome = SpeechOutcome.Completed()) : SpeechEngine {
        val spoken = mutableListOf<SpeechRequest>()
        val prepared = mutableListOf<Pair<Language, Gender>>()
        val selected = mutableListOf<String?>()
        override fun sources() = listOf(VoiceSource("com.google.tts", "Google"))
        override fun selectSource(sourceId: String?) { selected += sourceId }
        override suspend fun prepare(language: Language, gender: Gender) { prepared += language to gender }
        override suspend fun speak(request: SpeechRequest, onStarted: () -> Unit): SpeechOutcome { spoken += request; return outcome }
        override fun stop() = Unit
        override fun release() = Unit
    }

    private class FakeClips(var has: Boolean) : Fake(), ClipSpeechEngine {
        override fun available(request: SpeechRequest) = has
    }

    private val request = SpeechRequest(Language.KHMER, Gender.FEMALE, Money.usd(500))

    @Test
    fun `sources list custom recordings first`() {
        val engine = CompositeSpeechEngine(FakeClips(true), Fake())
        assertEquals(listOf("custom", "com.google.tts"), engine.sources().map { it.id })
    }

    @Test
    fun `automatic prefers clips and falls back with a notice`() = runTest {
        val clips = FakeClips(true); val native = Fake(SpeechOutcome.Completed(SpeechNotice.GENDER_PITCH_SHIFTED))
        val engine = CompositeSpeechEngine(clips, native)
        assertEquals(SpeechOutcome.Completed(), engine.speak(request) {})
        assertEquals(1, clips.spoken.size); assertTrue(native.spoken.isEmpty())

        clips.has = false
        assertEquals(SpeechOutcome.Completed(SpeechNotice.GENDER_PITCH_SHIFTED), engine.speak(request) {})
        assertEquals(1, native.spoken.size)

        val plain = CompositeSpeechEngine(FakeClips(false), Fake())
        assertEquals(SpeechOutcome.Completed(SpeechNotice.CLIPS_FALLBACK), plain.speak(request) {})
    }

    @Test
    fun `pinned custom source never uses the device engine`() = runTest {
        val clips = FakeClips(false); val native = Fake()
        val engine = CompositeSpeechEngine(clips, native)
        engine.selectSource("custom")
        assertEquals(SpeechOutcome.Failed(SpeechError.CLIP_MISSING), engine.speak(request) {})
        assertTrue(native.spoken.isEmpty())
        assertEquals(listOf<String?>(null), native.selected)
    }

    @Test
    fun `pinned device source bypasses clips`() = runTest {
        val clips = FakeClips(true); val native = Fake()
        val engine = CompositeSpeechEngine(clips, native)
        engine.selectSource("com.google.tts")
        engine.speak(request) {}
        assertTrue(clips.spoken.isEmpty()); assertEquals(1, native.spoken.size)
        assertEquals(listOf<String?>("com.google.tts"), native.selected)
        engine.prepare(Language.KOREAN, Gender.MALE)
        assertTrue(clips.prepared.isEmpty()); assertEquals(Language.KOREAN to Gender.MALE, native.prepared.single())
    }
}
