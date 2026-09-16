#!/usr/bin/env python3
"""Render the real bundled WebGL viewer and record browser checks, not design mockups.
Requires playwright and Pillow; uses system Chrome if present, otherwise Playwright Chromium.
Run from the repository root: python tools/atelier/render_qa.py
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

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'app-android/src/main/assets'
OUT = ROOT / 'docs/crocodyl/atelier/qa'
POSTERS = ASSETS / 'atelier/posters'

class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *_): pass


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    POSTERS.mkdir(parents=True, exist_ok=True)
    handler = functools.partial(QuietHandler, directory=str(ASSETS))
    server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    origin = f'http://127.0.0.1:{server.server_port}'
    report = {'kind': 'real Chromium WebGL2 render', 'checks': [], 'errors': [], 'externalRequests': []}
    errors = report['errors']
    def check(name, ok):
        report['checks'].append({'name': name, 'passed': bool(ok)})
    try:
        with sync_playwright() as p:
            executable = shutil.which('google-chrome') or shutil.which('chromium')
            options = {'headless': True, 'args': ['--no-sandbox', '--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader']}
            if executable: options['executable_path'] = executable
            browser = p.chromium.launch(**options)
            context = browser.new_context(viewport={'width': 1440, 'height': 1000}, device_scale_factor=1)
            page = context.new_page()
            page.on('pageerror', lambda e: errors.append(str(e)))
            page.on('request', lambda req: report['externalRequests'].append(req.url) if not req.url.startswith(origin) and not req.url.startswith('blob:') else None)
            for model in ['recurve', 'arrow', 'target']:
                for theme in ['dark', 'light']:
                    page.set_viewport_size({'width': 1440, 'height': 1000})
                    page.goto(f'{origin}/atelier/index.html#model={model}&theme={theme}')
                    page.wait_for_function('window.CrocodylAtelier && window.CrocodylAtelier.getStatus().ready', timeout=30000)
                    page.wait_for_timeout(500)
                    status = page.evaluate('window.CrocodylAtelier.getStatus()')
                    check(f'{model}/{theme}/loaded', status['ready'] and status['drawCalls'] > 0)
                    check(f'{model}/{theme}/idle-no-render-loop', not status['renderPending'])
                    check(f'{model}/{theme}/theme', status['theme'] == theme)
                    page.screenshot(path=str(OUT / f'{model}-{theme}-desktop.png'), full_page=True)
                    # A real canvas capture: offline Home feed posters never start a render loop.
                    image = page.locator('#view').screenshot(type='jpeg', quality=90)
                    (POSTERS / f'{model}-{theme}.jpg').write_bytes(image)
                    first = page.locator('[data-part]').first
                    first.click()
                    page.wait_for_timeout(1000)
                    check(f'{model}/{theme}/part-selection', bool(page.evaluate('window.CrocodylAtelier.getStatus().selected')))
                    page.locator('#reset').click()
                    page.wait_for_timeout(1000)
                    page.locator('#explode').click()
                    page.wait_for_timeout(1000)
                    check(f'{model}/{theme}/explode', page.locator('#explode').get_attribute('aria-pressed') == 'true')
                    page.locator('#explode').click()
                    page.wait_for_timeout(1000)
                    page.set_viewport_size({'width': 390, 'height': 844})
                    page.wait_for_timeout(300)
                    page.screenshot(path=str(OUT / f'{model}-{theme}-mobile.png'), full_page=True)
                    check(f'{model}/{theme}/mobile-no-overflow', page.evaluate('document.documentElement.scrollWidth <= innerWidth'))
                    page.evaluate('window.CrocodylAtelier.pause()')
                    check(f'{model}/{theme}/pause', page.evaluate('window.CrocodylAtelier.getStatus().paused && !window.CrocodylAtelier.getStatus().renderPending'))
                    page.evaluate('window.CrocodylAtelier.resume()')
            # Real detail crops from the same meshes, not generated replacement imagery.
            page.set_viewport_size({'width': 1440, 'height': 1000})
            for model, part in [('recurve', 'riser'), ('recurve', 'grip'), ('recurve', 'sight'), ('arrow', 'vanes'), ('arrow', 'nock')]:
                page.goto(f'{origin}/atelier/index.html#model={model}&theme=dark')
                page.wait_for_function('window.CrocodylAtelier.getStatus().ready')
                page.locator(f'[data-part="{part}"]').click()
                page.wait_for_timeout(1200)
                page.locator('#view').screenshot(path=str(POSTERS / f'{model}-{part}-detail.jpg'), type='jpeg', quality=92)
            page.emulate_media(reduced_motion='reduce')
            check('reduced-motion/turntable-disabled', page.locator('#rotate').is_disabled())
            page.emulate_media(reduced_motion='no-preference')
            page.locator('#rotate').click()
            page.wait_for_timeout(100)
            check('turntable/explicit-start', page.locator('#rotate').get_attribute('aria-pressed') == 'true')
            page.locator('#rotate').click()
            page.wait_for_timeout(100)
            check('turntable/stops', not page.evaluate('window.CrocodylAtelier.getStatus().renderPending'))
            check('no-JavaScript-errors', not errors)
            check('no-network-dependencies', not report['externalRequests'])
            for image in POSTERS.glob('*.jpg'):
                with Image.open(image) as im:
                    check(f'{image.name}/nonblank', max(ImageStat.Stat(im.convert('RGB')).stddev) > 5)
            report['browser'] = browser.version
            browser.close()
    finally:
        server.shutdown()
        (OUT / 'browser-report.json').write_text(json.dumps(report, indent=2) + '\n')
    failed = [c['name'] for c in report['checks'] if not c['passed']]
    print(json.dumps({'checks': len(report['checks']), 'failures': failed, 'errors': errors}, indent=2))
    if failed or errors: raise SystemExit(1)

if __name__ == '__main__': main()
