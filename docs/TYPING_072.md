# LineKey 0.7.2 typing changes

Compact left-to-right swipes confirm and add a space; right-to-left swipes remove one word, including trailing whitespace. A composing word is removed first. Repeated gestures continue through committed words. Private/raw editors retain their conservative deletion behavior. A horizontal swipe before the accent hold is no longer mistaken for precise-letter dragging; stationary accent holds and upward symbol selection remain supported.

The horizontally scrollable suggestion list preserves literal spelling and candidate highlighting. Up to 32 typing candidates and 16 model next-word predictions are available. The strip resets to its leading matches when input changes. Suggestion keys use readable content widths and retain the previous height; Shift/Delete/voice stay outside the scrolling area.

The compact spatial decoder raises its minimum normalized touch spread from .045 to .06, applies a 2.4 score penalty per difference between tap count and word length, and increases the log-frequency coefficient from .12 to .16. This preserves permissive horizontal matching while discouraging deletion of intended letters. The edit alignment still allows missed/repeated taps and transpositions. Context reranking, learning/privacy controls and full-key exact-spelling scoring are retained. Full mode now exposes up to 32 matches instead of truncating at three.

The user-reported five-tap `ankrj` example ranks `quick` first in the base compact decoder at 360dp, 240dp and 180dp using QWERTY letter centers. Actual taps and preceding context can change ranking; this is not an exact text replacement rule. Native keyboard tests also check that `quick` is visibly offered with context reranking enabled.

## Reproducible synthetic evaluation

`./gradlew :core:benchmarkTapCount` runs a deterministic 960-case same-character-count benchmark over 160 shuffled common English words, three widths and two noise levels. Tap positions include Gaussian error and shared horizontal bias. Baseline: existing .045 minimum spread with the modest .16 frequency coefficient; final: .06 plus the tap-count prior. Aggregate top-1: 414/960 to 504/960; top-3: 576/960 to 687/960. Shorter top-1 words: 135/960 to 3/960. Per-condition results are in tap-count-before-072.csv and tap-count-after-072.csv. These are synthetic samples, not a measured human typing success rate.

The existing `:core:benchmarkSmallScreens` benchmark is named `SmallScreenBenchmark` in source and can also be invoked with its Java classpath. It includes missed/repeated taps and transpositions. Aggregate top-1 across its 1,440 adaptive cases improves from 529 to 578; top-3 from 744 to 818. The validation and held-out rows are included separately in mixed-taps-before-072.csv and mixed-taps-after-072.csv. Neither benchmark proves production performance or universal improvements for every word. The model itself was not retrained.
