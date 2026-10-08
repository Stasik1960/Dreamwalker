"""Verify actual current-artifact diagnostics runtimes, local exports and saved reentry.

This is a read-only verifier. It never launches a game, makes a report pass by
declaration, or treats native/dev evidence as an ordinary delivered-JAR run.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import re
import statistics
import sys
import uuid
import zipfile
from pathlib import Path
from package_first_set import ROOT, digest, require, resolve, read, json_bytes, safe_name

SCHEMA = 'dreamwalker-review-v9-diagnostics-proof-v1'
PASS = 'PASS_CURRENT_ARTIFACT_DIAGNOSTICS_ACTUAL_RUNTIME_OFF_ON_REENTER_EXPORTS'
INPUTS = ('jar', 'server_off_report', 'server_on_report', 'server_reenter_report', 'client_report', 'client_reenter_report')


def file_ref(path):
    path = resolve(path, ROOT)
    return {'path': str(path), 'bytes': path.stat().st_size, 'sha256': digest(path)}


def checked_uuid(value):
    parsed = str(uuid.UUID(value))
    require(parsed == value, 'Session/instance UUID must be canonical')
    return parsed


def positive_samples(values, label, minimum=10):
    require(isinstance(values, list) and len(values) >= minimum and all(type(v) is int and v > 0 for v in values),
            'Actual positive raw samples are absent: ' + label)
    ordered = sorted(values)
    return {'samples': len(values), 'mean_ns': sum(values) / len(values),
            'median_ns': statistics.median(ordered), 'p95_ns': ordered[(len(ordered)*95+99)//100-1],
            'p99_ns': ordered[(len(ordered)*99+99)//100-1], 'min_ns': ordered[0], 'max_ns': ordered[-1]}


def nonnegative_cpu_samples(values,label,minimum=10):
    require(isinstance(values,list) and len(values)>=minimum and all(type(v)is int and v>=0 for v in values),
            'Actual nonnegative raw thread CPU samples are absent: '+label)
    ordered=sorted(values)
    return {'samples':len(values),'mean_ns':sum(values)/len(values),'median_ns':statistics.median(ordered),
        'p95_ns':ordered[(len(ordered)*95+99)//100-1],'p99_ns':ordered[(len(ordered)*99+99)//100-1],
        'min_ns':ordered[0],'max_ns':ordered[-1],'zero_clock_deltas':values.count(0),
        'scope':'Actual JVM thread CPU clock deltas; zero can result from clock granularity. No GPU measurement.'}


def checked_server_checks(actual):
    checks = actual.get('checks', {})
    require(actual.get('checksPassed') is True and isinstance(checks, dict) and checks
            and all(value is True for value in checks.values()), 'Actual dedicated QA checks are absent/failed')
    require(actual.get('forcedFlagsRestored') is True, 'Dedicated QA did not restore existing chunk-force flags')


def zip_members(path, required):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)) and archive.testzip() is None, 'Invalid/duplicate diagnostics ZIP')
        for name in names:
            safe_name(name)
            require(archive.getinfo(name).file_size <= 32 * 1024 * 1024, 'Unbounded diagnostics ZIP member')
        require(set(required).issubset(names), 'Diagnostics ZIP lacks required actual members')
        result = {name: archive.read(name) for name in required}
    return result


def decoded(data, label):
    value = json.loads(data.decode('utf8'))
    require(isinstance(value, (dict, list)), 'Unexpected diagnostics JSON structure: ' + label)
    return value


def checked_server_summary(data, session):
    text = data.decode('utf8')
    damaged = re.search(r'(?:Р[ґё°µѕ»]|С[Ѓ‚Њ‹]){2}|(?:Ã.|Â.|Ð.|Ñ.){2}|вЂ', text)
    require('\ufffd' not in text and damaged is None, 'Actual server ZIP summary is unreadable UTF-8/mojibake')
    folded = text.casefold()
    require(len(text.strip()) >= 120 and 'dreamwalker' in folded
            and ('диагност' in folded or 'diagnostic' in folded) and session in text,
            'Actual server ZIP summary lacks readable diagnostic meaning/session identity')
    require(any('gpu' in line.casefold() and 'NOT_MEASURED' in line for line in text.splitlines()),
            'Readable server summary must identify GPU as NOT_MEASURED')
    require(any(('network' in line.casefold() or 'traffic' in line.casefold() or 'сетев' in line.casefold()) and 'NOT_MEASURED' in line
                for line in text.splitlines()), 'Readable server summary must identify unmeasured network scope')
    return {'encoding': 'UTF-8', 'readable': True, 'session_identity_present': True,
            'gpu_scope': 'NOT_MEASURED', 'all_network_scope': 'NOT_MEASURED'}


def check_ack_chain(ack, render, session, instance):
    require(ack.get('sessionId') == session and ack.get('instanceId') == instance and type(ack.get('operation')) is int,
            'Actual server placement acknowledgement identity/operation is absent')
    require(ack.get('action') == 'place' and ack.get('result') == 'COMMITTED' and re.fullmatch(r'\d{5}', ack.get('typeId', '')),
            'Acknowledgement is not an actual committed TEMP type placement')
    require(ack.get('before', {}).get('heldItem') and ack['before'].get('heldTypeId') == ack['typeId'],
            'Actual used item/type was not captured by the server')
    require(render.get('serverPlacementAcknowledgement') == ack and render.get('instanceId') == instance
            and render.get('purpose') == 'world-root-render' and render.get('selectedClientModel')
            and render.get('result') == 'ACTUAL_RENDER_MODEL_SELECTED',
            'Actual renderer chain does not reference the same server operation/instance')
    require(render.get('clientRegistry') == ack.get('after', {}).get('registry'), 'Actual client/server registry differs')


def checked_client_setup(review, instance, acknowledgement):
    original = review.get('originalJoinedGameMode')
    require(original in ('survival', 'creative', 'adventure', 'spectator'), 'Actual original joined-player mode is absent')
    require(review.get('explicitIsolatedCreativeSetupBeforeAllComparisonWindows') is True
            and review.get('originalJoinedGameModeRestored') is True,
            'Actual isolated creative setup/original joined-player mode restoration is absent')
    root = review.get('temporaryRoot')
    require(isinstance(root, list) and len(root) == 3 and all(type(v) is int for v in root)
            and acknowledgement.get('root') == root, 'Temporary root differs from actual acknowledged placement root')
    selected = review.get('ordinaryCreativeBreakSelectedRoot', '')
    require(isinstance(selected, str) and re.fullmatch(r'-?\d+,\s*-?\d+,\s*-?\d+', selected)
            and [int(v.strip()) for v in selected.split(',')] == root
            and review.get('ordinaryCreativeBreakSelectedOwner') == instance,
            'Ordinary creative break did not select the exact acknowledged owner/root')
    require(review.get('temporaryLedgerRemoved') is True, 'Actual temporary owner-contribution cleanup is absent')
    return {'original_joined_game_mode': original, 'comparison_game_mode': 'creative',
            'original_mode_restored': True, 'selected_owner': instance, 'selected_root': root,
            'scope': 'Actual joined client/server player; isolated QA creative setup before all OFF/ON/OFF windows and restoration afterward. Dedicated FakePlayer survival movement proofs are separate.'}


def checked_timing_row(row, raw=False):
    require(isinstance(row,dict) and isinstance(row.get('typeChunkSection'),str), 'Actual sampled timing identity is absent')
    for key in ('measurements','totalNs','retainedSamples','droppedSamples'):
        require(type(row.get(key)) is int and row[key]>=0,'Invalid sampled timing count/value: '+key)
    require(row['retainedSamples']<=512 and row['measurements']==row['retainedSamples']+row['droppedSamples'],
            'Actual sampled timing retained/dropped population does not reconcile')
    if raw:
        values=row.get('rawRetainedSampleNs')
        require(isinstance(values,list) and len(values)==row['retainedSamples'] and all(type(v)is int and v>=0 for v in values),
                'Full local raw timing population is absent/invalid')
        require(sum(values)<=row['totalNs'] and (row['droppedSamples']!=0 or sum(values)==row['totalNs']),
                'Full local raw timing sum differs from total observed elapsed')
        if values:
            ordered=sorted(values)
            for key,expected in [('medianNs',ordered[(len(ordered)-1)//2]),('p95Ns',ordered[(95*len(ordered)+99)//100-1]),('p99Ns',ordered[(99*len(ordered)+99)//100-1])]:
                require(row.get(key)==expected,'Actual local raw timing percentile differs: '+key)
            require(abs(row.get('averageNs',-1)-row['totalNs']/row['measurements'])<=max(1e-6,row['totalNs']/row['measurements']*1e-12),
                    'Actual local timing mean does not use full observed population')
    return row['typeChunkSection']


def gson_packet_text(value):
    """Reconstruct Gson-compatible escaping; accepted receiver bounds remain primary."""
    text=json.dumps(value,ensure_ascii=False,separators=(',',':'))
    for character,escaped in [('<','\\u003c'),('>','\\u003e'),('&','\\u0026'),('=','\\u003d'),("'",'\\u0027'),('\u2028','\\u2028'),('\u2029','\\u2029')]:text=text.replace(character,escaped)
    return text


def checked_client_timing_retention(members,batches,session,artifact_sha,renderer_ack):
    local=decoded(members['local-timings.json'],'full local timing retention')
    require(local.get('schema')=='dw-local-client-timing-populations-v1' and local.get('session')==session,
            'Full local timing member has wrong schema/session')
    require(local.get('windowLimit')==64 and local.get('sectionLimit')==128 and local.get('sampleLimitPerSection')==512,
            'Full local timing limits differ from production contract')
    windows=local.get('windows');sections=local.get('sessionSampledTimings')
    require(isinstance(windows,list) and 0<len(windows)<=64 and local.get('windowsRetained')==len(windows)
            and isinstance(sections,list) and 0<len(sections)<=128,'Full local timing populations absent/out of bound')
    for key in ('windowsDropped','sectionObservationsDropped','windowSectionObservationsDroppedCumulative'):
        require(type(local.get(key))is int and local[key]>=0,'Full local timing dropped count absent: '+key)
    observed_windows=len(windows)+local['windowsDropped']
    require(len(batches)==min(256,observed_windows) and len(windows)==min(64,observed_windows),
            'Actual local timing/wire window retention counts do not reconcile')
    for batch in batches:
        encoded=gson_packet_text(batch)
        require(len(batch)<=32 and len(encoded.encode('utf-16-le'))//2<=16384 and len(encoded.encode('utf8'))<=60000,
                'Actual accepted wire JSON exceeds receiver field/char/UTF8 bounds')
        compact=batch.get('runtimeMetadata',{})
        require(isinstance(compact,dict) and compact.get('productionArtifactSha256')==artifact_sha
                and compact.get('processId') and compact.get('modVersion') and 'modVersions' not in compact
                and 'runtime.json' in compact.get('fullRuntimeMetadata',''), 'Actual network runtime identity is missing/noncompact/stale')
        require(batch.get('session')==session and isinstance(batch.get('timings'),list) and batch['timings'],
                'Actual new-policy wire batch lost all sampled timings')
        require(batch.get('wireBudget',{}).get('wholeJsonSerializations')==2,
                'Actual batch lacks constant whole-JSON serialization policy')
        for timing in batch['timings']:checked_timing_row(timing)
    keyed={};seen=set()
    for window,batch in zip(windows,batches[-len(windows):]):
        require(window.get('session')==session and window.get('timestampUtc')==batch.get('timestampUtc')
                and window.get('windowNs')==batch.get('windowNs') and window.get('wireBudget')==batch.get('wireBudget'),
                'Full timing window does not refer to the exact sent wire window')
        require(window['timestampUtc'] not in seen,'Duplicate local timing window would double-count samples');seen.add(window['timestampUtc'])
        rows=window.get('timings');require(isinstance(rows,list) and 0<len(rows)<=128,'Full timing window rows absent/unbounded')
        keys=[checked_timing_row(row) for row in rows];require(len(keys)==len(set(keys)),'Duplicate timing key in local window')
        counts=batch['wireBudget'].get('timings',{})
        require(counts.get('availableRows')==len(rows) and counts.get('retainedRows')==len(batch['timings'])
                and counts.get('omittedRows')==len(rows)-len(batch['timings']), 'Timing wire omission rows do not reconcile with full local data')
        available=sum(row['measurements'] for row in rows);retained=sum(row['measurements'] for row in batch['timings'])
        require(counts.get('availableSampledInvocations')==available and counts.get('retainedSampledInvocations')==retained
                and counts.get('omittedSampledInvocations')==available-retained, 'Timing wire omission sample population does not reconcile')
        require(all(row in rows for row in batch['timings']),'Wire timing row differs from full retained local window')
        for row in rows:
            entry=keyed.setdefault(row['typeChunkSection'],[0,0]);entry[0]+=row['measurements'];entry[1]+=row['totalNs']
        for name in ('events','errors','placementRenderAcknowledgements'):
            counts=batch['wireBudget'].get(name,{})
            require(type(counts.get('availableRows'))is int and counts['availableRows']>=len(batch[name])
                    and counts.get('retainedRows')==len(batch[name]) and counts.get('omittedRows')==counts['availableRows']-len(batch[name]),
                    'Actual wire section omission population is invalid: '+name)
    partial=decoded(members['partial-window.json'],'partial timing tail')
    for row in partial.get('timings',[]):
        checked_timing_row(row);entry=keyed.setdefault(row['typeChunkSection'],[0,0]);entry[0]+=row['measurements'];entry[1]+=row['totalNs']
    keys=[checked_timing_row(row,True) for row in sections]
    require(len(keys)==len(set(keys)),'Duplicate session sampled timing key')
    if local['windowsDropped']==0:
        for row in sections:
            require(keyed.get(row['typeChunkSection'])==[row['measurements'],row['totalNs']],
                    'Session timing aggregate is not each full window plus partial tail exactly once')
        missing=sum(value[0] for key,value in keyed.items() if key not in keys)
        require(missing==local['sectionObservationsDropped'],'Actual session timing section drops do not reconcile')
    else:
        for row in sections:
            kept=keyed.get(row['typeChunkSection'],[0,0])
            require(row['measurements']>=kept[0] and row['totalNs']>=kept[1], 'Bounded session timing aggregate is smaller than retained windows/tail')
    acknowledgements=decoded(members['placement-render-acknowledgements.json'],'full local renderer ACK cache')
    require(isinstance(acknowledgements,list) and len(acknowledgements)<=512 and renderer_ack in acknowledgements,
            'Exact ordinary renderer ACK is absent from bounded full local ACK member')
    require(all(row.get('session')==session for row in acknowledgements), 'Local renderer ACK cache crosses session UUID')
    return {'status':'PASS_CURRENT_ACCEPTED_WIRE_AND_FULL_BOUNDED_LOCAL_SAMPLED_TIMINGS',
        'session':session,'production_artifact_sha256':artifact_sha,'wire_batches':len(batches),
        'local_windows_retained':len(windows),'local_windows_dropped':local['windowsDropped'],
        'session_sections':len(sections),'session_sampled_invocations':sum(row['measurements'] for row in sections),
        'session_raw_samples_retained':sum(row['retainedSamples'] for row in sections),
        'session_raw_samples_dropped':sum(row['droppedSamples'] for row in sections),
        'wire_sampled_invocations_omitted':sum(row['wireBudget']['timings']['omittedSampledInvocations'] for row in batches),
        'ack_cache_contains_exact_ordinary_model_chain':True,
        'scope':'Exact current ZIP runtime identity; full window/session sample counts reconciled with partial tail once. Views overlap and must not be added; elapsed nested sampled timers are not unique CPU/GPU cost.'}


class Verifier:
    def __init__(self, args):
        self.args = args
        self.sha = args.artifact_sha
        self.require_client_timings = not getattr(args, 'historical_client_timings', False)
        self.primary = []
        self.evidence = []

    def retain(self, path, name=None):
        reference = file_ref(path)
        self.evidence.append(reference)
        if name:
            self.primary.append({'path': reference['path'], 'name': name, 'sha256': reference['sha256']})
        return reference

    def wrapper(self, path, side):
        path = resolve(path, ROOT)
        value = read(path)
        self.retain(path)
        require(value.get('artifact_sha256') == self.sha and value.get('exit_code') == 0 and 'termination' not in value,
                'Diagnostics runtime did not exit normally with the current JAR: ' + str(path))
        require(value.get('status') == ('PASS' if side == 'server' else 'PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT'),
                'Diagnostics ordinary runtime did not PASS: ' + str(path))
        run = resolve(value['run_directory'], ROOT)
        require(run.is_relative_to((ROOT/'build').resolve()), 'Diagnostics game was outside isolated project build')
        matching = [p for p in (run/'mods').glob('*.jar') if digest(p) == self.sha]
        require(len(matching) == 1, 'Actual diagnostics mods do not contain exactly one current production JAR')
        console = resolve(value.get('evidence', run/'launch-console.log'), ROOT)
        require(digest(console) == value['console_sha256'], 'Actual diagnostics console changed')
        self.retain(console, path.stem + '.console.log')
        if side == 'server':
            require(value.get('original_world_loaded') is False and value.get('rp_initialization_count') == 1,
                    'Diagnostics server isolation/RP initialization failed')
        else:
            require(value.get('integrated_save_messages_present') is True, 'Diagnostics client did not save its world')
        return value, run

    def raw(self, wrapper, run, field, stem):
        row = wrapper[field]
        path = resolve(row['path'], ROOT)
        require(path.is_relative_to(run), 'Actual QA output is outside its runtime')
        actual = read(path)
        if 'sha256' in row:
            require(digest(path) == row['sha256'], 'Wrapped actual QA bytes changed')
        if 'result' in row:
            require(actual == row['result'], 'Wrapped QA result differs from actual raw file')
        if 'status' in row:
            require(row['status'] == actual['status'], 'Wrapper/raw diagnostic status differs')
        self.retain(path, stem + '.' + field + '.json')
        return actual

    def source_copy(self, wrapper):
        copy = wrapper.get('derived_world_copy', {})
        require(copy.get('source_unchanged_after_run') is True and copy.get('copy_byte_verification') == 'PASS',
                'Actual diagnostics world-copy/source byte verification failed')
        source = resolve(copy['source'], ROOT)
        require(source.is_relative_to((ROOT/'build').resolve()), 'Diagnostics copied original/out-of-project world')
        for row in copy['files']:
            path = source/row['path']
            require(path.is_relative_to(source) and path.stat().st_size == row['bytes'] and digest(path) == row['sha256'],
                    'Immutable diagnostics input world bytes changed')
        return source

    def server_zip(self, path, session, stem, required_seconds=None):
        path = resolve(path, ROOT)
        require(path.is_relative_to((ROOT/'build').resolve()), 'Server local ZIP is outside isolated runtime')
        members = zip_members(path, ['summary.md', 'session.json', 'events.jsonl', 'measurements.json',
                                     'object-snapshots.json', 'marks.json', 'errors.json', 'client-batches.json', 'environment.json'])
        status = decoded(members['session.json'], 'session')
        summary = checked_server_summary(members['summary.md'], session)
        environment = decoded(members['environment.json'], 'environment')
        measures = decoded(members['measurements.json'], 'measurements')
        require(status.get('sessionId') == session and status.get('side') == 'server' and status.get('enabled') is False,
                'Server ZIP is not the completed requested session')
        require(environment.get('productionArtifactSha256') == self.sha, 'Server export came from another/dev artifact')
        require(environment.get('minecraftVersion') == '1.20.1' and environment.get('loaderVersion') and environment.get('modVersions'),
                'Server export has no actual version/modset metadata')
        require(environment.get('javaVersion') and environment.get('productionVersion') and environment.get('processId'),
                'Server export lacks actual Java/mod/process identity')
        events = [json.loads(line) for line in members['events.jsonl'].decode('utf8').splitlines() if line.strip()]
        require(events and all(row.get('sessionId') == session and row.get('side') == 'server' for row in events),
                'Server export events are absent or from another session')
        operations = [row['operation'] for row in events]
        require(all(type(op) is int for op in operations) and operations == sorted(set(operations)), 'Server operation sequence is not unique/ordered')
        require(measures.get('serverTicks', {}).get('retained', measures.get('serverTicks', {}).get('count', 0)) > 0
                or measures.get('serverTicks', {}).get('samples', 0) > 0, 'Actual server tick measurements are absent')
        require(isinstance(decoded(members['errors.json'], 'errors'), list), 'Error export is not a bounded list')
        require(members['summary.md'].strip() and isinstance(decoded(members['object-snapshots.json'], 'snapshots'), list)
                and decoded(members['marks.json'], 'marks'), 'Server export lacks a human summary/snapshots/actual marker')
        require(measures.get('diagnosticsOwnSynchronousOverhead', {}).get('allCount', 0) > 0
                and measures.get('processSamples') and isinstance(measures.get('ownCodeSampledSections'), dict),
                'Actual diagnostic overhead/process/section measurement populations are absent')
        if required_seconds is not None:
            require(status.get('secondsRequested') == required_seconds and status.get('stopReason') == 'AUTO_DURATION_EXPIRED'
                    and status.get('elapsedNs', 0) >= required_seconds * 1_000_000_000,
                    'Actual default60 session/automatic expiry is not proven')
        self.retain(path, stem + '.server-export.zip')
        return {'path': str(path), 'sha256': digest(path), 'session': status, 'events': events, 'measurements': measures,
                'readable_summary': summary, 'client_batches': decoded(members['client-batches.json'], 'server client batches')}

    def server(self, report, mode):
        wrapper, run = self.wrapper(report, 'server')
        actual = self.raw(wrapper, run, 'diagnostics_review', Path(report).stem)
        require(actual.get('schema') == 'dreamwalker-server-diagnostics-review-v1'
                and actual.get('mode') == mode and actual.get('status') == 'PASS_SERVER_DIAGNOSTICS_' + mode,
                'Actual dedicated diagnostics QA mode/schema did not PASS')
        require(actual.get('defaultSeconds') == 60, 'Server did not verify the default60 duration')
        checked_server_checks(actual)
        checks = actual.get('operatorPermissionChecks')
        require(checks is True or isinstance(checks, dict) and checks and all(v is True for v in checks.values()),
                'Actual operator permission checks are absent/failed')
        before, after = actual.get('balancedTargetStateBefore'), actual.get('balancedTargetStateAfter')
        require(before and before == after, 'Recording changed the bounded target typed state')
        samples = actual.get('observedBaselineTicks')
        if isinstance(samples, dict):
            samples = samples.get('rawNs', samples.get('rawTickIntervalsNs'))
        timing = positive_samples(samples, 'dedicated ' + mode)
        require(actual.get('actualSave') is True, 'Actual dedicated save was not observed')
        source = self.source_copy(wrapper)
        result = {'report': str(resolve(report, ROOT)), 'raw': actual, 'source': str(source), 'run': str(run), 'tick_samples': timing}
        if mode in ('ENABLED', 'REENTER'):
            session = checked_uuid(actual['sessionId'])
            archive = resolve(actual['exportZipPath'], ROOT)
            require(digest(archive) == actual['exportZipSha256'], 'Actual enabled server export bytes changed')
            result['export'] = self.server_zip(archive, session, Path(report).stem, 60 if mode == 'ENABLED' else None)
            require(result['export']['session'].get('secondsRequested') == 60, 'Dedicated command did not request default60')
        if mode != 'ENABLED':
            require(actual.get('diagnosticStatus', {}).get('enabled') is False, 'Recording was active in disabled/reenter observation')
        if mode == 'REENTER':
            require(actual.get('reenterPersistedIdentityEquality') is True and result['export']['session'].get('stopReason') == 'OPERATOR_STOP'
                    and result['export']['session'].get('elapsedNs', 0) >= 8_000_000_000,
                    'Actual persisted reentry identity/manual stop8s is absent')
        return result

    def client(self, report):
        wrapper, run = self.wrapper(report, 'client')
        actual = self.raw(wrapper, run, 'client_review_output', Path(report).stem)
        require(actual.get('normalStopRequested') is True and actual.get('guard') == 'ISOLATED_SAVED_REVIEW_CLIENT_ONLY'
                and actual.get('actualProductionOrigins') and all(row['sha256'] == self.sha for row in actual['actualProductionOrigins']),
                'Actual client review loaded another artifact or failed normal save/stop')
        marker = resolve(wrapper['qa_input']['source'], ROOT)
        require(digest(marker) == wrapper['qa_input']['sha256'] and read(marker).get('productionJarSha256') == self.sha
                and not read(marker).get('diagnosticOnly'), 'Diagnostic patch/stale QA marker cannot gate delivered artifact')
        self.retain(marker)
        exports = self.client_exports(actual.get('diagnosticsActualClient', {}), run, Path(report).stem)
        source = self.source_copy(wrapper)
        world = resolve(actual['actualWorldDirectory'], ROOT)
        require(world.is_relative_to(run), 'Actual saved client world outside run')
        return {'report': str(resolve(report, ROOT)), 'source': str(source), 'world': str(world),
                **exports, 'persistence': world_stability(source, world)}

    def client_exports(self, review, run, stem):
        """Verify one actual linked client/integrated-server export pair.

        This bounded check reads only local diagnostic ZIPs. A caller must gate
        its current ordinary wrapper separately; it does not imply saved-world
        reentry, default60, shader-option verification or manual acceptance.
        """
        run = resolve(run, ROOT)
        require(run.is_relative_to((ROOT/'build').resolve()), 'Client export runtime is outside isolated build')
        require(review.get('schema') == 'dw-actual-client-diagnostics-review-v1'
                and review.get('status') == 'PASS_ACTUAL_ORDINARY_ITEM_SERVER_ACK_RENDER_ACK_CLEANUP_TELEMETRY_EXPORT_OFF_ON_OFF',
                'Actual client diagnostics interaction/export review did not PASS')
        session, instance = checked_uuid(review['sessionId']), checked_uuid(review['instanceId'])
        check_ack_chain(review['actualServerPlacementAcknowledgement'], review['actualRendererAcknowledgement'], session, instance)
        setup = checked_client_setup(review, instance, review['actualServerPlacementAcknowledgement'])
        for field in ('recordingInitiallyOff', 'recordingFinallyOff', 'matchedCameraUnchanged', 'originalHandRestoredBeforeOnWindow', 'temporaryLedgerRemoved', 'ordinaryCreativeBreakPacketSent', 'activeNativeSaveResult'):
            require(review.get(field) is True, 'Actual diagnostic cleanup/save/on-off invariant absent: ' + field)
        require(review.get('temporaryNativeCellsRestored', 0) > 0, 'Actual temporary native restoration was not observed')
        windows = review.get('matchedPresentationWindows', [])
        require([row.get('label') for row in windows] == ['OFF_BEFORE', 'ON_IDENTICAL_CLEANED_SCENE', 'OFF_AFTER'], 'Actual matched off/on/off windows absent')
        timings = []
        for index, window in enumerate(windows):
            require(window.get('diagnosticsEnabledAtEnd') is (index == 1), 'Window recording state differs from its label')
            timings.append({'label': window['label'], **positive_samples(window.get('rawPresentationIntervalsNs'), window['label'])})
        client_zip = resolve(review['clientExport'], ROOT)
        require(client_zip.is_relative_to(run) and client_zip.stat().st_size == review['clientExportBytes'], 'Actual local client ZIP path/size differs')
        retention_required=self.require_client_timings or any(row.get('wireBudget') for row in review['actualClientBatches'])
        names=['summary.md', 'summary.json', 'runtime.json', 'graphics.json', 'client-batches.json', 'errors.json', 'partial-window.json', 'tail-events.json']
        if retention_required:names.extend(['local-timings.json','placement-render-acknowledgements.json'])
        members = zip_members(client_zip, names)
        summary, runtime = decoded(members['summary.json'], 'client summary'), decoded(members['runtime.json'], 'client runtime')
        require(summary.get('session') == session and summary.get('side') == 'client' and runtime.get('productionArtifactSha256') == self.sha,
                'Actual client export session/artifact does not match acknowledged placement')
        batches = decoded(members['client-batches.json'], 'client batches')
        require(batches == review['actualClientBatches'] and len(batches) >= 3 and all(row.get('session') == session and row.get('side') == 'client' for row in batches),
                'Actual client local ZIP batches differ from reviewed session')
        frames = sum(row.get('frames', {}).get('measurements', 0) for row in batches)
        require(frames == review['actualFrameIntervalsRecorded'] and frames >= 10, 'Actual frame sample population differs/absent')
        retention=checked_client_timing_retention(members,batches,session,self.sha,review['actualRendererAcknowledgement']) if retention_required else None
        graphics = decoded(members['graphics.json'], 'graphics')
        require(graphics and runtime.get('modVersions') and runtime.get('loaderVersion'), 'Actual client graphics/version/modset metadata absent')
        server_zip = resolve(review['serverExport'], ROOT)
        require(server_zip.is_relative_to(run) and server_zip.stat().st_size == review['serverExportBytes'], 'Actual integrated server ZIP path/size differs')
        integrated = self.server_zip(server_zip, session, stem + '.integrated')
        require(integrated['session'].get('stopReason') == 'AUTO_DURATION_EXPIRED', 'Integrated session did not stop automatically')
        require(review['serverStatusAfterAutomaticExpiry'].get('sessionId') == session
                and review['serverStatusAfterAutomaticExpiry'].get('enabled') is False,
                'Client-reported automatic expiry belongs to another/active server session')
        acknowledgement = review['actualServerPlacementAcknowledgement']
        require(acknowledgement in integrated['events'], 'Actual server ZIP does not contain the acknowledged placement operation')
        delivered = integrated['client_batches']
        require(delivered and all(row.get('sessionId') == session and row.get('batch') in batches
                                  for row in delivered), 'Linked server ZIP has no matching actual client telemetry payloads')
        self.retain(client_zip, stem + '.client-export.zip')
        result={'session': session,
                'timings': timings, 'actual_joined_player_setup': setup, 'acknowledgement': review['actualServerPlacementAcknowledgement'],
                'renderer_acknowledgement': review['actualRendererAcknowledgement'], 'graphics': graphics,
                'client_export': file_ref(client_zip), 'server_export': file_ref(server_zip)}
        if retention is not None:result['client_timing_retention']=retention
        return result


def world_stability(before, after):
    """Independent bounded scene comparison; no full-world bytes/vanilla timer equality claim."""
    from archive_first_set_scene import inventory
    from verify_client_scene_persistence import full_rp_entities, typed_sha
    from verify_source_review_saved import typed_diff
    from world_io import compound, read_nbt
    old_roots, old_bes, _ = inventory(before)
    new_roots, new_bes, _ = inventory(after)
    require(old_roots and old_roots == new_roots, 'Diagnostic on/off/save altered existing architectural states')
    require(old_bes == new_bes, 'Diagnostic temporary lifecycle/save altered existing full typed block entities')
    data = []
    for world in (before, after):
        data.append({path.name: read_nbt(path) for path in (world/'data').glob('*.dat') if path.name.startswith(('bloodborne_dw', 'bloodborne_rp'))})
    require(data[0] == data[1], 'Diagnostic lifecycle changed DW/RP full typed saved graph/owner data')
    old_rp, new_rp = full_rp_entities(before), full_rp_entities(after)
    require(old_rp and set(old_rp) == set(new_rp), 'Diagnostic lifecycle changed pre-existing RP instance set')
    rows = []
    permitted = {'Fire', 'Air', 'PortalCooldown', 'OnGround', 'FallDistance', 'Motion', 'TicksFrozen'}
    for key, old in sorted(old_rp.items()):
        new = new_rp[key]
        fields, saved = compound(old), compound(new)
        require(all(fields.get(name) == saved.get(name) for name in ('id', 'UUID', 'Pos', 'Rotation')), 'Diagnostic lifecycle changed RP identity/pose')
        changes = []
        typed_diff(old, new, 'rp/' + key, changes)
        require(all(row['path'].split('/')[2] in permitted for row in changes), 'Diagnostic lifecycle changed RP role/source typed data')
        rows.append({'uuid': key, 'registry': fields['id'].value, 'typed_source_sha256': typed_sha(old), 'permitted_vanilla_runtime_differences': changes})
    return {'status': 'PASS_BOUNDED_SCENE_STATE_TYPED_OWNERS_GRAPH_RP_IDENTITY_AND_ROLE_PERSISTENCE',
            'architecture_roots': len(old_roots), 'full_typed_block_entities': len(old_bes), 'saved_data_files': sorted(data[0]), 'rp_instances': rows,
            'scope': 'Existing architectural states, every existing block entity, DW/RP typed graph data, RP identity/pose/role/provenance; enumerated vanilla timers separately. Not all world bytes/DFU/players/entities.'}


def run(args):
    require(re.fullmatch(r'[0-9a-f]{64}', args.artifact_sha) and digest(args.jar) == args.artifact_sha, 'Exact frozen production SHA/JAR required')
    verifier = Verifier(args)
    off, on, reenter = [verifier.server(getattr(args, field), mode) for field, mode in
                         [('server_off_report', 'DISABLED'), ('server_on_report', 'ENABLED'), ('server_reenter_report', 'REENTER')]]
    require(off['source'] == on['source'] and off['raw']['balancedTargetStateBefore'] == on['raw']['balancedTargetStateBefore'], 'Standalone off/on runs do not share exact baseline/target state')
    require(resolve(reenter['source'], ROOT) == resolve(on['run'], ROOT)/'isolated-smoke-world'
            and reenter['raw']['balancedTargetStateBefore'] == on['raw']['balancedTargetStateAfter'], 'Actual standalone reentry did not use the previously saved enabled world/identity')
    require(reenter['raw']['sessionId'] != on['raw']['sessionId'], 'Actual dedicated reentry reused the previous session UUID')
    reenter['persistence'] = world_stability(resolve(reenter['source'], ROOT), resolve(reenter['run'], ROOT)/'isolated-smoke-world')
    client, client_reenter = verifier.client(args.client_report), verifier.client(args.client_reenter_report)
    require(client['session'] != client_reenter['session'] and client_reenter['source'] == client['world'], 'Actual client reentry did not reopen its prior saved world with a new/off-default session')
    inputs = {name: file_ref(getattr(args, name)) for name in INPUTS}
    result={'schema': SCHEMA, 'status': PASS, 'production_jar_sha256': args.artifact_sha, 'inputs': inputs,
            'dedicated_server': {'off': off, 'on': on, 'reenter': reenter}, 'client': client, 'client_reenter': client_reenter,
            'evidence': verifier.evidence, 'primary': verifier.primary,
            'default60_automatic_expiry': 'PASS_ACTUAL_DEDICATED_DEFAULT60_SESSION', 'saved_reentry': 'PASS_ACTUAL_SERVER_AND_CLIENT_PRIOR_SAVED_WORLD',
            'off_on_comparison': 'PASS_BALANCED_TYPED_TARGET_STATE_AND_RAW_MATCHED_PRESENTATION_WINDOWS',
            'coverage': {'ordinary_item_model_chain': 'ACTUAL90010_ARCHITECTURE_ITEM_SERVER_ACK_RENDER_ACK', 'rp_new_placement_model_chain': 'NOT_PROBED_BY_THIS_QA_STAGE; loaded RP resource events do not imply observed placement item',
                         'gpu_duration': 'NOT_MEASURED', 'all_mod_network_bytes': 'NOT_MEASURED', 'performance_attribution': 'NO_EXACT_CAUSAL_OVERHEAD_OR_OBJECT_FPS_PERCENTAGE'},
            'manual_visual_acceptance': 'PENDING_USER_REVIEW', 'full_task_status': 'NOT_READY_FULL_TASK'}
    if verifier.require_client_timings:
        result['client_timing_retention_required']=True
        result['client_timing_retention']={'status':'PASS_CURRENT_WIRE_AND_LOCAL_RETENTION_BOTH_ACTUAL_CLIENT_RUNS',
            'client':client['client_timing_retention'],'client_reenter':client_reenter['client_timing_retention']}
    return result


def verify_saved_report(path, jar, artifact_sha):
    document = read(resolve(path, ROOT))
    require(document.get('schema') == SCHEMA and document.get('status') == PASS and document.get('production_jar_sha256') == artifact_sha,
            'Current diagnostics proof is absent/failed/stale')
    inputs = document['inputs']
    for name in INPUTS:
        actual = file_ref(inputs[name]['path'])
        require(actual == inputs[name], 'Diagnostics input bytes changed since independent verification: ' + name)
    require(resolve(inputs['jar']['path'], ROOT) == resolve(jar, ROOT), 'Diagnostics proof gates another production path')
    arguments = argparse.Namespace(artifact_sha=artifact_sha, historical_client_timings=not document.get('client_timing_retention_required',False), **{name: resolve(row['path'], ROOT) for name, row in inputs.items()})
    require(run(arguments) == document, 'Independent diagnostics proof no longer matches actual runtimes/ZIPs/worlds')
    return document


def release_gate(args, artifact_sha):
    """Historical read-only checks may omit proof; no writer/collector/package may do so."""
    path = getattr(args, 'diagnostics_report', None)
    historical = getattr(args, 'historical_check_without_diagnostics', False)
    require(not historical or getattr(args, 'check_only', False) and not getattr(args, 'collect_primary_only', False),
            'Historical diagnostics omission is permitted only for a read-only --check-only')
    if path is None:
        require(historical, 'Final prototype.4 V9 requires --diagnostics-report from actual current-artifact on/off/reentry/linked exports')
        return {'status': 'NOT_CHECKED_HISTORICAL_ONLY_REQUIRED_FOR_FINAL', 'primary': []}
    require(not historical, 'Historical omission flag conflicts with an explicit current diagnostics proof')
    proof=verify_saved_report(path, args.jar, artifact_sha)
    require(proof.get('client_timing_retention_required') is True
            and proof.get('client_timing_retention',{}).get('status')=='PASS_CURRENT_WIRE_AND_LOCAL_RETENTION_BOTH_ACTUAL_CLIENT_RUNS',
            'Final V9 requires actual current retained client wire/local timings; candidate11 compatibility is historical only')
    return proof


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in INPUTS:
        parser.add_argument('--' + name.replace('_', '-'), type=Path, required=True)
    parser.add_argument('--artifact-sha', required=True)
    parser.add_argument('--historical-client-timings',action='store_true',help='Read-only historical candidate11 proof without fixed timing retention; cannot gate final checkpoint/primary/package')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    for name in INPUTS:
        setattr(args, name, resolve(getattr(args, name), ROOT))
    args.output = resolve(args.output, ROOT)
    require(args.output.is_relative_to((ROOT/'reports').resolve()) and not args.output.exists(), 'Use a fresh project reports path; do not overwrite historical proof')
    proof = run(args)
    args.output.write_bytes(json_bytes(proof))
    print(json.dumps({'status': proof['status'], 'artifact_sha256': args.artifact_sha, 'report': str(args.output)}))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError, zipfile.BadZipFile) as failure:
        print(json.dumps({'status': 'FAIL_CURRENT_DIAGNOSTICS_PROOF', 'error': str(failure)}), file=sys.stderr)
        raise SystemExit(1)
