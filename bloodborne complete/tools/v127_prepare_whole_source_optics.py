"""Derive a finite NEW-only carrier-shadow table from actual native evidence."""
import argparse,collections,json
from v127_visual_export import ROOT,RES,write_json,digest
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--run-name',default='tests12');args=parser.parse_args()
    source=ROOT/f'build/gametest-{args.run_name}/world/v127-source-lighting-native-proof.json';proof=json.loads(source.read_text(encoding='utf-8'))
    allowed={'bloodborne_dw:source_original_tree','bloodborne_dw:source_large_grille_gate'};groups={};occurrences=0
    differences=[r for r in proof['wholeConsumedHelperOptics'] if not r.get('opacityEqual',True)]
    for row in differences:
        assert row['nativeStatus'].startswith('PASS_NATIVE_SOURCE_AND_ACTUAL_HELPER_CLASS_OPTICS')
        target=row['targetState'];registry=target.split('[',1)[0];assert registry in allowed
        properties=dict(p.split('=',1) for p in target.split('[',1)[1].rstrip(']').split(','));assert properties['variant']=='0' and properties['open']=='false'
        pose=int(properties['variant'])+16*int(properties.get('art_bank',0));shift=row['payload']['SourceShift'];rotation=int(properties['rotation'])
        group=groups.setdefault(registry,{'registryId':registry,'variant':pose,'referenceRotation':rotation,'referenceSourceShift':shift,'carriers':[]})
        assert group['referenceSourceShift']==shift and group['referenceRotation']==rotation
        group['carriers'].append({'relativeCell':row['relativeCell'],'sourceState':row['sourceState'],'nativeOpacity':row['sourceOpacity'],'sourceOccurrences':row['occurrences'],'priorInstalledCellRole':row['expectedCellRole']})
        occurrences+=row['occurrences']
    assert set(groups)==allowed and sum(len(g['carriers']) for g in groups.values())==17 and occurrences==281
    document={'schemaVersion':1,'contextMarker':'SourceCarrierContextVersion=1','policy':'Only flagged converted NEW whole objects own the exact measured consumed source-carrier cells. Empty physical/selection footprints; shared native opacity rules; original V12 and normal hand placement unchanged.',
        'sourceHashes':proof['sourceHashes'],'nativeEvidenceRun':args.run_name,'nativeEvidenceSha256':digest(source.read_bytes()),'observedSourceCellOccurrences':occurrences,'objects':[groups[k] for k in sorted(groups)]}
    write_json(RES/'bloodborne_dw/imported-whole-source-optics-v127.json',document)
    write_json(ROOT/'docs/v12.7/conversion/whole-source-carrier-optics-assessment.json',{'status':'PASS_NATIVE_MEASURED_NEW_CARRIER_SHADOW_COHORT_IMPLEMENTATION_AWAITS_NEXT_NATIVE_RUN','nativeEvidenceRun':args.run_name,'coordinateRunSha256':proof['coordinateRunSha256'],'sourceHashes':proof['sourceHashes'],'table':document,
        'measuredDifferenceCells':occurrences,'treePriorAirOutsideFootprint':272,'grillePriorCompositeHelpers':9,'otherConsumedHelperCellsUnchanged':proof['wholeConsumedNonrootOccurrencesAudited']-occurrences,
        'shadowLookup':'One scalar computed on immutable snapshot publication; lighting workers read the scalar without NBT decoding, IO, world/chunk loading or new cache entries.',
        'transformPolicy':'Actual source cardinal rotations and SourceShift reproduce exact cells. Later transforms rotate carrier centres and move one shadow cell per carrier; physical/selection boxes are unchanged. Additional cells are normal empty guest memberships, removed with their owner.',
        'newRuntimeStatus':'NOT_RUN'})
    print(json.dumps({'status':'PASS_FINITE_NATIVE_TABLE_PREPARED','rules':len(groups),'offsets':17,'sourceOccurrences':occurrences}))
if __name__=='__main__':main()
