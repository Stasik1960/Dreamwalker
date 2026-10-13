"""Join actual native occupied-cell proof to conservative full source extents.

Includes the separately native-proven original model-offset translation, using
a one-cell conservative X/Z expansion only for its 64 NEW owner placements.
No source world write/load or full-map per-cell object list.
"""
import argparse,json,math
from pathlib import Path
from v125_map_index import ROOT,token,sha
from v127_apply_full_map_poses import write
def main(proofPath,offsetPath):
    proof=json.loads(proofPath.read_text(encoding='utf-8-sig'));offset=json.loads(offsetPath.read_text(encoding='utf-8-sig'));annotations=json.loads((ROOT/'docs/v12.7/conversion/final-source-annotations.json').read_text(encoding='utf-8-sig'));plan=ROOT/'build/v127-full-conversion-first.sqlite'
    assert proof['status'].startswith('PASS_NATIVE_') and proof['potentialOwnerBuildHeightConflictCases']==0 and proof['potentialOwnerChunkTicketLimitConflictCases']==0
    assert offset['status']=='PASS_NATIVE_EXACT_ORIGINAL_SOURCE_MODEL_OFFSETS' and offset['sourceCells']==64 and offset['yawChecks']==512 and proof['sourceHashes']==offset['sourceHashes']==annotations['sourceHashes']
    assert annotations['sourcePlanSha256']==sha(plan),'Native/source annotation plan changed'
    assert all(abs(r['nativeSourceModelOffset'][0])<=.25 and r['nativeSourceModelOffset'][1]==0 and abs(r['nativeSourceModelOffset'][2])<=.25 for r in offset['rows'])
    border=annotations['sourceWorldBorder'];assert border['BorderSizeLerpTime']==0;half=border['BorderSize']/2;allRows=[r for r in proof['cases'] if r['action']=='OWNER']+proof['wholeOwnerHeightCases'];minX=minZ=0;maxX=maxZ=0;ticket=0;expanded=0
    for row in allRows:
        extra=1 if row.get('sourceHasNativeModelOffset') else 0;expanded+=bool(extra);a=row['minimumOccupiedXOffset']-extra;b=row['maximumOccupiedXOffset']+extra;c=row['minimumOccupiedZOffset']-extra;d=row['maximumOccupiedZOffset']+extra
        minX=min(minX,a);maxX=max(maxX,b);minZ=min(minZ,c);maxZ=max(maxZ,d)
        # A rectangular superset is conservative for disjoint memberships.
        worst=max(((x+b)//16-(x+a)//16+3)*((z+d)//16-(z+c)//16+3) for x in range(16) for z in range(16));ticket=max(ticket,worst)
    assert ticket<=256
    dimensions=[]
    for source in annotations['sourceChunkExtents']:
        lowX=source['minimumSourceChunkX']*16+minX;highX=source['maximumSourceChunkX']*16+15+maxX;lowZ=source['minimumSourceChunkZ']*16+minZ;highZ=source['maximumSourceChunkZ']*16+15+maxZ
        assert lowX>=border['BorderCenterX']-half and highX<border['BorderCenterX']+half and lowZ>=border['BorderCenterZ']-half and highZ<border['BorderCenterZ']+half
        dimensions.append({'dimension':source['dimension'],'conservativeOccupiedX':[lowX,highX],'conservativeOccupiedZ':[lowZ,highZ],'worldBorderContainsAllOwnerAndWholeOccupiedCells':True})
    result={'status':'PASS_NATIVE_ALL_SOURCE_OWNER_WHOLE_HEIGHT_BORDER_AND_TICKET_BOUNDS','sourcePlanSha256':sha(plan),'sourceHashes':annotations['sourceHashes'],'nativeFootprintProofSha256':sha(proofPath),'nativeOffsetProofSha256':sha(offsetPath),'nativeHeightConflicts':0,'nativeBaseWorstTickets':max(r['worstChunkTicketsWithOneChunkMarginOverAll256RootAlignments'] for r in allRows),'conservativeWorstTicketsIncludingExactSourceModelOffsets':ticket,'authorTicketLimit':256,'nativeOwnerAndWholeStateGroups':len(allRows),'nativeOffsetExpandedGroups':expanded,'offsetCells':64,'offsetMaximumHorizontal':.25,'offsetVertical':0,'dimensions':dimensions,'sourceWorldBorder':border,'scope':'All actual native OWNER/WHOLE memberships, including source shift, rotation, mount and empty source-carrier shadows. Full original entity authoring and full-map launch remain NOT_RUN by user request. Source model offsets use a conservative one-cell horizontal expansion and actual native zero Y.','originalWorldWritten':False}
    write(ROOT/'docs/v12.7/conversion/native-full-plan-bounds-proof.json',result);print(token(result))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--proof',type=Path,required=True);p.add_argument('--offset-proof',type=Path,required=True);a=p.parse_args();main(a.proof,a.offset_proof)
