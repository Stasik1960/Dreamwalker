import hashlib,json,os,sys,zipfile,math,importlib.util
from pathlib import Path
PROJECT=Path(__file__).resolve().parents[2]
ROOT=PROJECT.parent
RES=PROJECT/'src/architecture/resources'
PREVIOUS=ROOT/'deliverables/block-fixes-20261020'
PREVIOUS_OUT=ROOT/'research/block-fixes-20261020'
OUT=ROOT/'research/block-fixes-2-20261020'
DELIVERY=ROOT/'deliverables/block-fixes-2-20261020'
BASE=PREVIOUS/'game/dreamwalker-bb-materials.1-streaming.7-blocks.1.jar'
SOURCE_WORLD=PREVIOUS/'map/bloodborne-blocks-fixed'
WORLD=DELIVERY/'map/bloodborne-blocks-fixed-2'
VERSION='0.1.0-v12.7.4-owner32-static-city.6-chunk-mesh.3-materials.1-streaming.7-blocks.2'
GAME='dreamwalker-bb-materials.1-streaming.7-blocks.2.jar'
JAVA_HOME=Path(r'C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma')
JAVA=JAVA_HOME/'bin/java.exe'
OUT.mkdir(parents=True,exist_ok=True)
spec=importlib.util.spec_from_file_location('repair_common_v1',PROJECT/'tools/block-fixes-20261020/common.py');legacy=importlib.util.module_from_spec(spec);spec.loader.exec_module(legacy)
for name in ['read','write','sha','ordinary','files','model_path','model','bounds','turn']:globals()[name]=getattr(legacy,name)
def log_to(name):
 sys.stdout=(OUT/(name+'.log')).open('w',encoding='utf8',buffering=1);sys.stderr=sys.stdout
def backup(p):
 dest=OUT/'source-before'/p.relative_to(PROJECT)
 if not dest.exists() and p.exists():dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(p.read_bytes())
