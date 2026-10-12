"""Independent final art handoff, protected IDs and exact alias-copy audit."""
import hashlib,json,subprocess,zipfile
from v127_visual_export import ROOT,RES,OUT,inventory,spec_of,write_json,digest

BASE='0c985dc28e4a38900cce3055ddb5658a71ed95e5'
def baseline(relative):
    return subprocess.run(['git','show',BASE+':bloodborne complete/'+relative],cwd=ROOT.parent,check=True,capture_output=True).stdout

def main():
    report=json.loads((OUT/'blockbench-export.json').read_text(encoding='utf-8'));archive=ROOT/'build/Dreamwalker-BB-V12.7-New-Blockbench-Assets.zip'
    assert digest(archive.read_bytes())==report['archiveSha256']
    rows=inventory();expected={r['registry']:r['id'] for r in rows}
    marker=json.loads((ROOT/'build/v126-gallery-input.json').read_text(encoding='utf-8'))
    protected={r['registry'] for r in marker['catalogue']['exhibits'] if r['origin']=='V12'}
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None;index=json.loads(z.read('INDEX.json'));objects=index['objects']
        actual={r['registryId']:r['id'] for r in objects};assert actual==expected and not protected.intersection(actual)
        utilities={r['registryId']:r['id'] for r in index['utilityObjects']};assert utilities=={'bloodborne_dw:dw_barrier':'93230','bloodborne_dw:dw_light':'93231'}
        assert len(set(actual.values())|set(utilities.values()))==len(objects)+2
        for row in objects:
            live=spec_of({'registry':row['registryId']});saved=json.loads(z.read(row['descriptor']));assert saved==live
            assert row['name']==live['displayName']
        assert len(z.infolist())==len({r.filename for r in z.infolist()}),'Duplicate archive paths'
        assert 'runtimeALTVertexMultiplierPreview' in z.read('README-RU.txt').decode()
    old=json.loads(baseline('src/architecture/resources/bloodborne_dw/imported-type-aliases.json'))
    current=json.loads((RES/'bloodborne_dw/imported-type-aliases.json').read_text(encoding='utf-8'))
    assert len(old['mappings'])==1281 and old['mappings']==current['mappings'],'Historic raw selector transforms changed'
    members=[m for g in current['groups'] for m in g['members']];assert len(members)==len(set(members))
    old_debug=json.loads(baseline('src/architecture/resources/bloodborne_dw/debug_catalogue.json'))
    debug=json.loads((RES/'bloodborne_dw/debug_catalogue.json').read_text(encoding='utf-8'))
    old_numbers={r['temporaryId']:r['registryId'] for r in old_debug['entries']};numbers={r['temporaryId']:r['registryId'] for r in debug['entries']}
    assert len(numbers)==len(debug['entries']) and all(numbers.get(n)==registry for n,registry in old_numbers.items()),'Reserved ID removed/reused'
    aliases=json.loads((RES/'bloodborne_dw/imported-type-aliases-v127.json').read_text(encoding='utf-8'))
    proofs=json.loads((OUT/'practical-dedup.json').read_text(encoding='utf-8'))['stateVertexUVTextureCopies']
    for row in proofs:
        a=next(r for r in aliases['mappings'] if r['oldId']==row['oldId'] and r['oldVariant']==row['oldPose'])
        source=spec_of({'registry':a['oldRegistryId']})['variants'][a['oldVariant']]
        target=spec_of({'registry':a['newRegistryId']})['variants'][a['newVariant']]
        assert source==target
        assert digest(json.dumps(target,sort_keys=True,separators=(',',':')).encode())==row['exactCurrentVariantSha256']
    result={'status':'PASS_FINAL_VISUAL_DELIVERY_AUDIT','baselineCommit':BASE,'archiveSha256':report['archiveSha256'],'archiveBytes':archive.stat().st_size,
        'workingNewTypes':len(objects)+2,'compositeWorkingNewTypes':len(objects),'utilityWorkingNewTypes':2,
        'originalV12TypeExports':0,'historic1281MappingsExact':True,'globalOldGroupMemberUniqueness':True,'previousReservedNumbersUnchanged':len(old_numbers),
        'followupCurrentExactPoseCopies':len(proofs),'archivedRuntimeDescriptorsMatchFinalLiveResources':True,'blockbenchGUIOpen':'NOT_RUN','nativeClientAndServer':'SEE_ROOT_REPORT'}
    write_json(OUT/'final-delivery-audit.json',result);print(json.dumps(result))

if __name__=='__main__':main()
