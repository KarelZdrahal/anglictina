#!/usr/bin/env python3
"""Sestaví z lesson_XX.json pracovní list (PDF), klíč (PDF) a poslech (MP3).
Použití: python3 build_lesson.py <lesson.json> <out_dir> [--voices DIR]"""
import json, sys, os, html, subprocess, tempfile, datetime

src, out = sys.argv[1], sys.argv[2]
voices = sys.argv[sys.argv.index('--voices')+1] if '--voices' in sys.argv else '/tmp/voices'
d = json.load(open(src))
os.makedirs(out, exist_ok=True)
N = d['week']; tag = f"Lekce_{N:02d}"
dt = datetime.date.fromisoformat(d['date'])
L = 'abcdefghij'

CSS = """
@page { size: A4; margin: 16mm 15mm 16mm 15mm; }
* { box-sizing: border-box; }
body { font-family: 'DejaVu Sans', sans-serif; font-size: 10pt; color: #1d2433; line-height: 1.45; }
.head { border-bottom: 3px solid #2f5d9b; padding-bottom: 6px; margin-bottom: 10px; display:flex; justify-content:space-between; align-items:flex-end; }
.head h1 { margin: 0; font-size: 20pt; color: #2f5d9b; }
.head .sub { font-size: 11pt; color: #555; }
.meta { text-align:right; font-size: 9pt; color:#555; }
.goals { background:#f3f6fb; border-left: 4px solid #2f5d9b; padding: 6px 10px; margin-bottom: 10px; font-size:9.5pt; }
.goals ul { margin: 2px 0 0 0; padding-left: 18px; }
h2.part { background:#2f5d9b; color:#fff; padding: 5px 10px; font-size: 12.5pt; margin: 16px 0 8px; border-radius: 4px; break-after: avoid; }
h2.part span { float:right; font-weight: normal; font-size: 10pt; }
.block { margin: 8px 0 12px; break-inside: avoid; }
.block.long { break-inside: auto; }
h3 { font-size: 11pt; margin: 0 0 3px; color:#2f5d9b; break-after: avoid; }
.pair { display:inline-block; background:#e8833a; color:#fff; font-size: 7.5pt; font-weight:bold; padding: 1px 6px; border-radius: 8px; margin-left: 6px; vertical-align: middle; letter-spacing:.3px; }
.instr { font-style: italic; color:#444; margin: 0 0 5px; }
ol { margin: 3px 0; padding-left: 22px; } li { margin: 2px 0; }
table.v { width:100%; border-collapse: collapse; font-size: 9pt; }
table.v th { background:#dfe8f5; text-align:left; padding:3px 5px; }
table.v td { border-bottom: 1px solid #e3e6ec; padding: 3px 5px; vertical-align: top; }
table.v td.ipa { color:#6a4fa3; white-space:nowrap; }
table.v td.ck { width: 14px; }
table.g { width:100%; border-collapse: collapse; margin: 4px 0 6px; font-size:9.3pt; }
table.g th { background:#dfe8f5; padding:4px; } table.g td { border:1px solid #d4dbe6; padding:4px 6px; vertical-align:top; width:50%; }
.gbox { background:#fbf8ef; border:1px solid #e6d9a8; border-radius:6px; padding: 8px 10px; font-size:9.5pt; }
.gbox p { margin: 4px 0; }
.cols { display:flex; gap: 18px; } .cols > div { flex:1; }
.text { border:1px solid #d4dbe6; border-radius:6px; padding: 8px 12px; background:#fcfdff; }
.text h4 { margin: 0 0 4px; text-align:center; font-size: 11pt; }
.text p { margin: 4px 0; text-align: justify; }
.line { display:inline-block; border-bottom: 1px solid #999; width: 90px; }
.tf { white-space:nowrap; color:#666; }
.card { border: 2px dashed #e8833a; border-radius: 6px; padding: 6px 10px; flex:1; font-size:9.3pt; }
.card b { color:#e8833a; }
.phr { background:#f3f6fb; border-radius:6px; padding: 6px 10px; margin-top: 6px; font-size:9.3pt; columns: 2; }
.phr div { break-inside: avoid; }
.write { border:1px solid #ccc; height: 48mm; border-radius:4px; background: repeating-linear-gradient(#fff, #fff 7.5mm, #e6e6e6 7.5mm, #e6e6e6 7.8mm); }
.ans { color: #1f7a3a; font-weight: bold; }
.key li { margin: 1px 0; }
.transcript p { margin: 2px 0; } .transcript b { display:inline-block; width: 46px; color:#2f5d9b; }
.foot { margin-top: 14px; font-size: 8pt; color:#888; text-align:center; }
"""

def e(s): return html.escape(s)
def pairtag(b): return '<span class="pair">PRÁCE VE DVOJICI</span>' if b.get('pair') else ''
def h3(b): return f"<h3>{e(b['label'])}{pairtag(b)}</h3>" + (f"<p class='instr'>{b['instr']}</p>" if b.get('instr') else '')

def vocab_rows(items, check=True):
    r = "<table class='v'><tr>" + ("<th></th>" if check else "") + "<th>English</th><th>Výslovnost</th><th>Česky</th><th>Example</th></tr>"
    for v in items:
        r += "<tr>" + ("<td class='ck'>☐</td>" if check else "") + f"<td><b>{e(v['en'])}</b></td><td class='ipa'>/{e(v['ipa'])}/</td><td>{e(v['cz'])}</td><td><i>{e(v['ex'])}</i></td></tr>"
    return r + "</table>"

def student(b):
    t = b['type']
    if t == 'discussion':
        return h3(b) + "<ol>" + "".join(f"<li>{e(q)}</li>" for q in b['items']) + "</ol>"
    if t == 'vocab_table':
        return h3(b) + vocab_rows(d['vocab_core'] if b['which']=='core' else d['vocab_ext'], check=b['which']=='core')
    if t == 'match':
        l = "<ol>" + "".join(f"<li>{e(x)}</li>" for x in b['left']) + "</ol>"
        r = "<ol type='a'>" + "".join(f"<li>{e(x)}</li>" for x in b['right']) + "</ol>"
        return h3(b) + f"<div class='cols'><div style='flex:2'>{l}</div><div>{r}</div></div>"
    if t in ('task','writing'):
        s = h3(b)
        if t == 'writing': s += "<div class='write'></div>"
        return s
    if t == 'grammar_box':
        return f"<h3>{e(b['label'])}</h3><div class='gbox'>{b['html']}</div>"
    if t in ('choose','gap','error'):
        return h3(b) + "<ol>" + "".join(f"<li>{e(i['q'])}" + ("<br><span class='line' style='width:85%'></span>" if t=='error' else "") + "</li>" for i in b['items']) + "</ol>"
    if t == 'reading':
        s = h3(b) + f"<div class='text'><h4>{e(b['title'])}</h4>" + "".join(f"<p>{e(p)}</p>" for p in b['paras']) + "</div>"
        s += "<ol>" + "".join(f"<li>{e(i['q'])} <span class='tf'>T / F</span></li>" for i in b['tf']) + "</ol>"
        s += "<p class='instr'>Discuss with your partner:</p><ol start='%d'>" % (len(b['tf'])+1) + "".join(f"<li>{e(i['q'])}</li>" for i in b['open']) + "</ol>"
        return s
    if t == 'listening':
        s = h3(b) + "<ol>"
        for i in b['items']:
            s += f"<li>{e(i['q'])}"
            if 'options' in i:
                s += "<br>" + " &nbsp; ".join(f"{L[k]}) {e(o)}" for k,o in enumerate(i['options']))
            else:
                s += "<br><span class='line' style='width:85%'></span>"
            s += "</li>"
        return s + "</ol>"
    if t == 'transform':
        s = h3(b) + "<div class='cols' style='flex-wrap:wrap;gap:4px 14px'>" + "".join(f"<div style='flex:0 0 31%'>{k+1}. {e(i['q'])} → <span class='line'></span></div>" for k,i in enumerate(b['items'])) + "</div>"
        s += "<ol type='a'>" + "".join(f"<li>{e(i['q'])}</li>" for i in b['sentences']) + "</ol>"
        return s
    if t == 'roleplay':
        s = h3(b) + "<div class='cols'>" + "".join(f"<div class='card'><b>{e(c['who'])}</b><br>{e(c['text'])}</div>" for c in b['cards']) + "</div>"
        s += "<div class='phr'><b>Useful phrases</b>" + "".join(f"<div>• {e(p)}</div>" for p in b['phrases']) + "</div>"
        return s
    if t == 'memory':
        return h3(b)
    if t == 'memory_list':
        allv = d['vocab_core'] + d['vocab_ext']; half = (len(allv)+1)//2
        def col(items): return "<table class='v' style='font-size:8.3pt'>" + "".join(f"<tr><td class='ck'>☐</td><td><b>{e(v['en'])}</b></td><td class='ipa'>/{e(v['ipa'])}/</td><td>{e(v['cz'])}</td></tr>" for v in items) + "</table>"
        return f"<div class='cols' style='gap:10px'><div>{col(allv[:half])}</div><div>{col(allv[half:])}</div></div>"
    return ''

def key(b):
    t = b['type']; lab = f"<h3>{e(b.get('label',''))}</h3>"
    if t == 'match': return lab + "<p class='ans'>" + ", ".join(b['answers']) + "</p>"
    if t in ('choose','gap','error'):
        return lab + "<ol class='key'>" + "".join(f"<li><span class='ans'>{e(i['a'])}</span></li>" for i in b['items']) + "</ol>"
    if t == 'reading':
        s = lab + "<ol class='key'>" + "".join(f"<li><span class='ans'>{i['a']}</span> – {e(i['q'])}</li>" for i in b['tf']) + "</ol>"
        s += "<ol class='key' start='%d'>" % (len(b['tf'])+1) + "".join(f"<li>{e(i['a'])}</li>" for i in b['open']) + "</ol>"
        return s
    if t == 'listening':
        s = lab + "<ol class='key'>" + "".join(f"<li><span class='ans'>{e(i['a'])}</span>" + (f" ({e(i['options'][L.index(i['a'])])})" if 'options' in i else '') + "</li>" for i in b['items']) + "</ol>"
        s += f"<h3>Transcript – {e(d['listening']['title'])}</h3><div class='transcript'>" + "".join(f"<p><b>{e(l['sp'])}:</b> {e(l['text'])}</p>" for l in d['listening']['lines']) + "</div>"
        return s
    if t == 'transform':
        return lab + "<p>" + ", ".join(f"{k+1}. <span class='ans'>{e(i['a'])}</span>" for k,i in enumerate(b['items'])) + "</p><p>" + ", ".join(f"{L[k]}) <span class='ans'>{e(i['a'])}</span>" for k,i in enumerate(b['sentences'])) + "</p>"
    if t in ('discussion','task','roleplay','writing'):
        return lab + "<p><i>Volná odpověď. Lektor/partner sleduje správné použití času a slovní zásoby z lekce.</i></p>"
    return ''

def header(kind):
    return f"""<div class='head'><div><div class='sub'>Angličtina B1 → B1+ · Lekce {N} {kind}</div><h1>{e(d['title_en'])}</h1><div class='sub'>{e(d['title_cz'])}</div></div>
<div class='meta'>{dt.day}. {dt.month}. {dt.year}<br>Úroveň {e(d['level'])} · cca 60 min<br>{e(d['grammar_focus'])}</div></div>"""

def page(body): return f"<!doctype html><html><head><meta charset='utf-8'><style>{CSS}</style></head><body>{body}</body></html>"

s = header('') + "<div class='goals'><b>Cíle lekce:</b><ul>" + "".join(f"<li>{e(g)}</li>" for g in d['goals']) + "</ul></div>"
k = header('– klíč a poznámky pro lektora')
for p in d['parts']:
    hp = f"<h2 class='part'>{p['n']}. {e(p['title'])} · {e(p['title_cz'])}<span>cca {p['minutes']} min</span></h2>"
    s += hp; 
    kb = "".join(key(b) for b in p['blocks'])
    if kb: k += hp + kb
    for b in p['blocks']:
        long = b['type'] in ('vocab_table','memory_list','reading')
        s += f"<div class='block{' long' if long else ''}'>{student(b)}</div>"
k += f"<h2 class='part'>Navázání na další lekci</h2><p>{e(d['next_links'])}</p>"
s += f"<div class='foot'>Poslech: {tag}_poslech.mp3 · Klíč: {tag}_klic.pdf · Slovíčka procvičuj denně v aplikaci.</div>"

stud_html = os.path.join(out, f"{tag}.html"); key_html = os.path.join(out, f"{tag}_klic.html")
open(stud_html,'w').write(page(s)); open(key_html,'w').write(page(k))

pdfjobs = [(stud_html, os.path.join(out, f"{tag}_{d['title_en'].replace(' & ','_and_').replace(' ','_')}.pdf")), (key_html, os.path.join(out, f"{tag}_klic.pdf"))]
js = """const {chromium}=require('playwright');(async()=>{const b=await chromium.launch();const p=await b.newPage();
for (const [h,o] of JSON.parse(process.argv[2])){await p.goto('file://'+h);await p.pdf({path:o,format:'A4',printBackground:true,displayHeaderFooter:true,headerTemplate:'<span></span>',
footerTemplate:"<div style='font-size:7pt;width:100%;text-align:center;color:#999'><span class='pageNumber'></span> / <span class='totalPages'></span></div>",
margin:{top:'14mm',bottom:'14mm',left:'14mm',right:'14mm'}});}await b.close();})();"""
jf = os.path.join(tempfile.mkdtemp(), 'pdf.js'); open(jf,'w').write(js)
subprocess.run(['node', jf, json.dumps([[os.path.abspath(h), os.path.abspath(o)] for h,o in pdfjobs])], check=True)
os.remove(stud_html); os.remove(key_html)

# Poslech
V = {'male': os.path.join(voices,'en-us-ryan-medium.onnx'), 'female': os.path.join(voices,'en-us-amy-low.onnx'), 'male2': os.path.join(voices,'en-gb-alan-low.onnx')}
tmp = tempfile.mkdtemp(); parts = []
for i, l in enumerate(d['listening']['lines']):
    w = os.path.join(tmp, f"{i:03d}.wav")
    subprocess.run(['piper','-m',V[l['voice']],'-f',w,'--length_scale','1.08','--sentence_silence','0.25'], input=l['text'].encode(), check=True, capture_output=True)
    n = os.path.join(tmp, f"{i:03d}n.wav")
    subprocess.run(['ffmpeg','-y','-loglevel','error','-i',w,'-ar','22050','-ac','1','-af','apad=pad_dur=0.6',n], check=True)
    parts.append(n)
lst = os.path.join(tmp,'l.txt'); open(lst,'w').write("".join(f"file '{p}'\n" for p in parts))
subprocess.run(['ffmpeg','-y','-loglevel','error','-f','concat','-safe','0','-i',lst,'-af','loudnorm','-codec:a','libmp3lame','-b:a','96k',os.path.join(out,f"{tag}_poslech.mp3")], check=True)
print("hotovo:", sorted(os.listdir(out)))
