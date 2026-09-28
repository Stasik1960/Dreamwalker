"""Read-only coordinates from immutable inputs and released negative fixtures."""
from pathlib import Path
import json
from compare_modded_reference import LazyReference
from convert_logical_world import DEFAULT_RESOURCES, as_tag_state, add
from logical_contract_v2 import direct_rules
from world_io import block_state_key, RegionFile
from source_variant_rng import guards_match
from modded_world_adapter import compile_modded_rules

ROOT = Path(__file__).resolve().parents[1]
POINTS = [(-563,85,-165),(-566,85,-165),(-284,43,-71),(-279,43,-49),
          (-280,42,-50),(-327,50,-115),(-326,50,-115),(-332,77,-137),
          (-540,41,-33),(-538,35,-36)]

def inspect():
    paths = {
        'source': ROOT/'reference-inputs/source-world.zip',
        'modded': ROOT/'reference-inputs/latest-modded-world.zip',
        'beta2': next((ROOT.parent/'releases/Bloodborne-Blocks/2.1.0-beta.2-city-recovery').glob('*Conservative*.zip'), None),
    }
    # Negative release remains available on archive branch; local build output
    # is not treated as authoritative input to any repair conversion.
    readers = {k: LazyReference(p) for k,p in paths.items() if p}
    class NegativeBuild(LazyReference):
        def _region(self, relative):
            if relative not in self.regions:
                p=ROOT/'build/grid-full-world-final'/relative
                self.regions[relative]=RegionFile.open(p) if p.exists() else None
            return self.regions[relative]
    readers['beta3_negative_build']=NegativeBuild(paths['modded'])
    rules,_ = direct_rules(DEFAULT_RESOURCES)
    result = {'coordinates': [], 'nearby_exact_reviewed_patterns': []}
    try:
        for pos in POINTS:
            result['coordinates'].append({'position':pos, **{
                k:r.state('eh_s2:yharnam' if k=='source' else 'minecraft:overworld',pos)[1]
                for k,r in readers.items()}})
        for rule in rules:
            if rule.target[0].split(':')[-1] not in {'o_c001','o_c474','o_c618','o_lantern','o_lanterns','o_wall_lantern'}:
                continue
            pieces=(rule.source,)+rule.members
            seen=set()
            for point in POINTS:
                for piece in pieces:
                    origin=tuple(point[i]-piece.offset[i] for i in range(3))
                    if origin in seen: continue
                    seen.add(origin)
                    if guards_match(rule.variant_guards,origin) and all(readers['source'].state('eh_s2:yharnam',add(origin,p.offset))[1]==block_state_key(as_tag_state(p.state)) for p in pieces):
                        result['nearby_exact_reviewed_patterns'].append({
                            'rule':rule.number,'target':rule.target,'origin':origin,
                            'cells':[{'position':add(origin,p.offset),'source':block_state_key(as_tag_state(p.state)),
                                      **{k:r.state('minecraft:overworld',add(origin,p.offset))[1] for k,r in readers.items() if k!='source'}} for p in pieces]})
        compiled,_,diagnostics=compile_modded_rules(DEFAULT_RESOURCES)
        for fixture in result['nearby_exact_reviewed_patterns']:
            origin=tuple(fixture['origin']); raw=fixture['rule']; checks=[]
            for rule in compiled:
                if not (rule.source_reference or '').endswith(f'raw rule {raw}'): continue
                bad=[{'position':add(origin,p.offset),'expected':block_state_key(as_tag_state(p.state)),
                      'actual':readers['modded'].state('minecraft:overworld',add(origin,p.offset))[1]}
                     for p in (rule.source,)+rule.members
                     if readers['modded'].state('minecraft:overworld',add(origin,p.offset))[1]!=block_state_key(as_tag_state(p.state))]
                checks.append({'rule':rule.number,'reference':rule.source_reference,'mismatches':bad})
            fixture['baseline_match_attempts']=checks
    finally:
        for r in readers.values(): r.close()
    out=ROOT/'docs/composite-grid-repair/fragmentation-diff.json'
    out.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print('fixtures',len(result['coordinates']),'exact reviewed patterns',len(result['nearby_exact_reviewed_patterns']))

if __name__=='__main__': inspect()
