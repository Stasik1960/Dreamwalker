"""Compare observed log error groups; this is not full compatibility acceptance."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
    paths=[ROOT/'reports/CLIENT_ORIGINAL_RP_BASELINE.json',ROOT/'reports/CLIENT_FULL_CLIENT.json']
    baseline,combined=[json.loads(p.read_text(encoding='utf-8')) for p in paths]
    assert baseline['profile']==combined['profile']=='full_client'
    assert baseline['loader']==combined['loader'] and baseline['modset_profile_sha256']==combined['modset_profile_sha256']
    a,b=baseline['error_groups'],combined['error_groups']
    difference=[{'group':key,'baseline':a.get(key,0),'combined':b.get(key,0)} for key in sorted(set(a)|set(b)) if a.get(key,0)!=b.get(key,0)]
    result={'schema':'dreamwalker-client-baseline-comparison-v1','status':'ERROR_GROUPS_EQUAL' if not difference else 'ERROR_GROUPS_DIFFER',
        'compatibility_acceptance':'NOT_ACCEPTED','baseline_artifact_sha256':baseline['artifact_sha256'],
        'combined_artifact_sha256':combined['artifact_sha256'],'selected_modset_sha256':combined['modset_profile_sha256'],
        'baseline_error_lines':baseline['error_line_count'],'combined_error_lines':combined['error_line_count'],'differences':difference,
        'reports':[{'path':str(p),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in paths],
        'limitations':['Baseline uses original standalone RP and same 109-JAR selected profile, default QA configs, no shaders.',
            'Baseline copied the ladder test scene: unknown prototype blocks are absent in baseline only; original world is untouched.',
            'Matching aggregate error groups suggests observed errors are shared; it does not establish all behavior, causal absence of new errors, or city performance.',
            'Both clients entered an isolated scene and then were forcibly stopped; no manual visual/Creative acceptance or normal reopen is established.']}
    (ROOT/'reports/CLIENT_BASELINE_COMPARISON.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'status':result['status'],'differences':len(difference),'baseline_errors':result['baseline_error_lines'],'combined_errors':result['combined_error_lines']}))
if __name__=='__main__':main()
