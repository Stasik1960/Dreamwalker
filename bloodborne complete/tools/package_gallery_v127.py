"""Package this new gallery only after native client/reopen and disk proofs."""
import argparse, json, shutil, subprocess
from pathlib import Path
from package_v127_release import ROOT,REPO,archive,files,sha,read,ordinary
p=argparse.ArgumentParser()
for name in ('world','capture-run','reopen-run','disk-before','disk-capture','disk-reopen','server-report','memory-report','reopen-memory-report','pixel-review','destination'):
    p.add_argument('--'+name,type=Path,required=True)
a=p.parse_args();world=a.world.resolve();dest=a.destination.resolve()
assert world.is_relative_to(ROOT/'build') and dest.is_relative_to(REPO/'releases/Dreamwalker-BB') and not dest.exists()
assert ordinary(world) and ordinary(dest)
production=ROOT/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7-final.jar';digest=sha(production)
assert digest=='b98c752b797ef07c5acf1d4852bda83eec52717fed798d4ec76860b4f6972850'
manifest=read(world/'gallery-manifest.json');assert manifest['productionJarSha256']==digest and len(manifest['exhibits'])==644
capture=read(a.capture_run/'v127-gallery-benchmark.json');reopen=read(a.reopen_run/'v127-gallery-benchmark.json')
for run in (capture,reopen):
    assert run['status']=='PASS_ENGINE_GALLERY_BENCHMARK_REQUIRES_ROOT_REVIEW_AND_SAVE_VERIFICATION' and run['normalSaveShutdownRequested']
    assert run['productionJarSha256']==digest and len(run['frames'])==11
    assert run['galleryCreated'] is True
assert capture['actualWorldEditCommandProof']['status']=='PASS_ACTUAL_WORLDEDIT_PLAYER_COMMANDS'
assert len(capture['actualWorldEditCommandProof']['commands'])==18
for diskpath in (a.disk_before,a.disk_capture,a.disk_reopen):
    d=read(diskpath);assert d['status']=='PASS' and d['productionJarSha256']==digest and d['verifiedExhibits']==8737
assert read(a.disk_capture)['productionReopenCompared'] and read(a.disk_reopen)['productionReopenCompared']
server=read(a.server_report);assert server['status']=='PASS' and server['exit_code']==0 and not server['errors']
assert {r['id'] for r in server['extraMods']}=={'worldedit'}
mem=read(a.memory_report);remem=read(a.reopen_memory_report)
for value in (mem,remem):assert value['status']=='PASS_PROCESS_OBSERVATION' and value['exitCode']==0 and value['productionJarSha256']==digest
pixels=read(a.pixel_review);assert pixels['status']=='PASS_ROOT_REVIEWED_22_NATIVE_FRAMES' and pixels['productionJarSha256']==digest
warm=[r for r in capture['stationMeasurements'] if r['kind']=='WARM_STATIONARY'];assert len(warm)==11
for row in warm:
    assert row['wallSeconds']>=20 and row['actualWorldRenderFrameIntervals']['sampleCount']>=10
    assert row['actualWindowFocusSamples']['unfocusedRenderCallbacks']==0 and row['actualWindowFocusSamples']['iconifiedRenderCallbacks']==0
assert subprocess.run(['git','diff','--quiet','f1000f341b19279251df45a3c82f517e0c6b6c2d','HEAD','--','bloodborne complete/src','bloodborne complete/build.gradle','bloodborne complete/gradle.properties','bloodborne complete/settings.gradle'],cwd=REPO).returncode==0
dest.mkdir(parents=True)
zipname='Dreamwalker-BB-V12.7-Compact-Gallery.zip'
entries=[(path,'Dreamwalker-BB-V12.7-Compact-Gallery/'+path.relative_to(world).as_posix()) for path in files(world) if path.name!='session.lock']
galleryaudit=archive(dest/zipname,entries)
evidence=[a.disk_before,a.disk_capture,a.disk_reopen,a.server_report,a.memory_report,a.reopen_memory_report,a.pixel_review]
evidence += [ROOT/'docs/v12.7-gallery/black-blocks-diagnosis.json',ROOT/'docs/v12.7-gallery/BLACK-BLOCKS-DIAGNOSIS.txt']
evidence += [a.capture_run/'v127-gallery-benchmark.json',a.reopen_run/'v127-gallery-benchmark.json',a.capture_run/'launch-console.log',a.reopen_run/'launch-console.log']
evidence += [path for run in (a.capture_run,a.reopen_run) for path in files(run/'screenshots')]
evidence += [a.capture_run/'saves/prototype-fixture/gallery-worldedit-qa.json']
verification=archive(dest/'Dreamwalker-BB-V12.7-Gallery-Verification.zip',[(path,path.relative_to(ROOT).as_posix()) for path in evidence])
generator=[path for path in files(ROOT/'tools/gallery-v127')]
generator += [ROOT/'tools'/name for name in ('prepare_gallery_v127_input.py','build_gallery_v127_addon.py','prepare_gallery_v127_checks.py','verify_gallery_v127.py','finalize_gallery_v127_world.py','normalize_gallery_v127_manifest.py','prepare_gallery_v127_capture.py','monitor_gallery_v127_process.py','prepare_gallery_v127_delivery.py','run_gallery_v127_preflight.py','package_gallery_v127.py','publish_gallery_v127.py','record_gallery_v127_delivery_review.py')]
sourceaudit=archive(dest/'Dreamwalker-BB-V12.7-Gallery-Tools.zip',[(path,path.relative_to(ROOT).as_posix()) for path in generator])
for name in ('README-RU.txt','GALLERY-INDEX.csv'):shutil.copyfile(world/name,dest/name)
lines=['Dreamwalker BB V12.7 — актуальная галерея','',
       'Minecraft 1.20.1, Java17, Fabric Loader0.16.10, Fabric API0.92.9+1.20.1, GeckoLib4.4.9; WorldEdit7.2.15.',
       'Пользовательская сборка из mods.zip и оптимизационные моды не установлены. Производственный JAR не изменён.',
       '644 основные платформы:621 размещаемый предмет и23 предмета в рамках. 22 образца сборок/дверей, рядом городской фрагмент8071 модовых корней. Размер256×292блока.',
       'PASS: клиент,18 команд WorldEdit по числовым ID и целым объектам, сохранение/reopen,8737 объектов на диске,16736 проверок геометрии; финальный сервер с модом и WorldEdit без QA-дополнения.',
       'NOT_RUN: физические мышь/клавиатура; полная исходная карта. Авторский экспорт коллизий не предоставлен/не применён.',
       '','Измерения: RTX3070 Laptop;1280×720;дальность8;симуляция5;heap4ГиБ;VSync и ограничение120FPS. Прогретые интервалы по20секунд; без принудительногоGC.',
       'FPS и тики ниже относятся к настоящему клиенту с интегрированным сервером, не ко всей исходной карте.']
for row in warm:
    frame=row['actualWorldRenderFrameIntervals'];tick=row['actualIntegratedServerTickDurations']
    lines.append(f"{row['station']}: FPS={frame.get('effectiveFramesPerSecond',1000/frame['meanMilliseconds']):.1f}; frame p95={frame['p95Milliseconds']:.1f}мс; tick p95={tick['p95Milliseconds']:.2f}мс.")
heap=[r['usedHeapBytesObserved'] for r in capture['nativeHeapSnapshots'] if 'usedHeapBytesObserved' in r]
lines += ['',f"Пик рабочей памяти процесса: {mem['peakWorkingSetBytes']/1024**3:.2f}ГиБ. Пик private commit: {mem['peakPrivateCommitBytes']/1024**3:.2f}ГиБ. Это весь Java-процесс; private commit не равен занятой физической RAM.",
          f"Наблюдавшийся heap JVM: {min(heap)/1024**3:.2f}–{max(heap)/1024**3:.2f}ГиБ при максимуме4ГиБ; принудительныйGC не использован.",
          'Память JVM,GC,фокус окна,полные интервалы,повторный запуск и журналы — в архиве Verification.',
          'Возможные ограничения FPS и известные предупреждения указаны в acceptance.json. Гарантии60FPS во всех сценах нет.',
          'FAIL (известный дефект мода): часть новых моделей почернела. Причина локализована: свет из непрозрачной клетки корня применяется ко всей модели. Исправление не внесено. Пользователь запросил эту галерею для поиска других проблем; её выдача не означает исправление освещения. Все236 сравнивавшихся исходных текстур совпадают побайтно. Подробности — BLACK-BLOCKS-DIAGNOSIS.txt в Verification.',
          '', 'Установка: распаковать папку мира в saves; установить финальный JAR V12.7 из релиза bb-v12.7-final и зависимости. QA-дополнение не нужно.',
          '/function dw_gallery:help — навигация; :barrier/:light — служебные образцы; :night/:day — освещение; :worldedit — отдельная тестовая площадка.',
          'SHA-256 производственного JAR: '+digest]
(dest/'REPORT-RU.txt').write_text('\n'.join(lines)+'\n',encoding='utf-8-sig')
doc=ROOT/'docs/v12.7-gallery';doc.mkdir(parents=True,exist_ok=True)
fatal=[];warnings=[];logged=[]
for run in (a.capture_run,a.reopen_run):
    log=(run/'launch-console.log').read_text(encoding='utf8',errors='replace')
    assert 'All dimensions are saved' in log and 'OutOfMemoryError' not in log and 'FAIL_ENGINE_GALLERY' not in log
    logged += [{'run':run.name,'line':line} for line in log.splitlines() if '/ERROR]' in line]
    warnings += [{'run':run.name,'line':line} for line in log.splitlines() if '/WARN]' in line]
accepted={'schema':'dreamwalker-v127-gallery-acceptance-v1','status':'PASS_GALLERY_ACCEPTED','productionJarSha256':digest,
          'galleryCreated':True,'originalMapChanged':False,'workingNewTypes':541,'mainPlatforms':644,'reviewPlatforms':22,'cityOwners':8071,
          'worldEdit':'PASS_ACTUAL_18_PLAYER_COMMANDS','nativeCaptureAndReopen':'PASS','savedObjects':'PASS_8737_EXACT_TYPED_SAVE_REOPEN','pixelReview':'PASS_22_NATIVE_FRAMES',
          'finalRuntimeExtraMods':['worldedit'],'userModpackInstalled':False,'optimizationModsInstalled':False,'qaNeededForWorld':False,
          'performanceMeasurements':warm,'wholeProcessMemory':{k:v for k,v in mem.items() if k!='samples'},
          'loggedErrorLines':logged,'warnings':warnings,'fatalGameErrors':fatal,'physicalInput':'NOT_RUN',
          'visualFindings':pixels.get('visualFindings',[]),'visualCorrectness':'FAIL_KNOWN_LIGHTING_RENDER_REGRESSION','releasePurpose':'ISSUE_DISCOVERY_WITH_DISCLOSED_KNOWN_DEFECT',
          'cameraControl':'QA locks the planned camera at client ticks and GameRenderer.renderWorld HEAD; GLFW requests window focus before each station. Native focus samples establish actual focus; legacy raw focusControl=false text is superseded by this explicit control description.',
          'knownLimits':['Dense scenes remain expensive; measurements do not establish vanilla-equivalent cost or universal60FPS.','Client startup includes costly model/state loading.','Whole original source city has not been converted or benchmarked.','Native automated gameplay; physical keyboard/mouse not used.'],
          'correctedQaAttempts':['Author generator unused stair art_bank removed.','Command prefix in QA corrected.','QA player moved outside incoming collision footprint.'],
          'evidence':[{'path':str(path.relative_to(ROOT)),'sha256':sha(path)} for path in evidence]}
for path in (doc/'acceptance.json',dest/'acceptance.json'):path.write_text(json.dumps(accepted,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
shutil.copyfile(dest/'REPORT-RU.txt',doc/'REPORT-RU.txt')
release={'status':'PASS_GALLERY_ACCEPTED','productionJarSha256':digest,'galleryZipSha256':sha(dest/zipname),'galleryArchive':galleryaudit,'verificationArchive':verification,'toolsArchive':sourceaudit,
         'files':[{'name':path.name,'bytes':path.stat().st_size,'sha256':sha(path)} for path in sorted(dest.iterdir()) if path.is_file()]}
(dest/'gallery-release-manifest.json').write_text(json.dumps(release,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
(dest/'SHA256SUMS.txt').write_text(''.join(sha(path)+'  '+path.name+'\n' for path in sorted(dest.iterdir()) if path.is_file()),encoding='ascii')
print(json.dumps({'status':'PASS_GALLERY_PACKAGED','destination':str(dest),'galleryZipSha256':release['galleryZipSha256']}))
