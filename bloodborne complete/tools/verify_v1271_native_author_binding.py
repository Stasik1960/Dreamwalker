"""Verify the ordinary converter contains the exact native-tested author code."""
import hashlib,json,zipfile
from verify_v1271_final_readiness import ROOT,read,sha,write

def main():
    normal=ROOT/'build/libs/dreamwalker-v127-full-conversion-author-1.2.7.jar'
    qa=ROOT/'build/libs/dreamwalker-v127-full-conversion-author-resume-probe-1.2.7.jar'
    proof=ROOT/'build/v1271-conversion-resume-native5d/dw-conversion-resume-proof.json'
    data=read(proof);assert data['status'].startswith('PASS_NATIVE_CONVERTER')
    build=read(ROOT/'build/v1271-conversion-resume-probe-build5d/build-report.json')
    assert build['addonSha256']==sha(qa) and build['productionJarSha256']==data['productionJarSha256']
    members=[];prefix='dev/dreamwalker/bloodbornedw/conversion/FullMapConversionAuthor'
    with zipfile.ZipFile(normal) as n,zipfile.ZipFile(qa) as q:
        paths=sorted(name for name in n.namelist() if name.startswith(prefix) and name.endswith('.class'))
        assert paths and paths==sorted(name for name in q.namelist() if name.startswith(prefix) and name.endswith('.class'))
        for name in paths:
            assert n.read(name)==q.read(name),('Native tested author class differs from ordinary author',name)
            members.append({'path':name,'sha256':hashlib.sha256(n.read(name)).hexdigest()})
        assert json.loads(n.read('dw-conversion-binding.json'))['productionJarSha256']==data['productionJarSha256']
    source=ROOT/'src/conversion/java/dev/dreamwalker/bloodbornedw/conversion/FullMapConversionAuthor.java'
    report={'status':'PASS_NORMAL_AUTHOR_CLASSES_BYTE_IDENTICAL_TO_NATIVE_VERIFIED_AUTHOR',
        'productionJarSha256':data['productionJarSha256'],'normalAuthorSha256':sha(normal),'nativeQaAuthorSha256':sha(qa),
        'nativeConversionResumeProof':str(proof),'nativeConversionResumeProofSha256':sha(proof),
        'authorSource':str(source),'authorSourceSha256':sha(source),'classMembers':members,
        'sourceIdentityScope':'Actual archived source SHA after successful normal/probe builds; independently repeated clean-build byte equality is not claimed'}
    destination=ROOT/'build/final-city-fixes/author-class-native-binding.json';write(destination,report)
    acceptance_path=ROOT/'build/final-city-fixes/final-acceptance.json';acceptance=read(acceptance_path)
    acceptance['nativeConverterAuthorClassBinding']=str(destination);write(acceptance_path,acceptance)
    print(json.dumps(report))

if __name__=='__main__':main()
