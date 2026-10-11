package dev.dreamwalker.bloodbornedw.v123qa;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.debug.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.MovementType;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

/** Explicit isolated helper. Its classes and resources never enter the production JAR. */
public final class OutlineNativeAudit implements ModInitializer {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final JsonObject result=new JsonObject();
    private final JsonArray fixtures=new JsonArray();
    private List<CompositeRootBlock> blocks;
    private int ticks,index,variants,poses,states,emptyStates,checkedCells,offsetStates;
    private boolean done;
    @Override public void onInitialize(){ServerTickEvents.END_SERVER_TICK.register(server->{
        if(!server.isDedicated()||done||++ticks<30)return;
        try{
            ServerWorld world=server.getOverworld();
            Path saved=server.getSavePath(WorldSavePath.ROOT).resolve("v123-fixture.json");
            if(blocks==null){
                result.addProperty("schema","dreamwalker-v123-native-outline-audit-v1");
                result.addProperty("productionJarSha256",OutlineQaEvidence.productionSha());
                result.add("fixtures",fixtures);
                if(Files.isRegularFile(saved)){reopen(world,saved);finish(world,"PASS_REOPEN");return;}
                require(server.getSaveProperties().getLevelName().equals("isolated-smoke-world"),"Fresh test world required");
                blocks=CompositeArchitecture.blocks().stream().filter(b->b.spec.simplePhysicalCollision).sorted(Comparator.comparing(b->b.spec.id.toString())).toList();
                require(blocks.size()==1045,"1045 NEW registry roots expected");
                for(var old:CompositeArchitecture.blocks())if(!old.spec.simplePhysicalCollision){
                    require(ImportedSelectionShapes.outline(world,new BlockPos(2048,128,2048),old.getDefaultState(),ShapeContext.absent()).isEmpty(),"V12 outline entered NEW mechanism "+old.spec.id);
                }
            }
            for(int n=0;n<3&&index<blocks.size();n++)geometry(blocks.get(index++));
            if(index<blocks.size()){if(index%30<3)write("RUNNING");return;}
            require(variants==1802&&poses==1803,"Offered variant/pose counts changed unexpectedly "+variants+"/"+poses);
            world.setTimeOfDay(6000);world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);
            world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,server);world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(0,server);
            for(String id:List.of("90407","90083","90086","90595","93206","90049","90151")){
                var row=DebugCatalogue.entries().stream().filter(e->e.temporaryId().equals(id)).findFirst().orElseThrow();
                var block=(CompositeRootBlock)Registries.BLOCK.get(row.registryId());
                fixture(world,actor(world),block,0,0,new NbtCompound(),id,true);
                fixture(world,actor(world),block,0,1,new NbtCompound(),id+"_45",false);
            }
            for(String path:List.of("source_hold_ladder_01","source_hold_ladder_02","source_hold_ladder_03","source_deepslate_iron_ore","source_acacia_fence_gate")){
                var block=CompositeArchitecture.kindBlock(path);fixture(world,actor(world),block,0,1,new NbtCompound(),path,false);
            }
            var door=blocks.stream().filter(b->b.spec.openable).findFirst().orElseThrow();
            fixture(world,actor(world),door,0,0,new NbtCompound(),"openable",false);
            var ladder=CompositeArchitecture.kindBlock("source_hold_ladder_02");NbtCompound shift=new NbtCompound();shift.putDouble(VerticalMount.KEY,1.25);
            NbtList vector=new NbtList();for(double v:new double[]{.25,.5,-.125})vector.add(NbtDouble.of(v));shift.put("SourceShift",vector);
            fixture(world,actor(world),ladder,0,1,shift,"height_and_source_shift",false);
            result.addProperty("newRegistryCount",blocks.size());result.addProperty("offeredVariantCount",variants);result.addProperty("distinctPoseCount",poses);
            result.addProperty("exactGeometryStates",states);result.addProperty("passableGeometryStates",emptyStates);result.addProperty("perCellChecks",checkedCells);result.addProperty("offsetGeometryStates",offsetStates);
            result.addProperty("physicalMouseKeyboard","NOT_RUN");result.addProperty("realAuthorExport","NOT_SUPPLIED_NOT_APPLIED");
            result.addProperty("protectedOldOutlineOptIn","PASS_NONE");result.add("fixtures",fixtures);
            Files.writeString(saved,JSON.toJson(result)+"\n");finish(world,"PASS");
        }catch(Throwable failure){done=true;result.addProperty("failure",failure.toString());java.io.StringWriter trace=new java.io.StringWriter();failure.printStackTrace(new java.io.PrintWriter(trace));result.addProperty("stackTrace",trace.toString());write("FAIL");try{Files.writeString(Path.of("qa-normal-stop.request"),"outline audit failed\n");}catch(Exception ignored){}}
    });}
    private void geometry(CompositeRootBlock block){
        for(int v=0;v<block.spec.variants.size();v++){
            variants++;var variant=block.spec.variants.get(v);boolean[] opened=variant.open()==variant.closed()?new boolean[]{false}:new boolean[]{false,true};poses+=opened.length;
            for(boolean open:opened)for(int yaw=0;yaw<8;yaw++)for(var profile:CompositeRootBlock.Profile.values()){
                BlockState state=block.getDefaultState().with(CompositeRootBlock.VARIANT,v).with(CompositeRootBlock.OPEN,open).with(CompositeRootBlock.ROTATION,yaw).with(CompositeRootBlock.PROFILE,profile);
                checkFootprint(block,state,new NbtCompound());states++;
                if(v==0&&yaw==1&&profile==CompositeRootBlock.Profile.BASE){NbtCompound payload=new NbtCompound();payload.putDouble(VerticalMount.KEY,.3125);NbtList shift=new NbtList();for(double d:new double[]{.25,-.125,.375})shift.add(NbtDouble.of(d));payload.put("SourceShift",shift);checkFootprint(block,state,payload);offsetStates++;}
            }
        }
    }
    private void checkFootprint(CompositeRootBlock block,BlockState state,NbtCompound payload){
        var physical=CompositeSourceShift.footprint(block.spec,state,payload);var selected=ImportedSelectionShapes.footprint(block,state,payload);
        boolean passable=physical.values().stream().allMatch(f->f.collision().isEmpty());if(passable)emptyStates++;
        Set<Cell> all=new TreeSet<>(physical.keySet());all.addAll(selected.keySet());boolean any=false;
        for(Cell cell:all){List<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box> selection=selected.getOrDefault(cell,List.of());
            any|=!selection.isEmpty();checkedCells++;
            if(!passable){var p=physical.get(cell);require(same(exact(p==null?List.of():p.collision()),exact(selection)),"Outline differs from real collision "+block.spec.id+" "+state+" cell="+cell+" payload="+payload);}
            else require(selection.size()<=1,"Passable per-cell outline contains multiple detail boxes "+block.spec.id+" "+cell);
            for(var box:selection)require(box.minX()>=-1e-8&&box.minY()>=-1e-8&&box.minZ()>=-1e-8&&box.maxX()<=1+1e-8&&box.maxY()<=1+1e-8&&box.maxZ()<=1+1e-8,"Selection not clipped to own cell");
        }
        require(any,"NEW object became impossible to select "+block.spec.id+" "+state);
        if(passable){VoxelShape global=VoxelShapes.empty();for(var e:selected.entrySet())global=VoxelShapes.union(global,exact(e.getValue()).offset(e.getKey().x(),e.getKey().y(),e.getKey().z()));require(global.simplify().getBoundingBoxes().size()==1,"Passable selection is not one ordinary global AABB "+block.spec.id);require(same(global,passableOracle(block,state,payload)),"Passable rectangle differs from independent source-bound/yaw/offset oracle "+block.spec.id+" "+state);}
    }
    private void fixture(ServerWorld world,ServerPlayerEntity actor,CompositeRootBlock block,int variant,int yaw,NbtCompound payload,String role,boolean screenshot){
        BlockPos root=new BlockPos(32+fixtures.size()*24,128,32);world.setChunkForced(root.getX()>>4,root.getZ()>>4,true);
        BlockState intended=block.getDefaultState().with(CompositeRootBlock.VARIANT,variant).with(CompositeRootBlock.ROTATION,yaw);var fp=CompositeSourceShift.footprint(block.spec,intended,payload);
        int minX=fp.keySet().stream().mapToInt(Cell::x).min().orElse(0)-3,maxX=fp.keySet().stream().mapToInt(Cell::x).max().orElse(0)+3;
        int minZ=fp.keySet().stream().mapToInt(Cell::z).min().orElse(0)-3,maxZ=fp.keySet().stream().mapToInt(Cell::z).max().orElse(0)+3;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){BlockPos p=root.add(x,-1,z);world.getChunk(p);world.setBlockState(p,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);}
        actor.refreshPositionAndAngles(root.getX()+.5,140,root.getZ()-4,yaw*45,0);ItemStack stack=new ItemStack(block.asItem());stack.getOrCreateSubNbt("BlockStateTag").putString("variant",Integer.toString(variant));
        actor.setStackInHand(Hand.MAIN_HAND,stack);var used=stack.getItem().useOnBlock(new ItemUsageContext(actor,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofBottomCenter(root),Direction.UP,root.down(),false)));
        require(used.isAccepted()&&world.getBlockState(root).isOf(block),"Ordinary NEW item placement failed "+role+" "+CompositeRuntime.lastResult());
        var own=(CompositeBlockEntity)world.getBlockEntity(root);final var owner=own.resident();
        if(!payload.isEmpty())require(CompositeRuntime.transitionPayload(world,owner,world.getBlockState(root),payload,actor).outcome()==TransactionCore.Outcome.COMMITTED,"Typed height/shift edit failed");
        if(block.spec.openable){require(CompositeRuntime.transition(world,owner,world.getBlockState(root).with(CompositeRootBlock.OPEN,true),actor).outcome()==TransactionCore.Outcome.COMMITTED,"Open transition failed");assertNative(world,root);require(CompositeRuntime.transition(world,owner,world.getBlockState(root).with(CompositeRootBlock.OPEN,false),actor).outcome()==TransactionCore.Outcome.COMMITTED,"Close transition failed");}
        assertNative(world,root);var selections=ImportedSelectionShapes.footprint(block,world.getBlockState(root),own.payload());
        var probe=selections.entrySet().stream().filter(e->!e.getValue().isEmpty()&&CompositeRuntime.contributions(world,root.add(e.getKey().x(),e.getKey().y(),e.getKey().z())).stream().anyMatch(c->c.owner().equals(owner))).max(Comparator.comparingDouble(e->e.getValue().get(0).maxY()-e.getValue().get(0).minY())).orElseThrow();
        var box=probe.getValue().get(0);BlockPos cell=root.add(probe.getKey().x(),probe.getKey().y(),probe.getKey().z());double x=cell.getX()+(box.minX()+box.maxX())/2,y=cell.getY()+(box.minY()+box.maxY())/2,z=cell.getZ()+(box.minZ()+box.maxZ())/2;
        actor.refreshPositionAndAngles(x,y-actor.getStandingEyeHeight(),z-2.5,0,0);actor.setHeadYaw(0);actor.setBodyYaw(0);actor.prevHeadYaw=0;actor.prevBodyYaw=0;actor.prevYaw=0;actor.prevPitch=0;
        Vec3d direction=actor.getRotationVec(1);require(direction.squaredDistanceTo(new Vec3d(0,0,1))<1e-10,"QA actor pose has not matched the intended native aiming direction "+direction);
        BlockHitResult hit=world.raycast(new RaycastContext(actor.getEyePos(),actor.getEyePos().add(direction.multiply(5)),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,actor));
        var targeted=CompositeRuntime.target(world,hit.getBlockPos(),actor);
        if(hit.getType()!=HitResult.Type.BLOCK||!owner.equals(targeted)){
            JsonObject debug=new JsonObject();debug.addProperty("role",role);debug.addProperty("expectedOwner",owner.toString());debug.addProperty("actualOwner",String.valueOf(targeted));debug.addProperty("hitType",hit.getType().toString());debug.addProperty("hit",hit.getPos().toString());debug.addProperty("hitCell",hit.getBlockPos().toString());debug.addProperty("probeCell",cell.toString());debug.addProperty("probeBox",box.toString());debug.addProperty("eye",actor.getEyePos().toString());debug.addProperty("rotation",actor.getRotationVec(1).toString());debug.addProperty("carrier",world.getBlockState(hit.getBlockPos()).toString());debug.addProperty("actualNativeOutline",world.getBlockState(hit.getBlockPos()).getOutlineShape(world,hit.getBlockPos(),ShapeContext.of(actor)).getBoundingBoxes().toString());debug.addProperty("contributions",CompositeRuntime.contributions(world,hit.getBlockPos()).toString());result.add("rayDiagnostic",debug);
        }
        require(hit.getType()==HitResult.Type.BLOCK&&owner.equals(targeted),"Ordinary native ray lost NEW object "+role+" "+hit.getBlockPos());
        ItemStack picked=world.getBlockState(hit.getBlockPos()).getBlock().getPickStack(world,hit.getBlockPos(),world.getBlockState(hit.getBlockPos()));require(picked.isOf(block.asItem()),"Native pick lost object");
        var before=CompositeRuntime.instance(world,root,world.getBlockState(root),owner.instanceId(),own.payload());
        boolean passable=before.cells().values().stream().allMatch(f->f.collision().isEmpty());
        if(passable){var walker=actor(world);walker.refreshPositionAndAngles(root.getX()-1.2,root.getY()+.1,root.getZ()+.5,0,0);double start=walker.getX();walker.move(MovementType.SELF,new Vec3d(2.4,0,0));require(Math.abs(walker.getX()-start-2.4)<1e-6,"Simple rectangle blocked ordinary player motion "+role);walker.discard();}
        actor.interactionManager.tryBreakBlock(hit.getBlockPos());CompositeRuntime.drain(world);
        require(before.cells().keySet().stream().allMatch(c->CompositeRuntime.contributions(world,root.add(c.x(),c.y(),c.z())).stream().noneMatch(e->e.owner().equals(owner))),"Ordinary break left owner membership");
        // Restore a fresh fixture via actual copied item, then preserve typed QA offsets.
        actor.setYaw(yaw*45);actor.setPosition(root.getX()+.5,140,root.getZ()-4);actor.setStackInHand(Hand.MAIN_HAND,picked);
        require(picked.getItem().useOnBlock(new ItemUsageContext(actor,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofBottomCenter(root),Direction.UP,root.down(),false))).isAccepted(),"Picked item cannot re-place");
        own=(CompositeBlockEntity)world.getBlockEntity(root);var restored=own.resident();if(!payload.isEmpty())require(CompositeRuntime.transitionPayload(world,restored,world.getBlockState(root),payload,actor).outcome()==TransactionCore.Outcome.COMMITTED,"Restore offset failed");assertNative(world,root);
        JsonObject row=new JsonObject();row.addProperty("role",role);row.addProperty("registry",block.spec.id.toString());row.addProperty("uuid",restored.instanceId().toString());row.add("root",JSON.toJsonTree(List.of(root.getX(),root.getY(),root.getZ())));row.addProperty("rotation",world.getBlockState(root).get(CompositeRootBlock.ROTATION));row.addProperty("variant",variant);row.addProperty("status","PASS");row.addProperty("ordinaryPlacementRayPickBreakCopy","PASS");row.addProperty("passableMovement",passable?"PASS_ACTUAL_PLAYER_MOVE":"NOT_APPLICABLE");row.addProperty("screenshot",screenshot);
        // Slightly elevated native camera still aims inside the same simple box.
        row.add("camera",JSON.toJsonTree(List.of(x,y+.25-1.62,z-3,0,Math.toDegrees(Math.atan2(.25,3)))));
        row.addProperty("savedPayload",own.payload().toString());row.addProperty("cellCount",CompositeRuntime.instance(world,root,world.getBlockState(root),restored.instanceId(),own.payload()).cells().size());fixtures.add(row);actor.discard();
    }
    private static void assertNative(ServerWorld world,BlockPos root){
        BlockState state=world.getBlockState(root);var block=(CompositeRootBlock)state.getBlock();var own=(CompositeBlockEntity)world.getBlockEntity(root);var instance=CompositeRuntime.instance(world,root,state,own.resident().instanceId(),own.payload());
        boolean passable=instance.cells().values().stream().allMatch(f->f.collision().isEmpty());
        for(var e:instance.cells().entrySet()){
            BlockPos p=root.add(e.getKey().x(),e.getKey().y(),e.getKey().z());BlockState carrier=world.getBlockState(p);
            // A passable decoration may share an ordinary platform block. Its
            // native backing still has its own cube and must never be erased.
            if(!(carrier.getBlock() instanceof CompositeRootBlock)&&!carrier.isOf(CompositeArchitecture.CELL))continue;
            VoxelShape outline=carrier.getOutlineShape(world,p),collision=carrier.getCollisionShape(world,p);
            if(!passable)require(same(outline,collision),"Actual native outline/collision mismatch at "+p+" "+block.spec.id);
            else {require(collision.isEmpty(),"Passable object gained physical collider");require(outline.simplify().getBoundingBoxes().size()<=1,"Native passable outline still detailed");}
        }
    }
    private void reopen(ServerWorld world,Path saved)throws Exception{
        JsonObject original=JsonParser.parseString(Files.readString(saved)).getAsJsonObject();require(original.get("productionJarSha256").getAsString().equals(OutlineQaEvidence.productionSha()),"Reopen must use identical production artifact");
        for(JsonElement entry:original.getAsJsonArray("fixtures")){JsonObject row=entry.getAsJsonObject();JsonArray coords=row.getAsJsonArray("root");BlockPos root=new BlockPos(coords.get(0).getAsInt(),coords.get(1).getAsInt(),coords.get(2).getAsInt());world.getChunk(root);
            require(Registries.BLOCK.getId(world.getBlockState(root).getBlock()).toString().equals(row.get("registry").getAsString()),"Saved registry lost");
            var own=(CompositeBlockEntity)world.getBlockEntity(root);require(own.resident().instanceId().toString().equals(row.get("uuid").getAsString()),"Saved UUID changed");
            for(Cell cell:CompositeRuntime.instance(world,root,world.getBlockState(root),own.resident().instanceId(),own.payload()).cells().keySet())world.getChunk(root.add(cell.x(),cell.y(),cell.z()));assertNative(world,root);fixtures.add(row.deepCopy());
        }result.add("fixtures",fixtures);result.addProperty("savedRoots",fixtures.size());result.addProperty("savedUuidAndStateAndNativeShapes","PASS");
    }
    private void finish(ServerWorld world,String status)throws Exception{done=true;world.getServer().save(false,true,true);write(status);Files.writeString(Path.of("qa-normal-stop.request"),"outline audit completed\n");}
    private void write(String status){result.addProperty("status",status);result.addProperty("completedRoots",index);result.addProperty("ticks",ticks);result.addProperty("exactGeometryStates",states);result.addProperty("passableGeometryStates",emptyStates);result.addProperty("perCellChecks",checkedCells);result.addProperty("offsetGeometryStates",offsetStates);try{Files.writeString(Path.of("v123-native-output.json"),JSON.toJson(result)+"\n");}catch(Exception e){throw new RuntimeException(e);}}
    private static ServerPlayerEntity actor(ServerWorld w){var a=new ServerPlayerEntity(w.getServer(),w,new GameProfile(UUID.nameUUIDFromBytes("v123-outline-actor".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"OutlineAudit"));a.networkHandler=new ServerPlayNetworkHandler(w.getServer(),new ClientConnection(NetworkSide.SERVERBOUND){@Override public void send(Packet<?> p){}@Override public void send(Packet<?> p,PacketCallbacks c){}},a);a.changeGameMode(GameMode.CREATIVE);a.getAbilities().allowModifyWorld=true;return a;}
    private static VoxelShape exact(List<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box> boxes){VoxelShape s=VoxelShapes.empty();for(var b:boxes)s=VoxelShapes.union(s,VoxelShapes.cuboid(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()));return s;}
    /** Independent bounding-box oracle: no production geometry transform calls. */
    private static VoxelShape passableOracle(CompositeRootBlock block,BlockState state,NbtCompound payload){
        double x0=Double.POSITIVE_INFINITY,y0=x0,z0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1,z1=x1;
        for(var b:block.spec.pose(state).selection()){x0=Math.min(x0,b.from().x()/16);y0=Math.min(y0,b.from().y()/16);z0=Math.min(z0,b.from().z()/16);x1=Math.max(x1,b.to().x()/16);y1=Math.max(y1,b.to().y()/16);z1=Math.max(z1,b.to().z()/16);}
        double a=state.get(CompositeRootBlock.ROTATION)*Math.PI/4,c=Math.cos(a),s=Math.sin(a),rx0=Double.POSITIVE_INFINITY,rz0=rx0,rx1=Double.NEGATIVE_INFINITY,rz1=rx1;
        for(double x:new double[]{x0,x1})for(double z:new double[]{z0,z1}){double rx=.5+c*(x-.5)-s*(z-.5),rz=.5+s*(x-.5)+c*(z-.5);rx0=Math.min(rx0,rx);rx1=Math.max(rx1,rx);rz0=Math.min(rz0,rz);rz1=Math.max(rz1,rz);}
        NbtList vector=payload.getList("SourceShift",6);double sx=vector.size()==3?vector.getDouble(0):0,sy=vector.size()==3?vector.getDouble(1):0,sz=vector.size()==3?vector.getDouble(2):0;
        double wx=sx*c-sz*s,wz=sx*s+sz*c,my=sy+payload.getDouble("MountY")+payload.getDouble(VerticalMount.KEY);
        return VoxelShapes.cuboid(rx0+wx,y0+my,rz0+wz,rx1+wx,y1+my,rz1+wz);
    }
    private static boolean same(VoxelShape a,VoxelShape b){return !VoxelShapes.matchesAnywhere(a,b,net.minecraft.util.function.BooleanBiFunction.NOT_SAME);}
    private static void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
}
