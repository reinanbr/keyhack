#!/usr/bin/env python3
# Copyright 2026 The KeyHack Authors. SPDX-License-Identifier: Apache-2.0
"""Builds KeyHack's emoji table from Unicode data (Unicode License V3).

Usage: python3 -I tools/gen_emoji.py <emoji-test.txt> <out.tsv> <lang>=<annotations.json>,<derived.json> ...

Inputs:
  https://unicode.org/Public/emoji/latest/emoji-test.txt
  https://github.com/unicode-org/cldr-json  cldr-annotations-full and
  cldr-annotations-derived-full, annotations.json per language.

Output: UTF-8 TSV, one emoji per line, in Unicode order:
  group<TAB>emoji<TAB>tone<TAB>version<TAB>keywords-lang1<TAB>keywords-lang2 ...
tone is 1 when the five Fitzpatrick variants exist as "insert the modifier
after the first code point". Keywords are lowercase, '|'-separated.
"""
import json
import sys

TONES = ["1F3FB", "1F3FC", "1F3FD", "1F3FE", "1F3FF"]
GROUPS = {
    "Smileys & Emotion": "smileys",
    "People & Body": "people",
    "Animals & Nature": "nature",
    "Food & Drink": "food",
    "Travel & Places": "travel",
    "Activities": "activities",
    "Objects": "objects",
    "Symbols": "symbols",
    "Flags": "flags",
}


def chars(cps):
    return "".join(chr(int(c, 16)) for c in cps)


def toned(cps, tone):
    rest = cps[1:]
    if rest and rest[0] == "FE0F":
        rest = rest[1:]
    return [cps[0], tone] + rest


def load_keywords(spec):
    table = {}
    for path in spec.split(","):
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        root = data.get("annotations") or data.get("annotationsDerived")
        for key, value in root["annotations"].items():
            words = list(value.get("tts", [])) + list(value.get("default", []))
            norm = key.replace("️", "")
            table.setdefault(norm, [])
            for w in words:
                w = w.strip().lower().replace("|", " ")
                if w and w not in table[norm]:
                    table[norm].append(w)
    return table


def main():
    src, dst, *langs = sys.argv[1:]
    keywords = [load_keywords(spec.split("=", 1)[1]) for spec in langs]
    entries, qualified, group = [], set(), None
    with open(src, encoding="utf-8") as f:
        for line in f:
            if line.startswith("# group:"):
                group = GROUPS.get(line.split(":", 1)[1].strip())
                continue
            if not line.strip() or line.startswith("#") or "; fully-qualified" not in line:
                continue
            cps = line.split(";")[0].split()
            qualified.add(" ".join(cps))
            version = line.split("#", 1)[1].split()[1].lstrip("E")
            if group and not any(t in cps for t in TONES):
                entries.append((group, cps, version))
    rows = []
    for group, cps, version in entries:
        tone = all(" ".join(toned(cps, t)) in qualified for t in TONES) and len(cps) >= 1
        emoji = chars(cps)
        norm = emoji.replace("️", "")
        kws = ["|".join(k.get(norm, [])) for k in keywords]
        rows.append("\t".join([group, emoji, "1" if tone else "0", version] + kws))
    with open(dst, "w", encoding="utf-8") as out:
        out.write("\n".join(rows) + "\n")
    print(f"{dst}: {len(rows)} emoji")


if __name__ == "__main__":
    main()
