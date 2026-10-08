"""Fresh review V9 kit/full-source package gated by explicit current evidence.

No default report selects historicalV7 proof. Collect actual raw evidence with
--collect-primary-only before the final source/docs freeze; then --check-only
or package into a fresh build/delivery/v9 directory. User acceptance stays pending.
"""
from __future__ import annotations
import argparse, hashlib, json, posixpath, re, sys, xml.etree.ElementTree as ET, zipfile
from pathlib import Path
from urllib.parse import unquote, urlsplit
from package_first_set import ROOT, digest, require, resolve, read, json_bytes, source_files, write_zip, verified_archive, directory_roots, zip_roots

SOURCE_PREFIX='dreamwalker-bb-fabric-1.20.1-full-source-review-v9/'
BASE='dreamwalker-bb-fabric-1.20.1-review-v9'

KIT_GUIDE_FILES=('DIAGNOSTICS_V9_REVIEW.md','RP_DIAGNOSTICS_V9.md','RP_REVIEW_V9.md',
    'MECHANISMS_V9_REVIEW.md','LADDER_V9_REVIEW.md','REVIEW_V9_TYPES_AND_GLAZING.md',
    'REVIEW_V9_DIAGNOSTICS_CLIENT.md')
# Inline links/images and reference definitions cover the delivered guides.
MARKDOWN_LINK=re.compile(r'(?P<prefix>!?\[(?:\\.|[^\]\\\n])*\]\()\s*'
    r'(?P<target><[^>\n]+>|(?:\\.|[^\s()\\\n]|\([^()\n]*\))+)'
    r'(?P<title>\s+(?:"[^"\n]*"|\x27[^\x27\n]*\x27))?\s*\)')
MARKDOWN_REFERENCE=re.compile(r'^(?P<prefix>[ \t]{0,3}\[[^\]\n]+\]:[ \t]*)'
    r'(?P<target><[^>\n]+>|\S+)(?P<title>[ \t]+(?:"[^"\n]*"|\x27[^\x27\n]*\x27))?[ \t]*$',re.M)


def local_markdown_target(target):
    """Return a decoded local path/suffix; external URLs and in-page anchors stay intact."""
    target=target[1:-1] if target.startswith('<') and target.endswith('>') else target
    target=target.replace('\\ ', ' ')
    parsed=urlsplit(target)
    drive=bool(re.match(r'^[A-Za-z]:[/\\]',target))
    if target.startswith('//') or (parsed.scheme and parsed.scheme.lower()!='file' and not drive):return None
    if not parsed.path:return None
    path=unquote(target.split('#',1)[0].split('?',1)[0] if drive else parsed.path)
    if parsed.scheme.lower()=='file' and re.match(r'^/[A-Za-z]:/',path):path=path[1:]
    return path,('?' + parsed.query if parsed.query else '')+('#'+parsed.fragment if parsed.fragment else '')


def validate_kit_guide_links(entries, guide_names):
    """Reject any clickable local guide destination outside the exact KIT entry set."""
    checked=0
    for name in guide_names:
        content=entries[name]
        text=(content.read_bytes() if isinstance(content,Path) else content).decode('utf8')
        for pattern in (MARKDOWN_LINK,MARKDOWN_REFERENCE):
            for match in pattern.finditer(text):
                local=local_markdown_target(match['target'])
                if local is None:continue
                destination=posixpath.normpath(posixpath.join(posixpath.dirname(name),local[0].replace('\\','/')))
                require(destination in entries,'KIT guide has missing local Markdown target: '+name+' -> '+match['target'])
                checked+=1
    return checked


def prepare_kit_guides(entries, source_snapshot_files):
    """Rebase in-memory delivery copies; original documents and evidence stay byte-exact."""
    guide_names=['REVIEW-INSTRUCTIONS.md','DIAGNOSTICS-V9.md']
    for filename in KIT_GUIDE_FILES:
        source=ROOT/'docs'/filename
        require(source.is_file(),'Current companion guide missing: '+filename)
        name='guides/'+filename;require(name not in entries,'KIT companion guide collision: '+name)
        entries[name]=source;guide_names.append(name)
    mapped={}
    for name,source in entries.items():
        if isinstance(source,Path):mapped.setdefault(source.resolve(),name)
    snapshot={source.resolve() for source in source_snapshot_files}
    stats={'schema':'dreamwalker-kit-guide-navigation-v1','status':'PASS_SELF_CONTAINED_GUIDE_LOCAL_LINKS',
        'scope':'Rebased delivery guide copies only; original project files/checkpoint/primary bytes unchanged',
        'guides':[],'rebased_local_links':0,'source_only_or_unavailable_labels':0,'external_or_anchor_links_kept':0}
    for name in guide_names:
        source=entries[name];data=source.read_bytes();text=data.decode('utf8')
        def rewrite(match):
            local=local_markdown_target(match['target'])
            if local is None:
                stats['external_or_anchor_links_kept']+=1;return match[0]
            path,suffix=local;resolved=(source.parent/Path(path)).resolve()
            if resolved in mapped:
                target=posixpath.relpath(mapped[resolved],posixpath.dirname(name) or '.')+suffix
                if any(char.isspace() for char in target):target='<'+target+'>'
                stats['rebased_local_links']+=1
                return match['prefix']+target+(match['title'] or '')+(')' if match.re is MARKDOWN_LINK else '')
            display=resolved.relative_to(ROOT).as_posix() if resolved.is_relative_to(ROOT) else path
            if resolved.is_relative_to(ROOT/'build') and resolved.suffix.lower()=='.zip':
                note='исторический архив не включён в KIT: '+display
            elif resolved in snapshot:note='в полном исходнике: '+display
            else:note='проектный или внешний файл не включён в KIT: '+display
            stats['source_only_or_unavailable_labels']+=1
            # Remove the clickable syntax; a reference use remains a plain label once its definition is gone.
            label=match['prefix'].lstrip('!')[1:-2] if match.re is MARKDOWN_LINK else match['prefix'].strip().rstrip(':')
            return label+' ('+note+')'
        text=MARKDOWN_LINK.sub(rewrite,text)
        text=MARKDOWN_REFERENCE.sub(rewrite,text)
        entries[name]=text.encode('utf8')
        stats['guides'].append({'entry':name,'original_source':source.relative_to(ROOT).as_posix(),
            'original_sha256':hashlib.sha256(data).hexdigest(),'delivered_sha256':hashlib.sha256(entries[name]).hexdigest()})
    # The generated top-level guide also participates in destination validation.
    stats['validated_local_links']=validate_kit_guide_links(entries,[*guide_names,'README-REVIEW-V9.md'])
    return stats

def optional_client_diagnostic_exports(actual, run, artifact_sha, stem, historical=False):
    """Retain optional actual client sessions without upgrading shader/reentry gates."""
    review=actual.get('diagnosticsActualClient')
    if review is None:return None
    from verify_review_v9_diagnostics import Verifier
    verifier=Verifier(argparse.Namespace(artifact_sha=artifact_sha,historical_client_timings=historical))
    verified=verifier.client_exports(review,run,stem)
    return {'status':'PASS_LINKED_CURRENT_CLIENT_AND_INTEGRATED_SERVER_EXPORT_BYTES',
        'session':verified['session'],'client_export':verified['client_export'],'server_export':verified['server_export'],
        'timings':verified['timings'],'actual_joined_player_setup':verified['actual_joined_player_setup'],
        'optional_iris_observation':verified['graphics'].get('optionalIris'),
        'client_timing_retention':verified.get('client_timing_retention','NOT_VERIFIED_HISTORICAL_EXPORT_ONLY'),
        'primary':verifier.primary,
        'scope':'Actual current client session, acknowledged item/model operation, cleanup, OFF/ON/OFF samples and local ZIP bytes. Default60 and prior saved-world reentry remain separate required diagnostics-report gates; this is not an Iris shader-options or manual visual gate.'}

def expected_native_cases():
    metadata=read(ROOT/'src/gametest/resources/fabric.mod.json');result=set()
    for classname in metadata['entrypoints']['fabric-gametest']:
        path=ROOT/'src/gametest/java'/Path(*classname.split('.')).with_suffix('.java')
        source=path.read_text(encoding='utf8');source=re.sub(r'/\*.*?\*/|//[^\n]*','',source,flags=re.S)
        names=re.findall(r'@GameTest\b[\s\S]*?\bpublic\s+void\s+(\w+)\s*\(',source)
        require(names,'Registered GameTest class has no actual methods: '+classname)
        for method in names:
            key=classname.rsplit('.',1)[1].lower()+'.'+method.lower();require(key not in result,'Duplicate expected GameTest method');result.add(key)
    return result

def gates(args):
    artifact_sha=digest(args.jar);require(artifact_sha==args.artifact_sha,'Production JAR bytes differ from explicit frozen SHA')
    from verify_review_v9_diagnostics import release_gate
    diagnostics=release_gate(args,artifact_sha)
    evidence=[];primary=[];client_diagnostic_exports=[]
    def gate(path,label):
        path=resolve(path,ROOT);value=read(path);evidence.append({'gate':label,'path':str(path),'sha256':digest(path),'status':'PASS'});return value
    def raw(report,path,field):
        output=report[field];source=resolve(output['path'],ROOT);require(digest(source)==output['sha256'] and read(source)==output['result'],'Actual wrapped JSON changed: '+field)
        primary.append((source,path.stem+'.'+field+'.json',output['sha256']));return output['result']
    if args.diagnostics_report:
        gate(args.diagnostics_report,'current_actual_diagnostics_off_on_reentry_session_exports')
        for name,row in diagnostics['inputs'].items():
            if name!='jar':gate(resolve(row['path'],ROOT),'diagnostics_actual_'+name)
        for row in diagnostics['primary']:
            primary.append((resolve(row['path'],ROOT),row['name'],row['sha256']))
    def server(path,label,profile):
        path=resolve(path,ROOT);report=gate(path,label)
        require(report.get('artifact_sha256')==artifact_sha and report.get('status')=='PASS' and report.get('exit_code')==0 and 'termination' not in report,'Current server did not start/save/exit normally: '+label)
        require(report.get('profile')==profile and report.get('original_world_loaded') is False and report.get('rp_initialization_count')==1,'Server profile/source/RP initialization differs: '+label)
        source=resolve(report['evidence'],ROOT);require(digest(source)==report['console_sha256'],'Current server console changed');primary.append((source,path.stem+'.console.log',report['console_sha256']))
        runtime=resolve(report['run_directory'],ROOT);require(len([p for p in (runtime/'mods').glob('*.jar') if digest(p)==artifact_sha])==1,'Exact current production is absent from actual server mods')
        return report
    author=server(args.author_report,'current_scene_author','minimal');author_actual=raw(author,args.author_report,'review_scene_output')
    require(author_actual['status']=='PASS_SCENE_AUTHORED_REQUIRES_PRODUCTION_REOPEN','Fresh author scene was incomplete')
    author_gameplay=raw(author,args.author_report,'ordinary_gameplay_output')
    require(author_gameplay['status']=='PASS_V9_ORDINARY_GAMEPLAY_AUTHOR_REQUIRES_ACTUAL_RESTART','Ordinary gameplay AUTHOR was incomplete')
    gameplay_restart=server(args.gameplay_restart_report,'current_actual_gameplay_restart','minimal')
    restarted_gameplay=raw(gameplay_restart,args.gameplay_restart_report,'ordinary_gameplay_output')
    require(restarted_gameplay['status']=='PASS_V9_ORDINARY_GAMEPLAY_AFTER_ACTUAL_RESTART','Actual persisted gameplay REOPEN was incomplete')
    reopen=server(args.reopen_report,'current_production_only_scene_reopen','minimal');require(not any(row.get('extra_mod') for row in reopen['modset']),'Ordinary reopen still includes QA addon')
    full=server(args.full_server_report,'current_full_server','full_server')
    scene=gate(args.scene_report,'current_exact_scene_archive');verification=gate(args.scene_verification,'current_independent_scene_verification')
    require(scene.get('production_jar_sha256')==verification.get('production_jar_sha256')==artifact_sha,'Scene proof belongs to another production JAR')
    require(scene['status']=='PACKAGED_VERIFIED_SCENE_PENDING_USER_REVIEW' and verification['status']=='PASS_SAVED_OWNERS_NATIVE_FIXTURES_AND_PRODUCTION_REOPEN','Current scene ownership/archive proof failed')
    scene_input=resolve(verification['scene_input'],ROOT);document=read(scene_input);expected_count=len(document['objects'])
    require(document['sceneId']=='first-set-review-v9' and expected_count==len({tuple(row['root']) for row in document['objects']}),'Current V9 input missing/duplicated roots')
    require(digest(scene_input)==verification['scene_input_sha256'] and scene['root_count']==verification['actual_root_count']==verification['expected_root_count']==expected_count,'Frozen exact V9 scene count/input hash failed')
    require(resolve(verification['author_report'],ROOT)==args.author_report and resolve(verification['production_reopen_report'],ROOT)==args.reopen_report,'Scene verification uses different author/reopen reports')
    require(digest(args.scene_verification)==scene['verification_report_sha256'],'Scene archive independent proof changed')
    scene_zip=resolve(scene['output'],ROOT);require(digest(scene_zip)==scene['sha256'],'Scene ZIP changed');verified_archive(scene_zip,scene['world_prefix'],scene['files'])
    require(len(zip_roots(scene_zip,scene['world_prefix']))==expected_count,'Exact archive root count differs from frozen input')
    from verify_review_v9_scene import run
    class VerificationArgs:pass
    check=VerificationArgs();check.author_report=args.author_report;check.server_report=args.reopen_report;check.scene_input=scene_input;check.artifact_sha=artifact_sha
    check.gameplay_restart_report=args.gameplay_restart_report;check.gameplay_input=args.gameplay_input;check.gameplay_reopen_input=args.gameplay_reopen_input
    require(resolve(verification['ordinary_gameplay']['gameplay_restart_report'],ROOT)==args.gameplay_restart_report,'Scene proof uses another gameplay restart')
    require(run(check)==verification,'Current saved scene no longer matches independent proof')
    if args.gameplay_verification:
        game_proof=gate(args.gameplay_verification,'current_independent_mechanisms_and_lamps_after_restart')
        require(game_proof==verification['ordinary_gameplay'],'Separate gameplay proof differs from complete scene proof')
    full_fresh_construction='NOT_RUN_CURRENT_ARTIFACT'
    if args.full_author_report:
        full_author=server(args.full_author_report,'current_full_fresh_construction_author','full_server')
        full_actual=raw(full_author,args.full_author_report,'review_scene_output')
        require(full_actual.get('status')=='PASS_SCENE_AUTHORED_REQUIRES_PRODUCTION_REOPEN','Current full modset did not finish actual fresh construction')
        require(args.full_gameplay_restart_report,'Full fresh construction requires its own exact persisted gameplay restart')
        full_gameplay=raw(full_author,args.full_author_report,'ordinary_gameplay_output')
        require(full_gameplay['status']=='PASS_V9_ORDINARY_GAMEPLAY_AUTHOR_REQUIRES_ACTUAL_RESTART','Full ordinary gameplay author incomplete')
        full_restart=server(args.full_gameplay_restart_report,'current_full_actual_gameplay_restart','full_server')
        full_replayed=raw(full_restart,args.full_gameplay_restart_report,'ordinary_gameplay_output')
        require(full_replayed['status']=='PASS_V9_ORDINARY_GAMEPLAY_AFTER_ACTUAL_RESTART','Full actual gameplay restart incomplete')
        require(not any(row.get('extra_mod') for row in full['modset']),'Current full server reopen still includes authoring QA')
        full_check=VerificationArgs();full_check.author_report=args.full_author_report;full_check.server_report=args.full_server_report;full_check.scene_input=scene_input;full_check.artifact_sha=artifact_sha
        full_check.gameplay_restart_report=args.full_gameplay_restart_report;full_check.gameplay_input=args.gameplay_input;full_check.gameplay_reopen_input=args.gameplay_reopen_input
        full_proof=run(full_check)
        require(full_proof['actual_root_count']==expected_count,'Full modset fresh construction/reopen differs from exact scene count')
        full_fresh_construction='PASS_ACTUAL_FULL_MODSET_CONSTRUCTION_AND_PRODUCTION_REOPEN_UUID_NBT_BINDINGS'

    def client(path,label,expected_profile):
        path=resolve(path,ROOT);report=gate(path,label)
        require(report.get('artifact_sha256')==artifact_sha and report.get('status')=='PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT' and report.get('exit_code')==0 and report.get('integrated_save_messages_present') is True and 'termination' not in report,'Current ordinary client world/resource/save/normal-exit proof failed: '+label)
        require(report['profile']==expected_profile,'Unexpected client modset profile')
        source=resolve(report['run_directory'],ROOT)/'launch-console.log';require(digest(source)==report['console_sha256'],'Client console bytes changed');primary.append((source,path.stem+'.console.log',report['console_sha256']))
        require('Ladder mount model lacks physical facing:' not in source.read_text(encoding='utf8',errors='replace'),'Current client still logged the ladder inventory model provider exception')
        actual=raw(report,path,'client_review_output');require(actual.get('status','').startswith('PASS_') and actual.get('normalStopRequested') is True and actual.get('guard')=='ISOLATED_SAVED_REVIEW_CLIENT_ONLY','Actual ordinary client output is incomplete')
        require(actual.get('actualProductionOrigins') and all(row['sha256']==artifact_sha for row in actual['actualProductionOrigins']),'Actual client loaded another production JAR')
        marker=resolve(report['qa_input']['source'],ROOT);require(digest(marker)==report['qa_input']['sha256'],'Client marker bytes changed');marker_document=read(marker)
        require(marker_document['productionJarSha256']==artifact_sha and not marker_document.get('diagnosticOnly'),'Diagnostic/historical client is not a packaging gate')
        require(report.get('derived_world_copy',{}).get('source_unchanged_after_run') is True,'Client changed its immutable scene source')
        client_world=resolve(actual['actualWorldDirectory'],ROOT);require(client_world.is_relative_to((ROOT/'build').resolve()),'Client world is outside isolated build area')
        require(directory_roots(client_world)==zip_roots(scene_zip,scene['world_prefix']),'Actual client root states differ from new packaged V9 scene')
        exports=optional_client_diagnostic_exports(actual,report['run_directory'],artifact_sha,path.stem)
        if exports is not None:
            for row in exports['primary']:primary.append((resolve(row['path'],ROOT),row['name'],row['sha256']))
            if not any(row['session']==exports['session'] for row in client_diagnostic_exports):
                client_diagnostic_exports.append({'report':str(path),**exports})
        return report,actual
    minimal_client,client_actual=client(args.client_report,'current_ordinary_minimal_client','minimal')
    optional={'full_client':'NOT_RUN_CURRENT_ARTIFACT','shaders':'NOT_RUN_CURRENT_ARTIFACT','bounded_source41':'NOT_INCLUDED_NO_CURRENT_PROOF','full_fresh_construction':full_fresh_construction}
    if args.full_client_report:
        client(args.full_client_report,'current_selected_full_client','full_client');optional['full_client']='PASS_SELECTED_PROFILE_NORMAL_LOAD_SAVE_EXIT'
    if args.shader_report:
        shader_wrapper,shader_actual=client(args.shader_report,'current_actual_shader_client','full_client')
        iris=shader_actual.get('irisRuntime',{});require(iris.get('status')=='PASS_ACTIVE_IRIS_RENDERING_PIPELINE','Shader resource PASS is not an actual active shader pipeline')
        first,last=iris.get('initial',{}),iris.get('final',{});require(last.get('frameCounter',0)>first.get('frameCounter',0),'Actual shader pipeline frame counter did not advance')
        require(last.get('fallback') is False and last.get('shadersEnabled') is True and last.get('shaderMapPresent') is True and last.get('pipelineClass')=='net.irisshaders.iris.pipeline.IrisRenderingPipeline','Shader client used fallback/no active map')
        optional['shaders']='PASS_ACTIVE_IRIS_PIPELINE_NORMAL_SAVE_EXIT_MANUAL_VISUAL_PENDING'
    expected=expected_native_cases();xml=ET.parse(args.native_xml).getroot();cases=list(xml.iter('testcase'));actual_names=[case.get('name','').lower() for case in cases]
    if args.expected_native_count is not None:require(len(expected)==args.expected_native_count,'Frozen native source list differs from explicit expected count')
    require(len(actual_names)==len(set(actual_names)) and set(actual_names)==expected,'Native XML does not cover the exact current declared GameTest set')
    require(not list(xml.iter('failure')) and not list(xml.iter('error')) and not list(xml.iter('skipped')),'Current native tests failed/errored/skipped')
    for suite in xml.iter('testsuite'):require(all(int(suite.get(k,'0'))==0 for k in ['failures','errors','skipped']),'Native summary reports failures')
    primary.append((args.native_xml,'REVIEW_V9_NATIVE_GAMETEST.xml',digest(args.native_xml)))
    evidence.append({'gate':'current_native_gametests','path':str(args.native_xml),'sha256':digest(args.native_xml),'tests':len(cases),'exact_current_cases':sorted(expected),'status':'PASS'})
    core_source=(ROOT/'src/test/java/dev/dreamwalker/bloodbornedw/runtime/TransactionCoreTest.java').read_text(encoding='utf8');core_count=len(re.findall(r'\brun\("',core_source))
    build=args.build_log.read_text(encoding='utf8',errors='replace');require(re.search(r'PASS '+str(core_count)+r' transaction core checks; Java 17\.',build) and 'BUILD SUCCESSFUL' in build and 'BUILD FAILED' not in build,'Current core/build log is incomplete or failed')
    primary.append((args.build_log,'REVIEW_V9_BUILD_AND_CORE.log',digest(args.build_log)));evidence.append({'gate':'current_java17_core_and_build','path':str(args.build_log),'sha256':digest(args.build_log),'tests':core_count,'status':'PASS'})
    rp=gate(args.rp_report,'current_full_rp_resource_bytes');require(rp.get('final_jar_sha256')==artifact_sha and rp.get('source_status')==rp.get('package_status')=='PASS' and all(row['source_status']==row['package_status']=='PASS' for row in rp['checks']),'Current full RP package bytes failed')
    package=gate(args.package_report,'current_production_qa_separation');require(package.get('production_sha256')==artifact_sha and package.get('status')=='PASS' and package.get('production_contains_qa') is False,'QA code leaked into current production')
    with zipfile.ZipFile(args.jar) as archive:
        metadata=json.loads(archive.read('fabric.mod.json'));require(metadata['id']=='bloodborne_dw' and 'bloodborne_rp' in metadata.get('provides',[]) and 'prototype.4' in metadata['version'],'Wrong combined production/version metadata')
        require(not any(name.startswith(('dev/dreamwalker/bloodbornedw/review/','dev/dreamwalker/bloodbornedw/gametest/')) for name in archive.namelist()),'QA/GameTest classes leaked into production')
        require(not any(name.startswith('dev/dreamwalker/bloodbornerp/lamp/V9GameplayReviewBootstrap') for name in archive.namelist()),'V9 gameplay author code leaked into production')
        for name in read(ROOT/'src/gametest/resources/fabric.mod.json')['entrypoints']['fabric-gametest']:
            prefix=name.replace('.','/');require(not any(entry==prefix+'.class' or entry.startswith(prefix+'$') for entry in archive.namelist()),'Registered native GameTest leaked into production:'+name)
    if args.qa_jar:require(digest(args.qa_jar)==package['qa_sha256'],'Optional separate QA JAR differs from verified addon')
    checkpoint=gate(args.checkpoint_report,'current_user_review_checkpoint');require(checkpoint.get('production_jar_sha256')==artifact_sha and checkpoint.get('user_review')=='PENDING_USER_REVIEW' and checkpoint.get('full_task_status')=='NOT_READY_FULL_TASK','Current checkpoint prematurely claims acceptance or uses another JAR')
    if args.diagnostics_report:
        require(checkpoint.get('diagnostics',{}).get('status')==diagnostics['status'] and resolve(checkpoint['diagnostics']['report'],ROOT)==args.diagnostics_report,'Checkpoint did not gate this exact actual diagnostics proof')
        require(any(resolve(row['path'],ROOT)==args.diagnostics_report and row['sha256']==digest(args.diagnostics_report) for row in checkpoint['runtime_evidence']),'Checkpoint diagnostics evidence bytes changed')
    dependencies={};profile=gate(args.modset_profile,'explicit_supplied_modset_selection');require(digest(args.modset)==profile['source_modset_sha256'],'Supplied mods ZIP changed')
    selected={row['path']:row for row in profile['selected']}
    with zipfile.ZipFile(args.modset) as archive:
        for path in profile['profiles']['minimal']:
            row=selected[path];data=archive.read(path);require(hashlib.sha256(data).hexdigest()==row['sha256'],'Exact dependency bytes changed: '+path)
            require(row['id'] in {'fabric-api','geckolib'},'Minimal kit dependency is not an approved small runtime dependency');dependencies['mods/'+Path(path).name]=data
    require({selected[path]['id'] for path in profile['profiles']['minimal']}=={'fabric-api','geckolib'},'Minimal dependency pair missing')
    source_zip=None
    if args.source_scene_report:
        source=gate(args.source_scene_report,'optional_current_bounded_source41');require(source.get('artifact_sha256')==artifact_sha and source.get('status','').startswith('PASS_ARCHIVE_EXACT_PRODUCTION_REOPEN'),'Optional source41 archive is not current PASS')
        require(source.get('all_archived_file_bytes_verified') is True and source.get('reopened_world_unchanged') is True and source['counts']['objects']==41 and source['counts']['source_member_cells']==110,'Optional bounded source preservation/archive proof failed')
        require(args.frozen_source and args.source_input,'Source41 requires its exact frozen source/input');require(digest(args.frozen_source)==source['source_fixture_sha256'] and digest(args.source_input)==source['input_sha256'],'Optional bounded source input/provenance changed')
        source_input=read(args.source_input);require(source_input.get('reviewRevision')=='V9' and source_input.get('authoringGuard')=='SOURCE_COPY_EXPLICIT_MEMBERS_ONLY' and len(source_input['objects'])==41 and source_input['sourceMemberCount']==110,'Optional source41 uses a historical/prepared manifest instead of the explicit V9 source input')
        plan_path=resolve(source['migration_plan'],ROOT);require(digest(plan_path)==source['migration_plan_sha256'],'Optional current source migration plan changed')
        evidence.append({'gate':'optional_source_current_migration_plan','path':str(plan_path),'sha256':source['migration_plan_sha256'],'status':'PASS_EXACT_CURRENT_ARCHIVE_PLAN_BYTES'})
        source_zip=resolve(source['archive'],ROOT);require(digest(source_zip)==source['archive_sha256'],'Optional source archive changed');verified_archive(source_zip,'First-set-migrated-source-fixture/',source['file_manifest'])
        for record in source['evidence_gates']:
            path=resolve(record['path'],ROOT);require(digest(path)==record['sha256'],'Optional source evidence changed');value=gate(path,'optional_source_'+path.stem);require(value.get('status','').startswith('PASS'),'Optional source gate failed')
            if 'artifact_sha256' in value:require(value['artifact_sha256']==artifact_sha,'Optional source gate uses stale production')
            if 'production_jar_sha256' in value:require(value['production_jar_sha256']==artifact_sha,'Optional source independent gate uses stale production')
            if 'evidence' in value and 'console_sha256' in value:
                log=resolve(value['evidence'],ROOT);require(digest(log)==value['console_sha256'],'Source console changed');primary.append((log,path.stem+'.console.log',value['console_sha256']))
            if 'source_review_output' in value:raw(value,path,'source_review_output')
            if 'bounded_source_output' in value:raw(value,path,'bounded_source_output')
        optional['bounded_source41']='PASS_CURRENT41_OBJECTS110_SOURCE_MEMBERS_TYPED_PROVENANCE'
    for path in args.extra_evidence:
        require(path.is_file(),'Extra evidence missing');evidence.append({'gate':'additional_'+path.stem,'path':str(path),'sha256':digest(path),'status':'INCLUDED_RAW_REPORT_NOT_AUTOMATICALLY_UPGRADED'})
        if path.suffix.lower()=='.json':
            value=read(path)
            if value.get('artifact_sha256')==artifact_sha and value.get('schema') in {'dreamwalker-final-jar-client-v1','dreamwalker-final-jar-server-v1'}:
                log=resolve(value['evidence'],ROOT) if value.get('evidence') else resolve(value['run_directory'],ROOT)/'launch-console.log'
                require(digest(log)==value['console_sha256'],'Current additional wrapper console changed: '+str(path))
                primary.append((log,path.stem+'.console.log',value['console_sha256']))
                for field in ('client_review_output','review_scene_output','bounded_source_output','ordinary_gameplay_output'):
                    if value.get(field):raw(value,path,field)
    return {'artifact_sha256':artifact_sha,'version':metadata['version'],'gates':evidence,'primary':primary,'dependencies':dependencies,
        'scene_zip':scene_zip,'scene_input':scene_input,'scene_root_count':expected_count,'source_zip':source_zip,
        'native_test_count':len(cases),'core_test_count':core_count,'optional_current_verdicts':optional,
        'full_server_other_error_lines':len(full.get('errors',[])),'scene_archive_report':scene,'scene_verification':verification,
        'gameplay_input':args.gameplay_input,'gameplay_reopen_input':args.gameplay_reopen_input,
        'gameplay_restart_report':args.gameplay_restart_report,'diagnostics':diagnostics,
        'actual_client_diagnostic_exports':client_diagnostic_exports}

def collect_primary(proof):
    folder=ROOT/'reports/runtime';folder.mkdir(parents=True,exist_ok=True);records=[];destinations={}
    for source,name,expected_sha in proof['primary']:
        destination=folder/name;duplicate=name in destinations;require(not duplicate or destinations[name]==source,'Primary evidence basename collision');destinations[name]=source
        data=source.read_bytes();require(hashlib.sha256(data).hexdigest()==expected_sha,'Primary bytes changed before collection')
        if destination.exists():require(destination.read_bytes()==data,'Refusing to overwrite a different historical primary evidence file: '+name)
        else:destination.write_bytes(data)
        if not duplicate:records.append({'path':destination.relative_to(ROOT).as_posix(),'source':str(source),'bytes':len(data),'sha256':expected_sha})
    manifest={'schema':'dreamwalker-review-v9-primary-evidence-v1','status':'PASS_CURRENT_RAW_BYTES_COLLECTED',
        'production_jar_sha256':proof['artifact_sha256'],'files':records}
    path=ROOT/'reports/REVIEW_V9_PRIMARY_EVIDENCE.json'
    if path.exists():require(read(path)==manifest,'Refusing to overwrite another V9 primary evidence revision')
    else:path.write_bytes(json_bytes(manifest))
    return path

def require_primary(proof):
    path=ROOT/'reports/REVIEW_V9_PRIMARY_EVIDENCE.json';manifest=read(path);require(manifest['production_jar_sha256']==proof['artifact_sha256'] and manifest['status']=='PASS_CURRENT_RAW_BYTES_COLLECTED','Current raw evidence has not been collected')
    expected={(str(source),expected_sha) for source,_,expected_sha in proof['primary']};require(expected=={(row['source'],row['sha256']) for row in manifest['files']},'Primary evidence manifest does not cover current proof set')
    for row in manifest['files']:
        source=resolve(row['path'],ROOT);require(source.is_relative_to(ROOT/'reports') and digest(source)==row['sha256'] and source.stat().st_size==row['bytes'],'Staged primary evidence changed')
    return path

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['jar','native-xml','build-log','author-report','gameplay-restart-report','gameplay-input','gameplay-reopen-input','reopen-report','scene-verification','scene-report','full-server-report','client-report','rp-report','package-report','modset-profile','modset','review-readme','checkpoint-report']:
        parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--artifact-sha',required=True)
    parser.add_argument('--expected-native-count',type=int,help='Optional explicit frozen count; exact method set is always read from current registered sources and matched to XML')
    for name in ['qa-jar','gameplay-verification','full-author-report','full-gameplay-restart-report','full-client-report','shader-report','source-scene-report','frozen-source','source-input','alt-example']:parser.add_argument('--'+name,type=Path)
    parser.add_argument('--diagnostics-report',type=Path,help='Required for final prototype.4 packaging and primary collection; current actual runtime/on-off/reentry/local exports')
    parser.add_argument('--historical-check-without-diagnostics',action='store_true',help='Only --check-only; incomplete historical check, never a release/primary collector')
    parser.add_argument('--extra-evidence',type=Path,action='append',default=[]);parser.add_argument('--output',type=Path,default=ROOT/'build/delivery/v9')
    parser.add_argument('--collect-primary-only',action='store_true');parser.add_argument('--check-only',action='store_true');args=parser.parse_args()
    for name,value in vars(args).items():
        if isinstance(value,Path):setattr(args,name,resolve(value,ROOT))
    args.extra_evidence=[resolve(path,ROOT) for path in args.extra_evidence];require(args.output.is_relative_to((ROOT/'build/delivery').resolve()),'Delivery must stay in this project build/delivery')
    require(args.review_readme.is_file(),'Russian review instructions missing')
    require((ROOT/'docs/DIAGNOSTICS_V9.md').is_file(),'Current delivered diagnostics instructions missing')
    for name in ['README.txt','TASK.md','PROGRESS.md','INPUTS.json','RELEASE-STATUS.md','CHECK_MATRIX.md','TEST_MATRIX.md']:require((ROOT/name).is_file(),'Full source checkpoint missing '+name)
    proof=gates(args)
    if args.collect_primary_only:
        manifest=collect_primary(proof);print(json.dumps({'status':'PASS_CURRENT_PRIMARY_BYTES_STAGED','manifest':str(manifest)}));return
    primary_manifest=None if args.historical_check_without_diagnostics else require_primary(proof);files=source_files(ROOT)
    if args.check_only:
        print(json.dumps({'status':'PASS_V9_HISTORICAL_GATES_DIAGNOSTICS_NOT_CHECKED' if args.historical_check_without_diagnostics else 'PASS_V9_PACKAGE_GATES_READ_ONLY','production_jar_sha256':proof['artifact_sha256'],'native_tests':proof['native_test_count'],'core_tests':proof['core_test_count'],'roots':proof['scene_root_count'],'source_files':len(files),'diagnostics':proof['diagnostics']['status'],'user_review':'PENDING_USER_REVIEW','full_task_status':'NOT_READY_FULL_TASK'}));return
    require(not args.output.exists(),'Never overwrite a delivery directory; use a fresh V9 output revision');args.output.mkdir(parents=True)
    source_zip=args.output/(BASE+'-full-source.zip');source_records=write_zip(source_zip,{SOURCE_PREFIX+path.relative_to(ROOT).as_posix():path for path in files})
    source_manifest={'schema':'dreamwalker-review-v9-full-source-v1','status':'FULL_SOURCE_CHECKPOINT_PENDING_USER_REVIEW',
        'production_jar_sha256':proof['artifact_sha256'],'archive':source_zip.name,'archive_sha256':digest(source_zip),'archive_bytes':source_zip.stat().st_size,
        'file_count':len(source_records),'files':source_records,'byte_verification':'PASS','excluded':['build','.git','.gradle','__pycache__','node_modules','original external inputs'],
        'user_review':'PENDING_USER_REVIEW','full_task_status':'NOT_READY_FULL_TASK','self_inclusion':False}
    source_manifest_path=args.output/(BASE+'-source-files.json');source_manifest_path.write_bytes(json_bytes(source_manifest))
    matrix={'schema':'dreamwalker-review-v9-package-gates-v1','production_jar_sha256':proof['artifact_sha256'],'version':proof['version'],
        'gates':proof['gates'],'native_tests':proof['native_test_count'],'core_tests':proof['core_test_count'],'scene_roots':proof['scene_root_count'],
        'optional_current_verdicts':proof['optional_current_verdicts'],'full_server_other_error_lines':proof['full_server_other_error_lines'],
        'diagnostics':{'status':proof['diagnostics']['status'],'coverage':proof['diagnostics']['coverage']},
        'actual_client_diagnostic_exports':proof['actual_client_diagnostic_exports'],
        'user_review':'PENDING_USER_REVIEW','full_task_status':'NOT_READY_FULL_TASK','manual_visual_acceptance':'PENDING_USER_REVIEW','mass_city_conversion':'NOT_RUN'}
    readme=(f'# Dreamwalker BB: review V9\n\nВерсия: {proof["version"]}. SHA256 production JAR: {proof["artifact_sha256"]}.\n\n'
        'Статус полного задания: NOT_READY_FULL_TASK. Приёмка исправленного первого набора: PENDING_USER_REVIEW.\n\n'
        'Установка: Minecraft1.20.1, Java17, Fabric Loader0.19.5. В mods лежат новый combined JAR и два исходных минимальных dependency JAR: Fabric API и GeckoLib. Уберите прежний Dreamwalker и отдельный bloodborne_rp перед установкой combined JAR. Оригинальные JAR зависимостей сохранены побайтно.\n\n'
        f'worlds/First-set-review-v9-scene.zip — отдельная новая площадка{proof["scene_root_count"]} объектов. Распакуйте папку мира в saves. Точные станции/координаты — REVIEW-INSTRUCTIONS.md и mapping/first_set_scene_input.json. Исходный полный город не изменён.\n\n'
        'Самостоятельное исполнение выбирается отдельным предметом. Строительный инструмент меняет допустимый поворот, BASE/ALT, монтаж и декоративную позу при наличии строительных прав; связывает рычаги с фактическими механизмами, показывает связи, разрывает их и отменяет выбор. Обычные двери/рычаги/фонари работают обычным взаимодействием; ограда соединяется по соседям сама. На площадке два зарегистрированных фонаря с маршрутами в обе стороны. Проверьте Creative выдачу, middle pick, удобство, силуэт/UV и passage в новой сцене. Технические тесты не заменяют вашу оценку.\n\n'
        'source/ содержит полный исходный проект с src, tools, docs, reports, gradle и корневыми инструкциями; исходные внешние ZIP/JAR пользователей не вложены в source checkpoint. TEST-ONLY-OPTIONAL, если присутствует, содержит QA addon только для технических воспроизводимых проверок и не нужен для обычной игры.\n\n'
        'Исторические V7/failed логи сохранены в полном исходнике и помечены своими hash/revision. Только явно выбранные текущие отчёты образуют gate V9. Шейдеры и полный клиент имеют отдельный текущий статус в CHECK-MATRIX.json; старый shader PASS не засчитывается новой версии.\n').encode('utf8')
    readme += '\nВстроенная диагностика выключена по умолчанию. Команды OP2, локальные журналы и ZIP, фильтры и ограничения измерений: DIAGNOSTICS-V9.md. Точная текущая проверка off/on/reenter и связанные server/client exports находятся в evidence и evidence/runtime; GPU time и процент нагрузки объекта по FPS не заявляются.\n'.encode('utf8')
    entries={'README-REVIEW-V9.md':readme,'REVIEW-INSTRUCTIONS.md':args.review_readme,'CHECK-MATRIX.json':json_bytes(matrix),
        'DIAGNOSTICS-V9.md':ROOT/'docs/DIAGNOSTICS_V9.md',
        'mods/'+args.jar.name:args.jar,'source/'+source_zip.name:source_zip,'source/'+source_manifest_path.name:source_manifest_path,
        'worlds/First-set-review-v9-scene.zip':proof['scene_zip'],'mapping/first_set_scene_input.json':proof['scene_input'],
        'mapping/first_set_v9_gameplay_input.json':proof['gameplay_input'],'mapping/first_set_v9_gameplay_input_reopen.json':proof['gameplay_reopen_input'],
        'mapping/INPUTS.json':ROOT/'INPUTS.json','evidence/REVIEW_V9_PRIMARY_EVIDENCE.json':primary_manifest,
        **proof['dependencies']}
    for name in ['PROGRESS.md','TASK.md','RELEASE-STATUS.md','CHECK_MATRIX.md','TEST_MATRIX.md','README.txt']:entries['project/'+name]=ROOT/name
    for row in proof['gates']:
        path=Path(row['path']);key='evidence/'+path.name;require(key not in entries or entries[key]==path,'Evidence basename collision');entries[key]=path
    for row in read(primary_manifest)['files']:entries['evidence/runtime/'+Path(row['path']).name]=resolve(row['path'],ROOT)
    if args.alt_example:
        with zipfile.ZipFile(args.alt_example) as archive:require(archive.testzip() is None and 'pack.mcmeta' in archive.namelist(),'Invalid optional ALT example')
        entries['resourcepacks/ALT-example.zip']=args.alt_example
    if proof['source_zip']:
        entries['worlds/First-set-migrated-source-fixture.zip']=proof['source_zip'];entries['worlds/Source-coordinate-fixture.zip']=args.frozen_source;entries['mapping/source-review-input.json']=args.source_input
    if args.qa_jar:
        entries['TEST-ONLY-OPTIONAL/'+args.qa_jar.name]=args.qa_jar;entries['TEST-ONLY-OPTIONAL/README.txt']='Не требуется для обычной игры. Отдельный addon для изолированных developer QA проверок; не production mod.\n'.encode('utf8')
    navigation=prepare_kit_guides(entries,files)
    matrix['kit_guide_navigation']=navigation;entries['CHECK-MATRIX.json']=json_bytes(matrix)
    kit_path=args.output/(BASE+'-review-kit.zip');kit_records=write_zip(kit_path,entries)
    require(digest(args.jar)==proof['artifact_sha256'],'Production changed during packaging')
    require(source_files(ROOT)==files,'Full source topology changed during packaging')
    for row in source_records:
        path=ROOT/row['path'][len(SOURCE_PREFIX):]
        require(path.stat().st_size==row['bytes'] and digest(path)==row['sha256'],'Full source bytes changed after snapshot: '+str(path))
    result={'schema':'dreamwalker-review-v9-delivery-v1','status':'PASS_CURRENT_REVIEW_KIT_AND_FULL_SOURCE_BYTES_VERIFIED',
        'production_jar_sha256':proof['artifact_sha256'],'production_version':proof['version'],'source_archive':source_manifest,
        'review_kit':{'archive':kit_path.name,'sha256':digest(kit_path),'bytes':kit_path.stat().st_size,'file_count':len(kit_records),'files':kit_records},
        'check_matrix':matrix,'kit_guide_navigation':navigation,'output_directory':str(args.output),'user_review':'PENDING_USER_REVIEW','full_task_status':'NOT_READY_FULL_TASK',
        'source41_included':proof['source_zip'] is not None,'old_delivery_overwritten':False,'manifest_self_inclusion':False}
    final_manifest=args.output/(BASE+'-delivery-manifest.json');final_manifest.write_bytes(json_bytes(result))
    print(json.dumps({'status':result['status'],'kit':str(kit_path),'source':str(source_zip),'manifest':str(final_manifest),'user_review':'PENDING_USER_REVIEW','full_task_status':'NOT_READY_FULL_TASK'}))

if __name__=='__main__':
    try:main()
    except (ValueError,KeyError,FileNotFoundError) as failure:
        print(json.dumps({'status':'FAIL_V9_CURRENT_PACKAGE_GATE','error':str(failure),'user_review':'PENDING_USER_REVIEW','full_task_status':'NOT_READY_FULL_TASK'}),file=sys.stderr);raise SystemExit(1)
