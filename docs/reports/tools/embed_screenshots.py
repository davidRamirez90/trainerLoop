#!/usr/bin/env python3
"""Embed captured, unmodified Android PNGs into the current HTML flow report."""
import base64
import json
import hashlib
import re
import struct
from pathlib import Path

root = Path(__file__).resolve().parents[1]
report = root / 'app-flow-report.html'
html = report.read_text()
views = json.loads(re.search(r'const views=(.*?);\nconst maps=', html, re.S).group(1))
views['permission']['entry'] = 'App launch when Bluetooth-scanning permission is missing; Grant action in Devices.'
views['home']['notes'].append('First launch may show Android Bluetooth and notification permission prompts before Home is usable.') if 'First launch may show Android Bluetooth and notification permission prompts before Home is usable.' not in views['home']['notes'] else None
html = re.sub(r'const views=.*?;\nconst maps=', lambda _: 'const views=' + json.dumps(views, ensure_ascii=False) + ';\nconst maps=', html, flags=re.S)
folder = root / 'screenshots' / 'current'
fixture_ids = {'home', 'history', 'session', 'complete', 'ftp', 'ftpPush', 'share'}
notes = {
    'home': 'Current app dashboard with a locally seeded sample ride; no trainer connected.',
    'history': 'Real History screen with a synthetic, locally seeded 20-minute ride.',
    'session': 'Real Session Detail using the same synthetic ride telemetry.',
    'complete': 'Real Completion screen initialized through its normal file payload with synthetic telemetry.',
    'ftp': 'Real FTP result card computed from synthetic telemetry; this is not a measured FTP test.',
    'ftpPush': 'Real inline prompt after accepting the sample FTP. Placeholder credentials; no external write performed.',
    'share': 'Android sharing UI opened by the real Share FIT action for a synthetic-telemetry FIT file.',
    'permission': 'Real Android location permission prompt required for Bluetooth scanning on Android 11. The app requests these at first launch as well as from Devices.',
    'import': 'Real Android document picker opened from the Workouts import action.',
    'devices': 'Real Devices sheet on the emulator; no physical trainer or HR sensor is attached.',
    'running': 'Real running Player. No physical trainer is connected, so sensor readings are unavailable.',
    'paused': 'Real paused Player after an in-app Pause action; no physical trainer is connected.',
    'controls': 'Real portrait control sheet expanded during the paused ride.',
    'stats': 'Real Ride stats pager view; no physical trainer is connected.',
    'landscape': 'Real Player after device orientation changes to landscape; no physical trainer is connected.',
    'stop': 'Real End workout confirmation opened from a progressed, paused ride.',
}
shots = {}
for id, view in views.items():
    file = folder / f'{id}.png'
    if not file.exists():
        raise SystemExit(f'Missing actual screenshot: {file}')
    data = file.read_bytes()
    if not data.startswith(b'\x89PNG\r\n\x1a\n'):
        raise SystemExit(f'Invalid PNG: {file}')
    shots[id] = {
        'src': 'data:image/png;base64,' + base64.b64encode(data).decode(),
        'file': f'screenshots/current/{id}.png',
        'note': notes.get(id, 'Real app UI captured on the Android emulator; no physical training devices attached.'),
        'fixture': id in fixture_ids,
        'sha256': hashlib.sha256(data).hexdigest(),
        'pixels': list(struct.unpack('>II', data[16:24])),
    }
def write_manifest():
    manifest = {
        'captured_date': '2026-10-10',
        'android_api': 30,
        'android_version': '11',
        'app_version': '0.1.0 (debug, current working tree)',
        'device': 'Pixel 6 AVD, software emulation, portrait 720×1600, density 320',
        'method': 'UiAutomation.takeScreenshot of production Compose screens and Android system UI; adb screencap for initial permission',
        'views': {id: {k: v for k, v in shot.items() if k != 'src'} for id, shot in shots.items()},
    }
    (root / 'screenshots' / 'capture-manifest.json').write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + '\n')

if 'const screenshots=' in html:
    html = re.sub(r'const screenshots=.*?;\nconst views=', lambda _: 'const screenshots=' + json.dumps(shots, ensure_ascii=False) + ';\nconst views=', html, flags=re.S)
    report.write_text(html)
    write_manifest()
    print(f'Refreshed {len(shots)} actual captures')
    raise SystemExit(0)
style = '''
.shot-preview{padding:0;border:1px solid var(--line);background:#f1f4f6;display:block;width:100%;margin:14px 0;overflow:hidden;border-radius:9px}.shot-preview img{display:block;width:100%;max-height:430px;object-fit:contain}.shot-caption{font-size:11px!important;color:var(--muted)}.shot-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:18px;margin-top:22px}.shot-card{border:1px solid var(--line);border-radius:10px;overflow:hidden;background:#fff;min-width:0}.shot-card h3{margin:14px 14px 4px}.shot-card p{font-size:12px;color:var(--muted);margin:0 14px 14px}.shot-card .shot-preview{margin:0;border:0;border-radius:0;height:360px}.shot-card img{height:360px;width:100%;object-fit:contain}.shot-card .inspect-shot{margin:0 14px 14px;padding:6px 10px;font-size:12px}dialog{max-width:95vw;max-height:96vh;border:0;border-radius:12px;padding:18px;background:white;color:var(--ink)}dialog::backdrop{background:#0d202ed9}dialog .dialog-head{display:flex;justify-content:space-between;align-items:center;gap:30px;margin-bottom:12px}dialog h2{margin:0;font-size:18px}dialog img{display:block;max-width:85vw;max-height:75vh;width:auto;height:auto;margin:auto}dialog p{font-size:12px;max-width:700px;margin:12px auto 0}.shot-nav{color:var(--green);font-weight:650;text-decoration:none;border:1px solid var(--line);padding:9px 14px;border-radius:8px;background:white}.capture-note{font-size:13px;color:var(--muted)}
@media(max-width:1000px){.shot-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:600px){.shot-grid{grid-template-columns:1fr}.shot-card .shot-preview,.shot-card img{height:420px}}@media print{.shot-nav,dialog{display:none}.shot-grid{grid-template-columns:repeat(3,minmax(0,1fr))}.shot-card{break-inside:avoid}.shot-card .shot-preview,.shot-card img{height:100mm}.inspect-shot{display:none}.shot-card p{font-size:10px}.shot-card h3{font-size:12px}}
'''
html = html.replace('</style>', style + '</style>')
html = html.replace('<span class="pill">Offline · self-contained HTML</span>', '<span class="pill">25 real Android captures</span><span class="pill">Offline · screenshots embedded</span>')
html = html.replace('<div class="tools">', '<div class="tools"><a class="shot-nav" href="#screenshots">Screenshots</a>', 1)
html = html.replace('<section class="notes">', '''<section class="notes" id="screenshots"><h2>Actual app screenshots</h2><p class="capture-note">Captured from the current Android app and Android-owned views on an API 30 (Android 11) software emulator. These are unmodified screen captures, not mockups or Compose previews. History, completion and FTP views use synthetic fixture telemetry, clearly labeled below. The player has no physical trainer attached. Tap an image to enlarge it.</p><p class="capture-note">Capture setup and reproducible commands: <a href="README.md">documentation notes</a>. The PNG originals are retained alongside this portable HTML report.</p><div class="shot-grid" id="shotGallery"></div></section><section class="notes">''', 1)
html = html.replace('</main>\n<script>', '</main><dialog id="shotDialog"><div class="dialog-head"><h2 id="shotTitle"></h2><button id="closeShot">Close</button></div><img id="shotImage" alt=""><p id="shotNote"></p></dialog>\n<script>')
html = html.replace('const views=', 'const screenshots=' + json.dumps(shots, ensure_ascii=False) + ';\nconst views=', 1)
html = html.replace('<h2>${escapeHtml(v.title)}</h2><p>${escapeHtml(v.summary)}</p>', '<h2>${escapeHtml(v.title)}</h2>${shotPreview(id)}<p class="shot-caption">${escapeHtml(screenshots[id].note)}</p><p>${escapeHtml(v.summary)}</p>', 1)
html = html.replace('let currentMap=', '''function shotPreview(id){const s=screenshots[id];return `<button class="shot-preview" onclick="openShot('${id}')" aria-label="Enlarge actual screenshot of ${escapeHtml(views[id].title)}"><img loading="lazy" src="${s.src}" alt="Actual Android capture: ${escapeHtml(views[id].title)}"></button>`;}
function openShot(id){document.querySelector('#shotTitle').textContent=views[id].title;document.querySelector('#shotImage').src=screenshots[id].src;document.querySelector('#shotImage').alt='Actual Android capture: '+views[id].title;document.querySelector('#shotNote').textContent=screenshots[id].note;document.querySelector('#shotDialog').showModal();}
document.querySelector('#closeShot').addEventListener('click',()=>document.querySelector('#shotDialog').close());
document.querySelector('#shotDialog').addEventListener('click',e=>{if(e.target===e.currentTarget)e.currentTarget.close();});
let currentMap=''')
html = html.replace('renderMaps();catalog();inspect(', '''document.querySelector('#shotGallery').innerHTML=Object.values(views).map(v=>`<article class="shot-card">${shotPreview(v.id)}<h3>${escapeHtml(v.title)}</h3><p>${escapeHtml(screenshots[v.id].note)}</p><button class="inspect-shot" onclick="inspect('${v.id}');document.querySelector('.layout').scrollIntoView({behavior:'smooth'});">View flow details</button></article>`).join('');
renderMaps();catalog();inspect(''')
html = html.replace('Source reviewed · 10 October 2026', 'Source &amp; captures · 10 October 2026')
# First-launch permissions were found while capturing the actual runtime.
report.write_text(html)
write_manifest()
print(f'Embedded {len(shots)} actual captures; HTML size {report.stat().st_size:,} bytes')
