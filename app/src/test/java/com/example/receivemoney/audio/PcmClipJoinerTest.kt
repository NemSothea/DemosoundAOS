package com.example.receivemoney.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmClipJoinerTest {

    private fun clip(rate: Int, vararg samples: Int) = PcmClip(rate, samples.map { it.toShort() }.toShortArray())

    @Test
    fun `wav round trip`() {
        val original = clip(24_000, 1, -2, 3, 32767, -32768)
        val parsed = PcmClipJoiner.readWav(PcmClipJoiner.toWav(original).inputStream())
        assertEquals(24_000, parsed.sampleRate)
        assertArrayEquals(original.samples, parsed.samples)
    }

    @Test
    fun `join inserts silence between clips`() {
        val joined = PcmClipJoiner.join(listOf(clip(1_000, 1, 2), clip(1_000, 3)), gapMs = 2)
        assertEquals(1_000, joined.sampleRate)
        assertArrayEquals(shortArrayOf(1, 2, 0, 0, 3), joined.samples)
    }

    @Test
    fun `join resamples to the first clip's rate`() {
        val joined = PcmClipJoiner.join(listOf(clip(1_000, 5), clip(2_000, 10, 10, 20, 20)), gapMs = 0)
        assertEquals(1_000, joined.sampleRate)
        assertEquals(3, joined.samples.size)   // 1 + 4 * 1000/2000
        assertEquals(5.toShort(), joined.samples[0])
    }

    @Test
    fun `stereo is down-mixed to the left channel`() {
        val wav = PcmClipJoiner.toWav(clip(8_000, 7, 9, 11, 13)).also { bytes -> bytes[22] = 2 } // channels = 2
        val parsed = PcmClipJoiner.readWav(wav.inputStream())
        assertArrayEquals(shortArrayOf(7, 11), parsed.samples)
    }

    @Test
    fun `trimSilence cuts padding and keeps a margin`() {
        // 1 kHz: 1 sample per ms. 5 ms silence, 3 loud samples, 10 ms silence.
        val padded = clip(1_000, 0, 0, 0, 0, 0, 1000, -1000, 1000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val trimmed = PcmClipJoiner.trimSilence(padded, threshold = 300, keepLeadMs = 2, keepTrailMs = 3, fadeMs = 0)
        assertArrayEquals(shortArrayOf(0, 0, 1000, -1000, 1000, 0, 0, 0), trimmed.samples)
    }

    @Test
    fun `trimSilence fades the cut edges`() {
        val loud = clip(1_000, 1000, 1000, 1000, 1000, 1000, 1000)
        val trimmed = PcmClipJoiner.trimSilence(loud, threshold = 300, keepLeadMs = 0, keepTrailMs = 0, fadeMs = 2)
        assertArrayEquals(shortArrayOf(0, 500, 1000, 1000, 500, 0), trimmed.samples)
    }

    @Test
    fun `trimSilence leaves an all-silent clip alone`() {
        val silent = clip(1_000, 0, 10, -10, 0)
        assertArrayEquals(silent.samples, PcmClipJoiner.trimSilence(silent).samples)
    }

    @Test
    fun `duration`() {
        assertEquals(500L, PcmClip(16_000, ShortArray(8_000)).durationMs)
    }
}
