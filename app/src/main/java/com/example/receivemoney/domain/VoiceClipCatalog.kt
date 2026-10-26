package com.example.receivemoney.domain

/**
 * The vocabulary of pre-recorded clips, per language. Every spoken announcement is a sequence of
 * these tokens ([AnnouncementTokens]); each token is one audio file
 * `assets/voice/<language tag>/<female|male>/<token>.wav`.
 *
 * Token ids are ASCII so they double as file names: digits "0".."9", English teens "10".."19",
 * tens "20".."90", scale words by value ("100", "1000", "10000", …), Chinese 两 as "2b",
 * Japanese irregular readings by value ("300", "600", "800", "3000", "8000") and the phrase
 * words "prefix", "suffix", "dollar", "dollars", "cent", "cents", "riel", "and", "point".
 */
object VoiceClipCatalog {

    const val PREFIX = "prefix"
    const val SUFFIX = "suffix"
    const val DOLLAR = "dollar"
    const val DOLLARS = "dollars"
    const val CENT = "cent"
    const val CENTS = "cents"
    const val RIEL = "riel"
    const val AND = "and"
    const val POINT = "point"
    const val TWO_BEFORE_SCALE = "2b"

    /** Token id → text to record (or to synthesize for placeholders), in display order. */
    fun texts(language: Language): Map<String, String> = when (language) {
        Language.ENGLISH -> ENGLISH
        Language.KHMER -> KHMER
        Language.CHINESE -> CHINESE
        Language.KOREAN -> KOREAN
        Language.JAPANESE -> JAPANESE
    }

    fun fileName(token: String) = "$token.wav"

    private val ENGLISH: Map<String, String> = linkedMapOf(
        PREFIX to "Received",
        *(listOf(
            "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
            "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
        ).mapIndexed { i, w -> i.toString() to w }.toTypedArray()),
        "20" to "twenty", "30" to "thirty", "40" to "forty", "50" to "fifty", "60" to "sixty",
        "70" to "seventy", "80" to "eighty", "90" to "ninety",
        "100" to "hundred", "1000" to "thousand", "1000000" to "million", "1000000000" to "billion",
        DOLLAR to "Dollar", DOLLARS to "Dollars", CENT to "Cent", CENTS to "Cents", RIEL to "Riel", AND to "and",
    )

    private val KHMER: Map<String, String> = linkedMapOf(
        PREFIX to "ទទួលបាន",
        "0" to "សូន្យ", "1" to "មួយ", "2" to "ពីរ", "3" to "បី", "4" to "បួន", "5" to "ប្រាំ",
        "6" to "ប្រាំមួយ", "7" to "ប្រាំពីរ", "8" to "ប្រាំបី", "9" to "ប្រាំបួន",
        "10" to "ដប់", "20" to "ម្ភៃ", "30" to "សាមសិប", "40" to "សែសិប", "50" to "ហាសិប",
        "60" to "ហុកសិប", "70" to "ចិតសិប", "80" to "ប៉ែតសិប", "90" to "កៅសិប",
        "100" to "រយ", "1000" to "ពាន់", "10000" to "ម៉ឺន", "100000" to "សែន", "1000000" to "លាន",
        DOLLAR to "ដុល្លារ", CENT to "សេន", RIEL to "រៀល", AND to "និង",
    )

    private val CHINESE: Map<String, String> = linkedMapOf(
        PREFIX to "收到",
        "0" to "零", "1" to "一", "2" to "二", TWO_BEFORE_SCALE to "两", "3" to "三", "4" to "四", "5" to "五",
        "6" to "六", "7" to "七", "8" to "八", "9" to "九",
        "10" to "十", "100" to "百", "1000" to "千", "10000" to "万", "100000000" to "亿",
        DOLLAR to "美金", RIEL to "瑞尔", POINT to "点",
    )

    private val KOREAN: Map<String, String> = linkedMapOf(
        "0" to "영", "1" to "일", "2" to "이", "3" to "삼", "4" to "사", "5" to "오",
        "6" to "육", "7" to "칠", "8" to "팔", "9" to "구",
        "10" to "십", "100" to "백", "1000" to "천", "10000" to "만", "100000000" to "억",
        DOLLAR to "달러", RIEL to "리엘", POINT to "점", SUFFIX to "입금되었습니다",
    )

    private val JAPANESE: Map<String, String> = linkedMapOf(
        "0" to "ゼロ", "1" to "いち", "2" to "に", "3" to "さん", "4" to "よん", "5" to "ご",
        "6" to "ろく", "7" to "なな", "8" to "はち", "9" to "きゅう",
        "10" to "じゅう", "100" to "ひゃく", "300" to "さんびゃく", "600" to "ろっぴゃく", "800" to "はっぴゃく",
        "1000" to "せん", "3000" to "さんぜん", "8000" to "はっせん", "10000" to "まん", "100000000" to "おく",
        DOLLAR to "ドル", RIEL to "リエル", POINT to "てん", SUFFIX to "を受信しました",
    )
}
