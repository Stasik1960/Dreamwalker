"""Remove test-only force-load tickets through the live native server console."""
import argparse,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
    p=argparse.ArgumentParser();p.add_argument('--profile',type=Path,required=True);a=p.parse_args()
    profile=json.loads(a.profile.read_text(encoding='utf8'));run=Path(profile['run_directory']).resolve()
    assert run.is_relative_to(ROOT/'build') and run.name.startswith('runtime-server-v126-flatgallery-')
    assert profile['derived_world_copy'] and not profile['extraMods']
    log=(run/'launch-console.log').read_text(encoding='utf8',errors='replace')
    assert 'Done (' in log and 'Stopping server' not in log
    target=run/'qa-live-commands.json';assert not target.exists()
    target.write_text(json.dumps(['forceload remove all','save-all flush'])+'\n',encoding='utf8')
    print(json.dumps({'status':'PASS_QUEUED_NATIVE_TICKET_CLEANUP','run':str(run)}))
if __name__=='__main__':main()
