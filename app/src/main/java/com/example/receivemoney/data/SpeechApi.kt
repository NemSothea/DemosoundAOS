package com.example.receivemoney.data

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Azure AI Speech text-to-speech REST API, base URL `https://<region>.tts.speech.microsoft.com/`.
 * Docs: https://learn.microsoft.com/azure/ai-services/speech-service/rest-text-to-speech
 */
interface SpeechApi {
    /** SSML in, audio out (format chosen by [outputFormat]). */
    @POST("cognitiveservices/v1")
    suspend fun synthesize(
        @Header("Ocp-Apim-Subscription-Key") key: String,
        @Header("X-Microsoft-OutputFormat") outputFormat: String,
        @Body ssml: RequestBody,
    ): Response<ResponseBody>

    /** JSON array of the voices available in the region. */
    @GET("cognitiveservices/voices/list")
    suspend fun voices(@Header("Ocp-Apim-Subscription-Key") key: String): Response<ResponseBody>
}
