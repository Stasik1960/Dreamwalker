"""Count actual declared registration state products and eager immutable data.

Read-only: counts are not a JVM heap estimate. Native histogram remains required.
"""
import collections,json
from v127_visual_export import ROOT,RES,OUT,write_json

def key(value):return json.dumps(value,sort_keys=True,separators=(',',':'))
def main():
    catalogue=json.loads((RES/'bloodborne_dw/remaining-catalogue.json').read_text(encoding='utf-8'))
    aliases=json.loads((RES/'bloodborne_dw/imported-type-aliases.json').read_text(encoding='utf-8'))
    full=json.loads((RES/'bloodborne_dw/imported-full-map-v127.json').read_text(encoding='utf-8'))
    compact=json.loads((RES/'bloodborne_dw/imported-compact-v127.json').read_text(encoding='utf-8'))['types']
    functional=json.loads((RES/'bloodborne_dw/imported-stair-types.json').read_text(encoding='utf-8'))
    banked={g['primaryRegistryId'].split(':')[1] for g in aliases['groups'] if g.get('poseCount',0)>16}|{g['registryId'].split(':')[1] for g in full['groups']}
    compacted={r['registryId'].split(':')[1] for r in compact};stairs={r['path'] for r in functional['stairTypes']};ladders={r['path'] for r in functional['ladderTypes']}
    objects=[];parts=[];bounds=[];rotations=[];same=0;poses=0
    for row in catalogue['objects']:
        p=row['path'];spec=json.loads((RES/'bloodborne_dw/composite'/f'{p}.json').read_text(encoding='utf-8'))
        factor=1;kind='CompositeRootBlock'
        if p=='source_original_tree':kind='SourceOriginalTreeBlock'
        elif p in compacted:kind='ImportedCompactBlock';factor=2
        elif p in stairs:kind='ImportedStairBlock';factor=4 if p=='source_stairs_polished_deepslate_stairs_bottom' else 2
        elif p in ladders:kind='ImportedLadderBlock';factor=2
        elif p in banked:kind='ImportedArtworkBlock';factor=30 if p=='source_assembly_brick_wall' else 10 if p=='source_assembly_deepslate_brick_wall' else 8
        objects.append({'registryId':spec['id'],'class':kind,'stateProduct':512*factor,'declaredPoses':len(spec['variants']),'factorAboveBase512':factor})
        for variant in spec['variants']:
            closed=variant['poses']['closed'];opened=variant['poses'].get('open',closed);same+=int(opened==closed)
            for pose in [closed]+([] if opened==closed else [opened]):
                poses+=1;parts.extend(key(r) for r in pose['parts'])
                for field in ('collision','selection'):bounds.extend(key(r) for r in pose.get(field,[]))
                rotations.extend(key(r) for r in pose.get('simpleCollisionByRotation',[]))
    all_shapes=set();even_shapes=set();odd_shapes=set();all_rows=even_rows=0
    for row in compact:
        for pose in row['poses']:
            values=row['shapes'][str(pose)];assert len(values)==8
            for yaw,value in enumerate(values):
                all_rows+=1;c=value['collision'];a=value['aim'];shapes=[key(c)]+([] if c else [key(a)])
                all_shapes.update(shapes)
                (odd_shapes if yaw&1 else even_shapes).update(shapes)
                if not yaw&1:even_rows+=1
    counts=collections.Counter(r['class'] for r in objects);states=collections.Counter()
    for r in objects:states[r['class']]+=r['stateProduct']
    # Seven protected composite prototype registrations are added by the
    # existing initializer; three glazing types multiply the base by3.
    protected_states=4*512+3*1536
    report={'status':'PASS_STATIC_COUNTS_NOT_HEAP_ESTIMATE','registeredNewCompositeTypes':len(objects),'newStateProducts':sum(states.values()),
        'protectedOriginalCompositeStateProducts':protected_states,'totalCompositeStateProducts':sum(states.values())+protected_states,
        'classCounts':dict(counts),'stateProductsByClass':dict(states),'objects':objects,
        'currentClosedOpenExactSharing':{'retainedDistinctPoses':poses,'duplicatedIdenticalClosedOpenAlreadyAvoided':same},
        'exactComponentOccurrences':{'parts':len(parts),'uniquePartDocuments':len(set(parts)),'bounds':len(bounds),'uniqueBoundsDocuments':len(set(bounds)),'simpleRotationRows':len(rotations),'uniqueSimpleRotationRows':len(set(rotations))},
        'compactEagerShapeConstruction':{'types':len(compact),'orientationRowsBefore':all_rows,'orientationRowsAfter':even_rows,'distinctShapeKeysBefore':len(all_shapes),'distinctUsedCardinalShapeKeysAfter':len(even_shapes),'unusedOddOnlyShapeKeysAvoided':len(all_shapes-even_shapes)},
        'scope':'Exact registration property Cartesian products inferred from current factory and immutable component documents; includes hidden save adapters. Does not estimate RAM or change any public state range.'}
    write_json(OUT/'static-ram-audit.json',report);print(json.dumps({k:v for k,v in report.items() if k!='objects'}))

if __name__=='__main__':main()
