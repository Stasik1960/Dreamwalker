"""Extract real native GameTest geometry/CPU metrics, verify data provenance.

Consumes the single DW_COMPOSITE_V8_GEOMETRY_METRICS log row and optional actual
GameTest XML. No synthetic PASS, runtime launch, descriptor mutation or FPS claim.
"""
from pathlib import Path
import argparse
import hashlib
import json
import statistics
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
PREFIX='DW_COMPOSITE_V8_GEOMETRY_METRICS='

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def path_ref(path):
    try:return str(path.resolve().relative_to(ROOT)).replace('\\','/')
    except ValueError:return str(path.resolve())
def summary(cases,side):
    fields=('authoredPhysicalBoxes','authoredSelectionBoxes','distributedPhysicalBoxes','distributedSelectionBoxes','nativePhysicalBoxesTotal','nativeSelectionBoxesTotal','nativePhysicalBoxesMaxPerCell','nativeSelectionBoxesMaxPerCell','physicalCells','selectionCells','footprintCellsIncludingRoot','helperCandidateCells')
    return {field:{'min':min(row[side][field] for row in cases),'max':max(row[side][field] for row in cases),'sumAcrossDistinctEffectiveCases':sum(row[side][field] for row in cases)} for field in fields}
def measurement_analysis(native,benchmarks,tree_descriptor):
    """Observed population counters, separated from causal/performance inference."""
    families=[];all_cells=[]
    for family in native['families']:
        cells=[cell for case in family['distinctEffectiveGeometryCases'] for cell in case['pairedCellMetrics']]
        all_cells.extend(cells)
        rows={'id':family['id'],'pairedCellRows':len(cells)}
        for kind in ('Collision','Selection'):
            field='native'+kind+'Boxes'
            rows[kind.lower()]={
                'nonemptyBefore':sum(cell['before'][field]>0 for cell in cells),
                'nonemptyAfter':sum(cell['after'][field]>0 for cell in cells),
                'beforeEmptyAfterNonempty':sum(cell['before'][field]==0<cell['after'][field] for cell in cells),
                'beforeNonemptyAfterEmpty':sum(cell['after'][field]==0<cell['before'][field] for cell in cells),
                'distributedInputBoxesBefore':sum(cell['before']['distributed'+kind+'Boxes'] for cell in cells),
                'distributedInputBoxesAfter':sum(cell['after']['distributed'+kind+'Boxes'] for cell in cells)}
        families.append(rows)
    population=len(all_cells)
    assert population==benchmarks['selection']['pairedCellQueryPopulation']
    iterations=benchmarks['selection']['iterationsPerRound']
    # Reproduce the actual loop order, including its incomplete final cycle.
    calls={side:sum(all_cells[i%population][side]['nativeSelectionBoxes']>0 for i in range(iterations)) for side in ('before','after')}
    tree=next(row for row in families if row['id']=='bloodborne_dw:prototype_tree')
    tree_native=next(row for row in native['families'] if row['id']=='bloodborne_dw:prototype_tree')
    after_selection=tree_descriptor['variants'][0]['poses']['closed']['selection']
    assert all(variant['poses']['closed']['selection']==after_selection for variant in tree_descriptor['variants'])
    round_comparisons={}
    for kind,benchmark in benchmarks.items():
        before,after=benchmark['beforeNanos'],benchmark['afterNanos']
        round_comparisons[kind]={'afterFasterRounds':sum(a<b for b,a in zip(before,after)),
            'afterSlowerRounds':sum(a>b for b,a in zip(before,after)),'equalRounds':sum(a==b for b,a in zip(before,after)),
            'beforeRangeNanos':[min(before),max(before)],'afterRangeNanos':[min(after),max(after)],
            'beforeMaxOverMin':max(before)/min(before),'afterMaxOverMin':max(after)/min(after)}
    after_box_count=len(after_selection)
    narrow_lower=after_box_count==4 and sorted({(box['from'][1],box['to'][1])for box in after_selection})==[(0,96),(96,288)]
    observations=[]
    for kind in ('collision','selection'):
        delta=100*(benchmarks[kind]['afterOverBeforeMedian']-1);rounds=round_comparisons[kind]
        direction='increased'if delta>0 else'decreased'if delta<0 else'was unchanged'
        observations.append(f'{kind.capitalize()} median {direction} by{abs(delta):.1f}%; after faster/slower/equal rounds: {rounds["afterFasterRounds"]}/{rounds["afterSlowerRounds"]}/{rounds["equalRounds"]}.')
        if max(rounds['beforeMaxOverMin'],rounds['afterMaxOverMin'])>1.5:
            observations.append(f'{kind.capitalize()} round ranges vary by more than1.5×; JIT/runtime drift was not isolated. The median is not steady-state speedup certification.')
    before_max=max(case['before']['helperCandidateCells']for case in tree_native['distinctEffectiveGeometryCases'])
    after_max=max(case['after']['helperCandidateCells']for case in tree_native['distinctEffectiveGeometryCases'])
    return {
        'observedFamilyQueryPopulations':families,
        'pairedCellPopulation':population,
        'treeFractionOfPopulation':tree['pairedCellRows']/population,
        'selectionNonemptyCallsPerActualRound':calls,
        'selectionMedianChangePercent':100*(benchmarks['selection']['afterOverBeforeMedian']-1),
        'collisionMedianChangePercent':100*(benchmarks['collision']['afterOverBeforeMedian']-1),
        'timingRoundComparisons':round_comparisons,
        'timingObservations':observations,
        'sourceBackedGeometryComparison':{
            'family':'bloodborne_dw:prototype_tree',
            'beforeAuthoredSelectionBoxes':sorted({case['before']['authoredSelectionBoxes']for case in tree_native['distinctEffectiveGeometryCases']}),
            'afterAuthoredSelectionBoxes':after_box_count,
            'afterSelectionBoundsUnits':after_selection,
            'narrowLowerBroadUpperCorrectionApplied':narrow_lower,
            'helperCandidatesMaxBefore':before_max,'helperCandidatesMaxAfter':after_max,
            'beforeLowerStemExtentPixels':{'x':[-16,32],'y':[0,96],'z':[-16,32]},
            'additionalNonemptyPairedTreeCells':tree['selection']['beforeEmptyAfterNonempty'],
            'inference':('Four planes restrict lower0..6-block selection to3-block width while keeping upper6..18-block crown width9blocks. Coarse upper distribution still adds occupied cells versus v7. Fewer boxes and more nonempty calls have competing costs; the observed aggregate timing direction is reported separately, not assigned to one unmeasured cause.'if narrow_lower else'Two full-height broad planes add occupied selection cells. Former empty-list/empty-shape fast paths become cached nonempty GridShape queries. This is a source-backed workload change, not an isolated causal timing proof.'),
            'listHashCost':'Actual CompositeShapes.of hashes nonempty input lists for cache lookup. Aggregate distributed selection input count decreases, so longer lists are not supported as the main cause; empty calls bypass that lookup entirely.'},
        'recommendedBoundedFollowUp':(['Narrow-lower/four-plane correction is already applied. Current native geometry metrics reflect that actual descriptor; no further production geometry correction is proposed by this parser.']if narrow_lower else['Historical two-plane outline was subsequently corrected to narrow lower/wide upper four planes; this historical timing result is retained.'])+[
            'For a later requested performance investigation, measure per-family cache and actual raycast queries alongside this proxy. Preserve all raw timings; do not infer FPS or compare independent server runs as a controlled within-process experiment.'],
        'productionChangeMadeByThisAnalysis':False,
        'benchmarkMeaning':'The operator is cache lookup plus calculateMaxOffset over collision or selection shapes. It is not an actual selection-raycast, world traversal, FPS or city benchmark. Its cell weighting is not source-world object frequency.',
        'acceptanceScope':'Current user review accepts wooden window panels and the volumetric RP tree; this flat composite tree remains under correction. Native PASS is not acceptance of the whole first set.'}
def main():
    p=argparse.ArgumentParser();p.add_argument('--log',required=True,type=Path);p.add_argument('--xml',type=Path)
    p.add_argument('--output',type=Path,default=ROOT/'reports/COMPOSITE_V8_GEOMETRY_METRICS.json')
    p.add_argument('--markdown',type=Path,default=ROOT/'reports/COMPOSITE_V8_GEOMETRY_METRICS.md')
    p.add_argument('--tree-descriptor',type=Path,help='Explicit byte-verified historical tree descriptor; other descriptors must still match the observed log.')
    a=p.parse_args();text=a.log.read_text(encoding='utf-8-sig',errors='replace')
    matches=[line.split(PREFIX,1)[1].strip() for line in text.splitlines() if PREFIX in line]
    if len(matches)!=1:raise SystemExit(f'Expected1 native metrics row, observed{len(matches)}; no report PASS synthesized')
    native=json.loads(matches[0]);assert native['status']=='PASS_NATIVE_GEOMETRY_COUNTS_AND_IDENTICAL_QUERY_CONDITIONS'
    baseline_path=ROOT/'reports/user-review-v7/baseline/metrics.json';baseline=json.loads(baseline_path.read_text(encoding='utf-8'))
    assert native['beforeArtifactSha256']==baseline['jar_sha256']
    old_counts={row['id']:row['registered_states'] for row in baseline['objects']}
    evidence={'log':path_ref(a.log),'logSha256':sha(a.log),'baselineMetrics':path_ref(baseline_path),'baselineMetricsSha256':sha(baseline_path),
      'legacyJava': 'src/gametest/java/dev/dreamwalker/bloodbornedw/composite/ReviewLegacyCompositeSpec.java','legacyJavaSha256':sha(ROOT/'src/gametest/java/dev/dreamwalker/bloodbornedw/composite/ReviewLegacyCompositeSpec.java'),
      'currentDistributorJavaSha256':sha(ROOT/'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeSpec.java'),
      'sameNativeShapeBuilderJavaSha256':sha(ROOT/'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeShapes.java'),
      'nativeMetricsTestJavaSha256':sha(ROOT/'src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/CompositeGeometryMetricsGameTests.java')}
    xml_verified=False
    if a.xml:
        xml=ET.parse(a.xml).getroot();rows=[r for r in xml.iter('testcase') if 'comparelegacyandcurrentgeometryandwarmnativequeries' in r.get('name','').lower()]
        assert len(rows)==1 and rows[0].find('failure') is None and rows[0].find('error') is None,'Actual native metrics testcase must be present and passed'
        evidence.update({'xml':path_ref(a.xml),'xmlSha256':sha(a.xml),'nativeTestcase':dict(rows[0].attrib)});xml_verified=True
    families=[];tree_descriptor=None
    for row in native['families']:
        path=row['id'].split(':',1)[1];old=ROOT/'reports/user-review-v7/baseline'/f'{path}.json';new=ROOT/'src/architecture/resources/bloodborne_dw/composite'/f'{path}.json';resource=ROOT/'src/gametest/resources/bloodborne_dw/review-v7-baseline'/f'{path}.json'
        if path=='prototype_tree' and a.tree_descriptor:new=a.tree_descriptor
        assert row['legacyDescriptorSha256']==sha(old)==sha(resource),f'Legacy descriptor byte provenance mismatch:{path}'
        assert row['currentDescriptorSha256']==sha(new),f'Current native descriptor differs from reviewed source:{path}'
        if path=='prototype_tree':tree_descriptor=json.loads(new.read_text(encoding='utf-8-sig'))
        assert row['registeredStatesBefore']==old_counts[row['id']]
        assert len(row['stateCases'])==256
        fields={'id':row['id'],'registeredStatesBefore':row['registeredStatesBefore'],'registeredStatesAfter':row['registeredStatesAfter'],
          'declaredVariantsBefore':row['descriptorVariantsBefore'],'declaredVariantsAfter':row['descriptorVariantsAfter'],'canonicalStateGeometryCases':len(row['stateCases']),
          'distinctEffectiveGeometryCases':len(row['distinctEffectiveGeometryCases']),'before':summary(row['distinctEffectiveGeometryCases'],'before'),'after':summary(row['distinctEffectiveGeometryCases'],'after'),
          'beforeDescriptor':path_ref(old),'beforeDescriptorSha256':sha(old),'afterDescriptor':path_ref(new),'afterDescriptorSha256':sha(new)}
        families.append(fields)
    assert len(families)==5 and native['canonicalStateGeometryCases']==1280
    benchmarks={}
    for kind,row in native['queryBenchmark'].items():
        assert len(row['beforeNanos'])==len(row['afterNanos'])==row['measuredRounds']==7 and row['warmupRounds']==5
        assert row['sameProcessAndInputs'] and row['ordering']=='alternating before/after'
        before=statistics.median(row['beforeNanos']);after=statistics.median(row['afterNanos'])
        benchmarks[kind]={**row,'beforeMedianNanos':before,'afterMedianNanos':after,'afterOverBeforeMedian':after/before,'rawTimingsPreserved':True}
    report={'schema':'dreamwalker-composite-native-before-after-reviewed-metrics-v1','status':'PASS_ACTUAL_NATIVE_GAMETEST_GEOMETRY_AND_MICROBENCH' if xml_verified else 'OBSERVED_NATIVE_OUTPUT_XML_VERIFICATION_PENDING',
      'evidence':evidence,'canonicalPose':native['canonicalPose'],'familySummaries':families,'otherArchitectureStateCounts':native['otherArchitectureStateCounts'],
      'canonicalStateGeometryCases':native['canonicalStateGeometryCases'],'helperCandidatePolicy':native['helperCandidatePolicy'],'queryBenchmark':benchmarks,
      'nativeOutput':native,'measurementAnalysis':measurement_analysis(native,benchmarks,tree_descriptor),'limitations':native['limitations'],'wholeCityPerformanceClaim':False,'manualVisualAcceptanceClaim':False,
      'historicalDescriptorOverride':path_ref(a.tree_descriptor)if a.tree_descriptor else None}
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    lines=['# Native composite geometry metrics', '', f'Status: `{report["status"]}`. Actual1280 canonical geometry state cases; newhorizontal mounts are excluded. Helper candidates are not actual owned helpers.', '',
      '| Family | States before →after | Physical input max | Selection input max | Native physical max total | Native selection max total | Helper candidates max |', '|---|---:|---:|---:|---:|---:|---:|']
    for row in families:
        b,n=row['before'],row['after'];pair=lambda key:f'{b[key]["max"]} →{n[key]["max"]}'
        lines.append(f'| {row["id"].split(":",1)[1]} | {row["registeredStatesBefore"]} →{row["registeredStatesAfter"]} | {pair("authoredPhysicalBoxes")} | {pair("authoredSelectionBoxes")} | {pair("nativePhysicalBoxesTotal")} | {pair("nativeSelectionBoxesTotal")} | {pair("helperCandidateCells")} |')
    lines+=['', 'Input counts describe authoring boxes. Native totals sum actual decomposed `VoxelShape.getBoundingBoxes()` over the object cells after the sameproduction16³ builder. Fullcell distributions and percell maxima remain inJSON.', '', '| Query | Paired cell population | Iterations /round | Median CPU before →after | After /before |', '|---|---:|---:|---:|---:|']
    for kind,row in benchmarks.items():lines.append(f'| {kind} | {row["pairedCellQueryPopulation"]} | {row["iterationsPerRound"]} | {row["beforeMedianNanos"]/1e6:.3f}ms →{row["afterMedianNanos"]/1e6:.3f}ms | {row["afterOverBeforeMedian"]:.3f} |')
    analysis=report['measurementAnalysis']
    sel_delta=analysis['selectionMedianChangePercent'];sel_direction='increased'if sel_delta>0 else'decreased'if sel_delta<0 else'was unchanged'
    sel_rounds=analysis['timingRoundComparisons']['selection']
    lines+=['', 'The same native cachelookup, local mover, cellpopulation, axis andoffset run in one serverprocess after5 warmups; seven measuredrounds alternate order. CPU-only microbenchmark: noFPS,heap,TPS,city/worldrender ormanual gameplay acceptance claim.', '',
      f'Selection-shape query median **{sel_direction} {abs(sel_delta):.1f}%**. After faster/slower/equal rounds: {sel_rounds["afterFasterRounds"]}/{sel_rounds["afterSlowerRounds"]}/{sel_rounds["equalRounds"]}. These queries use `calculateMaxOffset` on selection shapes, not actual selection raycasts. Results establish this bounded CPU observation, not steady-state or whole-world speedup.', '',
      '| Family | Paired cell rows | Selection nonempty before →after | Old empty →new nonempty | Distributed selection inputs before →after |', '|---|---:|---:|---:|---:|']
    for row in analysis['observedFamilyQueryPopulations']:
        sel=row['selection'];lines.append(f'| {row["id"].split(":",1)[1]} | {row["pairedCellRows"]} | {sel["nonemptyBefore"]} →{sel["nonemptyAfter"]} | {sel["beforeEmptyAfterNonempty"]} | {sel["distributedInputBoxesBefore"]} →{sel["distributedInputBoxesAfter"]} |')
    geometry=analysis['sourceBackedGeometryComparison']
    lines+=['', f'Tree cells form {analysis["treeFractionOfPopulation"]:.1%} of this geometry-weighted population. Current measured tree outline uses{geometry["afterAuthoredSelectionBoxes"]} boxes and adds{geometry["additionalNonemptyPairedTreeCells"]} nonempty paired cells relative to v7; maximum helper candidates {geometry["helperCandidatesMaxBefore"]} →{geometry["helperCandidatesMaxAfter"]}. {geometry["inference"]}', '',
      *analysis['timingObservations'],'', *analysis['recommendedBoundedFollowUp'],'',
      'The current user review accepts wooden window panels and the volumetric RP tree. The flat composite tree measured here remains under correction; native test PASS is not whole-set acceptance.', '', f'Raw actual data: `{path_ref(a.output)}`; log `{path_ref(a.log)}`.']
    a.markdown.parent.mkdir(parents=True,exist_ok=True);a.markdown.write_text('\n'.join(lines)+'\n',encoding='utf-8')
    print(json.dumps({'status':report['status'],'canonicalStateGeometryCases':1280,'familyMaxPhysicalInput':{row['id']: [row['before']['authoredPhysicalBoxes']['max'],row['after']['authoredPhysicalBoxes']['max']] for row in families},'benchmarkMediansMs':{kind:[row['beforeMedianNanos']/1e6,row['afterMedianNanos']/1e6] for kind,row in benchmarks.items()}},ensure_ascii=False))
if __name__=='__main__':main()
