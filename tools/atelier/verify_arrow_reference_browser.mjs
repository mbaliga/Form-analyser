/** Browser smoke tests for the Lancaster-grounded arrow reference families.
 * Uses system Chromium/Chrome and CDP only; no npm dependency or external request.
 */
import fs from 'node:fs/promises';
import {existsSync} from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import os from 'node:os';
import {fileURLToPath} from 'node:url';
import {spawn} from 'node:child_process';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const assets=path.join(root,'app-android/src/main/assets');
const out=path.join(root,'docs/crocodyl/arrow-reference-previews');
await fs.mkdir(out,{recursive:true});
const chrome=process.env.CHROME_BIN||['/usr/bin/google-chrome','/usr/bin/chromium','/usr/bin/chromium-browser'].find(existsSync);
if(!chrome) throw Error('Install Chromium or set CHROME_BIN.');

const server=http.createServer(async(req,res)=>{
 try{
  const u=new URL(req.url,'http://localhost');
  const f=path.resolve(assets,'.'+decodeURIComponent(u.pathname));
  if(!f.startsWith(assets+path.sep)) throw Error('blocked');
  const b=await fs.readFile(f);
  res.writeHead(200,{'Content-Type':({'.html':'text/html','.css':'text/css','.js':'text/javascript','.glb':'model/gltf-binary','.jpg':'image/jpeg'})[path.extname(f)]||'application/octet-stream'});res.end(b);
 }catch{res.writeHead(404);res.end();}
});
await new Promise(r=>server.listen(0,'127.0.0.1',r));
const origin=`http://127.0.0.1:${server.address().port}`;
const profile=await fs.mkdtemp(path.join(os.tmpdir(),'crocodyl-arrow-ref-'));
const proc=spawn(chrome,['--headless=new','--no-sandbox','--disable-dev-shm-usage','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--no-first-run','--no-default-browser-check','--disable-background-networking','--disable-extensions','--password-store=basic','--use-mock-keychain','--remote-debugging-pipe',`--user-data-dir=${profile}`],{stdio:['ignore','ignore','pipe','pipe','pipe']});
let stderr='',wire='',seq=0,sessionId;const pending=new Map(),network=[],exceptions=[],checks=[];
proc.stderr.on('data',b=>stderr+=b.toString());
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
function send(method,params={}){return new Promise((resolve,reject)=>{const id=++seq;const timeout=setTimeout(()=>{pending.delete(id);reject(Error(`CDP timeout: ${method}`));},15000);pending.set(id,{resolve:v=>{clearTimeout(timeout);resolve(v);},reject:e=>{clearTimeout(timeout);reject(e);}});proc.stdio[3].write(JSON.stringify({id,method,params,...(sessionId&&!method.startsWith('Browser.')&&!method.startsWith('Target.')?{sessionId}:{})})+'\0');});}
async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(r.exceptionDetails.text);return r.result.value;}
async function wait(fn,label,timeout=20000){const start=Date.now();while(Date.now()-start<timeout){try{const v=await fn();if(v)return v;}catch{}await sleep(60);}throw Error(`Timed out: ${label}\n${stderr.slice(-1000)}`);}
function check(ok,label){if(!ok)throw Error(label);checks.push(label);}
async function screenshot(file){const r=await send('Page.captureScreenshot',{format:'jpeg',quality:88,captureBeyondViewport:false});await fs.writeFile(file,Buffer.from(r.data,'base64'));}

try{
 proc.stdio[4].on('data',data=>{wire+=data.toString();let end;while((end=wire.indexOf('\0'))>=0){const raw=wire.slice(0,end);wire=wire.slice(end+1);if(!raw)continue;const m=JSON.parse(raw);if(m.id){const p=pending.get(m.id);if(p){pending.delete(m.id);m.error?p.reject(Error(m.error.message)):p.resolve(m.result);}}else if(m.method==='Runtime.exceptionThrown')exceptions.push(m.params.exceptionDetails);else if(m.method==='Network.requestWillBeSent')network.push(m.params.request.url);}});
 const version=await send('Browser.getVersion');
 const target=await send('Target.createTarget',{url:'about:blank'});sessionId=(await send('Target.attachToTarget',{targetId:target.targetId,flatten:true})).sessionId;
 await send('Page.enable');await send('Runtime.enable');await send('Network.enable');
 const families=['shaft_profiles','shaft_constructions','points','nock_systems'];
 for(const key of families){
  await send('Emulation.setDeviceMetricsOverride',{width:1280,height:820,deviceScaleFactor:1,mobile:false});
  await send('Page.navigate',{url:`${origin}/atelier/index.html?arrow-reference=${key}#model=${key}&theme=dark`});
  await wait(()=>evaluate(`window.CrocodylAtelier?.getStatus().ready && window.CrocodylAtelier.getStatus().model==='${key}'`),`${key} ready`);
  await wait(()=>evaluate('!window.CrocodylAtelier.getStatus().renderPending'),`${key} settled`);
  check(await evaluate('window.CrocodylAtelier.getStatus().drawCalls>0'),`${key}: GLB uploaded and drawn`);
  check(await evaluate("document.querySelector('canvas').getContext('webgl2').getError()===0"),`${key}: no WebGL error`);
  const firstPart=await evaluate("document.querySelector('[data-part]')?.dataset.part || ''");check(Boolean(firstPart),`${key}: component controls created`);
  await evaluate("document.querySelector('[data-part]').click()");await wait(()=>evaluate('Boolean(window.CrocodylAtelier.getStatus().selected)'),`${key} selection`);
  check(await evaluate('Boolean(window.CrocodylAtelier.getStatus().selected)'),`${key}: component focus`);
  await screenshot(path.join(out,`${key}-desktop.jpg`));
  await send('Emulation.setDeviceMetricsOverride',{width:412,height:915,deviceScaleFactor:1,mobile:true});await sleep(150);
  check(await evaluate('document.documentElement.scrollWidth<=innerWidth+1'),`${key}: no mobile horizontal overflow`);
  await screenshot(path.join(out,`${key}-mobile.jpg`));
 }
 check(exceptions.length===0,'no JavaScript exceptions');
 check(network.every(url=>url.startsWith(origin)||url.startsWith('data:')||url.startsWith('blob:')),'all requests remain local');
 const report={verifiedAt:new Date().toISOString(),browser:version.product,families,checks,exceptions:exceptions.length,externalRequests:network.filter(url=>!url.startsWith(origin)&&!url.startsWith('data:')&&!url.startsWith('blob:')),notVerified:['physical Android device','manufacturer replica accuracy beyond published dimensions','equipment compatibility or fitting advice']};
 await fs.writeFile(path.join(out,'verification.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));
}finally{
 proc.kill('SIGTERM');server.close();await sleep(150);await fs.rm(profile,{recursive:true,force:true}).catch(()=>{});
}
