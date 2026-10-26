package com.example.receivemoney.speech

import android.content.Context
import android.util.Log
import com.example.receivemoney.domain.Gender
import com.example.receivemoney.domain.Language
import com.example.receivemoney.domain.VoiceClipCatalog
import java.io.File

/**
 * Developer tool: synthesizes every word of [VoiceClipCatalog] with the device's text-to-speech
 * engine into `<filesDir>/voice/<tag>/<gender>/<token>.wav`, the layout [ClipEngine] reads.
 * The result sounds like the phone engine (it is a stand-in until real recordings exist), but it
 * exercises the whole clip pipeline. Pull the files with
 * `adb exec-out run-as com.example.receivemoney tar -cf - files/voice` into `app/src/main/assets/`.
 */
class PlaceholderClipGenerator(context: Context) {

    private val appContext = context.applicationContext

    /** Returns a one-line summary. [onProgress] receives "language/gender token". */
    suspend fun generateAll(onProgress: (String) -> Unit): String {
        val engine = NativeTtsEngine(appContext)
        var ok = 0
        val failed = mutableListOf<String>()
        try {
            for (language in Language.entries) for (gender in Gender.entries) {
                val dir = File(appContext.filesDir, "voice/${language.tag}/${gender.apiValue}").apply { mkdirs() }
                for ((token, text) in VoiceClipCatalog.texts(language)) {
                    onProgress("${language.tag}/${gender.apiValue} $token")
                    val file = File(dir, VoiceClipCatalog.fileName(token))
                    val done = engine.synthesizeToFile(language, gender, text, file) && file.length() > 44
                    if (done) ok++ else { failed += "${language.tag}/${gender.apiValue}/$token"; file.delete() }
                    Log.d(TAG, "${if (done) "ok  " else "FAIL"} ${language.tag}/${gender.apiValue}/$token \"$text\" ${file.length()}B")
                }
            }
        } finally {
            engine.release()
        }
        val summary = "Placeholder clips: $ok generated, ${failed.size} failed" +
            if (failed.isEmpty()) "" else " (${failed.take(5).joinToString()}${if (failed.size > 5) "…" else ""})"
        Log.i(TAG, summary)
        return summary
    }

    private companion object {
        const val TAG = "PlaceholderClips"
    }
}
