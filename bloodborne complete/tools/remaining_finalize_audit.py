"""Apply reviewed visible-source exclusions and label corrections to new content only.

The original V12 entries are protected. Provisional numbers remain reserved in
debug_catalogue.json, whose complete used-number set drives future allocation.
"""
import hashlib,json,traceback
from collections import Counter
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'docs/remaining-transfer'
RES=ROOT/'src/architecture/resources';META=RES/'bloodborne_dw'
REMOVED={'source_glass','source_ladder','source_assembly_glass_pane'}
NAMES={
 'source_addon_button_9':'Напольная декоративная пластина · вариант 10',
 'source_addon_button_10':'Напольная декоративная пластина · вариант 11',
 'source_addon_button_11':'Напольная декоративная пластина · вариант 12',
 'source_addon_button_12':'Напольная декоративная пластина · вариант 13',
 'source_addon_button_13':'Напольная декоративная пластина · вариант 14',
 'source_buttons_spruce_spruce_button_powered':'Напольный орнамент с наклонными плоскостями',
 'source_buttons_spruce_spruce_button_powered_2':'Объёмное украшение с прямоугольным основанием · вариант 3',
 'source_buttons_spruce_spruce_button_powered_3':'Низкое объёмное украшение · вариант 4',
 'source_assembly_brown_mushroom_block':'Крупная составная декоративная опора',
 'source_assembly_dark_oak_fence':'Декоративная ограда с парными опорами',
 'source_assembly_mossy_stone_brick_wall':'Гаргулья · водосток',
 'source_assembly_mushroom_stem':'Декоративное основание с наклонным навершием',
 'source_assembly_red_mushroom_block':'Составная декоративная опора',
 'source_assembly_red_nether_brick_wall':'Декоративная решётка с фигурной стойкой',
 'source_assembly_red_sandstone_wall':'Декоративная стена с карнизом',
 'source_assembly_sandstone_wall':'Декоративная решётка с тонкой стойкой',
}
def read(path):return json.loads(path.read_text(encoding='utf-8'))
def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def rename_fields(value,old,new):
    if isinstance(value,dict):
        for key,item in value.items():
            if key in ('displayName','name','sourceVariant') and isinstance(item,str) and item.startswith(old):value[key]=new+item[len(old):]
            elif isinstance(item,(dict,list)):rename_fields(item,old,new)
    elif isinstance(value,list):
        for item in value:rename_fields(item,old,new)
def main():
    manifest=read(OUT/'source-candidates.json');catalogue=read(META/'remaining-catalogue.json')
    imported=read(OUT/'imported-resources.json');types=read(OUT/'new-types.json');debug=read(META/'debug_catalogue.json')
    alpha=read(OUT/'source-alpha-audit.json'); assert alpha['status']=='PASS_READ_ONLY'
    blank={row['model']:row for row in alpha['modelsWithOnlyTransparentSampledFaces']}
    evidence={row['texture']:row for row in alpha['decodedTextureEvidence']}
    removed=[obj for obj in manifest['objects'] if obj['path'] in REMOVED]
    prior=OUT/'source-transparent-exclusions.json'
    if removed:
        assert len(removed)==3 and sum(len(obj['variants']) for obj in removed)==9
        nums={row['registryId']:row['temporaryId'] for row in debug['entries']}
        excluded=[]
        for obj in removed:
            for variant in obj['variants']:
                proofs=[]
                for app in variant['closed']:
                    assert app['model'] in blank,app['model']
                    for face in blank[app['model']]['faceSamples']:
                        texture=evidence[face['texture']]
                        assert texture['wholeImageAlphaExtrema']==[0,0],texture
                        proofs.append(texture)
                excluded.append({'registryId':'bloodborne_dw:'+obj['path'],'reservedNumber':nums['bloodborne_dw:'+obj['path']],
                                 'variant':variant['variant'],'sourceModels':variant['sourceModels'],'classification':'SOURCE_TRANSPARENT_HELPER',
                                 'status':'EXCLUDED_VISIBLE_ARTWORK_EMPTY','textures':list({proof['entry']:proof for proof in proofs}.values()),
                                 'evidence':'docs/remaining-transfer/source-alpha-audit.json'})
        exclusion={'reason':'All pixels of every visible-face source PNG have alpha 0; these source carriers provide invisible service geometry, not finished visible building artwork.',
                   'sourcePackSha256':manifest['packSha256'],'objects':3,'states':9,'entries':excluded,
                   'numberPolicy':'Allocated numbers remain reserved in debug_catalogue.json and are never reused.'}
        write(prior,exclusion)
    else:
        exclusion=read(prior);assert exclusion['objects']==3 and exclusion['states']==9
    reserved={entry['registryId']:entry['reservedNumber'] for entry in exclusion['entries']}
    registry_removed={'bloodborne_dw:'+path for path in REMOVED}
    name_log=[]
    for artwork in catalogue['objects']:
        if artwork['path'] not in NAMES:continue
        old=artwork['displayName'];new=NAMES[artwork['path']]
        name_log.append({'path':artwork['path'],'previousName':old,'displayName':new,'reason':'Source resolved element geometry; avoids stale placeholder source catalogue label.'})
        rename_fields(artwork,old,new)
        descriptor=META/'composite'/(artwork['path']+'.json')
        data=read(descriptor);rename_fields(data,old,new);write(descriptor,data)
        for entry in types['entries']:
            if entry['registryId']=='bloodborne_dw:'+artwork['path']:rename_fields(entry,old,new)
        for entry in imported['objects']:
            if entry['path']==artwork['path']:rename_fields(entry,old,new)
        for entry in debug['entries']:
            if entry['registryId']=='bloodborne_dw:'+artwork['path']:rename_fields(entry,old,new)
    write(OUT/'source-name-overrides.json',{'schemaVersion':1,'objects':name_log,'policy':'Only unpublished new source_ labels; geometry, source manifest labels and V12 names preserved.'})
    manifest['objects']=[obj for obj in manifest['objects'] if obj['path'] not in REMOVED]
    for entry in manifest['modelClassifications']:
        if entry['sourceModel'] not in blank:continue
        entry['alphaEvidence']='docs/remaining-transfer/source-alpha-audit.json'
        if entry.get('registryId','') in registry_removed or entry.get('wholeObjectPaths') and set(entry['wholeObjectPaths'])<=REMOVED:
            entry.update(kind='SOURCE_TRANSPARENT_HELPER',status='EXCLUDED_SOURCE_TRANSPARENT_HELPER',missing='Every visible-face source texture is fully transparent (PNG alpha 0).',
                         reservedNumber=reserved.get(entry.get('registryId',''),''),registryId='',numericId='')
            entry['wholeObjectPaths']=[]
        else:
            entry['kind']='SOURCE_TRANSPARENT_SERVICE_PART'
            entry['missing']='Invisible source component retained only inside its visible whole-object dependency.'
    manifest['sourceTransparentExclusionEvidence']='docs/remaining-transfer/source-transparent-exclusions.json'
    manifest['summary'].update(objects=len(manifest['objects']),offeredStates=sum(len(obj['variants']) for obj in manifest['objects']),
                               sourceTransparentExcludedObjects=3,sourceTransparentExcludedStates=9,
                               classificationCounts=dict(Counter(row['kind'] for row in manifest['modelClassifications'])),
                               statusCounts=dict(Counter(row['status'] for row in manifest['modelClassifications'])))
    assert manifest['summary']['objects']==1047 and manifest['summary']['offeredStates']==1805
    write(OUT/'source-candidates.json',manifest);write(OUT/'plan-summary.json',manifest['summary'])
    catalogue['objects']=[obj for obj in catalogue['objects'] if obj['path'] not in REMOVED];write(META/'remaining-catalogue.json',catalogue)
    types['entries']=[entry for entry in types['entries'] if entry['registryId'] not in registry_removed]
    types['reservedTransparentHelperNumbers']=reserved;write(OUT/'new-types.json',types)
    for entry in debug['entries']:
        if entry['registryId'] not in registry_removed:continue
        assert entry['temporaryId']==reserved[entry['registryId']]
        entry.update(kind='reserved_source_transparent_helper',name='Резерв · прозрачная служебная геометрия',aliases=[],offered=False,
                     exclusionEvidence='docs/remaining-transfer/source-transparent-exclusions.json')
    write(META/'debug_catalogue.json',debug)
    imported['objects']=[obj for obj in imported['objects'] if obj['path'] not in REMOVED]
    imported['inputManifestSha256']=sha(OUT/'source-candidates.json')
    imported['summary'].update(imported=1047,variants=1805,sourceTransparentExcludedObjects=3,sourceTransparentExcludedStates=9)
    imported['unofferedTransparentHelperArtifacts']=[{'path':path,'descriptor':'bloodborne_dw/composite/'+path+'.json','reason':'SOURCE_TRANSPARENT_HELPER'} for path in sorted(REMOVED)]
    for entry in imported['resources']:
        path=RES/entry['file'];assert path.is_file(),path
        entry['sha256']=sha(path)
    write(OUT/'imported-resources.json',imported)
    rows=read(OUT/'state-correspondence.json');affected=0
    for row in rows:
        before=row['target'].split(';') if row['target'] else []
        deleted=[target for target in before if target.split('[',1)[0] in registry_removed]
        if not deleted:continue
        row['target']=';'.join(target for target in before if target not in deleted)
        if not row['target']:
            affected+=1;row.update(numericId=';'.join(sorted({reserved[target.split('[',1)[0]] for target in deleted})),
                classification='SOURCE_TRANSPARENT_HELPER',status='EXCLUDED_SOURCE_TRANSPARENT_HELPER',resourceCheck='PASS_SOURCE_ALPHA_0',
                missing='All source visible-face PNG alpha pixels are 0; exact authored invisible helper explicitly unoffered; number reserved.',
                evidence='source_alpha:docs/remaining-transfer/source-alpha-audit.json')
    write(OUT/'state-correspondence.json',rows)
    write(OUT/'final-audit-adjustment.json',{'status':'PASS','objects':1047,'states':1805,'excludedOfferedStates':9,
             'sourceApplicationRowsExplicitlyExcluded':affected,'nameCorrections':len(name_log),'reservedNumbers':reserved,
             'manifestSha256':imported['inputManifestSha256'],'alphaEvidenceSha256':sha(OUT/'source-alpha-audit.json')})
if __name__=='__main__':
    try:main()
    except Exception:
        write(OUT/'final-audit-adjustment.json',{'status':'FAIL','traceback':traceback.format_exc()});raise
