"""Complete the manifest-only shard inventory; does not read maps or SQLite."""
import migrate as m
plan=m.ROOT/'research/native-grid-plan';path=plan/'context-extension/boundary-dependency-inventory.json';report=m.read(path);manifest=m.read(report['sourceBinding']['copyManifest']);shards=set()
for chunk in report['chunks']:
    rel='data/bloodborne_dw_owner_shards/owners-%d.%d.nbt'%(chunk['x'],chunk['z'])
    if rel in manifest['files']:shards.add(rel)
report['missingDependencyShards']=len(shards);report['shards']=[{'path':rel,'sha256':manifest['files'][rel]['sha256']} for rel in sorted(shards)];report['shardNaming']='data/bloodborne_dw_owner_shards/owners-<cx>.<cz>.nbt';report['shardEnumeration']='exact immutable manifest entries for the finite missing chunk set'
door_path=m.ROOT/'research/geometry-supplement/door-footprints.json';doors={r['key']:r for r in m.read(door_path)['poses']};original={tuple(v) for v in m.read(plan/'context-extension/inventory.json')['allowedChunks']};needed={(v['x'],v['z']) for v in report['chunks']}
unproved=[]
for owner in report['owners']:
    if owner['poseFootprintAvailable']:continue
    door=doors.get(owner['sourcePose'])
    if door is None:unproved.append(owner['uuid']);continue
    m.require({r['open'] for r in door['states']}=={False,True},'Protected openable lacks both exact states')
    x,y,z=owner['root'];footprint={(x+dx>>4,z+dz>>4) for dx,dy,dz in door['cells']}
    m.require(footprint.issubset(original|needed),'Protected door needs another exact dependency chunk')
    owner['completeDoorBothStateFootprint']={'relativeCells':door['cells'],'states':door['states'],'allChunksCovered':True}
report['ownersWithoutAnyCompleteFootprintProof']=unproved;report['protectedDoorFootprintProof']={'path':str(door_path),'sha256':m.sha(door_path),'coveredWithoutNewDependencyChunks':True}
m.atomic_json(path,report);print(m.canonical({k:report[k] for k in ('insideReferencedRetainedRealOwners','boundaryOwners','rootOutsideCopyOwners','missingDependencyChunks','missingDependencyShards','missingCachedChunks','ownersWithoutExportedPoseFootprint','seconds')}))
