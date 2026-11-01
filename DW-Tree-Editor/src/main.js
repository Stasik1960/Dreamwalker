
import * as THREE from '../vendor/three/build/three.module.js';
import {OrbitControls} from '../vendor/three/examples/jsm/controls/OrbitControls.js';
import {TransformControls} from '../vendor/three/examples/jsm/controls/TransformControls.js';
import presets from './presets.json';
import {clone,records,moveCube,duplicate,remove,validate,pivot} from './project.mjs';

const $=id=>document.getElementById(id), store='dreamwalker-tree-editor-v1';
let project=clone(presets.original), active=0, selected=null, rows=[], objects=new Map(), bones=new Map(), root=null, helper=null;
let history=[],future=[],dirty=false,dragStart=null,dragged=false,textureCache=new Map();
const status=(s)=>{$('status').textContent=s;};
const model=()=>project.models[active];
function autosave(){dirty=true;try{localStorage.setItem(store,JSON.stringify({project,active}));status('Черновик сохранён в браузере · для передачи нажми «Сохранить проект»');}catch{status('Для сохранения на диск нажми «Сохранить проект»');}}
function remember(){history.push(JSON.stringify(project));if(history.length>45)history.shift();future=[];buttons();}
function buttons(){for(const id of ['duplicate','delete','focus','move','rotate','apply'])$(id).disabled=!selected;$('undo').disabled=!history.length;$('redo').disabled=!future.length;}
const scene=new THREE.Scene();scene.background=new THREE.Color('#20262c');
const camera=new THREE.PerspectiveCamera(42,1,.02,1000);camera.position.set(18,12,22);
let renderer;
try{renderer=new THREE.WebGLRenderer({antialias:true,preserveDrawingBuffer:false});}catch(e){$('fatal').hidden=false;throw e;}
renderer.setPixelRatio(Math.min(devicePixelRatio,2));renderer.outputColorSpace=THREE.SRGBColorSpace;
$('viewport').appendChild(renderer.domElement);
const orbit=new OrbitControls(camera,renderer.domElement);orbit.enableDamping=true;orbit.dampingFactor=.1;
const transform=new TransformControls(camera,renderer.domElement);transform.setSpace('world');scene.add(transform.getHelper());
scene.add(new THREE.HemisphereLight(0xffffff,0x6c6d61,2));const sun=new THREE.DirectionalLight(0xfff1d9,2);sun.position.set(8,16,10);scene.add(sun);
const grid=new THREE.GridHelper(40,40,0x788276,0x3a4448);scene.add(grid);
const ray=new THREE.Raycaster(),mouse=new THREE.Vector2();
function rot(v=[0,0,0]){return new THREE.Euler(-v[0]*Math.PI/180,v[1]*Math.PI/180,-v[2]*Math.PI/180,'ZYX');}
const V=a=>new THREE.Vector3(...a);
function geometry(c,pivot,mirror){
  const inf=c.inflate||0,a=c.origin.map(v=>v-inf),s=c.size.map(v=>v+2*inf);
  const [x,y,z]=a,[dx,dy,dz]=s;
  const v=[[x,y,z],[x+dx,y,z],[x+dx,y+dy,z],[x,y+dy,z],[x,y,z+dz],[x+dx,y,z+dz],[x+dx,y+dy,z+dz],[x,y+dy,z+dz]];
  const faces={north:[1,0,3,2],south:[4,5,6,7],west:[0,4,7,3],east:[5,1,2,6],up:[3,7,6,2],down:[0,1,5,4]};
  const positions=[],uvs=[];const desc=model().geometry['minecraft:geometry'][0].description;
  for(const [face,f] of Object.entries(c.uv)){
    if(!faces[face]||!f?.uv)continue;
    const uv=f.uv,sz=f.uv_size||[1,1];let map=[[uv[0]+sz[0],uv[1]+sz[1]],[uv[0],uv[1]+sz[1]],[uv[0],uv[1]],[uv[0]+sz[0],uv[1]]];
    if(mirror)map=map.map(t=>[uv[0]*2+sz[0]-t[0],t[1]]);
    const turns=((f.uv_rotation||0)/90)%4;if(turns)map=map.map((_,i)=>map[(i+turns)%4]);
    for(const q of [0,1,2,0,2,3]){positions.push(...v[faces[face][q]].map((k,i)=>k-pivot[i]));uvs.push(map[q][0]/desc.texture_width,map[q][1]/desc.texture_height);}
  }
  const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));g.setAttribute('uv',new THREE.Float32BufferAttribute(uvs,2));g.computeVertexNormals();return g;
}
async function texture(){
  const url=project.textures[model().texture].dataURL;
  if(textureCache.has(url))return textureCache.get(url);
  const image=new Image();image.src=url;await image.decode();
  const tex=new THREE.Texture(image);tex.colorSpace=THREE.SRGBColorSpace;tex.flipY=false;tex.magFilter=THREE.NearestFilter;tex.minFilter=THREE.NearestFilter;tex.generateMipmaps=false;tex.needsUpdate=true;
  const cv=document.createElement('canvas');cv.width=image.naturalWidth;cv.height=image.naturalHeight;const ctx=cv.getContext('2d',{willReadFrequently:true});ctx.drawImage(image,0,0);const pixels=ctx.getImageData(0,0,cv.width,cv.height);
  const data={material:new THREE.MeshLambertMaterial({map:tex,alphaTest:.1,side:THREE.DoubleSide}),pixels};textureCache.set(url,data);return data;
}
let buildToken=0;
async function rebuild(key=selected?.key,frame=false){
  const token=++buildToken;const tex=await texture();if(token!==buildToken)return;
  transform.detach();if(helper){scene.remove(helper);helper.dispose();helper=null;}
  if(root){scene.remove(root);root.traverse(o=>{if(o.isMesh)o.geometry.dispose();});}
  rows=records(model());objects=new Map();bones=new Map();root=new THREE.Group();root.scale.set(-1/16,1/16,1/16);scene.add(root);
  const raw=new Map(model().geometry['minecraft:geometry'][0].bones.map(b=>[b.name,b]));
  function bone(b){if(bones.has(b.name))return bones.get(b.name);const parent=b.parent?bone(raw.get(b.parent)):root;const pp=b.parent?(raw.get(b.parent).pivot||[0,0,0]):[0,0,0];const g=new THREE.Group();g.position.copy(V(b.pivot||[0,0,0]).sub(V(pp)));g.rotation.copy(rot(b.rotation));parent.add(g);bones.set(b.name,g);return g;}
  for(const r of rows){const b=bone(r.bone),p=pivot(r.cube),g=new THREE.Group();g.position.copy(V(p).sub(V(r.bone.pivot||[0,0,0])));g.rotation.copy(rot(r.cube.rotation));b.add(g);const mesh=new THREE.Mesh(geometry(r.cube,p,r.cube.mirror??r.bone.mirror),tex.material);mesh.userData.record=r;mesh.userData.pixels=tex.pixels;g.add(mesh);objects.set(r.key,g);}
  root.updateMatrixWorld(true);selected=rows.find(r=>r.key===key)||null;renderList();select(selected?.key);if(frame)frameTree();
  $('counter').textContent=rows.length+' деталей · '+rows.filter(r=>r.leaf).length+' спрайтов';
}
function showInputs(){
 const c=selected?.cube;for(const [name,arr] of [['pos',c?.origin],['rot',c?.rotation||[0,0,0]],['size',c?.size]])for(let i=0;i<3;i++){const input=$(name+i);input.disabled=!c;input.value=c?Number(arr[i].toFixed(3)):'';}
 $('selected').textContent=selected?(selected.leaf?'Спрайт':'Деталь')+' · '+selected.bone.name+' / '+(selected.index+1):'Выбери элемент дерева';buttons();
}
function select(key){
 selected=rows.find(r=>r.key===key)||null;transform.detach();if(helper){scene.remove(helper);helper.dispose();helper=null;}
 if(selected){const obj=objects.get(selected.key);transform.attach(obj);helper=new THREE.BoxHelper(obj,0xe2bc78);scene.add(helper);}
 for(const button of $('elements').children)button.classList.toggle('chosen',button.dataset.key===selected?.key);
 showInputs();
}
function renderList(){
 const filter=$('filter').value,q=$('search').value.toLowerCase();$('elements').replaceChildren();
 for(const r of rows){if(filter==='leaf'&&!r.leaf||filter==='wood'&&r.leaf||q&&!r.bone.name.toLowerCase().includes(q))continue;const b=document.createElement('button');b.dataset.key=r.key;b.textContent=(r.leaf?'◇ ':'▰ ')+r.bone.name+' · '+(r.index+1);b.title=r.bone.name+' / '+r.index;b.onclick=()=>select(r.key);$('elements').appendChild(b);}
 $('visiblecount').textContent=$('elements').children.length+' в списке';
}
function frameTree(){if(!root)return;const box=new THREE.Box3().setFromObject(root);if(box.isEmpty())return;const center=box.getCenter(new THREE.Vector3()),size=Math.max(1,box.getSize(new THREE.Vector3()).length());orbit.target.copy(center);camera.position.copy(center).add(V([.75,.35,.9]).multiplyScalar(size));camera.near=.02;camera.far=Math.max(500,size*30);camera.updateProjectionMatrix();orbit.update();}
$('fit').onclick=frameTree;
$('focus').onclick=()=>{if(!selected)return;const box=new THREE.Box3().setFromObject(objects.get(selected.key)),center=box.getCenter(new THREE.Vector3()),distance=Math.max(2.5,box.getSize(new THREE.Vector3()).length()*2);const dir=camera.position.clone().sub(orbit.target).normalize();orbit.target.copy(center);camera.position.copy(center).add(dir.multiplyScalar(distance));orbit.update();};
for(const [id,mode] of [['move','translate'],['rotate','rotate']])$(id).onclick=()=>{transform.setMode(mode);$('move').classList.toggle('active',mode==='translate');$('rotate').classList.toggle('active',mode==='rotate');};
$('snap').onchange=()=>{transform.setTranslationSnap($('snap').checked?1/16:null);transform.setRotationSnap($('snap').checked?Math.PI/12:null);};
transform.addEventListener('dragging-changed',e=>{orbit.enabled=!e.value;dragged=e.value||dragged;if(!e.value&&dragStart){autosave();dragStart=null;rebuild();}});
transform.addEventListener('mouseDown',()=>{if(!selected)return;remember();dragStart={cube:clone(selected.cube),position:transform.object.position.clone()};});
transform.addEventListener('objectChange',()=>{
 if(!selected||!dragStart)return;
 if(transform.getMode()==='translate'){const delta=transform.object.position.clone().sub(dragStart.position).toArray();Object.assign(selected.cube,clone(dragStart.cube));moveCube(selected.cube,delta);}
 else {selected.cube.pivot=pivot(dragStart.cube);const e=transform.object.rotation.clone().reorder('ZYX');selected.cube.rotation=[-e.x,e.y,-e.z].map(v=>v*180/Math.PI);}
 showInputs();if(helper)helper.update();
});

function applyValues(){
 if(!selected)return;
 const values={};for(const name of ['pos','rot','size'])values[name]=[0,1,2].map(i=>Number($(name+i).value));
 if(Object.values(values).flat().some(v=>!Number.isFinite(v)||Math.abs(v)>100000)||values.size.some(v=>v<0)){status('Проверь координаты: нужны конечные числа, размеры не меньше нуля.');return;}
 remember();const c=selected.cube;moveCube(c,values.pos.map((v,i)=>v-c.origin[i]));c.rotation=values.rot;c.size=values.size;autosave();rebuild();
}
$('apply').onclick=applyValues;
for(const name of ['pos','rot','size'])for(let i=0;i<3;i++)$(name+i).onkeydown=e=>{if(e.key==='Enter'){e.preventDefault();applyValues();}};
$('duplicate').onclick=()=>{if(!selected)return;remember();const key=duplicate(selected);autosave();rebuild(key);};
$('delete').onclick=()=>{if(!selected)return;remember();remove(selected);selected=null;autosave();rebuild(null);};
$('undo').onclick=()=>{if(!history.length)return;future.push(JSON.stringify(project));project=JSON.parse(history.pop());selected=null;autosave();rebuild(null);};
$('redo').onclick=()=>{if(!future.length)return;history.push(JSON.stringify(project));project=JSON.parse(future.pop());selected=null;autosave();rebuild(null);};
$('filter').onchange=renderList;$('search').oninput=renderList;
$('tree').onchange=()=>{active=Number($('tree').value);selected=null;rebuild(null,true);};
$('preset').onchange=()=>{const choice=$('preset').value;if(!presets[choice])return;if(dirty&&!confirm('Загрузить исходный набор? Текущие правки можно сначала сохранить кнопкой «Сохранить проект».')){$('preset').value=project.sourcePreset||'original';return;}remember();project=clone(presets[choice]);selected=null;autosave();rebuild(null,true);};
$('reset').onclick=()=>{if(!confirm('Вернуть исходное дерево? Это действие можно отменить.'))return;remember();model().geometry=clone((presets[project.sourcePreset]||presets.original).models.find(m=>m.id===model().id).geometry);selected=null;autosave();rebuild(null,true);};
function download(data,name,type){const url=URL.createObjectURL(new Blob([data],{type})),a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),30000);}
$('save').onclick=()=>{validate(project);const data=clone(project);data.savedAt=new Date().toISOString();data.editor={activeTree:active};download(JSON.stringify(data,null,2),'DreamWalker-trees.dwtree.json','application/json');dirty=false;status('Проект скачан. Передай файл DreamWalker-trees.dwtree.json в чат.');};
$('export').onclick=()=>download(JSON.stringify(model().geometry,null,2),model().id+'.geo.json','application/json');
$('open').onclick=()=>$('file').click();$('file').onchange=async()=>{const file=$('file').files[0];if(!file)return;try{if(file.size>20000000)throw Error('Файл больше 20 МБ.');const next=validate(JSON.parse(await file.text()));remember();project=next;active=Math.max(0,Math.min(2,next.editor?.activeTree||0));$('tree').value=active;selected=null;autosave();rebuild(null,true);status('Проект открыт: '+file.name);}catch(e){alert('Не удалось открыть проект: '+e.message);}finally{$('file').value='';}};
let press;
renderer.domElement.addEventListener('pointerdown',e=>{press={x:e.clientX,y:e.clientY};dragged=false;});
renderer.domElement.addEventListener('pointerup',e=>{
 if(!press||dragged||transform.axis||Math.hypot(e.clientX-press.x,e.clientY-press.y)>5)return;const rect=renderer.domElement.getBoundingClientRect();mouse.set((e.clientX-rect.left)/rect.width*2-1,-(e.clientY-rect.top)/rect.height*2+1);ray.setFromCamera(mouse,camera);
 const hits=ray.intersectObject(root,true);
 for(const h of hits){const r=h.object.userData.record;if(!r||$('filter').value==='leaf'&&!r.leaf||$('filter').value==='wood'&&r.leaf)continue;const px=h.object.userData.pixels;if(h.uv&&px){const x=Math.max(0,Math.min(px.width-1,Math.floor(h.uv.x*px.width))),y=Math.max(0,Math.min(px.height-1,Math.floor(h.uv.y*px.height)));if(px.data[(y*px.width+x)*4+3]<26)continue;}select(r.key);return;}select(null);
});
window.addEventListener('keydown',e=>{if(/INPUT|SELECT|TEXTAREA/.test(document.activeElement.tagName))return;
 const c=e.ctrlKey||e.metaKey;
 if(c&&e.code==='KeyS'){e.preventDefault();$('save').click();}else if(c&&e.code==='KeyZ'){e.preventDefault();$(e.shiftKey?'redo':'undo').click();}else if(c&&e.code==='KeyY'){e.preventDefault();$('redo').click();}else if(c&&e.code==='KeyD'){e.preventDefault();$('duplicate').click();}else if(e.code==='Delete'||e.code==='Backspace'){e.preventDefault();$('delete').click();}else if(e.code==='KeyW')$('move').click();else if(e.code==='KeyE')$('rotate').click();else if(e.code==='KeyF')$('focus').click();else if(e.code==='Escape')select(null);
});
window.addEventListener('beforeunload',e=>{if(dirty){e.preventDefault();e.returnValue='';}});
new ResizeObserver(()=>{const r=$('viewport').getBoundingClientRect();renderer.setSize(r.width,r.height);camera.aspect=r.width/r.height;camera.updateProjectionMatrix();}).observe($('viewport'));
try{const saved=JSON.parse(localStorage.getItem(store));if(saved?.project){project=validate(saved.project);active=Math.max(0,Math.min(2,saved.active||0));status('Восстановлен черновик из браузера. Сохрани проект на диск для передачи.');}}catch{}
$('tree').value=active;$('preset').value=project.sourcePreset||'original';
rebuild(null,true).catch(e=>{status('Ошибка: '+e.message);console.error(e);});
renderer.setAnimationLoop(()=>{orbit.update();if(helper)helper.update();renderer.render(scene,camera);});
