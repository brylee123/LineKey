# LineKey 0.7.1 verification — 2026-10-14

176 core assertions, Android lint 0 errors / 14 warnings. The individual keyboard audit passed 191 assertions at 360dp/light and 191 at 240dp/dark, covering 31 captured states per width. See LAYOUT_REVIEW.md and layout-audit-071-normal.txt / layout-audit-071-narrow.txt. The separately distributed screenshot gallery contains all 62 individual states.

The audit checks row-edge alignment, nonoverlap, equal letter widths and proportional gutters, visible ink for every letter, readable Dock space and ABC caption, literal character insertion on both symbol pages, each letter map's input, number-pad zero/scrolling, phone dial-key uniqueness/formatting input, and tap-to-open tools in number/phone fields. Export validation checks expected control counts so missing controls below the navigation gutter cannot silently pass. Eight contact sheets and individual captures were visually reviewed.

The standard smoke suite also passed at normal and narrow widths on the same API 37 emulator. Its logs are emulator-071-standard.txt and emulator-071-narrow.txt. It retains one-touch accents, case/Shift/Caps Lock, prediction, emoji search, theme/system-night changes, private fields, navigation clearance, Space height/cursor gestures, clipboard/cursor panels, floating drag/dock and reviewed synthetic dictation. The final emulator display was restored to normal size for manual testing.

Root corrections: horizontal rows use key bounds instead of text baselines; alphabet and emoji input share a panel-relative grid; a shared letter size fits the widest English glyph to its cell; short captions autosize without a virtual scrolling width; Dock has more room; phone keys are dedicated to dialing; numeric/phone mode buttons open tools. The existing key color palette and language model are unchanged.

This remains an emulator portrait/gesture-navigation result, not exhaustive coverage of rotations, font scales, physical devices/API 26, every editor, real speech recognition or performance/battery. Earlier model/typing benchmarks below are historical and unchanged. License/permission/branding checks are repeated when packaging the APK and source archive.

## Earlier verification

# LineKey 0.7 verification — 2026-10-14

Build passed 176 core assertions and Android lint with 0 errors, 14 warnings. The visible API 37 emulator passed 119 checks at 360dp and 119 at 240dp using gesture navigation. The final APK includes the Material/Markdown dependencies and disables automatic downloadable-font initialization. Logs: emulator-07-standard.txt and emulator-07-narrow.txt. This is a debug APK, not a published signed release.

New checks exercise an actual Material license dialog, rendered bundled Markdown heading/link labels, the Material exposed theme dropdown, primary punctuation and secondary math/currency placements, zero aligned beneath five, the percent/Space/Delete column, scrolling to parentheses, operator insertion/deletion, and hidden microphone/menu actions in email, password, number and phone editors. Core checks cover URL/web-email/private/raw fields as well. Reference layout details and the retained mode cycle are in KEY_LAYOUTS.md.

Existing checks retain compact/full typing, case-aware accents and cancellation, painted native icons and emoji-query keys, privacy, theme changes and system night mode, compact swipes, next-word insertion, Space cursor gestures, cursor/clipboard panels, floating drag/dock and reviewed synthetic dictation. Standard-width screenshots of settings, rendered licenses, symbol pages and the number pad were visually reviewed. The emulator was restored to normal size for manual testing.

Material 3 is applied to settings, menus, dialogs and auxiliary panels; the input service uses a separate framework KeyboardTheme with unchanged KeyboardColors. Settings have readable light/dark system-bar icons. Cursor selection and clipboard commands are split into rows for narrow displays. Native keyboard keys and the compact strip retain their existing styling.

The merged manifest requests only AndroidX's app-local signature permission for protected internal receivers. No internet, microphone, contacts or storage permission is added. The automatic EmojiCompat font-provider initializer is absent. Material/AndroidX, Markwon and commonmark licenses and upstream attribution are bundled. Source and uncompressed APK entries are scanned for removed reference branding.

Limits remain: no physical-device/API 26 run, comprehensive accessibility, rotation/split-screen, actual speech recognition or performance/battery certification. Editor filters can reject symbols in numeric/phone fields. The open English model and earlier synthetic prediction benchmarks are unchanged; these results are historical below.

## Earlier verification

# LineKey 0.6.1 verification — 2026-10-14

Changed control vectors to regular weight 400 and their standard 24dp intrinsic size. Replaced microphone and emoji-menu text glyphs with matching monochrome native vectors. Shift still uses outline/fill/underline states, and existing actions and accessibility labels remain intact. These are openly licensed Google Material assets, documented in ICON_DATA.md; exact proprietary Gboard artwork is not claimed.

Validation: 160 core assertions; Android lint 0 errors, 14 warnings (including vector path-length advisories); API 37 emulator passed 91 integration checks at both 360dp and 240dp widths, using gesture navigation. Pixel checks verify Shift, Caps Lock, Enter, microphone and emoji icons are visibly painted. Emoji menu search/insertion, private-field hiding, mode cycling, themes, navigation clearance and other retained behavior pass. See emulator-061-standard.txt and emulator-061-narrow.txt. Standard-width screenshots were visually reviewed; the emulator was restored to normal size for manual testing.

This refinement does not add coverage for actual speech recognition, physical devices, rotation, battery use or other limitations described in the previous verification. APK and source archive scans retain the reference-branding cleanup. Full license notices remain bundled.

## Earlier verification

# LineKey 0.6 verification — 2026-10-14

Build: Gradle 8.11.1 / AGP 8.9.2, SDK 35, Java 17 bytecode. Core: 160 assertions. Android lint: 0 errors, 13 warnings. This is a debug APK.

The visible API 37 emulator passed 89 integration checks at 360 dp and 89 at 240 dp, with gesture navigation. Current logs are emulator-06-standard.txt and emulator-06-narrow.txt. Animations were disabled for deterministic checks.

New coverage: larger native vector controls are actually painted (screenshot pixel checks for Shift off, Caps Lock and Enter); Shift has no visible state text; Caps Lock remains selected after typing; full mode has one body Delete and no Compact header shortcut; the layout button cycles full alphabet, full numbers, full symbols and Mini without modifying the editor text. Its label names the next layout. Enter adapts its icon to the editor action, preserving the existing behavior. Numeric and phone editors retain their field-specific pads.

Both runs also exercised compact/full case handling, one-touch accent selection and cancellation, upward number entry, emoji query filtering and host-text preservation, private fields, theme toggles and system night changes, navigation clearance, Space height/colors/cursor gestures, compact swipes, next-word insertion, cursor/clipboard panels, floating drag/dock and synthetic reviewed dictation. The narrow floating test drags on the visible right side of the handle because Android's transient clipboard preview can overlap its left side.

Control vectors come from Google's open Material Symbols Rounded, weight 700. Caps Lock adds an independently drawn underline. Source attribution and the full Apache-2.0 license are included in the project and APK; see ICON_DATA.md. No proprietary keyboard assets are used. Distributed source and uncompressed APK entries are scanned for removed reference branding in UTF-8 and UTF-16.

Limitations remain: no physical-device/API 26, rotation/split-screen, actual microphone recognition, end-to-end battery/memory or comprehensive accessibility run. Earlier language-model and synthetic typing benchmarks are unchanged and are recorded below as historical results. The normal and narrow UI screenshots were visually reviewed.

## Previous release verification

# LineKey 0.5 verification — 2026-10-14

Build: Gradle 8.11.1 / AGP 8.9.2, SDK 35, Java 17 bytecode. Core checks: 152 assertions. Android lint: 0 errors, 12 warnings. Debug APK, not published or release-signed.

The visible LineKey_Test Android API 37 emulator passed 67 integration assertions at the normal 360 dp display width and 67 at 240 dp (480 physical pixels with density 320). Both runs use gesture navigation; the earlier 0.4 three-button result is retained separately as historical coverage, not a current 0.5 run. Final normal/narrow logs include a new check for preserving an explicitly chosen diacritic through subsequent typing. Guest animations were disabled for deterministic tests.

New behavior checked through actual touch streams: stationary hold opens an accent popup above the letter; movement highlights the targeted choice; release commits once without a second tap; dragging away cancels; popup closes on release; full mode preserves uppercase accents and later letters. Narrow displays wrap choices into additional rows. Field/mode/theme transitions dismiss popups. Accessible native choice actions remain available.

Pixel checks verify Space fills the row height, action controls differ from input keys, and compact/full input colors match. Screenshots were reviewed for popup placement, color consistency, distinct controls, spacing and system-control clearance. All modes share KeyboardColors; Space has the same vertical insets as neighboring keys. The upper lane and brief hold/upward slide retain number/symbol entry, and brief horizontal hold-slide retains precise compact letters.

Other passing checks: case-aware compact/full typing; navigation clearance; compact footprint; emoji query and painted keys after filtering; host text preservation; private fields; repeated Light/Dark toggles; System night changes; compact swipes; next-word insertion; space-bar cursor movement; cursor selection; session clipboard paste/history/clear; floating drag/dock/system-control contrast; reviewed synthetic dictation. An installed recognizer is available, but microphone recording, actual recognition, provider networking and lifecycle remain manually untested. The offline request is advisory and LineKey requests no microphone/internet permission.

The width-adaptive compact decoder uses actual strip width, tolerates missed/repeated taps and adjacent transpositions, and offers up to sixteen candidates to the contextual reranker before three alternatives are displayed. At 180 dp with 12 dp synthetic noise, held-out top-three coverage improved from 35/160 to 92/160 (21.9% to 57.5%); at 240 dp, from 76/160 to 105/160 (47.5% to 65.6%). Wide-screen results are mixed, and heavy noise still causes many misses. See SMALL_SCREEN_PREDICTION.md for all results, methodology and limits. Literal spelling remains selectable; the autocorrection confidence gate is retained. Full-mode words at least seven letters long can offer two-edit alternatives. No real-user accuracy claim.

The independent English bigram model and its WikiText held-out results are unchanged: 904,623 bytes, 123,522 observed edges, 23.70% top-three next-word accuracy against 13.74% unigram baseline on encyclopedia text. It loads in a background thread; it is not production conversational-quality certification. Clipboard snapshots and editor context remain transient; private/literal fields suppress context predictions, and private fields hide clipboard/voice controls. Optional personal learning remains off by default.

Code is Apache-2.0. Model data, word frequencies, Unicode metadata and Google Sans have separate documented licenses, bundled in source and APK.

Remaining coverage: physical devices/API 26, extremely short displays, rotation/split-screen, broader editors, end-to-end latency/memory/battery, learning persistence, correction undo edge cases, real speech recognition and comprehensive accessibility. Emoji deletion remains code-point based rather than whole-grapheme based.

## 0.5.1 distribution cleanup

Removed obsolete product inventories and comparison branding from the project, bundled notices and app help. Required attribution for actual third-party data, typography and build tooling is retained. Functional keyboard code is unchanged. The rebuilt APK passes 152 core assertions and lint with zero errors. The 67 normal-width and 67 narrow-width emulator checks above describe version 0.5; they were not repeated for this documentation-only update. Current source files and uncompressed APK entries were scanned for unwanted branding in filenames, UTF-8 and UTF-16 content, with no matches.
