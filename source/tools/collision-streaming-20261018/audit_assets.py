"""Compare resources and untouched lighting/mechanic bytecode with streaming.4."""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path

def sha(data):
    return hashlib.sha256(data).hexdigest()

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--baseline',type=Path,required=True)
    ap.add_argument('--candidate',type=Path,required=True)
    ap.add_argument('--out',type=Path,required=True)
    args=ap.parse_args()
    with zipfile.ZipFile(args.baseline) as before, zipfile.ZipFile(args.candidate) as after:
        def resource(n):
            return not n.endswith('/') and n.startswith(('assets/','bloodborne_dw/','data/'))
        resources={n for n in before.namelist() if resource(n)}
        after_resources={n for n in after.namelist() if resource(n)}
        changed=[n for n in sorted(resources & after_resources) if before.read(n)!=after.read(n)]
        allowed={
            'dev/dreamwalker/bloodbornedw/composite/'+n+'.class'
            for n in ['ImportedPhysicalShapes','StaticArchitectureCatalog',
                      'StaticArchitectureCatalog$Recipe','StaticArchitectureCatalog$Geometry',
                      'StaticArchitectureCatalog$CatalogValues','CompositeData',
                      'CompositeData$DecodePool','CompositeData$DecodePool$PackedOwners',
                      'CompositeOwnerNbt','CompositeBlockEntity']
        }
        allowed.add('dev/dreamwalker/bloodbornedw/runtime/CellSnapshot$BlockData.class')
        old_classes={n for n in before.namelist() if n.endswith('.class')}
        new_classes={n for n in after.namelist() if n.endswith('.class')}
        changed_classes=[n for n in sorted(old_classes & new_classes) if before.read(n)!=after.read(n)]
        unexpected=(set(changed_classes) | (new_classes-old_classes) | (old_classes-new_classes))-allowed
        result={'status':'PASS' if resources==after_resources and not changed and not unexpected else 'FAIL',
                'baselineSha256':sha(args.baseline.read_bytes()),
                'candidateSha256':sha(args.candidate.read_bytes()),
                'resourceCount':len(resources),
                'pngCount':sum(n.endswith('.png') for n in resources),
                'changedResources':changed,'addedResources':sorted(after_resources-resources),
                'removedResources':sorted(resources-after_resources),
                'changedClasses':changed_classes,'addedClasses':sorted(new_classes-old_classes),
                'removedClasses':sorted(old_classes-new_classes),
                'unexpectedClassChanges':sorted(unexpected)}
    args.out.parent.mkdir(parents=True,exist_ok=True)
    args.out.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result))
    if result['status']!='PASS':
        raise SystemExit(1)

if __name__=='__main__':
    main()
