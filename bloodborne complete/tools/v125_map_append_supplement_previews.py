"""Freeze supplemental observed decor + unique keys without repainting PNGs."""
import collections,copy,json
from v125_map_index import ROOT,dump,sha
BASE=ROOT/'docs/v12.5-city-review'
DEST=BASE/'assemblies'

def main():
    file=DEST/'gallery-review-platforms-final.json';data=json.loads(file.read_text(encoding='utf-8-sig'));doorfile=BASE/'doors/gallery-door-platforms-final.json';doors=json.loads(doorfile.read_text(encoding='utf-8-sig'));retired={r['registryId'] for r in json.loads((BASE/'style-retirement-confirmed.json').read_text(encoding='utf-8-sig'))['retirements']}
    renamed=[]
    for row in data['platforms']:
        if row['key']=='REGISTERED-WHOLE-93154':
            old=row['key'];row['key']=old+'-V'+row['components'][0]['properties']['variant'];row['actualProjectionCaptionKey']=old;renamed.append({'oldCaptionKey':old,'newUniqueKey':row['key'],'sourceProjectionTileNumber':row['sourceProjectionTileNumber'],'sourceSheetSha256':data['sourceProjectionReview']['sheetSha256']})
    supplement=json.loads((DEST/'fullcube-ornament-supplement.json').read_text());projection=json.loads((DEST/'cube-base-decor-projection-evidence.json').read_text());assert sha(ROOT/projection['fileName'])==projection['sha256']
    roles={'CUBEDECOR-001':'Карнизный кронштейн · тело и кирпичное завершение','CUBEDECOR-002':'Каменная стойка · карнизный кронштейн и опорная секция','CUBEDECOR-027':'Узкое окно/пилястра · декоративный кронштейн и верхняя рама','CUBEDECOR-062':'Кровельный кронштейн · наклонная форма и каменное завершение'}
    existing={r['key'] for r in data['platforms']};appended=[]
    for row in supplement['reportOnlyCandidates']:
        if row['key'] not in roles or row['key'] in existing:continue
        if any(c['registryId'] in retired for c in row['components']):continue
        index=next(i+1 for i,r in enumerate(projection['rows']) if r['key']==row['key'])
        candidate={**row,'russianLabel':roles[row['key']],'kind':'SOURCE_VISIBLE_SUPPLEMENTAL_DECOR_CONSTRUCTION_PREVIEW','confidence':'ACTUAL_JOINED_SOURCE_PIXELS_SHOW_PLAUSIBLE_COMPLEMENTARY_DECOR_ROLES','galleryDisposition':'PREVIEW_ONLY_NOT_ITEM_OR_REGISTRY_MERGE','sourceProjectionManuallyViewed':True,'sourceProjectionTileNumber':index,'sourceProjectionSupplementFile':projection['fileName'],'sourceProjectionSupplementSha256':projection['sha256'],'nativeReview':'NOT_RUN','boundaryReason':'Actuallyviewedtexturedsourcejoint: sourcecorbel/cap/support/ornamentedwindowframecontinueswithoutgap. Finiteboundaryandfuturefunctionalmergerremainuserreview.'};data['platforms'].append(candidate);appended.append(candidate['key'])
    allkeys=[r['key'] for r in data['platforms']+doors['platforms']];assert len(allkeys)==len(set(allkeys))
    for row in data['platforms']+doors['platforms']:
        roots=[tuple(c['relativePosition']) for c in row['components']];assert len(roots)==len(set(roots)),row['key']
    data['platformCount']=len(data['platforms']);data['uniquePlatformKeys']='PASS';data['uniqueRelativeComponentRoots']='PASS';data['projectionCaptionKeyCorrections']=renamed;data['supplementalSourceProjectionReview']={'fileName':projection['fileName'],'sha256':projection['sha256'],'actuallyViewedProjectionTiles':len(projection['rows']),'selectedWholeDecorKeys':appended,'allSourceFinalPlatformsActuallyViewed':True,'notNativeGame':'No nativegamepixelsclaim; originalsourceprojectionbytesretained.'};data['styleRetirementSha256']=sha(BASE/'style-retirement-confirmed.json');dump(file,data)
    doors['uniquePlatformKeys']='PASS';doors['uniqueRelativeComponentRoots']='PASS';dump(doorfile,doors)
    # Only the index caption->key correspondence changes; pixel files unchanged.
    pi=DEST/'gallery-source-preview-projections.json';p=json.loads(pi.read_text())
    for row in p['rows']:
        if row['section']=='assemblies' and row['key']=='REGISTERED-WHOLE-93154':
            original=row['key'];match=next(r for r in renamed if r['sourceProjectionTileNumber']==row['number']);row['key']=match['newUniqueKey'];row['actualImageCaptionKey']=original
    p['captionKeyCorrections']=renamed;p['supplementalObservedProjection']=projection;dump(pi,p)
    dump(DEST/'final-key-and-supplement-validation.json',{'status':'PASS_SOURCE_INPUT_KEYS_AND_OBSERVED_SUPPLEMENT','finalAssemblyPlatforms':len(data['platforms']),'finalDoorPlatforms':len(doors['platforms']),'totalUniqueKeys':len(allkeys),'relativeComponentRootsUnique':True,'captionKeyCorrections':renamed,'originalProjectionPngBytesUnchanged':True,'supplementalAddedKeys':appended,'assemblyFileSha256':sha(file),'doorFileSha256':sha(doorfile),'supplementProjectionSha256':projection['sha256'],'nativeReview':'NOT_RUN'})

if __name__=='__main__':main()
