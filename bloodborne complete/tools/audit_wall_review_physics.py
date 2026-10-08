"""Bounded native VoxelShape cost/count comparison, no Gradle/world/client startup.

Compiles the retained v7 geometry and current geometry against the same named
1.20.1 dependencies. This is a reproducible shape-query microbenchmark, not FPS,
heap, dedicated-server behavior or visual approval. Supplied inputs stay read-only.
"""
from pathlib import Path
import hashlib
import json
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
JDK=Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin')
BUILD=ROOT/'build/wall-review-native-audit'
REPORT=ROOT/'reports/WALL_REVIEW_CAUSE_FIX_COUNTS.json'
JAVA=ROOT/'src/architecture/java/dev/dreamwalker/bloodbornedw/architecture/wall'
HISTORICAL=ROOT/'reports/WALL_V7_GEOMETRY_HISTORICAL.java.txt'
HARNESS=r'''
package dev.dreamwalker.bloodbornedw.architecture.wall;
import com.google.gson.Gson;
import java.util.*;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
public final class WallReviewShapeAudit {
  static volatile double sink;
  static final Box MOVER=new Box(-.3,.2,.4,-.1,.7,.6);
  static int code(int mask,boolean tall){int value=0,factor=1;for(int d=0;d<4;d++){if((mask&(1<<d))!=0)value+=(tall?2:1)*factor;factor*=3;}return value;}
  static Map<String,Object> counts(List<VoxelShape> shapes){Map<Integer,Integer> histogram=new TreeMap<>();int max=0,total=0;for(VoxelShape shape:shapes){int count=shape.getBoundingBoxes().size();histogram.merge(count,1,Integer::sum);max=Math.max(max,count);total+=count;}return Map.of("geometryRecipes",shapes.size(),"maxNativeDecomposedBoxes",max,"totalNativeDecomposedBoxesAcrossRecipes",total,"nativeBoxCountHistogram",histogram);}
  static long move(List<VoxelShape> shapes,int iterations){double result=0;long start=System.nanoTime();for(int i=0;i<iterations;i++){VoxelShape shape=shapes.get(i%shapes.size());result+=VoxelShapes.calculateMaxOffset(Direction.Axis.X,MOVER,Collections.singletonList(shape),1);}sink=result;return System.nanoTime()-start;}
  public static void main(String[] args){V7WallGeometry old=new V7WallGeometry();WallGeometry current=new WallGeometry();List<VoxelShape> before=new ArrayList<>(),afterUniform=new ArrayList<>(),afterAll=new ArrayList<>(),outline=new ArrayList<>();
    long start=System.nanoTime();for(int yaw=0;yaw<8;yaw++)for(boolean post:List.of(false,true))for(boolean tall:List.of(false,true))for(int mask=0;mask<16;mask++)before.add(old.shape(mask,post,tall,yaw));long beforeBuild=System.nanoTime()-start;
    start=System.nanoTime();for(int yaw=0;yaw<8;yaw++)for(boolean post:List.of(false,true))for(boolean tall:List.of(false,true))for(int mask=0;mask<16;mask++)afterUniform.add(current.collision(code(mask,tall),post,yaw));long afterBuild=System.nanoTime()-start;
    for(int yaw=0;yaw<8;yaw++)for(boolean post:List.of(false,true))for(int code=0;code<81;code++){afterAll.add(current.collision(code,post,yaw));outline.add(current.outline(code,post,yaw));}
    for(int i=0;i<5;i++){move(before,200000);move(afterUniform,200000);}List<Long> beforeNs=new ArrayList<>(),afterNs=new ArrayList<>();for(int round=0;round<7;round++){if((round&1)==0){beforeNs.add(move(before,500000));afterNs.add(move(afterUniform,500000));}else{afterNs.add(move(afterUniform,500000));beforeNs.add(move(before,500000));}}
    Map<String,Object> report=new LinkedHashMap<>();report.put("beforeCollision",counts(before));report.put("beforeOutline",counts(before));report.put("afterUniformCollisionComparable512",counts(afterUniform));report.put("afterCollision",counts(afterAll));report.put("afterOutline",counts(outline));report.put("constructionNanos",Map.of("before512",beforeBuild,"afterComparable512",afterBuild));report.put("movementQueryBenchmark",Map.of("iterationsPerRound",500000,"warmupRounds",5,"measuredRounds",7,"beforeElapsedNanos",beforeNs,"afterElapsedNanos",afterNs,"ordering","alternating before/after", "sameMover",List.of(-.3,.2,.4,-.1,.7,.6),"maxOffset",1));report.put("cacheIdentity",current.collision(10,true,1)==current.collision(10,true,1)&&current.outline(10,true,1)==current.outline(10,true,1));System.out.println(new Gson().toJson(report));
  }
}
'''

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def dependency_cp():
    lines=(ROOT/'build/wall-shared-client-api-check/compile.args').read_text(encoding='utf-8').splitlines()
    index=lines.index('"-cp"')+1
    return lines[index].strip('"')

def main():
    BUILD.mkdir(parents=True,exist_ok=True)
    old=HISTORICAL.read_text(encoding='utf-8-sig').replace('final class WallGeometry','final class V7WallGeometry').replace('WallGeometry()','V7WallGeometry()').replace('WallGeometry.class','V7WallGeometry.class')
    (BUILD/'V7WallGeometry.java').write_text(old,encoding='utf-8')
    (BUILD/'WallReviewShapeAudit.java').write_text(HARNESS,encoding='utf-8')
    cp=dependency_cp()
    # Current owned sources + two new shared APIs. Compile dependencies from the
    # source path in an isolated output, never overwrite production build/classes.
    sources=list(JAVA.glob('*.java'))+[BUILD/'V7WallGeometry.java',BUILD/'WallReviewShapeAudit.java',ROOT/'src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/PrototypeWallGameTests.java',ROOT/'src/architecture/java/dev/dreamwalker/bloodbornedw/architecture/BuildPermissions.java',ROOT/'src/architecture/java/dev/dreamwalker/bloodbornedw/debug/DebugCatalogue.java']
    compile_cmd=[str(JDK/'javac.exe'),'--release','17','-encoding','UTF-8','-cp',cp,'-sourcepath',str(ROOT/'src/architecture/java'),'-d',str(BUILD)]+list(map(str,sources))
    compiled=subprocess.run(compile_cmd,capture_output=True,text=True)
    (BUILD/'compile.log').write_text(compiled.stdout+compiled.stderr,encoding='utf-8')
    if compiled.returncode:print(compiled.stdout+compiled.stderr);return compiled.returncode
    run=subprocess.run([str(JDK/'java.exe'),'-Xmx512M','-cp',str(BUILD)+';'+str(ROOT/'src/architecture/resources')+';'+cp,'dev.dreamwalker.bloodbornedw.architecture.wall.WallReviewShapeAudit'],capture_output=True,text=True)
    (BUILD/'shape-run.log').write_text(run.stdout+run.stderr,encoding='utf-8')
    if run.returncode:print(run.stdout+run.stderr);return run.returncode
    native=json.loads(run.stdout.strip().splitlines()[-1])
    report={
      'schema':'dreamwalker-user-review-wall-cause-fix-counts-v1','status':'PASS_LOCAL_NATIVE_COUNTS_ISOLATED_COMPILE_GAMEPLAY_PENDING',
      'sourceReviewSections':[2,8,9],'registryType':'bloodborne_dw:prototype_wall','temporaryTypeId':'90002','typeCountBefore':1,'typeCountAfter':1,
      'cause':'Boolean sides plus one common COURSE, own-family-only AUTO and16 fixed creative stacks prevented native independently low/tall connecting wall behavior.',
      'fix':'True WallBlock superclass/native neighbor+above logic, per-side NONE/LOW/TALL, additive WALLS tag; original shared160 art bakes remain bounded; simple cached collision and independent selection.',
      'registeredStatesBefore':32768,'registeredStatesAfter':82944,'stateFactorsAfter':{'sides':81,'up':2,'waterlogged':2,'material':8,'rotation':8,'profile':2,'connections':2},
      'stateIncreaseReason':'4 independent ternary sides replace4 booleans+shared binary course. Physics is keyed by geometry only, and water/material/profile/mode reuse the same shapes.',
      'mainCreativeWallStacksBefore':16,'mainCreativeWallStacksAfter':1,'helperCellsBefore':0,'helperCellsAfter':0,'blockEntityCountPerWall':0,
      'nativeShapes':native,'collisionPolicy':'Cardinal≤5 simple rectangles at native1.5 wall height; diagonal1 coarse AABB within root X/Z. No strip contour.','outlinePolicy':'0 for nonexistent empty recipe; otherwise1 source-bounds selection AABB with authored height≤1.',
      'supportAndPlacement':'Independent real central upper support face; top slab valid, unsupported bottom seating refused. Coarse AABB does not own or reserve foreign neighbor cells.',
      'rotationPolicy':'Ordinary cardinal AUTO keeps native world links; explicit45 tool turn/diagonal ordinary pose freezes selected MANUAL recipe. AUTO command resets cardinal yaw0.',
      'rights':'Ordinary placement/automatic connections allowed normally. Art editing via tool requires creative/OP2 plus normal region rights; OP2 administrative wall commands remain authorized.',
      'modelProvider':{'sourcePrimitiveIds':40,'nativeBakesPerReload':160,'maximumVisualAppearances':17152,'clonedQuads':0,'multipartPredicates':0},
      'geometryHistoricalSha256':sha(HISTORICAL),'geometryCurrentSha256':sha(JAVA/'WallGeometry.java'),
      'wallBlockSha256':sha(JAVA/'PrototypeWallBlock.java'),'testsSha256':sha(ROOT/'src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/PrototypeWallGameTests.java'),
      'nativeRulesEvidence':'reports/WALL_VANILLA_1_20_1_BYTECODE.txt','rulesBytecodeSha256':sha(ROOT/'reports/WALL_VANILLA_1_20_1_BYTECODE.txt'),
      'limits':['Shape microbenchmark uses same named1.20.1 library, process, mover, cache warmup and alternating rounds; does not measure FPS/server tick/heap.','Raw construction timings are not comparable performance certification: old runs first and includes library initialization.','Actual dedicated GameTests and ordinary4G resource/client load await root coordinated runs.','Original pack/world unchanged; immutablev7 world not rewritten and no mass migration.','Whole user review set remains unaccepted; this report only addresses wall2/8/9.']}
    assert native['afterCollision']['maxNativeDecomposedBoxes']<=5
    assert native['afterOutline']['maxNativeDecomposedBoxes']==1 and native['cacheIdentity']
    REPORT.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:report[k] for k in ('status','registeredStatesBefore','registeredStatesAfter','mainCreativeWallStacksAfter')}))
    print(json.dumps(native))
    return 0

if __name__=='__main__':sys.exit(main())
