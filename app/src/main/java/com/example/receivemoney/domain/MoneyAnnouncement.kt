package com.example.receivemoney.domain

import java.util.Locale

/**
 * Builds the sentence that is spoken when money is received, following the BU string pattern:
 *
 * | Language | Formula                             | USD example           | KHR example               |
 * |----------|-------------------------------------|-----------------------|---------------------------|
 * | Khmer    | ទទួលបាន {amount} {currency}          | ទទួលបាន ៥ ដុល្លារ        | ទទួលបាន ២ម៉ឺន រៀល           |
 * | English  | Received {amount} {currency}        | Received 5 Dollars    | Received 20,000 Riel      |
 * | Chinese  | 收到 {amount} {currency}             | 收到 5 美金             | 收到 2 万 瑞尔              |
 * | Korean   | {amount} {currency} 입금되었습니다     | 5달러 입금되었습니다      | 2만 리엘 입금되었습니다       |
 * | Japanese | {amount} {currency} を受信しました    | 5ドルを受信しました      | 2万 リエルを受信しました     |
 *
 * Amounts are written with digits. Round ten-thousands use the local scale word
 * (Khmer ម៉ឺន, Chinese 万, Korean 만, Japanese 万); English uses thousands separators.
 */
object MoneyAnnouncement {

    fun text(language: Language, amount: Money): String = when (language) {
        Language.KHMER -> "ទទួលបាន ${khmerNumber(amount)} ${if (amount.currency == Currency.USD) "ដុល្លារ" else "រៀល"}"
        Language.ENGLISH -> "Received ${englishNumber(amount)} ${englishCurrency(amount)}"
        Language.CHINESE -> "收到 ${cjkNumber(amount, "万", " ")} ${if (amount.currency == Currency.USD) "美金" else "瑞尔"}"
        Language.KOREAN -> when (amount.currency) {
            Currency.USD -> "${cjkNumber(amount, "만", "")}달러 입금되었습니다"
            Currency.KHR -> "${cjkNumber(amount, "만", "")} 리엘 입금되었습니다"
        }
        Language.JAPANESE -> when (amount.currency) {
            Currency.USD -> "${cjkNumber(amount, "万", "")}ドルを受信しました"
            Currency.KHR -> "${cjkNumber(amount, "万", "")} リエルを受信しました"
        }
    }

    private const val TEN_THOUSAND = 10_000L

    /** "5", "5.75", "20,000". */
    private fun englishNumber(amount: Money): String {
        val major = String.format(Locale.US, "%,d", amount.major)
        return if (amount.minor > 0) "$major.${minorDigits(amount)}" else major
    }

    private fun englishCurrency(amount: Money): String = when (amount.currency) {
        Currency.USD -> if (amount.major == 1L && amount.minor == 0) "Dollar" else "Dollars"
        Currency.KHR -> "Riel"
    }

    /** "៥", "៥.៧៥", "២ម៉ឺន" (Khmer digits; round ten-thousands use ម៉ឺន). */
    private fun khmerNumber(amount: Money): String {
        val text = if (amount.minor == 0 && isRoundTenThousand(amount.major)) {
            "${amount.major / TEN_THOUSAND}ម៉ឺន"
        } else {
            plainNumber(amount)
        }
        return text.map { c -> if (c in '0'..'9') '០' + (c - '0') else c }.joinToString("")
    }

    /** "5", "5.75", "2 万" / "2만" / "2万": round ten-thousands use [scaleWord], joined by [separator]. */
    private fun cjkNumber(amount: Money, scaleWord: String, separator: String): String =
        if (amount.minor == 0 && isRoundTenThousand(amount.major)) {
            "${amount.major / TEN_THOUSAND}$separator$scaleWord"
        } else {
            plainNumber(amount)
        }

    private fun isRoundTenThousand(major: Long) = major >= TEN_THOUSAND && major % TEN_THOUSAND == 0L

    private fun plainNumber(amount: Money): String =
        if (amount.minor > 0) "${amount.major}.${minorDigits(amount)}" else amount.major.toString()

    private fun minorDigits(amount: Money): String =
        amount.minor.toString().padStart(amount.currency.decimals, '0')
}
