package dev.dreamwalker.bloodbornedw.gametest;
import dev.dreamwalker.bloodbornedw.composite.GlazingTypes;

import com.google.gson.Gson;
import dev.dreamwalker.bloodbornedw.architecture.PrototypeArchitecture;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallArchitecture;
import dev.dreamwalker.bloodbornedw.composite.CompositeArchitecture;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeShapes;
import dev.dreamwalker.bloodbornedw.composite.ReviewLegacyCompositeSpec;
import dev.dreamwalker.bloodbornedw.composite.ThinWindowRootBlock;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/** Read-only geometry comparison and bounded native cached-query CPU measurements.
 * No placed blocks, source assets, render state, world data or production behavior
 * are changed. Helper candidates are geometry cells, not ownership observations.
 */
public final class CompositeGeometryMetricsGameTests implements FabricGameTest {
    private static final String PREFIX="DW_COMPOSITE_V9_GEOMETRY_METRICS=";
    private static final int ITERATIONS=100000,WARMUP=5,ROUNDS=7;
    private static final net.minecraft.util.math.Box MOVER=new net.minecraft.util.math.Box(-.3,.2,.4,-.1,.7,.6);
    private static volatile double sink;
    private record QueryPair(List<Box> before,List<Box> after){}

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=400,batchId="composite_geometry_metrics")
    public void compareLegacyAndCurrentGeometryAndWarmNativeQueries(TestContext context)throws Exception{
        Map<String,Object> output=new LinkedHashMap<>();output.put("schema","dreamwalker-native-composite-geometry-review-v10-v1");
        output.put("status","PASS_NATIVE_GEOMETRY_COUNTS_AND_IDENTICAL_QUERY_CONDITIONS");
        output.put("beforeArtifactSha256","1c9e2788c79223072e89150c2fdbef159e6e226bb76a823cd1296ba9e43027f9");
        output.put("canonicalPose",Map.of("mountY",0,"thinMount","vertical","sourceShiftApplied",false,"profilesIgnoredForGeometry",List.of("base","alt")));
        List<Map<String,Object>> families=new ArrayList<>();output.put("families",families);
        List<QueryPair> collisionQueries=new ArrayList<>(),selectionQueries=new ArrayList<>();
        int stateCases=0;
        for(CompositeRootBlock block:CompositeArchitecture.blocks()){
            String path=block.spec.id.getPath(),baselinePath=GlazingTypes.isGlazingPath(path)?"prototype_thin_window":path;ReviewLegacyCompositeSpec before=ReviewLegacyCompositeSpec.load(baselinePath);
            context.assertTrue(before.id.equals(block.spec.id)||GlazingTypes.isGlazingPath(path),"legacy/current identity follows explicit V10 window02/03 art splits;03 old intrinsic angle differs intentionally");
            Map<String,Object> family=new LinkedHashMap<>();family.put("id",block.spec.id.toString());
            family.put("registeredStatesBefore",512);family.put("registeredStatesAfter",block.getStateManager().getStates().size());
            family.put("descriptorVariantsBefore",before.variants.size());family.put("descriptorVariantsAfter",block.spec.variants.size());
            family.put("legacyDescriptorSha256",resourceHash("/bloodborne_dw/review-v7-baseline/"+baselinePath+".json"));family.put("currentDescriptorSha256",resourceHash("/bloodborne_dw/composite/"+path+".json"));
            List<Map<String,Object>> cases=new ArrayList<>(),geometry=new ArrayList<>();family.put("stateCases",cases);family.put("distinctEffectiveGeometryCases",geometry);
            Map<String,Integer> unique=new LinkedHashMap<>();
            for(int variant=0;variant<16;variant++)for(int yaw=0;yaw<8;yaw++)for(boolean open:List.of(false,true)){
                BlockState state=block.getDefaultState().with(CompositeRootBlock.VARIANT,variant).with(CompositeRootBlock.ROTATION,yaw).with(CompositeRootBlock.OPEN,open);
                context.assertTrue(ThinWindowRootBlock.mount(state)==ThinWindowRootBlock.Mount.VERTICAL,"comparison excludes newly mounted horizontal poses");
                int oldVariant=path.equals(GlazingTypes.WINDOW02)?1:path.equals(GlazingTypes.WINDOW03)?2:variant<before.variants.size()?variant:0,newVariant=variant<block.spec.variants.size()?variant:0;
                String key=oldVariant+":"+newVariant+":"+yaw+":"+open;
                Integer reference=unique.get(key);
                if(reference==null){
                    reference=geometry.size();unique.put(key,reference);
                    BlockState oldState=state.with(CompositeRootBlock.VARIANT,oldVariant);var oldPose=before.pose(oldState);var newPose=block.spec.pose(state);
                    Map<Cell,Footprint> oldFootprint=before.footprint(oldState,0),newFootprint=block.spec.footprint(state,0);
                    Map<String,Object> row=new LinkedHashMap<>();row.put("index",reference);row.put("variantEffectiveBefore",oldVariant);row.put("variantEffectiveAfter",newVariant);row.put("yaw",yaw);row.put("open",open);
                    row.put("before",metrics(oldFootprint,oldPose.collision().size(),oldPose.selection().size()));row.put("after",metrics(newFootprint,newPose.collision().size(),newPose.selection().size()));
                    TreeSet<Cell> cells=new TreeSet<>(oldFootprint.keySet());cells.addAll(newFootprint.keySet());List<Map<String,Object>> cellRows=new ArrayList<>();row.put("pairedCellMetrics",cellRows);
                    for(Cell cell:cells){Footprint oldCell=oldFootprint.getOrDefault(cell,Footprint.EMPTY),newCell=newFootprint.getOrDefault(cell,Footprint.EMPTY);
                        Map<String,Object> cellRow=new LinkedHashMap<>();cellRow.put("cell",List.of(cell.x(),cell.y(),cell.z()));cellRow.put("beforeInFootprint",oldFootprint.containsKey(cell));cellRow.put("afterInFootprint",newFootprint.containsKey(cell));
                        cellRow.put("before",cellMetrics(oldCell));cellRow.put("after",cellMetrics(newCell));cellRows.add(cellRow);
                        collisionQueries.add(new QueryPair(oldCell.collision(),newCell.collision()));selectionQueries.add(new QueryPair(oldCell.selection(),newCell.selection()));
                    }geometry.add(row);
                }
                Map<String,Object> stateRow=new LinkedHashMap<>();stateRow.put("variant",variant);stateRow.put("yaw",yaw);stateRow.put("open",open);stateRow.put("geometryCase",reference);stateRow.put("variantFallbackBefore",variant!=oldVariant);stateRow.put("variantFallbackAfter",variant!=newVariant);cases.add(stateRow);stateCases++;
            }families.add(family);
            context.assertTrue(cases.size()==256,"all16 registered variants/all8 yaws/both functional states checked");
            int expected=block instanceof ThinWindowRootBlock?1536:512;context.assertTrue(block.getStateManager().getStates().size()==expected,"current native registered state count: "+path);
        }
        context.assertTrue(families.size()==7&&stateCases==1792,"seven composite types and1792 registered state geometry cases, including retained legacy fallback states");
        output.put("canonicalStateGeometryCases",stateCases);
        output.put("otherArchitectureStateCounts",List.of(Map.of("id","bloodborne_dw:prototype_wall","before",32768,"after",PrototypeWallArchitecture.WALL.getStateManager().getStates().size()),Map.of("id","bloodborne_dw:prototype_ladder","before",192,"after",PrototypeArchitecture.LADDER.getStateManager().getStates().size())));
        output.put("helperCandidatePolicy","Footprint union includes collision and selection plusroot; helperCandidateCells excludesroot. Actual owned helpers/foreign overlays require separate scene installation evidence.");
        output.put("queryBenchmark",Map.of("collision",benchmark(collisionQueries),"selection",benchmark(selectionQueries)));
        output.put("geometryBuilder","Both sides use actual production CompositeShapes.of(List<ObjectGeometry.Box>): cached bounded16^3 occupancy, identical quantization. Old diagonal distributor1/16; current diagonal distributor1/4.");
        output.put("limitations",List.of("Read-only immutablev7 descriptor/legacy distributor vs current descriptor/distributor; accepted visual model/UV/PNG fingerprints are separate.","All registered VARIANT values include declared fallback-to0; new variants are distinguished, not claimed source-equivalent.","MountVERTICAL andmountY0 only; source compensation and newfloor/ceiling mounting are excluded here.","CPU benchmark includes actual cache lookup plus calculateMaxOffset for paired samecell populations andmover; noFPS,heap,TPS,worldrender,cityperformance ormanualacceptance claim.","Footprint cells are helper candidates only; no actual placement,foreign overlay ledger orsaved-world ownership measured."));
        System.out.println(PREFIX+new Gson().toJson(output));context.complete();
    }
    private static Map<String,Object> cellMetrics(Footprint cell){VoxelShape collision=CompositeShapes.of(cell.collision()),selection=CompositeShapes.of(cell.selection());return Map.of("distributedCollisionBoxes",cell.collision().size(),"distributedSelectionBoxes",cell.selection().size(),"nativeCollisionBoxes",collision.getBoundingBoxes().size(),"nativeSelectionBoxes",selection.getBoundingBoxes().size());}
    private static Map<String,Object> metrics(Map<Cell,Footprint> footprint,int authoredCollision,int authoredSelection){
        int inputCollision=0,inputSelection=0,nativeCollision=0,nativeSelection=0,maxCollision=0,maxSelection=0,collisionCells=0,selectionCells=0;
        for(Footprint cell:footprint.values()){inputCollision+=cell.collision().size();inputSelection+=cell.selection().size();int collision=CompositeShapes.of(cell.collision()).getBoundingBoxes().size(),selection=CompositeShapes.of(cell.selection()).getBoundingBoxes().size();nativeCollision+=collision;nativeSelection+=selection;maxCollision=Math.max(maxCollision,collision);maxSelection=Math.max(maxSelection,selection);if(collision!=0)collisionCells++;if(selection!=0)selectionCells++;}
        Map<String,Object> row=new LinkedHashMap<>();row.put("authoredPhysicalBoxes",authoredCollision);row.put("authoredSelectionBoxes",authoredSelection);row.put("distributedPhysicalBoxes",inputCollision);row.put("distributedSelectionBoxes",inputSelection);row.put("nativePhysicalBoxesTotal",nativeCollision);row.put("nativeSelectionBoxesTotal",nativeSelection);row.put("nativePhysicalBoxesMaxPerCell",maxCollision);row.put("nativeSelectionBoxesMaxPerCell",maxSelection);row.put("physicalCells",collisionCells);row.put("selectionCells",selectionCells);row.put("footprintCellsIncludingRoot",footprint.size());row.put("helperCandidateCells",footprint.size()-(footprint.containsKey(Cell.ORIGIN)?1:0));return row;
    }
    private static Map<String,Object> benchmark(List<QueryPair> pairs){
        if(pairs.isEmpty())throw new AssertionError("No native benchmark queries");
        // Prime both actual native caches with exactly the paired geometry lists.
        for(QueryPair pair:pairs){CompositeShapes.of(pair.before);CompositeShapes.of(pair.after);}
        for(int round=0;round<WARMUP;round++){query(pairs,true);query(pairs,false);}
        List<Long> before=new ArrayList<>(),after=new ArrayList<>();for(int round=0;round<ROUNDS;round++){if((round&1)==0){before.add(query(pairs,true));after.add(query(pairs,false));}else{after.add(query(pairs,false));before.add(query(pairs,true));}}
        Map<String,Object> row=new LinkedHashMap<>();row.put("pairedCellQueryPopulation",pairs.size());row.put("iterationsPerRound",ITERATIONS);row.put("warmupRounds",WARMUP);row.put("measuredRounds",ROUNDS);row.put("beforeNanos",before);row.put("afterNanos",after);row.put("ordering","alternating before/after");row.put("sameProcessAndInputs",true);row.put("mover",List.of(-.3,.2,.4,-.1,.7,.6));row.put("axis","X");row.put("offset",1);row.put("query","CompositeShapes.of(inputBoxes) +VoxelShapes.calculateMaxOffset(singleton shape)");row.put("sink",sink);return row;
    }
    private static long query(List<QueryPair> pairs,boolean before){double result=0;long start=System.nanoTime();for(int i=0;i<ITERATIONS;i++){QueryPair pair=pairs.get(i%pairs.size());VoxelShape shape=CompositeShapes.of(before?pair.before:pair.after);result+=VoxelShapes.calculateMaxOffset(Direction.Axis.X,MOVER,Collections.singletonList(shape),1);}sink=result;return System.nanoTime()-start;}
    private static String resourceHash(String path)throws Exception{try(InputStream input=CompositeGeometryMetricsGameTests.class.getResourceAsStream(path)){if(input==null)throw new IllegalStateException("Missing measured geometry:"+path);return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));}}
}
