"""Read actual retained JVM histograms; never attribute global arrays to mod."""
import hashlib,json,re
from v127_visual_export import ROOT,OUT,write_json

def histogram(relative):
    path=ROOT/relative;blob=path.read_bytes();text=blob.decode('utf-8')
    rows={}
    for line in text.splitlines():
        match=re.match(r'\s*\d+:\s+(\d+)\s+(\d+)\s+(.+)',line)
        if match:
            count,size,name=match.groups();rows[name]={'instances':int(count),'bytes':int(size)}
    total=re.search(r'^Total\s+(\d+)\s+(\d+)',text,re.M)
    assert total and rows
    return {'path':relative,'sha256':hashlib.sha256(blob).hexdigest(),'instances':int(total[1]),'bytes':int(total[2]),'classes':rows}

def main():
    before=histogram('build/v127-validation/tests5/startup-retained-heap-histogram.txt')
    after=histogram('build/v127-validation/tests6/startup-retained-heap-histogram.txt')
    names=['com.google.common.collect.ArrayTable','dev.dreamwalker.bloodbornedw.composite.CompactStateTransitions',
           'net.minecraft.block.BlockState','[Ljava.lang.Object; (java.base@17.0.15)',
           '[[Ljava.lang.Object; (java.base@17.0.15)','com.google.common.collect.ImmutableMapEntry',
           'com.google.common.collect.ImmutableMapEntry$NonTerminalImmutableMapEntry',
           '[Lcom.google.common.collect.ImmutableMapEntry;','com.google.common.collect.RegularImmutableMap']
    comparison=[]
    for name in names:
        a=before['classes'].get(name,{'instances':0,'bytes':0});b=after['classes'].get(name,{'instances':0,'bytes':0})
        comparison.append({'class':name,'before':a,'after':b,'bytesReduction':a['bytes']-b['bytes']})
    del before['classes'];del after['classes']
    reduction=before['bytes']-after['bytes']
    report={'status':'PASS_ACTUAL_STARTUP_RETAINED_HEAP_COMPARISON','before':before,'after':after,
        'retainedBytesReduction':reduction,'retainedGiBReduction':reduction/1024**3,
        'retainedPercentReduction':100*reduction/before['bytes'],
        'instancesReduction':before['instances']-after['instances'],'selectedClasses':comparison,
        'scope':'Actual entire JVM retained startup heaps after GC, captured by the parent in independent native server runs. Spawn progress, timing and source checkpoint differ slightly. Counts are not byte attribution to this mod alone and do not prove the complete QA suite passed.',
        'suiteStatus':'SEE_ROOT_NATIVE_REPORT; tests6 subsequently logged heap exhaustion after the game test server started.'}
    write_json(OUT/'startup-heap-comparison.json',report)
    print(json.dumps({k:v for k,v in report.items() if k!='selectedClasses'}))

if __name__=='__main__':main()
