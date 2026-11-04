#!/usr/bin/env python3
"""Package the exact runtime viewer + GLBs into one offline, shareable HTML file.
No server, build system, CDN or model download is needed when opening the output.
The output is a review artifact, not an Android WebView origin/security configuration.
"""
import argparse
import base64
import json
import re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'app-android/src/main/assets/atelier'


def main():
    ap=argparse.ArgumentParser();ap.add_argument('--out',type=Path,default=ROOT/'dist/Crocodyl-Equipment-Studio.html');args=ap.parse_args()
    h=(ASSETS/'index.html').read_text()
    h=re.sub(r'<meta http-equiv="Content-Security-Policy"[^>]*>', '<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; img-src data: blob:; style-src \'unsafe-inline\'; script-src \'unsafe-inline\'; connect-src \'none\'; object-src \'none\'">',h)
    h=h.replace('<link rel="stylesheet" href="atelier.css"><script src="atelier.js" defer></script>', '<style>'+(ASSETS/'atelier.css').read_text()+'</style>')
    models={'models/'+p.name:base64.b64encode(p.read_bytes()).decode('ascii') for p in sorted((ASSETS/'models').glob('*.glb'))}
    if set(models)!={'models/recurve.glb','models/arrow.glb','models/target.glb'}:raise RuntimeError('Bake all three model assets first')
    shim='const embeddedModels='+json.dumps(models)+';window.fetch=async function(path){const data=embeddedModels[String(path)];if(!data)throw new Error("No embedded asset: "+path);return new Response(Uint8Array.from(atob(data),c=>c.charCodeAt(0)),{status:200,headers:{"Content-Type":"model/gltf-binary"}});};'
    h=h.replace('</body>', '<script>'+shim+'</script><script>'+(ASSETS/'atelier.js').read_text()+'</script></body>')
    args.out.parent.mkdir(parents=True,exist_ok=True);args.out.write_text(h)
    print(args.out,args.out.stat().st_size)

if __name__=='__main__':main()
