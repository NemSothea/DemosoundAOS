package com.example.receivemoney.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** Plays one audio file at a time. Call from the main thread. */
class AudioPlayer {

    enum class Result { COMPLETED, ERROR, STOPPED }

    private var player: MediaPlayer? = null
    private var continuation: CancellableContinuation<Result>? = null

    suspend fun play(file: File, onStarted: () -> Unit): Result {
        stop()
        return suspendCancellableCoroutine { cont ->
            val mp = MediaPlayer()
            player = mp
            continuation = cont
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setOnPreparedListener {
                it.start()
                onStarted()
            }
            mp.setOnCompletionListener { finish(Result.COMPLETED) }
            mp.setOnErrorListener { _, _, _ ->
                finish(Result.ERROR)
                true
            }
            cont.invokeOnCancellation {
                if (continuation === cont) continuation = null
                releasePlayer(mp)
            }
            try {
                mp.setDataSource(file.absolutePath)
                mp.prepareAsync()
            } catch (e: Exception) {
                finish(Result.ERROR)
            }
        }
    }

    fun stop() = finish(Result.STOPPED)

    private fun finish(result: Result) {
        val cont = continuation
        continuation = null
        player?.let(::releasePlayer)
        if (cont?.isActive == true) cont.resume(result)
    }

    private fun releasePlayer(mp: MediaPlayer) {
        mp.setOnPreparedListener(null)
        mp.setOnCompletionListener(null)
        mp.setOnErrorListener(null)
        mp.release()
        if (player === mp) player = null
    }
}
