from common import *
import shutil,time
def main():
 log_to('prepare-world');target=DELIVERY/'map/bloodborne-blocks-fixed';backup=DELIVERY/'backup/bloodborne-before-block-fixes.zip'
 assert not target.exists() and not backup.exists(),'Refuse to replace an existing map or backup'
 target.mkdir(parents=True);backup.parent.mkdir(parents=True,exist_ok=True);manifest=[];total=0
 with zipfile.ZipFile(backup,'x',zipfile.ZIP_DEFLATED,compresslevel=3) as z:
  for p in files(SOURCE_WORLD):
   rel=p.relative_to(SOURCE_WORLD)
   if p.name=='session.lock':continue
   dest=target/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,dest);z.write(p,'bloodborne/'+rel.as_posix());n=p.stat().st_size;total+=n
   manifest.append({'path':rel.as_posix(),'bytes':n,'sha256':sha(p)})
 write(OUT/'world-before-manifest.json',manifest)
 write(OUT/'world-copy.json',{'status':'PASS','source':str(SOURCE_WORLD),'target':str(target),'backup':str(backup),'backupSha256':sha(backup),'files':len(manifest),'bytes':total})
 print('PASS',len(manifest),total)
if __name__=='__main__':main()
