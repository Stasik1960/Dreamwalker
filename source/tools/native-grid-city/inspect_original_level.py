import importlib.util,gzip
from pathlib import Path
spec=importlib.util.spec_from_file_location('migration',Path(__file__).with_name('migrate.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
m.control_check();binding=m.read(m.DEFAULT_PLAN/'source-binding.json');path=Path(binding['source'])/'level.dat'
with gzip.open(path,'rb') as stream:raw=stream.read(1048577)
m.require(len(raw)<=1048576,'Original level metadata exceeds 1MiB bound');root=m.compound(m.decode_nbt(raw).root);data=m.compound(root['Data']);generation=m.compound(data.get('WorldGenSettings',m.Tag(10,{})));dims=m.compound(generation.get('dimensions',m.Tag(10,{})))
report={'source':str(path),'sourceSha256':m.sha(path),'rawBytes':len(raw),'Spawn':[data.get(k,m.Tag(3,0)).value for k in ('SpawnX','SpawnY','SpawnZ')],'DataVersion':data.get('DataVersion',m.Tag(3,-1)).value,'generators':{key:m.plain_tag(m.compound(value).get('generator',m.Tag(10,{}))) for key,value in dims.items()},'worldScan':False,'worldWrites':0}
m.atomic_json(m.ROOT/'research'/'original-level-spawn-audit.json',report)
