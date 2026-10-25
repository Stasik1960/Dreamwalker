"""Read existing frame/chunk journals and JFR; never changes chunk loading."""
import argparse,bisect,csv,hashlib,json,subprocess,traceback
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024'
JAVA=Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin/java.exe')
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--run',type=Path,required=True);ap.add_argument('--out',type=Path,required=True);a=ap.parse_args()
 assert read(a.run/'receipt.json')['status']=='PASS';a.out.mkdir(parents=True,exist_ok=False)
 c=read(a.run/'client/client-route.json');loads=[]
 with (a.run/'client/client-chunks.csv').open(newline='',encoding='utf-8') as f:
  for row in csv.DictReader(f):
   if row['event']=='LOAD':loads.append(int(row['epochNs']))
 loads.sort();intervals=[]
 for p in c['phases']:
  for label,start,end in (('MOVEMENT_AND_SETTLING',p['startEpochNs'],p['stationaryStartEpochNs']),('STATIONARY',p['stationaryStartEpochNs'],p['endEpochNs'])):
   if end<=start:continue
   deliveries=bisect.bisect_left(loads,end)-bisect.bisect_left(loads,start)
   intervals.append(f'{start/1e6:.6f}\t{end/1e6:.6f}\t{label}\t{deliveries}\n')
 (a.out/'intervals.tsv').write_text(''.join(intervals),encoding='utf-8')
 slow=overlap=total=0;worst=[]
 with (a.run/'client/client-frames.csv').open(newline='',encoding='utf-8') as f:
  for row in csv.DictReader(f):
   total+=1;duration=int(row['intervalNs']);end=int(row['endEpochNs'])
   if duration<=250_000_000:continue
   slow+=1;count=bisect.bisect_right(loads,end)-bisect.bisect_left(loads,end-duration)
   overlap+=count>0;worst.append({'phase':row['phase'],'milliseconds':duration/1e6,'chunkLoadCallbacksDuringFrame':count})
 summary={'status':'JOURNALS_ANALYZED_JFR_PENDING','sourceRun':str(a.run),'frames':total,'framesOver250ms':slow,'slowFramesOverlappingChunkLoadCallback':overlap,'fraction':overlap/slow if slow else None,'worstFrames':sorted(worst,key=lambda x:x['milliseconds'],reverse=True)[:20],
  'limits':['Temporal overlap is not proof of causality; callbacks run on the client thread and can be delayed by its stall.','JFR execution samples show stack presence, not elapsed time. Allocation weights estimate cumulative allocations, not live RAM.','The128MiB recording may retain only the end of the route; inspect coverage and recording event bounds. CSV journals cover the full completed route.','Movement windows include travel and settling; stationary windows use the frozen recorder boundaries.']}
 (a.out/'journal-summary.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
 classes=a.out/'classes';classes.mkdir();analyzer=P/'tools/collision-streaming-20261018/JfrShapeSummary.java'
 commands=[[str(JAVA.with_name('javac.exe')),'-J-Xmx128M','-d',str(classes),str(analyzer)],
  [str(JAVA),'-Xmx768M','-cp',str(classes),'JfrShapeSummary',str(a.run/'client/client.jfr'),str(a.out/'intervals.tsv'),str(a.out/'jfr-analysis.json')]]
 with (a.out/'analyzer.log').open('w',encoding='utf-8') as log:
  for command in commands:
   result=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=600,creationflags=subprocess.CREATE_NO_WINDOW);assert result.returncode==0
 j=read(a.out/'jfr-analysis.json');summary.update({'status':'EXISTING_JOURNALS_AND_JFR_ANALYZED','jfrEventBoundsMs':[j['recordingFirstEventEpochMs'],j['recordingLastEventEpochMs']],'jfrEventsRead':j['eventsRead'],'analyzerSourceSha256':hashlib.sha256(analyzer.read_bytes()).hexdigest(),'jfrGroups':{key:j['phaseThreads'].get(key) for key in ('MOVEMENT_AND_SETTLING|ALL','MOVEMENT_AND_SETTLING|Render thread','MOVEMENT_AND_SETTLING|Worker-Main-*','STATIONARY|ALL')}})
 (a.out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
if __name__=='__main__':
 try:main()
 except BaseException:
  (T/'loading-analysis-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
