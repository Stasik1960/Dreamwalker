"""Provide vanilla loading markers for the two appended static recipe banks."""
import json, shutil
from pathlib import Path
P=Path(__file__).resolve().parents[1]
R=P/'src/architecture/resources/assets/bloodborne_dw/blockstates'
template=R/'static_architecture_255.json'
assert len(json.loads(template.read_text())['variants'])==128
for bank in (256,257):shutil.copy2(template,R/f'static_architecture_{bank}.json')
A=R.parent
old=A/'textures/interactions/shutter_glass.png';new=A/'textures/block/interactions/shutter_glass.png'
new.parent.mkdir(parents=True,exist_ok=True)
if old.exists():old.replace(new)
assert new.is_file()
model=A/'models/interactions/shutter_glass.json';data=json.loads(model.read_text())
data['textures']={'1':'bloodborne_dw:block/interactions/shutter_glass','particle':'bloodborne_dw:block/interactions/shutter_glass'}
model.write_text(json.dumps(data,separators=(',',':')),encoding='utf-8')
