# LineKey

An independent, open-source Android keyboard inspired by compact, approximate-touch typing. Working name: **LineKey**. This is a runnable 0.7 prototype.

## Open in Android Studio

Open this folder (the folder containing `settings.gradle.kts`) using **File → Open**, allow Gradle sync, then run the **app** configuration on an Android 8.0+ emulator or device. The setup screen links to Android's enable-keyboard settings and keyboard picker, and includes test fields.

Use JDK 17 or newer, Android SDK platform 35, and build tools 35.0.0. Gradle 8.11.1 and Android Gradle Plugin 8.9.2 are pinned. The compatibility combination follows [Android's AGP 8.9 documentation](https://developer.android.com/build/releases/agp-8-9-0-release-notes). The application source is Java. Material Components 1.12.0, AndroidX, Markwon 4.6.2 and commonmark-java 0.13.0 are runtime libraries resolved by Gradle; no NDK is needed. Android Studio can create `local.properties` for your SDK; this workstation's copy is ignored by Git.

```powershell
.\gradlew.bat :core:verifyCore :app:assembleDebug :app:lintDebug
```

On macOS/Linux: `sh gradlew :core:verifyCore :app:assembleDebug :app:lintDebug`.

## Available now

- Staggered compact letter strip based on the supplied screenshot, teal spacebar, highlighted candidates, and full letter keys; four layouts: QWERTY, QWERTZ, AZERTY, alphabetical.
- Width-adaptive offline spatial / edit-distance predictions with 10,000 independently licensed English frequency entries plus original starter words. Three algorithms were compared on reproducible noisy taps; see the prediction benchmark.
- Literal spelling always offered beside candidate words; correction on space and punctuation; immediate correction undo with Delete.
- Case-aware letters, Shift/caps lock, continuous hold/drag/release accent selection, and hold-and-drag upward for numbers/punctuation; compact swipes: right to confirm/space, left to erase a word, up for full mode, down to hide.
- Numbers, symbols, 3,781 Unicode emoji entries with English names/keywords, typo-tolerant matching, and a scrollable results grid, Shift, punctuation, editor-aware Enter, keyboard picker, and settings access.
- Tap the layout button to cycle **Mini → ABC → 123 → #+= → Mini**. Its label shows the next layout. Hold it for tools and settings. Full layouts have one body Delete key and no Compact/Delete shortcuts in the suggestion header. Emoji search has its own body Delete and an ABC return through the layout button.
- Compact mode uses three rows; no idle branding label. Bottom padding includes IME caption/navigation/gesture insets and a minimum 48dp system-control gutter in every mode.
- Light / Dark / System themes, Google Sans typography, rounded expanded keys, left/right handed placement, adjustable heights, swipe threshold, hold delay, system haptics and sound.
- Optional local learning (off by default), manual word teaching, and personal-dictionary erasure. Password / visible-password / web-password / numeric-password fields suppress suggestions and learning. URL/email fields use literal typing; `IME_FLAG_NO_PERSONALIZED_LEARNING` suppresses learning.
- No network, microphone, contacts or storage permissions; no app networking, analytics or Android backup. AndroidX adds a signature-only permission for protecting internal receivers. Voice delegates to an installed speech provider, requests offline recognition, and presents a draft for explicit insertion; the provider controls microphone and network behavior.

- Offline next-word suggestions and contextual reranking; see [model data, held-out accuracy, and limits](docs/LANGUAGE_MODEL.md).
- Floating keyboard with drag handle and Dock; space-bar cursor swipes, cursor selection/editing panel, and explicit session-only clipboard history (up to ten entries; sensitive clips excluded from history). Private fields hide clipboard and voice controls.

## Documentation

- [Architecture and provenance](docs/ARCHITECTURE.md): module boundaries and data sources.
- [Roadmap](docs/ROADMAP.md): planned work and acceptance criteria.
- [Verification](docs/VERIFICATION.md): actual build and runtime checks with remaining limitations.

## Emulator smoke test

`app/src/androidTest` contains a small custom instrumentation runner, with no external test framework. Build the test APK with `:app:assembleDebugAndroidTest`, install both app and test APKs on an isolated emulator, enable virtual input when hardware-keyboard emulation is on, then run:

```powershell
adb shell am instrument -w org.linekey.keyboard.test/org.linekey.keyboard.KeyboardSmokeTest
```

The runner types synthetic text, checks password controls, and switches dark/light four times. It changes the test emulator's selected keyboard and theme preferences; use a disposable emulator. Current API 37 results and remaining coverage gaps are in [Verification](docs/VERIFICATION.md).

## Source and third-party data

LineKey's application code, decoder, UI, icon and starter vocabulary are independently authored. Runtime dependencies include Android's public framework, the project's Java core module, Material Components/AndroidX and Markwon/commonmark. See [UI dependencies](docs/UI_LIBRARIES.md). Build tooling uses Gradle and Android Gradle Plugin.

Bundled data and typography have separate licenses: FrequencyWords English frequencies (CC BY-SA 4.0), WikiText-derived bigram statistics (CC BY-SA 3.0), Unicode Emoji/CLDR metadata (Unicode-3.0), and Google Sans (SIL OFL 1.1). Attribution and full license texts are included in the source and app notices.

## Limitations

English only; contextual correction and next-word predictions use a compact corpus-trained bigram model. Conversational quality is experimental and not production-validated. Missing-space repair, language packs, search/speed panels, adaptive themes, and dictionary import/export remain future work. Compact mode needs dedicated screen-reader support; use full mode for individual accessible key controls. Deletion operates by Unicode code point, not entire emoji grapheme clusters. See the roadmap before using this as a daily keyboard.

## License

Original LineKey code, documentation, icon, and starter vocabulary: **Apache-2.0**, see [LICENSE](LICENSE). Gradle's wrapper is Apache-2.0; see [NOTICE](NOTICE).

The additional English frequency data is **CC BY-SA 4.0**, separately from application source. See [word-data attribution](docs/WORD_DATA.md) and [data license](licenses/CC-BY-SA-4.0.txt).

Prediction measurements and reproduction instructions: [Prediction benchmark](docs/PREDICTION_BENCHMARK.md).

Unicode emoji data is licensed **Unicode-3.0**. See [emoji provenance](docs/EMOJI_DATA.md). Google Sans is bundled from the official Google Fonts distribution under SIL OFL 1.1; see docs/FONT_DATA.md. No fonts or graphics were extracted from Gboard.

The English bigram model is separately **CC BY-SA 3.0**. See [model attribution and reproduction](docs/LANGUAGE_MODEL.md) and [license](licenses/CC-BY-SA-3.0.txt).

## 0.5 typing and layout update

Touch tolerance scales with the actual compact strip width, with alignment for missed/repeated taps and adjacent transpositions. The contextual reranker sees up to sixteen spatial candidates before three alternatives are displayed. Unknown spelling remains selectable. See [narrow-screen measurements](docs/SMALL_SCREEN_PREDICTION.md).

Hold a letter to show its accent popup, drag to a highlighted variation, and release to insert it. Releasing without dragging inserts the base character; dragging down/away cancels. The popup does not move the keyboard. Numbers/symbols remain available by holding the small upper lane, or starting an upward drag after a brief hold before the accent popup opens. A brief hold and horizontal slide still selects precise compact letters.

Compact, full, emoji, symbol and floating modes share a teal palette. Action buttons use contrasting fills and outlines; letter/punctuation keys use the regular input fill; suggestion buttons use the surface fill. The space bar has the same vertical insets as neighboring keys. Selected diacritics remain literal through subsequent typing.

## 0.6 mode controls and icons

Shift and editor actions use Google Material Symbols Rounded vectors at regular weight 400. Shift is outlined while off, filled and highlighted while on, and underlined for Caps Lock. State text is available to accessibility services without visible On/Lock labels. Enter uses return, search, send, done or navigation icons according to the editor action, with the original input behavior preserved. Native image buttons scale the 24dp vectors to available key bounds. See [icon provenance and license](docs/ICON_DATA.md).

The mode key displays its next destination: ABC from Mini, 123 from alphabet, #+= from numbers, and Mini from symbols. Restricted text fields skip Mini; number and phone editors retain their field-appropriate keypads. Holding the key opens the existing tools/settings menu.

## 0.6.1 icon refinement

Standard 24dp, regular-weight Material controls replace the oversized bold set. Microphone and emoji-menu buttons now use matching monochrome vectors, keeping their existing actions and accessibility labels.

## 0.7 Material menus and familiar numeric layouts

Settings now use Material 3 cards, switches, outlined fields, exposed dropdowns, sliders and dialogs. Tools use themed Material menus and panel actions. These menus use light/dark teal Material surface roles independently of keyboard key colors. Bundled Markdown notices render as styled native text with links and tables; plain-text license files remain plain text.

The number/punctuation and secondary symbol pages follow the supplied character placements. The calculator-style pad is available through the stacked 12/34 shortcut on either page, and is the default for number/phone fields. Its left operator column scrolls through +, -, *, /, (, ); zero aligns with the center digit column. The existing four-mode cycle is retained. See [layout details](docs/KEY_LAYOUTS.md).

Voice buttons and menu entries are absent from email, URL, password, number, phone and other literal/private input fields. Ordinary text fields retain optional provider-based dictation. Native/system emoji rendering is retained; the transitive EmojiCompat downloadable-font initializer is disabled.

## 0.7.1 layout consistency review

Key rows align by their bounds rather than glyph baselines, fixing stepped symbol/calculator footers. Expanded and emoji letters share a ten-column grid that fits full, one-handed and floating panels. A uniform letter size keeps wide glyphs visible on narrow panels while preserving 22sp at normal width. Dock and short action captions fit their controls. The phone pad now uses dialing-specific symbols, and number/phone fields have a working tools button. See [individual layout review](docs/LAYOUT_REVIEW.md).
