"""Reuse exact prepared selections for finite native optical compatibility.

Read-only dictionary/fixture join, never a scan of the city or source world.
Keep source application order, model header, original XYZ seed evidence and
the current compact collider/outline in world-relative model units.
"""
from __future__ import annotations
import collections
import hashlib
import json
from pathlib import Path
import sqlite3
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/architecture/resources'
OUT = ROOT / 'docs/final-city-repair/geometry'
REFERENCE = Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW')
PACK = REFERENCE / 'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16/bbmc_v15_resource.zip'
NATIVE_PROOF = REFERENCE / 'bloodborne complete/build/gametest-tests21/world/v127-source-lighting-native-proof.json'

def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))

def write(path, doc):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(doc, ensure_ascii=False, indent=2)+'\n', encoding='utf-8', newline='\n')

def source_model_headers(pack, vanilla, model, seen=()):
    if model.startswith(('builtin/', 'minecraft:builtin/')):
        return {'ambientocclusion': True, 'gui_light': 'side'}
    assert model not in seen, (model, seen)
    namespace, relative = model.split(':', 1) if ':' in model else ('minecraft', model)
    path = f'assets/{namespace}/models/{relative}.json'
    data = json.loads(pack.read(path) if path in pack.namelist() else vanilla.read(path))
    header = source_model_headers(pack, vanilla, data['parent'], seen+(model,)) if data.get('parent') else {'ambientocclusion': True, 'gui_light': 'side'}
    return {key: data.get(key, header[key]) for key in ('ambientocclusion', 'gui_light')}

def main():
    native = read(NATIVE_PROOF)
    fixture = read(RES / '../..' / 'gametest/resources/bloodborne_dw/v127-source-lighting-decisions.json')
    cases = {row['caseId']: row for row in fixture['cases']}
    types = {row['registryId']: row for row in read(RES / 'bloodborne_dw/imported-compact-v127.json')['types']}
    connection = sqlite3.connect((ROOT / 'build/v1271-city-preparation/source-plan.sqlite').resolve().as_uri()+'?mode=ro', uri=True)
    decisions = {i: json.loads(v) for i, v in connection.execute('SELECT id,data FROM decisions')}
    connection.close()
    grouped = {}
    selected_cases = []
    for row in native['cases']:
        if row['action'] != 'COMPACT':
            continue
        text = row['targetState']
        registry = text.split('[')[0]
        properties = dict(item.split('=') for item in text.split('[')[1][:-1].split(','))
        pose = int(properties.get('variant', 0))+16*int(properties.get('art_bank', 0))
        optical = types[registry]
        geometry = optical['shapes'][str(pose)][int(properties.get('rotation', 0))]
        full = geometry['collision'] == [{'from': [0,0,0], 'to': [16,16,16]}]
        source_name = row['sourceState'].split('[')[0]
        # Those source states are already handled by the separate24-pose repair
        # and14 transparent native compatibility selections, respectively.
        if registry == 'bloodborne_dw:source_snow_height12' and pose == 3:
            continue
        if 'glass' in source_name:
            continue
        opacity = row['sourceOpacity']
        opaque_art = pose in optical['opaquePoses']
        if not ((full and opacity == 0) or (opacity == 15 and not opaque_art)):
            continue
        selected_cases.append(row)
        source_case = cases[row['caseId']]
        for decision_id in source_case['decisionIds']:
            decision = decisions[decision_id]
            assert decision['action'] == 'COMPACT', (decision_id, decision)
            assert decision.get('mountY',0) == 0 and not any(decision.get('sourceShift',[0,0,0]))
            apps = decision['sourceApplications']
            # Partial native shapes need their exact sourceState. Full opaque
            # sources may share native flags, but artwork/application/collider
            # equality still governs whether a SOURCE context can be shared.
            carrier = row['sourceState'] if opacity == 0 else 'FULL_NATIVE_OPAQUE'
            key = json.dumps([carrier, apps, geometry], sort_keys=True, separators=(',',':'))
            context = grouped.setdefault(key, {'sourceState': row['sourceState'], 'nativeSourceStates': [], 'sourceOpaque': True, 'sourceApplications': apps, 'collision': geometry['collision'], 'outline': geometry['aim'], 'sourceShift': [0,0,0], 'mountY': 0, 'blockEntitiesAdded': 0, 'decisionIds': [], 'caseIds': [], 'caseOccurrences': {}, 'oldArtworks': [], 'originalSamples': []})
            if row['sourceState'] not in context['nativeSourceStates']:
                context['nativeSourceStates'].append(row['sourceState'])
            if decision_id not in context['decisionIds']:
                context['decisionIds'].append(decision_id)
            if row['caseId'] not in context['caseIds']:
                context['caseIds'].append(row['caseId'])
                context['caseOccurrences'][str(row['caseId'])] = row['occurrences']
                context['originalSamples'].append(source_case['samples'][0])
            old = {'registryId': registry, 'pose': pose, 'rotation': int(properties.get('rotation',0))}
            if old not in context['oldArtworks']:
                context['oldArtworks'].append(old)
    vanilla_path = Path.home() / '.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'
    with zipfile.ZipFile(PACK) as pack, zipfile.ZipFile(vanilla_path) as vanilla:
        for index, context in enumerate(grouped.values()):
            context['context'] = index
            context['modelHeaders'] = [source_model_headers(pack, vanilla, app['model']) for app in context['sourceApplications']]
            context['nativeMultipartFirstModelHeader'] = context['modelHeaders'][0]
            context['allSelectedModelHeadersAgree'] = len({json.dumps(h,sort_keys=True) for h in context['modelHeaders']}) == 1
            context['occurrences'] = sum(context['caseOccurrences'].values())
    contexts = list(grouped.values())
    occurrences = sum(x['occurrences'] for x in selected_cases)
    assert occurrences == 348528 and len(selected_cases) == 80
    assert len(contexts) <= 128 and 77+len(contexts) <= 256
    unique_decisions = {i for context in contexts for i in context['decisionIds']}
    assert sum(len(context['decisionIds']) for context in contexts) == len(unique_decisions), 'One finite context per exact prepared decision'
    document = {'schemaVersion':1, 'status':'PASS_PREPARED_COMPACT_OPTICAL_CONTEXTS_WITH_PRESELECTED_SOURCE_ART_AND_PRESERVED_PHYSICS', 'nativeSourceProof':str(NATIVE_PROOF), 'nativeSourceProofSha256':hashlib.sha256(NATIVE_PROOF.read_bytes()).hexdigest(), 'originalCompactCases':1810, 'selectedCases':80, 'occurrences':occurrences, 'finiteContexts':len(contexts), 'preparedDecisionRecords':len(unique_decisions), 'sourcePaletteWorldRescanned':False, 'sourceShiftAndMountAreZero':True, 'nativeJVMRun':False, 'extraBlockEntities':0, 'sourceApplicationOrderAndWeightedSelectionsPreserved':True, 'collisionAndOutlineUnits':16, 'contexts':contexts}
    write(OUT / 'remaining-compact-optical-contexts.json', document)
    print(json.dumps({key:document[key] for key in ('status','finiteContexts','preparedDecisionRecords','selectedCases','occurrences','extraBlockEntities')}))

if __name__ == '__main__':
    main()
