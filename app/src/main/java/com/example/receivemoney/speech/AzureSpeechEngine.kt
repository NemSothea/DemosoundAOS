package com.example.receivemoney.speech

import com.example.receivemoney.audio.AudioPlayer
import com.example.receivemoney.data.AzureSpeechRepository
import com.example.receivemoney.data.SpeechResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Paid mode: asks the backend to synthesise the announcement with an Azure neural voice,
 * then plays the returned MP3. Audio is generated only when [speak] is called.
 */
class AzureSpeechEngine(
    private val repository: AzureSpeechRepository,
    private val player: AudioPlayer,
    private val cacheDir: File,
) : SpeechEngine {

    override suspend fun speak(request: SpeechRequest, onStarted: () -> Unit): SpeechOutcome {
        val audio = when (val result = repository.generate(request.language, request.gender, request.amount)) {
            is SpeechResult.Failure -> return SpeechOutcome.Failed(result.error)
            is SpeechResult.Success -> result.audio
        }
        val file = try {
            withContext(Dispatchers.IO) {
                File(cacheDir, AUDIO_FILE_NAME).apply { writeBytes(audio) }
            }
        } catch (e: IOException) {
            return SpeechOutcome.Failed(SpeechError.PLAYBACK)
        }
        return when (player.play(file, onStarted)) {
            AudioPlayer.Result.COMPLETED -> SpeechOutcome.Completed()
            AudioPlayer.Result.ERROR -> SpeechOutcome.Failed(SpeechError.PLAYBACK)
            AudioPlayer.Result.STOPPED -> SpeechOutcome.Stopped
        }
    }

    override fun stop() = player.stop()

    override fun release() {
        player.stop()
        File(cacheDir, AUDIO_FILE_NAME).delete()
    }

    private companion object {
        const val AUDIO_FILE_NAME = "azure_preview.mp3"
    }
}
