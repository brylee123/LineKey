# LineKey 0.7.1 — individual keyboard layout review

Reviewed 31 states at normal width (360dp, light) and the same 31 at narrow width (240dp, dark): 62 captured states. This includes all four letter maps in compact, expanded and emoji-query modes, expanded left/right-handed and floating layouts, punctuation, secondary symbols, the calculator pad, and email/password/numeric/phone fields. The screenshot gallery is distributed separately in `LineKey-layout-audit/index.html`.

## Findings and fixes

| Area | Inconsistency | Correction |
|---|---|---|
| Punctuation and symbols | Text baseline alignment shifted the stacked 12/34 button down within its row. | Keyboard rows align key rectangles, with text centered inside each key. The footer stays level and shorter. |
| Calculator footer | Larger zero text and smaller control labels did not share the same vertical key edges. | Disabled baseline alignment in nested key rows as well. |
| Expanded letters | Screen-width-based gutters were too large in one-handed/floating panels; bottom-row letter widths varied. | Shared ten-column row weights and symmetric spacers based on the actual panel. |
| Emoji-query letters | Middle/bottom letters stretched to different widths than alphabet mode. | Uses the same letter grid; its absent Shift has a spacer, keeping Delete and letter columns aligned. |
| Narrow panels | Some W/M-width letters disappeared or ellipsized. | A uniform size is fitted against the widest uppercase/lowercase glyph and the available cell width. Standard-width text remains 22sp. Pixel checks require visible ink for every letter. |
| Floating handle | Dock was truncated on narrow screens. | Gave Dock more width, with a minimum tested label area. |
| Narrow captions | Layout and symbol-page captions could ellipsize. | Short action captions fit their available bounds. |
| Phone pad | Duplicated dial symbols and exposed calculator-only controls. | One * and # in the bottom digit row; plus, pause, wait and parentheses replace calculator extras. Zero stays below five. |
| Numeric/phone tools | A fixed 123 caption looked tappable but its tap did nothing. | Shows an ellipsis tools button that opens the existing menu. |

## Individual results

| Letter map | Compact | Expanded | Emoji query | Left hand | Right hand | Floating |
|---|---|---|---|---|---|---|
| QWERTY | Pass | Pass | Pass | Pass | Pass | Pass |
| QWERTZ | Pass | Pass | Pass | Pass | Pass | Pass |
| AZERTY | Pass | Pass | Pass | Pass | Pass | Pass |
| Alphabetical | Pass | Pass | Pass | Pass | Pass | Pass |

| Other layout | Result |
|---|---|
| Numbers/punctuation | Level rows/footer; every displayed character inserts correctly |
| Secondary math/currency/symbols | Level rows/footer; every displayed character inserts correctly |
| Calculator-style number pad | Zero aligned with five; operator scrolling and parentheses accessible |
| Email | Correct alphabet grid and field-specific controls; no microphone |
| Password | Correct alphabet grid and field-specific controls; no microphone/emoji shortcut |
| Numeric field | Level calculator geometry; no microphone; tools key opens the menu |
| Phone | One star/hash; zero aligned; plus/pause/wait/parentheses insert; no microphone |

The matrix passes at both tested widths. Control rectangles have consistent top/bottom edges within each row, no horizontal overlaps, expected visible control counts, and equal letter-cell widths. Screenshot pixel checks verify that all 26 letters actually paint, rather than relying only on accessible labels. Alphabet probes verify displayed letters insert correctly in each map. The compact strip intentionally staggers letters; operator-list cells intentionally differ from main digit-key heights. These are layout choices rather than alignment defects.

## Limits

Emulator API 37, portrait and gesture navigation. This is not exhaustive coverage of every font scale, rotation, physical screen, editor or combination of layout and floating/handed mode. Existing accent, prediction, emoji, privacy, theme, navigation, clipboard, cursor and floating-drag regressions are covered separately by the smoke test. Language-model behavior and the existing key color palette are unchanged.
