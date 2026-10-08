"""Synthetic in-memory negative gate tests; these are not Minecraft/runtime evidence."""
import argparse
import copy
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from verify_review_v9_diagnostics import check_ack_chain, checked_client_setup, checked_server_checks, checked_server_summary, checked_client_timing_retention, positive_samples, nonnegative_cpu_samples, release_gate, zip_members
from package_first_set import ROOT
from package_review_v9 import optional_client_diagnostic_exports


class DiagnosticGateTests(unittest.TestCase):
    def timing_fixture(self):
        session='e8bdb736-9406-4c45-905a-925060f6d801';sha='0'*64
        def row(key,values,raw=False):
            ordered=sorted(values);result={'typeChunkSection':key,'measurements':len(values),'totalNs':sum(values),
                'retainedSamples':len(values),'droppedSamples':0,'averageNs':sum(values)/len(values),
                'medianNs':ordered[(len(values)-1)//2],'p95Ns':ordered[(len(values)*95+99)//100-1],
                'p99Ns':ordered[(len(values)*99+99)//100-1]}
            if raw:result['rawRetainedSampleNs']=values
            return result
        first,second=row('90010|0,0|render',[1,2]),row('91086|0,0|render',[5])
        counts={'wholeJsonSerializations':2,'timings':{'availableRows':2,'retainedRows':1,'omittedRows':1,
            'availableSampledInvocations':3,'retainedSampledInvocations':2,'omittedSampledInvocations':1}}
        for key in ('events','errors','placementRenderAcknowledgements'):counts[key]={'availableRows':0,'retainedRows':0,'omittedRows':0}
        batch={'session':session,'timestampUtc':'2026-10-08T12:00:00Z','windowNs':1_000_000_000,'timings':[first],
            'events':[],'errors':[],'placementRenderAcknowledgements':[], 'wireBudget':counts,
            'runtimeMetadata':{'productionArtifactSha256':sha,'modVersion':'unit','processId':1,'fullRuntimeMetadata':'LOCAL_CLIENT_ZIP/runtime.json'}}
        render={'session':session,'purpose':'world-root-render','instanceId':'unit-owner','operationNumber':7}
        local={'schema':'dw-local-client-timing-populations-v1','session':session,'windowLimit':64,'sectionLimit':128,
            'sampleLimitPerSection':512,'windowsRetained':1,'windowsDropped':0,'sectionObservationsDropped':0,
            'windowSectionObservationsDroppedCumulative':0,
            'windows':[{'session':session,'timestampUtc':batch['timestampUtc'],'windowNs':batch['windowNs'],'wireBudget':counts,'timings':[first,second]}],
            'sessionSampledTimings':[row(first['typeChunkSection'],[1,2,3,4],True),row(second['typeChunkSection'],[5],True)]}
        partial={'timings':[row(first['typeChunkSection'],[3,4])]}
        members={key:json.dumps(value).encode('utf8') for key,value in {'local-timings.json':local,
            'partial-window.json':partial,'placement-render-acknowledgements.json':[render]}.items()}
        return members,[batch],session,sha,render,row

    def test_final_emission_without_actual_report_rejected(self):
        for check in (False, True):
            args=argparse.Namespace(diagnostics_report=None,check_only=check,historical_check_without_diagnostics=False)
            with self.assertRaisesRegex(ValueError,'requires --diagnostics-report'):
                release_gate(args,'0'*64)

    def test_historical_omission_has_no_release_status(self):
        args=argparse.Namespace(diagnostics_report=None,check_only=True,historical_check_without_diagnostics=True)
        result=release_gate(args,'0'*64)
        self.assertEqual(result['status'],'NOT_CHECKED_HISTORICAL_ONLY_REQUIRED_FOR_FINAL')
        self.assertEqual(result['primary'],[])

    def test_historical_flag_cannot_write_or_collect(self):
        for check,collect in ((False,False),(True,True)):
            args=argparse.Namespace(diagnostics_report=None,check_only=check,collect_primary_only=collect,historical_check_without_diagnostics=True)
            with self.assertRaisesRegex(ValueError,'only for a read-only'):
                release_gate(args,'0'*64)

    def test_actual_item_operation_cannot_be_substituted_by_later_hand(self):
        ack={'sessionId':'session-unit','operation':7,'instanceId':'instance-unit','action':'place','result':'COMMITTED',
             'typeId':'90010','before':{'heldItem':'bloodborne_dw:prototype_glass_window_02','heldTypeId':'90010'},
             'after':{'registry':'bloodborne_dw:prototype_glass_window_02'}}
        render={'serverPlacementAcknowledgement':copy.deepcopy(ack),'instanceId':'instance-unit','purpose':'world-root-render',
                'selectedClientModel':'bloodborne_dw:block/example','result':'ACTUAL_RENDER_MODEL_SELECTED',
                'clientRegistry':ack['after']['registry'],'itemInMainHandAtObservation':'minecraft:air'}
        check_ack_chain(ack,render,'session-unit','instance-unit')
        render['serverPlacementAcknowledgement']['operation']=8
        with self.assertRaisesRegex(ValueError,'same server operation'):
            check_ack_chain(ack,render,'session-unit','instance-unit')

    def test_frames_require_real_positive_raw_population(self):
        for raw in ([],[1]*9,[0]*10,[1.0]*10):
            with self.assertRaises(ValueError):positive_samples(raw,'synthetic')
        self.assertEqual(positive_samples(list(range(1,11)),'synthetic')['mean_ns'],5.5)

    def test_thread_cpu_clock_can_repeat_without_inventing_positive_duration(self):
        raw=[0,15_625_000]*5
        result=nonnegative_cpu_samples(raw,'synthetic CPU clock')
        self.assertEqual(result['zero_clock_deltas'],5)
        self.assertEqual(result['mean_ns'],7_812_500)
        with self.assertRaises(ValueError):nonnegative_cpu_samples([0]*9+[-1],'synthetic invalid CPU')
        with self.assertRaises(ValueError):positive_samples(raw,'synthetic frame wall interval')

    def test_declaration_cannot_hide_failed_actual_server_check(self):
        actual={'checksPassed':True,'forcedFlagsRestored':True,'checks':{'actualSaveDuringWindow':True}}
        checked_server_checks(actual)
        actual['checks']['actualSaveDuringWindow']=False
        with self.assertRaisesRegex(ValueError,'checks are absent/failed'):checked_server_checks(actual)
        actual['checks']['actualSaveDuringWindow']=True
        actual['forcedFlagsRestored']=False
        with self.assertRaisesRegex(ValueError,'chunk-force flags'):checked_server_checks(actual)

    def test_joined_creative_setup_restore_and_exact_break_owner_required(self):
        actual={'originalJoinedGameMode':'survival','explicitIsolatedCreativeSetupBeforeAllComparisonWindows':True,
                'originalJoinedGameModeRestored':True,'temporaryRoot':[0,64,4],
                'ordinaryCreativeBreakSelectedRoot':'0, 64, 4','ordinaryCreativeBreakSelectedOwner':'instance-unit',
                'temporaryLedgerRemoved':True}
        acknowledgement={'root':[0,64,4]}
        self.assertEqual(checked_client_setup(actual,'instance-unit',acknowledgement)['comparison_game_mode'],'creative')
        for key,value,reason in [('originalJoinedGameModeRestored',False,'mode restoration'),
                                 ('explicitIsolatedCreativeSetupBeforeAllComparisonWindows',False,'creative setup'),
                                 ('ordinaryCreativeBreakSelectedOwner','foreign-unit','exact acknowledged owner/root'),
                                 ('ordinaryCreativeBreakSelectedRoot','0, 65, 4','exact acknowledged owner/root'),
                                 ('temporaryLedgerRemoved',False,'contribution cleanup')]:
            invalid=copy.deepcopy(actual);invalid[key]=value
            with self.assertRaisesRegex(ValueError,reason):checked_client_setup(invalid,'instance-unit',acknowledgement)

    def test_duplicate_or_escaping_zip_member_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'unit-only.zip'
            with zipfile.ZipFile(path,'w') as archive:archive.writestr('../escape.json','{}')
            with self.assertRaisesRegex(ValueError,'Unsafe archive entry'):zip_members(path,['../escape.json'])

    def test_server_summary_rejects_readable_utf8_bytes_that_are_mojibake(self):
        session='e8bdb736-9406-4c45-905a-925060f6d801'
        readable=('# Dreamwalker: диагностика\n\nСеанс: '+session+'\n'
                  'GPU duration: NOT_MEASURED; подготовка CPU не измеряет GPU.\n'
                  'All network bytes: NOT_MEASURED; только payload собственных каналов.\n'
                  'Выборки ограничены, сохранённые и отброшенные значения перечислены в JSON.\n')
        self.assertTrue(checked_server_summary(readable.encode('utf8'),session)['readable'])
        corrupted=readable.encode('utf8').decode('cp1251').encode('utf8')
        with self.assertRaisesRegex(ValueError,'unreadable UTF-8/mojibake'):checked_server_summary(corrupted,session)

    def test_optional_full_client_exports_reject_foreign_server_session(self):
        session='e8bdb736-9406-4c45-905a-925060f6d801'
        instance='939d0ddf-1aca-40be-a70a-fd6c85405bcb'
        foreign='c6f4c93e-582d-48d7-b6a1-d1ea73d88e46';sha='0'*64
        ack={'sessionId':session,'side':'server','operation':7,'instanceId':instance,'action':'place','result':'COMMITTED',
             'typeId':'90010','root':[0,64,4],'before':{'heldItem':'bloodborne_dw:prototype_glass_window_02','heldTypeId':'90010'},
             'after':{'registry':'bloodborne_dw:prototype_glass_window_02'}}
        render={'serverPlacementAcknowledgement':copy.deepcopy(ack),'instanceId':instance,'purpose':'world-root-render',
                'selectedClientModel':'bloodborne_dw:block/example','result':'ACTUAL_RENDER_MODEL_SELECTED',
                'clientRegistry':ack['after']['registry']}
        batches=[{'session':session,'side':'client','frames':{'measurements':10},'sequence':i} for i in range(3)]
        review={'schema':'dw-actual-client-diagnostics-review-v1',
                'status':'PASS_ACTUAL_ORDINARY_ITEM_SERVER_ACK_RENDER_ACK_CLEANUP_TELEMETRY_EXPORT_OFF_ON_OFF',
                'sessionId':session,'instanceId':instance,'actualServerPlacementAcknowledgement':ack,'actualRendererAcknowledgement':render,
                'originalJoinedGameMode':'survival','explicitIsolatedCreativeSetupBeforeAllComparisonWindows':True,
                'originalJoinedGameModeRestored':True,'temporaryRoot':[0,64,4],
                'ordinaryCreativeBreakSelectedRoot':'0, 64, 4','ordinaryCreativeBreakSelectedOwner':instance,
                'temporaryNativeCellsRestored':10,'actualClientBatches':batches,'actualFrameIntervalsRecorded':30,
                'serverStatusAfterAutomaticExpiry':{'sessionId':session,'enabled':False},
                'matchedPresentationWindows':[{'label':label,'diagnosticsEnabledAtEnd':i==1,'rawPresentationIntervalsNs':[1]*10}
                   for i,label in enumerate(['OFF_BEFORE','ON_IDENTICAL_CLEANED_SCENE','OFF_AFTER'])]}
        for name in ('recordingInitiallyOff','recordingFinallyOff','matchedCameraUnchanged','originalHandRestoredBeforeOnWindow',
                     'temporaryLedgerRemoved','ordinaryCreativeBreakPacketSent','activeNativeSaveResult'):review[name]=True
        with tempfile.TemporaryDirectory(prefix='diagnostic-export-unit-',dir=ROOT/'build') as directory:
            run=Path(directory);client=run/'client.zip';server=run/'server.zip'
            with zipfile.ZipFile(client,'w') as archive:
                for name,value in {'summary.json':{'session':session,'side':'client'},
                    'runtime.json':{'productionArtifactSha256':sha,'loaderVersion':'unit','modVersions':{'unit':'test'}},
                    'graphics.json':{'driver':'synthetic'},'client-batches.json':batches,'errors.json':[],
                    'partial-window.json':{},'tail-events.json':[]}.items():archive.writestr(name,json.dumps(value))
                archive.writestr('summary.md','Synthetic unit fixture; not runtime evidence.')
            def write_server(identity):
                with zipfile.ZipFile(server,'w') as archive:
                    for name,value in {'session.json':{'sessionId':identity,'side':'server','enabled':False,'stopReason':'AUTO_DURATION_EXPIRED'},
                        'measurements.json':{'serverTicks':{'retained':1},'diagnosticsOwnSynchronousOverhead':{'allCount':1},'processSamples':[1],'ownCodeSampledSections':{}},
                        'object-snapshots.json':[],'marks.json':[{'unit':True}],'errors.json':[],
                        'client-batches.json':[{'sessionId':session,'batch':row} for row in batches],
                        'environment.json':{'productionArtifactSha256':sha,'minecraftVersion':'1.20.1','loaderVersion':'unit',
                            'modVersions':{'unit':'test'},'javaVersion':'17','productionVersion':'unit','processId':1}}.items():archive.writestr(name,json.dumps(value))
                    archive.writestr('events.jsonl',json.dumps(ack)+'\n')
                    archive.writestr('summary.md','# Dreamwalker diagnostics session\nSession: '+session+'\nGPU timing: NOT_MEASURED.\nAll-mod / vanilla chunk / DataTracker / TCP traffic: NOT_MEASURED.\nSynthetic fixture; not actual runtime evidence.\n')
            review.update(clientExport=str(client),clientExportBytes=client.stat().st_size,serverExport=str(server))
            write_server(session);review['serverExportBytes']=server.stat().st_size
            verified=optional_client_diagnostic_exports({'diagnosticsActualClient':review},run,sha,'unit',historical=True)
            self.assertEqual(len(verified['primary']),2)
            write_server(foreign);review['serverExportBytes']=server.stat().st_size
            with self.assertRaisesRegex(ValueError,'completed requested session'):
                optional_client_diagnostic_exports({'diagnosticsActualClient':review},run,sha,'unit',historical=True)

    def test_wire_omission_keeps_full_local_population_without_tail_doublecount(self):
        members,batches,session,sha,render,_=self.timing_fixture()
        result=checked_client_timing_retention(members,batches,session,sha,render)
        self.assertEqual(result['session_sampled_invocations'],5)
        self.assertEqual(result['wire_sampled_invocations_omitted'],1)
        self.assertEqual(result['session_raw_samples_retained'],5)
        missing=copy.deepcopy(batches);missing[0]['timings']=[]
        with self.assertRaisesRegex(ValueError,'lost all sampled timings'):
            checked_client_timing_retention(members,missing,session,sha,render)

    def test_session_aggregate_cannot_repeat_window_samples_even_with_valid_percentiles(self):
        members,batches,session,sha,render,row=self.timing_fixture()
        local=json.loads(members['local-timings.json'])
        local['sessionSampledTimings'][0]=row('90010|0,0|render',[1,2,3,4,1,2],True)
        members['local-timings.json']=json.dumps(local).encode('utf8')
        with self.assertRaisesRegex(ValueError,'partial tail exactly once'):
            checked_client_timing_retention(members,batches,session,sha,render)

    def test_declared_retention_cannot_hide_wrong_packet_identity_or_missing_model_ack(self):
        members,batches,session,sha,render,_=self.timing_fixture()
        changed=copy.deepcopy(batches);changed[0]['runtimeMetadata']['productionArtifactSha256']='1'*64
        with self.assertRaisesRegex(ValueError,'identity is missing/noncompact/stale'):
            checked_client_timing_retention(members,changed,session,sha,render)
        changed=copy.deepcopy(batches)
        for i in range(33):changed[0]['extra'+str(i)]=i
        with self.assertRaisesRegex(ValueError,'field/char/UTF8 bounds'):
            checked_client_timing_retention(members,changed,session,sha,render)
        members['placement-render-acknowledgements.json']=b'[]'
        with self.assertRaisesRegex(ValueError,'Exact ordinary renderer ACK'):
            checked_client_timing_retention(members,batches,session,sha,render)


if __name__=='__main__':unittest.main()
