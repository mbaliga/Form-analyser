/** Actual Chromium/WebGL review and poster bake. Node >=22, system Chromium; no npm/CDN. */
import fs from 'node:fs/promises';
import {existsSync} from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import os from 'node:os';
import {fileURLToPath} from 'node:url';
import {spawn} from 'node:child_process';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const assets=path.join(root,'app-android/src/main/assets');
const preview=path.join(root,'docs/crocodyl/atelier-previews');
const posters=path.join(assets,'atelier/posters');
await fs.mkdir(preview,{recursive:true});await fs.mkdir(posters,{recursive:true});
const chrome=process.env.CHROME_BIN||['/usr/bin/google-chrome','/usr/bin/chromium','/usr/bin/chromium-browser'].find(existsSync);
if(!chrome)throw Error('Install Chromium or set CHROME_BIN. No browser checks have run.');
const server=http.createServer(async(req,res)=>{
 try{const u=new URL(req.url,'http://localhost');const f=path.resolve(assets,'.'+decodeURIComponent(u.pathname));if(!f.startsWith(assets+path.sep))throw Error('Blocked');const b=await fs.readFile(f);res.writeHead(200,{'Content-Type':({'.html':'text/html','.css':'text/css','.js':'text/javascript','.glb':'model/gltf-binary','.jpg':'image/jpeg'})[path.extname(f)]||'application/octet-stream'});res.end(b);}catch{res.writeHead(404);res.end();}
});
await new Promise(r=>server.listen(0,'127.0.0.1',r));
const origin=`http://127.0.0.1:${server.address().port}`;
const profile=await fs.mkdtemp(path.join(os.tmpdir(),'crocodyl-browser-'));
const proc=spawn(chrome,['--headless=new','--no-sandbox','--disable-dev-shm-usage','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--no-first-run','--no-default-browser-check','--disable-background-networking','--disable-extensions','--password-store=basic','--use-mock-keychain','--remote-debugging-pipe',`--user-data-dir=${profile}`],{stdio:['ignore','ignore','pipe','pipe','pipe']});
let stderr='';proc.stderr.on('data',b=>{stderr+=b.toString();});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
let sessionId,wire='',seq=0;const pending=new Map(),errors=[],network=[];const checks=[];
async function wait(fn,label,timeout=20000){const start=Date.now();while(Date.now()-start<timeout){try{const result=await fn();if(result)return result;}catch{}await sleep(60);}throw Error(`Timed out: ${label}\n${stderr.slice(-1200)}`);}
function send(method,params={}){return new Promise((resolve,reject)=>{const id=++seq;const timeout=setTimeout(()=>{pending.delete(id);reject(Error(`CDP timeout: ${method}`));},15000);pending.set(id,{resolve:v=>{clearTimeout(timeout);resolve(v);},reject:e=>{clearTimeout(timeout);reject(e);}});proc.stdio[3].write(JSON.stringify({id,method,params,...(sessionId&&!method.startsWith('Browser.')&&!method.startsWith('Target.')?{sessionId}:{})})+'\0');});}
async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.text);return r.result.value;}
const ready=()=>wait(()=>evaluate('window.CrocodylAtelier?.getStatus().ready'),'model readiness');
async function settled(){await wait(()=>evaluate('window.CrocodylAtelier?.getStatus().ready && !window.CrocodylAtelier.getStatus().renderPending'),'idle renderer');await sleep(100);}
function check(ok,label){if(!ok)throw Error(label);checks.push(label);}
async function screen(file,width=1440,height=1000){await send('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:false});await sleep(130);const r=await send('Page.captureScreenshot',{format:'jpeg',quality:88,captureBeyondViewport:false});await fs.writeFile(file,Buffer.from(r.data,'base64'));}
try{
 proc.stdio[4].on('data',data=>{wire+=data.toString();let end;while((end=wire.indexOf('\0'))>=0){const raw=wire.slice(0,end);wire=wire.slice(end+1);if(!raw)continue;const m=JSON.parse(raw);if(m.id){const p=pending.get(m.id);if(p){pending.delete(m.id);m.error?p.reject(Error(m.error.message)):p.resolve(m.result);}}else if(m.method==='Runtime.exceptionThrown')errors.push(m.params.exceptionDetails);else if(m.method==='Network.requestWillBeSent')network.push(m.params.request.url);}});
 const version=await send('Browser.getVersion');console.log(version.product);
 const target=await send('Target.createTarget',{url:'about:blank'});
 sessionId=(await send('Target.attachToTarget',{targetId:target.targetId,flatten:true})).sessionId;
 await send('Page.enable');await send('Runtime.enable');await send('Network.enable');
 for(const key of ['recurve','arrow','target']){
  await send('Page.navigate',{url:`${origin}/atelier/index.html?review=${key}#model=${key}&theme=dark`});await ready();await settled();
  check(await evaluate(`window.CrocodylAtelier.getStatus().model==='${key}' && window.CrocodylAtelier.getStatus().drawCalls>0`),`${key}: GLB uploaded and drawn`);
  check(await evaluate("document.querySelector('canvas').getContext('webgl2').getError()===0"),`${key}: no WebGL error`);
  await screen(path.join(preview,`${key}-desktop-dark.jpg`));
  await evaluate("document.querySelector('#theme').click()");await settled();await screen(path.join(preview,`${key}-desktop-light.jpg`));
  await screen(path.join(preview,`${key}-mobile-light.jpg`),412,915);
  check(await evaluate('document.documentElement.scrollWidth<=innerWidth+1'),`${key}: no mobile horizontal overflow`);
  const part={recurve:'grip',arrow:'vanes',target:'face'}[key];
  await evaluate(`document.querySelector('[data-part=${part}]').click()`);await settled();
  check(await evaluate(`window.CrocodylAtelier.getStatus().selected==='${part}'`),`${key}: component focus`);
  await screen(path.join(preview,`${key}-detail.jpg`),1440,1000);
  await evaluate("document.querySelector('#explode').click()");await settled();
  check(await evaluate("document.querySelector('#explode').getAttribute('aria-pressed')==='true'"),`${key}: separate parts`);
  await evaluate("document.querySelector('#explode').click()");await settled();
 }
 await evaluate("document.querySelector('#rotate').click()");await sleep(120);await evaluate('window.CrocodylAtelier.pause()');
 check(await evaluate('window.CrocodylAtelier.getStatus().paused && !window.CrocodylAtelier.getStatus().renderPending'),'pause cancels animation');
 await evaluate('window.CrocodylAtelier.resume()');await evaluate("document.querySelector('#rotate').click()");await settled();
 await send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});
 // CDP acknowledgement precedes delivery of the page's MediaQueryList change event.
 await wait(()=>evaluate("matchMedia('(prefers-reduced-motion: reduce)').matches && document.querySelector('#rotate').disabled"),'reduced-motion listener');
 check(await evaluate("document.querySelector('#rotate').disabled"),'reduced motion disables turntable');
 await send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'no-preference'}]});
 await wait(()=>evaluate("!matchMedia('(prefers-reduced-motion: reduce)').matches && !document.querySelector('#rotate').disabled"),'normal-motion listener');
 // Posters use the production shader/meshes. Keep the production CSP in force.
 for(const key of ['recurve','arrow','target'])for(const theme of ['dark','light']){
  const camera={recurve:'center=-0.20,0,0&radius=0.45&yaw=0.35&pitch=0.06',arrow:'center=-0.23,0,0&radius=0.22&yaw=0.10&pitch=0.24',target:'center=-0.34,0.50,0&radius=0.92&yaw=0.38&pitch=0.12'}[key];
  await send('Emulation.setDeviceMetricsOverride',{width:1200,height:700,deviceScaleFactor:1,mobile:false});
  await send('Page.navigate',{url:`${origin}/atelier/index.html?poster=${key}-${theme}#model=${key}&theme=${theme}&${camera}`});await ready();
  await evaluate("new Promise((resolve,reject)=>{const link=document.createElement('link');link.rel='stylesheet';link.href='poster.css';link.onload=()=>resolve(true);link.onerror=()=>reject(Error('Poster stylesheet failed'));document.head.append(link);})");
  await wait(()=>evaluate("getComputedStyle(document.querySelector('header')).display==='none' && document.querySelector('canvas').clientWidth===innerWidth"),'text-free full-frame poster layout');
  check(await evaluate("getComputedStyle(document.querySelector('.intro')).display==='none' && getComputedStyle(document.querySelector('.stage-actions')).display==='none'"),`${key}-${theme}: poster contains no UI chrome`);
  await settled();await screen(path.join(posters,`${key}-${theme}.jpg`),1200,700);
 }
 check(errors.length===0,'no JavaScript exceptions');
 check(network.every(url=>url.startsWith(origin)||url.startsWith('data:')||url.startsWith('blob:')),'all model/page requests remain local');
 const report={verifiedAt:new Date().toISOString(),browser:version.product,renderer:await evaluate("(()=>{const gl=document.querySelector('canvas').getContext('webgl2');return gl.getParameter(gl.RENDERER)})()"),checks,notVerified:['physical Android device','battery/thermal/frame pacing','biomechanics or equipment fitting'],posterOrigin:'Rendered from committed GLBs with the production WebGL2 shader; text-free layout checked with CSP enabled'};
 await fs.writeFile(path.join(preview,'verification.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));
}finally{proc.kill('SIGTERM');server.close();await sleep(200);await fs.rm(profile,{recursive:true,force:true}).catch(()=>{});}
