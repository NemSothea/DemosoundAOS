package com.example.receivemoney.domain

/**
 * Builds the sentence that is spoken when money is received.
 * The backend has an equivalent implementation (backend/app/announcement.py)
 * so Paid mode never accepts free-form text from the client.
 */
object MoneyAnnouncement {

    fun text(language: Language, amount: Money): String = when (language) {
        Language.ENGLISH -> english(amount)
        Language.KHMER -> khmer(amount)
    }

    private fun english(amount: Money): String {
        val parts = mutableListOf<String>()
        when (amount.currency) {
            Currency.USD -> {
                if (amount.major > 0) parts += englishUnit(amount.major, "dollar", "dollars")
                if (amount.minor > 0) parts += englishUnit(amount.minor.toLong(), "cent", "cents")
            }
            Currency.KHR -> parts += englishUnit(amount.major, "riel", "riels")
        }
        return "You have received ${parts.joinToString(" and ")}."
    }

    private fun englishUnit(n: Long, singular: String, plural: String) =
        "${EnglishNumberWords.toWords(n)} ${if (n == 1L) singular else plural}"

    private fun khmer(amount: Money): String {
        val parts = mutableListOf<String>()
        when (amount.currency) {
            Currency.USD -> {
                if (amount.major > 0) parts += "${KhmerNumberWords.toWords(amount.major)} ដុល្លារ"
                if (amount.minor > 0) parts += "${KhmerNumberWords.toWords(amount.minor.toLong())} សេន"
            }
            Currency.KHR -> parts += "${KhmerNumberWords.toWords(amount.major)} រៀល"
        }
        // "You have received the amount of ..."
        return "អ្នកបានទទួលប្រាក់ចំនួន ${parts.joinToString(" និង ")}។"
    }
}
