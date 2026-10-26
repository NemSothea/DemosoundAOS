"""API tests. Azure is replaced by an httpx.MockTransport *in tests only*;
the shipped server always calls the real Azure endpoint."""
import httpx
import pytest
from fastapi.testclient import TestClient

from app.config import DEFAULT_VOICES, Settings
from app.main import create_app

VOICE_LIST = [
    {"ShortName": "en-US-JennyNeural", "Locale": "en-US", "Gender": "Female"},
    {"ShortName": "en-US-GuyNeural", "Locale": "en-US", "Gender": "Male"},
    {"ShortName": "km-KH-SreymomNeural", "Locale": "km-KH", "Gender": "Female"},
    {"ShortName": "km-KH-PisethNeural", "Locale": "km-KH", "Gender": "Male"},
]
SECRET = "test-secret-key-123"


class FakeAzure:
    def __init__(self, voices=None, tts_status=200):
        self.voices = VOICE_LIST if voices is None else voices
        self.tts_status = tts_status
        self.ssml = []

    def __call__(self, request: httpx.Request) -> httpx.Response:
        assert request.headers["Ocp-Apim-Subscription-Key"] == SECRET
        if request.url.path.endswith("/voices/list"):
            return httpx.Response(200, json=self.voices)
        self.ssml.append(request.content.decode())
        assert request.headers["X-Microsoft-OutputFormat"] == "audio-24khz-48kbitrate-mono-mp3"
        if self.tts_status != 200:
            return httpx.Response(self.tts_status)
        return httpx.Response(200, content=b"ID3-mp3-bytes", headers={"Content-Type": "audio/mpeg"})


def make_client(azure=None, configured=True, rate_limit=100, handler=None):
    settings = Settings(
        azure_key=SECRET if configured else None,
        azure_region="southeastasia" if configured else None,
        voices=dict(DEFAULT_VOICES),
        rate_limit_per_minute=rate_limit,
    )
    transport = httpx.MockTransport(handler or azure or FakeAzure())
    return TestClient(create_app(settings, transport=transport))


VALID = {"language": "en-US", "gender": "female", "amount": "5.75"}


def test_health_does_not_reveal_secrets():
    with make_client() as client:
        r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "ok", "azure_configured": True}
    assert SECRET not in r.text


def test_generate_returns_mp3_built_from_server_text():
    azure = FakeAzure()
    with make_client(azure) as client:
        r = client.post("/api/speech/generate", json=VALID)
    assert r.status_code == 200
    assert r.headers["content-type"] == "audio/mpeg"
    assert r.content == b"ID3-mp3-bytes"
    assert "en-US-JennyNeural" in azure.ssml[0]
    assert "You have received five dollars and seventy-five cents." in azure.ssml[0]


def test_generate_khmer_male_uses_khmer_voice():
    azure = FakeAzure()
    with make_client(azure) as client:
        r = client.post("/api/speech/generate", json={"language": "km-KH", "gender": "male", "amount": "1"})
    assert r.status_code == 200
    assert "km-KH-PisethNeural" in azure.ssml[0]


@pytest.mark.parametrize("body", [
    {"language": "fr-FR", "gender": "female", "amount": "5"},
    {"language": "en-US", "gender": "other", "amount": "5"},
    {"language": "en-US", "gender": "female", "amount": "-5"},
    {"language": "en-US", "gender": "female", "amount": "5.755"},
    {"language": "en-US", "gender": "female", "amount": "abc"},
    {"language": "en-US", "gender": "female", "amount": 5.75},
    {"language": "en-US", "gender": "female"},
    {"language": "en-US", "gender": "female", "amount": "5", "text": "say anything"},
    {"language": "en-US", "gender": "female", "amount": "5", "voice": "en-US-AriaNeural"},
    {"language": "en-US", "gender": "female", "currency": "EUR", "amount": "5"},
    {"language": "en-US", "gender": "female", "currency": "KHR", "amount": "100.5"},
])
def test_rejects_invalid_requests(body):
    azure = FakeAzure()
    with make_client(azure) as client:
        r = client.post("/api/speech/generate", json=body)
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "invalid_request"
    assert azure.ssml == []  # nothing billable happened


def test_validation_error_does_not_echo_values():
    with make_client() as client:
        r = client.post("/api/speech/generate", json={**VALID, "amount": "123456789.123"})
    assert "123456789" not in r.text


def test_rejects_oversized_body():
    with make_client() as client:
        r = client.post("/api/speech/generate", content=b"{" + b" " * 5000 + b"}",
                        headers={"Content-Type": "application/json"})
    assert r.status_code == 413


def test_missing_azure_configuration():
    with make_client(configured=False) as client:
        r = client.post("/api/speech/generate", json=VALID)
        health = client.get("/health").json()
    assert r.status_code == 503
    assert r.json()["error"]["code"] == "azure_not_configured"
    assert health["azure_configured"] is False


def test_voice_missing_from_azure_region():
    azure = FakeAzure(voices=[v for v in VOICE_LIST if not v["Locale"].startswith("km")])
    with make_client(azure) as client:
        r = client.post("/api/speech/generate", json={"language": "km-KH", "gender": "female", "amount": "1"})
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "voice_unavailable"
    assert azure.ssml == []


def test_voice_with_wrong_gender_is_rejected():
    swapped = [dict(v, Gender="Male") if v["ShortName"] == "en-US-JennyNeural" else v for v in VOICE_LIST]
    with make_client(FakeAzure(voices=swapped)) as client:
        r = client.post("/api/speech/generate", json=VALID)
    assert r.json()["error"]["code"] == "voice_unavailable"


def test_azure_auth_failure():
    with make_client(FakeAzure(tts_status=401)) as client:
        r = client.post("/api/speech/generate", json=VALID)
    assert r.status_code == 502
    assert r.json()["error"]["code"] == "azure_auth_failed"
    assert SECRET not in r.text


def test_azure_timeout():
    def handler(request):
        raise httpx.ReadTimeout("slow", request=request)
    with make_client(handler=handler) as client:
        r = client.post("/api/speech/generate", json=VALID)
    assert r.status_code == 504
    assert r.json()["error"]["code"] == "azure_timeout"


def test_azure_unreachable():
    def handler(request):
        raise httpx.ConnectError("down", request=request)
    with make_client(handler=handler) as client:
        r = client.post("/api/speech/generate", json=VALID)
    assert r.status_code == 502
    assert r.json()["error"]["code"] == "azure_unreachable"


def test_rate_limit():
    with make_client(rate_limit=2) as client:
        codes = [client.post("/api/speech/generate", json=VALID).status_code for _ in range(3)]
    assert codes == [200, 200, 429]


def test_voices_endpoint_reports_availability():
    azure = FakeAzure(voices=VOICE_LIST[:2])
    with make_client(azure) as client:
        r = client.get("/api/speech/voices")
    voices = {(v["language"], v["gender"]): v["available"] for v in r.json()["voices"]}
    assert voices[("en-US", "female")] is True
    assert voices[("km-KH", "male")] is False


def test_generate_riel_in_khmer():
    azure = FakeAzure()
    with make_client(azure) as client:
        r = client.post("/api/speech/generate",
                        json={"language": "km-KH", "gender": "female", "currency": "KHR", "amount": "10000"})
    assert r.status_code == 200
    assert "អ្នកបានទទួលប្រាក់ចំនួន មួយម៉ឺន រៀល។" in azure.ssml[0]


def test_currency_defaults_to_usd():
    azure = FakeAzure()
    with make_client(azure) as client:
        client.post("/api/speech/generate", json=VALID)
    assert "five dollars and seventy-five cents" in azure.ssml[0]
