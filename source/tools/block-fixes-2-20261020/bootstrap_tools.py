from common import *
def main():
 log_to('bootstrap-tools');here=Path(__file__).parent;old=PROJECT/'tools/block-fixes-20261020'
 for name in ['prepare_world.py','build.py','qa.init.gradle','run_native_checks.py','package_asset_harness.py','run_client_assets.py']:
  p=here/name
  assert not p.exists(),str(p)
  s=(old/name).read_text(encoding='utf8').replace('block-fixes-20261020','block-fixes-2-20261020').replace('streaming.7-blocks.1','streaming.7-blocks.2').replace('map/bloodborne-blocks-fixed','map/bloodborne-blocks-fixed-2')
  p.write_text(s,encoding='utf8')
 print('PASS')
if __name__=='__main__':main()
