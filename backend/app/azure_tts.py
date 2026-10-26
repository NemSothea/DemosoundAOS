"""Azure AI Speech text-to-speech via its REST API.

Docs: https://learn.microsoft.com/azure/ai-services/speech-service/rest-text-to-speech
"""
import logging
import time
from typing import Dict, List, Optional
from xml.sax.saxutils import escape, quoteattr

import httpx

from .config import Settings

log = logging.getLogger("speech.azure")

VOICE_LIST_TTL_SECONDS = 3600


class SpeechServiceError(Exception):
    def __init__(self, status: int, code: str, message: str):
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message


class AzureTts:
    def __init__(self, settings: Settings, client: httpx.AsyncClient):
        self.settings = settings
        self.client = client
        self._voices: Optional[Dict[str, dict]] = None
        self._voices_fetched_at = 0.0

    def _headers(self) -> Dict[str, str]:
        return {"Ocp-Apim-Subscription-Key": self.settings.azure_key or "", "User-Agent": "receive-money-demo"}

    def _require_config(self) -> None:
        if not self.settings.azure_configured:
            raise SpeechServiceError(
                503, "azure_not_configured",
                "Azure Speech is not configured. Set AZURE_SPEECH_KEY and AZURE_SPEECH_REGION on the backend.",
            )

    async def _request(self, method: str, url: str, **kwargs) -> httpx.Response:
        try:
            response = await self.client.request(
                method, url, timeout=self.settings.azure_timeout_seconds, **kwargs
            )
        except httpx.TimeoutException:
            raise SpeechServiceError(504, "azure_timeout", "Azure Speech did not respond in time.")
        except httpx.HTTPError as e:
            log.warning("Azure request failed: %s", type(e).__name__)
            raise SpeechServiceError(502, "azure_unreachable", "Could not reach Azure Speech.")
        if response.status_code in (401, 403):
            raise SpeechServiceError(502, "azure_auth_failed", "Azure rejected the configured key or region.")
        if response.status_code == 429:
            raise SpeechServiceError(429, "rate_limited", "Azure Speech is throttling requests.")
        if response.status_code >= 400:
            log.warning("Azure returned HTTP %s", response.status_code)
            raise SpeechServiceError(502, "azure_error", "Azure Speech returned an error.")
        return response

    async def available_voices(self) -> Dict[str, dict]:
        """ShortName -> voice metadata, fetched from Azure and cached."""
        self._require_config()
        fresh = time.monotonic() - self._voices_fetched_at < VOICE_LIST_TTL_SECONDS
        if self._voices is None or not fresh:
            response = await self._request("GET", self.settings.voices_url, headers=self._headers())
            voices: List[dict] = response.json()
            self._voices = {v["ShortName"]: v for v in voices if "ShortName" in v}
            self._voices_fetched_at = time.monotonic()
        return self._voices

    async def resolve_voice(self, language: str, gender: str) -> str:
        """Returns the configured voice only if Azure reports it with the same locale and gender."""
        name = self.settings.voices.get((language, gender))
        voice = (await self.available_voices()).get(name or "")
        if (
            voice is None
            or voice.get("Locale", "").lower() != language.lower()
            or voice.get("Gender", "").lower() != gender
        ):
            raise SpeechServiceError(
                422, "voice_unavailable",
                "No {} {} voice is available in region {}.".format(gender, language, self.settings.azure_region),
            )
        return name

    async def synthesize(self, language: str, voice: str, text: str) -> bytes:
        self._require_config()
        ssml = "<speak version='1.0' xml:lang={lang}><voice name={voice}>{text}</voice></speak>".format(
            lang=quoteattr(language), voice=quoteattr(voice), text=escape(text)
        )
        headers = dict(self._headers())
        headers["Content-Type"] = "application/ssml+xml"
        headers["X-Microsoft-OutputFormat"] = self.settings.output_format
        response = await self._request("POST", self.settings.tts_url, headers=headers, content=ssml.encode("utf-8"))
        if not response.content:
            raise SpeechServiceError(502, "azure_error", "Azure Speech returned empty audio.")
        return response.content
