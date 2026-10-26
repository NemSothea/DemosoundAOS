"""Decimal-safe amount parsing and announcement text.

Mirrors app/src/main/java/com/example/receivemoney/domain/ on Android so that Paid mode
only ever synthesises text the server built itself.
"""
import re
from decimal import Decimal

# currency -> (announced decimal places, maximum in minor units)
# Riel sen are not used in practice, so KHR is whole riels only.
CURRENCIES = {
    "USD": (2, 100_000_000),      # $1,000,000.00
    "KHR": (0, 4_000_000_000),    # ៛4,000,000,000
}
_PLAIN_DECIMAL = re.compile(r"^(\d*)(?:\.(\d*))?$")


class AmountError(ValueError):
    pass


def parse_amount(text: str, currency: str = "USD") -> int:
    """Parses a positive amount into integer minor units (cents for USD, riels for KHR)."""
    decimals, max_minor = CURRENCIES[currency]
    text = text.strip()
    if not text:
        raise AmountError("amount is empty")
    if len(text) > 16:
        raise AmountError("amount is too long")
    if text.startswith("-"):
        raise AmountError("amount must be positive")
    match = _PLAIN_DECIMAL.match(text)
    if not match or (not match.group(1) and not match.group(2)):
        raise AmountError("amount must be a plain decimal number such as 5.75")
    value = Decimal("{}.{}".format(match.group(1) or "0", match.group(2) or "0"))
    if value == 0:
        raise AmountError("amount must be greater than zero")
    if -value.normalize().as_tuple().exponent > decimals:
        raise AmountError("amount has more than {} decimal places for {}".format(decimals, currency))
    minor = int(value.scaleb(decimals))
    if minor > max_minor:
        raise AmountError("amount exceeds the {} maximum".format(currency))
    return minor


_EN_ONES = [
    "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
    "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
    "seventeen", "eighteen", "nineteen",
]
_EN_TENS = ["", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"]


def _en_below_hundred(n: int) -> str:
    if n < 20:
        return _EN_ONES[n]
    if n % 10 == 0:
        return _EN_TENS[n // 10]
    return "{}-{}".format(_EN_TENS[n // 10], _EN_ONES[n % 10])


def _en_below_thousand(n: int) -> str:
    parts = []
    if n >= 100:
        parts.append("{} hundred".format(_EN_ONES[n // 100]))
    if n % 100:
        parts.append(_en_below_hundred(n % 100))
    return " ".join(parts)


def english_words(n: int) -> str:
    if n == 0:
        return _EN_ONES[0]
    parts = []
    for scale, name in ((1_000_000_000, "billion"), (1_000_000, "million"), (1_000, "thousand")):
        if n >= scale:
            parts.append("{} {}".format(_en_below_thousand(n // scale), name))
            n %= scale
    if n:
        parts.append(_en_below_thousand(n))
    return " ".join(parts)


_KM_ONES = ["សូន្យ", "មួយ", "ពីរ", "បី", "បួន", "ប្រាំ", "ប្រាំមួយ", "ប្រាំពីរ", "ប្រាំបី", "ប្រាំបួន"]
_KM_TENS = ["", "ដប់", "ម្ភៃ", "សាមសិប", "សែសិប", "ហាសិប", "ហុកសិប", "ចិតសិប", "ប៉ែតសិប", "កៅសិប"]


def _km_below_thousand(n: int) -> str:
    out = ""
    if n >= 100:
        out += _KM_ONES[n // 100] + "រយ"
    rest = n % 100
    if rest >= 10:
        out += _KM_TENS[rest // 10]
    if rest % 10:
        out += _KM_ONES[rest % 10]
    return out


def khmer_words(n: int) -> str:
    if n == 0:
        return _KM_ONES[0]
    out = ""
    if n >= 1_000_000:
        out += khmer_words(n // 1_000_000) + "លាន"
        n %= 1_000_000
    # Below a million each of សែន / ម៉ឺន / ពាន់ takes a single digit.
    for scale, word in ((100_000, "សែន"), (10_000, "ម៉ឺន"), (1_000, "ពាន់")):
        digit, n = divmod(n, scale)
        if digit:
            out += _KM_ONES[digit] + word
    if n:
        out += _km_below_thousand(n)
    return out


def _en_unit(n: int, singular: str, plural: str) -> str:
    return "{} {}".format(english_words(n), singular if n == 1 else plural)


def announcement_text(language: str, minor_units: int, currency: str = "USD") -> str:
    if currency == "USD":
        dollars, cents = divmod(minor_units, 100)
        en = ([_en_unit(dollars, "dollar", "dollars")] if dollars else []) + \
             ([_en_unit(cents, "cent", "cents")] if cents else [])
        km = (["{} ដុល្លារ".format(khmer_words(dollars))] if dollars else []) + \
             (["{} សេន".format(khmer_words(cents))] if cents else [])
    elif currency == "KHR":
        en = [_en_unit(minor_units, "riel", "riels")]
        km = ["{} រៀល".format(khmer_words(minor_units))]
    else:
        raise ValueError("unsupported currency")
    if language == "en-US":
        return "You have received {}.".format(" and ".join(en))
    if language == "km-KH":
        return "អ្នកបានទទួលប្រាក់ចំនួន {}។".format(" និង ".join(km))
    raise ValueError("unsupported language")
