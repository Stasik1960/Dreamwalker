"""Read exact source blockstate JSON for unselected source applications."""
import json, zipfile
from v125_map_index import ROOT, PACK, dump, sha
from scan_original_map_models import raw_blockstates
from v125_map_city import selected_apps

def main():
    raw,_,_=raw_blockstates();report=[]
    cases=[('minecraft:acacia_planks',{},[192,76,-1086]),
           ('minecraft:red_nether_brick_wall',{'east':'low','north':'none','south':'none','up':'false','waterlogged':'false','west':'low'},[176,68,-1071])]
    with zipfile.ZipFile(PACK) as z:
        prefix=next(n[:-len('pack.mcmeta')] for n in z.namelist() if n.endswith('pack.mcmeta'))
        for carrier,props,xyz in cases:
            file=prefix+'assets/minecraft/blockstates/'+carrier.split(':',1)[1]+'.json'
            doc=json.loads(z.read(file));apps=selected_apps(raw,carrier,props,xyz)
            report.append({'carrier':carrier,'sourceProperties':props,'sourcePosition':xyz,
                'sourceJsonPath':file,'sourceJson':doc,'selectedApplications':apps,'sourceJsonSha256':__import__('hashlib').sha256(z.read(file)).hexdigest()})
    dump(ROOT/'docs/v12.6-city-review/map/citycandidate/empty-source-states.json',{'status':'READONLY_EXACT_SOURCE_JSON_EVIDENCE','packSha256':sha(PACK),'rows':report})
    print(json.dumps(report,ensure_ascii=False))

if __name__=='__main__':main()
