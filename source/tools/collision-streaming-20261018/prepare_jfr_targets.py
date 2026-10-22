"""Prepare approximate UTC boundaries from exact same-JVM phase durations.

Capture-file mtime is written immediately after each probe begin. The least
delayed observed mtime anchor is applied to every System.nanoTime phase so
relative durations/gaps remain exact. The absolute anchor is approximate.
"""
import argparse,datetime,hashlib,json
from pathlib import Path
P=Path(__file__).absolute().parents[2];E=P.parent/'deliverables/collision-streaming-20261018/evidence'
def epoch(s):return datetime.datetime.fromisoformat(s.replace('Z','+00:00')).timestamp()*1000
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--case',required=True);a=ap.parse_args()
    prep=json.loads((E/(a.case+'-prepared.json')).read_text());run=Path(prep['run_directory'])
    cap=json.loads((run/'client-capture-output.json').read_text());assert cap['status']=='CAPTURE_COMPLETE'
    mem=[json.loads(x) for x in (E/(a.case+'-memory.jsonl')).read_text().splitlines() if x.strip()]
    phases=cap['streamingPhases'];anchors=[]
    for p in phases:
        candidates=[r for r in mem if r.get('captureFrame')==p['phase'] and r.get('captureStatus') in ('NAVIGATING','TRAVELLING') and r.get('capturePhaseWrittenUtc')]
        assert candidates,'Missing first capture-write phase anchor: '+p['phase']
        row=min(candidates,key=lambda r:epoch(r['capturePhaseWrittenUtc']));written=epoch(row['capturePhaseWrittenUtc'])
        anchors.append({'phase':p['phase'],'captureFileWrittenUtc':row['capturePhaseWrittenUtc'],'writtenEpochMs':written,'phaseStartedNanoTime':p['startedNanoTime'],'offsetMs':written-p['startedNanoTime']/1e6})
    offset=min(r['offsetMs'] for r in anchors);spread=max(r['offsetMs'] for r in anchors)-offset
    out=E/'jfr'/a.case;out.mkdir(parents=True,exist_ok=False)
    lines=[];bounds=[]
    for p in phases:
        start=offset+p['startedNanoTime']/1e6;end=offset+p['endedNanoTime']/1e6
        assert end>start
        row={'phase':p['phase'],'estimatedStartEpochMs':start,'estimatedEndEpochMs':end,'exactDurationMs':p['durationMs'],'deliveredChunks':p['chunkDelivery']['loadCallbacks']};bounds.append(row)
        lines.append(f"{start:.6f}\t{end:.6f}\t{p['phase']}\t{p['chunkDelivery']['loadCallbacks']}\n")
    (out/'intervals.tsv').write_text(''.join(lines))
    report={'status':'APPROXIMATE_UTC_BOUNDS_PREPARED','case':a.case,'jfr':str(run/'streaming.jfr'),'intervals':bounds,'firstWriteAnchors':anchors,'observedAnchorOffsetSpreadMs':spread,'chosenOffsetMs':offset,'boundaryMethod':'First per-frame NAVIGATING/TRAVELLING capture-file mtimes; least delayed observed anchor aligned to all same-JVM System.nanoTime phase boundaries.','limitations':'Absolute UTC boundary is approximate due scheduling/serialization/write delay after begin; observed offset spread measures relative anchor delay, not an upper bound on common absolute delay. Native route timings themselves remain exact monotonic measurements. Startup and save excluded. A separately reported core trims 500 ms from each estimated boundary.','preparedFileSha256':hashlib.sha256((E/(a.case+'-prepared.json')).read_bytes()).hexdigest()}
    (out/'phase-provenance.json').write_text(json.dumps(report,indent=2)+'\n')
if __name__=='__main__':main()
