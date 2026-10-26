# CLAUDE.md

Android demo (Kotlin, XML + ViewBinding, MVVM) that speaks a "money received" announcement for
two fixed amounts (USD $5, KHR ៛20,000) in Khmer, English, Chinese, Korean and Japanese. No
network at runtime. Full spec and decisions: `README.md`.

## Commands

```bash
./gradlew :app:testDebugUnitTest                 # all JVM unit tests (run after every change)
./gradlew :app:testDebugUnitTest --tests '*AnnouncementTokensTest'   # one class
./gradlew :app:assembleDebug                     # build APK
./gradlew installDebug                           # build + install on connected device
adb shell am start -n com.example.receivemoney/.MainActivity        # launch
```

JDK 17, compileSdk/targetSdk 35, minSdk 26. `local.properties` holds `sdk.dir` (git-ignored).
Project skills: `/run-app` (build, install, launch, logcat), `/voice-clips` (add or check clips).

## Architecture

`app/src/main/java/com/example/receivemoney/`

- `MainActivity` renders `ReceiveMoneyUiState`, forwards input to `ui/ReceiveMoneyViewModel`.
- `domain/` is pure Kotlin, no Android imports. Keep it that way so it stays JVM-testable.
  - `MoneyAnnouncement`: sentence as text (for device TTS).
  - `AnnouncementTokens` + `VoiceClipCatalog`: sentence as clip tokens (for recordings).
    Token id == WAV file name.
  - `Amount` / `PresetAmounts.forCurrency`: only source of the amount. Money is integer minor units.
- `speech/`: `SpeechEngine` interface. `CompositeSpeechEngine` routes to `ClipEngine`
  (joined WAVs via `AudioTrack`) or `NativeTtsEngine` (Android `TextToSpeech`, multi-engine).
- `audio/PcmClipJoiner`: pure-Kotlin WAV reader/joiner (16-bit PCM mono, 60 ms gap).
- Clips: `app/src/main/assets/voice/<lang tag>/<female|male>/<token>.wav`. Today they are
  stand-ins made by `tools/gen_clips.py` (edge-tts neural voices, run on a dev machine), not
  real recordings. `ClipEngine` trims each clip's silence on load (`PcmClipJoiner.trimSilence`).

## Rules

- When the spoken pattern changes, update the pattern table in `README.md`,
  `MoneyAnnouncementTest` and `AnnouncementTokensTest` together. One assertion per table cell.
- A new token in `VoiceClipCatalog` needs a WAV for all 5 languages × 2 genders (or a deliberate
  fallback to device TTS) and a row in `docs/RECORDING_SCRIPT.md`.
- Language, gender, currency and source persist through `SavedStateHandle`. New selection state
  must too.
- Dead code from old Azure Paid mode: `speech/AzureSpeechEngine.kt`, `data/`, `audio/AudioPlayer.kt`,
  `AZURE_*` buildConfig fields, `backend/`. Don't extend it; deleting it is fine if asked.
- Device-only behavior (TTS voices, audio) is checked by hand: `docs/MANUAL_TEST_CHECKLIST.md`.
