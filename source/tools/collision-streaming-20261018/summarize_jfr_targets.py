"""Compare bounded, phase-targeted JFR diagnostics of the one matched route."""
import hashlib,json,traceback
from pathlib import Path
P=Path(__file__).absolute().parents[2];E=P.parent/'deliverables/collision-streaming-20261018/evidence';J=E/'jfr'
TARGETS=['CompositeOwnerNbt.read','CompositeOwnerNbt.packedBytes','CompositeData.savedContributions','DecodePool.savedContributions','DecodePool.contributions','DecodePool.contribution','CompositeData.boxes','CompositeData.nbt','CompositeData.bytes','ImportedPhysicalShapes.exact','ImportedPhysicalShapes.buildExact','ImportedPhysicalShapes.*','ShardedCompositeLedger.read','VanillaChunkBuilder.*','CompositeWorldMesh.*','CompositeTerrain.*','VanillaNbtIo.*']
def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    route=read(E/'route-comparison.json');assert route['status']=='PASS_ONE_MATCHED_NATIVE_ROUTE'
    results={};source_sha=set()
    for case in ['shapes-before','shapes-after']:
        run=route['runs'][case];folder=J/case;analysis=read(folder/'target-analysis.json');provenance=read(folder/'phase-provenance.json');receipt=read(folder/'receipt.json');assert receipt['status']=='PASS' and all(r['recordingCoversInterval'] for r in analysis['coverage']);source_sha.add(receipt['analyzerSourceSha256'])
        delivered=run['deliveredChunks']['loadCallbacks'];assert delivered==sum(r['actualDeliveredChunks'] for r in analysis['coverage'] if r['phase'] in ['tower','brick'])
        groups={}
        for window in ['TRAVEL_TOTAL','TRAVEL_TOTAL_CORE_500MS']:
            output={}
            for group in ['ALL','Server thread','Render thread','Worker-Main-*','Netty-*']:
                stats=analysis['phaseThreads'].get(window+'|'+group)
                if stats is None:continue
                targets={}
                for target in TARGETS:
                    v=stats['targets'][target]
                    targets[target]={'executionSamplePresence':v['executionSamplePresence'],'executionSamplePercent':100*v['executionSamplePresence']/stats['executionSamples'] if stats['executionSamples'] else None,'allocationSampleEvents':v['allocationSampleEvents'],'inclusiveWeightedAllocatedGiB':v['weightedAllocationBytes']/2**30,'inclusiveWeightedAllocatedGiBPer1000Delivered':v['weightedAllocationBytes']/2**30*1000/delivered}
                output[group]={'executionSamples':stats['executionSamples'],'allocationSampleEvents':stats['allocationSampleEvents'],'weightedAllocatedGiB':stats['weightedAllocationBytes']/2**30,'weightedAllocatedGiBPer1000Delivered':stats['weightedAllocationBytes']/2**30*1000/delivered,'gcPauses':stats['gcPauses'],'targets':targets}
            groups[window]=output
        results[case]={'routeSeconds':run['travelAndEqualDeliveryWaitSeconds'],'actualDeliveredChunks':delivered,'actualUnloadCallbacks':run['deliveredChunks']['unloadCallbacks'],'sumUniquePerPhase':run['deliveredChunks']['sumUniquePerPhase'],'jfrSha256':receipt['jfrSha256'],'targetAnalysisSha256':sha(folder/'target-analysis.json'),'absoluteUtcPhaseAlignment':'Approximate capture-file first-write anchor plus exact same-JVM monotonic offsets; see phase-provenance.json.','observedRelativeAnchorSpreadMs':provenance['observedAnchorOffsetSpreadMs'],'groups':groups}
    assert len(source_sha)==1
    comparisons={}
    for window in ['TRAVEL_TOTAL','TRAVEL_TOTAL_CORE_500MS']:
        before=results['shapes-before']['groups'][window]['ALL'];after=results['shapes-after']['groups'][window]['ALL'];comparisons[window]={}
        for target in TARGETS:
            b=before['targets'][target];a=after['targets'][target];base=b['inclusiveWeightedAllocatedGiBPer1000Delivered'];end=a['inclusiveWeightedAllocatedGiBPer1000Delivered']
            comparisons[window][target]={'beforeStackPresence':b['executionSamplePresence'],'afterStackPresence':a['executionSamplePresence'],'beforeInclusiveWeightedGiBPer1000Delivered':base,'afterInclusiveWeightedGiBPer1000Delivered':end,'estimatedAllocationPerDeliveryChangePercent':100*(end/base-1) if base else None}
    report={'status':'PASS_ONE_MATCHED_ROUTE_JFR_TARGET_COMPARISON','profileScope':'tower+brick movement and equal FULL-delivery waits, excluding startup, facade first teleport and final save; all-thread totals and main/worker groups retained.','analyzerSourceSha256':next(iter(source_sha)),'routeComparisonSha256':sha(E/'route-comparison.json'),'unitsAndLimits':['Execution samples record stack presence, not invocation count or wall time. Zero samples do not prove no execution.','Allocation weights are estimates of cumulative allocated bytes; they are not exact allocations, live heap, process memory or memory savings. Different inclusive target categories overlap and must not be summed.','Per1000Delivered divides by actual accepted ClientChunkEvents.CHUNK_LOAD callbacks including redeliveries; unique sums are within each phase and may overlap between phases.','JFR absolute UTC alignment is approximate due capture-write delay. Full windows and windows trimmed500ms per phase boundary are both retained; the native route duration remains exact monotonic time.','One before/after observation with identical Java/options/profile/instrumentation and per-file world inputs. OS caches were not flushed. GC durations are measured top-level pause events whose start lies inside the estimated windows.','Static catalog initialization is outside these load-time windows; startup deduplication is not evidence of streaming improvement.'], 'runs':results,'targetChanges':comparisons}
    detailed=J/'comparison-detailed.json';detailed.write_text(json.dumps(report,indent=2)+'\n')
    primary=['CompositeOwnerNbt.read','DecodePool.savedContributions','DecodePool.contributions','DecodePool.contribution','CompositeData.boxes','ImportedPhysicalShapes.exact','ImportedPhysicalShapes.buildExact','ShardedCompositeLedger.read','VanillaChunkBuilder.*']
    concise={'status':report['status'],'scope':report['profileScope'],'unitsAndLimits':report['unitsAndLimits'],'detailFile':detailed.name,'detailSha256':sha(detailed),'runs':{}}
    for case,result in results.items():
        total=result['groups']['TRAVEL_TOTAL']['ALL']
        concise['runs'][case]={'routeSeconds':result['routeSeconds'],'actualDeliveredChunks':result['actualDeliveredChunks'],'allThreadExecutionSamples':total['executionSamples'],'weightedAllocatedGiB':total['weightedAllocatedGiB'],'weightedAllocatedGiBPer1000Delivered':total['weightedAllocatedGiBPer1000Delivered'],'gcPauses':total['gcPauses'],'targets':{t:total['targets'][t] for t in primary}}
    concise['targetChanges']={t:comparisons['TRAVEL_TOTAL'][t] for t in primary}
    (J/'comparison-summary.json').write_text(json.dumps(concise,indent=2)+'\n')
if __name__=='__main__':
    try:main()
    except Exception:
        (J/'comparison-error.txt').write_text(traceback.format_exc());raise
