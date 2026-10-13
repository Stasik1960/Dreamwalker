"""Read-only visual proof for retired mod types used by the original world.

A retired mod registration does not authorize deleting ordinary world cells.
Compare actual source-pack quad geometry/UV/RGBA with the same native carrier,
including the complete weighted choice sequence. Never modify the plan/world.
"""
import collections,functools,gzip,hashlib,itertools,json,sqlite3,time
import numpy as np
from v127_visual_export import ROOT,VANILLA,write_json
from v125_map_door_projections import SourceVisuals
from v124_visual_audit import Visuals
from v125_map_verify_extra_pose_uv import source_quads,signature
from v127_match_protected_source import planar_equal
from scan_original_map_models import branches_for,normalized_model

DEST=ROOT/'docs/v12.7/conversion'
REASONS={'USER_RETIRED_VANILLA_STYLE_TYPE','USER_RETIRED_VANILLA_STYLE_CARRIER','USER_REMOVED_VANILLA_MODEL_TEXTURE_FALLBACK'}
def token(v):return json.dumps(v,sort_keys=True,separators=(',',':'))
class VanillaVisuals(Visuals):
    def __init__(self):
        super().__init__(vanilla_jar=VANILLA);self.roots=();self.models={}
    @functools.lru_cache(maxsize=None)
    def document(self,id):
        id=normalized_model(id)
        if id in self.models:return self.models[id]
        ns,path=id.split(':',1);return json.loads(self.vanilla.read('assets/'+ns+'/models/'+path+'.json'))
    @functools.lru_cache(maxsize=None)
    def image(self,id):
        # Visuals sees empty resource roots: native JAR only, no mod fallback.
        return super().image(id)
def state_parts(text):
    reg=text.split('[',1)[0];tail=text.split('[',1)[1][:-1] if '[' in text else ''
    return reg,dict(p.split('=',1) for p in tail.split(',') if p)
def document(v,registry,pack=False):
    ns,path=registry.split(':',1);name='assets/'+ns+'/blockstates/'+path+'.json'
    return json.loads(v.pack.read(v.prefix+name)) if pack and v.prefix+name in v.pack.namelist() else json.loads(v.vanilla.read(name))
def options(v,registry,props,pack=False):
    return [[{**q[0],'model':normalized_model(q[0]['model'])} for q in branch] for _,branch in branches_for(document(v,registry,pack),props)]
def app(q):return {k:v for k,v in q.items() if k!='weight'}
def surfaces(v,apps):return source_quads(v,apps)
def vertexkey(v):return tuple(sorted(tuple(round(float(n),6) for n in p) for p in v))
def used_pixels_equal(one,va,two,vb):
    """Exact nearest-pixel proof on matching whole rectangular quad surfaces.

    Union all source/native texel edge breakpoints in each quad parameter axis;
    every open rectangle then has constant RGBA on both sides. Texture paths,
    unused atlas pixels, resolution and UV atlas placement do not matter.
    """
    a=collections.defaultdict(list);b=collections.defaultdict(list)
    for q in one:a[(vertexkey(q[0]),q[3])].append(q)
    for q in two:b[(vertexkey(q[0]),q[3])].append(q)
    if set(a)!=set(b) or any(len(a[k])!=len(b[k]) for k in a):return False,{'kind':'GEOMETRY_OR_FACE_PARTITION_DIFFERS'}
    for key in a:
        for source,native in zip(a[key],b[key]):
            vertices,uv,texture,tint=source;nvertices,nuv,ntexture,_=native
            order=[next(i for i,p in enumerate(nvertices) if np.max(np.abs(p-v))<1e-6) for v in vertices];nuv=nuv[order]
            axisbreaks=[{0.,1.},{0.,1.}];data=[]
            for coords,visual,tex in ((uv,va,texture),(nuv,vb,ntexture)):
                image=visual.image(tex);origin=coords[0];dx=coords[3]-origin;dy=coords[1]-origin
                if np.max(np.abs(coords[2]-origin-dx-dy))>1e-6:return False,{'kind':'NON_AFFINE_QUAD_UV_UNPROVEN'}
                for component,size in ((0,image.shape[1]),(1,image.shape[0])):
                    dependencies=[i for i,d in enumerate((dx[component],dy[component])) if abs(d)>1e-9]
                    if len(dependencies)>1:return False,{'kind':'SKEW_UV_UNPROVEN'}
                    if not dependencies:continue
                    axis=dependencies[0];delta=(dx,dy)[axis][component];low,high=sorted((float(origin[component]),float(origin[component]+delta)))
                    for edge in range(int(np.floor(low*size/16))-1,int(np.ceil(high*size/16))+2):
                        at=(edge*16/size-origin[component])/delta
                        if 0<at<1:axisbreaks[axis].add(float(at))
                data.append((image,origin,dx,dy))
            edges=[sorted(s) for s in axisbreaks]
            for x0,x1 in zip(edges[0],edges[0][1:]):
                for y0,y1 in zip(edges[1],edges[1][1:]):
                    x,y=(x0+x1)/2,(y0+y1)/2;pixels=[];at_uv=[]
                    for image,origin,dx,dy in data:
                        p=origin+x*dx+y*dy;at_uv.append([float(n) for n in p]);h,w=image.shape[:2];pixels.append(image[int(np.floor(p[1]*h/16))%h,int(np.floor(p[0]*w/16))%w])
                    if np.array_equal(pixels[0],pixels[1]) or pixels[0][3]==pixels[1][3]==0:continue
                    position=vertices[0]+x*(vertices[3]-vertices[0])+y*(vertices[1]-vertices[0])
                    return False,{'kind':'ACTUAL_REFERENCED_SURFACE_PIXEL_DIFFERS','surfacePositionModelUnits':[float(n) for n in position],'sourceTexture':texture,'nativeTexture':ntexture,'sourceUv':at_uv[0],'nativeUv':at_uv[1],'sourceRgba':pixels[0].tolist(),'nativeRgba':pixels[1].tolist()}
    return True,None
def exact(one,va,two,vb):
    return signature(one,va)==signature(two,vb) or planar_equal(one,va,two,vb) or used_pixels_equal(one,va,two,vb)[0]
def choice_sequence_equal(source,native,sg,ng):
    if len(sg)!=len(ng):return False
    return all(len(a)==len(b) and all(x.get('weight',1)==y.get('weight',1) and exact(surfaces(source,[app(x)]),source,surfaces(native,[app(y)]),native) for x,y in zip(a,b)) for a,b in zip(sg,ng))
def possible(groups):
    return [[app(a) for a in choices] for choices in itertools.product(*groups)]
def main():
    started=time.monotonic();fixture=json.loads((ROOT/'build/v127-source-lighting-decisions.json').read_text(encoding='utf-8'))
    db=sqlite3.connect(f"file:{(ROOT/fixture['sourcePlan']).resolve()}?mode=ro",uri=True)
    decisions={i:json.loads(s) for i,s in db.execute('SELECT id,data FROM decisions')};db.close()
    wanted={token([d['sourceState'],d['sourceApplications'],d['reason']]):d for d in decisions.values() if d['action']=='REMOVE' and d.get('reason') in REASONS}
    for d in wanted.values():d['count']=0;d['samples']=[];d['yMinimum']=None;d['yMaximum']=None
    rows_read=0
    with gzip.open(DEST/'source-removal-coordinates.jsonl.gz','rt',encoding='utf-8') as reader:
        for line in reader:
            r=json.loads(line);rows_read+=1
            if r['reason'] not in REASONS:continue
            key=token([r['sourceState'],r.get('sourceApplications',[]),r['reason']]);d=wanted[key];d['count']+=r['count'];cx,cz=r['chunk'];sy=r['sectionY']
            lo=sy*16+(r['linearRuns'][0][0]>>8);last=r['linearRuns'][-1];hi=sy*16+((last[0]+last[1]-1)>>8)
            d['yMinimum']=lo if d['yMinimum'] is None else min(d['yMinimum'],lo);d['yMaximum']=hi if d['yMaximum'] is None else max(d['yMaximum'],hi)
            if len(d['samples'])<5:
                for start,length in r['linearRuns']:
                    for i in range(start,start+min(length,5-len(d['samples']))):d['samples'].append({'dimension':r['dimension'],'position':[cx*16+(i&15),sy*16+(i>>8),cz*16+((i>>4)&15)]})
                    if len(d['samples'])==5:break
    source,native=SourceVisuals(),VanillaVisuals();rows=[]
    for d in sorted(wanted.values(),key=lambda x:-x['count']):
        if not d['count']:continue
        r={k:v for k,v in d.items() if k not in {'action','count'}};r['occurrences']=d['count'];reg,props=state_parts(d['sourceState'])
        try:
            observed=surfaces(source,d['sourceApplications']);sg=options(source,reg,props,True);ng=options(native,reg,props)
            candidates=possible(ng);nquads=[surfaces(native,a) for a in candidates]
            observed_matches=[i for i,q in enumerate(nquads) if exact(observed,source,q,native)]
            constant_native=bool(nquads) and all(exact(nquads[0],native,q,native) for q in nquads[1:])
            branches_exact=choice_sequence_equal(source,native,sg,ng)
            keep=branches_exact or constant_native and bool(observed_matches)
            r.update({'status':'KEEP_VANILLA_EXACT_PROOF' if keep else 'CUSTOM_VISIBLE_RETIRED_CONTENT_REQUIRES_POLICY_DECISION','nativeApplications':candidates,'sourceQuadCount':len(observed),'nativeQuadCounts':[len(q) for q in nquads],
                'observedMatchesNativeChoiceIndices':observed_matches,'allNativeWeightedChoicesVisuallyIdentical':constant_native,'sourceAndNativeWeightedBranchSequenceExact':branches_exact,
                'proofMethod':'Exact face vertex/UV associations with full decoded RGBA/tintindex or complete referenced nearest-texel domains; planar affine UV face union accepted. Native weighted choice sequence/weights compared, never a sampled projection.'})
            # Concrete dependency evidence is useful even when geometry differs.
            source_textures=sorted({q[2] for q in observed});native_textures=sorted({q[2] for q in nquads[0]}) if nquads else []
            r['sourceTextures']=[{'id':t,'shape':list(source.image(t).shape),'rgbaSha256':hashlib.sha256(source.image(t).tobytes()).hexdigest()} for t in source_textures]
            r['nativeTextures']=[{'id':t,'shape':list(native.image(t).shape),'rgbaSha256':hashlib.sha256(native.image(t).tobytes()).hexdigest()} for t in native_textures]
            r['nativeChoiceDifferenceWitnesses']=[used_pixels_equal(observed,source,q,native)[1] for q in nquads]
            vertices=np.asarray([q[0] for q in observed]).reshape(-1,3)
            r['sourceVisualBoundsModelUnits']=[vertices.min(0).tolist(),vertices.max(0).tolist()]
            r['nativeChoiceVisualBoundsModelUnits']=[]
            for q in nquads:
                vertices=np.asarray([v[0] for v in q]).reshape(-1,3)
                r['nativeChoiceVisualBoundsModelUnits'].append([vertices.min(0).tolist(),vertices.max(0).tolist()])
        except Exception as e:r.update({'status':'UNPROVEN_DEPENDENCY_OR_BRANCH_ERROR','error':repr(e)})
        rows.append(r)
    counts=collections.Counter();carriers=collections.defaultdict(collections.Counter)
    for r in rows:counts[r['status']]+=r['occurrences'];carriers[r['sourceState'].split('[')[0]][r['status']]+=r['occurrences']
    expected=sum(r['occurrences'] for r in fixture['cases'] if r['action']=='REMOVE' and any(decisions[i].get('reason') in REASONS for i in r['decisionIds']))
    assert sum(counts.values())==expected
    result={'schemaVersion':1,'status':'PASS_READ_ONLY_RETIRED_SOURCE_POLICY_AUDIT','sourceHashes':fixture['sourceHashes'],'worldModified':False,'planModified':False,'coordinateReport':str((DEST/'source-removal-coordinates.jsonl.gz').relative_to(ROOT)),
        'retiredOccurrenceCount':sum(counts.values()),'classificationOccurrences':dict(counts),'carriers':[{'sourceRegistryId':k,'classifications':dict(c),'occurrences':sum(c.values())} for k,c in sorted(carriers.items(),key=lambda x:-sum(x[1].values()))],
        'sourceApplicationCases':rows,'coordinateRowsRead':rows_read,'elapsedSeconds':round(time.monotonic()-started,2),'scope':'This proof distinguishes ordinary vanilla cells from retired custom visible content. No retired mod type is restored. Lighting/culling/runtime functional parity is not inferred from an offline visual proof.'}
    write_json(DEST/'retired-source-vanilla-equivalence.json',result)
    print(json.dumps({k:v for k,v in result.items() if k!='sourceApplicationCases'},ensure_ascii=False,indent=2),flush=True)
if __name__=='__main__':main()
