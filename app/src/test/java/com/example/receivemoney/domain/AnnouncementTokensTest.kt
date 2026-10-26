package com.example.receivemoney.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementTokensTest {

    private fun t(language: Language, amount: Money) = AnnouncementTokens.tokens(language, amount).joinToString(" ")
    private val usd5 = Money.usd(500)
    private val khr20k = Money.khr(20_000)

    // One case per cell of the BU pattern table.

    @Test fun `khmer presets`() {
        assertEquals("prefix 5 dollar", t(Language.KHMER, usd5))
        assertEquals("prefix 2 10000 riel", t(Language.KHMER, khr20k))
    }

    @Test fun `english presets`() {
        assertEquals("prefix 5 dollars", t(Language.ENGLISH, usd5))
        assertEquals("prefix 20 1000 riel", t(Language.ENGLISH, khr20k))
    }

    @Test fun `chinese presets`() {
        assertEquals("prefix 5 dollar", t(Language.CHINESE, usd5))
        assertEquals("prefix 2b 10000 riel", t(Language.CHINESE, khr20k))
    }

    @Test fun `korean presets`() {
        assertEquals("5 dollar suffix", t(Language.KOREAN, usd5))
        assertEquals("2 10000 riel suffix", t(Language.KOREAN, khr20k))
    }

    @Test fun `japanese presets`() {
        assertEquals("5 dollar suffix", t(Language.JAPANESE, usd5))
        assertEquals("2 10000 riel suffix", t(Language.JAPANESE, khr20k))
    }

    // Dynamic amounts.

    @Test fun `english numbers`() {
        assertEquals("prefix 1 dollar", t(Language.ENGLISH, Money.usd(100)))
        assertEquals("prefix 5 dollars and 70 5 cents", t(Language.ENGLISH, Money.usd(575)))
        assertEquals("prefix 1 cent", t(Language.ENGLISH, Money.usd(1)))
        assertEquals("prefix 1 100 riel", t(Language.ENGLISH, Money.khr(100)))
        assertEquals("prefix 1 1000 5 100 riel", t(Language.ENGLISH, Money.khr(1_500)))
        assertEquals("prefix 20 5 1000 riel", t(Language.ENGLISH, Money.khr(25_000)))
        assertEquals("prefix 1 100 1000 riel", t(Language.ENGLISH, Money.khr(100_000)))
        assertEquals("prefix 1 1000000 2 100 30 4 1000 5 100 60 7 riel", t(Language.ENGLISH, Money.khr(1_234_567)))
        assertEquals("prefix 4 1000000000 riel", t(Language.ENGLISH, Money.khr(4_000_000_000)))
        assertEquals("prefix 15 riel", t(Language.ENGLISH, Money.khr(15)))
    }

    @Test fun `khmer numbers mirror KhmerNumberWords`() {
        assertEquals("prefix 1 100 riel", t(Language.KHMER, Money.khr(100)))
        assertEquals("prefix 1 1000 5 100 riel", t(Language.KHMER, Money.khr(1_500)))
        assertEquals("prefix 2 10000 5 1000 riel", t(Language.KHMER, Money.khr(25_000)))
        assertEquals("prefix 1 100000 riel", t(Language.KHMER, Money.khr(100_000)))
        assertEquals("prefix 2 1000000 5 100000 riel", t(Language.KHMER, Money.khr(2_500_000)))
        assertEquals("prefix 20 5 riel", t(Language.KHMER, Money.khr(25)))
        assertEquals("prefix 10 5 riel", t(Language.KHMER, Money.khr(15)))
        assertEquals("prefix 5 dollar and 70 5 cent", t(Language.KHMER, Money.usd(575)))
        // Every token the Khmer tokenizer can emit exists in the catalog.
        for (n in listOf(1L, 15, 99, 105, 999, 1_000, 10_000, 100_000, 999_999, 1_000_000, 4_000_000_000)) {
            val missing = AnnouncementTokens.tokens(Language.KHMER, Money.khr(n)) - VoiceClipCatalog.texts(Language.KHMER).keys
            assertTrue("missing $missing for $n", missing.isEmpty())
        }
    }

    @Test fun `chinese numbers`() {
        assertEquals("prefix 1 100 dollar", t(Language.CHINESE, Money.usd(100_00)))
        assertEquals("prefix 10 5 riel", t(Language.CHINESE, Money.khr(15)))
        assertEquals("prefix 1 100 1 10 5 riel", t(Language.CHINESE, Money.khr(115)))
        assertEquals("prefix 1 100 0 5 riel", t(Language.CHINESE, Money.khr(105)))
        assertEquals("prefix 1 1000 5 100 riel", t(Language.CHINESE, Money.khr(1_500)))
        assertEquals("prefix 1 1000 0 5 riel", t(Language.CHINESE, Money.khr(1_005)))
        assertEquals("prefix 2b 100 riel", t(Language.CHINESE, Money.khr(200)))
        assertEquals("prefix 2b 10000 5 1000 riel", t(Language.CHINESE, Money.khr(25_000)))
        assertEquals("prefix 2b 10000 0 5 10 riel", t(Language.CHINESE, Money.khr(20_050)))
        assertEquals("prefix 1 10000 0 5 riel", t(Language.CHINESE, Money.khr(10_005)))
        assertEquals("prefix 10 10000 riel", t(Language.CHINESE, Money.khr(100_000)))
        assertEquals("prefix 1 100 10000 riel", t(Language.CHINESE, Money.khr(1_000_000)))
        assertEquals("prefix 40 100000000 riel".replace("40", "4 10"), t(Language.CHINESE, Money.khr(4_000_000_000)))
        assertEquals("prefix 5 point 7 5 dollar", t(Language.CHINESE, Money.usd(575)))
        assertEquals("prefix 5 point 5 dollar", t(Language.CHINESE, Money.usd(550)))
    }

    @Test fun `korean numbers`() {
        assertEquals("100 dollar suffix", t(Language.KOREAN, Money.usd(100_00)))
        assertEquals("10 5 riel suffix", t(Language.KOREAN, Money.khr(15)))
        assertEquals("100 5 riel suffix", t(Language.KOREAN, Money.khr(105)))
        assertEquals("1000 5 100 riel suffix", t(Language.KOREAN, Money.khr(1_500)))
        assertEquals("10000 riel suffix", t(Language.KOREAN, Money.khr(10_000)))
        assertEquals("2 10000 5 1000 riel suffix", t(Language.KOREAN, Money.khr(25_000)))
        assertEquals("10 10000 riel suffix", t(Language.KOREAN, Money.khr(100_000)))
        assertEquals("1 100000000 riel suffix", t(Language.KOREAN, Money.khr(100_000_000)))
        assertEquals("5 point 7 5 dollar suffix", t(Language.KOREAN, Money.usd(575)))
    }

    @Test fun `japanese numbers`() {
        assertEquals("100 dollar suffix", t(Language.JAPANESE, Money.usd(100_00)))
        assertEquals("300 riel suffix", t(Language.JAPANESE, Money.khr(300)))
        assertEquals("600 riel suffix", t(Language.JAPANESE, Money.khr(600)))
        assertEquals("800 riel suffix", t(Language.JAPANESE, Money.khr(800)))
        assertEquals("3000 riel suffix", t(Language.JAPANESE, Money.khr(3_000)))
        assertEquals("8000 riel suffix", t(Language.JAPANESE, Money.khr(8_000)))
        assertEquals("1000 5 100 riel suffix", t(Language.JAPANESE, Money.khr(1_500)))
        assertEquals("1 10000 riel suffix", t(Language.JAPANESE, Money.khr(10_000)))
        assertEquals("2 10000 5 1000 riel suffix", t(Language.JAPANESE, Money.khr(25_000)))
        assertEquals("10 10000 riel suffix", t(Language.JAPANESE, Money.khr(100_000)))
        assertEquals("10 5 riel suffix", t(Language.JAPANESE, Money.khr(15)))
        assertEquals("5 point 7 5 dollar suffix", t(Language.JAPANESE, Money.usd(575)))
    }

    @Test fun `every emitted token exists in the catalog for all languages`() {
        val samples = listOf(1L, 2, 10, 11, 15, 20, 25, 99, 100, 101, 105, 110, 200, 300, 999, 1_000, 1_005, 1_500, 8_000, 9_999,
            10_000, 10_005, 20_000, 20_050, 25_000, 99_999, 100_000, 123_456, 999_999, 1_000_000, 1_234_567, 10_000_000,
            100_000_000, 123_456_789, 4_000_000_000)
        for (language in Language.entries) {
            val catalog = VoiceClipCatalog.texts(language).keys
            for (n in samples) {
                for (amount in listOfNotNull(Money.khr(n), (if (n <= Currency.USD.maxMinorUnits) Money.usd(n) else null))) {
                    val missing = AnnouncementTokens.tokens(language, amount) - catalog
                    assertTrue("$language $amount missing $missing", missing.isEmpty())
                }
            }
            val cents = AnnouncementTokens.tokens(language, Money.usd(575)) - catalog
            assertTrue("$language cents missing $cents", cents.isEmpty())
        }
    }
}
