#!/usr/bin/env python3
"""Převede tools/bank-src/*.txt (řádky en|cz|příklad|úroveň, první řádek #Topic|Téma) na data/bank/*.json."""
import os, re, json, glob
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
out = os.path.join(ROOT, 'data', 'bank'); os.makedirs(out, exist_ok=True)
def slug(s): return re.sub(r'[^a-z0-9]+', '-', s.lower()).strip('-')
for f in sorted(glob.glob(os.path.join(ROOT, 'tools', 'bank-src', '*.txt'))):
    name = os.path.splitext(os.path.basename(f))[0]
    lines = [l.rstrip('\n') for l in open(f, encoding='utf-8') if l.strip()]
    topic, topic_cz = lines[0].lstrip('#').split('|')
    words, seen = [], set()
    for l in lines[1:]:
        en, cz, ex, lvl = l.split('|')
        wid = f"b{name[:2]}-{slug(en)}"
        if wid in seen: continue
        seen.add(wid)
        words.append(dict(id=wid, en=en, cz=cz, ex=ex, level=lvl))
    levels = sorted(set(w['level'] for w in words))
    json.dump(dict(id=f"pack{name[:2]}", topic=topic, topic_cz=topic_cz, level="/".join(levels), words=words),
              open(os.path.join(out, f"pack{name[:2]}.json"), 'w'), ensure_ascii=False, indent=0)
print("ok")
