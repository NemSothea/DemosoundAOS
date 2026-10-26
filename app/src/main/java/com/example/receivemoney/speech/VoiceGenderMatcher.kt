package com.example.receivemoney.speech

import com.example.receivemoney.domain.Gender

/**
 * Android's [android.speech.tts.Voice] has no gender property, so gender is read from:
 *  1. Explicit labels in the name or features, e.g. "en-us-x-sfg#female_1-local" or "gender=male".
 *  2. Samsung TTS names, which encode gender after "SMT": "en-US-SMTf00" (female), "en-US-SMTm00" (male).
 *  3. A short, hand-curated list of Google Speech Services voice codes. Google does not expose
 *     gender for these, so the list comes from listening to each voice; extend it only the same way.
 * Anything else is treated as unknown; we never guess.
 */
object VoiceGenderMatcher {
    private val FEMALE = Regex("""\bfemale""")
    private val MALE = Regex("""\bmale""")
    private val SAMSUNG = Regex("""smt([fm])\d""")
    private val GOOGLE_CODE = Regex("""^([a-z]{2}-[a-z]{2})-x-([a-z]{3})-""")

    private val GOOGLE_VOICES: Map<String, Gender> = mapOf(
        "en-us/iol" to Gender.MALE,
        "en-us/iom" to Gender.MALE,
        "en-us/tpd" to Gender.MALE,
        "en-us/iob" to Gender.FEMALE,
        "en-us/iog" to Gender.FEMALE,
        "en-us/sfg" to Gender.FEMALE,
        "en-us/tpc" to Gender.FEMALE,
        "en-us/tpf" to Gender.FEMALE,
        // Google's only Khmer voice; confirmed female by listening on a Galaxy Note10 (2026-10-23).
        "km-kh/khm" to Gender.FEMALE,
    )

    fun detect(name: String, features: Collection<String>): Gender? {
        val haystack = (listOf(name) + features).joinToString(" ").lowercase()
        when {
            FEMALE.containsMatchIn(haystack) -> return Gender.FEMALE
            MALE.containsMatchIn(haystack) -> return Gender.MALE
        }
        SAMSUNG.find(name.lowercase())?.let {
            return if (it.groupValues[1] == "m") Gender.MALE else Gender.FEMALE
        }
        GOOGLE_CODE.find(name.lowercase())?.let {
            return GOOGLE_VOICES["${it.groupValues[1]}/${it.groupValues[2]}"]
        }
        return null
    }
}
