package com.example.receivemoney.domain

import java.math.BigDecimal

/**
 * Supported currencies. [decimals] is the number of minor-unit digits announced.
 * Riel is ISO-4217 2-decimal, but sen are not used in practice (the smallest note is 100 ៛),
 * so the demo treats KHR as whole riels only.
 */
enum class Currency(val code: String, val decimals: Int, val maxMinorUnits: Long) {
    USD("USD", decimals = 2, maxMinorUnits = 100_000_000L),      // $1,000,000.00
    KHR("KHR", decimals = 0, maxMinorUnits = 4_000_000_000L),    // ៛4,000,000,000
}

/**
 * An exact amount of money stored as an integer number of minor units (cents for USD, riels for KHR).
 * No floating point is involved anywhere between user input and the spoken text.
 */
data class Money(val currency: Currency, val minorUnits: Long) {
    init {
        require(minorUnits in 1..currency.maxMinorUnits) { "Amount out of range" }
    }

    /** Whole units: dollars for USD, riels for KHR. */
    val major: Long get() = minorUnits / scale

    /** Fractional part in minor units: cents for USD, always 0 for KHR. */
    val minor: Int get() = (minorUnits % scale).toInt()

    private val scale: Long get() = BigDecimal.TEN.pow(currency.decimals).toLong()

    /** Canonical form sent to the backend, e.g. "5.75" (USD) or "10000" (KHR). */
    fun toPlainString(): String =
        BigDecimal.valueOf(minorUnits, currency.decimals).toPlainString()

    companion object {
        fun usd(cents: Long) = Money(Currency.USD, cents)
        fun khr(riels: Long) = Money(Currency.KHR, riels)
    }
}

enum class AmountError {
    EMPTY,
    MALFORMED,
    NEGATIVE,
    ZERO,
    TOO_MANY_DECIMALS,
    TOO_LARGE,
}

sealed interface AmountParseResult {
    data class Valid(val amount: Money) : AmountParseResult
    data class Invalid(val error: AmountError) : AmountParseResult
}

object AmountParser {
    private const val MAX_INPUT_LENGTH = 16

    // Digits with an optional decimal point, e.g. "5", "5.", "5.75", ".75".
    private val PLAIN_DECIMAL = Regex("""^(\d*)(?:\.(\d*))?$""")

    fun parse(input: String, currency: Currency): AmountParseResult {
        val text = input.trim()
        if (text.isEmpty()) return invalid(AmountError.EMPTY)
        if (text.length > MAX_INPUT_LENGTH) return invalid(AmountError.TOO_LARGE)

        val negative = text.startsWith("-")
        val unsigned = if (negative) text.substring(1) else text
        val match = PLAIN_DECIMAL.matchEntire(unsigned) ?: return invalid(AmountError.MALFORMED)
        val whole = match.groupValues[1]
        val fraction = match.groupValues[2]
        if (whole.isEmpty() && fraction.isEmpty()) return invalid(AmountError.MALFORMED)

        val value = BigDecimal((whole.ifEmpty { "0" }) + "." + fraction.ifEmpty { "0" })
        if (negative && value.signum() != 0) return invalid(AmountError.NEGATIVE)
        if (value.signum() == 0) return invalid(AmountError.ZERO)

        // "5.750" is still 5.75 USD; "5.755" USD or "100.5" KHR cannot be represented.
        if (value.stripTrailingZeros().scale() > currency.decimals) {
            return invalid(AmountError.TOO_MANY_DECIMALS)
        }
        val minorUnits = value.movePointRight(currency.decimals)
        if (minorUnits > BigDecimal.valueOf(currency.maxMinorUnits)) return invalid(AmountError.TOO_LARGE)

        return AmountParseResult.Valid(Money(currency, minorUnits.longValueExact()))
    }

    private fun invalid(error: AmountError) = AmountParseResult.Invalid(error)
}
