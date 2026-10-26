"""Speech backend for the Receive Money Sound demo.

Run: uvicorn app.main:app --host 0.0.0.0 --port 8000
"""
import logging
from contextlib import asynccontextmanager
from typing import Optional

import httpx
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, Response

from .announcement import announcement_text
from .azure_tts import AzureTts, SpeechServiceError
from .config import GENDERS, LANGUAGES, Settings, load_settings
from .models import GenerateSpeechRequest
from .rate_limit import RateLimiter

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
log = logging.getLogger("speech.api")


def error(status: int, code: str, message: str, headers: Optional[dict] = None) -> JSONResponse:
    return JSONResponse(status_code=status, content={"error": {"code": code, "message": message}}, headers=headers)


def create_app(settings: Optional[Settings] = None, transport: Optional[httpx.AsyncBaseTransport] = None) -> FastAPI:
    settings = settings or load_settings()
    limiter = RateLimiter(settings.rate_limit_per_minute)

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        async with httpx.AsyncClient(transport=transport) as client:
            app.state.tts = AzureTts(settings, client)
            if not settings.azure_configured:
                log.warning("Azure Speech is not configured; Paid mode will return 503 azure_not_configured.")
            yield

    app = FastAPI(title="Receive Money Sound - Speech API", version="1.0.0", lifespan=lifespan)

    @app.middleware("http")
    async def guard_requests(request: Request, call_next):
        if request.method == "POST":
            client_key = request.client.host if request.client else "unknown"
            if not limiter.allow(client_key):
                return error(429, "rate_limited", "Too many requests. Try again in a minute.", {"Retry-After": "60"})
            length = request.headers.get("content-length")
            if length is None:
                return error(411, "invalid_request", "Content-Length is required.")
            if not length.isdigit() or int(length) > settings.max_body_bytes:
                return error(413, "payload_too_large", "Request body is too large.")
        return await call_next(request)

    @app.exception_handler(RequestValidationError)
    async def on_validation_error(request: Request, exc: RequestValidationError):
        # Report which fields are wrong without echoing submitted values back.
        fields = sorted({".".join(str(p) for p in e["loc"][1:]) or "body" for e in exc.errors()})
        return error(422, "invalid_request", "Invalid field(s): {}".format(", ".join(fields)))

    @app.get("/health")
    async def health():
        return {"status": "ok", "azure_configured": settings.azure_configured}

    @app.get("/api/speech/voices")
    async def voices(request: Request):
        """Shows, per language/gender, the configured voice and whether Azure offers it in this region."""
        tts: AzureTts = request.app.state.tts
        try:
            available = await tts.available_voices()
        except SpeechServiceError as e:
            return error(e.status, e.code, e.message)
        result = []
        for language in LANGUAGES:
            for gender in GENDERS:
                name = settings.voices.get((language, gender))
                meta = available.get(name or "")
                ok = bool(meta) and meta.get("Locale", "").lower() == language.lower() \
                    and meta.get("Gender", "").lower() == gender
                result.append({"language": language, "gender": gender, "voice": name, "available": ok})
        return {"region": settings.azure_region, "voices": result}

    @app.post(
        "/api/speech/generate",
        responses={200: {"content": {"audio/mpeg": {}}, "description": "MP3, 24 kHz mono, 48 kbit/s"}},
    )
    async def generate(body: GenerateSpeechRequest, request: Request):
        tts: AzureTts = request.app.state.tts
        try:
            voice = await tts.resolve_voice(body.language, body.gender)
            audio = await tts.synthesize(body.language, voice, announcement_text(body.language, body.amount_minor_units, body.currency))
        except SpeechServiceError as e:
            # Amount and credentials are deliberately not logged.
            log.info("generate failed language=%s currency=%s gender=%s code=%s",
                     body.language, body.currency, body.gender, e.code)
            return error(e.status, e.code, e.message)

        log.info("generate ok language=%s currency=%s voice=%s bytes=%d", body.language, body.currency, voice, len(audio))
        return Response(content=audio, media_type=settings.media_type, headers={"Cache-Control": "no-store"})

    return app


app = create_app()
