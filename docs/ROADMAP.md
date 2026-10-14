# Development roadmap

## Milestone 1 — usable foundation (this project)

IME registration, setup, compact/full input, literal escape, candidate selection, an original demo decoder, field-aware editor actions, optional local words, basic emoji/symbols, appearance/geometry/gesture preferences. Build and runtime checks are recorded separately. This milestone establishes a project to iterate in Android Studio, not full functional parity.

## Milestone 2 — trustworthy editor integration

Composition/selection regression tests across real editors; delete selected text, cursor moves, field restarts, paste, hardware-keyboard events, signed numbers, phone and datetime input, editor completions, portrait/landscape/split-screen, rotation, process recreation, navigation insets, accessibility, and multi-code-point emoji deletion. Correction undo must never overwrite unrelated edits. Sensitive fields must not show suggestions or persist personal words.

## Milestone 3 — production prediction

Select a redistributable word/frequency dataset with a documented license. Add indexed lookup/beam search, calibrated spatial confusion, correction confidence, contextual / next-word model, capitalization and punctuation rules, missing-space repair, rejection/undo adaptation, personal-word management, and performance benchmarks. Preserve literal choice and explicit no-learning behavior. Gate publication on measured latency/memory/battery and anonymized synthetic accuracy tests.

## Milestone 4 — interaction and accessibility

Validate hold/drag gestures, mode transitions, screen size and placement, symbol/URL layouts, Shift/caps lock, candidate scrolling, deletion/selection, and dictionary removal. Expand acceptance tests for accessibility, theme changes, popup cancellation, editor lifecycle and additional devices.

## Milestone 5 — language and advanced tools

Dvorak/Colemak; independently licensed multilingual models and dual-language ranking; language switching; floating position; candidate/spacebar geometry; cursor/clipboard panels; modern opt-in voice delegation; emoji categories/search/recents/prediction; typing-speed statistics; original adaptive/custom themes. Language pack sources must be owned by this project or independently redistributable.

## Milestone 6 — open-source release

Provenance/dependency audit, fuller privacy policy, reproducible release build, signing outside source control, CI, contribution guidelines, regression matrix, supported Android versions, issue templates, release documentation, and optional F-Droid packaging. Publication and signing are separate release steps.

## 0.2 update

Completed: staggered letters, three-row compact layout, no idle branding, navigation padding, held secondary characters, hold-slide-release precision, emoji keyword search isolated from the host editor, larger independently licensed vocabulary, and controlled decoder benchmark. Contextual next-word modeling, missing-space repair, a comprehensive emoji catalog, and grapheme-cluster deletion remain future work.

## 0.3 UI update

Completed: normal system sans-serif type, rounded larger expanded keys with row staggering, visible return-to-Compact control, navigation/caption/gesture gutter, Light/Dark/System selection, Unicode emoji names and keywords, typo matching, scrollable search results, and editing the query at its selection. Context modeling and grapheme-cluster deletion remain future work.

## 0.4 interaction and tools update

Completed: case-aware compact/full letters; hold-still diacritics and hold-upward secondary characters; Shift/caps lock; independent offline English contextual reranking and next-word engine; draggable floating panel with docking and system-control gutter; space-bar cursor movement; cursor selection/copy/cut/paste controls; explicit session clipboard history; installed-provider voice input with review before insertion. Model data is CC BY-SA 3.0; Java code is Apache-2.0.

Production release gates remain: representative conversational prediction evaluation, physical-device performance/memory/battery checks, real speech-provider recognition and lifecycle checks, rotation and smaller screens, multilingual models, and comprehensive accessibility/editor regression coverage. Corpus-based next words are implemented, but production conversational accuracy is not certified.

## 0.5 small-screen typing update

Completed: width-aware forgiving spatial alignment; adjacent transposition and extra/missing tap handling; a broader shortlist for contextual reranking; continuous hold/drag/release diacritics and cancellation; shared mode palettes; distinct action controls; equal space-bar insets. Validation includes synthetic physical-noise comparisons and actual narrow-width emulator interactions. Physical phones, extreme short displays, real-user typing accuracy, and complete grapheme deletion remain open.

## 0.6 mode and icon update

Completed: explicit Mini/alphabet/numbers/symbols cycle with next-destination captions; bold native vector controls; outline/fill/underline Shift states without visible state text; editor-aware Enter icons; single body Delete controls and streamlined full headers. Native icons include open attribution and foreground pixel checks.

## 0.7 menus and layouts

Completed: Material 3 menus/settings/panels; rendered Markdown licenses; familiar punctuation/currency/math placements; a calculator-style number pad with scrollable operators; field-specific voice suppression. Input keyboard colors remain separate.
