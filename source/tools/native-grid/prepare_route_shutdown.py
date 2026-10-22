"""Preserve and fix only post-save recorder shutdown before the first pair."""
import hashlib,json
from run_profiles import P,ROOT,write,sha

for variant in ('baseline','candidate'):
    assert not (ROOT/'research/pilot'/('performance-'+variant)).exists(), 'Do not change the recorder during a matched pair'
folder=ROOT/'research/profiles/measurement-vm-lifecycle'
assert not folder.exists()
folder.mkdir()
source=P/'src/routeqa/java/dev/dreamwalker/bloodbornedw/routeqa/RouteServer.java'
artifact=P/'build/libs/dreamwalker-native-route-qa-0.1.0-v12.7.4-native-grid.1.jar'
before=source.read_bytes();newline=b'\r\n' if b'\r\n' in before else b'\n'
old=b'ServerLifecycleEvents.SERVER_STOPPED.register(server->{try{ticks.close();}catch(Exception e){e.printStackTrace();}});'
assert before.count(old)==1
new='''ServerLifecycleEvents.SERVER_STOPPED.register(server->{
            try{ticks.close();}catch(Exception e){e.printStackTrace();}
            // End only this opt-in recorder VM after every server callback and
            // the actual game-server thread have completed their normal stop.
            Thread completion=new Thread(()->{
                try{
                    server.getThread().join(30000);
                    if(server.getThread().isAlive())return;
                    report.addProperty("normalServerThreadFinished",true);save();
                    System.exit(!failed&&report.has("status")&&report.get("status").getAsString().equals("PASS_SAVED")?0:1);
                }catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
            },"NativeRouteQaExit");
            completion.setDaemon(true);completion.start();
        });'''.replace('\n',newline.decode()).encode()
(folder/'RouteServer-before.java').write_bytes(before)
(folder/'route-recorder-before.jar').write_bytes(artifact.read_bytes())
source.write_bytes(before.replace(old,new))
write(folder/'source-transition.json',{'status':'SOURCE_PREPARED_BEFORE_FIRST_PAIR','beforeSourceSha256':hashlib.sha256(before).hexdigest(),'afterSourceSha256':sha(source),'beforeHelperSha256':sha(artifact),'profileLockSha256':sha(ROOT/'research/profiles/profile-lock.json'),'sameHelperForBaselineAndCandidate':True,'measurementPhasesUnchanged':True,'onlyChange':'VM exit after actual server-thread termination and existing PASS_SAVED or FAIL','actualCauseEvidence':str(ROOT/'research/function-qa/candidate/post-save-threads.txt')})
