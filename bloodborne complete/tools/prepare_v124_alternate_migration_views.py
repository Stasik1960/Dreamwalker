"""Fresh mapped one-sided QA views; original models/worlds remain read-only."""
import json
import math
from pathlib import Path
from PIL import Image
from build_v124_stair_types import inherited
from gallery_platform_plan import ModelGeometry
ROOT=Path(__file__).resolve().parents[1];DOC=ROOT/'docs/v12.4-dedup';RES=ROOT/'src/architecture/resources'
def read(path):return json.loads(path.read_text(encoding='utf8'))
def write(path,value):path.write_bytes((json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode('utf8'))
def main():
    capture=read(ROOT/'build/runtime-client-v124-comparison-final-1/v124-client-comparison-output.json')
    assert capture['productionJarSha256']=='c30cb62063656b5230c2c2a6e9338ac208a687d9df712f6bc6ced51a1598a728'
    pairs={(row['oldId'],row['oldVariant']):row for frame in capture['frames'] for row in frame['pairs'] if not row.get('kind')}
    models=ModelGeometry();alpha=[]
    for number,variant in [('90141',0),('93223',3),('93223',4),('93223',5)]:
        row=pairs[(number,variant)];path=row['oldRegistryId'].split(':',1)[1]
        spec=read(RES/'bloodborne_dw/composite'/f'{path}.json')
        for part in spec['variants'][variant]['poses']['closed']['parts']:
            doc=inherited(models,part['model'])
            for element in models.elements(part['model']):
                for side,face in element.get('faces',{}).items():
                    texture=face['texture'];visited=set()
                    while texture.startswith('#'):
                        assert texture not in visited;visited.add(texture);texture=doc['textures'][texture[1:]]
                    ns,name=texture.split(':',1);image=Image.open(RES/f'assets/{ns}/textures/{name}.png').convert('RGBA');uv=face['uv']
                    x0,x1=sorted((uv[0]*image.width/16,uv[2]*image.width/16));y0,y1=sorted((uv[1]*image.height/16,uv[3]*image.height/16))
                    values=[image.getpixel((x,y))[3] for y in range(max(0,math.floor(y0)),min(image.height,math.ceil(y1))) for x in range(max(0,math.floor(x0)),min(image.width,math.ceil(x1)))]
                    alpha.append({'id':number,'variant':variant,'model':part['model'],'side':side,'uv':uv,'nonzeroAlphaPixels':sum(v>0 for v in values),'pixels':len(values)})
    previous=read(DOC/'additional-migration-review-fixtures.json')
    source=[row for row in previous['fixtures'] if row['kind']=='SOURCE_CATALOGUE_ALT_VIEW']
    fixtures=[]
    fields=('groupId','oldId','oldRegistryId','oldVariant','mainId','mainRegistryId','variant','yawDeltaSteps','sourceShiftDelta','properties')
    for number,variant in [('90141',0),('93223',3),('93223',4),('93223',5)]:
        row=pairs[(number,variant)]
        for yaw,pitch in ([(35,-25),(215,-25)] if number=='90141' else [(125,25),(305,25)]):
            fixture={key:row[key] for key in fields};fixture.update(kind='MIGRATION_ALT_VIEW',viewYawDegrees=yaw,viewPitchDegrees=pitch,
                savedNativeFixtureBinding='Fresh final native savedRows by oldRegistryId|oldVariant; no embedded preview payload',
                reviewReason='C30 one-sided source view was blank/edge-on; two independent front-side views must be inspected without assuming blank is PASS')
            fixtures.append(fixture)
    write(DOC/'additional-migration-review-fixtures.json',{'schemaVersion':1,'status':'FRONT_SIDE_VIEWS_PENDING_NATIVE_CAPTURE','migrationAlternateViewCount':len(fixtures),'sourceAlternateViewCount':len(source),'fixtures':fixtures+source})
    write(DOC/'one-sided-source-alpha-audit.json',{'schemaVersion':1,'status':'SOURCE_ALPHA_DIAGNOSTICS_NOT_NATIVE_ACCEPTANCE','sourceFilesModified':False,'faces':alpha})
    print(json.dumps({'alternateMigrationViews':len(fixtures),'alternateSourceViews':len(source),'alpha':{n:sum(r['nonzeroAlphaPixels'] for r in alpha if r['id']==n) for n in ['90141','93223']}}))
if __name__=='__main__':main()
