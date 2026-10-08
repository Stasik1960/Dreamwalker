"""Prepare current typed scene/client proof, without modifying a game profile."""
from pathlib import Path
import argparse,json,hashlib
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--jar',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--diagnostics-review',action='store_true',help='Run actual ordinary placement/model acknowledgement and OFF/ON/OFF diagnostics in the isolated client')
args=parser.parse_args()
scene=json.loads((ROOT/'tools/first_set_scene_v9_input.json').read_text(encoding='utf-8'))
registry=sorted({'bloodborne_dw:'+r['kind'] for r in scene['objects']})
assert len(registry)==17
marker={'schemaVersion':1,'guard':'ISOLATED_SAVED_REVIEW_CLIENT_ONLY','worldName':'prototype-fixture',
        'productionJarSha256':hashlib.sha256(args.jar.read_bytes()).hexdigest(),'maxClientTicks':7200,
        'expectedRegistryIds':registry,'expectedBlocks':[{'pos':r['root'],'id':'bloodborne_dw:'+r['kind']} for r in scene['objects']],
        'sceneId':scene['sceneId'],'manualVisualAcceptance':'NOT_RUN'}
if args.diagnostics_review:
    marker['diagnosticsReview']={'root':[0,64,4],'seconds':20,'windowTicks':100}
args.output.parent.mkdir(parents=True,exist_ok=True)
args.output.write_text(json.dumps(marker,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'registryFamilies':len(registry),'expectedRoots':len(marker['expectedBlocks']),'productionSha256':marker['productionJarSha256']}))
