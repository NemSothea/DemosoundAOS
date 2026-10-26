import pytest

from app.announcement import AmountError, announcement_text, khmer_words, parse_amount


@pytest.mark.parametrize("text,cents", [
    ("5.75", 575), ("5", 500), ("5.", 500), (".5", 50), ("0.01", 1), (" 5.75 ", 575),
    ("5.750", 575), ("10.05", 1005), ("1000000", 100_000_000),
])
def test_parses_valid_amounts(text, cents):
    assert parse_amount(text) == cents


@pytest.mark.parametrize("text", [
    "", " ", ".", "abc", "-5", "0", "0.00", "5.755", "1,000", "1e3", "+5", "1000000.01", "1" * 20, "NaN",
])
def test_rejects_invalid_amounts(text):
    with pytest.raises(AmountError):
        parse_amount(text)


def test_english_matches_android():
    assert announcement_text("en-US", 575) == "You have received five dollars and seventy-five cents."
    assert announcement_text("en-US", 101) == "You have received one dollar and one cent."
    assert announcement_text("en-US", 50) == "You have received fifty cents."
    assert announcement_text("en-US", 123_456) == (
        "You have received one thousand two hundred thirty-four dollars and fifty-six cents."
    )


def test_khmer_matches_android():
    assert announcement_text("km-KH", 575) == "អ្នកបានទទួលប្រាក់ចំនួន ប្រាំ ដុល្លារ និង ចិតសិបប្រាំ សេន។"
    assert khmer_words(10_000) == "មួយម៉ឺន"
    assert khmer_words(25_000) == "ពីរម៉ឺនប្រាំពាន់"
    assert khmer_words(100_000) == "មួយសែន"
    assert khmer_words(250_000) == "ពីរសែនប្រាំម៉ឺន"
    assert khmer_words(150_000_000) == "មួយរយហាសិបលាន"
    assert khmer_words(4_000_000_000) == "បួនពាន់លាន"
    assert khmer_words(1_234) == "មួយពាន់ពីររយសាមសិបបួន"


@pytest.mark.parametrize("text,riels", [("10000", 10_000), ("500.00", 500), ("4000000000", 4_000_000_000)])
def test_parses_riel(text, riels):
    assert parse_amount(text, "KHR") == riels


@pytest.mark.parametrize("text", ["100.5", "0", "-100", "4000000001", "10,000"])
def test_rejects_invalid_riel(text):
    with pytest.raises(AmountError):
        parse_amount(text, "KHR")


def test_riel_text_matches_android():
    assert announcement_text("en-US", 10_000, "KHR") == "You have received ten thousand riels."
    assert announcement_text("en-US", 1, "KHR") == "You have received one riel."
    assert announcement_text("km-KH", 10_000, "KHR") == "អ្នកបានទទួលប្រាក់ចំនួន មួយម៉ឺន រៀល។"
    assert announcement_text("km-KH", 2_500_000, "KHR") == "អ្នកបានទទួលប្រាក់ចំនួន ពីរលានប្រាំសែន រៀល។"
