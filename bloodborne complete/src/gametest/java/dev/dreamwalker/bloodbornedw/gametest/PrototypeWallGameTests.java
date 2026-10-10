package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallArchitecture;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallItem;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.WallShape;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.function.BooleanBiFunction;

/** Real BlockItem placement and native WallBlock neighbor transitions; no visual acceptance claim. */
public final class PrototypeWallGameTests implements FabricGameTest {
    private static final BlockPos ROOT=new BlockPos(3,3,3);
    private static final Direction[] ORDER={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
    private static PrototypeWallBlock wall(){if(PrototypeWallArchitecture.WALL==null)throw new AssertionError("Wall hook was not initialized");return PrototypeWallArchitecture.WALL;}
    private static BlockState form(int yaw,int material,PrototypeWallBlock.Course course,PrototypeWallBlock.Profile profile){
        WallShape side=course==PrototypeWallBlock.Course.TALL?WallShape.TALL:WallShape.LOW;
        return wall().getDefaultState().with(PrototypeWallBlock.CONNECTIONS,PrototypeWallBlock.Connections.MANUAL).with(PrototypeWallBlock.NORTH,side).with(PrototypeWallBlock.SOUTH,side)
            .with(PrototypeWallBlock.POST,false).with(PrototypeWallBlock.ROTATION,yaw).with(PrototypeWallBlock.MATERIAL,material).with(PrototypeWallBlock.PROFILE,profile);
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="prototype_wall")
    public void ordinaryItemPlacesEightYawOnTopSlabBesideForeignDecor(TestContext context){
        ServerWorld world=context.getWorld();BlockPos pos=context.getAbsolutePos(ROOT);PlayerEntity player=context.createMockSurvivalPlayer();
        try{for(int yaw=0;yaw<8;yaw++){
            clear(world,pos);outside(context,player,(yaw-4)*45F);BlockState support=Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE,SlabType.TOP);world.setBlockState(pos.down(),support,Block.NOTIFY_ALL);world.setBlockState(pos.west(),Blocks.GOLD_BLOCK.getDefaultState(),Block.NOTIFY_ALL);
            ItemStack stack=new ItemStack(PrototypeWallArchitecture.ITEM,2);context.assertTrue(use(PrototypeWallArchitecture.ITEM,world,player,stack,pos.down(),Direction.UP).isAccepted(),"real ordinary item places yaw "+yaw+" on partial support");
            BlockState placed=world.getBlockState(pos);context.assertTrue(placed.isOf(wall())&&placed.get(PrototypeWallBlock.ROTATION)==(((int)Math.floor(player.getYaw()/90+0.5)+2)*2&7),"player yaw selects only native cardinal poses");
            context.assertTrue(stack.getCount()==1&&stack.getNbt()==null,"placement consumes one item without changing remaining unselected stack");
            context.assertTrue(world.getBlockState(pos.down()).equals(support)&&world.getBlockState(pos.west()).isOf(Blocks.GOLD_BLOCK)&&placed.canPlaceAt(world,pos),"actual partial support and foreign decor are preserved");
            if((yaw&1)==0)context.assertTrue(wall().nativeState(placed).get(PrototypeWallBlock.WEST)!=WallShape.NONE,"AUTO cardinal yaw connects actual full-face neighbor in world direction");
        }context.complete();}finally{clear(world,pos);player.discard();}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="prototype_wall")
    public void actualItemSingleLineCornerTAndCrossMatchNativeWall(TestContext context){
        ServerWorld world=context.getWorld();BlockPos pos=context.getAbsolutePos(ROOT);PlayerEntity player=context.createMockSurvivalPlayer();outside(context,player,-180);
        try{context.assertTrue(wall().getDefaultState().isIn(BlockTags.WALLS),"independent wall is in additive vanilla WALLS tag");
            for(int mask:List.of(0,5,3,7,15)){
                clear(world,pos);floor(world,pos);for(int d=0;d<4;d++)if((mask&(1<<d))!=0){BlockPos neighbor=pos.offset(ORDER[d]);context.assertTrue(use(PrototypeWallArchitecture.ITEM,world,player,new ItemStack(PrototypeWallArchitecture.ITEM),neighbor.down(),Direction.UP).isAccepted(),"ordinary neighbor item places for pattern "+mask);}
                ItemStack stack=wall().artisticStack(wall().getDefaultState().with(PrototypeWallBlock.MATERIAL,6).with(PrototypeWallBlock.PROFILE,PrototypeWallBlock.Profile.ALT));stack.setCount(2);
                context.assertTrue(use(PrototypeWallArchitecture.ITEM,world,player,stack,pos.down(),Direction.UP).isAccepted(),"single primary item produces native topology pattern "+mask);BlockState actual=world.getBlockState(pos);assertNative(context,actual,nativeOracle(world,pos,player),"pattern "+mask);
                context.assertTrue(actual.get(PrototypeWallBlock.MATERIAL)==6&&actual.get(PrototypeWallBlock.PROFILE)==PrototypeWallBlock.Profile.ALT&&stack.getCount()==1,"automatic connection preserves picked art identity");
                context.assertTrue(actual.get(PrototypeWallBlock.POST)==(mask==0||mask==3||mask==7),"central post follows native single/line/corner/T/cross rule without an above block");
            }context.complete();
        }finally{clear(world,pos);player.discard();}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="prototype_wall")
    public void neighborFullWallAboveAndMixedHeightsUpdateWithoutTool(TestContext context){
        ServerWorld world=context.getWorld();BlockPos pos=context.getAbsolutePos(ROOT);PlayerEntity player=context.createMockSurvivalPlayer();outside(context,player,-180);
        try{
            clear(world,pos);floor(world,pos);ItemStack ordinary=wall().artisticStack(wall().getDefaultState().with(PrototypeWallBlock.MATERIAL,7).with(PrototypeWallBlock.PROFILE,PrototypeWallBlock.Profile.ALT));context.assertTrue(use(PrototypeWallArchitecture.ITEM,world,player,ordinary,pos.down(),Direction.UP).isAccepted(),"ordinary player places automatic wall without tool");
            world.setBlockState(pos.north(),wall().getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(pos.south(),wall().getDefaultState(),Block.NOTIFY_ALL);assertNative(context,world.getBlockState(pos),nativeOracle(world,pos,player),"own line added");context.assertTrue(!world.getBlockState(pos).get(PrototypeWallBlock.POST),"straight LOW line hides post");
            world.setBlockState(pos.east(),Blocks.POLISHED_DEEPSLATE_WALL.getDefaultState(),Block.NOTIFY_ALL);context.assertTrue(world.getBlockState(pos.east()).get(net.minecraft.block.WallBlock.WEST_SHAPE)!=WallShape.NONE,"vanilla wall connects back to independent wall");assertNative(context,world.getBlockState(pos),nativeOracle(world,pos,player),"vanilla wall added");
            world.setBlockState(pos.west(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);assertNative(context,world.getBlockState(pos),nativeOracle(world,pos,player),"full solid face added");context.assertTrue(world.getBlockState(pos).get(PrototypeWallBlock.WEST)!=WallShape.NONE,"full block connects automatically");
            // Native TALL probes run to9/16, not merely to the center8/16.
            // A half-depth TOP stair cannot cover any whole probe. A genuine
            // north-connected native wall above covers NORTH while other probes
            // remain uncovered; place that wall with its actual ordinary item.
            world.setBlockState(pos.up().north(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            context.assertTrue(use(Blocks.POLISHED_DEEPSLATE_WALL.asItem(),world,player,new ItemStack(Blocks.POLISHED_DEEPSLATE_WALL),pos,Direction.UP).isAccepted(),"actual native wall item provides partial upper coverage");
            BlockState mixed=world.getBlockState(pos);assertNative(context,mixed,nativeOracle(world,pos,player),"partial north wall above");boolean low=false,tall=false;for(Direction direction:ORDER){low|=mixed.get(PrototypeWallBlock.property(direction))==WallShape.LOW;tall|=mixed.get(PrototypeWallBlock.property(direction))==WallShape.TALL;}context.assertTrue(low&&tall,"partial coverage above makes independent LOW and TALL arms in the same state: lower="+mixed+" upper="+world.getBlockState(pos.up()));
            world.setBlockState(pos.up(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);BlockState covered=world.getBlockState(pos);assertNative(context,covered,nativeOracle(world,pos,player),"full block above");for(Direction direction:ORDER)context.assertTrue(covered.get(PrototypeWallBlock.property(direction))==WallShape.TALL,"full coverage raises each connected side independently");
            world.setBlockState(pos.up(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(pos.east(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(pos.west(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);BlockState line=world.getBlockState(pos);assertNative(context,line,nativeOracle(world,pos,player),"removing neighbors and coverage");context.assertTrue(!line.get(PrototypeWallBlock.POST)&&line.get(PrototypeWallBlock.EAST)==WallShape.NONE&&line.get(PrototypeWallBlock.WEST)==WallShape.NONE,"automatic removals restore straight LOW line");
            context.assertTrue(use(PrototypeWallArchitecture.ITEM,world,player,new ItemStack(PrototypeWallArchitecture.ITEM),pos,Direction.UP).isAccepted()&&world.getBlockState(pos.up()).isOf(wall()),"actual wall item places upper post on narrow lower straight wall");context.assertTrue(world.getBlockState(pos).get(PrototypeWallBlock.POST),"own upper WallBlock post keeps lower post");assertNative(context,world.getBlockState(pos),nativeOracle(world,pos,player),"own wall above");
            BlockState finalState=world.getBlockState(pos);context.assertTrue(PrototypeWallBlock.material(finalState)==6&&finalState.get(PrototypeWallBlock.PROFILE)==PrototypeWallBlock.Profile.ALT,"all native transitions preserve canonical90002 material type after hidden alias placement and profile");context.complete();
        }finally{clear(world,pos);player.discard();}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="prototype_wall")
    public void builderRightsAndEightCachedSimplePhysicalPoses(TestContext context){
        ServerWorld world=context.getWorld();BlockPos pos=context.getAbsolutePos(ROOT);PlayerEntity player=context.createMockSurvivalPlayer();outside(context,player,0);
        try{
            clear(world,pos);floor(world,pos);BlockState initial=form(0,4,PrototypeWallBlock.Course.LOW,PrototypeWallBlock.Profile.ALT);world.setBlockState(pos,initial,Block.NOTIFY_ALL);ItemStack tool=new ItemStack(PrototypeWallArchitecture.TOOL);tool.getOrCreateNbt().putString("marker","preserved");NbtCompound tag=tool.getNbt().copy();
            context.assertTrue(!use(PrototypeWallArchitecture.TOOL,world,player,tool,pos,Direction.UP).isAccepted()&&world.getBlockState(pos).equals(initial),"tool possession cannot grant ordinary survival player art editing rights");player.getAbilities().creativeMode=true;
            for(int turn=1;turn<=8;turn++){
                context.assertTrue(use(PrototypeWallArchitecture.TOOL,world,player,tool,pos,Direction.UP).isAccepted(),"authorized building tool step succeeds "+turn);BlockState state=world.getBlockState(pos);context.assertTrue(state.equals(initial.with(PrototypeWallBlock.ROTATION,turn&7)),"45 degree turn preserves selected sides, material, profile and registry");
                VoxelShape collision=state.getCollisionShape(world,pos),outline=state.getOutlineShape(world,pos,ShapeContext.absent());Box bounds=collision.getBoundingBox();context.assertTrue(bounds.minX>=0&&bounds.minZ>=0&&bounds.maxX<=1&&bounds.maxZ<=1&&bounds.maxY==1.5,"native wall-height physical footprint stays within root X/Z");
                context.assertTrue(collision.getBoundingBoxes().size()<=5&&outline.getBoundingBoxes().size()<=5,"simple collision budget and ordinary actual physical selection parts");context.assertTrue(collision==state.getCollisionShape(world,pos)&&outline==state.getOutlineShape(world,pos,ShapeContext.absent()),"query reuses cached native shape instances");
                context.assertTrue(outline.raycast(Vec3d.of(pos).add(-1,.4,.5),Vec3d.of(pos).add(2,.4,.5),pos)!=null,"whole wall remains easy to select");context.assertTrue(VoxelShapes.calculateMaxOffset(Direction.Axis.X,new Box(-.3,.2,.4,-.1,.7,.6),List.of(collision),1)<1,"simple collider blocks actual lateral movement");
                if((turn&1)!=0)context.assertTrue(collision.getBoundingBoxes().size()==1,"diagonal uses one authorized coarse collider rather than32strips");
            }
            player.setSneaking(true);context.assertTrue(use(PrototypeWallArchitecture.TOOL,world,player,tool,pos,Direction.UP).isAccepted(),"authorized tool selects artistic profile");
            BlockState changed=world.getBlockState(pos);context.assertTrue(changed.get(PrototypeWallBlock.PROFILE)==PrototypeWallBlock.Profile.BASE&&changed.get(PrototypeWallBlock.MATERIAL)==4,"profile editing preserves material and type");
            context.assertTrue(use(PrototypeWallArchitecture.TOOL,world,player,tool,pos,Direction.DOWN).isAccepted(),"authorized lower-face tool interaction migrates retired material to current type");changed=world.getBlockState(pos);context.assertTrue(PrototypeWallBlock.material(changed)==6&&changed.get(PrototypeWallBlock.PROFILE)==PrototypeWallBlock.Profile.BASE,"material type editing preserves profile and form");
            context.assertTrue(!use(net.minecraft.item.Items.STICK,world,player,new ItemStack(net.minecraft.item.Items.STICK),pos,Direction.UP).isAccepted()&&world.getBlockState(pos).equals(changed),"ordinary item does not edit wall artwork");
            player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);changed.onUse(world,player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(pos),Direction.UP,pos,false));context.assertTrue(world.getBlockState(pos).equals(changed),"empty-hand interaction does not edit wall artwork");
            context.assertTrue(tool.getCount()==1&&tool.getNbt().equals(tag),"tool count and typed NBT remain unchanged");context.complete();
        }finally{clear(world,pos);player.discard();}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="prototype_wall")
    public void pickLootAndPaletteNbtPreserveIndependentManualSides(TestContext context){
        ServerWorld world=context.getWorld();BlockPos pos=context.getAbsolutePos(ROOT);PlayerEntity player=context.createMockSurvivalPlayer();outside(context,player,0);
        try{clear(world,pos);floor(world,pos);for(int yaw=0;yaw<8;yaw++)for(int material:List.of(0,1,4,7))for(var profile:PrototypeWallBlock.Profile.values()){
            BlockState state=form(yaw,material,PrototypeWallBlock.Course.LOW,profile).with(PrototypeWallBlock.SOUTH,WallShape.TALL).with(PrototypeWallBlock.EAST,WallShape.LOW).with(PrototypeWallBlock.WATERLOGGED,true);world.setBlockState(pos,state,Block.NOTIFY_LISTENERS);assertForm(context,state,wall().getPickStack(world,pos,state));
            List<ItemStack> drops=Block.getDroppedStacks(state,world,pos,null,player,ItemStack.EMPTY);context.assertTrue(drops.size()==1&&drops.get(0).getCount()==1,"one wall yields one registry item");assertForm(context,state,drops.get(0));
            NbtCompound saved=NbtHelper.fromBlockState(state);context.assertTrue(NbtHelper.toBlockState(world.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),saved.copy()).equals(state),"actual Minecraft palette serialization retains each mixed side and yaw/art/water");
        }context.complete();}finally{clear(world,pos);player.discard();}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="prototype_wall")
    public void actualPickedManualItemsPlaceExactMixedArtInEveryYaw(TestContext context){
        ServerWorld world=context.getWorld();BlockPos pos=context.getAbsolutePos(ROOT);PlayerEntity player=context.createMockSurvivalPlayer();outside(context,player,180);
        try{for(int yaw=0;yaw<8;yaw++)for(var profile:PrototypeWallBlock.Profile.values()){
            clear(world,pos);floor(world,pos);BlockState expected=PrototypeWallArchitecture.changeMaterial(form(yaw,7,PrototypeWallBlock.Course.LOW,profile).with(PrototypeWallBlock.SOUTH,WallShape.TALL),7);ItemStack stack=wall().artisticStack(expected);stack.setCount(2);NbtCompound old=stack.getNbt().copy();
            context.assertTrue(use(PrototypeWallArchitecture.ITEM,world,player,stack,pos.down(),Direction.UP).isAccepted()&&world.getBlockState(pos).equals(expected),"real picked item preserves selected mixed manual recipe");context.assertTrue(stack.getCount()==1&&stack.getNbt().equals(old),"remaining picked stack is not rewritten");
        }context.complete();}finally{clear(world,pos);player.discard();}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="prototype_wall")
    public void unsupportedBottomSlabAndOccupiedPlacementPreserveSourceItems(TestContext context){
        ServerWorld world=context.getWorld();BlockPos pos=context.getAbsolutePos(ROOT);PlayerEntity player=context.createMockSurvivalPlayer();outside(context,player,180);
        try{clear(world,pos);BlockState bottom=Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE,SlabType.BOTTOM);world.setBlockState(pos.down(),bottom,Block.NOTIFY_ALL);ItemStack stack=wall().artisticStack(form(1,4,PrototypeWallBlock.Course.TALL,PrototypeWallBlock.Profile.ALT));stack.setCount(3);NbtCompound nbt=stack.getNbt().copy();
            context.assertTrue(use(PrototypeWallArchitecture.ITEM,world,player,stack,pos.down(),Direction.UP).isAccepted()&&PrototypeWallBlock.diagonalPost(world.getBlockState(pos)),"90012 can be placed without a bottom support");context.assertTrue(stack.getCount()==2&&stack.getNbt().equals(nbt)&&world.getBlockState(pos.down()).equals(bottom),"successful unsupported post placement consumes one item and preserves remaining NBT/slab");
            world.setBlockState(pos.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(pos,Blocks.DIAMOND_BLOCK.getDefaultState(),Block.NOTIFY_ALL);context.assertTrue(!use(PrototypeWallArchitecture.ITEM,world,player,stack,pos.down(),Direction.UP).isAccepted()&&world.getBlockState(pos).isOf(Blocks.DIAMOND_BLOCK),"coarse diagonal collider cannot erase an occupied root");context.assertTrue(stack.getCount()==2&&stack.getNbt().equals(nbt),"occupied refusal preserves item count/typed NBT");context.complete();
        }finally{clear(world,pos);player.discard();}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="prototype_wall")
    public void removingRealSupportDropsOnceAndPreservesArt(TestContext context){
        ServerWorld world=context.getWorld();BlockPos pos=context.getAbsolutePos(ROOT);
        try{for(int yaw=0;yaw<8;yaw++){
            clear(world,pos);world.setBlockState(pos.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);BlockState state=form(yaw,4,PrototypeWallBlock.Course.TALL,PrototypeWallBlock.Profile.ALT);world.setBlockState(pos,state,Block.NOTIFY_ALL);world.removeBlock(pos.down(),false);context.assertTrue(world.getBlockState(pos).isAir(),"unsupported wall removed at every yaw");List<ItemEntity> drops=world.getEntitiesByClass(ItemEntity.class,new Box(pos).expand(2),item->item.getStack().isOf(PrototypeWallArchitecture.materialBlock(4).asItem()));context.assertTrue(drops.size()==1&&drops.get(0).getStack().getCount()==1,"support removal drops exactly one canonical artistic item");assertForm(context,state,drops.get(0).getStack());
        }context.complete();}finally{clear(world,pos);}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="prototype_wall")
    public void allRecipesStayCachedSimpleWithNoHelperCells(TestContext context){
        ServerWorld world=context.getWorld();BlockPos pos=context.getAbsolutePos(ROOT);clear(world,pos);floor(world,pos);
        PlayerEntity player=context.createMockSurvivalPlayer();outside(context,player,180);
        try{context.assertTrue(wall().getStateManager().getStates().size()==82944,"legacy palettes retain82944states and fixed independent art types avoid a material cross-product");
            int states=0;java.util.Set<String> tempIds=new java.util.HashSet<>();
            for(int material:List.of(0,1,6)){
                PrototypeWallBlock independent=PrototypeWallArchitecture.materialBlock(material);states+=independent.getStateManager().getStates().size();
                context.assertTrue(material==6?independent==wall():!independent.getDefaultState().contains(PrototypeWallBlock.MATERIAL)&&independent.getStateManager().getStates().size()==10368,"only old primary carries legacy MATERIAL compatibility; each new artistic type has10368states");
                tempIds.add(dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.entry(independent.getDefaultState()).temporaryId());
                for(var profile:PrototypeWallBlock.Profile.values()){
                    world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);ItemStack stack=new ItemStack(independent.asItem(),2);stack.getOrCreateSubNbt("BlockStateTag").putString("profile",profile.asString());NbtCompound nbt=stack.getNbt().copy();
                    context.assertTrue(use(independent.asItem(),world,player,stack,pos.down(),Direction.UP).isAccepted(),"actual independent wall item places material"+material+"/"+profile);BlockState placed=world.getBlockState(pos);
                    context.assertTrue(placed.isOf(independent)&&PrototypeWallBlock.material(placed)==material&&placed.get(PrototypeWallBlock.PROFILE)==profile&&stack.getCount()==1&&stack.getNbt().equals(nbt),"server root keeps own art type/profile while remaining item bytes stay unchanged");
                    ItemStack picked=independent.getPickStack(world,pos,placed);context.assertTrue(picked.isOf(independent.asItem())&&PrototypeWallItem.material(picked)==material,"pick returns exact independent type without randomized art");
                    List<ItemStack> realLoot=Block.getDroppedStacks(placed,world,pos,null,player,ItemStack.EMPTY);context.assertTrue(realLoot.size()==1&&realLoot.get(0).isOf(independent.asItem())&&realLoot.get(0).getCount()==1&&PrototypeWallItem.profile(realLoot.get(0))==profile,"native data-namespace loot table yields exactly one own artistic type/profile");
                    BlockState legacy=wall().getDefaultState().with(PrototypeWallBlock.MATERIAL,material).with(PrototypeWallBlock.ROTATION,placed.get(PrototypeWallBlock.ROTATION)).with(PrototypeWallBlock.PROFILE,profile);
                    context.assertTrue(dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.entry(legacy).temporaryId().equals(dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.entry(placed).temporaryId()),"old material-state logical TEMP alias points to same independent type");
                    context.assertTrue(placed.getCollisionShape(world,pos)==legacy.getCollisionShape(world,pos)&&placed.getOutlineShape(world,pos)==legacy.getOutlineShape(world,pos),"artistic type split reuses accepted shared physics and selection instances");
                    context.assertTrue(NbtHelper.toBlockState(world.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),NbtHelper.fromBlockState(placed)).equals(placed),"new own registry palettes roundtrip native state");
                }
            }
            context.assertTrue(states==103680&&tempIds.size()==3,"only90011 plus90002/90012 are offered; hidden schemas retain their reserved numbers");world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            var rpType=Registries.ENTITY_TYPE.get(new net.minecraft.util.Identifier("bloodborne_rp","npc_window"));var raw=rpType.create(world);context.assertTrue(raw instanceof dev.dreamwalker.bloodbornerp.object.RpObjectEntity,"actual custom RP window entity factory exists");
            var plane=(dev.dreamwalker.bloodbornerp.object.RpObjectEntity)raw;
            try{
                plane.refreshPositionAndAngles(0,0,0,0,0);Box physical=plane.activePhysicalBoxes().get(0);
                plane.refreshPositionAndAngles(pos.getX()+.5-(physical.minX+physical.maxX)/2,pos.getY()+.5-(physical.minY+physical.maxY)/2,pos.getZ()+.5-(physical.minZ+physical.maxZ)/2,0,0);world.spawnEntity(plane);
                context.assertTrue(!plane.intersectionChecked&&!plane.isCollidable(),"custom RP plane deliberately does not use its large native entity AABB as a collider");
                ItemStack blocked=new ItemStack(PrototypeWallArchitecture.materialBlock(3).asItem(),2);blocked.getOrCreateSubNbt("BlockStateTag").putString("profile","alt");NbtCompound original=blocked.getNbt().copy();
                NbtCompound rpBefore=plane.writeNbt(new NbtCompound());context.assertTrue(use(blocked.getItem(),world,player,blocked,pos.down(),Direction.UP).isAccepted()&&world.getBlockState(pos).isOf(PrototypeWallArchitecture.materialBlock(6)),"V10 ordinary native wall placement may intersect nonliving RP decorative geometry");context.assertTrue(blocked.getCount()==1&&blocked.getNbt().equals(original)&&plane.writeNbt(new NbtCompound()).equals(rpBefore),"successful ordinary placement consumes one item but changes neither remaining art NBT nor foreign RP typed data");world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                plane.refreshPositionAndAngles(plane.getX()+4,plane.getY(),plane.getZ(),0,0);
                context.assertTrue(use(blocked.getItem(),world,player,blocked,pos.down(),Direction.UP).isAccepted()&&world.getBlockState(pos).isOf(PrototypeWallArchitecture.materialBlock(6)),"the same real item succeeds when the actual plane is outside its physical footprint");
            }finally{plane.removeByBuilder();world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);}
            for(int yaw=0;yaw<8;yaw++)for(boolean post:List.of(false,true))for(int code=0;code<81;code++){
                BlockState state=wall().getDefaultState().with(PrototypeWallBlock.ROTATION,yaw).with(PrototypeWallBlock.POST,post);int value=code;for(Direction direction:ORDER){int shape=value%3;value/=3;state=state.with(PrototypeWallBlock.property(direction),shape==0?WallShape.NONE:shape==1?WallShape.LOW:WallShape.TALL);}VoxelShape collision=state.getCollisionShape(world,pos),outline=state.getOutlineShape(world,pos,ShapeContext.absent());
                context.assertTrue(collision.getBoundingBoxes().size()<=5&&outline.getBoundingBoxes().size()<=5,"actual native decomposed physical/selection budget yaw="+yaw+" code="+code+" post="+post);context.assertTrue(collision==state.getCollisionShape(world,pos),"cached repeated collision query");
                if(!outline.isEmpty()){Box b=outline.getBoundingBox();context.assertTrue(b.minX>=0&&b.minZ>=0&&b.maxX<=1&&b.maxZ<=1&&b.maxY<=1.5,"selection never reserves adjacent cells and follows the real1.5-block wall collider");}
            }context.assertTrue(world.getBlockEntity(pos)==null,"wall has no root/helper block entities");for(Direction direction:ORDER)context.assertTrue(world.getBlockState(pos.offset(direction)).isAir(),"all neighbor cells remain unowned and placeable");context.complete();
        }finally{clear(world,pos);player.discard();}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="prototype_wall")
    public void verticalNativeJunctionDeduplicatesActualVolumesWithoutChangingPlayerUnion(TestContext context){
        ServerWorld world=context.getWorld();BlockPos lowerPos=context.getAbsolutePos(ROOT),upperPos=lowerPos.up();clear(world,lowerPos);floor(world,lowerPos);
        try{
            int cases=0,maxBoxes=0;
            for(int lowerYaw=0;lowerYaw<8;lowerYaw++)for(int upperYaw=0;upperYaw<8;upperYaw++)for(int kind=0;kind<3;kind++){
                BlockState lower=PrototypeWallArchitecture.materialBlock(1).getDefaultState().with(PrototypeWallBlock.CONNECTIONS,PrototypeWallBlock.Connections.MANUAL).with(PrototypeWallBlock.ROTATION,lowerYaw).with(PrototypeWallBlock.NORTH,WallShape.LOW).with(PrototypeWallBlock.SOUTH,WallShape.LOW).with(PrototypeWallBlock.POST,kind!=0);
                BlockState upper=PrototypeWallArchitecture.materialBlock(7).getDefaultState().with(PrototypeWallBlock.CONNECTIONS,PrototypeWallBlock.Connections.MANUAL).with(PrototypeWallBlock.ROTATION,upperYaw);
                if(kind==2)upper=upper.with(PrototypeWallBlock.EAST,WallShape.TALL).with(PrototypeWallBlock.WEST,WallShape.LOW);
                world.setBlockState(lowerPos,lower,Block.NOTIFY_LISTENERS);VoxelShape lowerRaw=PrototypeWallBlock.rawCollision(lower),upperRaw=PrototypeWallBlock.rawCollision(upper),actual=upper.getCollisionShape(world,upperPos);
                context.assertTrue(!VoxelShapes.matchesAnywhere(lowerRaw,actual.offset(0,1,0),BooleanBiFunction.AND),"actual assigned native wall volumes have no positive overlap: "+lowerYaw+"/"+upperYaw+"/"+kind);
                VoxelShape previousUnion=VoxelShapes.union(lowerRaw,upperRaw.offset(0,1,0)),currentUnion=VoxelShapes.union(lowerRaw,actual.offset(0,1,0));
                context.assertTrue(!VoxelShapes.matchesAnywhere(previousUnion,currentUnion,BooleanBiFunction.NOT_SAME),"complete player collider union is exact old raw union, without noclip or discarded volume");
                context.assertTrue(actual==upper.getCollisionShape(world,upperPos),"same real lower/upper shape pair reuses bounded cached native result");maxBoxes=Math.max(maxBoxes,actual.getBoundingBoxes().size());cases++;
            }
            BlockState top=PrototypeWallArchitecture.materialBlock(2).getDefaultState(),bottom=wall().getDefaultState();
            world.setBlockState(lowerPos,Blocks.POLISHED_DEEPSLATE_WALL.getDefaultState(),Block.NOTIFY_LISTENERS);VoxelShape vanillaRaw=world.getBlockState(lowerPos).getCollisionShape(world,lowerPos),own=top.getCollisionShape(world,upperPos);
            context.assertTrue(!VoxelShapes.matchesAnywhere(vanillaRaw,own.offset(0,1,0),BooleanBiFunction.AND)&&!VoxelShapes.matchesAnywhere(VoxelShapes.union(vanillaRaw,PrototypeWallBlock.rawCollision(top).offset(0,1,0)),VoxelShapes.union(vanillaRaw,own.offset(0,1,0)),BooleanBiFunction.NOT_SAME),"same actual deduplication works above vanilla native wall and preserves its whole union");
            world.setBlockState(lowerPos,bottom,Block.NOTIFY_LISTENERS);world.setBlockState(upperPos,top,Block.NOTIFY_LISTENERS);BlockPos thirdPos=upperPos.up();BlockState third=PrototypeWallArchitecture.materialBlock(5).getDefaultState();world.setBlockState(thirdPos,third,Block.NOTIFY_LISTENERS);
            VoxelShape actualColumn=VoxelShapes.union(bottom.getCollisionShape(world,lowerPos),top.getCollisionShape(world,upperPos).offset(0,1,0),third.getCollisionShape(world,thirdPos).offset(0,2,0));
            VoxelShape rawColumn=VoxelShapes.union(PrototypeWallBlock.rawCollision(bottom),PrototypeWallBlock.rawCollision(top).offset(0,1,0),PrototypeWallBlock.rawCollision(third).offset(0,2,0));context.assertTrue(!VoxelShapes.matchesAnywhere(actualColumn,rawColumn,BooleanBiFunction.NOT_SAME),"three stacked sections use lower RAW upper extension while retaining complete physical column");
            world.setBlockState(lowerPos,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);context.assertTrue(top.getCollisionShape(world,upperPos)==PrototypeWallBlock.rawCollision(top),"removing lower carrier immediately restores full upper RAW collider");
            int triples=0;
            for(int lowerYaw=0;lowerYaw<8;lowerYaw++)for(int middleYaw=0;middleYaw<8;middleYaw++)for(int capKind=0;capKind<3;capKind++){
                BlockState first=form(lowerYaw,6,PrototypeWallBlock.Course.LOW,PrototypeWallBlock.Profile.BASE).with(PrototypeWallBlock.POST,true);
                BlockState middle=form(middleYaw,6,PrototypeWallBlock.Course.TALL,PrototypeWallBlock.Profile.ALT).with(PrototypeWallBlock.EAST,WallShape.LOW).with(PrototypeWallBlock.POST,true);
                BlockState cap=capKind==0?PrototypeWallArchitecture.materialBlock(5).getDefaultState().with(PrototypeWallBlock.CONNECTIONS,PrototypeWallBlock.Connections.MANUAL).with(PrototypeWallBlock.NORTH,WallShape.TALL):capKind==1?Blocks.POLISHED_DEEPSLATE_WALL.getDefaultState().with(net.minecraft.block.WallBlock.NORTH_SHAPE,WallShape.LOW):Blocks.STONE.getDefaultState();
                world.setBlockState(thirdPos,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);world.setBlockState(lowerPos,first,Block.NOTIFY_LISTENERS);world.setBlockState(upperPos,middle,Block.NOTIFY_LISTENERS);
                VoxelShape withoutCap=middle.getCollisionShape(world,upperPos);world.setBlockState(thirdPos,cap,Block.NOTIFY_LISTENERS);
                VoxelShape firstRaw=PrototypeWallBlock.rawCollision(first),middleRaw=PrototypeWallBlock.rawCollision(middle),capRaw=cap.getBlock() instanceof PrototypeWallBlock?PrototypeWallBlock.rawCollision(cap):dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.nativeCollision(world,thirdPos,ShapeContext.absent());
                VoxelShape firstActual=first.getCollisionShape(world,lowerPos),middleActual=middle.getCollisionShape(world,upperPos),capActual=cap.getCollisionShape(world,thirdPos);
                context.assertTrue(!VoxelShapes.matchesAnywhere(firstActual,middleActual.offset(0,1,0),BooleanBiFunction.AND)&&!VoxelShapes.matchesAnywhere(middleActual,capActual.offset(0,1,0),BooleanBiFunction.AND),"native triple assigns each adjacent positive seam once: "+lowerYaw+"/"+middleYaw+"/"+capKind);
                VoxelShape oldUnion=VoxelShapes.union(firstRaw,middleRaw.offset(0,1,0),capRaw.offset(0,2,0)),newUnion=VoxelShapes.union(firstActual,middleActual.offset(0,1,0),capActual.offset(0,2,0));
                context.assertTrue(!VoxelShapes.matchesAnywhere(oldUnion,newUnion,BooleanBiFunction.NOT_SAME),"mixed own/vanilla/full-cap triple preserves the complete old player union exactly");
                context.assertTrue(middleActual==middle.getCollisionShape(world,upperPos),"same own/lower/cap triple reuses bounded cached result");maxBoxes=Math.max(maxBoxes,middleActual.getBoundingBoxes().size());triples++;
                world.setBlockState(thirdPos,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
                context.assertTrue(middle.getCollisionShape(world,upperPos)==withoutCap,"cap removal returns the same cached lower-seam assignment");
            }
            // Actual item placement under pre-existing native upper coverage
            // must use the deduplicated BlockState shape, without skipping the
            // ordinary positive-volume guard or changing the upper fixture.
            PlayerEntity player=context.createMockSurvivalPlayer();outside(context,player,-180);
            try{for(BlockState cap:List.of(Blocks.STONE.getDefaultState(),Blocks.POLISHED_DEEPSLATE_WALL.getDefaultState().with(net.minecraft.block.WallBlock.NORTH_SHAPE,WallShape.LOW))){
                clear(world,lowerPos);floor(world,lowerPos);world.setBlockState(lowerPos.up(),cap,Block.NOTIFY_LISTENERS);ItemStack item=new ItemStack(PrototypeWallArchitecture.ITEM,2);
                context.assertTrue(use(PrototypeWallArchitecture.ITEM,world,player,item,lowerPos.down(),Direction.UP).isAccepted()&&item.getCount()==1,"real ordinary item places below native coverage without new positive solid overlap");
                BlockState placed=world.getBlockState(lowerPos);VoxelShape actual=placed.getCollisionShape(world,lowerPos),nativeCap=cap.getCollisionShape(world,lowerPos.up());
                context.assertTrue(!VoxelShapes.matchesAnywhere(actual,nativeCap.offset(0,1,0),BooleanBiFunction.AND)&&!VoxelShapes.matchesAnywhere(VoxelShapes.union(PrototypeWallBlock.rawCollision(placed),nativeCap.offset(0,1,0)),VoxelShapes.union(actual,nativeCap.offset(0,1,0)),BooleanBiFunction.NOT_SAME),"ordinary placement actual shape has no positive cap overlap and the collider union remains unchanged");
                context.assertTrue(world.getBlockState(lowerPos.up()).equals(cap),"placement preserves exact native upper state");world.setBlockState(lowerPos.up(),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
                context.assertTrue(placed.getCollisionShape(world,lowerPos)==PrototypeWallBlock.rawCollision(placed),"removing native cap immediately restores full wall RAW extension");
            }}finally{player.discard();clear(world,lowerPos);}
            BlockState excluded=dev.dreamwalker.bloodbornedw.composite.CompositeArchitecture.kindBlock("prototype_roof").getDefaultState();world.setBlockState(upperPos,excluded,Block.NOTIFY_LISTENERS);
            context.assertTrue(bottom.getCollisionShape(world,lowerPos)==PrototypeWallBlock.rawCollision(bottom),"a full-cube CompositeRoot above is never treated as a native cap or silently cut out of wall physics");
            context.assertTrue(cases==192&&triples==192,"all8×8 yaws checked for three native support/upper recipes and three mixed cap recipes");System.out.println("DW_V9_WALL_STACK_GEOMETRY {\"pairsChecked\":"+cases+",\"triplesChecked\":"+triples+",\"ordinaryNativeCapPlacements\":2,\"maximumActualDecomposedBoxes\":"+maxBoxes+",\"samePlayerUnion\":true,\"migrationExceptionAdded\":false}");context.complete();
        }finally{world.setBlockState(lowerPos.up(2),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);clear(world,lowerPos);}
    }
    private static BlockState nativeOracle(ServerWorld world,BlockPos pos,PlayerEntity player){return Blocks.POLISHED_DEEPSLATE_WALL.getPlacementState(new ItemPlacementContext(world,player,Hand.MAIN_HAND,new ItemStack(Blocks.POLISHED_DEEPSLATE_WALL),new BlockHitResult(Vec3d.ofCenter(pos.down()),Direction.UP,pos.down(),false)));}
    private static void assertNative(TestContext context,BlockState actual,BlockState expected,String label){BlockState worldState=wall().nativeState(actual);for(Direction direction:ORDER)context.assertTrue(worldState.get(PrototypeWallBlock.property(direction))==expected.get(PrototypeWallBlock.property(direction)),label+" native independent side "+direction+": actual="+worldState+" expected="+expected);context.assertTrue(worldState.get(PrototypeWallBlock.POST).equals(expected.get(PrototypeWallBlock.POST)),label+" native post");}
    private static void assertForm(TestContext context,BlockState state,ItemStack stack){context.assertTrue(stack.isOf(PrototypeWallArchitecture.materialBlock(PrototypeWallBlock.material(state)).asItem())&&PrototypeWallItem.material(stack)==PrototypeWallBlock.material(state)&&PrototypeWallItem.profile(stack)==state.get(PrototypeWallBlock.PROFILE),"pick/drop retains canonical independent art identity");NbtCompound tag=stack.getSubNbt("BlockStateTag");context.assertTrue(tag!=null&&!tag.contains("waterlogged")&&!tag.contains("course")&&!tag.contains("post")&&tag.getString("connections").equals("manual")&&tag.getString("rotation").equals(Integer.toString(state.get(PrototypeWallBlock.ROTATION))),"manual recipe serializes no obsolete uniform-course/boolean properties");for(Direction direction:ORDER)context.assertTrue(tag.getString(direction.asString()).equals(state.get(PrototypeWallBlock.property(direction)).asString()),"manual independent arm "+direction);}
    private static ActionResult use(net.minecraft.item.Item item,ServerWorld world,PlayerEntity player,ItemStack stack,BlockPos clicked,Direction side){player.setStackInHand(Hand.MAIN_HAND,stack);return item.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(clicked),side,clicked,false)));}
    private static void outside(TestContext context,PlayerEntity player,float yaw){BlockPos pos=context.getAbsolutePos(new BlockPos(-4,5,-4));player.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()+.5,yaw,0);}
    private static void floor(ServerWorld world,BlockPos pos){for(BlockPos p:BlockPos.iterate(pos.add(-2,-1,-2),pos.add(2,-1,2)))world.setBlockState(p,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);}
    private static void clear(ServerWorld world,BlockPos pos){world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);for(BlockPos p:BlockPos.iterate(pos.add(-2,-1,-2),pos.add(2,1,2)))world.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);for(ItemEntity item:world.getEntitiesByClass(ItemEntity.class,new Box(pos).expand(3),entity->true))item.discard();}
}
