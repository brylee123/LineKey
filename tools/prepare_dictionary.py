# SPDX-License-Identifier: Apache-2.0
"""Reproduce the bundled vocabulary from pinned CC BY-SA 4.0 FrequencyWords data.
Run manually; Android and Gradle never download dictionaries at runtime/build time.
"""
from pathlib import Path
import argparse
import hashlib
import re
import urllib.request

COMMIT = '072bbed282316a23651aa7068c7173aa7898cf80'
SOURCE = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/" + COMMIT + "/content/2018/en/en_full.txt"
SHA256 = '7fea67ab954e2c01df6c608c9826e594cf36f8823b3243554f88245fb75dc506'
COUNT = 170_000

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, help="Use an already downloaded source file")
    args = parser.parse_args()
    data = args.input.read_bytes() if args.input else urllib.request.urlopen(SOURCE, timeout=60).read()
    if hashlib.sha256(data).hexdigest() != SHA256:
        raise SystemExit("Source SHA-256 mismatch")
    source_counts = dict(line.rsplit(" ", 1) for line in data.decode("utf-8").splitlines())
    words = {}
    for line in data.decode("utf-8").splitlines():
        word, frequency = line.rsplit(" ", 1)
        if re.fullmatch(r"[a-z]{1,24}", word) and word not in words:
            words[word] = int(frequency)
            if len(words) == COUNT:
                break
    if len(words) != COUNT or "install" not in words:
        raise SystemExit("Unexpected vocabulary coverage")
    target = Path(__file__).resolve().parents[1] / "core/src/main/resources/english-frequency.txt"
    target.write_bytes("".join(f"{word} {frequency}\n" for word, frequency in words.items()).encode("ascii"))
    # Source splits apostrophes into separate tokens. Restore independently listed
    # spellings, not arbitrary punctuation at every possible character boundary.
    spellings = Path(__file__).with_name("punctuated-spellings.txt")
    punctuated = {}
    for line in spellings.read_text(encoding="utf-8").splitlines():
        if line.startswith("#"):
            continue
        for spelling in line.lower().split():
            letters = re.sub(r"['.&/+#-]", "", spelling)
            if (not re.fullmatch(r"[a-z]{1,32}", letters)
                    or not re.fullmatch(r"[a-z]+(?:(?:['.&/-]|'-)[a-z]+)*(?:\.|\+\+|#)?", spelling)):
                raise SystemExit(f"Invalid spelling {spelling}")
            frequency = int(source_counts.get(spelling, 0))
            if not frequency:
                frequency = max(100, int(source_counts.get(letters, 0)))
            punctuated[spelling] = frequency
    additional = target.with_name("english-punctuated.txt")
    additional.write_bytes("".join(f"{w} {f}\n" for w, f in sorted(punctuated.items())).encode("ascii"))
    print(f"Wrote {len(punctuated)} additional spellings; SHA-256 {hashlib.sha256(additional.read_bytes()).hexdigest()}")
    print(f"Wrote {len(words):,} entries; minimum count {min(words.values())}; output SHA-256 {hashlib.sha256(target.read_bytes()).hexdigest()}")

if __name__ == "__main__":
    main()
