---
name: voice-clips
description: Add, replace, check or regenerate the per-word WAV voice clips in app/src/main/assets/voice. Use when the user has new recordings, asks which clips are missing or wrong, wants placeholder clips regenerated from a phone's TTS, or adds a word/token to VoiceClipCatalog.
---

# Voice clips

Layout: `app/src/main/assets/voice/<tag>/<female|male>/<token>.wav`.
Tags: `km-KH`, `en-US`, `zh-CN`, `ko-KR`, `ja-JP`. Token ids and the text for each come from
`domain/VoiceClipCatalog.kt`; `docs/RECORDING_SCRIPT.md` is the human-readable copy.
Format: 16-bit PCM WAV, mono, any sample rate (24 kHz recommended). `PcmClipJoiner` rejects
other formats.

## Check the clip set

1. Read `VoiceClipCatalog.kt` to get the token list per language.
2. For every tag and gender, list files that are missing from disk, and extra files that are not
   in the catalog.
3. Check the format of each file:

   ```bash
   for f in app/src/main/assets/voice/*/*/*.wav; do
     afinfo "$f" | grep -q 'Data format: *1 ch, .*Int16' || echo "BAD FORMAT: $f"
   done
   ```

   (`afinfo` is macOS. Elsewhere use `soxi` or `ffprobe`.)
4. Report missing, extra and bad-format files grouped by tag and gender. A missing clip is not a
   crash: the app falls back to device TTS in Automatic mode, and shows an error when the user
   pins "Custom recordings".

## Add new recordings

1. Get the files from the user and rename them to `<token>.wav`, matching the catalog exactly.
2. If they are not 16-bit mono WAV, convert. Ask before overwriting:
   `afconvert -f WAVE -d LEI16@24000 -c 1 in.wav out.wav` (macOS) or
   `ffmpeg -i in -ac 1 -ar 24000 -sample_fmt s16 out.wav`.
3. Copy into the right `<tag>/<gender>/` folder, run the check above, then
   `./gradlew :app:testDebugUnitTest`.
4. Remind the user that clips in the app's `files/voice` folder on the device override bundled
   ones until app data is cleared (`adb shell pm clear com.example.receivemoney`).

## Add a new token

Update all of these together:
- `VoiceClipCatalog` text maps (all 5 languages that use it)
- `AnnouncementTokens` if the tokenizer can now emit it
- `AnnouncementTokensTest`; its "every emitted token has a catalog entry" check must still pass
- `docs/RECORDING_SCRIPT.md` row for each language
- A WAV per tag and gender, or regenerate placeholders (below)

## Regenerate placeholders with neural voices (preferred)

`tools/gen_clips.py` speaks every row of `docs/RECORDING_SCRIPT.md` with a Microsoft neural voice
through `edge-tts` (network, no key) and converts with ffmpeg. Voice per tag and gender is the
`VOICES` table in the script. It overwrites real recordings, so confirm with the user first if
any clip is a real recording. Needs `ffmpeg` on PATH.

```bash
python3 -m venv <scratchpad>/venv && <scratchpad>/venv/bin/pip install -r tools/requirements.txt
<scratchpad>/venv/bin/python tools/gen_clips.py --dry-run          # list the jobs
<scratchpad>/venv/bin/python tools/gen_clips.py                    # all; or --lang km-KH --gender male --token 5
```

Then run the check above. The script reads the markdown table, so a new token needs its
`RECORDING_SCRIPT.md` row before it can be generated.

## Regenerate placeholders from a phone's TTS (lower quality)

Needs a connected device with voices for all languages (Speech Services by Google). Placeholders
overwrite real recordings, so confirm with the user first if any clip is a real recording.

```bash
adb shell am start -n com.example.receivemoney/.MainActivity --ez generate_placeholder_clips true
# wait until the on-screen progress finishes
adb exec-out run-as com.example.receivemoney tar -cf - files/voice | tar -xf - -C <scratchpad>
cp -R <scratchpad>/files/voice/. app/src/main/assets/voice/
```

Then run the check above.
