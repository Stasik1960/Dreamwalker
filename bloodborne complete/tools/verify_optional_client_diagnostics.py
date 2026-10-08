"""Bounded read-only proof of an optional completed client's linked diagnostic ZIPs.

No world decoding, Minecraft launch or production JAR hashing occurs here.
The mandatory default60/prior-world reentry proof remains separate.
"""
from __future__ import annotations
import argparse
import json
import sys
from pathlib import Path
from package_first_set import ROOT, digest, read, require, resolve, json_bytes
from package_review_v9 import optional_client_diagnostic_exports
from verify_review_v9_diagnostics import file_ref, positive_samples, nonnegative_cpu_samples


def run(args):
    path=resolve(args.wrapper,ROOT);wrapper=read(path)
    require(wrapper.get('artifact_sha256')==args.artifact_sha
        and wrapper.get('status')=='PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT'
        and wrapper.get('exit_code')==0 and 'termination' not in wrapper
        and wrapper.get('integrated_save_messages_present') is True,
        'Optional client is not a completed current-artifact ordinary save/exit run')
    runtime=resolve(wrapper['run_directory'],ROOT)
    require(runtime.is_relative_to((ROOT/'build').resolve()),'Optional client runtime outside isolated build')
    row=wrapper['client_review_output'];raw=resolve(row['path'],ROOT)
    require(raw.is_relative_to(runtime) and digest(raw)==row['sha256'] and read(raw)==row['result'],
        'Optional client actual raw JSON changed')
    actual=row['result']
    require(actual.get('normalStopRequested') is True
        and actual.get('guard')=='ISOLATED_SAVED_REVIEW_CLIENT_ONLY'
        and actual.get('actualProductionOrigins')
        and all(origin['sha256']==args.artifact_sha for origin in actual['actualProductionOrigins']),
        'Actual optional client did not load the frozen production normally')
    marker=resolve(wrapper['qa_input']['source'],ROOT)
    require(marker.is_relative_to((ROOT/'build').resolve())
        and digest(marker)==wrapper['qa_input']['sha256'], 'Optional client marker bytes changed')
    document=read(marker)
    require(document.get('productionJarSha256')==args.artifact_sha and not document.get('diagnosticOnly'),
        'Optional client used a patched/historical marker')
    checked=optional_client_diagnostic_exports(actual,runtime,args.artifact_sha,path.stem,getattr(args,'historical_client_timings',False))
    require(checked is not None,'Optional actual diagnostic stage is absent')
    review=actual['diagnosticsActualClient'];windows=[]
    for window in review['matchedPresentationWindows']:
        windows.append({'label':window['label'],
            'presentation':positive_samples(window['rawPresentationIntervalsNs'],window['label']),
            'render_thread_cpu':nonnegative_cpu_samples(window['rawRenderThreadCpuIntervalsNs'],window['label']+' CPU')})
    return {'schema':'dreamwalker-optional-client-diagnostics-exports-v1',
        'status':checked['status'],'production_jar_sha256':args.artifact_sha,
        'wrapper':file_ref(path),'raw_review':file_ref(raw),'marker':file_ref(marker),
        'profile':wrapper['profile'],'heap_arguments':[value for value in wrapper['command'] if value.startswith('-Xmx')],
        'root_mod_jars':len(list((runtime/'mods').glob('*.jar'))),
        'session':checked['session'],'client_export':checked['client_export'],'server_export':checked['server_export'],
        'primary':checked['primary'],'timings':windows,'actual_joined_player_setup':checked['actual_joined_player_setup'],
        'client_timing_retention':checked['client_timing_retention'],
        'temporary_native_cells_restored':review['temporaryNativeCellsRestored'],
        'optional_iris_observation':checked['optional_iris_observation'],
        'iris_full_options_and_counter_gate':'PRESENT_REQUIRES_SEPARATE_SHADER_GATE' if actual.get('irisRuntime') else 'NOT_PROBED_MISSING_REQUESTED_IRIS_RUNTIME_STAGE',
        'copied_shader_settings':{'bytes_identical':wrapper.get('shader_profile',{}).get('settings',{}).get('bytes_identical'),
            'sha256':wrapper.get('shader_profile',{}).get('settings',{}).get('copy_sha256'),
            'static_setting_count':wrapper.get('shader_profile',{}).get('settings',{}).get('static_option_audit',{}).get('provided_setting_count')},
        'actual_loaded_rp_probe':actual.get('rpV9ActualClient'),
        'scope':checked['scope'],
        'limitations':{'gpu_duration':'NOT_MEASURED','all_network_bytes':'NOT_MEASURED',
            'rp_new_item_placement_model_ack':'NOT_PROBED','manual_visual_gameplay':'PENDING_USER_REVIEW',
            'full_profile_4gib_compatibility':'NOT_PROVEN','causal_object_fps_cost':'NOT_MEASURED'}}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--wrapper',type=Path,required=True)
    parser.add_argument('--artifact-sha',required=True)
    parser.add_argument('--historical-client-timings',action='store_true',help='Candidate11 export history only; final package does not allow this omission')
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();output=resolve(args.output,ROOT)
    require(output.is_relative_to((ROOT/'reports').resolve()) and not output.exists(),
        'Use a fresh project report; historical proof is immutable')
    proof=run(args);output.write_bytes(json_bytes(proof))
    print(json.dumps({'status':proof['status'],'session':proof['session'],'output':str(output)}))


if __name__=='__main__':
    try:main()
    except (ValueError,KeyError,OSError) as failure:
        print(json.dumps({'status':'FAIL_OPTIONAL_ACTUAL_CLIENT_DIAGNOSTIC_EXPORTS','error':str(failure)}),file=sys.stderr)
        raise SystemExit(1)
