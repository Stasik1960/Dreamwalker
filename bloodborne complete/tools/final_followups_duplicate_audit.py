"""Read-only audit of exact geometry/texture and practical duplicate candidates."""
from pathlib import Path
import collections,hashlib,json,itertools
from final_followups_surface_audit import model,crop,read,write,ROOT,RES,OUT

manifest=read(ROOT/'build/v1271-gallery-delivery-final5b/World/gallery-manifest.json')
groups=collections.defaultdict(list);exact=collections.defaultdict(list);checked=0
for row in manifest['exhibits']:
    if row.get('kind')!='architecture':continue
    path=row['registry'].split(':')[-1];p=RES/'bloodborne_dw/composite'/(path+'.json')
    if not p.is_file():continue
    spec=read(p);variant=int(row.get('variant',0));variant=min(variant,len(spec['variants'])-1);parts=spec['variants'][variant]['poses']['closed']['parts']
    geometry=[];pixels=[]
    for part in parts:
        d=model(part['model'])
        transform={k:part.get(k) for k in ('offset','yaw','pitch','pivot','extraYaw','extraPivot')}
        for e in d.get('elements',[]):
            geometry.append([transform,e.get('from'),e.get('to'),e.get('rotation')])
            for side,face in sorted(e.get('faces',{}).items()):
                im=crop(d,face)
                if im is not None:pixels.append([side,list(im.size),hashlib.sha256(im.tobytes()).hexdigest()])
    if not geometry:continue
    g=json.dumps(sorted(geometry,key=str),sort_keys=True);t=json.dumps(pixels,sort_keys=True);entry={'id':row['id'],'registry':row['registry'],'name':row['name'],'pixelFacesSha256':hashlib.sha256(t.encode()).hexdigest()}
    groups[hashlib.sha256(g.encode()).hexdigest()].append(entry);exact[hashlib.sha256((g+t).encode()).hexdigest()].append(entry);checked+=1
candidates=[rows for rows in groups.values() if len(rows)>1]
write(OUT/'duplicate-audit.json',{'status':'PASS_READ_ONLY_FULL_OFFERED_COMPOSITE_GEOMETRY_PIXEL_AUDIT','offeredCompositeInstallersChecked':checked,'exactGeometryAndDrawablePixelsGroups':[rows for rows in exact.values() if len(rows)>1],'sameGeometryCandidateGroups':candidates,'userSpecifiedPracticalDuplicates':[{'keepId':'90023','hideId':'90025','reason':'User-confirmed practical ornament duplicate; source model has different mounting dimensions and is retained unchanged for the original city.'},{'keepId':'90597','hideId':'90599','reason':'User-confirmed practical lantern duplicate; exact old source artwork/ID remains available to saved city states.'}],'automaticNearSimilarityDeletion':False,'savedRegistryOrModelRemoval':False,'materialVariantsExcludedFromRemoval':True,'scope':'Source face pixels, geometry and transforms; same geometry alone is not proof that different cobblestone, brick, wood, glazed or other authored materials are duplicates.'})
