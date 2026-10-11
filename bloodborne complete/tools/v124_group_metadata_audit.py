"""Flag merged groups with behavior/render metadata requiring preservation."""
import json
from v124_visual_audit import OUT

baseline=json.loads((OUT/'baseline-content.json').read_text(encoding='utf-8'))['descriptors']
groups=json.loads((OUT/'general-functional-groups.json').read_text(encoding='utf-8'))['groups']
changes=[];dimensions=[];resolved=[]
alpha=json.loads((OUT/'merged-render-layer-alpha.json').read_text(encoding='utf-8')) if (OUT/'merged-render-layer-alpha.json').exists() else {}
alpha_by_id={r['id']:r for r in alpha.get('ids',[])}
tint_paths={'source_grass_block','source_bricks_grass_block_0','source_potted_fern'}
tint_checks=[]
for g in groups:
    specs=[(m['id'],baseline[m['registryId'].split(':')[1]]) for m in g['members']]
    flags={}
    for field in ['openable','layer','support','essentialMask','importedClimbingBounds']:
        values={json.dumps(s.get(field),sort_keys=True):[] for _,s in specs}
        for i,s in specs:values[json.dumps(s.get(field),sort_keys=True)].append(i)
        if len(values)>1:flags[field]=[{'value':json.loads(v),'ids':ids} for v,ids in values.items()]
    effective_layer=g.get('renderLayerOverride',baseline[g['mainRegistryId'].split(':')[1]].get('layer'))
    if set(flags)=={'layer'} and effective_layer=='translucent':
        cutout_ids=[i for i,s in specs if s.get('layer')=='cutout']
        if all(i in alpha_by_id and all(t['partialAlphaSamples']==0 for t in alpha_by_id[i]['textures'].values()) for i in cutout_ids):
            resolved.append({'mainId':g['mainId'],'effectiveLayer':effective_layer,'override':g.get('renderLayerOverride'),
                'reason':'Partial-alpha members remain translucent; cutout members reference only binary/opaque alpha, so their opacity is retained. Native final comparison still required.','fields':flags})
            flags={}
    tinted={m['registryId'].split(':')[1] in tint_paths for m in g['members']}
    if len(tinted)>1:flags['runtimeBiomeTint']={'members':[m['id'] for m in g['members']],'reason':'Mixed hardcoded tinted and untinted registry IDs lose source tint; do not merge without per-state preservation.'}
    if flags:changes.append({'mainId':g['mainId'],'fields':flags})
    if g['newVariantCount']>16:dimensions.append({'mainId':g['mainId'],'poses':g['newVariantCount'],'members':[m['id'] for m in g['members']]})
for path in sorted(tint_paths):
    registry='bloodborne_dw:'+path;group=next((g for g in groups if any(m['registryId']==registry for m in g['members'])),None)
    tint_checks.append({'registryId':registry,'canonicalRegistryId':group['mainRegistryId'] if group else registry,'status':'PASS' if not group or all(m['registryId'].split(':')[1] in tint_paths for m in group['members']) else 'FAIL','preservation':'Hardcoded biome-tint identity retained; no mixed tinted/untinted family.'})
output={'status':'PASS' if not changes else 'METADATA_REVIEW_REQUIRED','mergedGroupCount':len(groups),'metadataDifferences':changes,'resolvedRenderLayerDifferences':resolved,'runtimeTintChecks':tint_checks,'bankRequired':dimensions}
(OUT/'group-metadata-audit.json').write_text(json.dumps(output,ensure_ascii=False,indent=2),encoding='utf-8')
(OUT/'group-metadata-audit.txt').write_text(json.dumps(output,ensure_ascii=False,indent=2),encoding='utf-8')
