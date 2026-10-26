# Receive Money Sound Demo (Android)

A small Android demo that speaks an incoming-money announcement, for a bank-style review of
"money received" sounds. No account, no network, no cost. Two voice sources:

* **Custom recordings** – one short WAV clip per word (digits, scale words, currency, phrase),
  bundled in the APK and joined at play time into the sentence. This is where a real recorded
  voice goes. The clips shipped today are **stand-ins** generated with neural voices by
  `tools/gen_clips.py` (section 6); see `docs/RECORDING_SCRIPT.md` for the list to record.
* **FreeSDK/Native** – the device's built-in text-to-speech engines, used automatically when a
  recording is missing, or when pinned in the Voice source dropdown.

The demo plays one of two **fixed amounts**. There is no amount input.

| Currency | Amount announced |
|----------|------------------|
| USD      | $5               |
| KHR      | ៛20,000          |

The spoken sentence follows the BU string pattern:

| Language | Formula | USD | KHR |
|----------|---------|-----|-----|
| Khmer    | ទទួលបាន {amount} {currency}         | ទទួលបាន ៥ ដុល្លារ   | ទទួលបាន ២ម៉ឺន រៀល      |
| English  | Received {amount} {currency}       | Received 5 Dollars | Received 20,000 Riel |
| Chinese  | 收到 {amount} {currency}            | 收到 5 美金          | 收到 2 万 瑞尔          |
| Korean   | {amount} {currency} 입금되었습니다    | 5달러 입금되었습니다   | 2만 리엘 입금되었습니다   |
| Japanese | {amount} {currency} を受信しました   | 5ドルを受信しました   | 2万 リエルを受信しました  |

This is a demo only. It does not connect to any bank, payment provider or transaction system.

---

## 1. Screen

Top to bottom, matching the BU review wireframe (Free tab):

1. **FREE** header.
2. **Voice source / SDK name** – `Automatic` (custom recordings, else FreeSDK/Native),
   `Custom recordings`, or one of the installed TTS engines (e.g. Speech Services by Google,
   Samsung TTS).
3. **Language** – Khmer, English, Korean, Japanese, Chinese.
4. **Voice gender** – Female | Male.
5. **Amount** – the fixed amount for the selected currency, with a USD | KHR switch.
6. **Play sound** – Play button, progress and status text.

## 2. Project layout

```
app/                                   Android app (Kotlin, XML + ViewBinding, MVVM)
  src/main/java/com/example/receivemoney/
    MainActivity.kt                    Renders state, forwards user input to the ViewModel
    ui/ReceiveMoneyViewModel.kt        Selection state, Play/stop logic, voice warm-up, error mapping
    ui/ReceiveMoneyUiState.kt          Immutable UI state + PlaybackStatus
    domain/SpeechOptions.kt            Language (5), Gender
    domain/Amount.kt                   Currency (USD/KHR), Money (integer minor units), PresetAmounts
    domain/MoneyAnnouncement.kt        Builds the spoken sentence as text (pattern table above), for FreeSDK/Native
    domain/VoiceClipCatalog.kt         The word list per language: token id -> text (= clip file names)
    domain/AnnouncementTokens.kt       Builds the sentence as a token list, for the custom recordings
    domain/AmountFormatter.kt          English / Khmer number-to-words (reference; not used at runtime)
    audio/PcmClipJoiner.kt             WAV reader + joiner (pure Kotlin)
    speech/SpeechEngine.kt             Engine interface, voice sources, request/outcome/error types
    speech/CompositeSpeechEngine.kt    Routes to recordings or the device engine
    speech/ClipEngine.kt               Plays joined clips from assets/voice via AudioTrack
    speech/NativeTtsEngine.kt          Android TextToSpeech, multi-engine, pinnable source
    speech/PlaceholderClipGenerator.kt Developer tool: synthesizes placeholder clips with the phone's TTS
    speech/VoiceGenderMatcher.kt       Determines a TTS voice's gender (labels, Samsung, curated Google list)
  src/main/assets/voice/<tag>/<gender>/<token>.wav   Voice clips (generated stand-ins today)
  src/main/res/layout/activity_main.xml
  src/main/res/values-w360dp/styles.xml  Side-by-side label/control rows on ≥360dp screens
  src/test/…                           JVM unit tests
docs/PROJECT_GUIDE.md                  Full guide: architecture, clip pipeline, volume, what was tried, open items
docs/MANUAL_TEST_CHECKLIST.md
docs/RECORDING_SCRIPT.md               Every clip to record, per language and gender
tools/gen_clips.py                     Generates all clips from the recording script with edge-tts neural voices
```

Left over from an earlier Paid (Azure) mode and no longer referenced by the app:
`speech/AzureSpeechEngine.kt`, `data/AzureSpeechRepository.kt`, `data/SpeechApi.kt`,
`audio/AudioPlayer.kt`, the `AZURE_*` build config fields in `app/build.gradle.kts`, and `backend/`.
They can be deleted.

## 3. Requirements

* Android Studio (Ladybug or newer) **or** command-line SDK with platform 35, JDK 17+.
* Android device or emulator running Android 8.0 (API 26) or newer.
* A text-to-speech engine with voices for the languages you want to hear. **Speech Services by
  Google** covers all five; Samsung TTS covers English, Korean, Japanese and Chinese but not Khmer.
  Missing voices are reported on screen with a pointer to Settings › Text-to-speech.

## 4. Run the app

```bash
# Android Studio: File › Open… › this folder, then Run 'app'.
# Or from the command line:
./gradlew installDebug
```

If `local.properties` is missing, Android Studio creates it. On the command line, create it with
`sdk.dir=/path/to/Android/sdk`.

## 5. Tests

```bash
./gradlew :app:testDebugUnitTest
```

* `MoneyAnnouncementTest` – one assertion per cell of the pattern table, plus non-preset amounts.
* `AnnouncementTokensTest` – the same cells as clip tokens, number rules for each language
  (Chinese 两/零, Korean dropped 일, Japanese irregular readings), and a check that every token the
  tokenizer can emit has a catalog entry.
* `PcmClipJoinerTest`, `CompositeSpeechEngineTest` – WAV parsing/joining and source routing.
* `ReceiveMoneyViewModelTest` – presets, source pinning, play/stop, error and fallback notices,
  saved-state restore, with a fake engine.
* `VoiceGenderMatcherTest`, `AmountParserTest`, `AzureSpeechRepositoryTest` – unchanged helpers.

Manual device checks: `docs/MANUAL_TEST_CHECKLIST.md`.

## 6. Replacing the placeholder clips

1. Record the words in `docs/RECORDING_SCRIPT.md` (16-bit PCM WAV, mono).
2. Drop them into `app/src/main/assets/voice/<language tag>/<female|male>/` with the given file names.
3. Rebuild. Nothing else changes.

The bundled clips were generated with `tools/gen_clips.py`, which speaks every row of
`docs/RECORDING_SCRIPT.md` with a Microsoft neural voice (`edge-tts`, no API key) and converts the
result with ffmpeg. The app stays offline; the script runs once on a dev machine. To regenerate:
```bash
python3 -m venv .venv && .venv/bin/pip install -r tools/requirements.txt   # needs ffmpeg on PATH
.venv/bin/python tools/gen_clips.py                 # all languages; --lang km-KH --gender female for one set
```
`edge-tts` uses the Edge "Read aloud" service, which is fine for a demo but not licensed for a
product voice; real recordings (or a paid TTS licence) are still the goal.

`PlaceholderClipGenerator` is the older, lower-quality route: it synthesizes with the phone's own
engine (`--ez generate_placeholder_clips true` on launch, then pull `files/voice` with
`adb exec-out run-as`). Clips in the app's `files/voice` folder override the bundled ones until the
app data is cleared.

## 7. Implementation decisions

* **Custom recordings are stitched, not whole sentences.** A sentence is a token list
  (`AnnouncementTokens`), one clip per token, joined with a 60 ms gap and played through one
  `AudioTrack`. Any amount works once the word set exists. Number rules: English and Khmer by
  their number words; Chinese / Korean / Japanese by the 万 system with each language's quirks.
* **Automatic source** uses the recordings when every clip for the request exists and otherwise the
  device engine, telling the user. Pinning "Custom recordings" turns a missing clip into an error.

* **Fixed amounts.** `PresetAmounts.forCurrency` is the only source of the amount. The UI shows it in
  a read-only box; `AmountParser` remains in the code base but nothing calls it.
* **Digits, not number words.** The pattern table writes amounts as digits ("5", "20,000") and uses
  the local ten-thousand word for round amounts (Khmer ២ម៉ឺន, Chinese 2 万, Korean 2만, Japanese
  2万). Khmer uses Khmer numerals. `MoneyAnnouncement` stays a pure function of (language, amount).
* **Voice source.** `NativeTtsEngine` binds every installed engine at start-up. In automatic mode the
  default engine is tried first and the others in turn; a pinned source restricts speech to that
  engine, and an unsupported language then shows the install hint instead of silently switching.
* **Gender fallback.** When only one gender exists for a language (e.g. the single Khmer voice is
  female), the opposite voice is played with its pitch shifted and the user is told on screen.
* **Lifecycle.** Language, gender, currency and source survive process death through
  `SavedStateHandle`. Leaving the screen stops playback; rotation does not.
