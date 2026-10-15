# English frequency data

The 170,000 entries in core/src/main/resources/english-frequency.txt are adapted from Hermit Dave's FrequencyWords, OpenSubtitles 2018 full English list. The data is bundled and works offline.

Source: https://raw.githubusercontent.com/hermitdave/FrequencyWords/072bbed282316a23651aa7068c7173aa7898cf80/content/2018/en/en_full.txt
Retrieved: 2026-10-14. Source SHA-256: 7fea67ab954e2c01df6c608c9826e594cf36f8823b3243554f88245fb75dc506
Output SHA-256: 4474e79eee6033cf1aa422c04593791b11d6bceaeca2835514f706a6420fb2b7

Changes: retain the first 170,000 unique lowercase ASCII alphabetic words of length 1–24 in corpus-frequency order; preserve counts. The minimum retained count is 13. This is vocabulary expansion, not language-model retraining. It includes names, slang and adult language, and excludes contractions, hyphenated and accented forms from that base resource. A separate 332-entry punctuated resource supplements the base; see PUNCTUATED_WORDS.md for selection and heuristic weights. The existing independently authored starter vocabulary and optional personal words remain separate.

Reproduce with `python tools/prepare_dictionary.py`, or supply the pinned source with `--input`. The script verifies the source SHA-256 and exact output count. Gradle and the Android app never fetch dictionaries.

The adapted word data is licensed CC BY-SA 4.0: https://creativecommons.org/licenses/by-sa/4.0/ . Attribution: Hermit Dave / FrequencyWords / OpenSubtitles 2018. Upstream declares CC BY-SA 4.0 for content and MIT for its code; our preparation script is independently authored Apache-2.0. Full data license: licenses/CC-BY-SA-4.0.txt. Application code remains Apache-2.0. These are separate licenses.

See DICTIONARY_073.md for retrieval changes, coverage/latency measurements, and remaining limits.

Supplemental weighted spellings: core/src/main/resources/english-punctuated.txt, adapted under CC BY-SA 4.0. Source and base hashes above are unchanged; the supplemental hash and reproduction details are in PUNCTUATED_WORDS.md.

# Punctuation-free spelling (0.7.4)

Each vocabulary item now stores its displayed spelling separately from its letters-only matching sequence. Length buckets, touch alignment, missing/repeated taps, transpositions and full-key edit distance operate on letters, so punctuation consumes no imaginary taps. Suggestions and commits retain the full spelling. Periods, apostrophes, hyphens, ampersands and slashes are supported inside established words; selected terminal forms such as `c++` and `c#` are supported too. Mixed apostrophe/hyphen spellings such as `jack-o'-lantern` are valid. Curly apostrophes and typographic/nonbreaking hyphens normalize to ASCII equivalents. This does not normalize arbitrary Unicode dashes, digits, spaces or mathematical expressions.

The original 170,000 plain entries are unchanged. `tools/punctuated-spellings.txt` lists 332 independently selected conventional spellings: contractions (including multiple apostrophes), common compounds, a sample of possessives, dotted abbreviations, `&` / `/` forms, and selected technical terms. `tools/prepare_dictionary.py --input SOURCE` reproduces `core/src/main/resources/english-punctuated.txt` alongside the original resource from the existing pinned, hash-checked source. Supplemental SHA-256: 3327577c968619d7edb6e2919d40974fd1986e0f6b1ef59fab1824b51efdf608.

Where an exact punctuated spelling exists in FrequencyWords, its count is preserved. The source splits apostrophes into fragments, so absent forms use the letters-only count with a minimum weight of 100. Those fallback weights are heuristic ranking weights, not measured frequencies of the displayed spellings. The authored spelling inventory and preparation code are Apache-2.0; the generated weighted resource adapts FrequencyWords data under CC BY-SA 4.0. See WORD_DATA.md and the full licenses.

Plain and punctuated entries remain distinct, including `well`/`we'll`, `ill`/`I'll`, `were`/`we're`, `its`/`it's` and `cant`/`can't`. A small 0.15 score penalty for omitted punctuation favors an otherwise equally weighted exact plain spelling. Context can still reorder candidates; this does not claim to reliably infer grammatical intent. Literal input is always selectable. Choosing a suggestion inserts its punctuation; Delete restores the original literal spelling immediately after a correction. Existing word-delete swipes remove a whole whitespace-delimited spelling, including its internal punctuation. Caps and initial capitals follow the existing keyboard casing behavior.

Optional local learning and manual teaching accept supported punctuated spellings, and both the display spelling and normalized length index update together. Personal changes remain isolated from other Decoder instances; private and literal editor policies still suppress learning and suggestions.

This is a finite spelling inventory, not automatic hyphenation, possessive generation, or punctuation insertion into arbitrary words. Unlisted words can be typed literally or taught through the personal dictionary. It does not join space-separated phrases, parse email addresses/URLs, expand `cplusplus` into `c++`, or retrain the existing next-word model. Multiple display spellings for the same letters can all appear in the scrollable suggestions. It adds vocabulary competition; general noisy-input accuracy has not been re-benchmarked for this update.
