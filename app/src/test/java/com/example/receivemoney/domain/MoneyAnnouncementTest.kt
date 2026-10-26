package com.example.receivemoney.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyAnnouncementTest {

    private fun en(cents: Long) = MoneyAnnouncement.text(Language.ENGLISH, Money.usd(cents))
    private fun km(cents: Long) = MoneyAnnouncement.text(Language.KHMER, Money.usd(cents))

    @Test
    fun `english matches specification example`() {
        assertEquals("You have received five dollars and seventy-five cents.", en(575))
    }

    @Test
    fun `english singular plural and missing parts`() {
        assertEquals("You have received one dollar.", en(100))
        assertEquals("You have received one cent.", en(1))
        assertEquals("You have received fifty cents.", en(50))
        assertEquals("You have received one dollar and one cent.", en(101))
        assertEquals("You have received twenty dollars.", en(2000))
    }

    @Test
    fun `english large numbers`() {
        assertEquals(
            "You have received one thousand two hundred thirty-four dollars and fifty-six cents.",
            en(123_456),
        )
        assertEquals("You have received one million dollars.", en(100_000_000))
        assertEquals(
            "You have received nine hundred ninety-nine thousand nine hundred ninety-nine dollars and ninety-nine cents.",
            en(99_999_999),
        )
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
    fun `khmer announcement`() {
        assertEquals("អ្នកបានទទួលប្រាក់ចំនួន ប្រាំ ដុល្លារ និង ចិតសិបប្រាំ សេន។", km(575))
        assertEquals("អ្នកបានទទួលប្រាក់ចំនួន មួយ ដុល្លារ។", km(100))
        assertEquals("អ្នកបានទទួលប្រាក់ចំនួន ហាសិប សេន។", km(50))
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

    @Test
    fun `riel announcements`() {
        assertEquals("You have received ten thousand riels.", MoneyAnnouncement.text(Language.ENGLISH, Money.khr(10_000)))
        assertEquals("You have received one riel.", MoneyAnnouncement.text(Language.ENGLISH, Money.khr(1)))
        assertEquals("អ្នកបានទទួលប្រាក់ចំនួន មួយម៉ឺន រៀល។", MoneyAnnouncement.text(Language.KHMER, Money.khr(10_000)))
        assertEquals(
            "អ្នកបានទទួលប្រាក់ចំនួន ពីរលានប្រាំសែន រៀល។",
            MoneyAnnouncement.text(Language.KHMER, Money.khr(2_500_000)),
        )
        assertEquals(
            "You have received four billion riels.",
            MoneyAnnouncement.text(Language.ENGLISH, Money.khr(4_000_000_000)),
        )
    }
}
