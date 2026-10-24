#!/usr/bin/env python3
"""Actual Chromium WebGL renders/checks. Does not relax the runtime CSP.
Requires playwright and Pillow; installed Chrome or Playwright Chromium.
"""
from __future__ import annotations
import functools
import http.server
import json
import shutil
import threading
from pathlib import Path
from playwright.sync_api import sync_playwright
from PIL import Image, ImageStat

ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'app-android/src/main/assets'
OUT=ROOT/'docs/crocodyl/atelier/qa'
# Diagnostic captures are separate from runtime posters and cinematic art.
CAPTURES=OUT/'canvas'

class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self,*_):pass


def eventually(page, expression, attempts=120):
    # CDP polling avoids wait_for_function's in-page eval, which strict CSP blocks.
    for _ in range(attempts):
        if page.evaluate(expression):return True
        page.wait_for_timeout(100)
    return False


def main():
    OUT.mkdir(parents=True,exist_ok=True);CAPTURES.mkdir(parents=True,exist_ok=True)
    server=http.server.ThreadingHTTPServer(('127.0.0.1',0),functools.partial(QuietHandler,directory=str(ASSETS)))
    threading.Thread(target=server.serve_forever,daemon=True).start()
    origin=f'http://127.0.0.1:{server.server_port}'
    report={'kind':'real Chromium WebGL2 render','checks':[],'errors':[],'consoleErrors':[],'externalRequests':[]}
    def check(name,ok):report['checks'].append({'name':name,'passed':bool(ok)})
    def load(page,query,model,theme):
        page.goto(f'{origin}/atelier/index.html?capture={query}#model={model}&theme={theme}')
        if not eventually(page,'Boolean(window.CrocodylAtelier && window.CrocodylAtelier.getStatus().ready)',300):
            raise RuntimeError('Actual WebGL model did not become ready')
        page.wait_for_timeout(500)
    try:
        with sync_playwright() as p:
            executable=shutil.which('google-chrome') or shutil.which('chromium')
            options={'headless':True,'args':['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']}
            if executable:options['executable_path']=executable
            browser=p.chromium.launch(**options)
            context=browser.new_context(viewport={'width':1440,'height':1000},device_scale_factor=1)
            page=context.new_page()
            page.on('pageerror',lambda e:report['errors'].append(str(e)))
            page.on('console',lambda e:report['consoleErrors'].append(e.text) if e.type=='error' else None)
            page.on('request',lambda req:report['externalRequests'].append(req.url) if not req.url.startswith(origin) and not req.url.startswith('blob:') else None)
            try:
                for model in ['recurve','arrow','target']:
                    for theme in ['dark','light']:
                        page.set_viewport_size({'width':1440,'height':1000});load(page,model+'-'+theme,model,theme)
                        status=page.evaluate('window.CrocodylAtelier.getStatus()')
                        check(f'{model}/{theme}/loaded-correct-model',status['ready'] and status['drawCalls']>0 and status['model']==model)
                        check(f'{model}/{theme}/idle-no-render-loop',not status['renderPending'])
                        check(f'{model}/{theme}/theme',status['theme']==theme)
                        page.screenshot(path=str(OUT/f'{model}-{theme}-desktop.png'),full_page=True)
                        page.locator('#view').screenshot(path=str(CAPTURES/f'{model}-{theme}.jpg'),type='jpeg',quality=90)
                        page.locator('[data-part]').first.click();page.wait_for_timeout(1300)
                        check(f'{model}/{theme}/part-selection',bool(page.evaluate('window.CrocodylAtelier.getStatus().selected')))
                        page.locator('#reset').click();page.wait_for_timeout(1200)
                        page.locator('#explode').click();page.wait_for_timeout(1200)
                        check(f'{model}/{theme}/explode',page.locator('#explode').get_attribute('aria-pressed')=='true')
                        page.locator('#explode').click();page.wait_for_timeout(1200)
                        page.set_viewport_size({'width':390,'height':844});page.wait_for_timeout(350)
                        page.screenshot(path=str(OUT/f'{model}-{theme}-mobile.png'),full_page=True)
                        check(f'{model}/{theme}/mobile-no-overflow',page.evaluate('document.documentElement.scrollWidth <= innerWidth'))
                        page.evaluate('window.CrocodylAtelier.pause()')
                        check(f'{model}/{theme}/pause',page.evaluate('window.CrocodylAtelier.getStatus().paused && !window.CrocodylAtelier.getStatus().renderPending'))
                        page.evaluate('window.CrocodylAtelier.resume()')
                page.set_viewport_size({'width':1440,'height':1000})
                for model,part in [('recurve','riser'),('recurve','grip'),('recurve','sight'),('arrow','vanes'),('arrow','nock')]:
                    load(page,model+'-'+part,model,'dark');page.locator(f'[data-part="{part}"]').click();page.wait_for_timeout(1400)
                    page.locator('#view').screenshot(path=str(CAPTURES/f'{model}-{part}-detail.jpg'),type='jpeg',quality=92)
                page.emulate_media(reduced_motion='reduce')
                # Media-query change handlers run asynchronously; test the eventual UI state.
                check('reduced-motion/turntable-disabled',eventually(page,"document.getElementById('rotate').disabled",30))
                page.emulate_media(reduced_motion='no-preference')
                check('reduced-motion/preference-restored',eventually(page,"!document.getElementById('rotate').disabled",30))
                page.locator('#rotate').click();page.wait_for_timeout(150)
                check('turntable/explicit-start',page.locator('#rotate').get_attribute('aria-pressed')=='true')
                page.locator('#rotate').click()
                check('turntable/stops',eventually(page,'!window.CrocodylAtelier.getStatus().renderPending',30))
                check('no-JavaScript-errors',not report['errors']);check('no-network-dependencies',not report['externalRequests'])
                for image in CAPTURES.glob('*.jpg'):
                    with Image.open(image) as im:check(f'{image.name}/nonblank',max(ImageStat.Stat(im.convert('RGB')).stddev)>5)
                report['browser']=browser.version
            except Exception as e:
                report['errors'].append(str(e));page.screenshot(path=str(OUT/'failure.png'),full_page=True);raise
            finally:browser.close()
    finally:
        server.shutdown();(OUT/'browser-report.json').write_text(json.dumps(report,indent=2)+'\n')
    failed=[c['name'] for c in report['checks'] if not c['passed']]
    print(json.dumps({'checks':len(report['checks']),'failures':failed,'errors':report['errors']},indent=2))
    if failed or report['errors']:raise SystemExit(1)

if __name__=='__main__':main()
