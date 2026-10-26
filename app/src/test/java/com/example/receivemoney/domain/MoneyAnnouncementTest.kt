package com.example.receivemoney.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyAnnouncementTest {

    private val usd5 = PresetAmounts.forCurrency(Currency.USD)
    private val khr20k = PresetAmounts.forCurrency(Currency.KHR)

    private fun say(language: Language, amount: Money) = MoneyAnnouncement.text(language, amount)

    @Test
    fun `presets are 5 dollars and 20,000 riel`() {
        assertEquals(Money.usd(500), usd5)
        assertEquals(Money.khr(20_000), khr20k)
    }

    // One case per cell of the BU pattern table.

    @Test
    fun `khmer dollars`() = assertEquals("ទទួលបាន ៥ ដុល្លារ", say(Language.KHMER, usd5))

    @Test
    fun `khmer riel`() = assertEquals("ទទួលបាន ២ម៉ឺន រៀល", say(Language.KHMER, khr20k))

    @Test
    fun `english dollars`() = assertEquals("Received 5 Dollars", say(Language.ENGLISH, usd5))

    @Test
    fun `english riel`() = assertEquals("Received 20,000 Riel", say(Language.ENGLISH, khr20k))

    @Test
    fun `chinese dollars`() = assertEquals("收到 5 美金", say(Language.CHINESE, usd5))

    @Test
    fun `chinese riel`() = assertEquals("收到 2 万 瑞尔", say(Language.CHINESE, khr20k))

    @Test
    fun `korean dollars`() = assertEquals("5달러 입금되었습니다", say(Language.KOREAN, usd5))

    @Test
    fun `korean riel`() = assertEquals("2만 리엘 입금되었습니다", say(Language.KOREAN, khr20k))

    @Test
    fun `japanese dollars`() = assertEquals("5ドルを受信しました", say(Language.JAPANESE, usd5))

    @Test
    fun `japanese riel`() = assertEquals("2万 リエルを受信しました", say(Language.JAPANESE, khr20k))

    // The formatter stays a pure function of any amount, not only the presets.

    @Test
    fun `non-round amounts use plain digits`() {
        assertEquals("Received 5.75 Dollars", say(Language.ENGLISH, Money.usd(575)))
        assertEquals("Received 1 Dollar", say(Language.ENGLISH, Money.usd(100)))
        assertEquals("Received 1,234,567 Riel", say(Language.ENGLISH, Money.khr(1_234_567)))
        assertEquals("ទទួលបាន ៥.៧៥ ដុល្លារ", say(Language.KHMER, Money.usd(575)))
        assertEquals("ទទួលបាន ១០០០ រៀល", say(Language.KHMER, Money.khr(1_000)))
        assertEquals("收到 25000 瑞尔", say(Language.CHINESE, Money.khr(25_000)))
        assertEquals("25000 리엘 입금되었습니다", say(Language.KOREAN, Money.khr(25_000)))
        assertEquals("100ドルを受信しました", say(Language.JAPANESE, Money.usd(10_000)))
    }

    @Test
    fun `round ten-thousands use the local scale word`() {
        assertEquals("ទទួលបាន ១០ម៉ឺន រៀល", say(Language.KHMER, Money.khr(100_000)))
        assertEquals("收到 10 万 瑞尔", say(Language.CHINESE, Money.khr(100_000)))
        assertEquals("1만 리엘 입금되었습니다", say(Language.KOREAN, Money.khr(10_000)))
        assertEquals("1万 リエルを受信しました", say(Language.JAPANESE, Money.khr(10_000)))
        assertEquals("1万ドルを受信しました", say(Language.JAPANESE, Money.usd(1_000_000)))
    }

    @Test
    fun `english number words`() {
        assertEquals("zero", EnglishNumberWords.toWords(0))
        assertEquals("thirteen", EnglishNumberWords.toWords(13))
        assertEquals("forty", EnglishNumberWords.toWords(40))
        assertEquals("one hundred five", EnglishNumberWords.toWords(105))
        assertEquals("ten thousand", EnglishNumberWords.toWords(10_000))
    }

    @Test
    fun `khmer number words`() {
        assertEquals("ដប់", KhmerNumberWords.toWords(10))
        assertEquals("ដប់មួយ", KhmerNumberWords.toWords(11))
        assertEquals("ម្ភៃប្រាំ", KhmerNumberWords.toWords(25))
        assertEquals("មួយរយ", KhmerNumberWords.toWords(100))
        assertEquals("ពីររយហាសិប", KhmerNumberWords.toWords(250))
        assertEquals("មួយពាន់", KhmerNumberWords.toWords(1_000))
        assertEquals("មួយម៉ឺន", KhmerNumberWords.toWords(10_000))
        assertEquals("ពីរម៉ឺនប្រាំពាន់", KhmerNumberWords.toWords(25_000))
        assertEquals("មួយសែន", KhmerNumberWords.toWords(100_000))
        assertEquals("ពីរសែនប្រាំម៉ឺន", KhmerNumberWords.toWords(250_000))
        assertEquals("ប្រាំបួនសែនប្រាំបួនម៉ឺនប្រាំបួនពាន់ប្រាំបួនរយកៅសិបប្រាំបួន", KhmerNumberWords.toWords(999_999))
        assertEquals("ម្ភៃប្រាំលាន", KhmerNumberWords.toWords(25_000_000))
        assertEquals("មួយរយហាសិបលាន", KhmerNumberWords.toWords(150_000_000))
        assertEquals("បួនពាន់លាន", KhmerNumberWords.toWords(4_000_000_000))
        assertEquals("មួយលានមួយ", KhmerNumberWords.toWords(1_000_001))
        assertEquals("មួយពាន់ពីររយសាមសិបបួន", KhmerNumberWords.toWords(1_234))
        assertEquals("មួយលាន", KhmerNumberWords.toWords(1_000_000))
    }
}
