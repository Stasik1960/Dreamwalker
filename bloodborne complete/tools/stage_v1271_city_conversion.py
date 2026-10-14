"""Stage/validate the reused V12.7.1 city preparation on a fresh derived copy."""
import argparse, json, stat
from pathlib import Path

def main():
    p=argparse.ArgumentParser(description=__doc__)
    root=Path(__file__).resolve().parents[1]
    p.add_argument('--preparation',type=Path,default=root/'build/v1271-city-preparation')
    p.add_argument('--output',type=Path,default=root/'build/v1271-derived-city')
    p.add_argument('--execute',action='store_true')
    args=p.parse_args(); preparation=args.preparation.absolute()
    manifest=json.loads((preparation/'runtime/manifest.json').read_text(encoding='utf-8-sig'))
    if args.execute:
        assert manifest.get('runtimeExecution')=='READY_REUSED_NATIVE_REFERENCE_VERIFIED_PREPARATION', 'Final native preparation binding is not ready'
        assert manifest.get('counts',{}).get('REMOVE')==0, 'Source deletion decisions remain'
        assert manifest.get('nativeSourceFallbackCells')==10795 and manifest.get('nativeEmptyMultipartSourceCells')==523, 'Native source merge proof is incomplete'
        assert manifest.get('compactSourceOpticalCarrierSplitCells')==2905 and manifest.get('protectedSingleBeehiveLadderCells')==13, 'Native optical compatibility delta is incomplete'
        assert manifest.get('nativeSourceFallbackStates')==156 and manifest.get('nativeGlassOpticalCompatibilityCells')==40135, 'Source glass and frozen optical contexts are not finalized'
        assert manifest.get('nativeFrozenOpticalContextCells')==348528 and manifest.get('nativeFrozenOpticalContextAuditStatus')=='PASS', 'Full native optical audit is incomplete'
    import v125_map_index as source
    for kind,path in manifest['sourceArchivePaths'].items():
        path=Path(path).absolute(); assert path.drive.upper()=='C:'
        for parent in [path,*path.parents]:
            info=parent.lstat()
            assert not stat.S_ISLNK(info.st_mode) and not getattr(info,'st_file_attributes',0)&0x400, 'Reparsed source path'
        assert source.sha(path)==manifest['sourceHashes'][kind], 'Original archive binding mismatch'
        if kind=='map':source.MAP=path
        elif kind=='pack':source.PACK=path
    import v127_stage_full_conversion as stage
    stage.O=root/'docs/v12.7.1/conversion'
    stage.stage(preparation/'source-plan.sqlite',preparation/'runtime',args.output,args.execute)

if __name__=='__main__':main()
