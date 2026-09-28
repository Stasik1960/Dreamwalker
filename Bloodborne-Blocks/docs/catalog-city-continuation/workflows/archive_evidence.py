import gzip, json, shutil, sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
run=Path(sys.argv[1]).resolve()
target=ROOT/'docs/catalog-city-continuation'/sys.argv[2]
target.mkdir(parents=True, exist_ok=True)
for name in ('first.json','second.json','protected.json'):
    with (run/name).open('rb') as source, (target/(name+'.gz')).open('wb') as destination:
        with gzip.GzipFile(filename='', mode='wb', fileobj=destination, mtime=0) as packed:
            shutil.copyfileobj(source,packed)
for name in ('summary.json','preservation.json','helpers.json','second-preservation.json','second-byte-comparison.json','residuals.json.gz'):
    shutil.copy2(run/name,target/name)
for path in sorted(run.glob('*.command.json')):
    shutil.copy2(path,target/path.name)
for path in sorted(run.glob('*.log')):
    shutil.copy2(path,target/path.name)
print(json.dumps({'evidence':str(target),'files':sum(1 for path in target.iterdir() if path.is_file())}))
