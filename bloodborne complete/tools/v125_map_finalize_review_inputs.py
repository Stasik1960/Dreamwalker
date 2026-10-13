"""Freeze manually inspected source projection review inputs for gallery author."""
import json
from v125_map_index import ROOT,BASELINE,dump,sha
BASE=ROOT/'docs/v12.5-city-review'

def main():
    projection=json.loads((BASE/'assemblies/gallery-source-preview-projections.json').read_text());retirement=BASE/'style-retirement-confirmed.json'
    assert len(json.loads(retirement.read_text(encoding='utf-8-sig'))['retirements'])==21
    observed=[]
    for section,file,final in [('assemblies','gallery-preview-meaningful-assemblies.json','gallery-review-platforms-final.json'),('doors','gallery-door-preview-platforms.json','gallery-door-platforms-final.json')]:
        data=json.loads((BASE/section/file).read_text(encoding='utf-8-sig'));sheet=next(r for r in projection['sheets'] if r['section']==section);assert sha(ROOT/sheet['fileName'])==sheet['sha256']
        data['sourceProjectionReview']={'status':'ACTUALLY_VIEWED_JOINED_SOURCE_POLYGONS','sheetFile':sheet['fileName'],'sheetSha256':sheet['sha256'],'platformsActuallyViewed':len(data['platforms']),'nativeGamePixels':'NOT_RUN','observation':'Complementarywindowframes/capsandstoneornament,doorpanels/transoms,continuouspilaster/ladder/wooddecorationarevisibleinjoinedsourceprojections. OrdinaryLwallcornerandtwoseparateroundwindowsremovedtoreportafteractualpixelinspection. Doorgrille/shutter/framefunctionremainsreviewhypothesis.'}
        data['styleRetirementSha256']=sha(retirement)
        for no,p in enumerate(data['platforms'],1):p['sourceProjectionManuallyViewed']=True;p['sourceProjectionTileNumber']=no;p['nativeReview']='NOT_RUN'
        dump(BASE/section/final,data);observed.append({'section':section,'fileName':str((BASE/section/final).relative_to(ROOT)),'sha256':sha(BASE/section/final),'platformCount':len(data['platforms']),'sourceSheetSha256':sheet['sha256']})
    dump(BASE/'assemblies/final-source-preview-review.json',{'schemaVersion':1,'status':'PASS_SOURCE_PROJECTION_SELECTION_ONLY','baselineCommit':BASELINE,'reviewedFiles':observed,'sourceDoorNominationSheetsActuallyViewed':5,'sourceDoorNominationsViewed':150,'joinedConstructionAndDoorSheetsActuallyViewed':2,'allSelectedSourceLayoutsActuallyViewed':True,'removedAfterPixels':[{'originalCompactMainIds':['90253','90253'],'reason':'OrdinaryL-shapedmasonrycorner, notfinitepilasterwhole.'},{'originalCompactMainIds':['90304','90390'],'reason':'Twoindependentroundwindowswithgap, notcomplementaryparts.'}],'notNativeAcceptance':'NoMinecraftgameprocesswaslaunchedbythisagent. Actualnativegalleryrendering/placement/seating/interactionremainsrootQA.'})

if __name__=='__main__':main()
