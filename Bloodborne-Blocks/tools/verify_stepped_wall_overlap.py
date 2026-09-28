"""Prove the neighboring carrier owns the six additional slab polygons.

Evidence only: both complete objects must be restored atomically before this
merged cell may be consumed. This tool never changes a world or runtime art.
"""
import gzip
import json
from inspect_historical_wall_meshes import historical_art, carrier_cells, ROOT
from composite_world_oracle import EvidenceReader
from build_city_compat import rotated_mesh, JAR_SHA
from verify_composite_overlap_evidence import signature


def verify():
    source = EvidenceReader(ROOT/'reference-inputs/source-world.zip')
    modded = EvidenceReader(ROOT/'reference-inputs/latest-modded-world.zip')
    wall = (-676, 35, -312); stairs = (-676, 34, -312)
    wall_state = 'minecraft:sandstone_wall[east=tall,north=none,south=none,up=false,waterlogged=false,west=tall]'
    stair_state = 'minecraft:sandstone_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]'
    actual_state = 'bloodborne_blocks:m_37ae347a7c9d73ae[facing=east]'
    try:
        if source.state('eh_s2:yharnam', wall)[1] != wall_state: raise ValueError('SOURCE_WALL_CHANGED')
        if source.state('eh_s2:yharnam', stairs)[1] != stair_state: raise ValueError('SOURCE_STAIRS_CHANGED')
        if modded.state('minecraft:overworld', wall)[1] != actual_state: raise ValueError('MODDED_CELL_CHANGED')
        with historical_art() as jar:
            meshes = json.loads(gzip.decompress(jar.read('bloodborne_blocks/v2/meshes.json.gz')))
            wall_cells = carrier_cells(jar, 'sandstone_wall', dict(east='tall', north='none', south='none', up='false', west='tall', waterlogged='false'))
            stair_cells = carrier_cells(jar, 'sandstone_stairs', dict(facing='north', half='bottom', shape='straight', waterlogged='false'))
            actual = signature(rotated_mesh(meshes['m_37ae347a7c9d73ae'], 1)['polygons'])
            own = signature(wall_cells[(0, 0, 0)])
            incoming = signature(stair_cells[(0, 1, 0)])
            if actual != own | incoming or actual-own != incoming:
                raise ValueError('NEIGHBOR_POLYGON_UNION_NOT_EXACT')
            return {'result': 'PASS', 'historicalJarSha256': JAR_SHA,
                    'wall': list(wall), 'wallSource': wall_state,
                    'neighborRoot': list(stairs), 'neighborSource': stair_state,
                    'actualState': actual_state, 'wallPolygons': len(own),
                    'neighborPolygons': len(incoming), 'actualPolygons': len(actual),
                    'missing': 0, 'extra': 0,
                    'conversion': 'UNRESOLVED: must preserve both whole objects in one transaction'}
    finally:
        source.close(); modded.close()


if __name__ == '__main__':
    data = verify()
    (ROOT/'docs/composite-grid-repair/stepped-wall-overlap-evidence.json').write_text(json.dumps(data, indent=2)+'\n', encoding='utf8')
    print(json.dumps(data))
