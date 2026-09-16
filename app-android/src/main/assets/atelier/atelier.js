/* Crocodyl equipment atelier. Original minimal WebGL2/glTF renderer, no CDN or telemetry.
 * Supports this package's glTF subset: static triangle meshes, float vertex attributes,
 * uint32 indices, embedded PNG base-colour textures, PBR metallic/roughness factors.
 * This is NOT a general-purpose glTF importer or a biomechanics engine.
 */
'use strict';
(() => {
const $=id=>document.getElementById(id), canvas=$('view');
const reduced=window.matchMedia('(prefers-reduced-motion: reduce)');
const initial=new URLSearchParams(location.hash.slice(1));
const defs={
 recurve:{src:'models/recurve.glb',title:'A study in balance.',type:'OLYMPIC RECURVE / BRACED STUDY',material:'Bronze · carbon · walnut',center:[.20,0,0],radius:1.02,yaw:.46,pitch:.12,description:'A complete, original recurve assembly. Select a component to move closer, or separate the parts to inspect its construction.'},
 arrow:{src:'models/arrow.glb',title:'Small details. Straight flight.',type:'TARGET ARROW / COMPONENT STUDY',material:'Carbon · steel · polymer',center:[0,0,0],radius:.43,yaw:.12,pitch:.17,description:'A target arrow with a carbon shaft, three individually modelled vanes, a slotted nock and a rounded target point. Select a part for a close view.'},
 target:{src:'models/target.glb',title:'The place it all comes together.',type:'RECURVE TARGET / TEN-RING STUDY',material:'Paper · compressed fibre · timber',center:[0,.34,0],radius:1.13,yaw:.36,pitch:.10,description:'A complete ten-ring target and three-support stand. Geometry here is illustrative; scoring remains in Crocodyl’s independently tested scoring engine.'},
 shaft_profiles:{src:'arrow-reference-library/shaft_profiles.glb',title:'The shaft is not always a cylinder.',type:'TARGET ARROW / SHAFT PROFILE TAXONOMY',material:'Parallel · barrelled · front taper · rear taper',center:[0,0,0],radius:.48,yaw:.10,pitch:.14,description:'Compare four source-backed shaft profile families. The parallel exemplar uses published dimensions; taper stations on the generic families remain illustrative where Lancaster does not publish them.'},
 shaft_constructions:{src:'arrow-reference-library/shaft_constructions.glb',title:'What the shaft is made of matters.',type:'TARGET ARROW / SHAFT CONSTRUCTION',material:'Aluminium · carbon · carbon/aluminium laminates',center:[0,0,0],radius:.24,yaw:.18,pitch:.18,description:'Schematic construction studies distinguish aluminium, all-carbon, carbon-over-aluminium and aluminium-over-carbon shafts. Cutaway layer thicknesses are explanatory, not manufacturing specifications.'},
 points:{src:'arrow-reference-library/points.glb',title:'Shape, material and attachment are different choices.',type:'TARGET ARROW / POINT TAXONOMY',material:'Stainless · tungsten · aluminium · steel',center:[0,0,0],radius:.22,yaw:.24,pitch:.19,description:'Compare break-off, glue-in, NIBB, bullet, field and chisel point families. Material, nose shape, attachment and weight-adjustment construction are intentionally kept separate.'},
 nock_systems:{src:'arrow-reference-library/nock_systems.glb',title:'The rear interface is a system.',type:'TARGET ARROW / NOCK & REAR INTERFACES',material:'Direct-fit · pin · pin-out · bushing · swage',center:[0,.04,0],radius:.30,yaw:.28,pitch:.18,description:'Compare direct insert, pin, pin-out, bushing-backed and conventional swaged rear interfaces, plus a separate rear impact collar. Representative groove dimensions are labelled as examples, not universal fit values.'}
};
const state={model:'recurve',parts:[],draws:[],selected:null,explode:0,exploded:false,spin:false,paused:document.hidden,theme:'dark',yaw:.46,pitch:.12,radius:1.02,center:[.2,0,0],target:null,frame:0,last:0,loadToken:0,textures:[],ready:false,restored:false};
let gl,program,uniforms,resizeObserver,disposed=false;
const vsub=(a,b)=>a.map((x,i)=>x-b[i]),vadd=(a,b)=>a.map((x,i)=>x+b[i]),vmul=(a,s)=>a.map(x=>x*s),dot=(a,b)=>a.reduce((s,x,i)=>s+x*b[i],0),cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]],normal=a=>vmul(a,1/(Math.hypot(...a)||1));
function lookAt(eye,center){const z=normal(vsub(eye,center)),x=normal(cross([0,1,0],z)),y=cross(z,x);return new Float32Array([x[0],y[0],z[0],0,x[1],y[1],z[1],0,x[2],y[2],z[2],0,-dot(x,eye),-dot(y,eye),-dot(z,eye),1]);}
function multiply(a,b){const r=new Float32Array(16);for(let j=0;j<4;j++)for(let i=0;i<4;i++)for(let k=0;k<4;k++)r[j*4+i]+=a[k*4+i]*b[j*4+k];return r;}
function projection(radius,aspect){const r=radius,t=radius/aspect,n=.01,f=30;return new Float32Array([1/r,0,0,0,0,1/t,0,0,0,0,-2/(f-n),0,0,0,-(f+n)/(f-n),1]);}
const VS=`#version 300 es
precision highp float;
layout(location=0) in vec3 position;layout(location=1) in vec3 normal;layout(location=2) in vec2 uv;
uniform mat4 vp;uniform vec3 offset;out vec3 P;out vec3 N;out vec2 UV;
void main(){P=position+offset;N=normal;UV=uv;gl_Position=vp*vec4(P,1.);}`;
const FS=`#version 300 es
precision highp float;
in vec3 P;in vec3 N;in vec2 UV;out vec4 frag;
uniform vec3 eye;uniform vec3 base;uniform float metal;uniform float rough;uniform float selected;uniform float lightTheme;uniform float textured;uniform sampler2D colorMap;
const float PI=3.14159265;
vec3 fresnel(float c,vec3 f){return f+(1.-f)*pow(1.-c,5.);}
float ggx(float nh,float r){float a=r*r,a2=a*a,d=nh*nh*(a2-1.)+1.;return a2/max(PI*d*d,.00001);}
float geom(float nv,float r){float k=(r+1.)*(r+1.)/8.;return nv/(nv*(1.-k)+k);}
vec3 direct(vec3 n,vec3 v,vec3 l,vec3 energy,vec3 albedo,vec3 f0,float r){vec3 h=normalize(v+l);float nv=max(dot(n,v),.001),nl=max(dot(n,l),0.),nh=max(dot(n,h),0.);vec3 F=fresnel(max(dot(h,v),0.),f0);vec3 spec=ggx(nh,r)*geom(nv,r)*geom(nl,r)*F/max(4.*nv*nl,.001);return ((1.-F)*(1.-metal)*albedo/PI+spec)*energy*nl;}
vec3 environment(vec3 r,float roughness){vec3 col=mix(vec3(.025,.035,.031),vec3(.18,.21,.19),smoothstep(-.6,.8,r.y));float e=mix(190.,6.,roughness);col+=vec3(3.3,2.7,1.95)*pow(max(dot(r,normalize(vec3(-.8,1.,1.5))),0.),e);col+=vec3(1.9,2.4,2.5)*pow(max(dot(r,normalize(vec3(1.2,.5,-1.))),0.),e*.4);col+=vec3(1.5,1.6,1.2)*pow(max(dot(r,normalize(vec3(.2,1.4,.1))),0.),e*.35);return col;}
vec3 aces(vec3 c){return clamp((c*(2.51*c+.03))/(c*(2.43*c+.59)+.14),0.,1.);}
void main(){vec3 n=normalize(N);if(!gl_FrontFacing)n=-n;vec3 v=normalize(eye-P),albedo=base;if(textured>.5)albedo*=pow(texture(colorMap,UV).rgb,vec3(2.2));float r=max(.16,rough),nv=max(dot(n,v),0.);vec3 f0=mix(vec3(.04),albedo,metal);vec3 c=albedo*(1.-metal)*(.28+.20*lightTheme)*(.7+.3*n.y);c+=direct(n,v,normalize(vec3(-.8,1.3,1.8)),vec3(3.2,2.85,2.3),albedo,f0,r);c+=direct(n,v,normalize(vec3(1.2,.5,-1.)),vec3(1.1,1.7,1.8),albedo,f0,r);c+=environment(reflect(-v,n),r)*fresnel(nv,f0)*(.65+.25*metal);c+=albedo*selected*.10;frag=vec4(pow(aces(c),vec3(1./2.2)),1.);}`;
function compile(type,src){const s=gl.createShader(type);gl.shaderSource(s,src);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS)){const err=gl.getShaderInfoLog(s);gl.deleteShader(s);throw Error(err);}return s;}
function init(){
 gl=canvas.getContext('webgl2',{alpha:true,antialias:true,powerPreference:'low-power',preserveDrawingBuffer:false});if(!gl)throw Error('WebGL2 unavailable');
 const vs=compile(gl.VERTEX_SHADER,VS),fs=compile(gl.FRAGMENT_SHADER,FS);program=gl.createProgram();gl.attachShader(program,vs);gl.attachShader(program,fs);gl.linkProgram(program);gl.deleteShader(vs);gl.deleteShader(fs);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error('Renderer link failed');
 uniforms=Object.fromEntries(['vp','offset','eye','base','metal','rough','selected','lightTheme','textured','colorMap'].map(k=>[k,gl.getUniformLocation(program,k)]));
 gl.enable(gl.DEPTH_TEST);gl.disable(gl.CULL_FACE);gl.clearColor(0,0,0,0);resizeObserver=new ResizeObserver(()=>request());resizeObserver.observe(canvas);
}
function parseGLB(buffer){
 const v=new DataView(buffer);if(v.byteLength<28||v.getUint32(0,true)!==0x46546c67||v.getUint32(4,true)!==2||v.getUint32(8,true)!==v.byteLength)throw Error('Invalid GLB header');
 const jl=v.getUint32(12,true);if(v.getUint32(16,true)!==0x4e4f534a||20+jl+8>v.byteLength)throw Error('Invalid GLB JSON');
 const json=JSON.parse(new TextDecoder().decode(new Uint8Array(buffer,20,jl)));const bl=v.getUint32(20+jl,true);if(v.getUint32(24+jl,true)!==0x004e4942||28+jl+bl!==v.byteLength)throw Error('Invalid GLB binary');
 const binary=new Uint8Array(buffer,28+jl,bl);return {json,binary};
}
function disposeModel(){for(const d of state.draws){gl.deleteVertexArray(d.vao);d.buffers.forEach(b=>gl.deleteBuffer(b));}for(const t of state.textures)gl.deleteTexture(t);state.draws=[];state.textures=[];}
async function load(key){
 if(!Object.keys(defs).includes(key)||disposed)return;const token=++state.loadToken;state.ready=false;state.parts=[];$('parts').replaceChildren();$('marker').style.display='none';state.spin=false;updateSpin();$('loading').hidden=false;$('fallback').hidden=true;state.model=key;state.selected=null;state.exploded=false;state.explode=0;$('explode').setAttribute('aria-pressed','false');$('explode').textContent='Separate parts';
 document.querySelectorAll('[data-model]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.model===key)));const d=defs[key];$('model-type').textContent=d.type;$('model-subtitle').textContent=d.material;canvas.setAttribute('aria-label',`Three-dimensional ${key.replaceAll('_',' ')} model. Drag or use arrow keys to rotate; plus and minus to zoom. Select part buttons for details.`);setDetail(null);reset(false);
 try{
   const response=await fetch(d.src);if(!response.ok)throw Error('Model fetch failed');const buffer=await response.arrayBuffer();if(token!==state.loadToken||disposed)return;
   const {json:g,binary}=parseGLB(buffer);disposeModel();state.parts=g.nodes.map(n=>n.extras).filter(Boolean);makeParts();
   for(const image of g.images||[]){
     const b=g.bufferViews[image.bufferView];const blob=new Blob([binary.subarray(b.byteOffset,b.byteOffset+b.byteLength)],{type:image.mimeType});
     const bitmap=await createImageBitmap(blob);if(token!==state.loadToken||disposed){bitmap.close();return;}
     const t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGB,gl.RGB,gl.UNSIGNED_BYTE,bitmap);gl.generateMipmap(gl.TEXTURE_2D);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR_MIPMAP_LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);bitmap.close();state.textures.push(t);
   }
   for(const node of g.nodes){if(node.mesh===undefined)continue;for(const p of g.meshes[node.mesh].primitives){
     const vao=gl.createVertexArray();gl.bindVertexArray(vao);const buffers=[];
     for(const [name,loc,components] of [['POSITION',0,3],['NORMAL',1,3],['TEXCOORD_0',2,2]]){const a=g.accessors[p.attributes[name]],b=g.bufferViews[a.bufferView],buf=gl.createBuffer();buffers.push(buf);gl.bindBuffer(gl.ARRAY_BUFFER,buf);gl.bufferData(gl.ARRAY_BUFFER,binary.subarray(b.byteOffset,b.byteOffset+b.byteLength),gl.STATIC_DRAW);gl.enableVertexAttribArray(loc);gl.vertexAttribPointer(loc,components,gl.FLOAT,false,b.byteStride||0,a.byteOffset||0);}
     const a=g.accessors[p.indices],b=g.bufferViews[a.bufferView],ib=gl.createBuffer();buffers.push(ib);gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,ib);gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,binary.subarray(b.byteOffset,b.byteOffset+b.byteLength),gl.STATIC_DRAW);
     const mat=g.materials[p.material].pbrMetallicRoughness,ti=mat.baseColorTexture?.index,tex=ti===undefined?null:state.textures[g.textures[ti].source];state.draws.push({vao,buffers,count:a.count,indexType:a.componentType,indexOffset:a.byteOffset||0,part:node.extras,mat,tex});
   }}
   gl.bindVertexArray(null);state.ready=true;$('loading').hidden=true;
   if(!state.restored){state.restored=true;for(const [k,lo,hi] of [['yaw',-100,100],['pitch',-1.2,1.2],['radius',.035,3]]){const v=Number(initial.get(k));if(initial.has(k)&&Number.isFinite(v))state[k]=Math.max(lo,Math.min(hi,v));}const c=initial.get('center')?.split(',').map(Number);if(c?.length===3&&c.every(v=>Number.isFinite(v)&&Math.abs(v)<5))state.center=c;}
   request();save();
 }catch(e){if(token!==state.loadToken||disposed)return;console.error('Crocodyl atelier',e);$('loading').hidden=true;$('fallback').hidden=false;state.ready=false;request();}
}
function makeParts(){const list=$('parts');list.replaceChildren();for(const p of state.parts){const b=document.createElement('button');b.textContent=p.title;b.dataset.part=p.id;b.setAttribute('aria-pressed','false');b.addEventListener('click',()=>select(p.id));list.appendChild(b);}}
function setDetail(p){$('part-kicker').textContent=p?'COMPONENT STUDY':'THE COMPLETE ASSEMBLY';$('part-title').textContent=p?.title||defs[state.model].title;$('part-body').textContent=p?.description||defs[state.model].description;}
function select(id){const p=state.parts.find(p=>p.id===id);if(!p)return;state.selected=state.selected===id?null:id;state.spin=false;updateSpin();document.querySelectorAll('[data-part]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.part===state.selected)));if(!state.selected){reset();return;}setDetail(p);const sizes={recurve:{riser:.33,grip:.12,limbs:.78,string:.84,sight:.18,stabilisers:.59,rest:.10,hardware:.18},arrow:{shaft:.40,point:.08,nock:.055,vanes:.095,wrap:.12},target:{face:.70,butt:.78,stand:.65,pins:.17}};let center=[...p.hotspot];if(state.exploded)center=vadd(center,p.explode);state.target={center,radius:sizes[state.model]?.[id]||Math.max(.075,defs[state.model].radius*.48),yaw:state.yaw,pitch:state.pitch};request();save();}
function reset(animate=true){const d=defs[state.model];state.selected=null;setDetail(null);document.querySelectorAll('[data-part]').forEach(b=>b.setAttribute('aria-pressed','false'));const t={center:[...d.center],radius:d.radius,yaw:d.yaw,pitch:d.pitch};if(animate){state.target=t;}else{Object.assign(state,t);state.target=null;}request();save();}
function save(){if(disposed)return;const p=new URLSearchParams({model:state.model,theme:state.theme,yaw:state.yaw.toFixed(4),pitch:state.pitch.toFixed(4),radius:state.radius.toFixed(4),center:state.center.map(v=>v.toFixed(4)).join(',')});try{history.replaceState(null,'','#'+p);}catch{} }
function setTheme(theme){state.theme=theme==='light'?'light':'dark';document.documentElement.dataset.theme=state.theme;$('theme').setAttribute('aria-label',`Switch to ${state.theme==='dark'?'light':'dark'} theme`);request();save();}
function updateSpin(){$('rotate').setAttribute('aria-pressed',String(state.spin));$('rotate').textContent=state.spin?'Stop turntable':'Turntable';}
function request(){if(!disposed&&!state.frame&&!state.paused&&!document.hidden)state.frame=requestAnimationFrame(render);}
function render(time){
 state.frame=0;if(disposed||state.paused||document.hidden||!gl)return;const dt=Math.min(.05,(time-state.last)/1000||.016);state.last=time;
 let moving=false;
 if(state.target){const k=reduced.matches?1:1-Math.exp(-dt*8),t=state.target;state.center=state.center.map((a,i)=>a+(t.center[i]-a)*k);state.radius+=(t.radius-state.radius)*k;state.yaw+=(t.yaw-state.yaw)*k;state.pitch+=(t.pitch-state.pitch)*k;if(Math.abs(state.radius-t.radius)+Math.hypot(...vsub(state.center,t.center))<.0002){Object.assign(state,t);state.target=null;}else moving=true;}
 const goal=state.exploded?1:0;if(Math.abs(state.explode-goal)>.002){state.explode+=(goal-state.explode)*(reduced.matches?1:1-Math.exp(-dt*7));moving=true;}else state.explode=goal;
 if(state.spin&&!reduced.matches){state.yaw+=dt*.12;moving=true;}
 const w=canvas.clientWidth,h=canvas.clientHeight;if(!w||!h)return;const dpr=Math.min(window.devicePixelRatio||1,1.75);if(canvas.width!==Math.round(w*dpr)||canvas.height!==Math.round(h*dpr)){canvas.width=Math.round(w*dpr);canvas.height=Math.round(h*dpr);}
 gl.viewport(0,0,canvas.width,canvas.height);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);if(!state.ready)return;
 const aspect=w/h;let radius=state.radius*aspect;if(state.model==='arrow'||state.model==='shaft_profiles')radius=state.radius*1.03;
 const eye=vadd(state.center,[Math.sin(state.yaw)*Math.cos(state.pitch)*5,Math.sin(state.pitch)*5,Math.cos(state.yaw)*Math.cos(state.pitch)*5]);const vp=multiply(projection(radius,aspect),lookAt(eye,state.center));
 gl.useProgram(program);gl.uniformMatrix4fv(uniforms.vp,false,vp);gl.uniform3fv(uniforms.eye,eye);gl.uniform1f(uniforms.lightTheme,state.theme==='light'?1:0);gl.uniform1i(uniforms.colorMap,0);
 for(const d of state.draws){gl.bindVertexArray(d.vao);gl.uniform3fv(uniforms.offset,vmul(d.part.explode,state.explode));gl.uniform3fv(uniforms.base,d.mat.baseColorFactor.slice(0,3));gl.uniform1f(uniforms.metal,d.mat.metallicFactor);gl.uniform1f(uniforms.rough,d.mat.roughnessFactor);gl.uniform1f(uniforms.selected,state.selected===d.part.id?1:0);gl.uniform1f(uniforms.textured,d.tex?1:0);gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,d.tex||null);gl.drawElements(gl.TRIANGLES,d.count,d.indexType,d.indexOffset);}
 gl.bindVertexArray(null);
 const marker=$('marker'),p=state.parts.find(p=>p.id===state.selected);if(p){const pos=vadd(p.hotspot,vmul(p.explode,state.explode)),c=[...pos,1];const clip=[0,0,0,0].map((_,i)=>c.reduce((sum,a,j)=>sum+vp[j*4+i]*a,0));const x=(clip[0]/clip[3]*.5+.5)*w,y=(.5-clip[1]/clip[3]*.5)*h;marker.style.display=x>15&&x<w-80&&y>15&&y<h-75?'block':'none';marker.style.left=x+'px';marker.style.top=y+'px';marker.querySelector('small').textContent=p.title;}else marker.style.display='none';
 if(moving)request();else save();
}
const pointers=new Map();let pinch=0;
canvas.addEventListener('pointerdown',e=>{if(!state.ready)return;canvas.setPointerCapture(e.pointerId);pointers.set(e.pointerId,[e.clientX,e.clientY]);state.spin=false;state.target=null;updateSpin();if(pointers.size===2){const p=[...pointers.values()];pinch=Math.hypot(p[0][0]-p[1][0],p[0][1]-p[1][1]);}});
canvas.addEventListener('pointermove',e=>{if(!pointers.has(e.pointerId))return;const old=pointers.get(e.pointerId);pointers.set(e.pointerId,[e.clientX,e.clientY]);if(pointers.size===1){state.yaw-=(e.clientX-old[0])*.007;state.pitch=Math.max(-1.2,Math.min(1.2,state.pitch+(e.clientY-old[1])*.005));}else{const p=[...pointers.values()],d=Math.hypot(p[0][0]-p[1][0],p[0][1]-p[1][1]);if(d>0&&pinch>0)state.radius=Math.max(.035,Math.min(3,state.radius*pinch/d));pinch=d;}request();});
for(const evt of ['pointerup','pointercancel','lostpointercapture'])canvas.addEventListener(evt,e=>{pointers.delete(e.pointerId);pinch=0;});
canvas.addEventListener('wheel',e=>{e.preventDefault();state.target=null;state.radius=Math.max(.035,Math.min(3,state.radius*Math.exp(e.deltaY*.001)));request();},{passive:false});
canvas.addEventListener('keydown',e=>{let hit=true;state.target=null;switch(e.key){case'ArrowLeft':state.yaw-=.12;break;case'ArrowRight':state.yaw+=.12;break;case'ArrowUp':state.pitch=Math.min(1.2,state.pitch+.1);break;case'ArrowDown':state.pitch=Math.max(-1.2,state.pitch-.1);break;case'+':case'=':state.radius=Math.max(.035,state.radius*.85);break;case'-':state.radius=Math.min(3,state.radius/.85);break;case'Home':reset();break;default:hit=false;}if(hit){e.preventDefault();request();}});
$('theme').addEventListener('click',()=>setTheme(state.theme==='dark'?'light':'dark'));
$('reset').addEventListener('click',()=>reset());$('explode').addEventListener('click',()=>{state.exploded=!state.exploded;$('explode').setAttribute('aria-pressed',String(state.exploded));$('explode').textContent=state.exploded?'Reassemble':'Separate parts';reset();request();});
$('rotate').addEventListener('click',()=>{if(reduced.matches)return;state.spin=!state.spin;updateSpin();request();});
reduced.addEventListener('change',()=>{if(reduced.matches){state.spin=false;updateSpin();}$('rotate').disabled=reduced.matches;request();});$('rotate').disabled=reduced.matches;
document.querySelectorAll('[data-model]').forEach(b=>b.addEventListener('click',()=>gl?load(b.dataset.model):metadataOnly(b.dataset.model)));
async function metadataOnly(key){if(!Object.keys(defs).includes(key))return;state.model=key;setDetail(null);document.querySelectorAll('[data-model]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.model===key)));try{const r=await fetch(defs[key].src);if(!r.ok)throw Error('Unavailable');const {json}=parseGLB(await r.arrayBuffer());state.parts=json.nodes.map(n=>n.extras).filter(Boolean);makeParts();}catch{$('part-body').textContent='This equipment asset is unavailable in this build.';}['reset','explode','rotate'].forEach(id=>$(id).disabled=true);}
function pause(){state.paused=true;if(state.frame)cancelAnimationFrame(state.frame);state.frame=0;}
function resume(){state.paused=document.hidden;state.last=0;request();}
document.addEventListener('visibilitychange',()=>document.hidden?pause():resume());
canvas.addEventListener('webglcontextlost',e=>{e.preventDefault();pause();state.ready=false;$('fallback').hidden=false;$('fallback').querySelector('p').textContent='The graphics context was interrupted. Reopen this screen to reload the studio. Equipment descriptions remain available.';});
window.addEventListener('pagehide',pause);window.addEventListener('pageshow',resume);
window.CrocodylAtelier={pause,resume,setTheme,dispose(){disposed=true;pause();resizeObserver?.disconnect();if(gl){disposeModel();if(program)gl.deleteProgram(program);}},getStatus(){return{ready:state.ready,model:state.model,drawCalls:state.draws.length,renderPending:!!state.frame,paused:state.paused,selected:state.selected,theme:state.theme};}};
try{init();setTheme(initial.get('theme')||'dark');load(Object.keys(defs).includes(initial.get('model'))?initial.get('model'):'recurve');}catch(e){console.error(e);$('loading').hidden=true;$('fallback').hidden=false;metadataOnly(Object.keys(defs).includes(initial.get('model'))?initial.get('model'):'recurve');}
})();
