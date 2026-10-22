import argparse,hashlib,json,math
from pathlib import Path
P=Path(__file__).absolute().parents[2];O=P/'build/streaming-validation'
def stats(values):
 a=sorted(values)
 if not a:return {'count':0}
 return {'count':len(a),'averageMs':sum(a)/len(a)/1e6,'p50Ms':a[math.ceil(len(a)*.5)-1]/1e6,'p95Ms':a[math.ceil(len(a)*.95)-1]/1e6,'p99Ms':a[math.ceil(len(a)*.99)-1]/1e6,'maxMs':a[-1]/1e6,'over50ms':sum(x>50e6 for x in a),'over100ms':sum(x>100e6 for x in a),'over1000ms':sum(x>1e9 for x in a)}
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--candidate-case',default='after1');a=ap.parse_args();assert a.candidate_case.replace('-','').isalnum()
 result={'scope':'Three native camera phases. Facade includes initial teleport/settling. Tower and brick use actual one-block spectator movement, native packets and server acknowledgement. Startup/resource bake and final save excluded from timing samples. Server timings cover ServerWorld.tick plus registered END_WORLD_TICK maintenance; other server-loop work is not included. OS file cache was not flushed.','runs':{}}
 preparations=[]
 for case in ['before1',a.candidate_case]:
  prep=json.loads((O/(case+'-prepared.json')).read_text());launch=json.loads((O/(case+'-launch.json')).read_text());assert launch.get('exitCode')==0 and launch.get('captureComplete') and launch.get('normalSaveMessages')
  run=Path(prep['run_directory']);evidence=O/(case+'-evidence');capture_file=evidence/'client-capture-output.json' if evidence.exists() else run/'client-capture-output.json';cap=json.loads(capture_file.read_text());assert cap['status']=='CAPTURE_COMPLETE' and len(cap['frames'])==3
  mem=[json.loads(x) for x in (O/(case+'-memory.jsonl')).read_text().splitlines()]
  phases=[];travel_frames=[];travel_ticks=[]
  for phase in cap['streamingPhases']:
   phase=dict(phase);phase['presentation']={k:v for k,v in phase['presentation'].items() if k!='rawNs'};phase['serverWorldTicks']={k:v for k,v in phase['serverWorldTicks'].items() if k!='rawNs'};phases.append(phase)
  for phase in cap['streamingPhases'][1:]:travel_frames+=phase['presentation']['rawNs'];travel_ticks+=phase['serverWorldTicks']['rawNs']
  during=[row for row in mem if row.get('captureFrame') in ['tower','brick']]
  def peak(key,rows):return max((row[key] for row in rows if row.get(key) is not None),default=None)
  result['runs'][case]={'jarSha256':prep['artifact_sha256'],'fixtureManifestSha256':prep['fixtureManifestSha256'],'options':{k:prep['graphics'][k] for k in ['ao','gamma','renderDistance','simulationDistance','enableVsync','maxFps','resourcePacks']},'phases':phases,'travelPresentation':stats(travel_frames),'travelServerWorldTicks':stats(travel_ticks),'travelSeconds':sum(p['durationMs'] for p in phases[1:])/1000,'peakMemoryWholeRun':{k:peak(k,mem) for k in ['workingSetBytes','privateCommitBytes','dedicatedGpuBytes','sharedGpuBytes']},'peakMemoryTravel':{k:peak(k,during) for k in ['workingSetBytes','privateCommitBytes','dedicatedGpuBytes','sharedGpuBytes']},'frames':[{k:f.get(k) for k in ['key','fileName','sha256','actualCamera','travel','nativeMemory','loadedChunks','requiredClientBlockEvidence']} for f in cap['frames']]}
  preparations.append(prep)
  capped=any(p['presentation']['count']>=p['presentation'].get('sampleLimit',131072) for p in cap['streamingPhases'][1:])
  result['runs'][case]['travelPresentationCoverage']='PARTIAL_AFTER_SAMPLE_LIMIT' if capped else 'COMPLETE'
  result['runs'][case]['towerPresentation']=stats(cap['streamingPhases'][1]['presentation']['rawNs'])
  result['runs'][case]['towerPresentationCoverage']='PARTIAL_AFTER_SAMPLE_LIMIT' if cap['streamingPhases'][1]['presentation']['count']>=cap['streamingPhases'][1]['presentation'].get('sampleLimit',131072) else 'COMPLETE'
 assert preparations[0]['derived_world_copy']['files']==preparations[1]['derived_world_copy']['files'],'Native world inputs differed'
 assert result['runs']['before1']['options']==result['runs'][a.candidate_case]['options'],'Options differed'
 result['matchedWorldFiles']=len(preparations[0]['derived_world_copy']['files']);result['status']='PASS_MATCHED_NATIVE_ROUTE'
 for key in ['towerPresentation','travelServerWorldTicks']:
  result.setdefault('ratios',{})[key]={field:result['runs']['before1'][key][field]/result['runs'][a.candidate_case][key][field] for field in ['averageMs','p95Ms','p99Ms','maxMs']}
 result['frameTimingLimitNote']='The old brick phase reached its 131072-frame storage cap. Aggregate travel frame percentiles therefore do not cover the full old route and must not be used to claim improvement. Use uncapped tower frames plus complete server-world tick samples and route duration.'
 (O/('comparison-'+a.candidate_case+'.json')).write_text(json.dumps(result,indent=2)+'\n')
if __name__=='__main__':main()
