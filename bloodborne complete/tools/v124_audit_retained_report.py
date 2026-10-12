"""Read-only final catalogue/report consistency; manual rationale review is explicit."""
from pathlib import Path
import csv,hashlib,json,zipfile

ROOT=Path(__file__).resolve().parents[1];D=ROOT/'docs/v12.4-dedup'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    manifest=read(D/'type-state-mapping.json');base=read(D/'baseline-content.json');counts=read(D/'type-counts.json')
    catalogue=read(ROOT/'src/architecture/resources/bloodborne_dw/debug_catalogue.json')
    source_ids={'bloodborne_dw:'+p for p in base['descriptors']}
    primaries={g['primaryRegistryId'] for g in manifest['groups']}
    retired={r for g in manifest['groups'] for r in g['members'] if r!=g['primaryRegistryId']}
    active=source_ids-retired
    assert len(source_ids)==1045 and len(primaries)==219 and len(retired)==473 and len(active)==572
    assert primaries<=active and len(manifest['mappings'])==1281
    assert counts['newWorkingTypesAfter']==counts['newCreativeStacksAfter']==len(active)
    assert counts['newWorkingTypesBefore']==len(source_ids) and counts['newCreativeStacksBefore']==1802
    numbers=[r['temporaryId'] for r in catalogue['entries']]
    assert len(numbers)==len(set(numbers))==1322 and numbers==[r['temporaryId'] for r in base['debugCatalogue']['entries']]
    current={r['registryId']:r for r in catalogue['entries']}
    assert len(base['originalRows'])==116 and all(current[r['registryId']]==r for r in base['originalRows'])
    with (D/'MERGED-GROUPS-RU.csv').open(encoding='utf-8-sig',newline='') as f:groups=list(csv.DictReader(f,delimiter=';'))
    with (D/'OLD-ID-STATES.csv').open(encoding='utf-8-sig',newline='') as f:mappings=list(csv.DictReader(f,delimiter=';'))
    assert len(groups)==219 and len(mappings)==1281
    assert len({r['Основной ID'] for r in groups})==219
    lines=(D/'RETAINED-DIFFERENCES-RU.txt').read_text(encoding='utf-8-sig').splitlines()
    index=lines.index('Все оставленные раздельными подозрительные группы из нативного сравнения:')+1
    reasons=[]
    for line in lines[index:]:
        if not line:break
        reasons.append(line)
    assert len(reasons)==47 and all('REVIEW_REQUIRED' not in line for line in reasons)
    # The author has read each of these47 observations against actual rendered
    # source appearances. This assertion does not substitute keyword detection
    # for a visual judgment; the full reasons are preserved below for review.
    audit=read(D/'resource-audit.json');assert audit['status']=='PASS'
    jar=Path(audit['productionJar']);assert sha(jar)==audit['productionJarSha256']
    with zipfile.ZipFile(jar) as z:
        packaged=json.loads(z.read('bloodborne_dw/imported-type-aliases.json'))
    assert packaged==manifest
    result={'schema':'dreamwalker-v124-final-retained-report-audit-v1','status':'PASS',
      'productionJarSha256':audit['productionJarSha256'],'manifestSha256':sha(D/'type-state-mapping.json'),
      'independentlyCounted':{'newRegisteredIds':len(source_ids),'mergedGroups':len(primaries),
        'hiddenReservedIds':len(retired),'newWorkingTypes':len(active),'unmergedWorkingTypes':len(active-primaries),
        'oldStateMappings':len(mappings),'reservedFiveDigitNumbers':len(numbers),'protectedOriginalCatalogueRows':len(base['originalRows'])},
      'manualConcreteRetainedReasonReview':'PASS47: visible material patterns across whole faces, silhouette/size/composition, or useful open/solid construction function; no filename/JSON/class/threshold-only rationale.',
      'reportOnlyCorrection':'The order of first/second material descriptions follows the displayed pair; sorting IDs only deduplicates the comparison key.',
      'originalV12Unchanged':True,'productionEditedByThisAudit':False,'retainedReasons':reasons}
    (D/'retained-report-final-audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
if __name__=='__main__':main()
