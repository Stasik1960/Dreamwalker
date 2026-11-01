
export const clone = value => JSON.parse(JSON.stringify(value));
export function records(model) {
  const result=[];
  for(const bone of model.geometry['minecraft:geometry'][0].bones)
    (bone.cubes||[]).forEach((cube,index)=>result.push({bone,cube,index,key:bone.name+':'+index,leaf:cube.size.some(v=>v===0)||/leaf|leaves|foliage|card/i.test(bone.name)}));
  return result;
}
export function pivot(cube){return cube.pivot||((cube.rotation||[]).some(v=>v!==0)?[0,0,0]:cube.origin.map((v,i)=>v+cube.size[i]/2));}
export function moveCube(cube, delta) {
  const anchor=pivot(cube);
  cube.origin=cube.origin.map((v,i)=>v+delta[i]);
  cube.pivot=anchor.map((v,i)=>v+delta[i]);
}
export function duplicate(record) {
  const cube=clone(record.cube);moveCube(cube,[4,0,0]);record.bone.cubes.push(cube);
  return record.bone.name+':'+(record.bone.cubes.length-1);
}
export function remove(record){record.bone.cubes.splice(record.index,1);}
export function validate(project) {
  if(project?.format!=='dreamwalker-tree-project'||project.version!==1||!Array.isArray(project.models)||project.models.length!==3)throw Error('Ожидается файл проекта DreamWalker с тремя деревьями.');
  const ids=new Set();
  for(const model of project.models){
    if(!/^tree_[123]$/.test(model.id)||ids.has(model.id))throw Error('Некорректный набор деревьев.');ids.add(model.id);
    const geo=model.geometry?.['minecraft:geometry']?.[0];if(!geo?.description||!Array.isArray(geo.bones)||geo.bones.length>2000)throw Error('Повреждена геометрия дерева.');
    const names=new Map();let count=0;
    for(const bone of geo.bones){if(typeof bone.name!=='string'||names.has(bone.name))throw Error('Повторяющаяся часть дерева.');names.set(bone.name,bone);
      for(const c of bone.cubes||[]){if(++count>10000)throw Error('Слишком много элементов.');for(const k of ['origin','size','pivot','rotation'])if(c[k]&&(!Array.isArray(c[k])||c[k].length!==3||c[k].some(v=>!Number.isFinite(v)||Math.abs(v)>100000)))throw Error('Некорректные координаты.');if(!c.origin||!c.size||c.size.some(v=>v<0)||!c.uv)throw Error('Некорректный элемент.');}
      for(const k of ['pivot','rotation'])if(bone[k]&&(!Array.isArray(bone[k])||bone[k].length!==3||bone[k].some(v=>!Number.isFinite(v))))throw Error('Повреждён поворот ветки.');
    }
    for(const b of geo.bones){const visited=new Set();let next=b;while(next){if(visited.has(next.name))throw Error('Цикл в ветках.');visited.add(next.name);if(next.parent&&!names.has(next.parent))throw Error('Не найдена родительская ветка.');next=names.get(next.parent);}}
    const t=project.textures?.[model.texture];if(!t||!/^data:image\/png;base64,[A-Za-z0-9+/=\r\n]+$/.test(t.dataURL)||t.dataURL.length>16000000)throw Error('Не найдена PNG-текстура проекта.');
  }
  return project;
}
