package com.example.receivemoney.data

import com.example.receivemoney.domain.Gender
import com.example.receivemoney.domain.Language
import com.example.receivemoney.domain.Money
import com.example.receivemoney.domain.MoneyAnnouncement
import com.example.receivemoney.speech.SpeechError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONException
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface SpeechResult {
    class Success(val audio: ByteArray) : SpeechResult
    data class Failure(val error: SpeechError) : SpeechResult
}

/** Azure credentials and the voice to use for each language/gender. */
data class AzureConfig(
    val key: String,
    val region: String,
    val voices: Map<Pair<Language, Gender>, String>,
) {
    val isConfigured: Boolean get() = key.isNotBlank() && region.isNotBlank()

    // Never print the key.
    override fun toString() = "AzureConfig(region=$region, configured=$isConfigured, voices=$voices)"
}

/**
 * Paid mode: calls Azure AI Speech directly from the app (demo setup, no backend).
 * Before synthesising, the configured voice is checked against Azure's live voice list for the
 * region (name, locale and gender), so an unavailable voice fails without a billable request.
 */
class AzureSpeechRepository(
    private val api: SpeechApi,
    private val config: AzureConfig,
) {
    private data class AzureVoice(val locale: String, val gender: String)

    private val voiceListLock = Mutex()
    private var voiceList: Map<String, AzureVoice>? = null

    suspend fun generate(language: Language, gender: Gender, amount: Money): SpeechResult {
        if (!config.isConfigured) return SpeechResult.Failure(SpeechError.AZURE_NOT_CONFIGURED)
        val voiceName = config.voices[language to gender]
            ?: return SpeechResult.Failure(SpeechError.VOICE_UNAVAILABLE)

        val voices = when (val result = availableVoices()) {
            is VoiceListResult.Loaded -> result.voices
            is VoiceListResult.Failed -> return SpeechResult.Failure(result.error)
        }
        val voice = voices[voiceName]
        if (voice == null ||
            !voice.locale.equals(language.tag, ignoreCase = true) ||
            !voice.gender.equals(gender.apiValue, ignoreCase = true)
        ) {
            return SpeechResult.Failure(SpeechError.VOICE_UNAVAILABLE)
        }

        val text = MoneyAnnouncement.text(language, amount)
        val ssml = "<speak version='1.0' xml:lang='${language.tag}'>" +
            "<voice name='${xmlEscape(voiceName)}'>${xmlEscape(text)}</voice></speak>"
        val response = call { api.synthesize(config.key, OUTPUT_FORMAT, ssml.toRequestBody(SSML)) }
            ?: return SpeechResult.Failure(SpeechError.NETWORK)
        if (!response.isSuccessful) return SpeechResult.Failure(mapError(response.code()))

        val bytes = readBytes(response.body()) ?: return SpeechResult.Failure(SpeechError.NETWORK)
        return if (bytes.isNotEmpty()) SpeechResult.Success(bytes) else SpeechResult.Failure(SpeechError.SERVER)
    }

    private sealed interface VoiceListResult {
        class Loaded(val voices: Map<String, AzureVoice>) : VoiceListResult
        class Failed(val error: SpeechError) : VoiceListResult
    }

    /** Fetched once per app run; failures are not cached so a later Play can retry. */
    private suspend fun availableVoices(): VoiceListResult = voiceListLock.withLock {
        voiceList?.let { return VoiceListResult.Loaded(it) }
        val response = call { api.voices(config.key) } ?: return VoiceListResult.Failed(SpeechError.NETWORK)
        if (!response.isSuccessful) return VoiceListResult.Failed(mapError(response.code()))
        val json = readBytes(response.body())?.decodeToString() ?: return VoiceListResult.Failed(SpeechError.NETWORK)
        val parsed = try {
            parseVoiceList(json)
        } catch (e: JSONException) {
            return VoiceListResult.Failed(SpeechError.SERVER)
        }
        voiceList = parsed
        VoiceListResult.Loaded(parsed)
    }

    private fun parseVoiceList(json: String): Map<String, AzureVoice> {
        val array = JSONArray(json)
        return (0 until array.length()).associate { i ->
            val item = array.getJSONObject(i)
            item.getString("ShortName") to AzureVoice(item.optString("Locale"), item.optString("Gender"))
        }
    }

    /** Runs an HTTP call; returns null on network failure. Cancellation is propagated. */
    private suspend fun <T> call(block: suspend () -> Response<T>): Response<T>? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        null
    }

    private fun readBytes(body: ResponseBody?): ByteArray? = try {
        body?.use { it.bytes() }
    } catch (e: IOException) {
        null
    }

    companion object {
        /** MP3, 24 kHz mono, 48 kbit/s; MediaPlayer plays it on every supported Android version. */
        const val OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3"
        private val SSML = "application/ssml+xml; charset=utf-8".toMediaType()

        fun mapError(httpStatus: Int): SpeechError = when (httpStatus) {
            401, 403 -> SpeechError.AZURE_AUTH_FAILED
            429 -> SpeechError.RATE_LIMITED
            400 -> SpeechError.INVALID_REQUEST
            else -> SpeechError.SERVER
        }

        private fun xmlEscape(text: String): String = text
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")

        fun create(config: AzureConfig): AzureSpeechRepository {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build()
            // A placeholder host keeps Retrofit happy when no region is configured; generate()
            // returns AZURE_NOT_CONFIGURED before any request is made in that case.
            val host = config.region.ifBlank { "unconfigured" }
            val api = Retrofit.Builder()
                .baseUrl("https://$host.tts.speech.microsoft.com/")
                .client(client)
                .build()
                .create(SpeechApi::class.java)
            return AzureSpeechRepository(api, config)
        }
    }
}
