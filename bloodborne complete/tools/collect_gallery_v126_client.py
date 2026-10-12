"""Bind actual client opening, frame/root checks and saved-NBT comparison evidence.

Offline launcher uses an explicit dummy legacy token; its 401 authentication
rejection is preserved separately from local mod/world verification. Every other
error or exception remains a failure. Physical keyboard/mouse is not inferred.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

ROOT=Path(__file__).resolve().parents[1]

def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n')

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--client-profile',type=Path,required=True)
    parser.add_argument('--after-snapshot',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    profile=read(args.client_profile);after=read(args.after_snapshot)
    run=Path(profile['run_directory']).resolve()
    assert run.is_relative_to((ROOT/'build').resolve()) and 'flatgallery' in run.name
    capture_path=run/'client-capture-output.json';capture=read(capture_path)
    marker_path=run/'client-capture-input.json';marker=read(marker_path)
    jar=Path(profile['artifact']);digest=sha(jar)
    assert profile['artifact_sha256']==capture['productionJarSha256']==after['productionJarSha256']==digest
    assert marker['schema']=='dreamwalker-flat-gallery-v126-capture-input' and marker['productionJarSha256']==digest
    assert capture['inputSha256']==sha(marker_path) and capture['expectedFrames']==len(marker['frames'])
    assert capture['status']=='CAPTURE_COMPLETE' and capture['captureComplete'] is True and capture['capturedFrames']==capture['expectedFrames']
    assert after['status']=='PASS' and after['productionReopenCompared'] is True
    assert after['schema']=='dreamwalker-flat-gallery-v126-disk-verification'
    assert after['functionalCatalogue']['status']=='PASS' and marker['newWorkingTypes']==after['functionalCatalogue']['newWorkingTypes']
    copied=profile['derived_world_copy'];source=Path(copied['source']).resolve()
    assert source.is_relative_to((ROOT/'build').resolve()) and copied['copy_byte_verification']=='PASS'
    assert all(sha(source/row['path'])==row['sha256'] for row in copied['files']),'Source gallery changed during client fixture run'
    roots=entities=native_stairs=rung_ladders=0;sections=set();frames=[]
    assert [row['key'] for row in capture['frames']]==[row['key'] for row in marker['frames']]
    for row in capture['frames']:
        assert row['status']=='PASS'
        png=run/'screenshots'/row['fileName']
        assert sha(png)==row['sha256'] and png.stat().st_size==row['bytes'] and png.read_bytes()[:8]==b'\x89PNG\r\n\x1a\n'
        assert row['width']==1280 and row['height']==720
        assert row.get('renderedCameraVerified') is True and row.get('frameRenderEvents',0)>0
        for root in row['actualRootChecks']:
            assert root['status']=='PASS' and root['registry']==root['actualRegistry'] and root['uuid']==root['actualUuid']
            for key in ('variant','profile','open','rotation','half','shape','trim','connections','height','art_bank','mount'):
                if key in root:assert str(root[key])==root['actualProperties'][key]
            if root['registry'].startswith('bloodborne_dw:prototype_wall') and root['registry']!='bloodborne_dw:prototype_wall_skin_1':
                assert root['actualProperties']['connections']=='auto'
            if root['registry']=='bloodborne_dw:source_hold_ladder_02':
                assert root['actualProperties']['height']=='one';rung_ladders+=1
            roots+=1
        if 'semanticStairState' in row:
            assert len(row['actualRootChecks'])==1
            live=int(row['actualRootChecks'][0]['actualProperties']['variant']);assert 0<=live<10
            semantic=row['semanticStairState'];shapes=('straight','inner_left','inner_right','outer_left','outer_right')
            assert semantic['encodedVariant']==live and semantic['half']==('top' if live>=5 else 'bottom') and semantic['shape']==shapes[live%5]
            native_stairs+=1
        for entity in row['actualEntityChecks']:
            assert entity['status']=='PASS' and entity['registry']==entity['actualRegistry'] and entity['uuid']==entity['actualUuid']
            entities+=1
        sections.add(row['section'])
        frames.append({'fileName':row['fileName'],'key':row['key'],'section':row['section'],'name':row['name'],
            'sha256':row['sha256'],'bytes':row['bytes'],'liveRootChecks':len(row['actualRootChecks']),
            'liveEntityChecks':len(row['actualEntityChecks']),'status':'PASS'})
    assert {'v12','new','absent','items','assemblies','doors','city'} <= sections
    assert rung_ladders>0, 'Native stair material/ladder state capture missing'
    log_path=run/'launch-console.log';log=log_path.read_text(encoding='utf8',errors='replace')
    assert 'Starting integrated minecraft server version 1.20.1' in log and 'logged in with entity id' in log
    assert 'Stopping!' in log and 'All dimensions are saved' in log
    errors=[line for line in log.splitlines() if '/ERROR]' in line]
    expected_auth=[line for line in errors if re.fullmatch(r'\[[^\]]+\] \[Render thread/ERROR\]: Failed to verify authentication',line)]
    assert errors==expected_auth and len(expected_auth)<=1,'Unexpected client/world error: '+repr(errors)
    exception_lines=[line for line in log.splitlines() if 'Exception' in line]
    known_exception_patterns=[
        r'^\[[^\]]+\] \[main/WARN\]: Error loading class: com/jamieswhiteshirt/reachentityattributes/ReachEntityAttributes \(java\.lang\.ClassNotFoundException: com/jamieswhiteshirt/reachentityattributes/ReachEntityAttributes\)$',
        r'^com\.mojang\.authlib\.exceptions\.InvalidCredentialsException: Status: 401$',
        r'^Caused by: com\.mojang\.authlib\.exceptions\.MinecraftClientHttpException: Status: 401$',
        r'^\s+at com\.mojang\.authlib\.exceptions\.MinecraftClientHttpException\.toAuthenticationException\(MinecraftClientHttpException\.java:56\).*$',
    ]
    unexpected=[line for line in exception_lines if not any(re.fullmatch(pattern,line) for pattern in known_exception_patterns)]
    assert not unexpected,'Unexpected game/mod exception: '+repr(unexpected)
    result={'schema':'dreamwalker-flat-gallery-v126-native-client-verification','status':'PASS',
        'productionJarSha256':digest,'minecraft':'1.20.1','fabricLoader':'0.16.10','actualIntegratedWorldOpened':'PASS',
        'nativeFramebufferImages':len(frames),'livePersistedRootStateAndUuidChecks':roots,'livePersistedEntityUuidChecks':entities,
        'sectionsRendered':sorted(sections),'normalClientShutdownAndWorldSave':'PASS','allSavedObjectDataUnchangedAfterClientReopen':'PASS',
        'verifiedSavedObjects':after['verifiedExhibits'],'verifiedDiagnosticPlatforms':after.get('verifiedDiagnostics', 0),
        'newCanonicalWorkingTypes':marker['newWorkingTypes'],'capturedNativeStairMaterials':native_stairs,'capturedCanonicalRungLadders':rung_ladders,
        'nativeStairHalfShapeEncoding':'VARIANT0..4 bottom,5..9 top; straight/inner_left/inner_right/outer_left/outer_right',
        'sourceGalleryUnchangedAfterClientRun':True,
        'offlineAuthentication':{'status':'EXPECTED_DUMMY_LEGACY_TOKEN_401' if expected_auth else 'NO_AUTH_ERROR',
            'errors':expected_auth,'scope':'Explicit isolated offline identity with accessToken=0; no online account authentication was requested.'},
        'knownWarnings':[line for line in log.splitlines() if '/WARN]' in line],
        'localModWorldErrors':[],'physicalMouseKeyboard':'NOT_RUN','visualReview':'PENDING_SEPARATE_PIXEL_REVIEW',
        'frames':frames,'evidence':{
            'clientProfile':{'path':str(args.client_profile.resolve()),'sha256':sha(args.client_profile)},
            'nativeCapture':{'path':str(capture_path),'sha256':sha(capture_path)},
            'captureMarker':{'path':str(marker_path),'sha256':sha(marker_path),'manifestSha256':marker['manifestSha256']},
            'clientLog':{'path':str(log_path),'sha256':sha(log_path)},
            'savedNbtAfterClient':{'path':str(args.after_snapshot.resolve()),'sha256':sha(args.after_snapshot)}}}
    write(args.output,result)

if __name__=='__main__':
    try:main()
    except Exception as failure:
        if '--output' in sys.argv:write(Path(sys.argv[sys.argv.index('--output')+1]),{'schema':'dreamwalker-flat-gallery-v126-native-client-verification','status':'FAIL','failure':repr(failure)})
        raise
