package com.example.receivemoney.audio

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** Decoded 16-bit mono PCM. */
class PcmClip(val sampleRate: Int, val samples: ShortArray) {
    val durationMs: Long get() = samples.size * 1000L / sampleRate
}

/**
 * Reads 16-bit PCM WAV clips and joins them into one continuous clip, so a sequence of word
 * recordings plays as a single sentence. Pure Kotlin: no Android APIs, unit-testable.
 */
object PcmClipJoiner {

    /** Parses a RIFF/WAVE stream. Supports PCM 16-bit; stereo is down-mixed to mono (left channel). */
    fun readWav(input: InputStream): PcmClip {
        val bytes = input.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") { "Not a WAV file" }
        var pos = 12
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var data: ShortArray? = null
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = buf.getInt(pos + 4)
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    val format = buf.getShort(body).toInt()
                    require(format == 1 || format == 0xFFFE) { "Only PCM WAV is supported (format $format)" }
                    channels = buf.getShort(body + 2).toInt()
                    sampleRate = buf.getInt(body + 4)
                    bitsPerSample = buf.getShort(body + 14).toInt()
                    require(bitsPerSample == 16) { "Only 16-bit WAV is supported ($bitsPerSample-bit)" }
                }
                "data" -> {
                    require(channels > 0) { "data chunk before fmt chunk" }
                    val available = minOf(size, bytes.size - body)
                    val frames = available / (2 * channels)
                    data = ShortArray(frames) { i -> buf.getShort(body + i * 2 * channels) }
                }
            }
            pos = body + size + (size and 1)
        }
        return PcmClip(sampleRate, checkNotNull(data) { "WAV has no data chunk" })
    }

    /**
     * Concatenates [clips] with [gapMs] of silence between them. All clips are resampled to the
     * first clip's rate (linear interpolation) so one AudioTrack can play the result.
     */
    fun join(clips: List<PcmClip>, gapMs: Int): PcmClip {
        require(clips.isNotEmpty()) { "No clips" }
        val rate = clips.first().sampleRate
        val gap = ShortArray(rate * gapMs / 1000)
        val parts = clips.map { resample(it, rate).samples }
        val total = parts.sumOf { it.size } + gap.size * (parts.size - 1)
        val out = ShortArray(total)
        var offset = 0
        parts.forEachIndexed { i, part ->
            part.copyInto(out, offset); offset += part.size
            if (i < parts.lastIndex) offset += gap.size
        }
        return PcmClip(rate, out)
    }

    /**
     * Cuts the silence a recording (or a TTS engine) leaves around a word, keeping [keepLeadMs]
     * before the first and [keepTrailMs] after the last sample louder than [threshold], and fades
     * the new edges over [fadeMs] so the cut doesn't click. Without this, each word in a joined
     * sentence is followed by its own 200-400 ms of dead air plus the gap, which sounds choppy.
     * A clip that is silent throughout is returned unchanged.
     */
    fun trimSilence(
        clip: PcmClip,
        threshold: Int = SILENCE_THRESHOLD,
        keepLeadMs: Int = KEEP_LEAD_MS,
        keepTrailMs: Int = KEEP_TRAIL_MS,
        fadeMs: Int = FADE_MS,
    ): PcmClip {
        val src = clip.samples
        val perMs = clip.sampleRate / 1000
        val first = src.indexOfFirst { abs(it.toInt()) > threshold }
        if (first < 0) return clip
        val last = src.indexOfLast { abs(it.toInt()) > threshold }
        val start = (first - keepLeadMs * perMs).coerceAtLeast(0)
        val end = (last + 1 + keepTrailMs * perMs).coerceAtMost(src.size)
        val out = src.copyOfRange(start, end)
        val fade = (fadeMs * perMs).coerceAtMost(out.size / 2)
        for (i in 0 until fade) {
            out[i] = (out[i] * i / fade).toShort()
            out[out.lastIndex - i] = (out[out.lastIndex - i] * i / fade).toShort()
        }
        return PcmClip(clip.sampleRate, out)
    }

    fun resample(clip: PcmClip, targetRate: Int): PcmClip {
        if (clip.sampleRate == targetRate) return clip
        val src = clip.samples
        val outSize = (src.size.toLong() * targetRate / clip.sampleRate).toInt()
        val out = ShortArray(outSize) { i ->
            val pos = i.toDouble() * clip.sampleRate / targetRate
            val i0 = pos.toInt().coerceAtMost(src.lastIndex)
            val i1 = (i0 + 1).coerceAtMost(src.lastIndex)
            val frac = pos - i0
            (src[i0] * (1 - frac) + src[i1] * frac).toInt().toShort()
        }
        return PcmClip(targetRate, out)
    }

    /** Writes a 16-bit mono PCM WAV; used by tests and the placeholder generator. */
    fun toWav(clip: PcmClip): ByteArray {
        val dataSize = clip.samples.size * 2
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + dataSize).put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16).putShort(1).putShort(1)
            .putInt(clip.sampleRate).putInt(clip.sampleRate * 2).putShort(2).putShort(16)
        buf.put("data".toByteArray(Charsets.US_ASCII)).putInt(dataSize)
        clip.samples.forEach { buf.putShort(it) }
        return buf.array()
    }

    /** Below ~1% of full scale is treated as silence; TTS output and quiet room noise sit under it. */
    const val SILENCE_THRESHOLD = 300
    const val KEEP_LEAD_MS = 20
    const val KEEP_TRAIL_MS = 60
    const val FADE_MS = 5
}
