# LineKey 0.7.3: 170,000-entry vocabulary

## Data and reproduction

The bundled frequency list expands from 10,000 to exactly 170,000 unique lowercase ASCII alphabetic entries, lengths 1–24, from the pinned full OpenSubtitles 2018 English list in FrequencyWords. Counts are preserved; the last retained frequency is 13. See WORD_DATA.md for attribution, source/output hashes and the CC BY-SA 4.0 data license. Application code remains Apache-2.0. No network permission or runtime download is introduced.

Run `python tools/prepare_dictionary.py` manually to reproduce the asset, or supply `--input PATH` for a previously downloaded source file. The script verifies its SHA-256 and exact entry count before writing. The repository bundles the output, so ordinary builds are offline after Gradle dependencies are cached. The data includes names, slang and adult language and excludes accented words and contractions; it is a corpus list, not a manually curated dictionary of standard English.

The existing context model is unchanged: expanding spelling coverage does not retrain bigram predictions. Personal learning still requires opt-in, and personal words do not mutate the shared base dictionary.

## Search and ranking

Base vocabulary and length indexes are immutable and shared across Decoder instances. Personal changes make a private copy on first modification. Touch/letter losses and log-frequency priors are cached, dynamic-programming searches stop when their lower bound cannot reach the current shortlist, and a bounded heap retains only the requested best candidates. Full-key search checks nearby word lengths using a bounded edit-distance band. Tie-breaking remains deterministic. A comparison against exhaustive search produced identical word lists in 202 cases covering four layouts, two widths, limits 1/3/32, full input and learned words; this is regression evidence, not a universal proof.

The 0.7.2 touch spread (.06 minimum), per-length-difference penalty (2.4), and log-frequency term (.16) remain. New: a bounded common-word bonus is zero at corpus counts <=500, rises logarithmically to 3 at 5,000, and saturates above that. Stronger trial bonuses crowded out rare words and caused an existing missing-tap regression; the gentler setting retains all previous core cases. Rare entries remain searchable rather than being removed.

Reported `insgzoo` and `inxtzoo` inputs now rank `install` first in QWERTY base-decoder tests at 360dp, 240dp and 180dp. The prior `ankrj` / `quick` regression remains. Actual coordinates, layout and preceding context can affect ranking.

## Measurements and tradeoffs

`sh gradlew :core:benchmarkDictionary :core:benchmarkTapCount :core:benchmarkVocabularyCoverage` reproduces the diagnostic desktop samples. Full mixed-tap evaluation is available via `:core:benchmarkSmallScreens` (also includes the older, slower comparison algorithm).

Desktop dictionary benchmark (24 compact-prefix queries and eight full queries; includes cold/JIT effects):

| Build/search | Initial load | Compact median | Compact p95 | Full median |
|---|---:|---:|---:|---:|
| 10k old search | 21.850 ms | 3.059 ms | 16.706 ms | 2.891 ms |
| 170k old search | 146.552 ms | 31.287 ms | 103.319 ms | 59.965 ms |
| 170k final search | 118.254 ms | 4.465 ms | 43.454 ms | 17.475 ms |

A subsequent Decoder construction reusing the immutable base took 0.004 ms in this sample. These are one-machine diagnostic results, not device latency guarantees. CSV/text logs are bundled.

Same-count noisy taps (960 cases): 0.7.2 top-1/top-3 = 504/687; 0.7.3 = 511/691. Shorter top choices remain 3/960. New-word coverage sample: 64 words randomly sampled beyond the old 10k cutoff, two widths, ideal tap centers: top-1 88/128, top-32 128/128. The old list cannot propose these absent words. This is a small synthetic sample, not 100% coverage for all words or human typing.

Mixed missed/repeated/transposed-tap benchmark shows a tradeoff from the additional competing words. Validation (480 cases): 0.7.2 184/274 top-1/top-3, 0.7.3 183/270. Held-out (960 cases): 0.7.2 394/544, 0.7.3 372/522. Expanded vocabulary does not uniformly improve noisy-input ranking. The selected mild prior preserves existing regression cases and sample tail coverage; user-specific evaluation and better corpus curation remain useful future work. See mixed-taps-073.csv and the 0.7.2 benchmark files for individual conditions.

Android runtime and complete build/smoke results are recorded in VERIFICATION.md after testing. No claim is made about real devices, comprehensive accessibility, battery use or all languages.

## Android runtime validation

API 37 x86_64 emulator, 720x1280/320dpi and 480x1280/320dpi, software graphics, two virtual cores: 131 smoke assertions passed at each width. They include visible Install suggestions for insgzoo/inxtzoo, the existing Quick example, word deletion, scrolling, accents, themes, private fields, navigation, floating and clipboard/cursor behavior. This is emulator coverage; no physical-device run was performed.

One-time dictionary load measured 550.448 ms at normal width and 744.076 ms at narrow width. The 12-query cold compact sample at 240dp measured median/p95 39.541/123.086 ms and 37.833/103.169 ms respectively. Queries remain synchronous; long-word/cold lookups can cause visible input delay. Shared base data prevents reloading on every editor switch. These small samples are diagnostic, not a comprehensive latency or memory certification.
