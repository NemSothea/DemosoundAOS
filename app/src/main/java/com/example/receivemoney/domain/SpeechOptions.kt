package com.example.receivemoney.domain

import java.util.Locale

/**
 * Languages offered by the demo, in the order shown in the Language dropdown.
 * [tag] is the BCP-47 tag used to pick the device's text-to-speech voice.
 */
enum class Language(val tag: String, val locale: Locale) {
    KHMER("km-KH", Locale("km", "KH")),
    ENGLISH("en-US", Locale.US),
    KOREAN("ko-KR", Locale.KOREA),
    JAPANESE("ja-JP", Locale.JAPAN),
    CHINESE("zh-CN", Locale.SIMPLIFIED_CHINESE),
}

enum class Gender(val apiValue: String) {
    MALE("male"),
    FEMALE("female");

    val opposite: Gender get() = if (this == MALE) FEMALE else MALE
}
