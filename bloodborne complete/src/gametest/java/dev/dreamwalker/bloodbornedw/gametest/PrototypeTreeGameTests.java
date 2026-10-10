package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

/** Server technical evidence; these checks do not substitute for visual acceptance. */
public final class PrototypeTreeGameTests implements FabricGameTest {
    private static final String TEMPLATE="bloodborne_dw:tree_test",KEY="prototype_tree";
    private static final BlockPos LOCAL=new BlockPos(10,3,10);
    private static CompositeRootBlock block(){return CompositeArchitecture.kindBlock(KEY);}
    private static boolean committed(TransactionCore.Result result){return result.outcome()==TransactionCore.Outcome.COMMITTED;}
    @GameTest(templateName=TEMPLATE,tickLimit=240,batchId="prototype_tree")
    public void ordinaryItemPlacesWholeTreeAtEightYawsBothProfiles(TestContext c){ServerWorld world=c.getWorld();BlockPos root=c.getAbsolutePos(LOCAL);PlayerEntity player=c.createMockSurvivalPlayer();try{
        c.assertTrue(block().spec.pose(block().getDefaultState()).parts().size()==18,"exact18 frozen source models belong to one object");
        for(int yaw=0;yaw<8;yaw++)for(var profile:CompositeRootBlock.Profile.values()){
            clear(world,root);world.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);outside(player,root,yaw*45);
            ItemStack stack=block().art(block().getDefaultState().with(CompositeRootBlock.PROFILE,profile),new NbtCompound());stack.setCount(2);NbtCompound before=stack.getNbt().copy();
            c.assertTrue(placeItem(world,player,stack,root).isAccepted(),"ordinary item creates entire tree");BlockState state=world.getBlockState(root);Owner owner=owner(world,root);
            c.assertTrue(state.isOf(block())&&state.get(CompositeRootBlock.ROTATION)==yaw&&state.get(CompositeRootBlock.PROFILE)==profile,"eight player orientations and declared profile preserved");
            c.assertTrue(owner!=null&&stack.getCount()==1&&stack.getNbt().equals(before),"one UUID root and one item consumed without rewriting remaining art");
            c.assertTrue(ownedCells(world,owner).stream().anyMatch(p->p.getY()>=root.getY()+17),"actual sparse helpers include exact source far crown");
            c.assertTrue(world.getBlockState(root.up(10)).getCollisionShape(world,root.up(10)).isEmpty(),"upper foliage/stem billboards remain passable");
            c.assertTrue(committed(CompositeRuntime.remove(world,owner,player,false)),"whole object cleanup commits");assertAbsent(c,world,owner);
        }c.complete();
    }finally{clear(world,root);player.discard();}}
    @GameTest(templateName=TEMPLATE,tickLimit=160,batchId="prototype_tree")
    public void remoteCrownPickAndNativeBreakYieldExactlyOneTree(TestContext c){ServerWorld world=c.getWorld();BlockPos root=c.getAbsolutePos(LOCAL);PlayerEntity player=c.createMockSurvivalPlayer();try{
        clear(world,root);support(world,root);outside(player,root,0);ItemStack item=block().art(block().getDefaultState().with(CompositeRootBlock.PROFILE,CompositeRootBlock.Profile.ALT),new NbtCompound());c.assertTrue(placeItem(world,player,item,root).isAccepted(),"tree item places");Owner owner=owner(world,root);
        BlockPos remote=ownedCells(world,owner).stream().filter(p->p.getY()>root.getY()+1&&CompositeRuntime.contributions(world,p).stream().anyMatch(part->!part.shape().collision().isEmpty())).findFirst().orElseThrow();BlockState state=world.getBlockState(remote);ItemStack pick=state.getBlock().getPickStack(world,remote,state);
        c.assertTrue(pick.isOf(block().asItem())&&pick.getCount()==1&&pick.getSubNbt("BlockStateTag").getString("profile").equals("alt"),"far helper pick preserves one tree art");aimAtShape(player,world,remote);
        c.assertTrue(world.breakBlock(remote,true,player),"native helper break resolves whole owner");c.assertTrue(world.getBlockState(root).isAir(),"root removed together");assertAbsent(c,world,owner);
        List<ItemEntity> drops=world.getEntitiesByClass(ItemEntity.class,new Box(root).expand(20),e->e.getStack().isOf(block().asItem()));c.assertTrue(drops.size()==1&&drops.get(0).getStack().getCount()==1,"entire tree drops exactly one item");c.complete();
    }finally{clear(world,root);player.discard();}}
    @GameTest(templateName=TEMPLATE,tickLimit=200,batchId="prototype_tree")
    public void sourceAdjacentTreesShareCellsAndForeignChestSurvivesRemoval(TestContext c){ServerWorld world=c.getWorld();BlockPos first=c.getAbsolutePos(LOCAL),second=first.add(2,0,4);PlayerEntity player=c.createMockSurvivalPlayer();try{
        clear(world,first);support(world,first);support(world,second);outside(player,first,0);
        Set<Cell> a=absoluteCells(first,block().getDefaultState()),b=absoluteCells(second,block().getDefaultState());Cell shared=a.stream().filter(b::contains).filter(cell->!cell.equals(CompositeData.cell(first))&&!cell.equals(CompositeData.cell(second))).findFirst().orElseThrow();BlockPos foreign=CompositeData.pos(shared);
        world.setBlockState(foreign,Blocks.CHEST.getDefaultState(),Block.NOTIFY_ALL);ChestBlockEntity chest=(ChestBlockEntity)world.getBlockEntity(foreign);chest.setStack(0,new ItemStack(Items.DIAMOND,7));NbtCompound original=chest.createNbt();BlockState originalState=world.getBlockState(foreign);
        c.assertTrue(committed(CompositeRuntime.place(world,first,block().getDefaultState(),UUID.randomUUID(),player)),"first whole tree shares native chest");Owner left=owner(world,first);
        c.assertTrue(committed(CompositeRuntime.place(world,second,block().getDefaultState(),UUID.randomUUID(),player)),"source-evidenced neighboring tree soft overlap accepted");Owner right=owner(world,second);
        c.assertTrue(CompositeLedger.get(world).at(shared).size()==2,"one foreign cell concretely caches both independent owners");
        c.assertTrue(world.getBlockEntity(foreign)==chest&&chest.createNbt().equals(original),"foreign BE instance and full NBT unchanged");
        c.assertTrue(committed(CompositeRuntime.remove(world,left,player,false)),"first owner removal commits");assertAbsent(c,world,left);c.assertTrue(right.equals(owner(world,second))&&CompositeLedger.get(world).at(shared).size()==1,"neighbor and its shared contribution survive");
        c.assertTrue(world.getBlockState(foreign).equals(originalState)&&world.getBlockEntity(foreign)==chest&&chest.createNbt().equals(original),"foreign chest survives first removal");
        c.assertTrue(committed(CompositeRuntime.remove(world,right,player,false)),"second owner removal commits");assertAbsent(c,world,right);c.assertTrue(world.getBlockEntity(foreign)==chest&&chest.createNbt().equals(original),"foreign chest survives final removal");c.complete();
    }finally{clear(world,first);player.discard();}}
    @GameTest(templateName=TEMPLATE,tickLimit=240,batchId="prototype_tree")
    public void eightRotationsKeepUuidAndBeLedgerPaletteRoundtrip(TestContext c){ServerWorld world=c.getWorld();BlockPos root=c.getAbsolutePos(LOCAL);PlayerEntity player=c.createMockSurvivalPlayer();try{
        clear(world,root);support(world,root);outside(player,root,0);NbtCompound payload=new NbtCompound();payload.putString("PreservedNote","opaque tree data");c.assertTrue(committed(CompositeRuntime.place(world,root,block().getDefaultState().with(CompositeRootBlock.PROFILE,CompositeRootBlock.Profile.ALT),UUID.randomUUID(),player,payload)),"payload tree places");Owner owner=owner(world,root);
        for(int yaw=1;yaw<=8;yaw++){BlockState next=world.getBlockState(root).with(CompositeRootBlock.ROTATION,yaw%8);c.assertTrue(committed(CompositeRuntime.transition(world,owner,next,player)),"45 degree transition commits");c.assertTrue(owner.equals(owner(world,root)),"owner UUID stable");}
        BlockState state=world.getBlockState(root);NbtCompound palette=NbtHelper.fromBlockState(state);c.assertTrue(NbtHelper.toBlockState(world.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),palette).equals(state),"real Minecraft palette roundtrips rotation/art/profile");
        BlockEntity entity=world.getBlockEntity(root);NbtCompound saved=entity.createNbtWithIdentifyingData();BlockEntity restored=BlockEntity.createFromNbt(root,state,saved.copy());c.assertTrue(restored instanceof CompositeBlockEntity&&saved.equals(restored.createNbtWithIdentifyingData()),"registered root BE codec roundtrips UUID and cached masks");
        NbtCompound ledger=CompositeLedger.get(world).writeNbt(new NbtCompound());c.assertTrue(CompositeLedger.read(ledger.copy()).writeNbt(new NbtCompound()).equals(ledger),"PersistentState multi-owner codec exact roundtrip");
        ItemStack picked=CompositeRuntime.pick(world,owner);c.assertTrue(picked.getSubNbt("BlockStateTag").getString("profile").equals("alt")&&picked.getSubNbt("CompositePayload")==null&&((CompositeBlockEntity)world.getBlockEntity(root)).payload().getString("PreservedNote").equals("opaque tree data"),"pick preserves artistic profile, installed opaque payload and omits installation privileges");c.assertTrue(committed(CompositeRuntime.remove(world,owner,player,false)),"remove saved tree");c.assertTrue(placeItem(world,player,picked,root).isAccepted()&&!owner.equals(owner(world,root)),"picked placement allocates fresh UUID");c.complete();
    }finally{clear(world,root);player.discard();}}
    @GameTest(templateName=TEMPLATE,tickLimit=160,batchId="prototype_tree")
    public void failedWriteRollsBackAllCellsAndForeignNbt(TestContext c){ServerWorld world=c.getWorld();BlockPos root=c.getAbsolutePos(LOCAL);try{
        clear(world,root);support(world,root);ObjectInstance object=CompositeRuntime.instance(root,block().getDefaultState(),UUID.randomUUID(),new NbtCompound());BlockPos foreign=object.cells().keySet().stream().filter(cell->cell.y()>10).map(cell->root.add(cell.x(),cell.y(),cell.z())).findFirst().orElseThrow();world.setBlockState(foreign,Blocks.CHEST.getDefaultState(),Block.NOTIFY_ALL);ChestBlockEntity chest=(ChestBlockEntity)world.getBlockEntity(foreign);chest.setStack(2,new ItemStack(Items.EMERALD,9));NbtCompound chestNbt=chest.createNbt();
        Map<Cell,BlockState> before=new HashMap<>();for(Cell offset:object.cells().keySet()){Cell absolute=object.owner().root().add(offset);before.put(absolute,world.getBlockState(CompositeData.pos(absolute)));}
        FabricCompositeWorld failing=new FabricCompositeWorld(world,Map.of(object.owner(),object)){int writes;boolean failed;@Override public boolean writeSilently(Cell cell,CellSnapshot next){boolean result=super.writeSilently(cell,next);if(!failed&&++writes==4){failed=true;return false;}return result;}};
        var preparation=CompositeRuntime.core().placement(failing,object,List.of());c.assertTrue(preparation.accepted(),"valid tree preflight accepted before injected real world write failure");var result=CompositeRuntime.execute(failing,preparation);c.assertTrue(result.outcome()==TransactionCore.Outcome.ROLLED_BACK,"adapter rolls back partially changed Minecraft world");
        for(var entry:before.entrySet())c.assertTrue(world.getBlockState(CompositeData.pos(entry.getKey())).equals(entry.getValue()),"old cell state restored");c.assertTrue(world.getBlockEntity(foreign)==chest&&chest.createNbt().equals(chestNbt),"foreign original BE instance/NBT preserved through rollback");assertAbsent(c,world,object.owner());c.complete();
    }finally{clear(world,root);}}
    @GameTest(templateName=TEMPLATE,tickLimit=180,batchId="prototype_tree")
    public void unsupportedRootConflictAndEntityObstructionAreAtomic(TestContext c){ServerWorld world=c.getWorld();BlockPos root=c.getAbsolutePos(LOCAL);try{
        clear(world,root);c.assertTrue(!committed(CompositeRuntime.place(world,root,block().getDefaultState(),UUID.randomUUID(),null)),"unsupported new build rejected");support(world,root);world.setBlockState(root,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);c.assertTrue(!committed(CompositeRuntime.place(world,root,block().getDefaultState(),UUID.randomUUID(),null))&&world.getBlockState(root).isOf(Blocks.STONE),"essential root conflict retains foreign state");world.setBlockState(root,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        ArmorStandEntity entity=new ArmorStandEntity(world,root.getX()+.5,root.getY()+.1,root.getZ()+.5);world.spawnEntity(entity);c.assertTrue(!committed(CompositeRuntime.place(world,root,block().getDefaultState(),UUID.randomUUID(),null))&&world.getBlockState(root).isAir(),"actual collidable entity prevents stem placement atomically");entity.discard();c.complete();
    }finally{clear(world,root);}}
    @GameTest(templateName=TEMPLATE,tickLimit=180,batchId="prototype_tree")
    public void measuredSourceWestGapRemainsOpenAndStemPolicyIsExplicit(TestContext c){ServerWorld world=c.getWorld();BlockPos root=c.getAbsolutePos(LOCAL);PlayerEntity player=c.createMockSurvivalPlayer();try{
        clear(world,root);support(world,root);c.assertTrue(committed(CompositeRuntime.place(world,root,block().getDefaultState(),UUID.randomUUID(),null)),"whole proposed tree installs");
        player.refreshPositionAndAngles(root.getX()-1.5,root.getY()+10.05,root.getZ()-1.5,0,0);double start=player.getZ();player.move(MovementType.SELF,new Vec3d(0,0,3));c.assertTrue(player.getZ()-start>2.99,"measured original west foliagegap3m remains passable");
        player.refreshPositionAndAngles(root.getX()+.5,root.getY()+10.05,root.getZ()-1.5,0,0);start=player.getZ();player.move(MovementType.SELF,new Vec3d(0,0,3));c.assertTrue(player.getZ()-start>2.99,"proposed uppercentralbillboard is passable; originalwoolstop1.2m change requires useracceptance");
        player.refreshPositionAndAngles(root.getX()+.5,root.getY()+1.05,root.getZ()-1.5,0,0);start=player.getZ();player.move(MovementType.SELF,new Vec3d(0,0,3));c.assertTrue(player.getZ()-start<2,"actual lowerstem blocks movement without solidfoliage AABB");c.complete();
    }finally{clear(world,root);player.discard();}}
    @GameTest(templateName=TEMPLATE,tickLimit=160,batchId="prototype_tree")
    public void manyFractionalStripsRemainBoundedAndDoNotFillDiagonalGaps(TestContext c){List<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box> strips=new ArrayList<>();for(int i=0;i<1024;i++){double x0=i/1024.0,x1=(i+1)/1024.0;strips.add(new dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box(x0,.100000003+i/1e7,Math.max(0,x0-.002),x1,.9-i/1e7,Math.min(1,x1+.002)));}
        var shape=CompositeRuntime.shape(strips);c.assertTrue(shape==CompositeRuntime.shape(List.copyOf(strips)),"identicalfractionalstripgeometry reuses immutable cached shape");List<Box> boxes=shape.getBoundingBoxes();c.assertTrue(boxes.size()<=4096&&!boxes.isEmpty(),"first shape construction hasbounded16cubedoccupancy");
        c.assertTrue(boxes.stream().noneMatch(box->box.contains(.1,.5,.9)),"thin diagonal does not become filled bounding square");c.assertTrue(boxes.stream().anyMatch(box->box.contains(.5,.5,.5)),"authored diagonal contact preserved");
        for(Box box:boxes)for(double value:new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ})c.assertTrue(Math.abs(value*16-Math.rint(value*16))<1e-9,"shape axesbounded1/16block lattice");c.complete();}
    @GameTest(templateName=TEMPLATE,tickLimit=160,batchId="prototype_tree")
    public void actualFabricSurvivalBreakHookResolvesTrustedNativeHelper(TestContext c){ServerWorld world=c.getWorld();BlockPos root=c.getAbsolutePos(LOCAL);PlayerEntity player=c.createMockSurvivalPlayer();try{
        clear(world,root);support(world,root);c.assertTrue(committed(CompositeRuntime.place(world,root,block().getDefaultState(),UUID.randomUUID(),null)),"native helper tree installs");Owner owner=owner(world,root);BlockPos helper=ownedCells(world,owner).stream().filter(p->p.getY()>root.getY()+10&&world.getBlockState(p).isOf(CompositeArchitecture.CELL)).findFirst().orElseThrow();outside(player,root,0);
        c.assertTrue(CompositeRuntime.target(world,helper,player)==null,"fixture intentionally has actor head away from alreadyauthorized native helper event");BlockState state=world.getBlockState(helper);boolean allowVanilla=PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world,player,helper,state,world.getBlockEntity(helper));
        c.assertTrue(!allowVanilla&&world.getBlockState(root).isAir(),"actual Fabric survivalhook removes whole soleowner and cancels duplicate vanillabreak");assertAbsent(c,world,owner);List<ItemEntity> drops=world.getEntitiesByClass(ItemEntity.class,new Box(root).expand(20),entity->entity.getStack().isOf(block().asItem()));c.assertTrue(drops.size()==1&&drops.get(0).getStack().getCount()==1,"one artitem from actual beforebreakhook");c.complete();
    }finally{clear(world,root);player.discard();}}
    @GameTest(templateName=TEMPLATE,tickLimit=160,batchId="prototype_tree")
    public void nearerForeignChestKeepsNativeInteractionAndBreakTarget(TestContext c){ServerWorld world=c.getWorld();BlockPos root=c.getAbsolutePos(LOCAL),foreign=root.east();PlayerEntity player=c.createMockSurvivalPlayer();try{
        clear(world,root);support(world,root);world.setBlockState(foreign,Blocks.CHEST.getDefaultState(),Block.NOTIFY_ALL);ChestBlockEntity chest=(ChestBlockEntity)world.getBlockEntity(foreign);chest.setStack(0,new ItemStack(Items.DIAMOND,3));NbtCompound before=chest.createNbt();c.assertTrue(committed(CompositeRuntime.place(world,root,block().getDefaultState(),UUID.randomUUID(),null)),"tree shares foreign carrier without claiming it");Owner owner=owner(world,root);
        c.assertTrue(CompositeRuntime.contributions(world,foreign).stream().anyMatch(entry->entry.owner().equals(owner)),"foreign chest concretely has tree overlay");player.refreshPositionAndAngles(foreign.getX()+.5,foreign.getY()+.5-(player.getEyeY()-player.getY()),foreign.getZ()-3,0,0);player.setHeadYaw(0);
        c.assertTrue(CompositeRuntime.target(world,foreign,player)==null,"nearer native chest outline wins over decorative tree panel");c.assertTrue(PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world,player,foreign,world.getBlockState(foreign),chest),"survivalhook allows native foreign break instead of stealing object");c.assertTrue(owner.equals(owner(world,root))&&world.getBlockEntity(foreign)==chest&&chest.createNbt().equals(before),"native interaction authorization leaves root and original foreign BE untouched");c.complete();
    }finally{clear(world,root);player.discard();}}
    @GameTest(templateName=TEMPLATE,tickLimit=200,batchId="prototype_tree")
    public void offhandToolsDoNotEditWhileTechnicalSharedSelectionIsReadOnly(TestContext c){ServerWorld world=c.getWorld();BlockPos first=c.getAbsolutePos(LOCAL),second=first.add(2,0,4);PlayerEntity player=c.createMockSurvivalPlayer();try{
        clear(world,first);support(world,first);support(world,second);outside(player,first,0);c.assertTrue(committed(CompositeRuntime.place(world,first,block().getDefaultState(),UUID.randomUUID(),null))&&committed(CompositeRuntime.place(world,second,block().getDefaultState(),UUID.randomUUID(),null)),"source-adjacent independent trees install before shared target selection");
        player.setStackInHand(Hand.MAIN_HAND,CompositeArchitecture.BUILDER.getDefaultStack());Owner a=owner(world,first),b=owner(world,second);BlockPos hit=findSharedRay(world,player,Set.of(a,b));c.assertTrue(hit!=null,"90009 ray intersects both owner selection contributions in one shared helper cell");
        Map<BlockPos,BlockState> states=new HashMap<>();Map<BlockPos,NbtCompound> entities=new HashMap<>();for(Cell cell:CompositeLedger.get(world).cells()){BlockPos pos=CompositeData.pos(cell);states.put(pos,world.getBlockState(pos));if(world.getBlockEntity(pos)!=null)entities.put(pos,world.getBlockEntity(pos).createNbtWithIdentifyingData());}NbtCompound ledger=CompositeLedger.get(world).writeNbt(new NbtCompound());
        player.getAbilities().creativeMode=true;
        for(Item tool:List.of(dev.dreamwalker.bloodbornedw.architecture.PrototypeArchitecture.BUILDER_TOOL,CompositeArchitecture.BUILDER)){
            CompositeRuntime.clearSelection(player.getUuid());Owner initial=CompositeRuntime.target(world,hit,player);player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);ItemStack held=new ItemStack(tool);player.setStackInHand(Hand.OFF_HAND,held);ItemUsageContext context=new ItemUsageContext(world,player,Hand.OFF_HAND,held,new BlockHitResult(Vec3d.ofCenter(hit),Direction.NORTH,hit,false));
            c.assertTrue(!tool.useOnBlock(context).isAccepted()&&initial.equals(CompositeRuntime.target(world,hit,player)),"offhand tool consumes no edit or accidental normal interaction");Owner next=CompositeRuntime.cycleTarget(world,hit,player);c.assertTrue(!initial.equals(next)&&Set.of(a,b).contains(next),"technical selection resolves the other current ray owner");c.assertTrue(initial.equals(CompositeRuntime.cycleTarget(world,hit,player)),"technical second cycle returns the initial shared owner");
            for(var entry:states.entrySet())c.assertTrue(world.getBlockState(entry.getKey()).equals(entry.getValue()),"selection leaves art/orientation/open/profile and helper carrier state unchanged");for(var entry:entities.entrySet())c.assertTrue(world.getBlockEntity(entry.getKey()).createNbtWithIdentifyingData().equals(entry.getValue()),"selection does not rewrite root/helper UUID or cached masks");c.assertTrue(CompositeLedger.get(world).writeNbt(new NbtCompound()).equals(ledger),"selection does not change shared ownership or collision");
        }
        outside(player,first,0);c.assertTrue(CompositeRuntime.cycleTarget(world,hit,player)==null,"a player looking away cannot request an arbitrary cached owner");c.complete();
    }finally{CompositeRuntime.clearSelection(player.getUuid());clear(world,first);player.discard();}}
    private static BlockPos findSharedRay(ServerWorld world,PlayerEntity player,Set<Owner> owners){for(Cell cell:CompositeLedger.get(world).cells()){
        List<CompositeData.Contribution> entries=CompositeLedger.get(world).at(cell);if(!world.getBlockState(CompositeData.pos(cell)).isOf(CompositeArchitecture.CELL)||!entries.stream().map(CompositeData.Contribution::owner).collect(java.util.stream.Collectors.toSet()).containsAll(owners))continue;
        for(var entry:entries)for(var box:entry.shape().selection())for(float yaw:new float[]{0,90,180,270}){Vec3d center=Vec3d.of(CompositeData.pos(cell)).add((box.minX()+box.maxX())/2,(box.minY()+box.maxY())/2,(box.minZ()+box.maxZ())/2);Vec3d start=center.subtract(Vec3d.fromPolar(0,yaw).multiply(3));player.refreshPositionAndAngles(start.x,start.y-(player.getEyeY()-player.getY()),start.z,yaw,0);player.setHeadYaw(yaw);if(new HashSet<>(CompositeRuntime.targets(world,CompositeData.pos(cell),player)).containsAll(owners))return CompositeData.pos(cell);}
    }return null;}
    @GameTest(templateName=TEMPLATE,tickLimit=160,batchId="prototype_tree")
    public void sourceTechnicalLightIsForeignAndSparseOverlayRetainsEmission(TestContext c){ServerWorld world=c.getWorld();BlockPos root=c.getAbsolutePos(LOCAL),helper=root.up(3),passable=root.up(10);try{
        clear(world,root);support(world,root);BlockState source=dev.dreamwalker.bloodbornedw.architecture.compat.SourceTechnicalLight.SOURCE_LIGHT.getDefaultState();world.setBlockState(root,source,Block.NOTIFY_ALL);
        c.assertTrue(source.getLuminance()==9&&source.getCollisionShape(world,root).isEmpty()&&source.getOutlineShape(world,root).isEmpty(),"registered compatibility light preserves original invisible empty shapes and emission9");
        c.assertTrue(new FabricCompositeWorld(world,Map.of()).read(CompositeData.cell(root)).kind()==CellSnapshot.Kind.FOREIGN,"custom source air-like block is foreign data rather than a free vanilla air cell");
        c.assertTrue(CompositeRuntime.place(world,root,block().getDefaultState(),UUID.randomUUID(),null).outcome()==TransactionCore.Outcome.REJECTED&&world.getBlockState(root).equals(source),"essential tree root cannot consume existing source light");
        world.setBlockState(root,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(helper,source,Block.NOTIFY_ALL);world.setBlockState(passable,source,Block.NOTIFY_ALL);c.assertTrue(committed(CompositeRuntime.place(world,root,block().getDefaultState(),UUID.randomUUID(),null)),"tree service geometry softly overlaps registered source light");Owner owner=owner(world,root);
        c.assertTrue(world.getBlockState(helper).equals(source)&&world.getBlockState(helper).getLuminance()==9&&world.getBlockEntity(helper)==null,"foreign light native state/emission remains intact without replacing it by a helper BE");
        var contribution=CompositeRuntime.contributions(world,helper).stream().filter(entry->entry.owner().equals(owner)).findFirst().orElseThrow();var collision=world.getBlockState(helper).getCollisionShape(world,helper);
        c.assertTrue(!net.minecraft.util.shape.VoxelShapes.matchesAnywhere(collision,CompositeRuntime.shape(contribution.shape().collision()),net.minecraft.util.function.BooleanBiFunction.NOT_SAME),"overlay adds only authored narrow stem collision, with no hidden source light cube: actual="+collision.getBoundingBoxes()+" expected="+CompositeRuntime.shape(contribution.shape().collision()).getBoundingBoxes()+" contextual="+world.getBlockState(helper).getCollisionShape(world,helper,ShapeContext.absent()).getBoundingBoxes()+" overlay="+CompositeRuntime.overlay(world,helper,true).getBoundingBoxes()+" baseShape="+CompositeRuntime.baseShape()+" owner="+owner+" liveRoot="+owner(world,root));
        c.assertTrue(!net.minecraft.util.shape.VoxelShapes.matchesAnywhere(collision,world.getBlockState(helper).getCollisionShape(world,helper,ShapeContext.absent()),net.minecraft.util.function.BooleanBiFunction.NOT_SAME),"cached and contextual native collision queries both include the same owner overlay");
        var selection=world.getBlockState(helper).getOutlineShape(world,helper);
        c.assertTrue(!selection.isEmpty()&&!net.minecraft.util.shape.VoxelShapes.matchesAnywhere(selection,CompositeRuntime.shape(contribution.shape().collision()),net.minecraft.util.function.BooleanBiFunction.NOT_SAME)&&!net.minecraft.util.shape.VoxelShapes.matchesAnywhere(selection,world.getBlockState(helper).getOutlineShape(world,helper,ShapeContext.absent()),net.minecraft.util.function.BooleanBiFunction.NOT_SAME),"both native outline overloads expose exactly the actual physical owner parts over an empty foreign light");
        c.assertTrue(world.getBlockState(passable).getCollisionShape(world,passable).isEmpty()&&world.getBlockState(passable).getCollisionShape(world,passable,ShapeContext.absent()).isEmpty()&&world.getBlockState(passable).getOutlineShape(world,passable).isEmpty(),"decorative upper foliage has no ordinary interaction ghost and remains passable over a native light whose collision delegates to outline");
        c.assertTrue(committed(CompositeRuntime.remove(world,owner,null,false))&&world.getBlockState(helper).equals(source)&&world.getBlockState(helper).getLuminance()==9,"whole tree cleanup preserves technical light9");
        c.assertTrue(world.getBlockState(helper).getCollisionShape(world,helper).isEmpty()&&world.getBlockState(helper).getOutlineShape(world,helper).isEmpty()&&CompositeLedger.get(world).at(CompositeData.cell(helper)).isEmpty(),"removal clears only owner overlay and restores exact empty source collision/selection");
        c.assertTrue(world.getBlockState(passable).equals(source)&&world.getBlockState(passable).getLuminance()==9&&world.getBlockState(passable).getCollisionShape(world,passable).isEmpty()&&world.getBlockState(passable).getOutlineShape(world,passable).isEmpty(),"selection-only overlap cleanup also retains native light with both empty shapes");c.complete();
    }finally{clear(world,root);}}
    @GameTest(templateName=TEMPLATE,tickLimit=160,batchId="prototype_tree")
    public void legacyTreeNormalizationIsDeferredAndKeepsAssemblyOwnerAndPayload(TestContext c){
        ServerWorld world=c.getWorld();BlockPos root=c.getAbsolutePos(LOCAL);try{
            clear(world,root);support(world,root);NbtCompound payload=new NbtCompound();payload.putString("LegacyOpaque","preserve exact user field");
            c.assertTrue(committed(CompositeRuntime.place(world,root,block().getDefaultState(),UUID.randomUUID(),null,payload)),"install complete accepted tree before simulating old palette");
            Owner original=owner(world,root);Set<BlockPos> cells=new HashSet<>(ownedCells(world,original));
            world.setBlockState(root,world.getBlockState(root).with(CompositeRootBlock.VARIANT,1),Block.NOTIFY_LISTENERS);
            CompositeRuntime.chunkLoaded(world,new net.minecraft.util.math.ChunkPos(root));
            c.assertTrue(world.getBlockState(root).get(CompositeRootBlock.VARIANT)==1,"CHUNK_LOAD only queues normalization; no synchronous world/root inspection or mutation");
            CompositeRuntime.drain(world);
            c.assertTrue(world.getBlockState(root).get(CompositeRootBlock.VARIANT)==0&&original.equals(owner(world,root)),"next world tick normalizes the legacy variant with the same exact instance UUID");
            c.assertTrue(cells.equals(new HashSet<>(ownedCells(world,original)))&&((CompositeBlockEntity)world.getBlockEntity(root)).payload().getString("LegacyOpaque").equals("preserve exact user field"),"complete accepted assembly and opaque original payload remain intact");
            c.complete();
        }finally{clear(world,root);}
    }
    private static Set<Cell> absoluteCells(BlockPos root,BlockState state){Set<Cell> result=new TreeSet<>();block().spec.footprint(state,0).keySet().forEach(offset->result.add(CompositeData.cell(root).add(offset)));return result;}
    private static List<BlockPos> ownedCells(ServerWorld world,Owner owner){return CompositeLedger.get(world).cells().stream().filter(cell->CompositeLedger.get(world).at(cell).stream().anyMatch(entry->entry.owner().equals(owner))).map(CompositeData::pos).toList();}
    private static Owner owner(ServerWorld world,BlockPos root){return world.getBlockEntity(root)instanceof CompositeBlockEntity entity?entity.resident():null;}
    private static void assertAbsent(TestContext c,ServerWorld world,Owner owner){c.assertTrue(ownedCells(world,owner).isEmpty(),"no stale cell ledger contributions");}
    private static void support(ServerWorld world,BlockPos root){world.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);}
    private static ActionResult placeItem(ServerWorld world,PlayerEntity player,ItemStack stack,BlockPos root){player.setStackInHand(Hand.MAIN_HAND,stack);return CompositeArchitecture.kindItem(KEY).useOnBlock(new ItemUsageContext(world,player,Hand.MAIN_HAND,stack,new BlockHitResult(Vec3d.ofCenter(root.down()).add(0,.5,0),Direction.UP,root.down(),false)));}
    private static void outside(PlayerEntity player,BlockPos root,float yaw){player.refreshPositionAndAngles(root.getX()+8.5,root.getY()+.1,root.getZ()+8.5,yaw,0);player.setHeadYaw(yaw);}
    private static void aimAtShape(PlayerEntity player,ServerWorld world,BlockPos pos){Box box=world.getBlockState(pos).getOutlineShape(world,pos).getBoundingBoxes().get(0);Vec3d target=Vec3d.of(pos).add((box.minX+box.maxX)/2,(box.minY+box.maxY)/2,(box.minZ+box.maxZ)/2);player.refreshPositionAndAngles(target.x,target.y-(player.getEyeY()-player.getY()),target.z-3,0,0);player.setHeadYaw(0);if(CompositeRuntime.target(world,pos,player)==null)throw new AssertionError("native break ray resolves owner: "+CompositeRuntime.debugTarget(world,pos,player));}
    private static void clear(ServerWorld world,BlockPos root){for(BlockPos pos:BlockPos.iterate(root.add(-8,-2,-8),root.add(8,20,8)))world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);CompositeRuntime.drain(world);for(ItemEntity item:world.getEntitiesByClass(ItemEntity.class,new Box(root).expand(21),entity->true))item.discard();}
}
