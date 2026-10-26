package com.example.receivemoney.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.receivemoney.audio.PcmClip
import com.example.receivemoney.audio.PcmClipJoiner
import com.example.receivemoney.domain.AnnouncementTokens
import com.example.receivemoney.domain.Gender
import com.example.receivemoney.domain.Language
import com.example.receivemoney.domain.VoiceClipCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** A speech engine that can say whether it has recordings for a request before being asked to speak. */
interface ClipSpeechEngine : SpeechEngine {
    fun available(request: SpeechRequest): Boolean
}

/**
 * Custom recordings: one WAV clip per word ([VoiceClipCatalog]) under
 * `assets/voice/<language tag>/<female|male>/<token>.wav`, joined at play time into one sentence.
 *
 * Clips placed in `<filesDir>/voice/...` take precedence over the bundled assets, which is where
 * the placeholder generator writes. The format is 16-bit PCM WAV, mono; any sample rate.
 */
class ClipEngine(context: Context) : ClipSpeechEngine {

    private val appContext = context.applicationContext
    private val cache = ConcurrentHashMap<String, PcmClip>()
    @Volatile private var track: AudioTrack? = null

    private fun relativePath(language: Language, gender: Gender, token: String) =
        "voice/${language.tag}/${gender.apiValue}/${VoiceClipCatalog.fileName(token)}"

    private fun open(path: String): InputStream? {
        val override = File(appContext.filesDir, path)
        if (override.exists()) return override.inputStream()
        return try { appContext.assets.open(path) } catch (e: FileNotFoundException) { null }
    }

    private fun exists(path: String): Boolean {
        if (File(appContext.filesDir, path).exists()) return true
        return try { appContext.assets.open(path).close(); true } catch (e: FileNotFoundException) { false }
    }

    override fun available(request: SpeechRequest): Boolean =
        AnnouncementTokens.tokens(request.language, request.amount)
            .all { cache.containsKey(relativePath(request.language, request.gender, it)) || exists(relativePath(request.language, request.gender, it)) }

    /** Clips are trimmed once, when loaded, so the sentence doesn't inherit each word's padding. */
    private fun load(path: String): PcmClip? =
        cache[path] ?: open(path)?.use { PcmClipJoiner.trimSilence(PcmClipJoiner.readWav(it)) }?.also { cache[path] = it }

    override suspend fun prepare(language: Language, gender: Gender) = withContext(Dispatchers.IO) {
        VoiceClipCatalog.texts(language).keys.forEach { token -> runCatching { load(relativePath(language, gender, token)) } }
    }

    override suspend fun speak(request: SpeechRequest, onStarted: () -> Unit): SpeechOutcome {
        val tokens = AnnouncementTokens.tokens(request.language, request.amount)
        val sentence = withContext(Dispatchers.IO) {
            val clips = tokens.map { token ->
                load(relativePath(request.language, request.gender, token)) ?: return@withContext null
            }
            PcmClipJoiner.join(clips, GAP_MS)
        } ?: return SpeechOutcome.Failed(SpeechError.CLIP_MISSING)
        Log.d(TAG, "playing ${tokens.joinToString(" ")} (${sentence.durationMs} ms @ ${sentence.sampleRate} Hz)")
        return play(sentence, onStarted)
    }

    private suspend fun play(clip: PcmClip, onStarted: () -> Unit): SpeechOutcome = withContext(Dispatchers.IO) {
        stop()
        val bytes = clip.samples.size * 2
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(clip.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        if (audioTrack.state != AudioTrack.STATE_NO_STATIC_DATA && audioTrack.state != AudioTrack.STATE_INITIALIZED) {
            audioTrack.release(); return@withContext SpeechOutcome.Failed(SpeechError.PLAYBACK)
        }
        track = audioTrack
        try {
            if (audioTrack.write(clip.samples, 0, clip.samples.size) < clip.samples.size) {
                return@withContext SpeechOutcome.Failed(SpeechError.PLAYBACK)
            }
            audioTrack.play()
            onStarted()
            val frames = clip.samples.size
            while (true) {
                ensureActive()
                if (track !== audioTrack) return@withContext SpeechOutcome.Stopped
                if (audioTrack.playState != AudioTrack.PLAYSTATE_PLAYING) return@withContext SpeechOutcome.Stopped
                if (audioTrack.playbackHeadPosition >= frames) break
                delay(POLL_MS)
            }
            delay(TAIL_MS)
            SpeechOutcome.Completed()
        } finally {
            if (track === audioTrack) track = null
            runCatching { audioTrack.stop() }
            audioTrack.release()
        }
    }

    override fun stop() {
        val current = track ?: return
        track = null
        runCatching { current.pause(); current.flush() }
    }

    override fun release() {
        stop()
        cache.clear()
    }

    private companion object {
        const val TAG = "ClipEngine"
        const val GAP_MS = 60
        const val POLL_MS = 40L
        const val TAIL_MS = 80L
    }
}
