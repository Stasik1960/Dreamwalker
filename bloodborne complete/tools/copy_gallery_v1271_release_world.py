"""Copy the exact verified gallery ZIP contents into a local release World.

No world NBT or configuration is edited. Every ordinary source/destination
file is checked against the already roundtrip-verified gallery archive manifest.
"""
import argparse, json, shutil
from pathlib import Path
from run_v11_server import files_without_links, sha
from verify_v1271_final_readiness import ROOT, ordinary, read, write

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--world',type=Path,required=True)
    p.add_argument('--reopen-report',type=Path,required=True)
    p.add_argument('--destination',type=Path,default=ROOT.parent/'releases/Dreamwalker-BB/v12.7.1-city-fixes');a=p.parse_args()
    source=ordinary(a.world);dest=ordinary(a.destination);target=dest/'World'
    assert source.is_relative_to(ROOT/'build') and source.drive.upper()==dest.drive.upper()=='C:'
    assert not target.exists(),'Refusing to overwrite an unpacked release world'
    proof=read(ordinary(a.reopen_report));assert proof['status']=='PASS' and proof['productionReopenCompared'] and proof['inputFilesUnchanged']
    assert ordinary(Path(proof['world']))==source
    release=read(dest/'gallery-release-manifest.json');assert release['status']=='PASS_LOCAL_GALLERY_PACKAGED_AFTER_PRODUCTION_REOPEN'
    assert release['productionJarSha256']==proof['productionJarSha256']
    archive=next(row for row in release['archives'] if row['file']=='Dreamwalker-BB-V12.7.1-Gallery.zip')
    assert sha(ordinary(dest/archive['file']))==archive['sha256']
    prefix='Dreamwalker-BB-V12.7.1-Gallery/'
    expected={row['path'][len(prefix):]:row for row in archive['members']}
    assert len(expected)==len(archive['members']) and all(row['path'].startswith(prefix) for row in archive['members'])
    paths={path.relative_to(source).as_posix():path for path in files_without_links(source) if path.relative_to(source).parts[0] not in {'playerdata','stats','advancements'}}
    assert set(paths)==set(expected),'Tested world differs from packaged gallery file set'
    target.mkdir()
    for relative,path in sorted(paths.items()):
        ordinary(path);row=expected[relative];assert sha(path)==row['sha256'] and path.stat().st_size==row['bytes']
        copy=target/relative;copy.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,copy)
        assert sha(ordinary(copy))==sha(path)==row['sha256']
    assert all(sha(path)==expected[relative]['sha256'] for relative,path in paths.items()),'Verified source world changed during release copy'
    write(dest/'unpacked-gallery-world.json',{'status':'PASS_BYTE_EXACT_UNPACKED_VERIFIED_GALLERY_WORLD',
          'world':str(target),'source':str(source),'productionJarSha256':proof['productionJarSha256'],
          'verifiedGalleryZipSha256':archive['sha256'],'reopenReportSha256':sha(a.reopen_report),
          'ordinaryFilesVerified':len(paths),'worldNbtModified':False,'qaPlayersOmitted':True,
          'sourceUnchanged':True,'sameBytesAsProductionReopenedAndRoundtripVerifiedZip':True})

if __name__=='__main__':main()
