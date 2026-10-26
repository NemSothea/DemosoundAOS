#!/usr/bin/env python3
"""Generate the per-word voice clips with Microsoft neural voices (edge-tts).

Reads the word list from docs/RECORDING_SCRIPT.md (the human copy of VoiceClipCatalog), speaks
every word once per language and gender, and writes 16-bit mono 24 kHz WAVs to
app/src/main/assets/voice/<tag>/<gender>/<token>.wav, the layout ClipEngine reads.

Run once on a dev machine; the app stays offline. Requires ffmpeg on PATH and `pip install edge-tts`
(see tools/requirements.txt). These clips are stand-ins until real recordings exist: edge-tts uses
the Edge "Read aloud" service without a licence for product use.

    python3 tools/gen_clips.py                      # everything
    python3 tools/gen_clips.py --lang km-KH --gender female
    python3 tools/gen_clips.py --token 5 --token dollar --dry-run
"""

import argparse
import asyncio
import re
import subprocess
import sys
import tempfile
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

HEADING = re.compile(r"^## .*assets/voice/([a-z]{2}-[A-Z]{2})/")
ROW = re.compile(r"^\|\s*`([^`]+)\.wav`\s*\|\s*(.+?)\s*\|$")


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


async def synthesize(voice: str, text: str, out: Path, semaphore: asyncio.Semaphore) -> None:
    with tempfile.NamedTemporaryFile(suffix=".mp3", delete=False) as tmp:
        mp3 = Path(tmp.name)
    try:
        async with semaphore:
            for attempt in range(1, RETRIES + 1):
                try:
                    await edge_tts.Communicate(text, voice).save(str(mp3))
                    break
                except Exception as e:  # network hiccups; the service rate-limits bursts
                    if attempt == RETRIES:
                        raise
                    print(f"  retry {attempt} for {out}: {e}", file=sys.stderr)
                    await asyncio.sleep(2 * attempt)
        out.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(
            ["ffmpeg", "-loglevel", "error", "-y", "-i", str(mp3),
             "-ac", "1", "-ar", str(SAMPLE_RATE), "-sample_fmt", "s16", str(out)],
            check=True,
        )
    finally:
        mp3.unlink(missing_ok=True)


async def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--lang", action="append", choices=sorted(VOICES), help="language tag; repeatable (default all)")
    ap.add_argument("--gender", action="append", choices=["female", "male"], help="repeatable (default both)")
    ap.add_argument("--token", action="append", help="only this token id; repeatable")
    ap.add_argument("--out", type=Path, default=ASSETS, help=f"output root (default {ASSETS.relative_to(ROOT)})")
    ap.add_argument("--dry-run", action="store_true", help="list what would be generated")
    args = ap.parse_args()

    words = read_script(SCRIPT)
    missing = sorted(set(VOICES) - set(words))
    if missing:
        sys.exit(f"{SCRIPT.name} has no table for {missing}")

    jobs: list[tuple[str, str, Path]] = []
    for tag in args.lang or sorted(VOICES):
        for gender in args.gender or ("female", "male"):
            for token, text in words[tag].items():
                if args.token and token not in args.token:
                    continue
                jobs.append((VOICES[tag][gender], text, args.out / tag / gender / f"{token}.wav"))

    for voice, text, out in jobs:
        print(f"{out.relative_to(args.out)}  {voice}  \"{text}\"")
    if args.dry_run:
        return 0

    semaphore = asyncio.Semaphore(CONCURRENCY)
    results = await asyncio.gather(
        *(synthesize(voice, text, out, semaphore) for voice, text, out in jobs), return_exceptions=True
    )
    failed = [(job[2], err) for job, err in zip(jobs, results) if isinstance(err, Exception)]
    for out, err in failed:
        print(f"FAILED {out}: {err}", file=sys.stderr)
    print(f"{len(jobs) - len(failed)} clips written, {len(failed)} failed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
