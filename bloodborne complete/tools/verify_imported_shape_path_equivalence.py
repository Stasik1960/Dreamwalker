"""Static OLD-path proof for the narrowly gated exact NEW shape adapter."""
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT/'docs/collision-simplification/runtime-old-path-equivalence.json'
JAVA = 'src/architecture/java/dev/dreamwalker/bloodbornedw/'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def baseline(relative):
    return subprocess.check_output(['git', 'show', 'HEAD:bloodborne complete/'+relative], cwd=ROOT)


def method(text, signature):
    start = text.index(signature)
    brace = text.index('{', start)
    depth = 1
    cursor = brace + 1
    while depth:
        depth += (text[cursor] == '{') - (text[cursor] == '}')
        cursor += 1
    return text[start:cursor]


def main():
    checks = []
    sources = {}
    def check(name, condition, explanation):
        checks.append({'check':name, 'status':'PASS' if condition else 'FAIL', 'evidence':explanation})
    runtime_path = JAVA+'composite/CompositeRuntime.java'
    current = (ROOT/runtime_path).read_text(encoding='utf-8')
    previous = baseline(runtime_path).decode('utf-8')
    sources[runtime_path] = digest((ROOT/runtime_path).read_bytes())
    for name, signature in [('topHeight','public static Double topHeight('), ('bottomHeight','public static Double bottomHeight(')]:
        body = method(current, signature)
        normalized = body.replace('ImportedPhysicalShapes.physical(contribution,support)', 'shape(physicalParts(contribution,support))')
        check(name+'_old_branch', normalized == method(previous, signature), 'Replacing the per-contribution NEW dispatcher with its OLD fallback reproduces the entire original method exactly; native shape, center contact and height calculation are unchanged.')
    hook = '        if(collision){var imported=ImportedPhysicalShapes.collision(world,pos,fallback);if(imported.isPresent())return imported.get();}\n'
    outline_fallback = '        if(!collision&&fallback.getBlock() instanceof CompositeRootBlock block&&block.spec.simplePhysicalCollision&&contributions(world,pos).isEmpty())return shape(ImportedLegacyOutline.fallback(block,fallback));\n'
    signature = 'public static VoxelShape cellShape(BlockView world,BlockPos pos,boolean collision,BlockState fallback,ShapeContext context)'
    check('cellShape_selection_and_old_branch', method(current,signature).replace(hook,'').replace(outline_fallback,'') == method(previous,signature), 'The physical hook and original-outline fallback are restricted to newly imported objects; otherwise the entire previous method remains exact.')
    for name, signature in [('nativeCollision','public static VoxelShape nativeCollision(BlockState state,'), ('contextSelection','private static List<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box> contextSelection('), ('selectionParts','private static List<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box> selectionParts('), ('physicalParts','public static List<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box> physicalParts('), ('shape','public static VoxelShape shape(')]:
        source = method(current,signature)
        if name == 'contextSelection':
            source = source.replace('        var imported=ImportedLegacyOutline.selection(entry,cell);if(imported.isPresent())return imported.get();\n','')
        check(name+'_unchanged', source == method(previous,signature), 'Original method source matches HEAD exactly after removing its NEW-only outline early return, where present.')
    fabric_path = JAVA+'composite/FabricCompositeWorld.java'
    fabric = (ROOT/fabric_path).read_text(encoding='utf-8')
    sources[fabric_path] = digest((ROOT/fabric_path).read_bytes())
    old_line = 'var pos=CompositeData.pos(cell);var boxes=CompositeRuntime.shape(proposed.collision()).getBoundingBoxes().stream().map(box->box.offset(pos)).toList();'
    new_line = 'var pos=CompositeData.pos(cell);var physical=incoming.size()==1&&ImportedPhysicalShapes.isImported(incoming.keySet().iterator().next())?ImportedPhysicalShapes.exact(proposed.collision()):CompositeRuntime.shape(proposed.collision());var boxes=physical.getBoundingBoxes().stream().map(box->box.offset(pos)).toList();'
    check('entity_check_old_branch_entire_adapter', fabric.replace(new_line,old_line) == baseline(fabric_path).decode('utf-8'), 'Sole change is the physical VoxelShape factory. Non-singleton or non-imported incoming owners execute the original factory; entity filtering and world coordinates are unchanged.')
    helper_path = JAVA+'composite/ImportedPhysicalShapes.java'
    helper = (ROOT/helper_path).read_text(encoding='utf-8')
    sources[helper_path] = digest((ROOT/helper_path).read_bytes())
    check('helper_actual_registered_spec_gate', 'Registries.BLOCK.containsId(id)' in helper and 'Registries.BLOCK.get(id) instanceof CompositeRootBlock block' in helper and 'block.spec.simplePhysicalCollision' in helper, 'Gate is the registered block descriptor marker, including its constructor namespace/source_ restriction. Carrier block type alone does not activate NEW physics.')
    check('helper_physical_old_fallback', 'var boxes = CompositeRuntime.physicalParts(entry, cell);' in helper and 'return isImported(entry.owner()) ? exact(boxes) : CompositeRuntime.shape(boxes);' in helper, 'OLD contribution dispatch invokes exactly the unchanged physicalParts and legacy shape factory.')
    check('helper_mixed_owners_split', '(isImported(entry.owner()) ? exact : legacy).addAll(CompositeRuntime.physicalParts(entry, pos));' in helper and 'VoxelShapes.union(CompositeRuntime.shape(legacy), exact(exact))' in helper, 'Legacy owners form one old raster batch; NEW owners form a separate ordinary-cuboid batch. Union does not re-rasterize the new batch.')
    check('helper_worker_snapshot_gate', 'if (CompositeShapeSnapshots.worker(view)) return CompositeShapeSnapshots.validOrDeferred(view, owner);' in helper, 'Worker validity consumes immutable published metadata before any World/chunk/BE access. Registry classification is world-independent.')
    outline_path = JAVA+'composite/ImportedLegacyOutline.java'
    outline = (ROOT/outline_path).read_text(encoding='utf-8')
    sources[outline_path] = digest((ROOT/outline_path).read_bytes())
    check('original_outline_is_new_only', 'if (!ImportedPhysicalShapes.isImported(entry.owner())) return Optional.empty();' in outline, 'Every original-outline contribution query rejects non-NEW owners before resolving any state or original data.')
    check('original_outline_reuses_legacy_geometry', 'CompositeSpec.legacyOutlineSpec(document)' in outline and 'CompositeSourceShift.footprint(old, state, payload)' in outline and 'ImportedCollisionMath' not in outline, 'A hidden unregistered spec without NEW marker invokes original yaw/mount/source-shift distribution. New simplified physics mathematics never supplies aiming.')
    check('original_outline_retains_empty_cell_rule', 'return Optional.of(physical ? part.collision() : part.selection());' in outline and 'return part.collision().isEmpty() ? part.selection() : part.collision();' in outline, 'Normal bound entries retain the old whole-object passability decision; unbound root fallback retains the old local-cell decision.')
    spec_path = JAVA+'composite/CompositeSpec.java'
    spec = (ROOT/spec_path).read_text(encoding='utf-8')
    sources[spec_path] = digest((ROOT/spec_path).read_bytes())
    old_spec = baseline(spec_path).decode('utf-8')
    for name, signature in [('distribute','private static void distribute('),('intersects','private static boolean intersects('),('clip','private static void clip(')]:
        check('original_outline_'+name+'_byte_exact', method(spec,signature) == method(old_spec,signature), 'Original distribution/raster/clip method source is exact, without a duplicated geometry algorithm.')
    protected = ['composite/CompositeShapes.java','composite/CompositeShapeSnapshots.java','composite/mixin/CompositeShapeMixin.java','architecture/PlacementPhysics.java','architecture/SourceConversionScope.java','composite/CompositeRootBlock.java','composite/CompositeCellBlock.java']
    for suffix in protected:
        relative = JAVA+suffix
        data = (ROOT/relative).read_bytes()
        sources[relative] = digest(data)
        check('byte_exact_'+suffix, data == baseline(relative), 'Entire source file remains byte-for-byte equal to HEAD; no global shape, placement, source privilege, worker publication or native carrier hook change.')
    failures = sum(row['status']=='FAIL' for row in checks)
    result = {'schema':'dreamwalker-v122-new-physical-adapter-old-path-equivalence-v1', 'status':'PASS_STATIC_SOURCE_EQUIVALENCE' if not failures else 'FAIL', 'baselineCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(), 'checks':len(checks), 'failures':failures, 'sources':sources, 'cases':checks, 'limitations':'Source-path proof only. Actual cuboid movement bounds, mixed owners, foreign/native carriers, worker and occupied-save migration require Minecraft QA.'}
    OUT.parent.mkdir(parents=True,exist_ok=True)
    OUT.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    if failures:
        raise SystemExit(1)


if __name__=='__main__':
    main()
