import hashlib,json,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'npc-evidence';R=P/'src/rp/resources';cat=json.loads((R/'assets/bloodborne_rp/catalog.json').read_text());ids=list(json.loads((P/'interaction-evidence/source-files.json').read_text()))
names=['cleric_beast','vicar_amelia','blood_starved_beast','scourge_beast','executioner','huntsman_a','huntsman_b','huntsman_c','huntsman_d','huntsman_wheelchair','large_huntsman','carrion_crow','giant_rat','small_rat','rabid_dog','maneater_boar','brick_troll','rotted_corpse'];rows=[]
for name in names:
 spec=cat[name];row={'id':name,'scale':spec['scale'],'clips':spec['clips'],'resources':{}}
 for key in ('model','texture','animation'):
  value=spec[key];namespace,path=value.split(':',1) if ':' in value else ('bloodborne_rp',value);file=R/'assets'/namespace/path;assert file.is_file(),str(file);row['resources'][key]={'path':str(file.relative_to(R)),'sha256':hashlib.sha256(file.read_bytes()).hexdigest()}
 rows.append(row)
(D/'assets-audit.json').write_text(json.dumps({'status':'PASS','mobs':len(rows),'missingDeath':[r['id'] for r in rows if not any(k.startswith('death') for k in r['clips'])],'missingIdle':[r['id'] for r in rows if not any(k.startswith('idle') for k in r['clips'])],'assets':rows},indent=2),encoding='utf-8')
with zipfile.ZipFile(next(Path('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker/mods').glob('CustomNPCs-*.jar'))) as z:(D/'custom-npc-metadata.json').write_text(z.read('fabric.mod.json').decode('utf-8'),encoding='utf-8')
