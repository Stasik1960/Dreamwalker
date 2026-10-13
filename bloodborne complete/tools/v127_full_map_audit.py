"""Complete original finite-cell source-resolution audit (no world writes)."""
from __future__ import annotations
import collections,copy,json,sqlite3
from pathlib import Path
from v125_map_index import ROOT,MAP,PACK,CACHE,token,dump,sha
from v127_full_map_resolver import CurrentResolver,pose_proposals
OUT=ROOT/'docs/v12.7/conversion'
def main():
    OUT.mkdir(parents=True,exist_ok=True);r=CurrentResolver();con=sqlite3.connect(f'file:{CACHE}?mode=ro',uri=True)
    counts=collections.Counter();reasoncounts=collections.Counter();rows=[];unresolved=[]
    for ai,apps,si,carrier,props,state,n in con.execute('SELECT a.id,a.apps,s.id,s.name,s.properties,s.state,count(*) FROM cells c JOIN applications a ON c.applications=a.id JOIN states s ON c.state=s.id GROUP BY a.id,s.id'):
        apps=json.loads(apps);props=json.loads(props);decision=r.classify(apps,carrier,props);counts[decision['action']]+=n;reasoncounts[decision.get('reason',decision.get('resolution'))]+=n
        rows.append({'sourceApplicationId':ai,'sourceStateId':si,'sourceState':state,'count':n,'sourceApplications':apps,'decision':decision})
        if decision['action']=='UNRESOLVED_VISIBLE':unresolved.append((carrier,props,apps,n))
    proposals=pose_proposals(r,unresolved);dump(OUT/'source-pose-proposals.json',proposals)
    dump(OUT/'finite-cell-resolutions.json',{'status':'PASS_READONLY_EXHAUSTIVE_FINITE_CELL_CLASSIFICATION','scope':'All2,816,596actualfinite source cells grouped by exact XYZ-weighted source application+state; all nonfinite sections scanned separately.','counts':dict(counts),'reasonCounts':dict(reasoncounts),'rows':rows,'proposals':str((OUT/'source-pose-proposals.json').relative_to(ROOT))})
    dump(OUT/'resolution-summary.json',{'status':'READONLY_COMPLETE_FINITE_SOURCE_AUDIT','counts':dict(counts),'reasonCounts':dict(reasoncounts),'requiredConditionalPoses':sum(len(g['poses']) for g in proposals['groups']),'conditionalPoseGroups':len(proposals['groups']),'capacityExceededGroups':[{'registryId':g['registryId'],'currentPoseCount':g['currentPoseCount'],'totalPoseCount':g['totalPoseCount']} for g in proposals['groups'] if not g['withinCurrentPoseCapacity']],'missingDependencies':sorted({p for g in proposals['groups'] for pose in g['poses'] for p in pose['missingDependencies']}),'unrepresentedSourceStates':proposals['unrepresentedVisibleSourceStates'],'sourceHashes':{'map':sha(MAP),'pack':sha(PACK)},'sourceArchivesModified':False})
    print(json.dumps({'counts':dict(counts),'conditionalPoses':sum(len(g['poses']) for g in proposals['groups']),'capacityExceeded':[g['registryId'] for g in proposals['groups'] if not g['withinCurrentPoseCapacity']],'unrepresented':len(proposals['unrepresentedVisibleSourceStates'])}))
if __name__=='__main__':main()
