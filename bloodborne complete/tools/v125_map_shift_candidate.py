"""Readonly nearby city bounds alternatives avoiding protected material1 mismatch."""
import collections,json
from v125_map_city import read_box,selected_apps,DEST
from v125_map_index import ROOT,OUT,MAP,PACK,dump,sha
from scan_original_map_models import raw_blockstates
from v125_map_resolve import Resolver

def main():
    meta=json.loads((OUT/'original-map-index.json').read_text())['models'];finite={m for m,v in meta.items() if v['hasGeometry'] and not v['fullCubeContext']};raw,_,_=raw_blockstates();res=Resolver();results=[]
    for dz in [1,2,3,4]:
        box=[144,64,-1088+dz,175,112,-1057+dz];clip=read_box(box);models=collections.Counter();wall1=[];exceptions=[];affected=0
        for x,y,z,key,carrier,props in clip['blocks']:
            if carrier not in raw:continue
            affected+=1;apps=selected_apps(raw,carrier,props,(x,y,z));models.update(a['model'] for a in apps)
            if carrier=='minecraft:polished_deepslate_wall' and any(a['model'].endswith('_side_tall_1') for a in apps):wall1.append([x,y,z])
            if carrier in {'minecraft:jungle_trapdoor','minecraft:potted_spruce_sapling'}:exceptions.append({'sourcePosition':[x,y,z],'sourceState':key})
        results.append({'offsetZ':dz,'boundsInclusive':box,'nonAirCells':len(clip['blocks']),'affectedCarrierCells':affected,'finiteSelectedModels':len(set(models)&finite),'selectedModels':len(models),'protectedMaterial1UnsupportedCells':wall1,'explicitDeletedOrMissingExceptions':exceptions,'blockEntities':len(clip['blockEntities']),'status':'SOURCE_READONLY_NEARBY_ALTERNATIVE_NOT_SELECTED'})
    dump(DEST/'nearby-bounds-source-review.json',{'status':'PASS_SOURCE_BOUNDS_COMPARISON','mapSha256':sha(MAP),'packSha256':sha(PACK),'baselineCandidateFiniteModels':249,'baselineCandidateBounds':[144,64,-1088,175,112,-1057],'candidates':results,'recommendation':'Prefer smallest shifted bbox withzeroexistingV12material1mismatches andcomparable source diversity, ifrootapproves; rawsource stillmustexportfullchosenvolume.'})

if __name__=='__main__':main()
