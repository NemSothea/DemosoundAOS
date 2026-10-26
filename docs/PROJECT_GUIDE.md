# Receive Money Sound – project guide

Everything about this project in one place: what it is, how it works, how to run and test it,
how the voice clips are made, what has been tried, and what is left to do. The product spec
(screen, sentence patterns, fixed amounts) lives in `README.md`; this guide does not repeat the
pattern table, because `README.md`, `MoneyAnnouncementTest` and `AnnouncementTokensTest` are the
three places that must stay in sync when the pattern changes.

Repository: https://github.com/NemSothea/DemosoundAOS

---

## 1. What the app does

An Android demo for reviewing a "money received" announcement sound. The user picks a voice
source, a language (Khmer, English, Chinese, Korean, Japanese), a voice gender and a currency,
then presses **Play**. The app speaks "Received $5" or "Received ៛20,000" in that language.

Constraints that shape everything else:

* **No network at runtime.** Every sound is produced on the phone from bundled files or the
  phone's own text-to-speech engine.
* **No account, no cost.** No cloud TTS key is needed to build or run the app.
* **Two fixed amounts** (USD $5, KHR ៛20,000). There is no amount input.
* **Demo only.** Nothing talks to a bank or payment system.

## 2. Architecture

Kotlin, XML layouts with ViewBinding, MVVM. `minSdk 26`, `compileSdk`/`targetSdk 35`, JDK 17.

```
MainActivity ──renders──> ReceiveMoneyUiState
     │ user input
     ▼
ReceiveMoneyViewModel ──SpeechRequest(language, gender, amount, source)──> CompositeSpeechEngine
                                                                              │
                              ┌───────────────────────────────────────────────┤
                              ▼                                               ▼
                        ClipEngine                                      NativeTtsEngine
              (custom recordings, offline WAVs)                 (Android TextToSpeech, any engine)
                              │                                               │
            AnnouncementTokens → VoiceClipCatalog                 MoneyAnnouncement (text)
            tokens → assets/voice/<tag>/<gender>/<token>.wav
                              │
                    PcmClipJoiner.trimSilence + join
                              │
                         AudioTrack (USAGE_MEDIA)
```

### 2.1 Layers

| Package | Role | Android imports? |
|---|---|---|
| `domain/` | Amount, languages, sentence as text (`MoneyAnnouncement`) and as clip tokens (`AnnouncementTokens`, `VoiceClipCatalog`) | No – pure Kotlin, JVM-tested |
| `audio/` | `PcmClipJoiner`: WAV reader, silence trimmer, joiner, resampler | No |
| `speech/` | `SpeechEngine` interface, `CompositeSpeechEngine` router, `ClipEngine`, `NativeTtsEngine`, `VoiceGenderMatcher`, `PlaceholderClipGenerator` | Yes |
| `ui/` | `ReceiveMoneyViewModel`, `ReceiveMoneyUiState`, `PlaybackStatus` | ViewModel only |
| `MainActivity` | Binds state to views, forwards taps | Yes |

Dead code from an earlier Azure "Paid" mode (`speech/AzureSpeechEngine.kt`, `data/`,
`audio/AudioPlayer.kt`, `AZURE_*` build config fields, `backend/`) is not referenced by the app
and can be deleted.

### 2.2 Voice sources

The **Voice source / SDK name** dropdown has three kinds of entries:

| Entry | What plays | Missing clip or voice |
|---|---|---|
| **Automatic** | Custom recordings when every clip for the sentence exists; otherwise the phone's TTS, with a notice on screen | Falls back, tells the user |
| **Custom recordings** | Only the bundled clips | Error on screen |
| **FreeSDK/Native: \<engine\>** | That one TTS engine (e.g. Speech Services by Google, Samsung TTS) speaking the whole sentence | Error with a pointer to Settings › Text-to-speech |

Today all 5 languages × 2 genders have a full clip set, so Automatic and Custom recordings
sound identical.

### 2.3 Sentence as tokens

`AnnouncementTokens.tokens(language, amount)` turns an amount into a list of token ids, for
example Khmer $5 → `prefix 5 dollar`, Khmer ៛20,000 → `prefix 2 10000 riel`. Token id equals the
WAV file name (`5.wav`, `dollar.wav`). `VoiceClipCatalog` maps each token to the text to record
or synthesize. Number rules per language (Chinese 两/零, Korean dropped 일, Japanese irregular
readings such as さんびゃく) live in `AnnouncementTokens` and are covered by
`AnnouncementTokensTest`.

### 2.4 Clip playback pipeline

1. `ClipEngine.load` opens `<filesDir>/voice/...` if present (developer override), else the
   asset. Reads the WAV with `PcmClipJoiner.readWav` (16-bit PCM, mono; stereo takes the left
   channel; any sample rate).
2. **Silence trimming** (`PcmClipJoiner.trimSilence`, applied once per clip at load time):
   samples below ±300 (about 1 % of full scale) count as silence; the clip is cut to 20 ms before
   the first loud sample and 60 ms after the last, with a 5 ms fade on each cut edge so there are
   no clicks. An all-silent clip is left unchanged. Without this, each generated word carried
   70–150 ms of lead and 190–430 ms of trail, and sentences sounded choppy.
3. `PcmClipJoiner.join` resamples every clip to the first clip's rate and concatenates them with
   a **60 ms gap** (`ClipEngine.GAP_MS`).
4. One `AudioTrack` in `MODE_STATIC`, `USAGE_MEDIA` / `CONTENT_TYPE_SPEECH`, plays the result.
   Playback is polled until `playbackHeadPosition` reaches the end, then an 80 ms tail.
5. Clips are cached in memory per engine instance; `prepare(language, gender)` warms the cache
   when the selection changes.

### 2.5 Native TTS

`NativeTtsEngine` binds every installed engine at start-up. For a request it picks, in order:
an engine with both genders for the language, then one with the requested gender, then one with
the opposite gender (pitch-shifted, and the user is told), then the engine default. It sets
`USAGE_MEDIA` audio attributes, so it uses the same media volume as the clips.

### 2.6 State

Language, gender, currency and source persist through `SavedStateHandle`, so they survive
process death. Leaving the screen stops playback; rotation does not.

## 3. Voice clips

### 3.1 Layout and format

```
app/src/main/assets/voice/<tag>/<female|male>/<token>.wav
tags: km-KH  en-US  zh-CN  ko-KR  ja-JP
```

16-bit PCM WAV, mono, 24 kHz (any rate works). 262 clips today, about 23 MB. Word counts per
language: Khmer 29, English 39, Chinese 20, Korean 19, Japanese 24.

### 3.2 How the bundled clips were made

`tools/gen_clips.py` reads the word tables in `docs/RECORDING_SCRIPT.md`, speaks every row with a
Microsoft neural voice through the `edge-tts` Python package (no API key), converts with ffmpeg,
and writes the WAVs into the assets folder. It runs once on a developer machine; the app stays
offline.

**Words are cut from carrier sentences, not spoken alone.** A word spoken on its own gets a
falling "end of sentence" tone, and a sentence stitched from such words sounds like a list. So
the script speaks each word inside a short sentence that puts it in its real position, asks
edge-tts for word boundaries (`boundary="WordBoundary"`, start and duration per word), and cuts
the word out with a 60 ms margin bounded by its neighbours and a 5 ms fade:

| Token kind | Carrier (Khmer example) | Cut |
|---|---|---|
| prefix / suffix | ទទួលបាន ប្រាំ ដុល្លារ | the prefix |
| digit or number word | ទទួលបាន **ប្រាំ** ដុល្លារ | the number |
| scale word (100, 1000, 10000, …) | ទទួលបាន ពីរ **រយ** ដុល្លារ | the scale word, after "two" |
| currency word | ទទួលបាន ប្រាំ **រៀល** | the currency |
| and / point | ទទួលបាន ប្រាំ ដុល្លារ **និង** ម្ភៃ សេន | the connector |

Korean and Japanese carriers are `number currency suffix`; Chinese 两 (`2b`) is spoken before 万.
The `CARRIERS` table in the script holds the slot words per language. If a word cannot be
located among the returned boundaries the script falls back to speaking it alone and says so;
on the current word list every clip in all 10 sets is found. `--isolated` restores the old
behaviour. Clips came out 30–40 % shorter than the isolated ones, since mid-sentence words are
spoken faster.

| Language | Female | Male |
|---|---|---|
| Khmer | `km-KH-SreymomNeural` | `km-KH-PisethNeural` |
| English | `en-US-JennyNeural` | `en-US-GuyNeural` |
| Chinese | `zh-CN-XiaoxiaoNeural` | `zh-CN-YunxiNeural` |
| Korean | `ko-KR-SunHiNeural` | `ko-KR-InJoonNeural` |
| Japanese | `ja-JP-NanamiNeural` | `ja-JP-KeitaNeural` |

```bash
python3 -m venv .venv && .venv/bin/pip install -r tools/requirements.txt   # ffmpeg must be on PATH
.venv/bin/python tools/gen_clips.py --dry-run                              # list the jobs
.venv/bin/python tools/gen_clips.py                                        # all 262 clips (~2 min)
.venv/bin/python tools/gen_clips.py --lang km-KH --gender male --token 5   # one clip
```

Running the script again without changing anything produces the same audio; the voices are
deterministic. Regenerate only after changing a voice in the `VOICES` table, a `Say` cell in the
recording script, or adding a token.

**Licence note.** `edge-tts` uses the Edge "Read aloud" service. It is fine for a demo but not a
licensed product voice. The goal remains real recordings or a paid TTS licence.

### 3.3 Replacing clips with real recordings

1. A native speaker records every row of `docs/RECORDING_SCRIPT.md`, once per gender, as 16-bit
   mono WAV (24 kHz recommended), each word on its own with a little silence around it (the app
   trims it).
2. Convert if needed: `afconvert -f WAVE -d LEI16@24000 -c 1 in.wav out.wav` (macOS) or
   `ffmpeg -i in -ac 1 -ar 24000 -sample_fmt s16 out.wav`.
3. Copy into `assets/voice/<tag>/<gender>/` with the exact file names. Rebuild. No code change.
4. Keep the mic distance and level the same across a set, so the joined sentence sounds like one
   take. The project skill `/voice-clips` checks for missing, extra and badly formatted files.

### 3.4 Adding a token

Update together: `VoiceClipCatalog` (all languages that use it), `AnnouncementTokens` if the
tokenizer can emit it, `AnnouncementTokensTest` (its "every emitted token has a catalog entry"
check must pass), a row in `docs/RECORDING_SCRIPT.md`, and a WAV for every language × gender
(or run `gen_clips.py --token <id>`).

### 3.5 Volume

The app has no volume control. What you hear is:

1. **Device media volume** – both `ClipEngine` and `NativeTtsEngine` use `USAGE_MEDIA`, the
   same slider as music. A fresh emulator defaults to 5/15, which sounds quiet.
2. **Loudness baked into each WAV.** `gen_clips.py` normalises every clip: the RMS of the
   non-silent part is scaled to −16 dBFS, capped so no peak exceeds −1 dBFS (`--no-normalize`
   keeps the engine's level). Before this, the Korean, Japanese and Chinese male sets varied by
   10–15 dB between words (Korean `2.wav` at −30 dBFS vs `dollar.wav` at −16 dBFS). After, every
   set sits within about 2 dB (−19 to −16 dBFS); the few words below −16 are peak-limited, since
   a louder gain would clip them.
3. **Native TTS output level** is decided by the engine; `KEY_PARAM_VOLUME` can only lower it.
4. On the emulator, the Mac's output volume and output device as well.

## 4. Build, run, test

```bash
./gradlew :app:testDebugUnitTest      # all JVM unit tests – run after every change
./gradlew :app:assembleDebug          # APK
./gradlew installDebug                # build + install on the connected device
adb shell am start -n com.example.receivemoney/.MainActivity
```

`local.properties` (git-ignored) holds `sdk.dir`. Android Studio creates it; on the command line
write `sdk.dir=/path/to/Android/sdk`.

Project skills for Claude Code: `/run-app` (build, install, launch, screenshot, logcat) and
`/voice-clips` (add, check or regenerate clips).

### 4.1 Unit tests

| Test | Covers |
|---|---|
| `MoneyAnnouncementTest` | One assertion per cell of the pattern table, plus non-preset amounts |
| `AnnouncementTokensTest` | Same cells as token lists; per-language number rules; every emitted token has a catalog entry |
| `PcmClipJoinerTest` | WAV round trip, stereo down-mix, join gap, resampling, silence trimming, edge fades |
| `CompositeSpeechEngineTest` | Routing between recordings and native engine per source |
| `ReceiveMoneyViewModelTest` | Presets, source pinning, play/stop, error and fallback notices, saved-state restore (fake engine) |
| `VoiceGenderMatcherTest`, `AmountParserTest`, `AzureSpeechRepositoryTest` | Unchanged helpers |

### 4.2 Manual checks

Audio cannot be verified from logs. `docs/MANUAL_TEST_CHECKLIST.md` lists the device checks:
each language × gender × currency through each source, the missing-voice hint, fallback notice,
stop on leaving the screen, state after rotation and process death.

Useful logcat lines (`adb logcat | grep -E 'ClipEngine|NativeTtsEngine'`):

```
NativeTtsEngine: prepared engine=default voice=FEMALE/KHMER in 576ms
ClipEngine: playing prefix 5 dollar (1940 ms @ 24000 Hz)
```

`adb shell dumpsys audio | grep AudioTrack` shows the app's track with `state:started` and
`mutedState:none` while a sentence plays.

### 4.3 Emulator notes

* Start one with `emulator -avd <name>`; `adb devices` must list it before `installDebug`.
* Emulator sound goes to the **Mac's current default output device, chosen when the emulator
  starts**. Bluetooth earphones (AirPods) often give silence or crackle. Switch the Mac to its
  built-in speakers, then restart the emulator.
* Raise the media volume: `adb shell cmd media_session volume --stream 3 --set 12`.
* Emulator audio can stutter; judge quality on a real phone or by playing the WAV on the Mac.

## 5. Decisions and why

* **Stitched words, not whole sentences.** One clip per token means any amount works once the
  word set exists, and the recording script stays short (19–39 words per language). The cost is
  a slight list-reading cadence; cutting generated words from carrier sentences (section 3.2)
  removes most of it, and a human speaker should likewise record each word inside a sentence.
* **Trim at load, not in the files.** Recordings and generated clips both carry padding;
  trimming in `ClipEngine` keeps the asset files untouched and makes the gap predictable.
* **Automatic falls back, Custom does not.** Reviewers want to hear the recordings when they
  exist and still hear something when they do not; pinning Custom makes a missing clip visible.
* **Digits in the text patterns, number words in the clips.** `MoneyAnnouncement` (for TTS)
  follows the business-unit string table with digits; `AnnouncementTokens` (for clips) must spell
  the number out because there is a clip per word.
* **Fixed amounts.** `PresetAmounts.forCurrency` is the only amount source; `AmountParser` and
  `AmountFormatter` remain as reference code and are not used at runtime.
* **Gender fallback with pitch shift.** Some engines ship one Khmer voice; shifting pitch keeps
  the demo usable and the notice keeps it honest.
* **Generated clips come from edge-tts, run offline on a dev machine**, because it was the
  clearest voice among the free options tried (section 6) and needs no key.

## 6. What has been tried for voice quality

| Attempt | Result | Status |
|---|---|---|
| Google Android TTS, per word (`PlaceholderClipGenerator`) | Works on any phone, robotic Khmer, 300–500 ms dead air between words | Kept as fallback tool only |
| Silence trimming + edge fades in `PcmClipJoiner` | Sentence 2.9 s → 2.2 s, no clicks, far less choppy | Shipped |
| Microsoft neural voices via `edge-tts`, per word | Clearly more natural than Google TTS; all 10 voices available | **Shipped** (current clips) |
| Loudness normalisation in `gen_clips.py` | Word-to-word spread 15 dB → about 2 dB, no clipping | Shipped |
| Cut words from carrier sentences via edge-tts word boundaries | Mid-sentence tone per word; judged clearly better than isolated words, close to the whole-sentence ceiling | **Shipped** (current clips) |
| Comma-separated sentence split on silence | Khmer voice inserts no pause at commas; could not split | Rejected |
| `edge-tts` whole sentence | Best intonation, but needs network at runtime, so not usable in the app; serves as the quality ceiling | Reference only |
| Zio TTS (open Khmer VITS, 23 voices, offline ONNX, CC BY-SA) | All auditioned voices judged worse than edge-tts | Rejected |
| Meta MMS Khmer, Piper, Kokoro | MMS Khmer is below edge-tts; Piper and Kokoro have no Khmer | Not pursued |

Conclusion: no free offline Khmer model beats edge-tts today. The remaining quality step is a
human recording.

## 7. Open items

1. **Real recordings** for Khmer first (29 words × 2 genders, about 15 minutes with a phone mic).
   Run them through the same normalisation (`normalize()` in `gen_clips.py`) so they match.
2. **Rate/pitch tuning** of edge-tts voices (`--rate`, `--pitch`) if a language sounds too fast.
3. **On-device neural TTS** (sherpa-onnx) for whole-sentence intonation offline. Large change:
   native library plus a 30–100 MB model per language, and no Khmer model that sounds acceptable
   yet. Not planned.
4. **Delete the Azure dead code** when convenient.
5. **Git LFS for `*.wav`** if the repo grows from repeated regeneration.

## 8. Glossary

* **Token** – one spoken unit of a sentence (`prefix`, `5`, `10000`, `dollar`); also the WAV file
  name.
* **Clip** – one WAV for one token, one language and one gender.
* **Source** – where the voice comes from: Automatic, Custom recordings, or a named TTS engine.
* **Placeholder / stand-in** – a generated clip that will be replaced by a human recording.
* **dBFS** – level relative to the loudest possible 16-bit sample; 0 dBFS is full scale.
