#!/usr/bin/env python3
# Copyright 2026 The KeyHack Authors. SPDX-License-Identifier: Apache-2.0
"""Converts an AOSP LatinIME *.combined.gz wordlist into KeyHack's dictionary.

Usage: python3 -I tools/gen_dict.py <input.combined.gz> <output.dict.gz>

Source wordlists (Apache-2.0) come from
https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/refs/heads/main/dictionaries/

Output: gzip'd UTF-8 text, one entry per line, sorted by lowercase word:
  word<TAB>freq             a valid word; freq 0..255 (0 = valid, never suggested)
  typo<TAB>-<TAB>correction  a whitelisted autocorrection (e.g. musica -> música)
"""
import gzip
import re
import sys

NEVER_SUGGEST = {"offensive", "nonword", "babytalk", "drugs"}


def main(src: str, dst: str) -> None:
    words: dict[str, int] = {}
    fixes: dict[str, str] = {}
    current = None
    with gzip.open(src, "rt", encoding="utf-8") as f:
        for line in f:
            if line.startswith(" word="):
                attrs = dict(kv.split("=", 1) for kv in line.strip().split(",") if "=" in kv)
                word = attrs["word"]
                current = word
                if "\t" in word or not re.fullmatch(r"[^\W\d_]+(['’-][^\W\d_]+)*", word):
                    current = None
                    continue
                if attrs.get("not_a_word") == "true":
                    continue
                flags = set(attrs.get("flags", "").split(":"))
                freq = int(attrs.get("f", "0"))
                words[word] = 0 if flags & NEVER_SUGGEST else max(1, min(255, freq))
            elif line.startswith("  shortcut=") and current is not None:
                attrs = dict(kv.split("=", 1) for kv in line.strip().split(",") if "=" in kv)
                if attrs.get("f") == "whitelist":
                    fixes[current] = attrs["shortcut"]
    for typo in fixes:
        words.pop(typo, None)
    out = [f"{w}\t{f}" for w, f in words.items()] + [f"{t}\t-\t{c}" for t, c in fixes.items()]
    out.sort(key=lambda line: (line.split("\t", 1)[0].lower(), line))
    with gzip.GzipFile(dst, "wb", mtime=0) as g:
        g.write(("\n".join(out) + "\n").encode("utf-8"))
    print(f"{dst}: {len(words)} words, {len(fixes)} corrections")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
