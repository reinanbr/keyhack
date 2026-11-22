#!/usr/bin/env python3
# Copyright 2026 The KeyHack Authors. SPDX-License-Identifier: Apache-2.0
"""Builds KeyHack's next-word model (bigrams and trigrams) from conversation-style
sentences.

Usage: python3 -I tools/gen_ngrams.py <sentences.tsv.bz2> <lang.dict> [<lang.chat>] <out.ngram>

Source: Tatoeba per-language exports (CC BY 2.0 FR), e.g.
https://downloads.tatoeba.org/exports/per_language/por/por_sentences.tsv.bz2
(id<TAB>lang<TAB>sentence). Only counts are kept; no sentence is stored.

Rules:
- Text is lowercased and split into words; punctuation (including commas)
  breaks a sequence, as the keyboard predicts only right after a word.
- A word must be in the keyboard dictionary with a non-zero frequency (this
  also drops words flagged offensive) or in the chat word list.
- Names: a word that is capitalized in most of its mid-sentence uses ("Tom",
  "Mary", "Boston") is a proper noun and breaks the sequence, except the
  generic words in KEEP_CAPITALIZED ("I", days, months, languages). Tatoeba
  repeats a few names and places very often; they must not be predicted.
- Pruned to frequent contexts and their best followers.

Output: plain UTF-8 text (F-Droid's scanner refuses archives), one context
per line, sorted:
  context<TAB>word:score word:score ...
where context is one word (bigram) or two words separated by a space
(trigram), and score 1..255 is the follower's conditional probability on a
log scale (255 = always, 170 = 10%, 85 = 1%).
"""
import bz2
import collections
import math
import re
import sys

WORD = re.compile(r"[^\W\d_]+(?:['’-][^\W\d_]+)*")
BREAK = re.compile(r"[.!?…;:,()\[\]{}\"“”«»/|–—]+")
MIN_BIGRAM_CONTEXT = 8
MIN_TRIGRAM_CONTEXT = 12
MIN_FOLLOWER = 3
FOLLOWERS = 6
MAX_TRIGRAM_CONTEXTS = 40000
KEEP_CAPITALIZED = {
    "i", "i'm", "i'd", "i've", "i'll", "ok", "tv", "god", "deus", "christmas", "natal", "brasil",
    "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
    "january", "february", "march", "april", "may", "june", "july", "august", "september",
    "october", "november", "december",
    "english", "french", "german", "spanish", "portuguese", "italian", "japanese", "chinese",
    "american", "brazilian", "british", "european",
}


def load_vocab(dict_path, chat):
    vocab = set()
    with open(dict_path, encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) == 2 and parts[1].isdigit() and int(parts[1]) > 0:
                vocab.add(parts[0].lower())
    if chat:
        with open(chat, encoding="utf-8") as f:
            for line in f:
                if line.strip() and not line.startswith("#"):
                    vocab.add(line.split("\t")[0].lower())
    return vocab


def sentences(path):
    with bz2.open(path, "rt", encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) >= 3:
                yield parts[2]


def proper_nouns(path):
    """Words written capitalized in most of their non-initial uses."""
    upper = collections.Counter()
    lower = collections.Counter()
    for s in sentences(path):
        for chunk in BREAK.split(s):
            for i, m in enumerate(WORD.finditer(chunk)):
                w = m.group()
                if i == 0 and chunk is not None and chunk.strip().startswith(w):
                    continue  # sentence/clause start: case says nothing
                if w[0].isupper():
                    upper[w.lower()] += 1
                else:
                    lower[w.lower()] += 1
    return {w for w, n in upper.items() if n >= 3 and n > 4 * lower[w] and w not in KEEP_CAPITALIZED}


def runs(path, vocab, names):
    """Lists of consecutive known words."""
    for s in sentences(path):
        for chunk in BREAK.split(s):
            run = []
            for m in WORD.finditer(chunk):
                w = m.group().lower()
                if w in vocab and w not in names:
                    run.append(w)
                else:
                    if len(run) > 1:
                        yield run
                    run = []
            if len(run) > 1:
                yield run


def score(count, total):
    p = count / total
    return max(1, min(255, round(255 * (1 + math.log10(p) / 3))))


def best(followers, total):
    top = [(w, c) for w, c in followers.most_common(FOLLOWERS) if c >= MIN_FOLLOWER]
    return " ".join(f"{w}:{score(c, total)}" for w, c in top)


def main(src, dict_gz, chat, dst):
    vocab = load_vocab(dict_gz, chat)
    names = proper_nouns(src)
    bigrams = collections.defaultdict(collections.Counter)
    for run in runs(src, vocab, names):
        for a, b in zip(run, run[1:]):
            bigrams[a][b] += 1
    # Second pass: trigrams only after frequent word pairs keeps memory small.
    frequent = {(a, b) for a, f in bigrams.items() for b, c in f.items() if c >= MIN_TRIGRAM_CONTEXT}
    trigrams = collections.defaultdict(collections.Counter)
    for run in runs(src, vocab, names):
        for a, b, c in zip(run, run[1:], run[2:]):
            if (a, b) in frequent:
                trigrams[f"{a} {b}"][c] += 1

    lines = []
    for a, f in bigrams.items():
        total = sum(f.values())
        if total >= MIN_BIGRAM_CONTEXT and (row := best(f, total)):
            lines.append(f"{a}\t{row}")
    ranked = sorted(trigrams.items(), key=lambda kv: -sum(kv[1].values()))[:MAX_TRIGRAM_CONTEXTS]
    for ctx, f in ranked:
        total = sum(f.values())
        if total >= MIN_TRIGRAM_CONTEXT and (row := best(f, total)):
            lines.append(f"{ctx}\t{row}")
    lines.sort()
    with open(dst, "w", encoding="utf-8", newline="\n") as out:
        out.write("# KeyHack next-word model. Counts derived from Tatoeba (CC BY 2.0 FR).\n")
        out.write("\n".join(lines) + "\n")
    print(f"{dst}: {len(lines)} contexts, {len(names)} names skipped", file=sys.stderr)


if __name__ == "__main__":
    args = sys.argv[1:]
    if len(args) == 3:
        main(args[0], args[1], None, args[2])
    elif len(args) == 4:
        main(*args)
    else:
        sys.exit(__doc__)
