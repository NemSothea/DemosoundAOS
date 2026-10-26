# Receive Money Sound Demo (Android)

A small Android demo that speaks an incoming-money announcement such as
*"You have received five dollars and seventy-five cents."* in **English** or **Khmer**, for amounts in
**US dollars (USD)** or **Khmer riel (KHR)**, using either:

| Mode | Engine | Cost | Needs network |
|------|--------|------|---------------|
| **Free** | Android `TextToSpeech` (the engines installed on the device) | none | no (unless the device's engine uses network voices) |
| **Paid** | Azure AI Speech neural voices, called directly from the app | Azure billing per Play (free F0 tier available) | yes |

This is a demo only. It does not connect to any bank, payment provider or transaction system.

> **Demo security note:** Paid mode calls Azure directly, so the Azure key is built into the APK.
> Anyone with the APK can extract it. Use a **free-tier (F0)** Speech resource, don't distribute the
> APK, and **regenerate the key** after the demo. For production, use a server that keeps the key
> (the unused `backend/` folder is a starting point for that).

---

## 1. Project layout

```
app/                                   Android app (Kotlin, XML + ViewBinding, MVVM)
  src/main/java/com/example/receivemoney/
    MainActivity.kt                    Renders state, forwards user input to the ViewModel
    ui/ReceiveMoneyViewModel.kt        Selection state, Play/stop logic, voice warm-up, error mapping
    ui/ReceiveMoneyUiState.kt          Immutable UI state + PlaybackStatus
    domain/SpeechOptions.kt            Language, Gender, SpeechMode
    domain/Amount.kt                   Currency (USD/KHR), Money (integer minor units), AmountParser
    domain/AmountFormatter.kt          English / Khmer number-to-words
    domain/MoneyAnnouncement.kt        Builds the spoken sentence
    speech/SpeechEngine.kt             Engine interface, request/outcome/error types
    speech/NativeTtsEngine.kt          Free mode (Android TextToSpeech, multi-engine)
    speech/VoiceGenderMatcher.kt       Determines a TTS voice's gender (labels, Samsung, curated Google list)
    speech/AzureSpeechEngine.kt        Paid mode (Azure → MP3 → AudioPlayer)
    data/SpeechApi.kt                  Retrofit interface for the Azure TTS REST API
    data/AzureSpeechRepository.kt      Voice validation, SSML, synthesis, error mapping
    audio/AudioPlayer.kt               MediaPlayer wrapper (one clip at a time)
  src/main/res/layout/activity_main.xml
  src/main/res/values-w360dp/styles.xml  Side-by-side label/control rows on ≥360dp screens
  src/test/…                           JVM unit tests
backend/                               Optional FastAPI server (not used by the demo app)
docs/MANUAL_TEST_CHECKLIST.md
```

## 2. Requirements

* Android Studio (Ladybug or newer) **or** command-line SDK with platform 35, JDK 17+.
* Android device or emulator running Android 8.0 (API 26) or newer.
* An Azure Speech resource for Paid mode. **Free mode needs no Azure account.**

## 3. Run the app

```bash
# Android Studio: File › Open… › this folder, then Run 'app'.
# Or from the command line:
./gradlew installDebug
```

If `local.properties` is missing, Android Studio creates it. On the command line, create it with
`sdk.dir=/path/to/Android/sdk`.

## 4. Azure configuration (Paid mode)

1. In the Azure portal create a **Speech** resource (Azure AI services › Speech), pricing tier
   **Free F0**. Pick a region that offers the Khmer neural voices, e.g. `southeastasia`.
2. Open the resource › **Keys and Endpoint**. Copy **Key 1** and the **Location/Region**.
3. Add them to `local.properties` in the project root (git-ignored):
   ```properties
   azureSpeechKey=<Key 1>
   azureSpeechRegion=southeastasia
   ```
4. Optional: change voices.
   ```properties
   azureVoiceEnFemale=en-US-JennyNeural
   azureVoiceEnMale=en-US-GuyNeural
   azureVoiceKmFemale=km-KH-SreymomNeural
   azureVoiceKmMale=km-KH-PisethNeural
   ```
5. Rebuild and install: `./gradlew installDebug`.

How Paid mode works:
* On the first Paid Play, the app downloads Azure's voice list for the region
  (`GET https://<region>.tts.speech.microsoft.com/cognitiveservices/voices/list`) and keeps it
  for the rest of the run.
* The configured voice must exist **and** have the requested locale and gender. Otherwise the app
  shows *"Azure has no … voice in this region"* and makes no billable request.
* The app builds the sentence (same text as Free mode), wraps it in SSML, and requests MP3
  (`audio-24khz-48kbitrate-mono-mp3`), which `MediaPlayer` plays.
* Errors shown to the user:

| Situation | Message |
|---|---|
| No key/region in `local.properties` | "Paid mode is not configured…" (no network request) |
| Azure 401/403 | "Azure rejected the key…" |
| Azure 429 | "Too many requests…" |
| No internet / timeout | "Could not reach Azure Speech…" |
| Voice not in region | "Azure has no Male Khmer voice in this region." |

## 5. Tests

```bash
./gradlew testDebugUnitTest
```

`AzureSpeechRepositoryTest` replaces Azure with a fake API **inside the test suite only**. The app
always calls the real Azure endpoint and has no fake-audio path.

Manual and device tests are listed in [docs/MANUAL_TEST_CHECKLIST.md](docs/MANUAL_TEST_CHECKLIST.md).

## 6. Implementation decisions

* **Money is integers.** `AmountParser` validates with a strict regex, converts with `BigDecimal`,
  and stores `Money` as a `Long` number of minor units (cents for USD, riels for KHR). No `Double`
  is used anywhere between input and spoken text. Inputs with more decimals than the currency
  allows (`5.755` USD, `100.5` KHR) are **rejected**, not rounded; `5.750` is accepted as 5.75.
* **Currencies.**
  * USD: $0.01 to $1,000,000.00, spoken as dollars and cents.
  * KHR: ៛1 to ៛4,000,000,000, whole numbers only, because sen are not used in practice and the
    smallest note is 100 ៛. Spoken as "ten thousand riels" / "មួយម៉ឺន រៀល".
  * Changing the currency re-validates the amount already typed.
  * Empty, zero, negative, malformed (`1,000`, `1e3`, `$5`) and out-of-range inputs are rejected.
    Play stays disabled until the amount is valid.
* **Khmer text.** Khmer numbers are written without spaces (e.g. 75 → ចិតសិបប្រាំ) and use the
  Khmer scale words រយ 100, ពាន់ 1,000, ម៉ឺន 10,000, សែន 100,000, លាន 1,000,000
  (25,000 → ពីរម៉ឺនប្រាំពាន់, 250,000 → ពីរសែនប្រាំម៉ឺន, 4,000,000,000 → បួនពាន់លាន).
  Sentence: *អ្នកបានទទួលប្រាក់ចំនួន … ដុល្លារ និង … សេន។* / *… រៀល។*
  **Have a native speaker review the wording before any real use.**
* **No invented voices (Free mode).** Android's `Voice` API has no gender field.
  `VoiceGenderMatcher` only uses:
  * explicit labels (`…#female_1-local`, `gender=male`);
  * Samsung TTS names (`en-US-SMTf00` = female, `SMTm00` = male);
  * a small **hand-curated** list of Google voice codes: en-US `iol`, `iom`, `tpd` male; `iob`,
    `iog`, `sfg`, `tpc`, `tpf` female; Khmer `khm` female. Google doesn't expose gender, so this
    list was verified by synthesising each voice and measuring its pitch (males 138–141 Hz,
    females 200–226 Hz, `tpc` 164 Hz, Khmer 233 Hz). Verify the same way before extending it.

  Language support is checked with `isLanguageAvailable()` before every utterance. Missing voice
  data and unsupported languages produce specific messages.
* **Engine choice (Free mode).**
  * Engines are tried in order, system default first. For example, Samsung phones default to
    Samsung TTS, which has no Khmer, so Khmer is spoken by Speech Services by Google.
  * An engine with **both** a male and a female voice for the language is preferred, so switching
    gender keeps the same engine and voice style. On a Galaxy Note10, English uses Google
    (`tpf` female, `iom` male).
  * Otherwise the first engine with a voice of the requested gender is used.
  * If no voice of the requested gender exists but one of the opposite gender does, that voice is
    played with `TextToSpeech.setPitch()` shifted into the requested range (female→male 0.6,
    male→female 1.4; measured: the female Khmer voice at 0.6 lands at ~141–146 Hz, the same as
    Google's real male English voice). The user is **told** on screen, with a pointer to Paid mode,
    which has a real male Khmer voice (`km-KH-PisethNeural`).
  * If no gender-labelled voice exists at all, the engine default is used and the user is told.
  * Logcat tag `NativeTtsEngine` shows the engine chosen for each Play.
* **Fast switching (Free mode).**
  * All installed engines are bound when the app starts.
  * Voice lists are read once per engine.
  * Changing Language or Gender calls `prepare()`, which selects the voice and does a silent
    warm-up synthesis to a cache file. No audio is played and Azure is never called.
  * Measured on the Note10: the first Play of a voice starts in ~0.3 s, and later switches in
    ~0.05 s. Before this change the first switch to male took 2.6 s.
* **Playback lifecycle.**
  * Engines are owned by the ViewModel, so playback survives rotation. `TextToSpeech.shutdown()`
    and MediaPlayer release happen in `onCleared()`.
  * Language, gender, currency, mode and amount are kept in `SavedStateHandle`, so they survive
    the activity being recreated.
  * Playback stops when the Activity is stopped (but not during a configuration change).
  * Pressing Play stops and cancels the previous preview first.
  * Switching Free/Paid stops current playback and never starts speech.
  * While Paid audio is being generated, Play is disabled, so rapid taps cannot create duplicate
    billable requests. Once audio is playing, Play is enabled again and restarts the preview.
* **Dependencies kept small.** Retrofit without a JSON converter calls Azure's REST API (SSML in,
  MP3 out). The voice list is parsed with the platform's `org.json`. No Azure SDK.
* **Network.** All traffic is HTTPS; cleartext is disabled in every build type.
* **Layout.** A single layout. Row orientation comes from styles: label beside control on screens
  ≥ 360dp wide (`values-w360dp`), label above control on narrower ones. Labels use `labelFor`,
  the status text is a polite live region, and touch targets are ≥ 48dp.
