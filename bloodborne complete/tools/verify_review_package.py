"""Verify optional authoring QA code is absent from the ordinary production JAR."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[1]


def verify(production,qa):
    with zipfile.ZipFile(production) as archive:
        names=archive.namelist();metadata=json.loads(archive.read('fabric.mod.json'))
        forbidden=[name for name in names if name.startswith('dev/dreamwalker/bloodbornedw/review/') or name.startswith('review-scene-')
                   or name in {'dev/dreamwalker/bloodbornerp/lamp/V9GameplayReviewBootstrap.class','dev/dreamwalker/bloodbornerp/client/RpV9ClientReviewProbe.class'}
                   or name.startswith('dev/dreamwalker/bloodbornerp/lamp/V9GameplayReviewBootstrap$')
                   or name.startswith('dev/dreamwalker/bloodbornerp/client/RpV9ClientReviewProbe$')]
        assert metadata['id']=='bloodborne_dw',metadata['id']
        assert not forbidden,'Production JAR includes QA authoring:'+str(forbidden)
        assert 'ReviewSceneBootstrap' not in json.dumps(metadata),'Production metadata includes QA entrypoint'
        production_version=metadata['version']
    with zipfile.ZipFile(qa) as archive:
        names=archive.namelist();metadata=json.loads(archive.read('fabric.mod.json'))
        assert metadata['id']=='bloodborne_dw_review' and metadata['environment']=='*'
        expected_main=['dev.dreamwalker.bloodbornedw.review.ReviewSceneBootstrap']
        if production_version=='0.1.0-prototype.4':expected_main.extend(['dev.dreamwalker.bloodbornerp.lamp.V9GameplayReviewBootstrap','dev.dreamwalker.bloodbornedw.review.DiagnosticsReviewBootstrap'])
        assert metadata['entrypoints']['main']==expected_main
        assert metadata['entrypoints']['client']==['dev.dreamwalker.bloodbornedw.review.ReviewClientBootstrap']
        assert 'dev/dreamwalker/bloodbornedw/review/ReviewSceneBootstrap.class' in names
        assert 'dev/dreamwalker/bloodbornedw/review/ReviewClientBootstrap.class' in names
        assert not any(name.startswith('assets/') or name.startswith('dev/potomy/') for name in names),'QA add-on must not redistribute another production asset/code copy'
    return {'schema':'dreamwalker-review-package-verification-v1','status':'PASS','production':str(production.resolve()),
            'production_sha256':hashlib.sha256(production.read_bytes()).hexdigest(),'qa':str(qa.resolve()),
            'qa_sha256':hashlib.sha256(qa.read_bytes()).hexdigest(),'production_contains_qa':False,
            'qa_registry_namespace':'bloodborne_dw_review','qa_server_only':False,'qa_client_requires_explicit_isolated_marker':True,'qa_is_optional_separate_addon':True}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--production-jar',type=Path,required=True);parser.add_argument('--qa-jar',type=Path,required=True)
    parser.add_argument('--report',type=Path,default=ROOT/'reports/REVIEW_PACKAGE_VERIFICATION.json');args=parser.parse_args()
    result=verify(args.production_jar,args.qa_jar)
    args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print(json.dumps(result))


if __name__=='__main__':main()
