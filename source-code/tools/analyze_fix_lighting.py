import collections,json,sys
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'fix-evidence'/('client-'+(sys.argv[1] if len(sys.argv)>1 and sys.argv[1].isdigit() else '2'));rows=[json.loads(line) for line in (D/'light-trace.jsonl').read_text(encoding='utf-8').splitlines()];groups=collections.defaultdict(list);pairs={}
def key(row):return json.dumps([row['root'],row['model'],row['vertices'],row['sprite']],separators=(',',':'))
def texture(row):
 v=int(row.get('textureCenterABGR','ffffffff'),16);return round(((v&255)+((v>>8)&255)+((v>>16)&255))/(3*255),4)
for row in rows:
 row['meanBrightness']=sum(row['brightness'])/4;row['skyValues']=[(v>>20)&15 for v in row['lights']];row['blockValues']=[(v>>4)&15 for v in row['lights']];row['textureMean']=texture(row);groups[row['phase']].append(row);pairs[(row['phase'].split('_',2)[-1],key(row),'OFF' if 'AO_OFF' in row['phase'] else 'ON')]=row
differences=[]
for (scene,k,mode),a in pairs.items():
 if mode!='ON':continue
 b=pairs.get((scene,k,'OFF'))
 if b is not None and (abs(a['meanBrightness']-b['meanBrightness'])>.2 or a['skyValues']!=b['skyValues']):differences.append({'on':a,'off':b})
summary={phase:{'quads':len(g),'darkAO':sum(r['meanBrightness']<.3 for r in g),'packedSkyBelowSurface':sum(max(r['skyValues'])+3<r['surfaceSky'] for r in g),'darkTextureCenter':sum(r['textureMean']<.1 for r in g),'brightnessOutsideNormalizedRange':sum(min(r['brightness'])<0 or max(r['brightness'])>1 for r in g)} for phase,g in groups.items()}
focused=[]
for phase in ('AO_ON_3','AO_OFF_3','AO_ON_6','AO_OFF_6'):
 target=[75,26,-851] if phase.endswith('_3') else [85,48,-1011]
 selected=[r for r in groups[phase] if r['root']==target]
 focused.append({'phase':phase,'root':target,'quads':len(selected),'minBrightness':min((min(r['brightness']) for r in selected),default=None),'maxBrightness':max((max(r['brightness']) for r in selected),default=None),'darkest':sorted(selected,key=lambda r:sum(r['lights'])/4*max(.001,r['meanBrightness']))[:8]})
invalid=[r for r in rows if min(r['brightness'])<0 or max(r['brightness'])>1]
out={'rows':len(rows),'phases':summary,'changedBetweenAoModes':len(differences),'outOfRangeExamples':invalid[:20],'focused':focused,'largestAoDifferences':sorted(differences,key=lambda p:abs(p['on']['meanBrightness']-p['off']['meanBrightness']),reverse=True)[:50]};(D/'lighting-analysis.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(summary,indent=2));print(json.dumps(focused,indent=2))
