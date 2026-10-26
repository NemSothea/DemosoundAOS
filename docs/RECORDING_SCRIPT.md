# Recording script for custom voice clips

The app speaks by joining one short clip per word. Record every row below once per language and
per gender, and save each as **16-bit PCM WAV, mono** (any sample rate; 24 kHz recommended) under
`app/src/main/assets/voice/<language tag>/<female|male>/<file>`. File names are fixed.

Guidelines for the voice talent:

* Say each word on its own, naturally, with about 0.2 s of silence before and after. The app adds
  a 60 ms gap between words, so do not pad more than that.
* Keep the same microphone distance, level and tone for all words of one language and gender so
  the joined sentence sounds like one take.
* Khmer scale words (រយ, ពាន់, ម៉ឺន, សែន, លាន) and Chinese / Korean / Japanese scale words
  (十 百 千 万, 십 백 천 만, じゅう ひゃく せん まん) are spoken after a digit, e.g. 20,000 = ពីរ + ម៉ឺន,
  两 + 万, 이 + 만, に + まん. Record them as they sound in that position.
* Japanese `300.wav`, `600.wav`, `800.wav`, `3000.wav`, `8000.wav` are the irregular readings
  (さんびゃく, ろっぴゃく, はっぴゃく, さんぜん, はっせん). Chinese `2b.wav` is 两 (used before 万/千/百);
  `2.wav` is 二.
* A missing file makes the app fall back to the phone's text-to-speech for that sentence and say so
  on screen, so partial sets are fine while recording is in progress.

The clips currently bundled are **placeholders** synthesized from the tables below by
`tools/gen_clips.py` (Microsoft neural voices via `edge-tts`). Replace them file by file; no code
change is needed. The script reads these tables, so keep the `File` and `Say` columns exact.

## Khmer (`assets/voice/km-KH/female/` and `assets/voice/km-KH/male/`)

| File | Say |
|---|---|
| `prefix.wav` | ទទួលបាន |
| `0.wav` | សូន្យ |
| `1.wav` | មួយ |
| `2.wav` | ពីរ |
| `3.wav` | បី |
| `4.wav` | បួន |
| `5.wav` | ប្រាំ |
| `6.wav` | ប្រាំមួយ |
| `7.wav` | ប្រាំពីរ |
| `8.wav` | ប្រាំបី |
| `9.wav` | ប្រាំបួន |
| `10.wav` | ដប់ |
| `20.wav` | ម្ភៃ |
| `30.wav` | សាមសិប |
| `40.wav` | សែសិប |
| `50.wav` | ហាសិប |
| `60.wav` | ហុកសិប |
| `70.wav` | ចិតសិប |
| `80.wav` | ប៉ែតសិប |
| `90.wav` | កៅសិប |
| `100.wav` | រយ |
| `1000.wav` | ពាន់ |
| `10000.wav` | ម៉ឺន |
| `100000.wav` | សែន |
| `1000000.wav` | លាន |
| `dollar.wav` | ដុល្លារ |
| `cent.wav` | សេន |
| `riel.wav` | រៀល |
| `and.wav` | និង |

## English (`assets/voice/en-US/female/` and `assets/voice/en-US/male/`)

| File | Say |
|---|---|
| `prefix.wav` | Received |
| `0.wav` | zero |
| `1.wav` | one |
| `2.wav` | two |
| `3.wav` | three |
| `4.wav` | four |
| `5.wav` | five |
| `6.wav` | six |
| `7.wav` | seven |
| `8.wav` | eight |
| `9.wav` | nine |
| `10.wav` | ten |
| `11.wav` | eleven |
| `12.wav` | twelve |
| `13.wav` | thirteen |
| `14.wav` | fourteen |
| `15.wav` | fifteen |
| `16.wav` | sixteen |
| `17.wav` | seventeen |
| `18.wav` | eighteen |
| `19.wav` | nineteen |
| `20.wav` | twenty |
| `30.wav` | thirty |
| `40.wav` | forty |
| `50.wav` | fifty |
| `60.wav` | sixty |
| `70.wav` | seventy |
| `80.wav` | eighty |
| `90.wav` | ninety |
| `100.wav` | hundred |
| `1000.wav` | thousand |
| `1000000.wav` | million |
| `1000000000.wav` | billion |
| `dollar.wav` | Dollar |
| `dollars.wav` | Dollars |
| `cent.wav` | Cent |
| `cents.wav` | Cents |
| `riel.wav` | Riel |
| `and.wav` | and |

## Korean (`assets/voice/ko-KR/female/` and `assets/voice/ko-KR/male/`)

| File | Say |
|---|---|
| `0.wav` | 영 |
| `1.wav` | 일 |
| `2.wav` | 이 |
| `3.wav` | 삼 |
| `4.wav` | 사 |
| `5.wav` | 오 |
| `6.wav` | 육 |
| `7.wav` | 칠 |
| `8.wav` | 팔 |
| `9.wav` | 구 |
| `10.wav` | 십 |
| `100.wav` | 백 |
| `1000.wav` | 천 |
| `10000.wav` | 만 |
| `100000000.wav` | 억 |
| `dollar.wav` | 달러 |
| `riel.wav` | 리엘 |
| `point.wav` | 점 |
| `suffix.wav` | 입금되었습니다 |

## Japanese (`assets/voice/ja-JP/female/` and `assets/voice/ja-JP/male/`)

| File | Say |
|---|---|
| `0.wav` | ゼロ |
| `1.wav` | いち |
| `2.wav` | に |
| `3.wav` | さん |
| `4.wav` | よん |
| `5.wav` | ご |
| `6.wav` | ろく |
| `7.wav` | なな |
| `8.wav` | はち |
| `9.wav` | きゅう |
| `10.wav` | じゅう |
| `100.wav` | ひゃく |
| `300.wav` | さんびゃく |
| `600.wav` | ろっぴゃく |
| `800.wav` | はっぴゃく |
| `1000.wav` | せん |
| `3000.wav` | さんぜん |
| `8000.wav` | はっせん |
| `10000.wav` | まん |
| `100000000.wav` | おく |
| `dollar.wav` | ドル |
| `riel.wav` | リエル |
| `point.wav` | てん |
| `suffix.wav` | を受信しました |

## Chinese (`assets/voice/zh-CN/female/` and `assets/voice/zh-CN/male/`)

| File | Say |
|---|---|
| `prefix.wav` | 收到 |
| `0.wav` | 零 |
| `1.wav` | 一 |
| `2.wav` | 二 |
| `2b.wav` | 两 |
| `3.wav` | 三 |
| `4.wav` | 四 |
| `5.wav` | 五 |
| `6.wav` | 六 |
| `7.wav` | 七 |
| `8.wav` | 八 |
| `9.wav` | 九 |
| `10.wav` | 十 |
| `100.wav` | 百 |
| `1000.wav` | 千 |
| `10000.wav` | 万 |
| `100000000.wav` | 亿 |
| `dollar.wav` | 美金 |
| `riel.wav` | 瑞尔 |
| `point.wav` | 点 |
