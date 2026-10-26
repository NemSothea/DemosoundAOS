package com.example.receivemoney.data

import com.example.receivemoney.domain.Gender
import com.example.receivemoney.domain.Language
import com.example.receivemoney.domain.Money
import com.example.receivemoney.speech.SpeechError
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class AzureSpeechRepositoryTest {

    private val voiceListJson = """[
        {"ShortName":"en-US-JennyNeural","Locale":"en-US","Gender":"Female"},
        {"ShortName":"en-US-GuyNeural","Locale":"en-US","Gender":"Male"},
        {"ShortName":"km-KH-SreymomNeural","Locale":"km-KH","Gender":"Female"},
        {"ShortName":"km-KH-PisethNeural","Locale":"km-KH","Gender":"Male"}
    ]"""

    private val config = AzureConfig(
        key = "test-key",
        region = "southeastasia",
        voices = mapOf(
            (Language.ENGLISH to Gender.FEMALE) to "en-US-JennyNeural",
            (Language.ENGLISH to Gender.MALE) to "en-US-GuyNeural",
            (Language.KHMER to Gender.FEMALE) to "km-KH-SreymomNeural",
            (Language.KHMER to Gender.MALE) to "km-KH-PisethNeural",
        ),
    )

    /** Stand-in for Azure in tests only. */
    private class FakeAzure(
        var voices: () -> Response<ResponseBody>,
        var synthesize: () -> Response<ResponseBody>,
    ) : SpeechApi {
        val ssml = mutableListOf<String>()
        var voiceListCalls = 0
        var lastKey: String? = null
        var lastFormat: String? = null

        override suspend fun synthesize(key: String, outputFormat: String, ssml: RequestBody): Response<ResponseBody> {
            lastKey = key
            lastFormat = outputFormat
            this.ssml += Buffer().also { ssml.writeTo(it) }.readUtf8()
            return synthesize()
        }

        override suspend fun voices(key: String): Response<ResponseBody> {
            voiceListCalls++
            return voices()
        }
    }

    private val mp3 = byteArrayOf(0x49, 0x44, 0x33)

    private fun ok(body: String) = Response.success(body.toResponseBody("application/json".toMediaType()))
    private fun audio() = Response.success(mp3.toResponseBody("audio/mpeg".toMediaType()))
    private fun error(status: Int): Response<ResponseBody> =
        Response.error(status, "".toResponseBody("text/plain".toMediaType()))

    private fun fake() = FakeAzure(voices = { ok(voiceListJson) }, synthesize = { audio() })

    @Test
    fun `khmer male uses Piseth voice and server-side text`() = runTest {
        val azure = fake()
        val result = AzureSpeechRepository(azure, config).generate(Language.KHMER, Gender.MALE, Money.khr(10_000))

        assertArrayEquals(mp3, (result as SpeechResult.Success).audio)
        assertEquals("test-key", azure.lastKey)
        assertEquals("audio-24khz-48kbitrate-mono-mp3", azure.lastFormat)
        assertEquals(
            "<speak version='1.0' xml:lang='km-KH'><voice name='km-KH-PisethNeural'>" +
                "ទទួលបាន ១ម៉ឺន រៀល</voice></speak>",
            azure.ssml.single(),
        )
    }

    @Test
    fun `english female uses Jenny`() = runTest {
        val azure = fake()
        AzureSpeechRepository(azure, config).generate(Language.ENGLISH, Gender.FEMALE, Money.usd(575))
        assertTrue(azure.ssml.single().contains("<voice name='en-US-JennyNeural'>Received 5.75 Dollars"))
    }

    @Test
    fun `voice list is fetched once`() = runTest {
        val azure = fake()
        val repo = AzureSpeechRepository(azure, config)
        repo.generate(Language.ENGLISH, Gender.FEMALE, Money.usd(100))
        repo.generate(Language.KHMER, Gender.MALE, Money.usd(100))
        assertEquals(1, azure.voiceListCalls)
    }

    @Test
    fun `missing key or region fails without any request`() = runTest {
        val azure = fake()
        for (bad in listOf(config.copy(key = ""), config.copy(region = " "))) {
            val result = AzureSpeechRepository(azure, bad).generate(Language.ENGLISH, Gender.MALE, Money.usd(100))
            assertEquals(SpeechResult.Failure(SpeechError.AZURE_NOT_CONFIGURED), result)
        }
        assertEquals(0, azure.voiceListCalls)
        assertTrue(azure.ssml.isEmpty())
    }

    @Test
    fun `voice missing from region is rejected before synthesis`() = runTest {
        val azure = fake().apply { voices = { ok("""[{"ShortName":"en-US-GuyNeural","Locale":"en-US","Gender":"Male"}]""") } }
        val result = AzureSpeechRepository(azure, config).generate(Language.KHMER, Gender.MALE, Money.usd(100))
        assertEquals(SpeechResult.Failure(SpeechError.VOICE_UNAVAILABLE), result)
        assertTrue("No billable request", azure.ssml.isEmpty())
    }

    @Test
    fun `configured voice with wrong gender or locale is rejected`() = runTest {
        val swapped = config.copy(voices = config.voices + ((Language.KHMER to Gender.MALE) to "km-KH-SreymomNeural"))
        val wrongLocale = config.copy(voices = config.voices + ((Language.KHMER to Gender.MALE) to "en-US-GuyNeural"))
        for (cfg in listOf(swapped, wrongLocale)) {
            val azure = fake()
            val result = AzureSpeechRepository(azure, cfg).generate(Language.KHMER, Gender.MALE, Money.usd(100))
            assertEquals(SpeechResult.Failure(SpeechError.VOICE_UNAVAILABLE), result)
            assertTrue(azure.ssml.isEmpty())
        }
    }

    @Test
    fun `maps azure http errors`() = runTest {
        val cases = mapOf(401 to SpeechError.AZURE_AUTH_FAILED, 403 to SpeechError.AZURE_AUTH_FAILED,
            429 to SpeechError.RATE_LIMITED, 400 to SpeechError.INVALID_REQUEST, 500 to SpeechError.SERVER)
        for ((status, expected) in cases) {
            val onVoices = fake().apply { voices = { error(status) } }
            assertEquals(
                SpeechResult.Failure(expected),
                AzureSpeechRepository(onVoices, config).generate(Language.ENGLISH, Gender.MALE, Money.usd(100)),
            )
            val onSynthesis = fake().apply { synthesize = { error(status) } }
            assertEquals(
                SpeechResult.Failure(expected),
                AzureSpeechRepository(onSynthesis, config).generate(Language.ENGLISH, Gender.MALE, Money.usd(100)),
            )
        }
    }

    @Test
    fun `voice list failure is retried on next play`() = runTest {
        var fail = true
        val azure = fake().apply { voices = { if (fail) throw IOException("offline") else ok(voiceListJson) } }
        val repo = AzureSpeechRepository(azure, config)
        assertEquals(SpeechResult.Failure(SpeechError.NETWORK), repo.generate(Language.ENGLISH, Gender.MALE, Money.usd(1)))
        fail = false
        assertTrue(repo.generate(Language.ENGLISH, Gender.MALE, Money.usd(1)) is SpeechResult.Success)
    }

    @Test
    fun `network failure during synthesis maps to NETWORK`() = runTest {
        val azure = fake().apply { synthesize = { throw IOException("unreachable") } }
        val result = AzureSpeechRepository(azure, config).generate(Language.ENGLISH, Gender.FEMALE, Money.usd(100))
        assertEquals(SpeechResult.Failure(SpeechError.NETWORK), result)
    }

    @Test
    fun `config toString hides the key`() {
        assertTrue("test-key" !in config.toString())
    }
}
