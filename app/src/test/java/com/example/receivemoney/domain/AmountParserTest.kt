package com.example.receivemoney.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountParserTest {

    private fun cents(input: String): Long =
        (AmountParser.parse(input, Currency.USD) as AmountParseResult.Valid).amount.minorUnits

    private fun error(input: String): AmountError =
        (AmountParser.parse(input, Currency.USD) as AmountParseResult.Invalid).error

    @Test
    fun `parses valid decimal amounts exactly`() {
        assertEquals(575, cents("5.75"))
        assertEquals(500, cents("5"))
        assertEquals(500, cents("5."))
        assertEquals(50, cents(".5"))
        assertEquals(1, cents("0.01"))
        assertEquals(575, cents(" 5.75 "))
        assertEquals(575, cents("5.750"))
        assertEquals(100_000_000, cents("1000000"))
    }

    @Test
    fun `avoids floating point rounding`() {
        // 0.1 + 0.2 style inputs that are inexact as doubles stay exact.
        assertEquals(29, cents("0.29"))
        assertEquals(1_005, cents("10.05"))
        assertEquals(99_999_999, cents("999999.99"))
    }

    @Test
    fun `rejects empty input`() {
        assertEquals(AmountError.EMPTY, error(""))
        assertEquals(AmountError.EMPTY, error("   "))
    }

    @Test
    fun `rejects malformed input`() {
        listOf(".", "abc", "5.7.5", "1,000", "1e3", "5 75", "+5", "$5", "--5", "0x10").forEach {
            assertEquals("input=$it", AmountError.MALFORMED, error(it))
        }
    }

    @Test
    fun `rejects negative and zero`() {
        assertEquals(AmountError.NEGATIVE, error("-5"))
        assertEquals(AmountError.NEGATIVE, error("-0.01"))
        assertEquals(AmountError.ZERO, error("0"))
        assertEquals(AmountError.ZERO, error("0.00"))
        assertEquals(AmountError.ZERO, error("-0"))
    }

    @Test
    fun `rejects more decimals than USD precision`() {
        assertEquals(AmountError.TOO_MANY_DECIMALS, error("5.755"))
        assertEquals(AmountError.TOO_MANY_DECIMALS, error("0.001"))
    }

    @Test
    fun `rejects out of range amounts`() {
        assertEquals(AmountError.TOO_LARGE, error("1000000.01"))
        assertEquals(AmountError.TOO_LARGE, error("99999999"))
        assertEquals(AmountError.TOO_LARGE, error("1".repeat(40)))
    }

    @Test
    fun `plain string is canonical`() {
        assertEquals("5.75", Money.usd(575).toPlainString())
        assertEquals("0.05", Money.usd(5).toPlainString())
        assertEquals("12.00", Money.usd(1200).toPlainString())
    }

    private fun khr(input: String) = AmountParser.parse(input, Currency.KHR)

    @Test
    fun `riel amounts are whole numbers`() {
        assertEquals(AmountParseResult.Valid(Money.khr(10_000)), khr("10000"))
        assertEquals(AmountParseResult.Valid(Money.khr(500)), khr("500.00"))
        assertEquals(AmountParseResult.Valid(Money.khr(4_000_000_000)), khr("4000000000"))
        assertEquals(AmountParseResult.Invalid(AmountError.TOO_MANY_DECIMALS), khr("100.5"))
        assertEquals(AmountParseResult.Invalid(AmountError.TOO_LARGE), khr("4000000001"))
        assertEquals(AmountParseResult.Invalid(AmountError.ZERO), khr("0"))
        assertEquals(AmountParseResult.Invalid(AmountError.NEGATIVE), khr("-100"))
        assertEquals(AmountParseResult.Invalid(AmountError.MALFORMED), khr("10,000"))
    }

    @Test
    fun `riel plain string has no decimals`() {
        assertEquals("10000", Money.khr(10_000).toPlainString())
        assertEquals(10_000, Money.khr(10_000).major)
        assertEquals(0, Money.khr(10_000).minor)
    }
}
