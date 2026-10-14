"""Archive a verified reused city preparation; never author or convert a world."""
import argparse, hashlib, json, shutil, zipfile
from pathlib import Path
from package_gallery_v1271 import archive
from run_v11_server import files_without_links
from verify_v1271_final_readiness import ROOT,ordinary,read,sha,write

NAME='Dreamwalker-BB-V12.7.1-PreparedCity'

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--production-jar',type=Path,required=True);p.add_argument('--readiness',type=Path,required=True)
    p.add_argument('--preparation',type=Path,default=ROOT/'build/v1271-city-preparation')
    p.add_argument('--native-resume-proof',type=Path,required=True)
    p.add_argument('--author-binding-report',type=Path,default=ROOT/'build/final-city-fixes/author-class-native-binding.json')
    p.add_argument('--evidence',type=Path,action='append',default=[])
    p.add_argument('--destination',type=Path,default=ROOT.parent/'releases/Dreamwalker-BB/v12.7.1-city-fixes');a=p.parse_args()
    prep=ordinary(a.preparation);dest=ordinary(a.destination);readiness_path=ordinary(a.readiness)
    ready=read(readiness_path);digest=sha(ordinary(a.production_jar))
    assert ready['status']=='PASS_READ_ONLY_REUSED_PREPARATION_AND_NATIVE_RESOURCE_BINDING' and ready['productionJarSha256']==digest
    assert ready['inputFilesUnchanged'] and ready['discardedSourceCells']==0 and not ready['fullWorldConverted']
    manifest=read(prep/'runtime/manifest.json')
    assert sha(prep/'source-plan.sqlite')==ready['sourcePlanSha256']==manifest['sourcePlanSha256']
    assert sha(prep/'runtime/manifest.json')==ready['runtimeManifestSha256']
    assert sha(prep/'decision-frequency-proof.json')==ready['independentDecisionFrequencyProofSha256']
    for name,value in ready['runtimeFiles'].items():assert sha(prep/'runtime'/name)==value==manifest['runtimeFiles'][name]
    resume_path=ordinary(a.native_resume_proof);resume=read(resume_path)
    assert resume['status']=='PASS_NATIVE_CONVERTER_ALIAS_REPLAY_TYPED_NBT_BANK_AND_SHIFT_GUARDS' and resume['productionJarSha256']==digest
    assert resume['fixtureOnly'] and resume['freshInstallations']==2 and resume['exactReplays']==4
    assert resume['durableCursorPhase0Line0Verified'] and not resume['originalCityOpened'] and not resume['originalCityConverted']
    assert len(resume['cases'])==2 and all(row['status']=='PASS' for row in resume['cases'])
    assert any(row.get('alteredSourceShiftRejected') for row in resume['cases']) and any(row.get('alteredArtBankRejected') for row in resume['cases'])
    copy_path=ordinary(ROOT/'docs/v12.7.1/conversion/converter-input-copy.json');converter=read(copy_path)
    assert converter['status']=='PASS_LOCAL_CONVERTER_INPUTS_COPIED_AND_VERIFIED' and converter['productionJarSha256']==digest
    assert converter['sourcePlanSha256']==ready['sourcePlanSha256'] and converter['runtimeManifestSha256']==ready['runtimeManifestSha256']
    assert converter['readinessSha256']==sha(readiness_path) and converter['authorBoundAndVerified']
    build_path=ordinary(Path(converter['authorBuildReport']));build=read(build_path)
    assert sha(build_path)==converter['authorBuildReportSha256'] and build['status']=='PASS_AUTHOR_BUILD'
    assert build['productionJarSha256']==digest and build['productionJarUnchanged'] and not build['worldConversionAttempted']
    author=ordinary(Path(build['addon']));author_sha=sha(author)
    assert author_sha==build['addonSha256']==converter['authorJarSha256']
    for row in converter['files'].values():assert sha(ordinary(Path(row['destination'])))==row['sha256']
    binding_path=ordinary(a.author_binding_report);binding=read(binding_path)
    assert binding['status']=='PASS_NORMAL_AUTHOR_CLASSES_BYTE_IDENTICAL_TO_NATIVE_VERIFIED_AUTHOR'
    assert binding['productionJarSha256']==digest and binding['normalAuthorSha256']==author_sha
    assert binding['nativeConversionResumeProofSha256']==sha(resume_path)
    assert ordinary(Path(binding['nativeConversionResumeProof']))==resume_path
    source_path=ordinary(Path(binding['authorSource']));assert sha(source_path)==binding['authorSourceSha256']
    with zipfile.ZipFile(author) as compiled:
        actual={name for name in compiled.namelist() if name.startswith('dev/dreamwalker/bloodbornedw/conversion/FullMapConversionAuthor') and name.endswith('.class')}
        expected={row['path']:row['sha256'] for row in binding['classMembers']}
        assert len(expected)==len(binding['classMembers']) and set(expected)==actual
        assert all(hashlib.sha256(compiled.read(name)).hexdigest()==value for name,value in expected.items())
    dest.mkdir(parents=True,exist_ok=True)
    entries=[(prep/'source-plan.sqlite',NAME+'/source-plan.sqlite'),(prep/'decision-frequency-proof.json',NAME+'/decision-frequency-proof.json')]
    entries += [(path,NAME+'/runtime/'+path.relative_to(prep/'runtime').as_posix()) for path in files_without_links(prep/'runtime')]
    proof_root=ROOT/'docs/v12.7.1/conversion'
    for path in sorted(proof_root.glob('*')):
        if path.is_file():entries.append((ordinary(path),NAME+'/verification/'+path.name))
    entries.append((readiness_path,NAME+'/verification/readiness.json'))
    supplemental=[resume_path,build_path,binding_path,source_path,*[ordinary(path) for path in a.evidence]]
    entries += [(path,NAME+f'/verification/supplemental/{i:02d}-'+path.name) for i,path in enumerate(supplemental,1)]
    record=archive(dest/(NAME+'.zip'),entries)
    note=(f'Dreamwalker BB v12.7.1 — готовая подготовка исходного города\n\n'
          f'Производственный JAR SHA-256: {digest}\n'
          f'SQL SHA-256: {ready["sourcePlanSha256"]}\n'
          f'Runtime manifest SHA-256: {ready["runtimeManifestSha256"]}\n\n'
          'Использованы уже подготовленные массивы v12.7; исходный мир и взвешенный выбор моделей не пересоздавались. '
          'Независимо проверены все861360 подготовленных секций и7983 решения: реально удаляемых исходных ячеек0. '
          'Невидимые исходные модели сохраняют физику/свет; каталог скрывает служебные совместимые типы. '
          'ALT хранит возможность переключения; отдельных ALT-изображений0.\n\n'
          'Этот архив содержит подготовку, а не уже конвертированный полный город. '
          'Нативная визуальная проверка и производительность финального мода подтверждаются отдельными отчётами релиза. '
          'Конвертация выполняется штатным tools/stage_v1271_city_conversion.py из полных исходников финального мода. '
          'Сначала запуск без --execute проверяет план; --execute создаёт отдельную производную копию мира. '
          'Оригинальные архивы не изменяются. Minecraft затем продолжает конвертацию стандартным сервисом полного города.\n\n'
          'Исходные архивы, указанные в runtime/manifest.json, должны оставаться доступны на локальномC:\n'+
          ''.join(f'{kind}: {path}\nSHA-256: {manifest["sourceHashes"][kind]}\n' for kind,path in manifest['sourceArchivePaths'].items()))
    (dest/'PREPARED-CITY-README-RU.txt').write_text(note,encoding='utf8')
    write(dest/'prepared-city-release-manifest.json',{'status':'PASS_VERIFIED_PREPARATION_PACKAGED_REQUIRES_SEPARATE_NATIVE_RELEASE_ACCEPTANCE_AND_CONVERSION',
          'productionJarSha256':digest,'sourcePlanSha256':ready['sourcePlanSha256'],'runtimeManifestSha256':ready['runtimeManifestSha256'],
          'discardedSourceCells':0,'sourcePreparationReused':True,'fullWorldConverted':False,'originalSourceArchivesModified':False,
          'converterAuthorJarSha256':author_sha,'converterAuthorBuildReportSha256':sha(build_path),
          'converterNativeResumeFixtureProofSha256':sha(resume_path),'converterFixtureOriginalCityConverted':False,
          'converterAuthorClassNativeBindingSha256':sha(binding_path),'converterAuthorSourceSha256':binding['authorSourceSha256'],
          'supplementalVerificationSha256':{path.name:sha(path) for path in supplemental},'archive':record})

if __name__=='__main__':main()
