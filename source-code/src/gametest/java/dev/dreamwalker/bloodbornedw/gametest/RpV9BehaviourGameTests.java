package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornerp.object.*;
import dev.dreamwalker.bloodbornerp.content.SourceLegacyPayload;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

/** Requested RP behaviours through real entities/items/player movement, separate from visual acceptance. */
public final class RpV9BehaviourGameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_v9")
    public void diagnosticsPreserveTypedRpStateRefusalsAndDeduplicateCorruptEnvelope(TestContext c){
        ServerWorld world=c.getWorld();RpObjectEntity cage=create(c,"cage_obj_1"),gate=create(c,"wood_gate");
        dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.stop(world.getServer(),"RP_TEST_BASELINE");
        cage.setDogsVisible(false);cage.setLocked(true);cage.setObjectScale(1.25f);NbtCompound off=cage.writeNbt(new NbtCompound());
        cage.setDogsVisible(true);cage.setLocked(false);cage.setObjectScale(1);
        dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.start(world,60,new dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.Filter("object",RpDiagnostics.typeId(cage),cage.getUuidAsString(),world.getRegistryKey().getValue().toString(),cage.getBlockPos(),0));
        try{
            cage.setDogsVisible(false);cage.setLocked(true);cage.setObjectScale(1.25f);NbtCompound on=cage.writeNbt(new NbtCompound());
            c.assertTrue(off.equals(on),"diagnostics off/on preserves every typed saved RP field");
            long gateErrors=dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.errors().stream().filter(row->gate.getUuidAsString().equals(row.get("instanceId"))).count();
            c.assertTrue(!gate.setOpen(true),"wood gate retains pulse-only ordinary refusal");
            c.assertTrue(dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.errors().stream().filter(row->gate.getUuidAsString().equals(row.get("instanceId"))).count()==gateErrors,"ordinary refusal is an event, never an ERROR");
            NbtCompound malformed=on.copy(),envelope=new NbtCompound();envelope.putString("Schema","wrong type");malformed.put(SourceLegacyPayload.KEY,envelope);cage.readNbt(malformed.copy());cage.readNbt(malformed.copy());
            var errors=dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.errors().stream().filter(row->cage.getUuidAsString().equals(row.get("instanceId"))&&"source_payload_corruption".equals(row.get("category"))).toList();
            c.assertTrue(errors.size()==1&&((Number)errors.get(0).get("repeats")).longValue()==2,"two real corruption reads retain one exact identity/error with repeat count2");
            c.assertTrue(cage.writeNbt(new NbtCompound()).equals(on),"invalid envelope retains the existing nonrecursive fallback and all valid role/identity fields");
        }finally{dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.stop(world.getServer(),"RP_TEST_COMPLETE");}c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_v9")
    public void dogsAreIndependentAndTypedStateSurvivesPickAndSave(TestContext c){
        for(String id:List.of("cage_obj_1","cage_obj_2","cage_obj_3")){
            RpObjectEntity a=create(c,id),b=create(c,id);UUID uuid=a.getUuid();a.setCustomName(Text.literal("source dog cage"));
            c.assertTrue(a.dogsVisible()&&b.dogsVisible(),"legacy/default dogs are visible");
            c.assertTrue(a.setDogsVisible(false)&&!a.dogsVisible()&&b.dogsVisible(),"hiding one instance leaves the same type's other instance visible");
            NbtCompound saved=a.writeNbt(new NbtCompound());RpObjectEntity restored=create(c,id);restored.readNbt(saved);
            c.assertTrue(!restored.dogsVisible()&&restored.getUuid().equals(uuid)&&restored.getCustomName().equals(a.getCustomName()),"typed dog state preserves identity and CustomName");
            ItemStack picked=a.getPickBlockStack();c.assertTrue(!picked.getNbt().getCompound("bloodborne_rp_object").getBoolean("DogsVisible"),"one picked item retains hidden dogs");
            saved.remove("DogsVisible");restored.readNbt(saved);c.assertTrue(restored.dogsVisible(),"missing legacy flag defaults to source-visible dogs");
        }c.assertTrue(!create(c,"tree1").setDogsVisible(false),"accepted tree is not a dog visibility target");c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_v9")
    public void exactFurnitureAliasesPreserveOldRegistryUuidNamePayloadAndLinks(TestContext c){
        for(var entry:RpObjectCompatibility.ALIASES.entrySet()){
            RpObjectEntity original=create(c,entry.getKey());original.setCustomName(Text.literal("legacy "+entry.getKey()));
            NbtCompound source=original.writeNbt(new NbtCompound());source.putInt("AnimationId",0);source.putString("Opaque","unchanged source field");
            UUID linked=UUID.randomUUID();NbtList links=new NbtList();links.add(NbtHelper.fromUuid(linked));source.put("Links",links);UUID uuid=original.getUuid();original.readNbt(source.copy());
            NbtCompound saved=original.writeNbt(new NbtCompound());
            c.assertTrue(Registries.ENTITY_TYPE.getId(original.getType()).getPath().equals(entry.getKey()),"old registry remains loadable");
            c.assertTrue(original.assetId().equals(entry.getValue())&&original.getUuid().equals(uuid)&&original.hasCustomName(),"canonical display/art does not recreate an old instance");
            c.assertTrue(saved.get("Links").equals(links)&&saved.getString("Opaque").equals("unchanged source field"),"typed UUID links and opaque source fields survive");
            c.assertTrue(saved.getCompound(SourceLegacyPayload.KEY).getCompound("Original").equals(source),"exact full typed source provenance retained");
            ItemStack picked=original.getPickBlockStack();c.assertTrue(Registries.ITEM.getId(picked.getItem()).getPath().equals(entry.getValue()+"_placer"),"new pick uses the canonical construction item");
            c.assertTrue(picked.getName().equals(original.getCustomName())&&!picked.getNbt().contains("Links")&&!picked.getNbt().contains("UUID"),"item preserves CustomName without cloning world identity/links");
        }c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_v9")
    public void realSmallChandelierItemMountsVisibleBaseAndCeilingHook(TestContext c){
        ServerPlayerEntity p=c.createMockCreativeServerPlayerInWorld();Vec3d at=base(c);ServerWorld w=c.getWorld();BlockPos floor=BlockPos.ofFloored(at).down();w.setBlockState(floor,Blocks.STONE.getDefaultState());
        RpObjectEntity ground=use(c,p,"chandelier_small",floor,Direction.UP,at,180);c.assertTrue(Math.abs(ground.visualBounds().minY-at.y)<1e-6,"floor placement aligns actual model bottom, not old y-5.5");
        c.assertTrue(ground.activePhysicalBoxes().isEmpty()&&!ground.isCollidable(),"hanging decoration does not acquire a model AABB collider");ground.removeByBuilder();
        Vec3d ceiling=at.add(4,10,0);BlockPos ceilingBlock=BlockPos.ofFloored(ceiling);w.setBlockState(ceilingBlock,Blocks.STONE.getDefaultState());
        RpObjectEntity hanging=use(c,p,"chandelier_small",ceilingBlock,Direction.DOWN,ceiling,180);
        c.assertTrue(Math.abs(hanging.visualBounds().maxY-ceiling.y)<1e-6&&hanging.visualBounds().minY<ceiling.y-5,"ceiling click aligns source chain hook and retains visible body beneath it");
        hanging.removeByBuilder();w.setBlockState(floor,Blocks.AIR.getDefaultState());w.setBlockState(ceilingBlock,Blocks.AIR.getDefaultState());p.discard();c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_v9")
    public void realStairsLadderAndWindowItemsUseBasesWhileSavedSourcePosIsExact(TestContext c){
        ServerPlayerEntity p=c.createMockCreativeServerPlayerInWorld();Vec3d at=base(c);ServerWorld w=c.getWorld();
        for(String id:List.of("stairs","ladder","npc_window")){
            for(int x=-2;x<=2;x++)for(int z=-2;z<=15;z++)w.setBlockState(BlockPos.ofFloored(at).add(x,-1,z),Blocks.STONE.getDefaultState());
            RpObjectEntity entity=use(c,p,id,BlockPos.ofFloored(at).down(),Direction.UP,at,270);
            double minimum=entity.activePhysicalBoxes().stream().mapToDouble(b->b.minY).min().orElseThrow();
            c.assertTrue(Math.abs(minimum-at.y)<1e-6,"working base reaches selected surface: "+id+" y="+minimum);
            c.assertTrue(entity.getBoundingBox().contains(at.add(0,.5,0))||id.equals("npc_window"),"query/culling bounding box covers real base far below entity origin");
            if(id.equals("stairs"))c.assertTrue(entity.getY()-at.y>11.9,"stairs source origin is compensated only for new construction");
            if(id.equals("ladder"))c.assertTrue(entity.getY()-at.y>29.5&&entity.climbingBoxes().size()==2,"ladder base and its two authored moving/static zones are accessible");
            if(id.equals("stairs")||id.equals("ladder")){
                p.setPosition(at.x,at.y,at.z-1);aim(p,at.add(0,1.5,0));c.assertTrue(p.squaredDistanceTo(entity)>64,"actual base is farther than the old source-origin reach");
                c.assertTrue(entity.damage(p.getDamageSources().playerAttack(p),1)&&entity.isRemoved(),"ordinary authorized creative attack reaches the real visible base:"+id);
                c.assertTrue(!RpObjectIndex.in(w,new Box(at.add(-2,-1,-2),at.add(2,3,2))).contains(entity),"removed owner immediately leaves the loaded-object index");
            }else entity.removeByBuilder();
            RpObjectEntity loaded=create(c,id);NbtCompound source=loaded.writeNbt(new NbtCompound());NbtList pos=new NbtList();pos.add(NbtDouble.of(200.25));pos.add(NbtDouble.of(160.5));pos.add(NbtDouble.of(-100.75));source.put("Pos",pos);source.putInt("AnimationId",0);loaded.readNbt(source);
            c.assertTrue(loaded.writeNbt(new NbtCompound()).get("Pos").equals(pos),"reading old source entity does not run the new placement compensation: "+id);
        }
        for(int x=-2;x<=2;x++)for(int z=-2;z<=15;z++)w.setBlockState(BlockPos.ofFloored(at).add(x,-1,z),Blocks.AIR.getDefaultState());p.discard();c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_v9")
    public void gateOneShotRejectsHeldCadenceOffhandAndReloadBackground(TestContext c){
        RpObjectEntity gate=create(c,"wood_gate");PlayerEntity p=c.createMockSurvivalPlayer();
        c.assertTrue(!gate.requestWoodGatePulse(p,Hand.OFF_HAND)&&!gate.woodGatePulseActive(),"offhand never starts or queues a pulse");
        c.assertTrue(gate.requestWoodGatePulse(p,Hand.MAIN_HAND)&&gate.woodGatePulseTicks()==32&&!gate.isOpen(),"one mainhand click starts authored1.6seconds without fakeOpen");
        for(int i=0;i<32;i++){gate.tick();c.assertTrue(!gate.requestWoodGatePulse(p,Hand.MAIN_HAND),"held/repeated requests cannot restart or queue: "+i);}
        c.assertTrue(!gate.woodGatePulseActive()&&gate.woodGatePulseTicks()==0,"exact32 ticks return idle");
        c.assertTrue(!gate.requestWoodGatePulse(p,Hand.MAIN_HAND),"continuous held cadence remains refused after clip completion");
        c.assertTrue(gate.pulseFromMechanism(),"an independent mechanism command starts a separate real pulse");NbtCompound saved=gate.writeNbt(new NbtCompound());RpObjectEntity loaded=create(c,"wood_gate");loaded.readNbt(saved);
        c.assertTrue(!loaded.woodGatePulseActive()&&!saved.contains("WoodGatePulseTicks")&&!loaded.isOpen(),"load is idle and does not persist local pending clicks/background playback");
        c.waitAndRun(8,()->{c.assertTrue(gate.requestWoodGatePulse(p,Hand.MAIN_HAND),"a distinct later click can start a fresh pulse");c.complete();});
        // The saved instance is deliberately separate and cannot inherit a shared controller clock.
        for(int i=0;i<32;i++)gate.tick();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_v9")
    public void authoredGateAndLadderKeyframesRemainNumericExactAndIdleNeutral(TestContext c){
        c.assertTrue(AuthoredObjectMotion.gateBones().size()==8,"all eight authored gate bone channels are sampled");
        c.assertTrue(Math.abs(AuthoredObjectMotion.gateSample("right_door","rotation",.64).y+3.75)<1e-8,"original right leaf keyframe");
        c.assertTrue(Math.abs(AuthoredObjectMotion.gateSample("bar_front","position",.64).z+6.17)<1e-8,"original bar translation keyframe");
        c.assertTrue(Math.abs(AuthoredObjectMotion.gateSample("right_door","rotation",.18).y+1.25)<1e-8,"source numeric interpolation is linear");
        for(String bone:AuthoredObjectMotion.gateBones())for(String channel:List.of("rotation","position"))c.assertTrue(AuthoredObjectMotion.gateSample(bone,channel,1.6).lengthSquared()<1e-10,"one-shot ends at the authored neutral pose: "+bone);
        RpObjectEntity ladder=create(c,"ladder");c.assertTrue(ladder.isOpen()&&ladder.ladderOffsetY()==0,"new default is visible full working ladder");ladder.setOpen(false);c.assertTrue(ladder.ladderOffsetY()==228&&ladder.ladderOffsetZ()==-2,"authored collapsed pose");ladder.setOpen(true);
        for(int i=0;i<20;i++)ladder.tick();c.assertTrue(Math.abs(ladder.ladderOffsetY()-181)<1e-8,"authored1second deployment key");for(int i=20;i<48;i++)ladder.tick();c.assertTrue(ladder.ladderOffsetY()==0&&ladder.ladderOffsetZ()==0,"authored2.4second deployed final pose");c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_v9")
    public void actualWindowAndGateBlockBothSidesWithoutDecorativeBoundingBoxWall(TestContext c){
        PlayerEntity p=c.createMockSurvivalPlayer();Vec3d at=base(c).add(0,35,0);
        for(String id:List.of("npc_window","wood_gate")){
            RpObjectEntity object=create(c,id);object.refreshPositionAndAngles(at.x,at.y,at.z,90,0);c.getWorld().spawnEntity(object);
            c.assertTrue(object.hasCustomPhysicalGeometry()&&!object.isCollidable(),"custom local physics never activates vanilla giant AABB: "+id);
            double y=at.y+(id.equals("wood_gate")?2:0),plane=at.z+(id.equals("npc_window")?.84375:0);
            for(int sign:List.of(-1,1)){p.refreshPositionAndAngles(at.x,y,plane+sign*3,0,0);double z=p.getZ();p.move(MovementType.SELF,new Vec3d(0,0,-sign*6));c.assertTrue(Math.abs(p.getZ()-z)<3,"actual central passage blocks from either side: "+id+" sign="+sign);}
            p.refreshPositionAndAngles(at.x+(id.equals("wood_gate")?9:3),y,plane-3,0,0);double z=p.getZ();p.move(MovementType.SELF,new Vec3d(0,0,6));c.assertTrue(p.getZ()-z>5.99,"outside the working surface is passable: "+id);
            object.removeByBuilder();
        }p.discard();c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=160,batchId="rp_v9")
    public void actualPlayerWalksTwentySixSourceTreadsAndDescends(TestContext c){
        ServerPlayerEntity builder=c.createMockCreativeServerPlayerInWorld();PlayerEntity p=c.createMockSurvivalPlayer();ServerWorld w=c.getWorld();Vec3d at=base(c);
        for(int x=-2;x<=2;x++)for(int z=-3;z<=16;z++)w.setBlockState(BlockPos.ofFloored(at).add(x,-1,z),Blocks.STONE.getDefaultState());
        RpObjectEntity stairs=use(c,builder,"stairs",BlockPos.ofFloored(at).down(),Direction.UP,at,270);c.assertTrue(stairs.activePhysicalBoxes().size()==26,"only the twenty-six source tread cubes provide stair physics");
        p.refreshPositionAndAngles(at.x,at.y+.05,at.z-1,0,0);p.move(MovementType.SELF,new Vec3d(0,-.2,0));double startZ=p.getZ(),startY=p.getY();
        for(int i=0;i<52;i++)p.move(MovementType.SELF,new Vec3d(0,-.08,.25));
        c.assertTrue(p.getZ()-startZ>11&&p.getY()-startY>10,"actual native stepping climbs the visible half-block treads; position="+p.getPos());double upperY=p.getY();
        for(int i=0;i<56;i++)p.move(MovementType.SELF,new Vec3d(0,-.6,-.25));c.assertTrue(upperY-p.getY()>9&&p.getY()>=at.y-1e-6,"actual descent reaches ground without passing through the staircase");
        stairs.removeByBuilder();for(int x=-2;x<=2;x++)for(int z=-3;z<=16;z++)w.setBlockState(BlockPos.ofFloored(at).add(x,-1,z),Blocks.AIR.getDefaultState());builder.discard();p.discard();c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=160,batchId="rp_v9")
    public void actualLadderWorkingZoneClimbsDescendsAndMovesWithBottomBone(TestContext c){
        PlayerEntity p=c.createMockSurvivalPlayer();RpObjectEntity ladder=create(c,"ladder");Vec3d at=base(c).add(0,40,0);ladder.refreshPositionAndAngles(at.x,at.y,at.z,90,0);c.getWorld().spawnEntity(ladder);
        p.refreshPositionAndAngles(at.x,at.y-28,at.z-1.184375,180,0);c.assertTrue(RpObjectIndex.in(c.getWorld(),p.getBoundingBox()).contains(ladder),"loaded-object index finds actual working bounds twenty-eight metres below the origin section");c.assertTrue(p.isClimbing(),"working region near actual lower ladder is climbable far below entity origin");double y=p.getY();
        for(int i=0;i<35;i++)p.travel(new Vec3d(0,0,1));c.assertTrue(p.getY()-y>1,"actual vanilla travel climbs against the thin working face; delta="+(p.getY()-y));
        p.setVelocity(0,0,0);p.setYaw(0);double up=p.getY();for(int i=0;i<25;i++)p.travel(Vec3d.ZERO);c.assertTrue(p.getY()<up-.5,"actual native ladder descent works");
        p.setPosition(at.x+3,at.y-28,at.z-1.4);c.assertTrue(!p.isClimbing(),"large visual origin box does not create a distant climbing zone");
        ladder.setOpen(false);p.setPosition(at.x,at.y-28,at.z-1.3);c.assertTrue(!p.isClimbing(),"collapsed source bottom no longer climbs at old lower position");
        p.setPosition(at.x,at.y-14,at.z-1.3);c.assertTrue(p.isClimbing(),"collapsed animated bottom carries its real working zone upward");
        ladder.removeByBuilder();p.discard();c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_v9")
    public void normalRpItemAllowsRpAndNativeOverlapWithoutReplacingNativeCells(TestContext c){
        ServerPlayerEntity p=c.createMockCreativeServerPlayerInWorld();Vec3d at=base(c);ServerWorld w=c.getWorld();BlockPos floor=BlockPos.ofFloored(at).down();w.setBlockState(floor,Blocks.STONE.getDefaultState());RpObjectEntity window=use(c,p,"npc_window",floor,Direction.UP,at,270);
        ItemStack stack=Registries.ITEM.get(new Identifier("bloodborne_rp","npc_window_placer")).getDefaultStack();stack.setCount(2);p.setStackInHand(Hand.MAIN_HAND,stack);p.setPosition(at.x,at.y,at.z-3);
        ActionResult accepted=stack.getItem().useOnBlock(new ItemUsageContext(p,Hand.MAIN_HAND,new BlockHitResult(at,Direction.UP,floor,false)));c.assertTrue(accepted.isAccepted()&&stack.getCount()==2,"V10 allows permanent RP overlap; Creative stack remains unchanged");
        for(Entity e:w.iterateEntities())if(e instanceof RpObjectEntity rp&&rp!=window&&rp.assetId().equals("npc_window")&&rp.getPos().squaredDistanceTo(window.getPos())<1e-8)rp.removeByBuilder();
        float yaw=window.getYaw();Box next=RpObjectGeometry.localVisualBounds("npc_window");BlockPos obstruct=BlockPos.ofFloored(window.getPos().add(.8,0,-.1));w.setBlockState(obstruct,Blocks.STONE.getDefaultState());
        c.assertTrue(window.rotateByBuilder(p,yaw+90)&&window.getYaw()!=yaw,"V10 allows rotation through native blocks without replacing them");c.assertTrue(w.getBlockState(obstruct).isOf(Blocks.STONE),"RP rotation retains the obstructing native state");w.setBlockState(obstruct,Blocks.AIR.getDefaultState());
        c.assertTrue(window.rotateByBuilder(p,yaw+90),"rotation commits when the real new shape is clear");window.removeByBuilder();w.setBlockState(floor,Blocks.AIR.getDefaultState());p.discard();c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_v9")
    public void deferredMovementShapesKeepNativeVolumesDeduplicateAndIncludeStepSweep(TestContext c){
        PlayerEntity p=c.createMockSurvivalPlayer();ServerWorld world=c.getWorld();Vec3d at=base(c).add(0,35,0);
        RpObjectEntity window=create(c,"npc_window");window.refreshPositionAndAngles(at.x,at.y,at.z,90,0);world.spawnEntity(window);
        p.refreshPositionAndAngles(at.x,at.y,at.z-3,0,0);Vec3d movement=new Vec3d(0,0,6);
        var empty=java.util.List.<net.minecraft.util.shape.VoxelShape>of();
        var deferred=dev.dreamwalker.bloodbornedw.architecture.RpCollisionShapes.forMovement(p,movement,empty);
        c.assertTrue(deferred.size()==1&&deferred.get(0).getBoundingBoxes().equals(window.activePhysicalBoxes()),"a deferred empty engine list receives the exact working plane rather than decorative AABB");
        var nativeMarker=net.minecraft.util.shape.VoxelShapes.cuboid(new Box(at.x-2,at.y-1,at.z-4,at.x+2,at.y,at.z+4));
        var seeded=List.of(nativeMarker,deferred.get(0));
        c.assertTrue(dev.dreamwalker.bloodbornedw.architecture.RpCollisionShapes.forMovement(p,movement,seeded)==seeded,"native shape objects/list are unchanged and existing exact RP volume is not appended twice");
        var nativeQuery=world.getEntityCollisions(p,p.getBoundingBox().stretch(movement));
        c.assertTrue(dev.dreamwalker.bloodbornedw.architecture.RpCollisionShapes.forMovement(p,movement,nativeQuery)==nativeQuery,"default World hook and movement hook share one deduplicated working volume");
        double z=p.getZ(),expectedZ=window.activePhysicalBoxes().get(0).minZ-p.getWidth()/2d;
        c.assertTrue(expectedZ>z&&expectedZ<z+movement.z,"requested movement crosses the actual authored plane");
        p.move(MovementType.SELF,movement);c.assertTrue(Math.abs(p.getZ()-expectedZ)<1e-6,"actual native movement stops its feet/body at the authored plane; expected="+expectedZ+" actual="+p.getZ());
        p.refreshPositionAndAngles(at.x,at.y,at.z-3,0,0);window.setPosition(at.x,at.y+3.875,at.z);
        c.assertTrue(dev.dreamwalker.bloodbornedw.architecture.RpCollisionShapes.append(world,p,p.getBoundingBox().stretch(movement),empty).isEmpty(),"raised working surface lies above ordinary body sweep");
        c.assertTrue(dev.dreamwalker.bloodbornedw.architecture.RpCollisionShapes.forMovement(p,movement,empty).size()==1,"step-height body sweep includes its overhead working surface");
        window.removeByBuilder();p.discard();c.complete();
    }
    private static void aim(PlayerEntity player,Vec3d target){Vec3d d=target.subtract(player.getEyePos());player.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));player.setPitch((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));}
    private static RpObjectEntity create(TestContext c,String id){EntityType<?> type=Registries.ENTITY_TYPE.getOrEmpty(new Identifier("bloodborne_rp",id)).orElseThrow(()->new AssertionError("Missing actual RP registry:"+id));Entity value=type.create(c.getWorld());if(!(value instanceof RpObjectEntity object))throw new AssertionError("Wrong RP factory:"+id);return object;}
    private static Vec3d base(TestContext c){BlockPos p=c.getAbsolutePos(new BlockPos(3,3,3));for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)c.getWorld().getChunk(p.add(x*16,0,z*16));return new Vec3d(p.getX()+.5,120,p.getZ()+.5);}
    private static RpObjectEntity use(TestContext c,ServerPlayerEntity p,String id,BlockPos clicked,Direction face,Vec3d hit,float yaw){
        Set<UUID> before=new HashSet<>();for(Entity e:c.getWorld().iterateEntities())before.add(e.getUuid());p.setPosition(hit.x,hit.y+ (face==Direction.DOWN?-1:0),hit.z-3);p.setYaw(yaw);p.getAbilities().creativeMode=true;
        ItemStack stack=Registries.ITEM.get(new Identifier("bloodborne_rp",id+"_placer")).getDefaultStack();p.setStackInHand(Hand.MAIN_HAND,stack);ActionResult result=stack.getItem().useOnBlock(new ItemUsageContext(p,Hand.MAIN_HAND,new BlockHitResult(hit,face,clicked,false)));
        c.assertTrue(result.isAccepted(),"actual ordinary item placement must succeed: "+id+" face="+face);
        List<RpObjectEntity> created=new ArrayList<>();for(Entity e:c.getWorld().iterateEntities())if(e instanceof RpObjectEntity rp&&!before.contains(e.getUuid()))created.add(rp);
        c.assertTrue(created.size()==1,"one item creates exactly one RP instance");return created.get(0);
    }
}
