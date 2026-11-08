#!/usr/bin/env python3
"""Převede lesson_XX.json (zdroj lekce) do formátu aplikace a aktualizuje data/index.json.
Použití: python3 tools/export_app.py <lesson.json> [<lesson.json> …]
Spouští se z kořene repozitáře."""
import json, sys, re, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, 'data')

def slug(s): return re.sub(r'[^a-z0-9]+', '-', s.lower()).strip('-')

def html_for_textview(h):
    h = re.sub(r"<tr>\s*<th>.*?</tr>", "", h, flags=re.S)
    h = re.sub(r"</td>\s*<td>", "<br>", h)
    h = re.sub(r"<tr>\s*<td>", "<p>• ", h)
    h = re.sub(r"</td>\s*</tr>", "</p>", h)
    h = re.sub(r"</?table[^>]*>", "", h)
    return h

def export(src):
    d = json.load(open(src))
    lid = d['id']
    vocab = [dict(id=f"{lid}-{slug(v['en'])}", en=v['en'], cz=v['cz'], ipa=v['ipa'], ex=v['ex']) for v in d['vocab_core']]
    vocab += [dict(id=f"{lid}-{slug(v['en'])}", en=v['en'], cz=v['cz'], ipa=v['ipa'], ex=v['ex'], ext=True) for v in d['vocab_ext']]
    ex = []; n = 0
    def add(t, q, a, options=None):
        nonlocal n; n += 1
        e = dict(id=f"{lid}-e{n:03d}", type=t, q=q, a=a)
        if options: e['options'] = options
        ex.append(e)
    grammar_html = ''
    for part in d['parts']:
        for b in part['blocks']:
            t = b['type']
            if t == 'grammar_box' and not grammar_html: grammar_html = html_for_textview(b['html'])
            elif t == 'choose':
                for i in b['items']: add('choose', i['q'], i['a'])
            elif t == 'gap':
                for i in b['items']: add('gap', i['q'], i['a'])
            elif t == 'error':
                for i in b['items']: add('error', i['q'], i['a'])
            elif t == 'transform':
                for i in b['items']: add('transform', i['q'], i['a'])
                for i in b.get('sentences', []): add('gap', i['q'], i['a'])
            elif t == 'match':
                # „kdo je …“ – výběr přídavného jména k popisu
                for k, a in enumerate(b['answers']):
                    li = int(a[:-1]) - 1; ri = 'abcdefghij'.index(a[-1])
                    add('choose', b['left'][li] + " → (" + " / ".join(sorted(set([b['right'][ri]] + [b['right'][(ri + j) % len(b['right'])] for j in (1, 3, 5)]))) + ")", b['right'][ri])
    for i in d.get('extra_practice', []):
        add(i['type'], i['q'], i['a'])
    app = dict(id=lid, week=d['week'], date=d['date'], level=d['level'], title_en=d['title_en'], title_cz=d['title_cz'],
               grammar_focus=d['grammar_focus'], grammar_html=grammar_html,
               audio=f"lessons/{lid}.mp3", pdf=f"lessons/{lid}.pdf", vocab=vocab, exercises=ex)
    os.makedirs(os.path.join(DATA, 'lessons'), exist_ok=True)
    json.dump(app, open(os.path.join(DATA, 'lessons', f'{lid}.json'), 'w'), ensure_ascii=False, indent=1)
    return lid, d['week'], len(vocab), len(ex)

def rebuild_index():
    idx_path = os.path.join(DATA, 'index.json')
    old = json.load(open(idx_path)) if os.path.exists(idx_path) else {}
    oldv = {e['file']: e.get('v', 1) for k in ('lessons', 'bank') for e in old.get(k, [])}
    oldh = {e['file']: e.get('h') for k in ('lessons', 'bank') for e in old.get(k, [])}
    import hashlib
    def entry(rel, **kw):
        h = hashlib.sha1(open(os.path.join(DATA, rel), 'rb').read()).hexdigest()[:12]
        v = oldv.get(rel, 1) + (1 if rel in oldh and oldh[rel] != h else 0)
        return dict(file=rel, v=v, h=h, **kw)
    lessons = []
    for f in sorted(os.listdir(os.path.join(DATA, 'lessons'))):
        if f.endswith('.json'):
            j = json.load(open(os.path.join(DATA, 'lessons', f)))
            lessons.append(entry(f'lessons/{f}', id=j['id'], week=j['week'], title=j['title_en']))
    bank = []
    bd = os.path.join(DATA, 'bank')
    if os.path.isdir(bd):
        for f in sorted(os.listdir(bd)):
            if f.endswith('.json'):
                j = json.load(open(os.path.join(bd, f)))
                bank.append(entry(f'bank/{f}', id=j['id'], topic=j['topic'], count=len(j['words'])))
    json.dump(dict(version=old.get('version', 0) + 1, lessons=lessons, bank=bank), open(idx_path, 'w'), ensure_ascii=False, indent=1)
    print(f"index: {len(lessons)} lekcí, {len(bank)} témat zásoby, {sum(b['count'] for b in bank)} slov")

if __name__ == '__main__':
    for s in sys.argv[1:]:
        print(export(s))
    rebuild_index()
