package com.example.receivemoney.domain

import java.util.Locale

/** Languages offered by the demo. [tag] is the BCP-47 tag shared with the backend. */
enum class Language(val tag: String, val locale: Locale) {
    KHMER("km-KH", Locale("km", "KH")),
    ENGLISH("en-US", Locale.US),
}

enum class Gender(val apiValue: String) {
    MALE("male"),
    FEMALE("female");

    val opposite: Gender get() = if (this == MALE) FEMALE else MALE
}

enum class SpeechMode {
    /** Android native TextToSpeech, on-device, no cost. */
    FREE,

    /** Azure AI Speech neural voices through the backend. */
    PAID,
}
