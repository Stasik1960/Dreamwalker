
import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';import {clone,records,moveCube,duplicate,remove,validate,pivot} from '../src/project.mjs';
const presets=JSON.parse(fs.readFileSync(new URL('../src/presets.json',import.meta.url),'utf8'));
test('three-tree project preserves moves, rotations, copy/delete and all UVs through save/open',()=>{
 for(const preset of Object.values(presets)){
  const before=JSON.stringify(preset),p=clone(preset);validate(p);const a=records(p.models[0])[0],old=a.cube.origin.slice(),uv=JSON.stringify(a.cube.uv),anchor=pivot(a.cube).slice();
  moveCube(a.cube,[5,-2,7]);a.cube.rotation=[10,25,-15];assert.deepEqual(a.cube.origin,[old[0]+5,old[1]-2,old[2]+7]);assert.deepEqual(a.cube.pivot,[anchor[0]+5,anchor[1]-2,anchor[2]+7]);assert.equal(JSON.stringify(a.cube.uv),uv);
  const count=records(p.models[0]).length,key=duplicate(a);assert.equal(records(p.models[0]).length,count+1);remove(records(p.models[0]).find(r=>r.key===key));assert.equal(records(p.models[0]).length,count);
  const loaded=validate(JSON.parse(JSON.stringify(p)));assert.deepEqual(loaded,p);assert.equal(JSON.stringify(preset),before);assert.deepEqual(loaded.models[1],preset.models[1]);assert.deepEqual(loaded.models[2],preset.models[2]);
 }
});
test('invalid files and cyclic bones are rejected without mutating working project',()=>{
 assert.throws(()=>validate({}));const p=clone(presets.original),b=p.models[0].geometry['minecraft:geometry'][0].bones;b[0].parent=b[0].name;assert.throws(()=>validate(p));
});
