# English frequency data

The 170,000 entries in core/src/main/resources/english-frequency.txt are adapted from Hermit Dave's FrequencyWords, OpenSubtitles 2018 full English list. The data is bundled and works offline.

Source: https://raw.githubusercontent.com/hermitdave/FrequencyWords/072bbed282316a23651aa7068c7173aa7898cf80/content/2018/en/en_full.txt
Retrieved: 2026-10-14. Source SHA-256: 7fea67ab954e2c01df6c608c9826e594cf36f8823b3243554f88245fb75dc506
Output SHA-256: 4474e79eee6033cf1aa422c04593791b11d6bceaeca2835514f706a6420fb2b7

Changes: retain the first 170,000 unique lowercase ASCII alphabetic words of length 1–24 in corpus-frequency order; preserve counts. The minimum retained count is 13. This is vocabulary expansion, not language-model retraining. It includes names, slang and adult language, and excludes contractions, hyphenated and accented forms. The existing independently authored starter vocabulary and optional personal words remain separate.

Reproduce with `python tools/prepare_dictionary.py`, or supply the pinned source with `--input`. The script verifies the source SHA-256 and exact output count. Gradle and the Android app never fetch dictionaries.

The adapted word data is licensed CC BY-SA 4.0: https://creativecommons.org/licenses/by-sa/4.0/ . Attribution: Hermit Dave / FrequencyWords / OpenSubtitles 2018. Upstream declares CC BY-SA 4.0 for content and MIT for its code; our preparation script is independently authored Apache-2.0. Full data license: licenses/CC-BY-SA-4.0.txt. Application code remains Apache-2.0. These are separate licenses.

See the source documentation, docs/DICTIONARY_073.md for retrieval changes, coverage/latency measurements, and remaining limits.
