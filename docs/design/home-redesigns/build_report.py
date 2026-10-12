#!/usr/bin/env python3
"""Build an offline Home design review from inspected native renders and the spec."""
import base64
import html
from pathlib import Path
import re
import shutil

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
SHOTS = ROOT / 'android/app/src/test/screenshots'


def inline(value):
    value = html.escape(value)
    value = re.sub(r'\[([^\]]+)\]\((https://[^)]+)\)', r'<a href="\2" target="_blank" rel="noreferrer">\1</a>', value)
    value = re.sub(r'`([^`]+)`', r'<code>\1</code>', value)
    return re.sub(r'\*\*([^*]+)\*\*', r'<strong>\1</strong>', value)


def render_markdown(text):
    # This report's controlled subset: headings, paragraphs, lists, tables, code.
    lines = text.splitlines()
    out = []
    i = 0
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            i += 1
            continue
        if line.startswith('```'):
            code = []
            i += 1
            while i < len(lines) and not lines[i].startswith('```'):
                code.append(lines[i]); i += 1
            out.append('<pre><code>' + html.escape('\n'.join(code)) + '</code></pre>')
        elif line.startswith('|'):
            rows = []
            while i < len(lines) and lines[i].startswith('|'):
                cells = [c.strip() for c in lines[i].strip('|').split('|')]
                if not all(re.fullmatch(r'[:\- ]+', c) for c in cells):
                    tag = 'th' if not rows else 'td'
                    rows.append('<tr>' + ''.join(f'<{tag}>{inline(c)}</{tag}>' for c in cells) + '</tr>')
                i += 1
            out.append('<div class="table-wrap"><table>' + ''.join(rows) + '</table></div>')
            continue
        elif line.startswith('#'):
            level = min(len(line) - len(line.lstrip('#')), 4)
            out.append(f'<h{level}>{inline(line.lstrip("# "))}</h{level}>')
        elif line.startswith('- '):
            items = []
            while i < len(lines) and lines[i].startswith('- '):
                items.append('<li>' + inline(lines[i][2:]) + '</li>'); i += 1
            out.append('<ul>' + ''.join(items) + '</ul>')
            continue
        else:
            out.append('<p>' + inline(line) + '</p>')
        i += 1
    return '\n'.join(out)


def data_image(path):
    return 'data:image/png;base64,' + base64.b64encode(path.read_bytes()).decode('ascii')


concepts = [
    ('focus', '01', 'Focus', 'One good ride', 'A calm session-first Home. The planned workout leads; equipment becomes a compact supporting row. Profile moves to the avatar and the last ride stays visible.', 'Today · Workouts · History', 'forest', ['Today’s plan', 'Open workout → idle Player', 'Play → record ride', 'Completion → Done → Today']),
    ('console', '02', 'Console', 'Prepare, then execute', 'A technical training instrument. Device status, FTP, duration, and interval structure are prominent, with direct Library and FTP-test shortcuts.', 'Ride · Library · History · Setup', 'lime', ['Device / FTP check', 'Load workout → idle Player', 'Play → record ride', 'Completion → Done → Ride']),
    ('journal', '03', 'Journal', 'The next ride and the last', 'A warm daily record. Today’s plan and the previous saved ride share the screen, giving equal clarity to training and reviewing.', 'Journal · Workouts · History', 'cobalt', ['Today’s planned entry', 'Open workout → idle Player', 'Play → record ride', 'Completion → Done → Journal']),
]
cards = []
flows = []
for slug, number, title, subtitle, description, navigation, accent, steps in concepts:
    filename = f'proposal-{int(number)}-{slug}.png'
    shutil.copy2(SHOTS / filename, HERE / filename)
    image = data_image(HERE / filename)
    cards.append(f'''<article class="concept {accent}" id="{slug}"><div class="eyebrow">DIRECTION {number}</div>
    <h2>{title}</h2><p class="subtitle">{subtitle}</p>
    <button class="image-button" aria-label="Enlarge {title} Home screen" onclick="enlarge(this)"><img src="{image}" alt="Native Compose render of the {title} Home proposal" width="1233" height="2673"></button>
    <p>{description}</p><p class="navspec">{navigation}</p></article>''')
    flow = '<span class="arrow" aria-hidden="true">↓</span>'.join(f'<div class="flow-node">{html.escape(s)}</div>' for s in steps)
    flows.append(f'<article class="flow"><h3>{title}</h3>{flow}<p>Alternative workout: Library → Preview → idle Player.<br>Latest saved ride → Session Detail.<br>Devices → existing connection dialog.</p></article>')

spec = render_markdown((HERE / 'DESIGN_SPEC.md').read_text())
current = data_image(SHOTS / 'home-planned-light.png')
page = '''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>TrainerLoop — Home design directions</title><style>
*{box-sizing:border-box}body{margin:0;background:#f2f1ec;color:#202c29;font:16px/1.65 system-ui,sans-serif}a{color:#243cb5}header,main{max-width:1440px;margin:auto;padding:36px}header{padding-top:54px}header h1{font-size:clamp(32px,4vw,56px);letter-spacing:-.04em;line-height:1.1;margin:12px 0 20px;max-width:950px}.eyebrow{font-size:12px;font-weight:750;letter-spacing:.12em}.lede{max-width:800px;color:#58635f}.toolbar{display:flex;gap:10px;flex-wrap:wrap;margin-top:24px}.toolbar a,.toolbar button{background:white;padding:10px 18px;border:1px solid #ccd3ca;border-radius:24px;text-decoration:none;color:inherit;font:inherit}.grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:24px}.concept{padding:24px;border-radius:20px;background:white;border-top:5px solid #163e32}.concept.lime{border-color:#799c40}.concept.cobalt{border-color:#243cb5}.concept h2{font-size:30px;margin:8px 0 0}.subtitle{color:#67716c;margin:0 0 24px}.concept img{display:block;width:100%;height:auto;border-radius:12px}.image-button{border:0;padding:0;background:none;display:block;width:100%;cursor:zoom-in}.concept p{font-size:15px}.navspec{border-top:1px solid #ddd;padding-top:12px;font-size:13px!important;font-weight:650}.notice{padding:18px 24px;border-radius:12px;background:#e2e8dc;margin:28px 0}.flows{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:24px;margin:24px 0}.flow{padding:20px;background:white;border-radius:12px}.flow-node{border:1px solid #ccd3ca;padding:12px;border-radius:8px;text-align:center}.arrow{display:block;text-align:center;color:#687564}.flow p{font-size:13px;color:#58635f}.spec{background:white;padding:36px;border-radius:20px;margin-top:28px}.spec h1{font-size:30px}.spec h2{border-top:1px solid #ddd;padding-top:26px;margin-top:40px;line-height:1.3}.spec h3{font-size:20px}.spec p,.spec ul{max-width:1000px}.table-wrap{overflow:auto}table{border-collapse:collapse;width:100%;font-size:14px}th,td{text-align:left;padding:14px;border:1px solid #dfe3da;vertical-align:top}th{background:#eef1e9}code{font-size:13px;overflow-wrap:anywhere}pre{background:#eef1e9;padding:16px;overflow:auto}details{margin:24px 0;padding:20px;background:white;border-radius:12px}summary{cursor:pointer;font-weight:700}details img{max-width:340px;width:100%;margin-top:20px}dialog{border:0;border-radius:16px;padding:16px;max-width:95vw;max-height:95vh;background:#fff}dialog::backdrop{background:#000b}dialog img{display:block;max-width:85vw;max-height:82vh;width:auto;height:auto}dialog button{display:block;margin:0 0 12px auto;padding:8px 16px;font:inherit}footer{padding:32px;color:#58635f;text-align:center}.grid.single{grid-template-columns:minmax(280px,430px);justify-content:center}.grid.single .concept{display:none}.grid.single .concept.selected{display:block}
@media(max-width:850px){.grid,.flows{grid-template-columns:1fr}.concept{max-width:470px;margin:auto}header,main{padding:24px}.spec{padding:24px}}
@media print{.toolbar,dialog{display:none}.grid{grid-template-columns:repeat(3,1fr)}header,main{padding:10px}.concept{padding:12px}.spec{padding:12px}details{display:none}body{background:white}}
</style></head><body><header><div class="eyebrow">TRAINERLOOP / HOME DESIGN REVIEW / 11 OCT 2026</div><h1>Three ways to begin<br>the same training session.</h1><p class="lede">One retained feature set, three priorities: launch with less friction, prepare like an instrument, or see training as a daily record. Every screen below is a native Compose render produced without an emulator.</p><nav class="toolbar" aria-label="Review controls"><button onclick="show('all')">Compare all</button><button onclick="show('focus')">Focus</button><button onclick="show('console')">Console</button><button onclick="show('journal')">Journal</button><a href="#research-spec">Research &amp; full specification</a></nav></header><main>
'''
page += '<section class="grid" id="designs" aria-label="Three Home proposals">' + ''.join(cards) + '</section>'
page += '''<p class="notice"><strong>Recommendation: Focus.</strong> Its hierarchy best matches the goal of keeping essential training functionality. These are review concepts with sample data and inert controls; production Home is unchanged. Click a screen to enlarge it.</p>'''
page += '<h2>Proposed launch paths</h2><section class="flows">' + ''.join(flows) + '</section>'
page += f'<details><summary>Compare with current production Home (light)</summary><p>Existing production layout, captured with a planned-workout fixture. Its screenshot excludes the app navigation; proposal screenshots include their proposed navigation.</p><img src="{current}" alt="Current production Home, light theme"></details>'
page += '<section class="spec" id="research-spec">' + spec + '</section>'
page += '''</main><footer>Offline report · embedded PNGs · no external assets or scripts · design proposals only</footer>
<dialog id="zoom"><button onclick="document.getElementById('zoom').close()">Close</button><img alt="Enlarged Home proposal"></dialog>
<script>
function show(id){const grid=document.getElementById('designs');grid.classList.toggle('single',id!=='all');grid.querySelectorAll('.concept').forEach(c=>c.classList.toggle('selected',c.id===id));document.querySelectorAll('.toolbar button').forEach((b,i)=>b.setAttribute('aria-pressed',String(['all','focus','console','journal'][i]===id)));}
function enlarge(button){const d=document.getElementById('zoom');const source=button.querySelector('img');d.querySelector('img').src=source.src;d.querySelector('img').alt=source.alt;d.showModal();}
</script></body></html>'''
(HERE / 'index.html').write_text(page)
print(f'Built {HERE / "index.html"} ({len(page):,} characters)')
