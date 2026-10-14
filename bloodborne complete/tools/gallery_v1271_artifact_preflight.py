"""Read-only quick binding check before rebuilding the derived gallery."""
import json, zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
jar=root/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.1-city-fixes.jar'
with zipfile.ZipFile(jar) as z:
    spec=json.loads(z.read('bloodborne_dw/composite/source_compatibility_fragment.json'))
    row=next(r for r in json.loads(z.read('bloodborne_dw/remaining-catalogue.json'))['objects'] if r['path']=='source_compatibility_fragment')
    metadata=json.loads(z.read('fabric.mod.json'))
    result={'productionVersion':metadata['version'],'hiddenSourcePoses':len(spec['variants']),'conversionOnlyMetadata':row.get('conversionOnly',False),'invisibleCarrierMetadataPresent':'bloodborne_dw/invisible-source-carriers-v1271.json' in z.namelist(),'nativeGalleryBuildNeeded':'Do not author until current JAR includes all final prepared poses/carriers and conversionOnly guard'}
(root/'build/v1271-gallery-artifact-preflight.json').write_text(json.dumps(result,indent=2),encoding='utf8')
