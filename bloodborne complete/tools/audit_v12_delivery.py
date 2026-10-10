"""Read-only V12 delivery integrity audit; never start Minecraft or a build.

Pending evidence is valid during preparation. --require-completed exits unsuccessfully
unless every check passes. Native case verdicts are supplied by the human/native review;
this audit verifies their scope and artifact binding, not gameplay from screenshots.
"""
from __future__ import annotations

import argparse
import csv
from datetime import datetime, timezone
import hashlib
import io
import json
import math
from pathlib import Path, PurePosixPath
import re
import stat
import struct
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = '0.1.0-v12'
MODEL_SHA = 'a23dbdf32d288eb0249de4f1c3146dfe122354e77a62dba699651c8b17f1a827'
NATIVE_CASES = {'G1', *(f'T{i}' for i in range(1, 13)), *(f'C{i}' for i in range(1, 5)),
                'P1', 'P2', 'D1', 'D2', 'L1'}
DW = 'dev/dreamwalker/bloodbornedw/'
RP = 'dev/dreamwalker/bloodbornerp/'
COMPAT = DW + 'compat/collision/'


class Pending(Exception):
    pass


def require(condition, message):
    if not condition:
        raise ValueError(message)


def file(path):
    if path is None or not path.exists():
        raise Pending('Evidence file unavailable: ' + str(path))
    for part in (path, *path.parents):
        info = part.lstat()
        require(not stat.S_ISLNK(info.st_mode) and not getattr(info, 'st_file_attributes', 0) & 0x400,
                'Reparse/symlink input is unsupported: ' + str(part))
    require(path.is_file(), 'Expected regular file: ' + str(path))
    return path


def digest(path):
    value = hashlib.sha256()
    with file(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(chunk)
    return value.hexdigest()


def read_json(path):
    return json.loads(read_text(path))


def read_text(path):
    data = file(path).read_bytes()
    return data.decode('utf16' if data[:2] in (b'\xff\xfe', b'\xfe\xff') else 'utf-8-sig', errors='replace')


def provenance(path):
    return {'path': str(file(path).absolute()), 'sha256': digest(path), 'bytes': path.stat().st_size}


def checked_zip(path):
    """No extraction, links, duplicate members or executable archive contents."""
    archive = zipfile.ZipFile(file(path))
    try:
        names = archive.namelist()
        require(len(names) == len(set(names)), 'Duplicate ZIP members: ' + str(path))
        for entry in archive.infolist():
            name = PurePosixPath(entry.filename)
            require('\\' not in entry.filename and not name.is_absolute() and
                    '..' not in name.parts and not re.match(r'^[A-Za-z]:', entry.filename),
                    'Unsafe ZIP member: ' + entry.filename)
            require(not stat.S_ISLNK(entry.external_attr >> 16), 'ZIP symlink: ' + entry.filename)
        require(archive.testzip() is None, 'ZIP CRC failed: ' + str(path))
        return archive
    except Exception:
        archive.close()
        raise


def sidecar(path):
    checksum = file(Path(str(path) + '.sha256')).read_text(encoding='utf-8-sig').strip()
    match = re.fullmatch(r'([0-9a-fA-F]{64})\s+\*?(.+)', checksum)
    require(match is not None and match[2] == path.name and match[1].lower() == digest(path),
            'SHA sidecar mismatch: ' + str(path))
    return provenance(path)


class ClassInfo:
    """Small class-file reader for packaged member references and code, without JVM execution."""
    def __init__(self, data):
        self.data, self.pos = data, 0
        require(self.take(4) == b'\xca\xfe\xba\xbe', 'Invalid packaged class')
        self.take(4)
        self.cp = [None] * self.u2()
        index = 1
        while index < len(self.cp):
            tag = self.u1()
            if tag == 1:
                value = self.take(self.u2()).decode('utf8', errors='replace')
            elif tag in (7, 8, 16, 19, 20):
                value = self.u2()
            elif tag in (9, 10, 11, 12, 17, 18):
                value = (self.u2(), self.u2())
            elif tag == 15:
                value = (self.u1(), self.u2())
            elif tag in (3, 4):
                value = self.take(4)
            elif tag in (5, 6):
                value = self.take(8)
            else:
                raise ValueError('Unknown constant-pool tag ' + str(tag))
            self.cp[index] = (tag, value)
            index += 2 if tag in (5, 6) else 1
        self.take(6)
        self.take(2 * self.u2())
        self.fields = self.members(False)
        self.methods = self.members(True)

    def take(self, size):
        start = self.pos
        self.pos += size
        require(self.pos <= len(self.data), 'Truncated packaged class')
        return self.data[start:self.pos]

    def u1(self):
        return self.take(1)[0]

    def u2(self):
        return struct.unpack('>H', self.take(2))[0]

    def u4(self):
        return struct.unpack('>I', self.take(4))[0]

    def text(self, index):
        return self.cp[index][1]

    def member(self, index):
        tag, (owner, signature) = self.cp[index]
        require(tag in (9, 10, 11), 'Expected field/method reference')
        name, descriptor = self.cp[signature][1]
        return self.text(self.cp[owner][1]), self.text(name), self.text(descriptor)

    def members(self, code):
        result = {}
        for _ in range(self.u2()):
            self.take(2)
            name, descriptor = self.text(self.u2()), self.text(self.u2())
            body = None
            for _ in range(self.u2()):
                attribute = self.text(self.u2())
                content = self.take(self.u4())
                if code and attribute == 'Code':
                    length = struct.unpack('>I', content[4:8])[0]
                    body = content[8:8 + length]
            result[(name, descriptor)] = body
        return result

    @property
    def strings(self):
        return {row[1] for row in self.cp[1:] if row and row[0] == 1}

    @property
    def references(self):
        return [self.member(i) for i, row in enumerate(self.cp) if row and row[0] in (9, 10, 11)]


class Audit:
    def __init__(self, args):
        self.args, self.checks, self.cache = args, [], {}

    def check(self, name, action):
        try:
            details = action()
            self.checks.append({'check': name, 'status': 'PASS', 'details': details})
        except Pending as error:
            self.checks.append({'check': name, 'status': 'PENDING', 'reason': str(error)})
        except (OSError, ValueError, KeyError, TypeError, IndexError, zipfile.BadZipFile, ET.ParseError) as error:
            self.checks.append({'check': name, 'status': 'FAIL', 'reason': str(error)})

    def production(self):
        if 'production' not in self.cache:
            with checked_zip(self.args.jar) as archive:
                metadata = json.loads(archive.read('fabric.mod.json'))
                classes = {name: ClassInfo(archive.read(name)) for name in archive.namelist() if name.endswith('.class')}
                self.cache['production'] = metadata, classes, archive.namelist()
        return self.cache['production']

    def metadata(self):
        meta, _, names = self.production()
        require(meta['id'] == 'bloodborne_dw' and meta['version'] == VERSION, 'Wrong production ID/version')
        require('bloodborne_rp' in meta.get('provides', []), 'Missing merged RP provider')
        require(meta.get('depends', {}).get('minecraft') == '1.20.1', 'Wrong Minecraft dependency')
        require('dreamwalker_modelpacks' not in meta.get('depends', {}), 'External dependency leaked into production')
        require(not any(name.startswith('dev/dreamwalker/modelpacks/') for name in names), 'External mod classes bundled into production')
        require(not any('/gametest/' in n.lower() or '/qa/' in n.lower() or '/gallery/' in n.lower() for n in names if n.endswith('.class')),
                'QA/gametest/gallery classes leaked into production')
        return {'artifact': sidecar(self.args.jar), 'metadata': meta}

    def tool(self):
        _, classes, _ = self.production()
        service = classes[DW + 'tool/BbToolService.class']
        main = classes[DW + 'DreamwalkerBb.class']
        creative = classes[DW + 'debug/UnifiedCreativeCatalogue.class']
        require('bb_tool' in service.strings and 'bloodborne_dw' in service.strings, 'Wrong tool registry ID')
        require(any(owner == DW + 'tool/BbToolService' and name == 'initialize' for owner, name, _ in main.references),
                'Tool initialization absent from production entrypoint')
        require(any(owner == DW + 'tool/BbToolService' and name == 'ITEM' for owner, name, _ in creative.references),
                'Tool missing from current offered-item catalogue')
        with checked_zip(self.args.jar) as archive:
            model = json.loads(archive.read('assets/bloodborne_dw/models/item/bb_tool.json'))
            require(model['textures']['layer0'] == 'bloodborne_dw:item/composite_builder', 'Retained icon reference changed')
            icon = archive.read('assets/bloodborne_dw/textures/item/composite_builder.png')
            require(icon[:8] == b'\x89PNG\r\n\x1a\n' and icon[12:16] == b'IHDR', 'Retained icon is not a PNG')
        return {'registry': 'bloodborne_dw:bb_tool', 'model': model, 'iconBytes': len(icon)}

    def lamps(self):
        _, classes, _ = self.production()
        forbidden = [name for name in classes if name.startswith(RP) and
                     re.search(r'(LampClient|LampEditor|LampNetworking|LampNetwork|LampScreen)', name)]
        require(not forbidden, 'Obsolete lamp frontend/network classes: ' + ', '.join(forbidden))
        lamp = classes[RP + 'lamp/LampService.class']
        require({'pairLamps', 'open', 'removeWholeGroup'} <= {name for name, _ in lamp.methods}, 'Missing direct pair/travel/removal API')
        require(not any(owner.endswith('/ServerPlayNetworking') and name == 'registerGlobalReceiver'
                        for owner, name, _ in lamp.references), 'Lamp edit/travel packet receiver remains')
        return {'obsoleteClassesAbsent': True, 'directPairApiPresent': True,
                'scope': 'Packaged implementation integrity; native P1/P2 prove gameplay separately'}

    def angular(self):
        _, classes, _ = self.production()
        ladder = classes[DW + 'architecture/PrototypeLadderBlock.class']
        code = next((body for (name, _), body in ladder.methods.items() if name in ('method_9549', 'getCollisionShape')), None)
        require(code is not None and len(code) >= 20, 'Missing ladder physical method')
        # javac: state.get(DIAGONAL).booleanValue(); if true return VoxelShapes.empty().
        require(code[:2] == b'\x2b\xb2' and ladder.member(int.from_bytes(code[2:4], 'big'))[1] == 'DIAGONAL' and
                code[13] == 0x99 and code[16] == 0xb8 and code[19] == 0xb0 and
                ladder.member(int.from_bytes(code[17:19], 'big'))[:2] in
                (('net/minecraft/class_259', 'method_1073'), ('net/minecraft/util/shape/VoxelShapes', 'empty')),
                'Angular empty collision must return before shifted/cardinal fallback')
        require(not any('RotatedSectionShape' in name for name in classes), 'Old outline strip helper remains')
        require('diagonalOutline' not in {name for name, _ in ladder.fields}, 'Old diagonal strip cache remains')
        require('ordinaryDiagonalOutline' in {name for name, _ in ladder.methods}, 'Angular selection fallback missing')
        return {'diagonalPhysicalEarlyEmptyReturn': True, 'stripHelperAbsent': True,
                'scope': 'Packaged contract; cardinal/legacy/outline behavior also checked by native GameTests'}

    def staging(self):
        _, classes, _ = self.production()
        registry = classes[COMPAT + 'CollisionReplacements.class']
        pack = classes[COMPAT + 'CollisionPack.class']
        require({'register', 'clear', 'revision', 'query'} <= {name for name, _ in registry.methods}, 'Missing optional provider API')
        require({'read', 'replacement'} <= {name for name, _ in pack.methods}, 'Missing data parser/exact selector API')
        consumers = [path for path, info in classes.items() if not path.startswith(COMPAT) and
                     any(owner.startswith(COMPAT) for owner, _, _ in info.references)]
        require(not consumers, 'Collision staging unexpectedly has active production consumers: ' + ', '.join(consumers))
        return {'activeConsumers': [], 'currentOverrides': 0,
                'scope': 'No configured/automatic replacement physics; OBB parser and register API are staging only'}

    def gallery(self):
        manifest_path = self.args.release_dir / 'gallery-manifest.json'
        manifest = read_json(manifest_path)
        require(manifest.get('revision') == 'V12' and manifest.get('modVersion') == VERSION and
                manifest['productionJarSha256'] == digest(self.args.jar), 'Gallery revision/SHA differs')
        rows = manifest['exhibits']
        offered, npcs = manifest['expectedItems'], manifest['expectedNpcs']
        require(len(rows) == 121 and len(offered) == len(set(offered)) == 103 and len(npcs) == 18, 'Wrong current catalogue counts')
        require(sum(row['kind'] == 'rp_mob' for row in rows) == 18 and
                {row['item'] for row in rows if row['kind'] != 'rp_mob'} == set(offered), 'Exhibit kinds/offered items differ from current catalogue')
        require(len({(row['kind'], row['registry']) for row in rows}) == 121, 'Duplicate typed exhibit')
        tools = [row for row in rows if row['item'] == 'bloodborne_dw:bb_tool']
        require(len(tools) == 1 and tools[0]['representation'] == 'item_frame', 'Tool must be one framed offered item')
        previous = None
        for row in rows:
            x, z, width, depth = row['platform']
            a, _, c, d, _, f = row['bounds']
            require((x, z, x + width, z + depth) == (math.floor(a) - 2, math.floor(c) - 2, math.ceil(d) + 2, math.ceil(f) + 2),
                    'Physical bounds +2 platform differs: ' + row['registry'])
            if previous:
                require(x - (previous[0] + previous[2]) == 2 and z == previous[1], 'Gallery edge gap differs from2')
            previous = row['platform']
        index_path = self.args.release_dir / 'GALLERY-INDEX.csv'
        index = list(csv.DictReader(io.StringIO(file(index_path).read_text(encoding='utf-8-sig'))))
        require(len(index) == 121 and len({row['visit_key'] for row in index}) == 121 and
                {(r['kind'], r['registry']) for r in index} == {(r['kind'], r['registry']) for r in rows}, 'Gallery index differs')
        indexed = {(row['kind'], row['registry']): row for row in index}
        for row in rows:
            entry = indexed[row['kind'], row['registry']]
            require(entry['item'] == row['item'] and entry['command'] == '/function ' + row['visitFunction'] and
                    [float(entry[key]) for key in ('x', 'y', 'z', 'yaw', 'pitch')] == row['camera'],
                    'Index item/navigation differs: ' + row['registry'])
        reopen = manifest.get('productionReopen', {})
        if not reopen:
            raise Pending('Production-only gallery reopen unavailable')
        require(reopen.get('status') == 'PASS_PRODUCTION_ONLY_V12' and reopen.get('verifiedExhibits') == 121 and
                all(reopen.get(key) is True for key in ('identitiesPosesAndTypedStatesUnchanged', 'boundsPlusTwo', 'exactTwoBlockGaps')),
                'Production-only gallery reopen incomplete/failed')
        with checked_zip(self.args.world_zip) as archive:
            embedded = 'Dreamwalker-BB-v12-Gallery/gallery-manifest.json'
            require('Dreamwalker-BB-v12-Gallery/level.dat' in archive.namelist(), 'Missing saved world level.dat')
            require(json.loads(archive.read(embedded)) == manifest, 'Embedded world manifest differs')
            require(not any(name.lower().endswith('.jar') for name in archive.namelist()), 'JAR bundled in gallery world')
        return {'manifest': provenance(manifest_path), 'index': provenance(index_path), 'world': sidecar(self.args.world_zip),
                'exhibits': len(rows), 'offeredItems': len(offered), 'realNpcs': len(npcs), 'productionReopen': reopen}

    def tests(self):
        path = file(self.args.game_xml)
        tree = ET.parse(path)
        cases = tree.findall('.//testcase')
        failed = sum(bool(case.findall('failure')) for case in cases)
        errors = sum(bool(case.findall('error')) for case in cases)
        skipped = sum(bool(case.findall('skipped')) for case in cases)
        self.cache['gameTests'] = {'total': len(cases), 'passed': len(cases) - failed - errors - skipped,
                                   'failed': failed, 'errors': errors, 'skipped': skipped}
        require(len(cases) >= 57 and not (failed or errors or skipped), 'Native GameTest XML incomplete/failed: ' + str(self.cache['gameTests']))
        required = ('ordinaryladderitemandsavedangularownershavenophysicalcolliderbutretainoutline',
                    'diagonalladdercollisionstaysemptywithorwithoutactualmountfaces',
                    'studioreplacementcontractisexactimmutableanddisableduntilexplicitregistration')
        names = {case.get('name', '').lower() for case in cases}
        require(all(any(expected in name for name in names) for expected in required), 'Required angular/compat cases absent')
        return {**self.cache['gameTests'], 'xml': provenance(path), 'minimumExpected': 57, 'fixedCountExpectation': False}

    def transaction(self):
        text = read_text(self.args.build_log)
        summaries = list(re.finditer(r'^PASS (\d+) transaction core checks; Java ([^\r\n]+)', text, re.M))
        require(bool(summaries), 'No actual transactionCheck summary in supplied log')
        last = summaries[-1]
        start = text.rfind('> Task :transactionCheck', 0, last.start())
        require(start >= 0, 'Transaction summary lacks its task section')
        individual = re.findall(r'^PASS .+$', text[start:last.start()], re.M)
        count = int(last[1])
        require(len(individual) == count and count > 0 and last[2].startswith('17.'), 'Transaction count/Java disagrees with individual log outcomes')
        require(text.rfind('BUILD SUCCESSFUL') > text.rfind('BUILD FAILED'), 'Latest supplied build did not succeed')
        self.cache['transactionChecks'] = count
        return {'passed': count, 'java': last[2], 'log': provenance(self.args.build_log), 'fixedCountExpectation': False}

    def native(self):
        report = read_json(self.args.native_report)
        require(report.get('productionJarSha256') == digest(self.args.jar), 'Native report bound to another production JAR')
        rows = report.get('cases', [])
        require(len(rows) == 22 and {row['case'] for row in rows} == NATIVE_CASES, 'Native report must have the22 exact acceptance cases')
        failures = [r['case'] for r in rows if str(r.get('status', '')).startswith('FAIL')]
        require(not failures, 'Native failures: ' + ', '.join(failures))
        pending = [r['case'] for r in rows if not str(r.get('status', '')).startswith('PASS')]
        if pending:
            raise Pending('Native cases still pending: ' + ', '.join(pending))
        refs = report.get('evidence', [])
        verified = []
        for ref in refs:
            if isinstance(ref, dict) and 'path' in ref and 'sha256' in ref:
                path = Path(ref['path'])
                if not path.is_absolute():
                    path = self.args.native_report.parent / path
                require(digest(path) == ref['sha256'], 'Native evidence hash differs: ' + str(path))
                verified.append(provenance(path))
        return {'casesPassed': 22, 'review': provenance(self.args.native_report), 'verifiedEvidenceReferences': verified,
                'scope': 'Supplied native review verdicts, not an inference of gameplay from file presence'}

    def modelpacks(self):
        source = self.args.modelpacks_jar
        require(digest(source) == MODEL_SHA, 'External ModelPacks source differs from inspected archive JAR')
        with checked_zip(source) as archive:
            meta = json.loads(archive.read('fabric.mod.json'))
        require(meta['id'] == 'dreamwalker_modelpacks' and meta['version'] == '0.1.0', 'Wrong external mod identity')
        with checked_zip(self.args.modelpacks_archive) as archive:
            matches = [name for name in archive.namelist() if name.endswith('/dreamwalker-modelpacks-0.1.0.jar') or name == 'dreamwalker-modelpacks-0.1.0.jar']
            require(len(matches) == 1 and hashlib.sha256(archive.read(matches[0])).hexdigest() == MODEL_SHA, 'External JAR provenance does not match input ZIP')
        return {'source': provenance(source), 'originalArchive': provenance(self.args.modelpacks_archive),
                'archiveMember': matches[0], 'metadata': meta}

    def compatibility(self):
        server = read_json(self.args.compat_server_profile)
        require(server.get('artifact_sha256') == digest(self.args.jar), 'Combined server uses another production JAR')
        require(not server.get('errors') and not str(server.get('status', '')).startswith('FAIL'), 'Combined server failed')
        if server.get('status') != 'PASS':
            raise Pending('Combined server has not finished its real load/join check')
        run = Path(server['run_directory'])
        log = read_text(run / 'launch-console.log')
        require('bloodborne_dw ' + VERSION in log and 'dreamwalker_modelpacks 0.1.0' in log, 'Combined server log lacks both loaded mod versions')
        if not self.args.compat_client_profile:
            raise Pending('Combined real-client profile unavailable')
        profiles = [(self.args.compat_server_profile, server)]
        joined = []
        for path in self.args.compat_client_profile:
            client = read_json(path)
            require(client.get('artifact_sha256') == digest(self.args.jar), 'Combined client uses another production JAR')
            username = client['offline_username']
            if username + ' joined the game' not in log:
                raise Pending('No actual combined client join in server log: ' + username)
            profiles.append((path, client))
            joined.append(username)
        details = []
        for path, profile in profiles:
            extras = [row for row in profile.get('extraMods', []) if row.get('id') == 'dreamwalker_modelpacks']
            require(len(extras) == 1, 'Combined profile missing unique ModelPacks provenance')
            extra = extras[0]
            require(extra.get('sha256') == extra.get('copiedSha256') == MODEL_SHA and extra.get('copyByteVerification') == 'PASS', 'Combined extraJAR copy not byte verified')
            runtime = Path(profile['run_directory'])
            require(digest(runtime / extra['runtimePath']) == MODEL_SHA, 'Combined runtime extraJAR differs')
            packs = runtime / 'config/dreamwalker_modelpacks/packs'
            require(packs.is_dir() and not any(packs.iterdir()), 'Combined config packs must exist and be empty; no override allowed')
            details.append({'profile': provenance(path), 'extraMods': extras, 'packDirectory': str(packs), 'packEntries': 0})
        return {'profiles': details, 'actualJoinedClients': joined, 'serverConsole': provenance(run / 'launch-console.log'),
                'scope': 'Both mods loaded, real client joined, byte-identical externalJAR and empty pack configs; no replacement-physics claim'}

    def release(self):
        qa = read_json(self.args.release_dir / 'QA.json')
        require(qa.get('revision') == 'V12' and qa.get('productionJarSha256') == digest(self.args.jar) and
                qa.get('worldZipSha256') == digest(self.args.world_zip), 'Final QA revision/artifact binding differs')
        if 'gameTests' in self.cache:
            actual = self.cache['gameTests']
            require(all(qa.get('gameTests', {}).get(key) == actual[key] for key in ('passed', 'failed', 'errors')), 'QA GameTest summary differs from XML')
        if 'transactionChecks' in self.cache:
            require(qa.get('transactionChecks', {}).get('passed') == self.cache['transactionChecks'], 'QA transaction count differs from actual build log')
        require(qa.get('productionJar', {}).get('fabricMetadataVersion') == VERSION, 'QA production metadata differs')
        forbidden = []
        for path in self.args.release_dir.rglob('*'):
            if not path.is_file():
                continue
            file(path)
            if path.suffix.lower() == '.jar' and path != self.args.jar:
                forbidden.append(str(path))
            if path.suffix.lower() in ('.jar', '.zip'):
                with checked_zip(path) as archive:
                    forbidden += [str(path) + ':' + name for name in archive.namelist()
                                  if 'dreamwalker-modelpacks' in name.lower() or name.lower().endswith('.jar') and path.suffix.lower() == '.zip']
        require(not forbidden, 'External/QA JAR bundled in release: ' + ', '.join(forbidden))
        evidence = self.args.release_dir / 'QA-EVIDENCE.zip'
        with checked_zip(evidence) as archive:
            require(bool(archive.namelist()), 'Empty evidence archive')
        return {'QA': provenance(self.args.release_dir / 'QA.json'), 'evidence': sidecar(evidence), 'externalJarsBundled': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--release-dir', type=Path, required=True)
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--world-zip', type=Path)
    parser.add_argument('--game-xml', type=Path)
    parser.add_argument('--build-log', type=Path, required=True)
    parser.add_argument('--native-report', type=Path)
    parser.add_argument('--compat-server-profile', type=Path)
    parser.add_argument('--compat-client-profile', type=Path, action='append', default=[])
    parser.add_argument('--modelpacks-jar', type=Path, default=ROOT / 'build/v12-compat/modelpacks-input/dreamwalker-modelpacks-0.1.0.jar')
    parser.add_argument('--modelpacks-archive', type=Path, default=Path('C:/Users/vakir/Downloads/Dreamwalker-ModelPacks-Fabric-1.20.1.zip'))
    parser.add_argument('--output', type=Path, required=True, help='Ignored project-build report only')
    parser.add_argument('--require-completed', action='store_true')
    args = parser.parse_args()
    args.release_dir = args.release_dir.absolute()
    args.jar = (args.jar or args.release_dir / ('dreamwalker-bb-fabric-1.20.1-' + VERSION + '.jar')).absolute()
    args.world_zip = (args.world_zip or args.release_dir / 'Dreamwalker-BB-v12-Gallery.zip').absolute()
    args.game_xml = (args.game_xml or args.release_dir / 'GAME-TESTS.xml').absolute()
    output = args.output.absolute()
    require(output.resolve().is_relative_to((ROOT / 'build').resolve()), 'Audit output must stay in ignored project build')
    audit = Audit(args)
    for name in ('metadata', 'tool', 'lamps', 'angular', 'staging', 'gallery', 'tests', 'transaction', 'native', 'modelpacks', 'compatibility', 'release'):
        audit.check(name, getattr(audit, name))
    failed = sum(check['status'] == 'FAIL' for check in audit.checks)
    pending = sum(check['status'] == 'PENDING' for check in audit.checks)
    status = 'FAIL' if failed else 'PENDING' if pending else 'COMPLETED_ALL_PASS'
    report = {'schema': 'dreamwalker-v12-delivery-audit-v1', 'status': status,
              'utc': datetime.now(timezone.utc).isoformat(), 'modVersion': VERSION,
              'checksPassed': len(audit.checks) - failed - pending, 'checksFailed': failed, 'checksPending': pending,
              'checks': audit.checks, 'execution': 'Read-only file/ZIP/class/XML/log inspection; no Minecraft, JVM or build launch',
              'completionRule': 'Every integrity, native22-case and real combined-runtime check must PASS'}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes((json.dumps(report, ensure_ascii=False, indent=2) + '\n').encode('utf8'))
    print(json.dumps({'status': status, 'passed': report['checksPassed'], 'failed': failed, 'pending': pending, 'output': str(output)}), flush=True)
    return 1 if failed or args.require_completed and pending else 0


if __name__ == '__main__':
    raise SystemExit(main())
