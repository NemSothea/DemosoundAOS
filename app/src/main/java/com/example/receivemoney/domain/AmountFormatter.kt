package com.example.receivemoney.domain

/** Converts non-negative whole numbers into spoken words. */
object EnglishNumberWords {
    private val ONES = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen",
    )
    private val TENS = listOf(
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
    )
    private val SCALES = listOf(1_000_000_000L to "billion", 1_000_000L to "million", 1_000L to "thousand")

    fun toWords(number: Long): String {
        require(number >= 0) { "Negative numbers are not supported" }
        if (number == 0L) return ONES[0]
        val parts = mutableListOf<String>()
        var rest = number
        for ((scale, name) in SCALES) {
            if (rest >= scale) {
                parts += "${belowThousand((rest / scale).toInt())} $name"
                rest %= scale
            }
        }
        if (rest > 0) parts += belowThousand(rest.toInt())
        return parts.joinToString(" ")
    }

    private fun belowThousand(n: Int): String {
        val hundreds = n / 100
        val rest = n % 100
        val parts = mutableListOf<String>()
        if (hundreds > 0) parts += "${ONES[hundreds]} hundred"
        if (rest > 0) parts += belowHundred(rest)
        return parts.joinToString(" ")
    }

    private fun belowHundred(n: Int): String = when {
        n < 20 -> ONES[n]
        n % 10 == 0 -> TENS[n / 10]
        else -> "${TENS[n / 10]}-${ONES[n % 10]}"
    }
}

/**
 * Khmer number words. Khmer script does not separate words with spaces, so the
 * parts of a number are concatenated (e.g. 75 -> "ចិតសិបប្រាំ").
 * Uses the Khmer scale words: រយ (100), ពាន់ (1,000), ម៉ឺន (10,000), សែន (100,000), លាន (1,000,000),
 * e.g. 25,000 -> "ពីរម៉ឺនប្រាំពាន់". Counts of millions reuse the same rules (4,000,000,000 -> "បួនពាន់លាន").
 */
object KhmerNumberWords {
    private val ONES = listOf(
        "សូន្យ", "មួយ", "ពីរ", "បី", "បួន", "ប្រាំ", "ប្រាំមួយ", "ប្រាំពីរ", "ប្រាំបី", "ប្រាំបួន",
    )
    private val TENS = listOf(
        "", "ដប់", "ម្ភៃ", "សាមសិប", "សែសិប", "ហាសិប", "ហុកសិប", "ចិតសិប", "ប៉ែតសិប", "កៅសិប",
    )
    private const val HUNDRED = "រយ"
    private const val THOUSAND = "ពាន់"
    private const val TEN_THOUSAND = "ម៉ឺន"
    private const val HUNDRED_THOUSAND = "សែន"
    private const val MILLION = "លាន"

    fun toWords(number: Long): String {
        require(number >= 0) { "Negative numbers are not supported" }
        if (number == 0L) return ONES[0]
        val sb = StringBuilder()
        var rest = number
        if (rest >= 1_000_000) {
            sb.append(toWords(rest / 1_000_000)).append(MILLION)
            rest %= 1_000_000
        }
        // Below a million each of សែន / ម៉ឺន / ពាន់ takes a single digit.
        for ((scale, word) in listOf(100_000L to HUNDRED_THOUSAND, 10_000L to TEN_THOUSAND, 1_000L to THOUSAND)) {
            val digit = (rest / scale).toInt()
            if (digit > 0) sb.append(ONES[digit]).append(word)
            rest %= scale
        }
        if (rest > 0) sb.append(belowThousand(rest.toInt()))
        return sb.toString()
    }

    private fun belowThousand(n: Int): String {
        val sb = StringBuilder()
        if (n >= 100) sb.append(ONES[n / 100]).append(HUNDRED)
        val rest = n % 100
        if (rest > 0) {
            if (rest >= 10) sb.append(TENS[rest / 10])
            if (rest % 10 > 0) sb.append(ONES[rest % 10])
        }
        return sb.toString()
    }
}
