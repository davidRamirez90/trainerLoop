"""Build a portable gallery from the production Compose screenshot references."""
from pathlib import Path
import base64
import html

root = Path(__file__).resolve().parents[3]
shots = root / 'android/app/src/test/screenshots'
cards = []
for shot in sorted(shots.glob('focus-*.png')):
    name = shot.stem.removeprefix('focus-').replace('-', ' ').title()
    encoded = base64.b64encode(shot.read_bytes()).decode()
    cards.append(f'<article><h2>{html.escape(name)}</h2><button class="capture" aria-label="Enlarge {html.escape(name)}"><img src="data:image/png;base64,{encoded}" alt="Native Compose capture: {html.escape(name)}" loading="lazy"></button><small>{shot.name}</small></article>')
page = '''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>TrainerLoop · Focus migration</title><style>
*{box-sizing:border-box}body{margin:0;background:#f5f4ed;color:#172b25;font:16px system-ui}header{max-width:1200px;margin:auto;padding:32px 24px}h1{font:48px Georgia;margin:16px 0}h2{font:24px Georgia}p{line-height:1.6;max-width:850px}a{color:#163e32}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:24px;max-width:1200px;margin:auto;padding:0 24px 40px}article{background:white;border-radius:24px;padding:20px;align-self:start}img{width:100%;height:auto;display:block}.capture{border:0;padding:0;background:transparent;width:100%;cursor:zoom-in}small{display:block;margin-top:12px;color:#526157;overflow-wrap:anywhere}dialog{border:0;padding:16px;border-radius:24px;max-width:95vw;max-height:95vh;background:#f5f4ed}dialog img{width:auto;max-width:90vw;max-height:83vh}dialog::backdrop{background:#101c17dd}#close{padding:12px 24px;margin-bottom:12px;border:0;border-radius:24px;background:#163e32;color:white;cursor:pointer}
</style><header><b>TRAINERLOOP · FOCUS</b><h1>One good ride.</h1><p>Production app migration to the selected Focus design. Today → Workouts → Preview → Player → Completion → History → Session detail. Today’s avatar opens Profile; equipment opens Devices. Back returns from detail screens, and Play begins recording.</p><p>Native Compose screenshots rendered with Roborazzi, then visually inspected. Fixtures include dark mode, loading/errors, empty states, landscape, and enlarged text. Long screens scroll; these captures show the visible viewport. Device connectivity and integrations use deterministic fixtures. The workout overflow menu is checked through Compose interaction assertions; its popup window is omitted because the host renderer cropped the wrong window.</p><p><a href="README.md">Design specification and verification notes</a> · <a href="../../reports/app-flow-report.html">Interactive app flow documentation</a></p></header><main>'''
page += ''.join(cards)
page += '''</main><dialog><button id="close">Close</button><img alt="Enlarged app screen"></dialog><script>const d=document.querySelector('dialog');document.querySelectorAll('.capture').forEach(b=>b.onclick=()=>{d.querySelector('img').src=b.querySelector('img').src;d.querySelector('img').alt=b.querySelector('img').alt;d.showModal()});document.querySelector('#close').onclick=()=>d.close();d.onclick=e=>{if(e.target===d)d.close()};</script></html>'''
Path(__file__).with_name('index.html').write_text(page)
print(f'Embedded {len(cards)} production captures')
