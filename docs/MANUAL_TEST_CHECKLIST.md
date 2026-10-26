# Manual test checklist

Record device model, Android version and installed TTS engine (Settings › Accessibility ›
Text-to-speech output) with each run. The expected results below are what the code is written to do.

## Setup
- [ ] For Paid tests: `azureSpeechKey` and `azureSpeechRegion` set in `local.properties`, app rebuilt and installed.

## Free mode — Android native TTS
| # | Steps | Expected |
|---|-------|----------|
| F1 | Free, English, Female, amount `5.75`, Play | Status: Preparing → Speaking → "Announcement played." Hears "You have received five dollars and seventy-five cents." |
| F2 | Same with Male | Male voice, no notice. On a Galaxy Note10 the app picks Google `en-us-x-iom-local` (logcat tag `NativeTtsEngine`); Female picks Samsung `en-US-SMTf00`. ✅ Verified 2026-10-22 |
| F2a | Free, Khmer, Female | Google `km-kh-x-khm` voice, **no** gender notice. |
| F2b | Free, Khmer, Male | Plays the Khmer voice at lowered pitch (clearly male-sounding), with notice "No Male voice for Khmer… the Female voice is played with its pitch adjusted. Paid mode (Azure) has a real Male voice." ✅ Verified 2026-10-23 (measured 146 Hz vs 242 Hz for Female) |
| F3 | Free, Khmer, amount `5.75`, Play, on a device with Speech Services by Google installed (even if Samsung TTS is the default) | Speaks the Khmer sentence via the Google engine. ✅ Verified 2026-10-22 on Galaxy Note10 (Android 12, default Samsung TTS). |
| F4 | Free, Khmer, on a device where **no** installed engine supports Khmer | Red error: "Khmer … is not supported…" or "…voice data is not installed…". No crash, no English fallback. |
| F5 | Disable/uninstall all TTS engines (or use an emulator image without one), Play | Error: no TTS engine available / did not start in time. |
| F6 | Airplane mode, Free, English | Still works if the engine has an offline English voice. |

## Paid mode — Azure neural voices
| # | Steps | Expected |
|---|-------|----------|
| P1 | Paid, English, Female, `5.75`, Play | Progress + "Generating Azure neural speech…", then a neural voice (JennyNeural) speaks the sentence. Logcat (OkHttp) shows one call to `cognitiveservices/v1`. |
| P2 | Paid, English, Male | GuyNeural (or configured) voice. |
| P3 | Paid, Khmer, Female/Male (region with km-KH voices) | SreymomNeural / PisethNeural speak the Khmer sentence. |
| P4 | Set `azureVoiceKmMale=xx-XX-DoesNotExistNeural`, rebuild, Paid Khmer Male | Error "Azure has no Male Khmer voice in this region." No synthesis call. |
| P5 | Remove `azureSpeechKey`, rebuild, Paid, Play | Error "Paid mode is not configured…", no network request. ✅ Verified 2026-10-23 on Galaxy Note10 |
| P6 | Wrong key | Error "Azure rejected the key…". |
| P7 | Airplane mode, Paid, Play | Error "Could not reach Azure Speech…" within ~10 s. Play re-enabled. |
| P8 | Selecting Paid, changing language/gender | No Azure request until Play is pressed. |

## Input validation
| # | Input | Expected |
|---|-------|----------|
| V1 | empty | Play disabled, no error text |
| V2 | `0`, `0.00` | "Amount must be greater than zero", Play disabled |
| V3 | `5.755` | "Use at most 2 decimal places", Play disabled |
| V4 | `.` or `5.7.5` (paste) | "Enter a number such as 5.75" |
| V5 | `1000000.01` | "Maximum is $1,000,000.00" |
| V6 | `.5`, `5.` | Valid: "fifty cents", "five dollars" |
| V7 | `1` / `0.01` | "one dollar" / "one cent" (singular) |

## Currency
| # | Steps | Expected |
|---|-------|----------|
| C1 | Currency KHR, amount `10000`, English, Free, Play | "You have received ten thousand riels." ✅ Verified 2026-10-22 on Galaxy Note10 |
| C2 | Currency KHR, amount `10000`, Khmer, Free, Play | "អ្នកបានទទួលប្រាក់ចំនួន មួយម៉ឺន រៀល។" |
| C2b | KHR `250000` / `1500000`, Khmer | "ពីរសែនប្រាំម៉ឺន រៀល" / "មួយលានប្រាំសែន រៀល" |
| C3 | Currency KHR, amount `100.5` | "Riel amounts must be whole numbers", Play disabled ✅ Verified 2026-10-22 |
| C4 | Type `5.75` in USD, then switch to KHR | Amount error appears immediately; switching back to USD clears it |
| C5 | Currency KHR, `4000000001` | "Maximum is ៛4,000,000,000" |
| C6 | Paid, KHR, Play (with Azure configured) | Neural voice says the riel sentence. |

## Concurrency and lifecycle
| # | Steps | Expected |
|---|-------|----------|
| L1 | Free: tap Play 5× quickly | Only the last preview is heard; no overlapping voices; no crash. |
| L2 | Paid: tap Play rapidly | Play disables while generating; only **one** Azure synthesis request is made. |
| L3 | Play a long amount (`999999.99`) in Free, then tap Paid while speaking | Speech stops immediately; status "Paid mode selected…"; nothing new plays. |
| L4 | Same as L3 in Paid, then tap Free | Azure audio stops immediately. |
| L5 | Play, then rotate the device | Speech continues; selections and amount are kept. |
| L6 | Play, then press Home | Speech stops. Return: status "Playback stopped.", Play works again. |
| L7 | Play, then Back (finish activity) | Speech stops. No "leaked ServiceConnection" in Logcat (TTS shut down in `onCleared`). |
| L8 | Azure throttles (HTTP 429), e.g. many Paid plays quickly on F0 | Error "Too many requests…". |

## Accessibility
| # | Steps | Expected |
|---|-------|----------|
| A1 | TalkBack: swipe through the screen | Title announced as heading. Fields read with their labels. Free/Paid announce checked state. |
| A2 | TalkBack: Play | Status changes are announced (live region). |
| A3 | Display size Largest + Font size Largest, small screen (e.g. 320dp wide) | Labels stack above controls, nothing clipped, screen scrolls. |
| A4 | Dark theme | All text readable. Selected mode clearly filled. |
