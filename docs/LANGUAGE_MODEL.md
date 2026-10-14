# Offline English language model

Independent Java bigram engine with interpolated absolute-discount Kneser-Ney probabilities (discount 0.75). Trained only on WikiText-2 raw-v1 training split. Source: [Salesforce WikiText](https://huggingface.co/datasets/Salesforce/wikitext), Wikipedia contributors; Stephen Merity, Caiming Xiong, James Bradbury, Richard Socher (2016), Pointer Sentinel Mixture Models. Source is CC BY-SA 3.0 / GFDL; this adaptation is distributed under CC BY-SA 3.0. Java engine and training script are Apache-2.0.

Changes: lowercase English words, skip headings, preserve sentence boundaries, undo spaced contraction suffixes, expand unambiguous contractions, map unknown words to a marker without joining neighbors. Retain existing keyboard vocabulary; keep 24 observed successors per context. Unseen/pruned edges use discounted continuation backoff. 9,424 continuation unigrams, 123,522 bigrams; binary model 904,623 bytes.

Binary format: LKLM signature, vocabulary IDs, float32 log probabilities and up to 24 successors per context. Model loading runs on a background thread; spatial predictions remain available during loading. Precomputed successor lists and cached fallback avoid a vocabulary scan per prediction.

Original held-out splits, never used for training:

validation: 174,279 targets; vocabulary coverage 84.60%; top-1 15.13%; top-3 23.93%; unigram top-3 14.17%. All targets, including unknown words, are counted in the denominator.

test: 195,962 targets; vocabulary coverage 83.17%; top-1 14.82%; top-3 23.70%; unigram top-3 13.74%. All targets, including unknown words, are counted in the denominator.

These measurements describe encyclopedia text, not conversational typing. This is a lightweight offline baseline, not a validated production-quality conversational model or neural LLM. Corpus-specific and uncommon suggestions remain possible. Actual chat accuracy, battery, memory, physical phones, and broader language support still need validation. No runtime network, telemetry, or persistent editor context. An earlier news model was rejected because it omitted common short words.

Rebuild: install Python and pyarrow, then run `python tools/train_language_model.py`. Downloads use verified SHA-256 hashes; raw text is not bundled. Data license and attribution are bundled in source and APK.

Source SHA-256:

- train-00000-of-00001.parquet: e83889baabc497075506f91975be5fac0d45c5290b6b20582c8cd1e853d0c9f7
- validation-00000-of-00001.parquet: 204929b7ff9d6184953f867dedb860e40aa69c078fc1e54b3baaa8fb28511c4c
- test-00000-of-00001.parquet: 5f1bea067869d04849c0f975a2b29c4ff47d867f484f5010ea5e861eab246d91
