#!/usr/bin/env python3
"""Převede tools/practice-src/*.txt na data/practice/prNN.json (procvičování B1 nad rámec lekcí).

Soubor LXX_nazev.txt patří k lekci LXX (opakování v aplikaci bere jen sady probraných lekcí).
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
import os, re, json, glob
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
out = os.path.join(ROOT, 'data', 'practice'); os.makedirs(out, exist_ok=True)
for old in glob.glob(os.path.join(out, '*.json')): os.remove(old)
total = 0
for f in sorted(glob.glob(os.path.join(ROOT, 'tools', 'practice-src', '*.txt'))):
    base = os.path.splitext(os.path.basename(f))[0]
    m = re.match(r'(L\d\d)_(.+)', base)
    lesson = m.group(1) if m else ''
    pid = f"{m.group(1)}-{m.group(2)}" if m else f"pr{base[:2]}"
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
                add(type='read', q=q, a=a, options=opts, text='\n\n'.join(text) if isinstance(text, list) else text)
                if isinstance(text, list): text = '\n\n'.join(text)
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
    qs = [e['q'] for e in ex]
    dup = {q for q in qs if qs.count(q) > 1 and not q.startswith('@')}
    if dup: raise SystemExit(f"{f}: duplicitní zadání: {sorted(dup)[:5]}")
    total += len(ex)
    json.dump(dict(id=pid, lesson=lesson, topic=topic, topic_cz=topic_cz, level="B1", exercises=ex),
              open(os.path.join(out, f"{pid}.json"), 'w'), ensure_ascii=False, indent=0)
    print(pid, topic, len(ex), {t: sum(1 for e in ex if e['type'] == t) for t in ('choose','gap','error','order','translate','read')})
print("celkem", total)
