"""Print bounded current gallery-plan facts into a local structured report."""
import argparse,json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--input',type=Path,required=True);p.add_argument('--report',type=Path,required=True);a=p.parse_args()
m=json.loads(a.input.read_text(encoding='utf-8-sig'));city=m['city']
result={'schema':m['gallerySchema'],'productionJarSha256':m['productionJarSha256'],'buildingItems':len(m['catalogue']['exhibits']),'canonicalNewArchitecture':sum(r['origin']!='V12' and r['kind']=='architecture' for r in m['catalogue']['exhibits']),'reviewPlatforms':len(m['reviewPlatforms']),'cityOwners':len(city['owners']),'cityNativeStates':len(city['passThroughBlocks']),'sourceNativeOffsets':sum('plannedNativeSourceModelOffset' in r for r in city['owners']),'remainingOriginalSourceOmissions':city['remainingPreparedOmissions'],'cityBounds':city['localVisualBounds']}
a.report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
