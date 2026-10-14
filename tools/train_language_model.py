# SPDX-License-Identifier: Apache-2.0
"""Rebuild LineKey's English model: pip install pyarrow; python tools/train_language_model.py.

Corpus statistics/model data: CC BY-SA 3.0. This script and Java engine: Apache-2.0.
Only the training split contributes counts. Original validation and test splits are held out.
"""
from collections import Counter, defaultdict
from pathlib import Path
import hashlib
import math
import re
import struct
import urllib.request

import pyarrow.parquet as parquet

PROJECT = Path(__file__).resolve().parents[1]
CACHE = PROJECT / "build" / "corpus"
BASE_URL = "https://huggingface.co/datasets/Salesforce/wikitext/resolve/main/wikitext-2-raw-v1/"
SOURCE_HASHES = {
    "train": "e83889baabc497075506f91975be5fac0d45c5290b6b20582c8cd1e853d0c9f7",
    "validation": "204929b7ff9d6184953f867dedb860e40aa69c078fc1e54b3baaa8fb28511c4c",
    "test": "5f1bea067869d04849c0f975a2b29c4ff47d867f484f5010ea5e861eab246d91",
}
DISCOUNT = 0.75
SUCCESSOR_LIMIT = 24
TOKENS = re.compile(r"[a-z]+(?:'[a-z]+)?|[.!?]")


def normalize_contractions(text):
    """Undo WikiText's spaced suffixes, then expand unambiguous contractions.

    Possessive 's and ambiguous 'd are left as whole tokens and map to unknown
    when absent from the keyboard vocabulary. They must not become bogus 's'/'d' words.
    """
    text = re.sub(r"\b([a-z]+)\s+('(?:s|m|re|ve|d|ll))\b", r"\1\2", text.lower())
    text = re.sub(r"\b([a-z]+)\s+(n't)\b", r"\1\2", text)
    text = text.replace("can't", "can not").replace("won't", "will not")
    text = re.sub(r"\b([a-z]+)n't\b", r"\1 not", text)
    for suffix, expansion in [("'m", " am"), ("'re", " are"), ("'ve", " have"), ("'ll", " will")]:
        text = re.sub(r"\b([a-z]+)" + suffix + r"\b", r"\1" + expansion, text)
    for subject in ["it", "that", "there", "what", "who", "where", "how"]:
        text = re.sub(r"\b" + subject + r"'s\b", subject + " is", text)
    return text


def corpus_pairs(split, vocabulary):
    CACHE.mkdir(parents=True, exist_ok=True)
    name = f"{split}-00000-of-00001.parquet"
    source = CACHE / name
    if not source.exists():
        source.write_bytes(urllib.request.urlopen(BASE_URL + name, timeout=60).read())
    actual_hash = hashlib.sha256(source.read_bytes()).hexdigest()
    if actual_hash != SOURCE_HASHES[split]:
        raise ValueError(f"Corpus hash differs for {split}; review provenance before rebuilding")
    for paragraph in parquet.read_table(source)["text"].to_pylist():
        if paragraph.strip().startswith("="):
            continue
        previous = "<s>"
        for token in TOKENS.findall(normalize_contractions(paragraph)):
            if token in ".!?":
                previous = "<s>"
                continue
            word = token if token in vocabulary else "<unk>"
            yield previous, word
            previous = word


def train(vocabulary):
    pairs = Counter(corpus_pairs("train", vocabulary))
    totals, unique_successors, continuations = Counter(), Counter(), Counter()
    for (previous, word), count in pairs.items():
        totals[previous] += count
        unique_successors[previous] += 1
        continuations[word] += 1
    unigrams = {word: count / len(pairs) for word, count in continuations.items()}
    backoffs = {word: DISCOUNT * unique_successors[word] / count for word, count in totals.items()}
    successors = defaultdict(list)
    for (previous, word), count in pairs.items():
        if word == "<unk>":
            continue
        probability = (count - DISCOUNT) / totals[previous] + backoffs[previous] * unigrams[word]
        successors[previous].append((word, probability))
    successors = {
        word: sorted(edges, key=lambda edge: (-edge[1], edge[0]))[:SUCCESSOR_LIMIT]
        for word, edges in successors.items()
    }
    return unigrams, backoffs, successors


def write_model(unigrams, backoffs, successors):
    output = PROJECT / "core/src/main/resources/english-model.bin"
    words = sorted(set(unigrams) | set(backoffs))
    if len(words) > 65535:
        raise ValueError("Model format supports at most 65535 words")
    identifiers = {word: index for index, word in enumerate(words)}
    with output.open("wb") as stream:
        stream.write(struct.pack(">II", 0x4C4B4C4D, len(words)))
        for word in words:
            encoded = word.encode("ascii")
            stream.write(struct.pack(">H", len(encoded)) + encoded)
            stream.write(struct.pack(">ff", round(math.log10(unigrams.get(word, 1e-8)), 6), round(math.log10(backoffs.get(word, 1)), 6)))
        for word in words:
            edges = successors.get(word, [])
            stream.write(struct.pack(">B", len(edges)))
            for following, probability in edges:
                stream.write(struct.pack(">Hf", identifiers[following], round(math.log10(probability), 6)))
    return output


def evaluate(split, vocabulary, unigrams, successors):
    fallback = [word for word, _ in sorted(unigrams.items(), key=lambda item: (-item[1], item[0])) if not word.startswith("<")][:3]
    total = covered = first = three = baseline = 0
    for previous, target in corpus_pairs(split, vocabulary):
        total += 1
        if target == "<unk>":
            continue
        covered += 1
        predictions = [word for word, _ in successors.get(previous, [])[:3]] or fallback
        first += target == predictions[0]
        three += target in predictions
        baseline += target in fallback
    return (f"{split}: {total:,} targets; vocabulary coverage {covered / total:.2%}; "
            f"top-1 {first / total:.2%}; top-3 {three / total:.2%}; "
            f"unigram top-3 {baseline / total:.2%}. All targets, including unknown words, are counted in the denominator.")


def main():
    vocabulary = {line.split()[0] for line in (PROJECT / "core/src/main/resources/english-frequency.txt").read_text().splitlines()}
    unigrams, backoffs, successors = train(vocabulary)
    model = write_model(unigrams, backoffs, successors)
    metrics = [evaluate(split, vocabulary, unigrams, successors) for split in ["validation", "test"]]
    report = f"""# Offline English language model

Independent Java bigram engine with interpolated absolute-discount Kneser-Ney probabilities (discount {DISCOUNT}). Trained only on WikiText-2 raw-v1 training split. Source: [Salesforce WikiText](https://huggingface.co/datasets/Salesforce/wikitext), Wikipedia contributors; Stephen Merity, Caiming Xiong, James Bradbury, Richard Socher (2016), Pointer Sentinel Mixture Models. Source is CC BY-SA 3.0 / GFDL; this adaptation is distributed under CC BY-SA 3.0. Java engine and training script are Apache-2.0.

Changes: lowercase English words, skip headings, preserve sentence boundaries, undo spaced contraction suffixes, expand unambiguous contractions, map unknown words to a marker without joining neighbors. Retain existing keyboard vocabulary; keep {SUCCESSOR_LIMIT} observed successors per context. Unseen/pruned edges use discounted continuation backoff. {len(unigrams):,} continuation unigrams, {sum(map(len, successors.values())):,} bigrams; binary model {model.stat().st_size:,} bytes.

Binary format: LKLM signature, vocabulary IDs, float32 log probabilities and up to {SUCCESSOR_LIMIT} successors per context. Model loading runs on a background thread; spatial predictions remain available during loading. Precomputed successor lists and cached fallback avoid a vocabulary scan per prediction.

Original held-out splits, never used for training:

""" + "\n\n".join(metrics) + """

These measurements describe encyclopedia text, not conversational typing. This is a lightweight offline baseline, not a validated production-quality conversational model or neural LLM. Corpus-specific and uncommon suggestions remain possible. Actual chat accuracy, battery, memory, physical phones, and broader language support still need validation. No runtime network, telemetry, or persistent editor context. An earlier news model was rejected because it omitted common short words.

Rebuild: install Python and pyarrow, then run `python tools/train_language_model.py`. Downloads use verified SHA-256 hashes; raw text is not bundled. Data license and attribution are bundled in source and APK.

Source SHA-256:
"""
    for split, digest in SOURCE_HASHES.items():
        report += f"\n- {split}-00000-of-00001.parquet: {digest}"
    report += "\n"
    (PROJECT / "docs/LANGUAGE_MODEL.md").write_text(report, encoding="utf-8")
    (PROJECT / "app/src/main/assets/licenses/LANGUAGE_MODEL.md").write_text(report, encoding="utf-8")
    print("\n".join(metrics), flush=True)
    print(f"Binary model: {model.stat().st_size:,} bytes", flush=True)


if __name__ == "__main__":
    main()
