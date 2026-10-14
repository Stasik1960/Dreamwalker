"""Record final local acceptance and copy the verified production artifacts."""
import json,shutil
from pathlib import Path
from xml.etree import ElementTree
from verify_v1271_final_readiness import ROOT,ordinary,read,sha,write

def main():
    out=ROOT/'build/final-city-fixes';release=ROOT.parent/'releases/Dreamwalker-BB/v12.7.1-city-fixes'
    jar=ROOT/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.1-city-fixes.jar';digest=sha(jar)
    xml=ROOT/'build/test-results/gametest/TEST-final-city-fixes-r5.xml';suite=ElementTree.parse(xml).getroot()
    tests=list(suite.iter('testcase'));assert len(tests)==148 and not list(suite.iter('failure')) and not list(suite.iter('error'))
    proof=ROOT/'build/runtime-client-comparison-fresh-final-reference-proof-r4/native-source-target-model-proof.json'
    binding=out/'native-model-proof-final-binding.json';b=read(binding);assert b['status']=='PASS' and b['targetProductionJarSha256']==digest and b['nativeSourceModelProofSha256']==sha(proof)
    performance=out/'performance-final5.json';p=read(performance);assert p['status'].startswith('PASS_FINAL') and p['productionJarSha256']==digest
    readiness=ROOT/'build/v1271-gallery/readiness-final5.json';r=read(readiness);assert r['status'].startswith('PASS_READ_ONLY') and r['productionJarSha256']==digest and r['discardedSourceCells']==0
    gallery=ROOT/'build/v1271-gallery/delivery-reopened-final5b.json';g=read(gallery);assert g['status']=='PASS' and g['productionReopenCompared'] and g['productionJarSha256']==digest
    pixels=ROOT/'docs/final-city-repair/geometry/final-gallery-native-pixel-review.json';v=read(pixels);assert str(v['status']).startswith('PASS')
    resume=ROOT/'build/v1271-conversion-resume-native5d/dw-conversion-resume-proof.json';c=read(resume)
    assert c['status']=='PASS_NATIVE_CONVERTER_ALIAS_REPLAY_TYPED_NBT_BANK_AND_SHIFT_GUARDS' and c['productionJarSha256']==digest and c['freshInstallations']==2 and c['exactReplays']==4
    assert not c['originalCityConverted']
    author=ROOT/'build/libs/dreamwalker-v127-full-conversion-author-1.2.7.jar';author_report=ROOT/'build/v1271-conversion-author-final5c/build-report.json'
    ar=read(author_report);assert ar['status']=='PASS_AUTHOR_BUILD' and ar['productionJarSha256']==digest and ar['addonSha256']==sha(author)
    copy_report=ROOT/'docs/v12.7.1/conversion/converter-input-copy.json';assert read(copy_report)['status'].startswith('PASS_LOCAL')
    result={'status':'PASS','scope':'Local mod, reused conversion preparation, native controlled-slice performance and verified gallery; complete original city not converted',
        'productionJarSha256':digest,'currentUnresolvedTestFailures':[],'wholeCityConverted':False,
        'gameTestXml':str(xml),'gameTestXmlSha256':sha(xml),'gameTestCount':148,
        'buildReport':str(out/'build-result.json'),'gameTestResult':str(out/'gametest-r5-result.json'),
        'nativeSourceModelProof':str(proof),'nativeSourceModelProofSha256':sha(proof),
        'nativeSourceModelProofProductionJarSha256':b['sourceProductionJarSha256'],'nativeProofCompatibilityReport':str(binding),
        'nativePerformanceReport':str(performance),'nativeUnloadProbe':str(performance),
        'gallerySavedReopenProof':str(gallery),'galleryNativePixelReview':str(pixels),
        'nativeConversionResumeProof':str(resume),'converterAuthorBuildReport':str(author_report),'converterInputCopyReport':str(copy_report),
        'preparationReadiness':str(readiness),'altArtworkLoaded':0,'areaRadiusLimit':300,
        'nativeMeanFps':p['groups']['native']['finalMeanOfSixViewsFps'],'baselineV127NativeMeanFps':p['groups']['native']['baselineMeanOfSixViewsFps'],
        'sodiumMeanFps':p['groups']['sodium']['finalMeanOfSixViewsFps'],'baselineV127SodiumMeanFps':p['groups']['sodium']['baselineMeanOfSixViewsFps']}
    write(out/'final-acceptance.json',result)
    release.mkdir(parents=True,exist_ok=True)
    for source in [jar,ROOT/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.1-city-fixes-sources.jar']:
        target=release/source.name;shutil.copyfile(source,target);assert sha(source)==sha(target)
    converted=release/'Conversion';converted.mkdir(exist_ok=True)
    for source in [author,ROOT/'docs/v12.7.1/conversion/SOURCE-AND-CONVERSION-RU.txt',copy_report,author_report,resume]:
        target=converted/source.name;shutil.copyfile(source,target);assert sha(source)==sha(target)
    evidence=release/'Verification-Final';evidence.mkdir(exist_ok=True)
    for source in [out/'final-acceptance.json',performance,binding,xml,proof,pixels,gallery,readiness,resume]:
        target=evidence/source.name;shutil.copyfile(source,target);assert sha(source)==sha(target)
    print(json.dumps({k:v for k,v in result.items() if k in ('status','productionJarSha256','gameTestCount','wholeCityConverted','nativeMeanFps','sodiumMeanFps')}))

if __name__=='__main__':main()
