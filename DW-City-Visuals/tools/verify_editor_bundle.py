"""Check city resource preservation and Mixin namespace isolation before runtime tests."""
import argparse,json,zipfile
from pathlib import Path

def verify(reference,candidate):
    failures=[];checked=0
    with zipfile.ZipFile(reference) as old,zipfile.ZipFile(candidate) as new:
        names=set(new.namelist());meta=json.loads(new.read('fabric.mod.json'))
        for name in old.namelist():
            if name not in names:
                # Private record used only by the removed creative-state expansion;
                # Neither this helper nor the removed creative-only fence appender is a registered or saved type.
                if name not in {
                    'dev/dreamwalker/bloodbornedw/editor/catalogue/EditorCatalogue$SourcePalette.class',
                    'dev/dreamwalker/bloodbornedw/composite/overlaymixin/FenceUnifiedCatalogueMixin.class',
                }:
                    failures.append('Removed reference entry: '+name)
                continue
            city=name.startswith('dw_city_visuals/') or (name.startswith('assets/') and (name.endswith('.png') or '/models/' in name or '/geo/' in name))
            if city:
                checked+=1
                if old.read(name)!=new.read(name):
                    if name=='dw_city_visuals/alt/assets/minecraft/atlases/blocks.json':
                        before=json.loads(old.read(name));after=json.loads(new.read(name))
                        expected=dict(before)
                        expected['sources']=before['sources']+[{'type':'minecraft:directory','source':'block/editor2_palette','prefix':'block/editor2_palette/'}]
                        if after!=expected:failures.append('Unexpected atlas modification: '+name)
                    else:failures.append('City image/model changed: '+name)
        for entry in meta['mixins']:
            config=entry if isinstance(entry,str) else entry['config'];data=json.loads(new.read(config));package=data['package'].replace('.','/')+'/'
            declared={package+name.replace('.','/')+'.class' for key in ('mixins','client','server') for name in data.get(key,[])}
            for name in declared:
                if name not in names:failures.append('Missing mixin class: '+name)
            for name in names:
                if name.startswith(package) and name.endswith('.class') and '$' not in name and name not in declared:
                    # Multiple configs may share one package (the pre-existing overlay does).
                    all_declared=set()
                    for other in meta['mixins']:
                        other_data=json.loads(new.read(other if isinstance(other,str) else other['config']))
                        prefix=other_data['package'].replace('.','/')+'/'
                        all_declared.update(prefix+n.replace('.','/')+'.class' for side in ('mixins','client','server') for n in other_data.get(side,[]))
                        if other_data.get('plugin'):all_declared.add(other_data['plugin'].replace('.','/')+'.class')
                    if name not in all_declared:failures.append('Ordinary class inside reserved mixin package: '+name)
        for lang in ('ru_ru','en_us'):
            path=f'assets/bloodborne_dw/lang/{lang}.json'
            if path in old.namelist():
                before=json.loads(old.read(path));after=json.loads(new.read(path))
                for key,value in before.items():
                    if after.get(key)!=value:failures.append('Existing translation changed: '+key)
        test_prefixes=tuple('dev/dreamwalker/'+p+'/' for p in ('editorqa','serverqa','perfqa','flightqa','renderdiag','visualqa','qacommon'))
        diagnostic_bridge='dev/dreamwalker/bloodbornedw/composite/RenderDiagBridge'
        production_editor_qa=any(n.startswith(test_prefixes) or n==diagnostic_bridge+'.class' or n.startswith(diagnostic_bridge+'$') for n in names)
        if production_editor_qa:failures.append('Test driver included in production')
    return {'pass':not failures,'cityResourcesUnchanged':checked,'failures':sorted(set(failures))}
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--reference',type=Path,required=True);p.add_argument('--candidate',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    result=verify(a.reference,a.candidate);a.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result))
    if not result['pass']:raise SystemExit(1)
if __name__=='__main__':main()
