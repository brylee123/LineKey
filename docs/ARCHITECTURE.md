# Architecture

## Independent source boundaries

`core` is a plain Java module: letter-layout geometry, an original starter vocabulary plus a separately licensed frequency resource, a spatial sequence decoder, edit-distance suggestions, and field policy. It runs deterministic checks without Android or downloaded test libraries.

`app` is a native Android application: `LineKeyIme` handles the IME lifecycle, composing/committed text, candidate selection, correction undo, editor actions, and field transitions. `CompactStrip` handles taps and gestures. `MainActivity` supplies onboarding, test fields, settings, and vocabulary controls. `Preferences` and `WordStore` store local state.

The service uses Android's documented `InputMethodService` / `InputConnection` APIs. IME registration, settings activities, composition, and editor actions are documented in [Create an input method](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method) and [InputMethodService](https://developer.android.com/reference/android/inputmethodservice/InputMethodService).

## Original decoding algorithm

Each supported full layout is defined by independently specified letter rows. Compact mode orders those letters by horizontal position and maps their order to evenly spaced centers on a strip. A tap stores normalized horizontal position. Dynamic programming aligns taps with candidate spellings using squared horizontal displacement plus insertion/deletion costs. A small log-weight preference breaks close scores. Full mode uses conventional edit distance with conservative candidate filtering.

Suggestions update while literal spelling remains composed. Space/punctuation can commit the best eligible word; the literal candidate is always selectable. A correction records the immediately committed suffix solely for undo, and only replaces it when the current editor still exposes that exact suffix. Leaving an editor clears transient composition and undo state.

## Data

No runtime network permission. Personal word counts are local SharedPreferences JSON, bounded to 500 ASCII English words, 2–32 letters each. Learning is disabled by default and suppressed for literal/private fields. The app respects editor requests against personalized learning. It does not log surrounding text or store sentence context. Android app backup is disabled. A manual erase control clears the personal dictionary.

## Dependencies

Runtime: Android public framework, the original `core` module, Material Components/AndroidX, Markwon and commonmark. Build: Gradle 8.11.1 / AGP 8.9.2, Java 17 bytecode, SDK 35. No native components. The included wrapper comes from the locally available Gradle bootstrap and is the upstream wrapper binary.

## Context, tools, and voice

`LanguageModel` reads an immutable compact binary corpus model on a background loader thread. Cached successor lists supply next-word choices; optional contextual scores rerank spatial candidates. Editor context is bounded and transient, and is not read for literal/private prediction. `Accents` defines original Unicode variations.

`ClipboardSession` reads clipboard text only while its explicitly opened panel is rendered. It retains up to ten non-sensitive entries in memory, with no listener, disk history, or URI dereferencing. Clear history clears LineKey's session history, not the Android clipboard. Private editors hide clipboard history and voice actions.

Cursor controls use public `InputConnection` key events and editing actions. Space-bar motion changes selection without inserting spaces. Floating mode changes the IME's touch region so touches outside the panel reach the editor; position is clamped when the panel resizes, and system controls keep a contrasting gutter. No overlay permission is needed.

`VoiceInputActivity` delegates recording to an installed Android recognition activity and requests offline operation. A provider may still use a network. `VoiceDraft` holds the returned text in memory for explicit review/insert/discard; it never automatically inserts into an editor that gained focus during recognition. LineKey itself requests neither recording nor internet permission.

## Continuous accents and consistent keys (0.5)

`AccentPopup` is a non-focusable attached popup. The original held view keeps the touch stream; screen-coordinate movement highlights a variation, and release commits once. The popup uses window-relative positioning and remains above the held key, with multiple rows on narrow displays. Dragging away cancels, and field/theme/mode changes dismiss it. Accessible choices also expose native click actions. Upper-lane holds and upward motion before the accent timeout retain symbols; brief horizontal holds retain letter precision.

`KeyboardColors` supplies the same surface, input, action and highlight colors to compact/full/symbol/emoji/floating layouts and accent popups. Key roles distinguish inputs, actions, suggestions and Space. Space uses the same vertical insets as neighboring controls.

## Explicit layouts and native icons (0.6)

`KeyboardMode` defines Mini, alphabet, numbers and symbols, including next-layout captions and restricted-field handling. The emoji query is an overlay that returns to full alphabet. Mode changes finish composition without silently inserting spaces. Number/symbol/query boards each provide body deletion; the full suggestion header has no duplicate mode/delete controls.

`IconButton` uses framework ImageButton rather than empty-text Button drawing. This fixed blank icons observed during emulator screenshot review. A small software layer is limited to icon controls; pixel-level checks require visible icon foreground for Shift, Caps Lock, Enter, microphone and emoji menu. Key style/layout setup is shared with text controls, and state changes invalidate the keyboard for reliable case/icon painting. Regular 24dp Material Symbols have a separately documented open license; the Caps Lock vector adds an underline to the filled Shift design.

## Material menus and reference layouts (0.7)

`MainActivity` is an AppCompatActivity with a Material 3 DayNight theme. `MaterialUi` centralizes panel cards/buttons and light/dark menu contexts. The input service explicitly selects `KeyboardTheme`, so adding the menu library does not restyle keyboard keys. Cursor selection and clipboard actions use separate rows to retain useful targets on narrow displays. The app's system bars match the settings surface brightness.

`MarkdownDocuments` reads bundled notices and renders .md documents with Markwon core/table support into selectable TextViews with clickable links. It uses no WebView or remote content loader. Plain-text notices remain plain text. Every bundled notice is selectable from a Material dialog, including UI-library attribution and the full commonmark BSD license.

`SymbolLayout` stores the reference characters separately from the IME's layout code. `KeyboardMode.NUMPAD` is an explicit secondary choice, returning to alphabet in ordinary text; it does not add another step to the four-mode cycle. The pad uses weighted columns, three digit rows, a scrollable operator viewport, and an aligned zero row. `FieldPolicy.voiceAllowed()` gates both the microphone control and menu action. The final voice launcher also checks the policy.

Material's transitive AndroidX manifest adds a signature-only internal receiver permission; no network, microphone, contacts or storage permission is requested. The default EmojiCompat downloadable-font initializer is explicitly removed, retaining native/system emoji. See UI_LIBRARIES.md for versions, upstream sources and licenses.

## Bound-aligned rows and letter fit (0.7.1)

`newKeyRow()` disables LinearLayout baseline alignment. This matters for multiline layout captions, larger digit labels and ImageButton controls sharing a row. `addLetterRows()` supplies proportional spacers and equal letter cells for both the alphabet and emoji-query surfaces; it does not use display width for row gutters. A layout listener measures the widest English letter in either case and applies one shared size per row, bounded by the normal 22sp size and the actual cell/padding width. Phone columns and footer use dialing-specific inputs. Restricted numeric/phone layout keys open tools instead of advertising a no-op cycle.

The instrumentation runner accepts `-e layout_audit true` (and optional `-e audit_dark true`) to capture 31 individual states, measure row/letter geometry, test visible letter pixels, and exercise character input. Screenshot manifests include control counts so the exported review rejects missing/clipped controls. The standard smoke runner remains available without these arguments.
