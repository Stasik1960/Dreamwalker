import sys
from common import *
sys.path.insert(0,str(PROJECT/'tools/city-streaming-20261018'))
from world_io import read_nbt
result={}
for name,world in [('source',SOURCE),('working',WORLD)]:
 data=read_nbt(world/'level.dat').root.value['Data'].value
 result[name]={key:data[key].value for key in ['SpawnX','SpawnY','SpawnZ']}
write(OUT/'save-spawn.json',result)
