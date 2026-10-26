# Manual test checklist

Record device model, Android version and installed TTS engines (Settings › Accessibility ›
Text-to-speech output) with each run. The expected results below are what the code is written to do.

Everything uses FreeSDK/Native (the device's text-to-speech). There is no paid mode and no amount
input: USD always announces $5, KHR always announces ៛20,000.

## Screen
| # | Steps | Expected |
|---|-------|----------|
| S1 | Open the app | Header "FREE" with mic icon. Rows: Voice source / SDK name, Language, Voice gender, Amount, Play sound, status card. No tabs, no Paid, no text field. |
| S2 | Defaults | Source "Automatic (recordings, else FreeSDK/Native)", Language Khmer, Female checked, Amount "$5", USD checked, status "Press Play to hear the announcement." |
| S3 | Tap KHR | Amount box shows "៛20,000". Nothing plays. |
| S4 | Open the source dropdown | "Automatic…" first, then "Custom recordings", then one "FreeSDK/Native: <engine>" row per installed engine. |

## Spoken text (Female, automatic source, device with Speech Services by Google)
| # | Language | USD ($5) | KHR (៛20,000) |
|---|----------|----------|---------------|
| T1 | Khmer    | ទទួលបាន ៥ ដុល្លារ | ទទួលបាន ២ម៉ឺន រៀល |
| T2 | English  | Received 5 Dollars | Received 20,000 Riel |
| T3 | Korean   | 5달러 입금되었습니다 | 2만 리엘 입금되었습니다 |
| T4 | Japanese | 5ドルを受信しました | 2万 リエルを受信しました |
| T5 | Chinese  | 收到 5 美金 | 收到 2 万 瑞尔 |

For each: status Preparing → Speaking → "Announcement played." Note whether the Khmer voice reads
the Khmer numerals (៥, ២ម៉ឺន) naturally; if not, switch the Khmer amount to number words.

✅ 2026-10-26, Galaxy Note10 (SM-N971N, Android 12, default Samsung TTS, Google TTS installed):
all ten pairs reached "Announcement played." via adb (Khmer/English/Japanese/Chinese on Google TTS,
Korean on Samsung TTS). Audio content not listened to (phone was muted) — still to be checked by ear.
Gender on this device: English has real Male/Female; Khmer and Korean Male use the pitch-shifted
Female voice (notice shown); Japanese and Chinese show "default voice was used" for both genders
because Google's ja/zh voice names carry no gender label (`VoiceGenderMatcher`).

## Custom recordings
| # | Steps | Expected |
|---|-------|----------|
| R1 | Automatic source, any language, Play | Logcat tag `ClipEngine` shows "playing <tokens>"; no fallback notice. Sentence is the joined clips (placeholders sound like the phone engine, word by word). |
| R2 | Pin "Custom recordings", every language × currency × gender | All play from clips. |
| R3 | Delete one clip from assets (e.g. `ja-JP/male/suffix.wav`), rebuild, Automatic, Japanese Male | Plays via the phone engine with notice "No custom recording for the Male Japanese voice yet…". |
| R4 | Same with "Custom recordings" pinned | Red error "No custom recording for the Male Japanese voice…". Nothing plays. |
| R5 | Play, then tap Play again quickly | First sentence stops, second plays; no overlap. |

## Voices and fallbacks
| # | Steps | Expected |
|---|-------|----------|
| V1 | Khmer, Male | Khmer voice at lowered pitch (clearly male-sounding) with notice "No Male voice for Khmer… the Female voice is played with its pitch adjusted." |
| V2 | English, Male and Female | Two distinct voices, no notice. |
| V3 | Korean / Japanese / Chinese, Male and Female | Distinct voices where the engine has both; otherwise the pitch-shift notice. |
| V4 | Pin "FreeSDK/Native: Samsung TTS" (or any engine without Khmer), Khmer, Play | Red error "No Khmer voice is installed…" with the Settings hint. No fallback to another engine. |
| V5 | Pin Speech Services by Google, every language | All five languages play. |
| V6 | Language whose voice data is not downloaded | "… voice data is not installed. Open Settings › Text-to-speech …". No crash. |
| V7 | Disable/uninstall all TTS engines (or an emulator image without one), Play | Error: no TTS engine available / did not start in time. |
| V8 | Airplane mode | Still works for languages with an offline voice. |

## Concurrency and lifecycle
| # | Steps | Expected |
|---|-------|----------|
| L1 | Tap Play 5× quickly | Only the last announcement is heard; no overlapping voices; no crash. |
| L2 | While speaking, change the voice source | Speech stops immediately; nothing new plays until Play. |
| L3 | Play, then rotate the device | Speech continues; source, language, gender and currency are kept. |
| L4 | Play, then press Home | Speech stops. Return: status "Playback stopped.", Play works again. |
| L5 | Play, then Back (finish activity) | Speech stops. No "leaked ServiceConnection" in Logcat (TTS shut down in `onCleared`). |

## Accessibility
| # | Steps | Expected |
|---|-------|----------|
| A1 | TalkBack: swipe through the screen | Header announced as heading. Dropdowns read with their labels. Female/Male and USD/KHR announce checked state; USD/KHR describe the amount they announce. |
| A2 | TalkBack: Play | Status changes are announced (live region). |
| A3 | Display size Largest + Font size Largest, small screen (e.g. 320dp wide) | Labels stack above controls, nothing clipped, screen scrolls. |
| A4 | Dark theme | All text readable. Checked toggle options clearly filled. |
