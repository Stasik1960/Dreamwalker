"""Exact appropriate vanilla counterpart for one unmodified source fallback."""
import gzip,json
from v125_map_index import ROOT,MAP,sha,token
from v127_apply_full_map_poses import write
from v127_retired_source_policy_audit import VanillaVisuals,options,app,surfaces
from v125_map_door_projections import SourceVisuals
from v125_map_verify_extra_pose_uv import signature
def main():
    source=SourceVisuals();native=VanillaVisuals();rows=[]
    with gzip.open(ROOT/'docs/v12.7/conversion/source-removal-coordinates.jsonl.gz','rt',encoding='utf8') as stream:
        for line in stream:
            r=json.loads(line)
            if r['reason']!='USER_REMOVED_VANILLA_MODEL_TEXTURE_FALLBACK':continue
            state=r['sourceState'];carrier,_,tail=state.partition('[');props=dict(p.split('=',1) for p in tail.rstrip(']').split(',') if p);target={'face':'floor','facing':props['facing'],'powered':props.get('powered','false')};one=signature(surfaces(source,r['sourceApplications']),source);matches=[]
            for facing in ['north','east','south','west']:
                candidate={**target,'facing':facing};apps=[app(v) for branch in options(native,'minecraft:acacia_button',candidate) for v in branch]
                if one==signature(surfaces(native,apps),native):matches.append((candidate,apps))
            assert matches,(r['sourceApplications'],target);target,applications=matches[0]
            for start,length in r['linearRuns']:
                for i in range(start,start+length):
                    p=[r['chunk'][0]*16+(i&15),r['sectionY']*16+(i>>8),r['chunk'][1]*16+((i>>4)&15)];rows.append({'dimension':r['dimension'],'position':p,'sourceState':state,'sourceApplications':r['sourceApplications'],'targetRegistryId':'minecraft:acacia_button','targetProperties':target,'targetApplications':applications,'status':'PASS_EXACT_VERTEX_UV_RGBA_TINTINDEX'})
    assert len(rows)==1,len(rows);write(ROOT/'docs/v12.7/conversion/appropriate-vanilla-button.json',{'status':'PASS_EXACT_APPROPRIATE_VANILLA_COUNTERPART','rows':rows,'sourceMapSha256':sha(MAP),'sourceArchivesModified':False,'worldWritten':False});print(token(rows))
if __name__=='__main__':main()
