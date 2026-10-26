"""Server-side configuration read from environment variables. Secrets never leave this process."""
import os
from dataclasses import dataclass, field
from typing import Dict, Optional, Tuple

LANGUAGES = ("en-US", "km-KH")
GENDERS = ("male", "female")

DEFAULT_VOICES: Dict[Tuple[str, str], str] = {
    ("en-US", "female"): "en-US-JennyNeural",
    ("en-US", "male"): "en-US-GuyNeural",
    ("km-KH", "female"): "km-KH-SreymomNeural",
    ("km-KH", "male"): "km-KH-PisethNeural",
}


def _voice_env_name(language: str, gender: str) -> str:
    return "AZURE_VOICE_{}_{}".format(language.replace("-", "_").upper(), gender.upper())


@dataclass(frozen=True)
class Settings:
    azure_key: Optional[str]
    azure_region: Optional[str]
    voices: Dict[Tuple[str, str], str] = field(default_factory=dict)
    rate_limit_per_minute: int = 10
    azure_timeout_seconds: float = 15.0
    max_body_bytes: int = 1024
    output_format: str = "audio-24khz-48kbitrate-mono-mp3"
    media_type: str = "audio/mpeg"

    @property
    def azure_configured(self) -> bool:
        return bool(self.azure_key and self.azure_region)

    @property
    def tts_url(self) -> str:
        return "https://{}.tts.speech.microsoft.com/cognitiveservices/v1".format(self.azure_region)

    @property
    def voices_url(self) -> str:
        return "https://{}.tts.speech.microsoft.com/cognitiveservices/voices/list".format(self.azure_region)

    def __repr__(self) -> str:  # never print the key
        return "Settings(region={!r}, azure_configured={})".format(self.azure_region, self.azure_configured)


def load_settings() -> Settings:
    voices = {
        key: os.environ.get(_voice_env_name(*key), "").strip() or default
        for key, default in DEFAULT_VOICES.items()
    }
    return Settings(
        azure_key=os.environ.get("AZURE_SPEECH_KEY", "").strip() or None,
        azure_region=os.environ.get("AZURE_SPEECH_REGION", "").strip() or None,
        voices=voices,
        rate_limit_per_minute=int(os.environ.get("RATE_LIMIT_PER_MINUTE", "10")),
        azure_timeout_seconds=float(os.environ.get("AZURE_TIMEOUT_SECONDS", "15")),
    )
