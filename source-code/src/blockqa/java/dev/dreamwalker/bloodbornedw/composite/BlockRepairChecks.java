package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.*;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;

public final class BlockRepairChecks implements ModInitializer {
    private final JsonArray checks=new JsonArray();
    private void check(boolean condition,String name){var row=new JsonObject();row.addProperty("name",name);row.addProperty("pass",condition);checks.add(row);if(!condition)throw new IllegalStateException(name);}
    @Override public void onInitialize(){ServerLifecycleEvents.SERVER_STARTED.register(server->server.execute(()->run(server)));}
    private void run(MinecraftServer server){
        JsonObject report=new JsonObject();
        try{
            ServerWorld world=server.getOverworld();BlockPos pos=new BlockPos(8,120,8);world.getChunk(pos);
            check(!Registries.BLOCK.containsId(new Identifier("bloodborne_dw:source_invisible_carrier")),"invisible carrier unregistered");
            check(!Registries.BLOCK.containsId(new Identifier("bloodborne_dw:source_invisible_solid_carrier")),"solid invisible carrier unregistered");
            check(MaterialSlabs.blocks().size()>=100,"all restored materials registered");
            int i=0;
            for(var slab:MaterialSlabs.blocks()){
                if(MaterialSlabs.hasOverlay(slab.getDefaultState()))continue;
                String id=Registries.BLOCK.getId(slab).toString();
                BlockState bottom=slab.getDefaultState().with(SlabBlock.TYPE,SlabType.BOTTOM).with(SlabBlock.WATERLOGGED,false);
                check(bottom.getFluidState().isEmpty(),id+" dry by default");
                world.setBlockState(pos,bottom,Block.NOTIFY_ALL);
                var shape=bottom.getCollisionShape(world,pos);var b=shape.getBoundingBox();
                check(b.equals(new net.minecraft.util.math.Box(0,0,0,1,.5,1)),id+" exact lower half");
                check(slab.tryFillWithFluid(world,pos,bottom,Fluids.WATER.getStill(false)),id+" water fill accepted");
                BlockState wet=world.getBlockState(pos);
                check(wet.get(SlabBlock.WATERLOGGED)&&wet.getFluidState().isOf(Fluids.WATER)&&wet.getCollisionShape(world,pos).getBoundingBox().equals(b),id+" water preserves lower slab collision");
                ItemStack drained=slab.tryDrainFluid(world,pos,wet);
                check(drained.isOf(Items.WATER_BUCKET)&&!world.getBlockState(pos).get(SlabBlock.WATERLOGGED),id+" bucket drains only water");
                BlockState upper=bottom.with(SlabBlock.TYPE,SlabType.TOP);world.setBlockState(pos,upper,Block.NOTIFY_ALL);
                check(upper.getCollisionShape(world,pos).getBoundingBox().equals(new net.minecraft.util.math.Box(0,.5,0,1,1,1)),id+" exact upper half");
                check(slab.tryFillWithFluid(world,pos,upper,Fluids.WATER.getStill(false)),id+" upper half waterlogging");
                BlockState full=bottom.with(SlabBlock.TYPE,SlabType.DOUBLE);world.setBlockState(pos,full,Block.NOTIFY_ALL);
                check(!slab.canFillWithFluid(world,pos,full,Fluids.WATER),id+" double refuses water");
                check(Block.isShapeFullCube(full.getCollisionShape(world,pos))&&full.getFluidState().isEmpty(),id+" exact dry double");
                check(full.getOpacity(world,pos)==Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE,SlabType.DOUBLE).getOpacity(world,pos),id+" double slab native opacity");
                if(i++==0){
                    world.setBlockState(pos,bottom.with(SlabBlock.WATERLOGGED,true),Block.NOTIFY_ALL);
                    ItemStack stack=new ItemStack(slab);var context=new ItemPlacementContext(world,null,Hand.MAIN_HAND,stack,new BlockHitResult(Vec3d.ofCenter(pos).add(0,0,0),Direction.UP,pos,false));
                    check(((BlockItem)slab.asItem()).place(context).isAccepted(),"real item combines two lower slabs");
                    check(world.getBlockState(pos).get(SlabBlock.TYPE)==SlabType.DOUBLE&&!world.getBlockState(pos).get(SlabBlock.WATERLOGGED),"combining clears slab water");
                    world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                    var side=new ItemPlacementContext(world,null,Hand.MAIN_HAND,new ItemStack(slab),new BlockHitResult(new Vec3d(pos.getX()+.5,pos.getY()+.75,pos.getZ()+.5),Direction.NORTH,pos,false));
                    check(slab.getPlacementState(side).get(SlabBlock.TYPE)==SlabType.TOP,"side click in upper half places upper slab");
                    world.setBlockState(pos,bottom,Block.NOTIFY_ALL);
                    var actor=new ArmorStandEntity(world,pos.getX()+.5,pos.getY()+1.25,pos.getZ()+.5);world.spawnEntity(actor);actor.move(MovementType.SELF,new Vec3d(0,-2,0));
                    check(Math.abs(actor.getY()-(pos.getY()+.5))<1e-6,"entity stands on lower slab instead of falling through");actor.discard();
                }
                world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            }
            int overlays=0;
            for(var slab:MaterialSlabs.blocks()){
                var state=slab.getDefaultState();if(!MaterialSlabs.hasOverlay(state))continue;overlays++;
                var recipe=StaticArchitectureCatalog.recipe(state);
                check(recipe.resident()<0,"helper overlay preserves its separate visible root "+recipe.index());
                state=state.with(SlabBlock.WATERLOGGED,true).with(MaterialSlabs.FACING,Direction.EAST);
                for(int fragment=0;fragment<recipe.fragments().size();fragment++){
                    var next=StaticArchitectureCatalog.without(state,fragment);
                    check(next.getBlock() instanceof MaterialSlabs.Slab&&next.get(SlabBlock.WATERLOGGED)&&next.get(MaterialSlabs.FACING)==Direction.EAST&&next.get(SlabBlock.TYPE)==SlabType.BOTTOM,"removing neighbouring component preserves water and slab "+recipe.index()+"/"+fragment);
                    check(recipe.removal(fragment)<0?!MaterialSlabs.hasOverlay(next):MaterialSlabs.recipeIndex(next)==recipe.removal(fragment),"finite helper removal closure "+recipe.index()+"/"+fragment);
                }
            }
            check(overlays>0,"mixed slab/helper variants covered");
            // An actual static component can still be removed atomically when
            // one of its physical cells shares a native, waterlogged slab.
            boolean lifecycle=false;
            for(var slab:MaterialSlabs.blocks()){
                var overlay=slab.getDefaultState();if(!MaterialSlabs.hasOverlay(overlay))continue;var recipe=StaticArchitectureCatalog.recipe(overlay);
                if(recipe.fragments().size()!=1)continue;var fragment=recipe.fragments().get(0);var pose=fragment.pose();if(pose.footprint().size()>30)continue;
                var singletons=new HashMap<Cell,Integer>();
                for(var r:StaticArchitectureCatalog.recipes())if(r.fragments().size()==1&&r.fragments().get(0).pose()==pose)singletons.put(r.fragments().get(0).origin(),r.index());
                if(pose.footprint().keySet().stream().anyMatch(c->!singletons.containsKey(new Cell(-c.x(),-c.y(),-c.z()))))continue;
                BlockPos root=new BlockPos(8,145,8);
                for(var c:pose.footprint().keySet()){BlockPos at=root.add(c.x(),c.y(),c.z());world.getChunk(at);world.setBlockState(at,StaticArchitectureCatalog.state(singletons.get(new Cell(-c.x(),-c.y(),-c.z()))),Block.FORCE_STATE);}
                BlockPos shared=root.add(-fragment.origin().x(),-fragment.origin().y(),-fragment.origin().z());world.setBlockState(shared,overlay.with(SlabBlock.WATERLOGGED,true),Block.FORCE_STATE);
                var result=StaticArchitectureRuntime.remove(world,root,pose);
                check(result.outcome()==dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED,"remove real mixed static component: "+result.reason());
                var kept=world.getBlockState(shared);check(kept.getBlock() instanceof MaterialSlabs.Slab&&!MaterialSlabs.hasOverlay(kept)&&kept.get(SlabBlock.WATERLOGGED),"native slab and water survive actual neighbouring component removal");
                world.setBlockState(shared,Blocks.AIR.getDefaultState(),Block.FORCE_STATE);lifecycle=true;break;
            }
            check(lifecycle,"actual helper lifecycle fixture exercised");
            var floor=CompositeArchitecture.kindBlock("source_gray_wool");var floorState=floor.getDefaultState();
            var footprint=WoodFloorCollision.footprint(floor.spec,floorState,0,0,1,0);
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)check(!footprint.getOrDefault(new Cell(x,0,z),new Footprint(List.of(),List.of())).collision().isEmpty(),"90566 outer floor cell collision "+x+","+z);
            var floorPayload=ImportedTypeAliases.markPayload(null);var shift=new net.minecraft.nbt.NbtList();shift.add(net.minecraft.nbt.NbtDouble.of(0));shift.add(net.minecraft.nbt.NbtDouble.of(1));shift.add(net.minecraft.nbt.NbtDouble.of(0));floorPayload.put("SourceShift",shift);
            BlockPos floorPos=new BlockPos(8,130,8);var floorPlaced=CompositeRuntime.place(world,floorPos,floorState,UUID.randomUUID(),null,floorPayload);
            check(floorPlaced.outcome()==dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED,"90566 real floor placement: "+floorPlaced.reason());
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){BlockPos at=floorPos.add(x,0,z);check(!world.getBlockState(at).getCollisionShape(world,at).isEmpty(),"90566 actual world collision "+x+","+z);}
            BlockPos grounded=new BlockPos(8,135,8);world.setBlockState(grounded.down(),Blocks.STONE.getDefaultState(),Block.FORCE_STATE);
            var groundId=UUID.randomUUID();var groundPlaced=CompositeRuntime.place(world,grounded,floorState,groundId,null,ImportedTypeAliases.markPayload(null));
            check(groundPlaced.outcome()==dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED,"90566 placed on native support: "+groundPlaced.reason());
            check(world.getBlockState(grounded.down()).isOf(Blocks.STONE),"90566 keeps native support intact");
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)check(CompositeRuntime.contributions(world,grounded.add(x,-1,z)).stream().anyMatch(c->c.owner().instanceId().equals(groundId)&&!c.shape().collision().isEmpty()),"90566 grounded owner covers cell "+x+","+z);
            var onEdge=new ArmorStandEntity(world,grounded.getX()+1.5,grounded.getY()+1,grounded.getZ()+1.5);world.spawnEntity(onEdge);onEdge.move(MovementType.SELF,new Vec3d(0,-2,0));
            check(Math.abs(onEdge.getY()-grounded.getY())<1e-6,"entity stands on outer plank beyond native support");onEdge.discard();
            var touching=WoodFloorCollision.placementBoxes(world,List.of(new net.minecraft.util.math.Box(8,134.9375,8,9,135,9)));
            check(touching.isEmpty(),"flat floor accepts coplanar support contact");
            var penetrating=WoodFloorCollision.placementBoxes(world,List.of(new net.minecraft.util.math.Box(8,134.5,8,9,134.5625,9)));
            check(!penetrating.isEmpty(),"flat floor still checks penetration below support surface");
            int variants=0;
            for(var block:CompositeArchitecture.blocks())for(int variant=0;variant<block.spec.variants.size();variant++){
                var s=block.getDefaultState().with(CompositeRootBlock.VARIANT,variant%16);var bank=ImportedArtworkBlock.bankProperty(s);if(bank!=null)s=s.with(bank,variant/16);
                if(!WoodFloorCollision.supports(block.spec,s))continue;variants++;
                for(int yaw=0;yaw<8;yaw++){
                    var shapes=WoodFloorCollision.footprint(block.spec,s.with(CompositeRootBlock.ROTATION,yaw),0,0,0,0);check(shapes.values().stream().anyMatch(f->!f.collision().isEmpty()),block.spec.id+" floor variant "+variant+" rotation "+yaw);
                }
            }
            check(variants>=17,"all wooden floor variants covered");
            int repaired=0;
            for(var recipe:StaticArchitectureCatalog.recipes())if(recipe.fragments().stream().anyMatch(f->WoodFloorCollision.supports(f.pose().block().spec,f.pose().state()))){
                List<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box> expected=new ArrayList<>();
                for(var fragment:recipe.fragments()){var o=fragment.origin();var f=fragment.pose().footprint().get(new Cell(-o.x(),-o.y(),-o.z()));if(f!=null)expected.addAll(f.collision());}
                check(recipe.collision().equals(expected),"existing static floor recipe "+recipe.index()+" updates in place");repaired++;
            }
            check(repaired>0,"existing static floor recipes repaired");
            var poseExport=new JsonArray();
            for(var pose:StaticArchitectureCatalog.poses()){
                JsonObject row=new JsonObject();row.addProperty("key",pose.key());row.addProperty("source",pose.block().spec.id.toString());var cells=new JsonArray();
                for(var entry:pose.footprint().entrySet()){var cell=new JsonObject();cell.add("cell",new Gson().toJsonTree(new int[]{entry.getKey().x(),entry.getKey().y(),entry.getKey().z()}));cell.add("collision",new Gson().toJsonTree(entry.getValue().collision()));cells.add(cell);}row.add("footprint",cells);poseExport.add(row);
            }
            Path out=Path.of(System.getProperty("dw.blockqa.output"));Files.createDirectories(out);Files.writeString(out.resolve("runtime-poses.json"),new GsonBuilder().setPrettyPrinting().create().toJson(poseExport));
            report.addProperty("status","PASS");report.addProperty("materials",i);report.addProperty("helperVariants",overlays);report.addProperty("floorVariants",variants);report.addProperty("staticFloorRecipes",repaired);
        }catch(Throwable failure){report.addProperty("status","FAIL");report.addProperty("error",failure.toString());failure.printStackTrace();}
        report.add("checks",checks);
        try{Path out=Path.of(System.getProperty("dw.blockqa.output"));Files.createDirectories(out);Files.writeString(out.resolve("checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception failure){failure.printStackTrace();}
        server.stop(false);
    }
}
