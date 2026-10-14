# Prediction benchmark — 2026-10-14

Three approaches use exactly the same 10,007-word vocabulary and QWERTY geometry: (0) nearest-letter transcription plus edit distance, (1) spatial sequence alignment with squared tap distance and insertion/deletion costs, (2) robust capped alignment with a stronger frequency prior. Algorithm 1 won validation under severe noise; algorithm 2 tied it under moderate noise and was slightly faster. Algorithm 1 was selected in versions 0.2–0.4. Version 0.5 uses width-aware alignment; see [the narrow-screen comparison](SMALL_SCREEN_PREDICTION.md). The larger dictionary is the main improvement over version 0.1; this does not claim a newly invented algorithm outperformed the existing spatial method.

80 validation and 160 disjoint held-out words were sampled deterministically from the first 3,000 frequency entries whose length is 3–10. Seed 7341 selects words; seed 90210 + split generates noise. Taps use zero-mean Gaussian horizontal noise of 0, 1.8%, or 3.5% of strip width. The severe tier independently omits or duplicates each tap with 2% probability. All intended words are in vocabulary. Frequency counts are fixed; no touch samples were used for training. Selection uses validation, then held-out reporting. Unknown words, contextual prose, accents, real finger bias, and different devices are not modeled.

| Held-out noise | Algorithm | First suggestion | Within three | Mean desktop time/word |
|---|---|---:|---:|---:|
| 0.0% | Nearest + edits | 100.0% | 100.0% | 1.499 ms |
| 0.0% | Spatial alignment (selected) | 100.0% | 100.0% | 4.447 ms |
| 0.0% | Robust alignment | 100.0% | 100.0% | 3.957 ms |
| 1.8% | Nearest + edits | 65.6% | 78.1% | 1.451 ms |
| 1.8% | Spatial alignment (selected) | 95.6% | 100.0% | 4.488 ms |
| 1.8% | Robust alignment | 95.6% | 100.0% | 4.018 ms |
| 3.5% | Nearest + edits | 16.9% | 23.1% | 1.407 ms |
| 3.5% | Spatial alignment (selected) | 76.2% | 89.4% | 4.332 ms |
| 3.5% | Robust alignment | 69.4% | 81.9% | 3.911 ms |

Timing is from this Windows Java host, includes ranking, and is not an Android latency claim. Each sample is one noisy realization; percentages have substantial uncertainty and are not a production typing-accuracy estimate. Default autocorrection now also requires a reasonable spatial cost and a score margin; literal spelling remains available. Benchmark measures suggestions, not the separate confidence gate or real-user acceptance.

Reproduce from the project root:
```
./gradlew :core:benchmarkPredictions
```
Raw counts, validation results, and timing: [prediction-benchmark.csv](prediction-benchmark.csv). Word provenance: [WORD_DATA.md](WORD_DATA.md).
