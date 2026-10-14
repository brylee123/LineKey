# Narrow-screen typing benchmark — 2026-10-14

The 0.5 compact decoder scales horizontal tolerance from actual strip width: sigma is max(0.045, min(0.10, 16 / widthDp)). Alignment supports missed/repeated taps (cost 2.7), adjacent transpositions (cost 1.2 plus spatial errors), and up to three extra/missing characters. It evaluates a broader candidate pool; the optional context model reranks up to sixteen candidates before three alternatives are displayed. Literal spelling remains selectable and the existing automatic-correction confidence gate remains in place. Full typing also permits two edits for words at least seven letters long; the table below evaluates only compact spatial suggestions.

The baseline is the previous spatial decoder, using exactly the same 10,007-word frequency vocabulary and QWERTY layout. 80 validation words and 160 disjoint held-out words are selected from the first 3,000 frequency entries (length 3–10), shuffle seed 7341. Touch seed 51392 + split gives physical Gaussian noise of 12 or 18 dp, a per-word 4 dp Gaussian offset, 4% independent missed taps, 4% repeated taps, and a 15% chance of one adjacent transposition per word. Widths are 360, 240 and 180 dp. Both methods receive identical touches. This physical-noise test differs from the older normalized-width benchmark, so their percentages must not be compared directly.

The width-aware approach was checked against validation before held-out reporting. No tuning used held-out results. The corpus context model is excluded from this comparison. All intended words are in vocabulary; unknown words, actual people, learned adaptation, multilingual prose and vertical errors are not covered.

| Width | Noise | Previous top 1 | New top 1 | Previous top 3 | New top 3 |
|---|---|---|---|---|---|
| 360 dp | 12 dp | 60.0% | 64.4% | 78.8% | 76.9% |
| 360 dp | 18 dp | 36.9% | 35.6% | 51.2% | 51.9% |
| 240 dp | 12 dp | 33.8% | 50.0% | 47.5% | 65.6% |
| 240 dp | 18 dp | 11.9% | 22.5% | 18.1% | 32.5% |
| 180 dp | 12 dp | 16.9% | 35.0% | 21.9% | 57.5% |
| 180 dp | 18 dp | 4.4% | 15.6% | 5.6% | 25.0% |

At 180 dp with 12 dp noise, top-three coverage rises from 35/160 to 92/160 (21.9% to 57.5%). At 240 dp it rises from 76/160 to 105/160 (47.5% to 65.6%). Wider layouts have mixed small differences, and severe noise still causes many misses. This does not establish real-user accuracy or universal superiority.

The new alignment reuses four short work arrays and precomputes each candidate's letter centers, rather than allocating a matrix for every word. This host run measured about 3.4–3.8 ms per held-out adaptive query versus 4.3–5.0 ms previously. Desktop figures do not measure Android end-to-end latency.

Reproduce: build `:core:compileTestJava`, then run `org.linekey.core.SmallScreenBenchmark` with the core main/test/resource classpath. Raw results: [small-screen-benchmark.csv](small-screen-benchmark.csv). The earlier [prediction comparison](PREDICTION_BENCHMARK.md) describes the 0.2–0.4 decoder, which remains a benchmark baseline.
