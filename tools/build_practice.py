#!/usr/bin/env python3
"""Převede tools/practice-src/*.txt na data/practice/prNN.json (procvičování B1 nad rámec lekcí).

Formát souboru (oddělovač polí je " ## "):
  #Topic|Téma                       první řádek
  choose ## věta s (a/b/c) ## správná možnost
  gap ## věta s _____ (sloveso) ## odpověď (varianty oddělené " | ")
  error ## věta s chybou ## opravená věta
  order ## slovo / slovo / slovo ## správná věta
  tr ## česká věta ## anglický překlad | další varianta | …
  @text                             začátek textu k porozumění (řádky textu až do první otázky)
  @q ## otázka ## možnost / možnost / možnost ## správná možnost
  @end                              konec textu
"""
import os, json, glob
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
out = os.path.join(ROOT, 'data', 'practice'); os.makedirs(out, exist_ok=True)
total = 0
for f in sorted(glob.glob(os.path.join(ROOT, 'tools', 'practice-src', '*.txt'))):
    nn = os.path.basename(f)[:2]
    pid = f"pr{nn}"
    lines = [l.rstrip('\n') for l in open(f, encoding='utf-8') if l.strip()]
    topic, topic_cz = lines[0].lstrip('#').split('|')
    ex, text, n = [], None, 0
    def add(**kw):
        global n
        n += 1; ex.append(dict(id=f"{pid}-{n:03d}", **kw))
    for ln, l in enumerate(lines[1:], 2):
        try:
            if l.startswith('@text'): text = []; continue
            if l.startswith('@end'): text = None; continue
            if l.startswith('@q'):
                _, q, opts, a = l.split(' ## ')
                opts = [o.strip() for o in opts.split(' / ')]
                assert a in opts, a
                add(type='read', q=q, a=a, options=opts, text=' '.join(text) if isinstance(text, list) else text)
                if isinstance(text, list): text = ' '.join(text)
                continue
            if isinstance(text, list): text.append(l.strip()); continue
            t, q, a = l.split(' ## ')
            t = {'tr': 'translate'}.get(t, t)
            assert t in ('choose', 'gap', 'error', 'order', 'translate'), t
            if t == 'choose':
                opts = q[q.index('(') + 1:q.rindex(')')].split('/')
                assert a in [o.strip() for o in opts], (q, a)
            if t == 'order':
                assert sorted(w.strip().lower() for w in q.split('/')) == sorted(a.rstrip('.?!').replace(',', '').lower().split()), (q, a)
            add(type=t, q=q, a=a)
        except Exception as e:
            raise SystemExit(f"{f}:{ln}: {e!r}: {l}")
    total += len(ex)
    json.dump(dict(id=pid, topic=topic, topic_cz=topic_cz, level="B1", exercises=ex),
              open(os.path.join(out, f"{pid}.json"), 'w'), ensure_ascii=False, indent=0)
    print(pid, topic, len(ex), {t: sum(1 for e in ex if e['type'] == t) for t in ('choose','gap','error','order','translate','read')})
print("celkem", total)
