#!/usr/bin/env python3
"""Generate the per-word voice clips with Microsoft neural voices (edge-tts).

Reads the word list from docs/RECORDING_SCRIPT.md (the human copy of VoiceClipCatalog), speaks
every word once per language and gender, and writes 16-bit mono 24 kHz WAVs to
app/src/main/assets/voice/<tag>/<gender>/<token>.wav, the layout ClipEngine reads.

Each word is spoken inside a short carrier sentence that puts it where it sits in a real
announcement ("Received five Dollars", "ទទួលបាន ពីរ រយ ដុល្លារ", ...) and cut out with the word
timestamps the service returns, so a clip keeps its mid-sentence tone instead of the falling
"end of sentence" tone a word spoken alone gets. --isolated turns this off.

Run once on a dev machine; the app stays offline. Requires ffmpeg on PATH and `pip install edge-tts`
(see tools/requirements.txt). These clips are stand-ins until real recordings exist: edge-tts uses
the Edge "Read aloud" service without a licence for product use.

    python3 tools/gen_clips.py                      # everything
    python3 tools/gen_clips.py --lang km-KH --gender female
    python3 tools/gen_clips.py --token 5 --token dollar --dry-run
    python3 tools/gen_clips.py --no-normalize     # keep each voice's own loudness
    python3 tools/gen_clips.py --isolated         # speak each word alone (old behaviour)
"""

import argparse
import array
import asyncio
import math
import re
import subprocess
import sys
import tempfile
import wave
from dataclasses import dataclass
from pathlib import Path

try:
    import edge_tts
except ImportError:
    sys.exit("edge-tts is not installed: pip install -r tools/requirements.txt")

ROOT = Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "docs" / "RECORDING_SCRIPT.md"
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "voice"

VOICES = {
    "km-KH": {"female": "km-KH-SreymomNeural", "male": "km-KH-PisethNeural"},
    "en-US": {"female": "en-US-JennyNeural", "male": "en-US-GuyNeural"},
    "zh-CN": {"female": "zh-CN-XiaoxiaoNeural", "male": "zh-CN-YunxiNeural"},
    "ko-KR": {"female": "ko-KR-SunHiNeural", "male": "ko-KR-InJoonNeural"},
    "ja-JP": {"female": "ja-JP-NanamiNeural", "male": "ja-JP-KeitaNeural"},
}
SAMPLE_RATE = 24000
CONCURRENCY = 4
RETRIES = 3

# Loudness: every clip is scaled so the RMS of its non-silent part hits TARGET_RMS_DBFS, capped so
# no peak exceeds PEAK_DBFS. Neural voices differ by 10-15 dB between words otherwise, and a joined
# sentence then jumps in volume. SILENCE matches PcmClipJoiner.SILENCE_THRESHOLD.
TARGET_RMS_DBFS = -16.0
PEAK_DBFS = -1.0
SILENCE = 300
NORMALIZE = True

# Carrier sentences. Each language lists its slot order and the words that fill the slots the
# token does not occupy; "connector" is the sentence for "and" / "point". Scale words are spoken
# after the language's "two" (two hundred, ពីរ រយ, 两 万, 이 백, に ひゃく) as in a real amount.
CARRIERS = {
    "km-KH": {"order": ("prefix", "num", "cur"), "two": "ពីរ", "num": "ប្រាំ", "cur": "ដុល្លារ",
              "connector": "ទទួលបាន ប្រាំ ដុល្លារ {w} ម្ភៃ សេន"},
    "en-US": {"order": ("prefix", "num", "cur"), "two": "two", "num": "five", "cur": "Dollars",
              "connector": "Received five Dollars {w} twenty Cents"},
    "zh-CN": {"order": ("prefix", "num", "cur"), "two": "两", "num": "五", "cur": "美金",
              "connector": "收到 五 {w} 五 美金"},
    "ko-KR": {"order": ("num", "cur", "suffix"), "two": "이", "num": "오", "cur": "달러",
              "connector": "오 {w} 오 달러 입금되었습니다"},
    "ja-JP": {"order": ("num", "cur", "suffix"), "two": "に", "num": "ご", "cur": "ドル",
              "connector": "ご {w} ご ドル を受信しました"},
}
CURRENCY_TOKENS = {"dollar", "dollars", "cent", "cents", "riel"}
CONNECTOR_TOKENS = {"and", "point"}
SCALE_TOKENS = {"100", "1000", "10000", "100000", "1000000", "100000000", "1000000000"}
ISOLATED = False
CUT_MARGIN_MS = 60   # taken around the word's reported span, never past the neighbouring words
FADE_MS = 5

HEADING = re.compile(r"^## .*assets/voice/([a-z]{2}-[A-Z]{2})/")
ROW = re.compile(r"^\|\s*`([^`]+)\.wav`\s*\|\s*(.+?)\s*\|$")
NOT_LETTER = re.compile(r"[\s\W_]+", re.UNICODE)


@dataclass
class Job:
    tag: str
    gender: str
    token: str
    text: str
    sentence: str | None   # carrier sentence, or None to speak the word alone
    out: Path


def read_script(path: Path) -> dict[str, dict[str, str]]:
    """tag -> {token: text}, in document order."""
    words: dict[str, dict[str, str]] = {}
    tag = None
    for line in path.read_text(encoding="utf-8").splitlines():
        if m := HEADING.match(line):
            tag = m.group(1)
            words[tag] = {}
        elif tag and (m := ROW.match(line)):
            words[tag][m.group(1)] = m.group(2)
    return words


def carrier_for(tag: str, token: str, text: str, words: dict[str, str]) -> str:
    """The sentence in which [token] is spoken; the word is cut out of it afterwards."""
    c = CARRIERS[tag]
    if token in CONNECTOR_TOKENS:
        return c["connector"].format(w=text)
    slots = {"prefix": words.get("prefix", ""), "suffix": words.get("suffix", ""), "num": c["num"], "cur": c["cur"]}
    if token in ("prefix", "suffix"):
        slots[token] = text
    elif token in CURRENCY_TOKENS:
        slots["cur"] = text
    elif token in SCALE_TOKENS:
        slots["num"] = f"{c['two']} {text}"
    elif token == "2b":   # Chinese 两, only used before a scale word
        slots["num"] = f"{text} {words['10000']}"
    else:
        slots["num"] = text
    return " ".join(slots[s] for s in c["order"] if slots[s])


def letters(s: str) -> str:
    return NOT_LETTER.sub("", s).lower()


def find_span(boundaries: list[tuple[float, float, str]], text: str) -> tuple[float, float, float, float] | None:
    """(start, end, previous word's end, next word's start) in seconds for the first run of
    boundaries whose text spells [text]. Boundaries may split or merge words, so runs are tried."""
    want = letters(text)
    for i in range(len(boundaries)):
        joined = ""
        for j in range(i, len(boundaries)):
            joined += letters(boundaries[j][2])
            if joined == want:
                start, end = boundaries[i][0], boundaries[j][0] + boundaries[j][1]
                prev_end = boundaries[i - 1][0] + boundaries[i - 1][1] if i > 0 else 0.0
                next_start = boundaries[j + 1][0] if j + 1 < len(boundaries) else math.inf
                return start, end, prev_end, next_start
            if not want.startswith(joined):
                break
    return None


def read_wav(path: Path) -> tuple[array.array, object]:
    with wave.open(str(path), "rb") as w:
        params = w.getparams()
        samples = array.array("h", w.readframes(w.getnframes()))
    if sys.byteorder == "big":
        samples.byteswap()
    return samples, params


def write_wav(path: Path, samples: array.array, params) -> None:
    if sys.byteorder == "big":
        samples = array.array("h", samples)
        samples.byteswap()
    with wave.open(str(path), "wb") as w:
        w.setparams(params)
        w.setnframes(len(samples))
        w.writeframes(samples.tobytes())


def cut(path: Path, span: tuple[float, float, float, float]) -> None:
    """Keeps the word's span plus a margin, bounded by its neighbours, with faded edges."""
    samples, params = read_wav(path)
    rate = params.framerate
    start, end, prev_end, next_start = span
    a = int(max(start - CUT_MARGIN_MS / 1000, prev_end) * rate)
    b = int(min(end + CUT_MARGIN_MS / 1000, next_start) * rate)
    a, b = max(0, a), min(len(samples), b)
    out = samples[a:b]
    fade = min(FADE_MS * rate // 1000, len(out) // 2)
    for i in range(fade):
        out[i] = int(out[i] * i / fade)
        out[len(out) - 1 - i] = int(out[len(out) - 1 - i] * i / fade)
    write_wav(path, out, params)


def normalize(path: Path) -> float:
    """Scales the 16-bit mono WAV at [path] in place. Returns the gain applied, in dB."""
    samples, params = read_wav(path)
    loud = [s for s in samples if abs(s) > SILENCE]
    if not loud:
        return 0.0
    rms = math.sqrt(sum(s * s for s in loud) / len(loud))
    peak = max(abs(s) for s in samples)
    gain = min(10 ** (TARGET_RMS_DBFS / 20) * 32768 / rms, 10 ** (PEAK_DBFS / 20) * 32767 / peak)
    scaled = array.array("h", (max(-32768, min(32767, round(s * gain))) for s in samples))
    write_wav(path, scaled, params)
    return 20 * math.log10(gain)


async def speak(voice: str, text: str, mp3: Path) -> list[tuple[float, float, str]]:
    """Synthesizes [text] to [mp3]; returns (offset s, duration s, word) per word boundary."""
    for attempt in range(1, RETRIES + 1):
        try:
            audio = bytearray()
            boundaries = []
            async for chunk in edge_tts.Communicate(text, voice, boundary="WordBoundary").stream():
                if chunk["type"] == "audio":
                    audio += chunk["data"]
                elif chunk["type"] == "WordBoundary":
                    boundaries.append((chunk["offset"] / 1e7, chunk["duration"] / 1e7, chunk["text"]))
            if not audio:
                raise RuntimeError("no audio returned")
            mp3.write_bytes(audio)
            return boundaries
        except Exception as e:  # network hiccups; the service rate-limits bursts
            if attempt == RETRIES:
                raise
            print(f"  retry {attempt} for \"{text}\": {e}", file=sys.stderr)
            await asyncio.sleep(2 * attempt)
    return []


def to_wav(mp3: Path, out: Path) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["ffmpeg", "-loglevel", "error", "-y", "-i", str(mp3),
         "-ac", "1", "-ar", str(SAMPLE_RATE), "-sample_fmt", "s16", str(out)],
        check=True,
    )


async def synthesize(job: Job, semaphore: asyncio.Semaphore) -> str:
    """Writes job.out. Returns "carrier" or "isolated" (the latter also when the word could not
    be located in its carrier sentence)."""
    voice = VOICES[job.tag][job.gender]
    with tempfile.NamedTemporaryFile(suffix=".mp3", delete=False) as tmp:
        mp3 = Path(tmp.name)
    try:
        async with semaphore:
            how = "isolated"
            if job.sentence:
                boundaries = await speak(voice, job.sentence, mp3)
                span = find_span(boundaries, job.text)
                if span:
                    to_wav(mp3, job.out)
                    cut(job.out, span)
                    how = "carrier"
                else:
                    print(f"  {job.out.relative_to(ASSETS) if job.out.is_relative_to(ASSETS) else job.out}: "
                          f"\"{job.text}\" not found in boundaries {[b[2] for b in boundaries]}; spoken alone",
                          file=sys.stderr)
            if how == "isolated":
                await speak(voice, job.text, mp3)
                to_wav(mp3, job.out)
        if NORMALIZE:
            normalize(job.out)
        return how
    finally:
        mp3.unlink(missing_ok=True)


async def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--lang", action="append", choices=sorted(VOICES), help="language tag; repeatable (default all)")
    ap.add_argument("--gender", action="append", choices=["female", "male"], help="repeatable (default both)")
    ap.add_argument("--token", action="append", help="only this token id; repeatable")
    ap.add_argument("--out", type=Path, default=ASSETS, help=f"output root (default {ASSETS.relative_to(ROOT)})")
    ap.add_argument("--dry-run", action="store_true", help="list what would be generated")
    ap.add_argument("--no-normalize", action="store_true", help=f"keep the engine's own level (default: RMS {TARGET_RMS_DBFS} dBFS)")
    ap.add_argument("--isolated", action="store_true", help="speak each word alone instead of cutting it from a carrier sentence")
    args = ap.parse_args()
    global NORMALIZE, ISOLATED
    NORMALIZE = not args.no_normalize
    ISOLATED = args.isolated

    words = read_script(SCRIPT)
    missing = sorted(set(VOICES) - set(words))
    if missing:
        sys.exit(f"{SCRIPT.name} has no table for {missing}")

    jobs: list[Job] = []
    for tag in args.lang or sorted(VOICES):
        for gender in args.gender or ("female", "male"):
            for token, text in words[tag].items():
                if args.token and token not in args.token:
                    continue
                sentence = None if ISOLATED else carrier_for(tag, token, text, words[tag])
                jobs.append(Job(tag, gender, token, text, sentence, args.out / tag / gender / f"{token}.wav"))

    for job in jobs:
        print(f"{job.out.relative_to(args.out)}  {VOICES[job.tag][job.gender]}  \"{job.text}\""
              + (f"  in \"{job.sentence}\"" if job.sentence else ""))
    if args.dry_run:
        return 0

    semaphore = asyncio.Semaphore(CONCURRENCY)
    results = await asyncio.gather(*(synthesize(job, semaphore) for job in jobs), return_exceptions=True)
    failed = [(job.out, r) for job, r in zip(jobs, results) if isinstance(r, Exception)]
    for out, err in failed:
        print(f"FAILED {out}: {err}", file=sys.stderr)
    alone = sum(1 for job, r in zip(jobs, results) if r == "isolated" and job.sentence)
    print(f"{len(jobs) - len(failed)} clips written ({alone} spoken alone because the word was not found "
          f"in its carrier), {len(failed)} failed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
