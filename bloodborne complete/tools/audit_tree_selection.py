"""Independent source-part bounds + actual native footprint/raycast audit.

No Gradle/client/server/world launch. Calls unchanged production distributor and
native VoxelShape.raycast in an isolated Java process; no timing/FPS claim.
"""
import copy
import hashlib
import itertools
import json
import math
from pathlib import Path
import subprocess
import zipfile
from prepare_tree_selection import tree_selection,without_selection,fingerprint

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/architecture/resources'
BUILD=ROOT/'build/tree-selection-native-audit'
JDK=Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin')
PACK=Path('C:/Users/vakir/Downloads/bbmc_v15_resource (1).zip')

HARNESS=r'''
package dev.dreamwalker.bloodbornedw.composite;
import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import java.nio.file.*;
import java.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
public final class TreeSelectionNativeAudit{
 static double[] vec(JsonArray a){return new double[]{a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()};}
 static Map<Cell,List<Box>> distribute(JsonArray boxes,int yaw,boolean legacy)throws Exception{
  Class<?> type=legacy?ReviewLegacyCompositeSpec.class:CompositeSpec.class;
  Class<?> v=Class.forName(type.getName()+"$Vec"),b=Class.forName(type.getName()+"$Bounds");
  var vc=v.getConstructor(double.class,double.class,double.class);var bc=b.getConstructor(v,v);
  var method=type.getDeclaredMethod("distribute",b,int.class,double.class,Map.class);method.setAccessible(true);
  Map<Cell,List<Box>> result=new TreeMap<>();for(JsonElement element:boxes){JsonObject box=element.getAsJsonObject();double[] f=vec(box.getAsJsonArray("from")),t=vec(box.getAsJsonArray("to"));method.invoke(null,bc.newInstance(vc.newInstance(f[0],f[1],f[2]),vc.newInstance(t[0],t[1],t[2])),yaw,0d,result);}return result;
 }
 static Map<String,Object> counts(Map<Cell,List<Box>> selection,Map<Cell,List<Box>> collision){
  TreeSet<Cell> cells=new TreeSet<>(selection.keySet());cells.addAll(collision.keySet());cells.add(Cell.ORIGIN);
  int nativeBoxes=0,max=0,nonempty=0,inputs=0;for(var entry:selection.entrySet()){int count=CompositeShapes.of(entry.getValue()).getBoundingBoxes().size();nativeBoxes+=count;max=Math.max(max,count);if(count>0)nonempty++;inputs+=entry.getValue().size();}
  return Map.of("footprintCellsIncludingRoot",cells.size(),"helperCandidateCells",cells.size()-1,"selectionNonemptyCells",nonempty,"nativeSelectionBoxesTotal",nativeBoxes,"nativeSelectionBoxesMaxPerCell",max,"distributedSelectionInputs",inputs);
 }
 static Vec3d global(double[] p,int yaw){double a=Math.toRadians(yaw*45),c=Math.cos(a),s=Math.sin(a),x=p[0]/16-.5,z=p[2]/16-.5;return new Vec3d(.5+c*x-s*z,p[1]/16,.5+s*x+c*z);}
 public static void main(String[] args)throws Exception{
  JsonObject input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();List<Map<String,Object>> rows=new ArrayList<>();int hits=0,miss=0;
  for(int yaw=0;yaw<8;yaw++){
   Map<Cell,List<Box>> collision=distribute(input.getAsJsonArray("collision"),yaw,false);
   Map<Cell,List<Box>> after=distribute(input.getAsJsonArray("after"),yaw,false);
   Map<String,Object> row=new LinkedHashMap<>();row.put("yaw",yaw);row.put("v7Original",counts(distribute(input.getAsJsonArray("v7"),yaw,true),collision));row.put("beforeTwoPlanes",counts(distribute(input.getAsJsonArray("before"),yaw,false),collision));row.put("afterFourPlanes",counts(after,collision));
   int checked=0,failed=0;List<Map<String,Object>> failures=new ArrayList<>();
   for(JsonElement element:input.getAsJsonArray("targets")){
    JsonObject target=element.getAsJsonObject();Vec3d point=global(vec(target.getAsJsonArray("point")),yaw);
    double[] n=vec(target.getAsJsonArray("normal"));double a=Math.toRadians(yaw*45);Vec3d normal=new Vec3d(Math.cos(a)*n[0]-Math.sin(a)*n[2],n[1],Math.sin(a)*n[0]+Math.cos(a)*n[2]);
    for(int sign:List.of(-1,1)){
     Vec3d start=point.add(normal.multiply(.75*sign)),end=point.subtract(normal.multiply(.75*sign));boolean hit=false;
     for(var entry:after.entrySet()){var cell=entry.getKey();if(CompositeShapes.of(entry.getValue()).raycast(start,end,new BlockPos(cell.x(),cell.y(),cell.z()))!=null){hit=true;break;}}
     checked++;if(hit)hits++;else{miss++;failed++;failures.add(Map.of("variant",target.get("variant").getAsInt(),"part",target.get("part").getAsInt(),"element",target.get("element").getAsInt(),"point",List.of(point.x,point.y,point.z),"side",sign));}
    }
   }
   row.put("actualNativeRays",checked);row.put("nativeRayMisses",failed);row.put("failures",failures);rows.add(row);
  }
  System.out.println(new Gson().toJson(Map.of("status",miss==0?"PASS_NATIVE_SOURCE_TARGET_RAYCASTS":"FAIL_NATIVE_SOURCE_TARGET_RAYCASTS","actualNativeRayHits",hits,"actualNativeRayMisses",miss,"rows",rows)));
  if(miss!=0)throw new AssertionError("native source-art ray targets missed");
 }
}
'''

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def rotate(point,pivot,degrees):
    c=math.cos(math.radians(degrees));s=math.sin(math.radians(degrees));x=point[0]-pivot[0];z=point[2]-pivot[2]
    return [pivot[0]+c*x-s*z,point[1],pivot[2]+s*x+c*z]
def inside(point,box):return all(box['from'][i]-1e-7<=point[i]<=box['to'][i]+1e-7 for i in range(3))
def main():
    BUILD.mkdir(parents=True,exist_ok=True)
    path=RES/'bloodborne_dw/composite/prototype_tree.json';document=json.loads(path.read_text(encoding='utf8'))
    historical=ROOT/'reports/TREE_SELECTION_V8_TWO_PLANES_HISTORICAL.json';before=json.loads(historical.read_text(encoding='utf8'))
    baseline=ROOT/'reports/user-review-v7/baseline/prototype_tree.json';old=json.loads(baseline.read_text(encoding='utf8'))
    # Original art and physics are immutable. Variant1 lower art has a separate,
    # explicitly recorded legal two-panel correction since the old outline audit.
    assert without_selection({'variants':[document['variants'][0]]})==without_selection({'variants':[before['variants'][0]]})
    original_parts=document['variants'][0]['poses']['closed']['parts']
    upper_parts=[part for part in original_parts if not part['model'].endswith('/melon')]
    proposed_parts=document['variants'][1]['poses']['closed']['parts']
    assert len(original_parts)==18 and len(upper_parts)==16
    assert len(proposed_parts)==18 and proposed_parts[2:]==upper_parts
    assert all(variant['poses']['closed']['collision']==before['variants'][0]['poses']['closed']['collision']for variant in document['variants'])
    for key in ('id','units','schemaVersion','support','essentialMask','layer','openable'):
        assert document[key]==before[key]
    asset_hashes={p.relative_to(ROOT).as_posix():sha(p) for folder in ('models/tree_source','models/tree_alt','models/tree_proposal','textures/tree_source') for p in (RES/'assets/bloodborne_dw'/folder).rglob('*') if p.is_file()}
    targets=[];part_rows=[];corner_checks=0;uv_rows=[];element_counts=[]
    with zipfile.ZipFile(PACK) as source:
        for vi,variant in enumerate(document['variants']):
            pose=variant['poses']['closed'];assert pose['selection']==tree_selection()
            element_count=0
            for pi,part in enumerate(pose['parts']):
                assert part.get('pitch',0)==0 and part.get('extraYaw',0)==0
                namespace,local=part['model'].split(':');model_path=RES/'assets'/namespace/'models'/(local+'.json');model=json.loads(model_path.read_text(encoding='utf8'))
                if local.startswith('tree_source/'):
                    raw=source.read('assets/minecraft/models/'+local.removeprefix('tree_source/')+'.json');original=json.loads(raw)
                    assert model['elements']==original['elements'],'Source geometry/UV changed'
                else:
                    assert vi==1 and local in ('tree_proposal/lower_source_base','tree_proposal/lower_source_base_upper')
                    original=json.loads(source.read('assets/minecraft/models/block/white_wool.json'))
                    assert len(model['elements'])==2
                    is_upper=local.endswith('_upper');offset=64 if is_upper else 16
                    assert part=={'model':'bloodborne_dw:'+local,'altModel':'bloodborne_dw:'+local+'_alt',
                        'offset':[0,offset,0],'yaw':0,'pitch':0,'pivot':[8,8,8]}
                    sample_count=0
                    for ei,(authored,element)in enumerate(zip(original['elements'][:2],model['elements'])):
                        expected=copy.deepcopy(authored)
                        for face in expected['faces'].values():
                            if is_upper:face['uv'][3]=14.5
                            else:face['uv'][1]=14.5
                        assert element==expected,'Only exact declared lower V interval may change from source'
                        assert all(-16<=value<=32 for key in ('from','to')for value in element[key])
                        for name,face in element['faces'].items():
                            source_uv=authored['faces'][name]['uv'];uv=face['uv']
                            for local_fraction in (0,.25,.5,.75,1):
                                world_y=(48 if is_upper else 0)+48*local_fraction
                                expected_v=source_uv[3]+(source_uv[1]-source_uv[3])*world_y/96
                                actual_v=uv[3]+(uv[1]-uv[3])*local_fraction
                                for across in (0,.25,.5,.75,1):
                                    expected_u=source_uv[0]+(source_uv[2]-source_uv[0])*across
                                    actual_u=uv[0]+(uv[2]-uv[0])*across
                                    assert (expected_u,expected_v)==(actual_u,actual_v)
                                    sample_count+=1
                    uv_rows.append({'model':part['model'],'source':'minecraft:block/white_wool first2elements',
                        'legalLocalYUnits':[-16,32],'partOffsetYUnits':offset,'worldYUnits':[48,96]if is_upper else[0,48],
                        'atlasVUnits':[13,14.5]if is_upper else[14.5,16],
                        'geometryAndSourceFaceFieldsExactExceptDeclaredVInterval':True,
                        'sourceAffineUVSamplesExact':sample_count,'proposalNotUserAccepted':True})
                # Every imported atlas stays byte-exact; ALT is a parent wrapper.
                for texture in model['textures'].values():
                    if texture.startswith('#'):continue
                    namespace,texture_local=texture.split(':',1)
                    assert namespace=='bloodborne_dw' and texture_local.startswith('tree_source/')
                    atlas=RES/'assets'/namespace/'textures'/(texture_local+'.png')
                    assert atlas.read_bytes()==source.read('assets/minecraft/textures/'+texture_local.removeprefix('tree_source/')+'.png')
                alt_namespace,alt_local=part['altModel'].split(':')
                alt_path=RES/'assets'/alt_namespace/'models'/(alt_local+'.json')
                assert json.loads(alt_path.read_text(encoding='utf8'))=={'parent':part['model']}
                transformed=[]
                for ei,element in enumerate(model['elements']):
                    element_count+=1
                    assert element.get('rotation',{}).get('angle',0)==0 and not element.get('rotation',{}).get('rescale',False)
                    flat=[i for i in range(3)if element['from'][i]==element['to'][i]];assert len(flat)==1
                    axis=flat[0];normal=[0,0,0];normal[axis]=1;normal=rotate(normal,[0,0,0],part.get('yaw',0))
                    for point in itertools.product(*zip(element['from'],element['to'])):
                        point=rotate(point,part.get('pivot',[8,8,8]),part.get('yaw',0));point=[point[i]+part['offset'][i] for i in range(3)]
                        assert any(inside(point,box)for box in tree_selection()),f'Source corner outside outline:{vi}/{pi}/{ei}/{point}'
                        transformed.append(point);corner_checks+=1
                    varying=[i for i in range(3)if i!=axis]
                    for ratios in itertools.product((.1,.5,.9),repeat=2):
                        point=list(element['from'])
                        for i,ratio in zip(varying,ratios):point[i]+=ratio*(element['to'][i]-element['from'][i])
                        point=rotate(point,part.get('pivot',[8,8,8]),part.get('yaw',0));point=[point[i]+part['offset'][i] for i in range(3)]
                        targets.append({'variant':vi,'part':pi,'element':ei,'point':point,'normal':normal})
                part_rows.append({'variant':vi,'part':pi,'model':part['model'],'modelSha256':sha(model_path),'bounds':{'from':[min(p[i]for p in transformed)for i in range(3)],'to':[max(p[i]for p in transformed)for i in range(3)]},'allTransformedElementCornersCovered':True})
            element_counts.append(element_count)
    input_data={'v7':old['variants'][0]['poses']['closed']['selection'],'before':before['variants'][0]['poses']['closed']['selection'],'after':tree_selection(),'collision':document['variants'][0]['poses']['closed']['collision'],'targets':targets}
    (BUILD/'input.json').write_text(json.dumps(input_data),encoding='utf8');(BUILD/'TreeSelectionNativeAudit.java').write_text(HARNESS,encoding='utf8')
    lines=(ROOT/'build/wall-shared-client-api-check/compile.args').read_text(encoding='utf8').splitlines();cp=lines[lines.index('"-cp"')+1].strip('"')
    cp=str(ROOT/'build/classes/java/main')+';'+str(ROOT/'build/classes/java/gametest')+';'+cp
    run=subprocess.run([str(JDK/'javac.exe'),'--release','17','-encoding','UTF-8','-cp',cp,'-d',str(BUILD),str(BUILD/'TreeSelectionNativeAudit.java')],capture_output=True,text=True)
    (BUILD/'compile.log').write_text(run.stdout+run.stderr,encoding='utf8')
    if run.returncode:raise SystemExit(run.stdout+run.stderr)
    run=subprocess.run([str(JDK/'java.exe'),'-Xmx512M','-cp',str(BUILD)+';'+cp,'dev.dreamwalker.bloodbornedw.composite.TreeSelectionNativeAudit',str(BUILD/'input.json')],capture_output=True,text=True)
    (BUILD/'native.log').write_text(run.stdout+run.stderr,encoding='utf8')
    if run.returncode:raise SystemExit(run.stdout+run.stderr)
    native=json.loads(run.stdout.strip().splitlines()[-1]);assert native['actualNativeRayMisses']==0
    assert native['actualNativeRayHits']==len(targets)*8*2
    assert asset_hashes=={name:sha(ROOT/name) for name in asset_hashes},'Art asset modified during audit'
    report={'schema':'dreamwalker-tree-selection-source-native-audit-v2','status':'PASS_ISOLATED_NATIVE_SOURCE_BOUNDS_AND_RAYCASTS',
        'evidence':{'v7DescriptorSha256':sha(baseline),'beforeTwoPlanesDescriptorSha256':sha(historical),'afterFourPlanesDescriptorSha256':sha(path),'unchangedProductionDistributorSha256':sha(ROOT/'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeSpec.java'),'unchangedProductionShapeBuilderSha256':sha(ROOT/'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeShapes.java'),'nativeLogSha256':sha(BUILD/'native.log'),'sourcePackSha256':sha(PACK)},
        'sourcePolicy':{'lowerYUnits':[0,96],'lowerCrossExtentUnits':[-16,32],'upperYUnits':[96,288],'upperCrossExtentUnits':[-64,80],'rayThicknessUnits':.25,'selectionVolumesBefore':2,'selectionVolumesAfter':4},
        'originalVariant0NonSelectionUnchanged':True,'upper16SourcePartsUnchanged':True,'physicalStemUnchanged':True,
        'currentProposalLower':'Separate unaccepted art proposal: two legal source-local48-unit panels with V-range split, total6-block height. Not a claim that all current art equals the prior one-piece invalid JSON.',
        'partsModelsUVTexturesMutatedByThisAudit':False,'nonSelectionDescriptorFingerprint':fingerprint(without_selection(document)),
        'partsByVariant':[len(variant['poses']['closed']['parts'])for variant in document['variants']],
        'elementsByVariant':element_counts,'sourceUVSplitPanels':uv_rows,
        'sourcePartElementCornerChecks':corner_checks,'sourcePartBounds':part_rows,'immutableAssetHashes':asset_hashes,'actualNative':native,
        'limits':['Isolated named1.20.1 native distributor/cache/raycast; no client/server/world launch or user acceptance.','Target rays sample nine interior points per authored plane, both sides, all8 yaw; corner containment independently checks every transformed source element corner.','Source-derived UV affine mapping and byte-exact atlas are checked; this is not sprite-filtering or manual visual acceptance.','Selection coarse upper outline remains wider than individual canopy pixels; collision unchanged. Helper candidates are not actual installed ownership.','Current75-test dedicated performance/geometry metrics are separate COMPOSITE_V8_GEOMETRY_METRICS.json (Attempt19). No timing/FPS result is inferred from this shape audit.']}
    output=ROOT/'reports/TREE_SELECTION_SOURCE_NATIVE_AUDIT.json';output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    maxima={stage:max(row[stage]['helperCandidateCells'] for row in native['rows']) for stage in ('v7Original','beforeTwoPlanes','afterFourPlanes')}
    print(json.dumps({'status':report['status'],'actualNativeRayHits':native['actualNativeRayHits'],'cornerChecks':corner_checks,'maxHelperCandidates':maxima,'artAndPhysicsMutatedByAudit':False,'partsByVariant':report['partsByVariant']}))
if __name__=='__main__':main()
