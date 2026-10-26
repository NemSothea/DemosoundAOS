package com.example.receivemoney.domain

import com.example.receivemoney.domain.VoiceClipCatalog.AND
import com.example.receivemoney.domain.VoiceClipCatalog.CENT
import com.example.receivemoney.domain.VoiceClipCatalog.CENTS
import com.example.receivemoney.domain.VoiceClipCatalog.DOLLAR
import com.example.receivemoney.domain.VoiceClipCatalog.DOLLARS
import com.example.receivemoney.domain.VoiceClipCatalog.POINT
import com.example.receivemoney.domain.VoiceClipCatalog.PREFIX
import com.example.receivemoney.domain.VoiceClipCatalog.RIEL
import com.example.receivemoney.domain.VoiceClipCatalog.SUFFIX
import com.example.receivemoney.domain.VoiceClipCatalog.TWO_BEFORE_SCALE

/**
 * Breaks an announcement into the clip tokens of [VoiceClipCatalog], following the same sentence
 * pattern as [MoneyAnnouncement] but with every number spelled out in the language's own system:
 * English and Khmer by their number words, Chinese / Korean / Japanese by the 万-based system.
 *
 * Examples: 20,000 riel → English [prefix, 20, 1000, riel], Khmer [prefix, 2, 10000, riel],
 * Chinese [prefix, 2b, 10000, riel], Korean [2, 10000, riel, suffix], Japanese [2, 10000, riel, suffix].
 */
object AnnouncementTokens {

    fun tokens(language: Language, amount: Money): List<String> = when (language) {
        Language.ENGLISH -> english(amount)
        Language.KHMER -> khmer(amount)
        Language.CHINESE -> listOf(PREFIX) + chinese(amount.major) + decimal(amount) + currency(amount)
        Language.KOREAN -> korean(amount.major) + decimal(amount) + currency(amount) + SUFFIX
        Language.JAPANESE -> japanese(amount.major) + decimal(amount) + currency(amount) + SUFFIX
    }

    // ---- English -------------------------------------------------------------------------

    private fun english(amount: Money): List<String> = buildList {
        add(PREFIX)
        when (amount.currency) {
            Currency.USD -> {
                if (amount.major > 0 || amount.minor == 0) {
                    addAll(englishNumber(amount.major)); add(if (amount.major == 1L) DOLLAR else DOLLARS)
                }
                if (amount.minor > 0) {
                    if (amount.major > 0) add(AND)
                    addAll(englishNumber(amount.minor.toLong())); add(if (amount.minor == 1) CENT else CENTS)
                }
            }
            Currency.KHR -> { addAll(englishNumber(amount.major)); add(RIEL) }
        }
    }

    private val ENGLISH_SCALES = listOf(1_000_000_000L, 1_000_000L, 1_000L)

    private fun englishNumber(n: Long): List<String> {
        if (n == 0L) return listOf("0")
        val out = mutableListOf<String>()
        var rest = n
        for (scale in ENGLISH_SCALES) {
            if (rest >= scale) { out += englishBelowThousand((rest / scale).toInt()); out += scale.toString(); rest %= scale }
        }
        if (rest > 0) out += englishBelowThousand(rest.toInt())
        return out
    }

    private fun englishBelowThousand(n: Int): List<String> = buildList {
        if (n >= 100) { add((n / 100).toString()); add("100") }
        val rest = n % 100
        when {
            rest == 0 -> Unit
            rest < 20 -> add(rest.toString())
            else -> { add((rest / 10 * 10).toString()); if (rest % 10 > 0) add((rest % 10).toString()) }
        }
    }

    // ---- Khmer ---------------------------------------------------------------------------

    private fun khmer(amount: Money): List<String> = buildList {
        add(PREFIX)
        when (amount.currency) {
            Currency.USD -> {
                if (amount.major > 0 || amount.minor == 0) { addAll(khmerNumber(amount.major)); add(DOLLAR) }
                if (amount.minor > 0) {
                    if (amount.major > 0) add(AND)
                    addAll(khmerNumber(amount.minor.toLong())); add(CENT)
                }
            }
            Currency.KHR -> { addAll(khmerNumber(amount.major)); add(RIEL) }
        }
    }

    /** Mirrors [KhmerNumberWords]: លាន for millions, then one digit each of សែន / ម៉ឺន / ពាន់. */
    private fun khmerNumber(n: Long): List<String> {
        if (n == 0L) return listOf("0")
        val out = mutableListOf<String>()
        var rest = n
        if (rest >= 1_000_000) { out += khmerNumber(rest / 1_000_000); out += "1000000"; rest %= 1_000_000 }
        for (scale in listOf(100_000L, 10_000L, 1_000L)) {
            val digit = (rest / scale).toInt()
            if (digit > 0) { out += digit.toString(); out += scale.toString() }
            rest %= scale
        }
        if (rest >= 100) { out += (rest / 100).toString(); out += "100" }
        val below = (rest % 100).toInt()
        if (below >= 10) out += (below / 10 * 10).toString()
        if (below % 10 > 0) out += (below % 10).toString()
        return out
    }

    // ---- Chinese / Korean / Japanese (万-based) --------------------------------------------

    private fun currency(amount: Money) = if (amount.currency == Currency.USD) DOLLAR else RIEL

    /** Cents as "point seven five"; a trailing zero is dropped (5.50 → point five). */
    private fun decimal(amount: Money): List<String> {
        if (amount.minor == 0) return emptyList()
        val digits = amount.minor.toString().padStart(amount.currency.decimals, '0').trimEnd('0')
        return listOf(POINT) + digits.map { it.toString() }
    }

    /** Splits into 万-groups: [亿 part, 万 part, units part], highest first, dropping leading zeros. */
    private fun myriadGroups(n: Long): List<Pair<Int, Long>> {
        val groups = listOf(100_000_000L, 10_000L, 1L).map { scale -> ((n / scale) % 10_000).toInt() to scale }
        return groups.dropWhile { it.first == 0 }
    }

    /**
     * Chinese: 两 before 万/亿/千/百, 二 elsewhere; one 零 for each internal gap (一百零五, 一万零五);
     * 一 dropped before a leading 十 (十五, 十万) but kept inside (一百一十五).
     */
    private fun chinese(n: Long): List<String> {
        if (n == 0L) return listOf("0")
        val out = mutableListOf<String>()
        var pendingZero = false
        myriadGroups(n).forEachIndexed { index, (value, scale) ->
            if (value == 0) {
                pendingZero = true
            } else {
                if (index > 0 && (pendingZero || value < 1000)) out += "0"
                pendingZero = false
                out += chineseGroup(value, leading = index == 0, beforeScale = scale > 1)
                if (scale > 1) out += scale.toString()
            }
        }
        return out
    }

    private fun chineseGroup(value: Int, leading: Boolean, beforeScale: Boolean): List<String> = buildList {
        val digits = listOf(value / 1000, value / 100 % 10, value / 10 % 10, value % 10)
        val scales = listOf("1000", "100", "10", null)
        var started = false
        var pendingZero = false
        for (i in digits.indices) {
            val d = digits[i]
            val scale = scales[i]
            if (d == 0) {
                if (started) pendingZero = true
                continue
            }
            if (pendingZero) add("0")
            pendingZero = false
            val dropOne = d == 1 && scale == "10" && !started && leading
            val useLiang = d == 2 && (scale == "1000" || scale == "100" || (scale == null && beforeScale && !started))
            if (!dropOne) add(if (useLiang) TWO_BEFORE_SCALE else d.toString())
            if (scale != null) add(scale)
            started = true
        }
    }

    /** Korean (Sino): 일 dropped before 십/백/천/만 (백오 = 105), kept before 억; no zero word. */
    private fun korean(n: Long): List<String> {
        if (n == 0L) return listOf("0")
        val out = mutableListOf<String>()
        for ((value, scale) in myriadGroups(n)) {
            if (value == 0) continue
            if (scale == 100_000_000L) { out += cjkGroup(value, oneBeforeScale = false, irregular = emptyMap()); out += "100000000"; continue }
            if (scale == 10_000L) {
                if (value != 1) out += cjkGroup(value, oneBeforeScale = false, irregular = emptyMap())
                out += "10000"
                continue
            }
            out += cjkGroup(value, oneBeforeScale = false, irregular = emptyMap())
        }
        return out
    }

    private val JAPANESE_IRREGULAR = mapOf(300 to "300", 600 to "600", 800 to "800", 3000 to "3000", 8000 to "8000")

    /** Japanese: いち dropped before 十/百/千, kept before 万/億 (いちまん); irregular 300/600/800/3000/8000. */
    private fun japanese(n: Long): List<String> {
        if (n == 0L) return listOf("0")
        val out = mutableListOf<String>()
        for ((value, scale) in myriadGroups(n)) {
            if (value == 0) continue
            when (scale) {
                100_000_000L -> { out += cjkGroup(value, oneBeforeScale = false, irregular = JAPANESE_IRREGULAR); out += "100000000" }
                10_000L -> { out += cjkGroup(value, oneBeforeScale = false, irregular = JAPANESE_IRREGULAR); out += "10000" }
                else -> out += cjkGroup(value, oneBeforeScale = false, irregular = JAPANESE_IRREGULAR)
            }
        }
        return out
    }

    /** 0–9999 as [digit?, scale]... with 1 dropped before 千/百/十 when [oneBeforeScale] is false. */
    private fun cjkGroup(value: Int, oneBeforeScale: Boolean, irregular: Map<Int, String>): List<String> = buildList {
        for (scale in listOf(1000, 100, 10)) {
            val d = value / scale % 10
            if (d == 0) continue
            val irregularReading = irregular[d * scale]
            if (irregularReading != null) {
                add(irregularReading)
            } else {
                if (d != 1 || oneBeforeScale) add(d.toString())
                add(scale.toString())
            }
        }
        val ones = value % 10
        if (ones > 0) add(ones.toString())
    }
}
