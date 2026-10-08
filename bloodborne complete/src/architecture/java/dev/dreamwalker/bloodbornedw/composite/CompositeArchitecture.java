package dev.dreamwalker.bloodbornedw.composite;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;

public final class CompositeArchitecture {
    private static final Map<String,CompositeRootBlock> BLOCKS=new LinkedHashMap<>();
    private static final Map<String,CompositeItem> ITEMS=new LinkedHashMap<>();
    public static CompositeCellBlock CELL;
    public static BlockEntityType<CompositeBlockEntity> CELL_ENTITY;
    public static Item BUILDER;
    private CompositeArchitecture(){}
    public static void initialize(String... paths){
        if(CELL!=null)return;
        CELL=Registry.register(Registries.BLOCK,id("composite_cell"),new CompositeCellBlock());
        List<String> kinds=new ArrayList<>(Arrays.asList(paths));if(kinds.contains(GlazingTypes.WINDOW01)){if(!kinds.contains(GlazingTypes.WINDOW02))kinds.add(GlazingTypes.WINDOW02);if(!kinds.contains(GlazingTypes.WINDOW03))kinds.add(GlazingTypes.WINDOW03);}
        for(String path:kinds){CompositeSpec spec=CompositeSpec.load(path);CompositeRootBlock block=Registry.register(Registries.BLOCK,spec.id,GlazingTypes.isGlazingPath(path)?new ThinWindowRootBlock(spec):new CompositeRootBlock(spec));BLOCKS.put(spec.id.getPath(),block);ITEMS.put(spec.id.getPath(),Registry.register(Registries.ITEM,spec.id,new CompositeItem(block)));}
        List<Block> carriers=new ArrayList<>(BLOCKS.values());carriers.add(CELL);carriers.addAll(dev.dreamwalker.bloodbornedw.architecture.PrototypeArchitecture.LADDERS);carriers.addAll(dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallArchitecture.blocks());
        CELL_ENTITY=Registry.register(Registries.BLOCK_ENTITY_TYPE,id("composite_cell"),FabricBlockEntityTypeBuilder.create(CompositeBlockEntity::new,carriers.toArray(Block[]::new)).build());
        BUILDER=Registry.register(Registries.ITEM,id("composite_builder"),new dev.dreamwalker.bloodbornedw.architecture.BuildingTool());
        dev.dreamwalker.bloodbornedw.debug.UnifiedCreativeCatalogue.initialize();
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,entity)->{var owner=CompositeRuntime.target(world,pos,player);if(owner==null)owner=CompositeRuntime.soleNativeOwner(world,pos);if(owner==null)return !(state.getBlock() instanceof CompositeRootBlock||state.isOf(CELL))||CompositeRuntime.contributions(world,pos).isEmpty();if(world instanceof ServerWorld server)CompositeRuntime.remove(server,owner,player,!player.getAbilities().creativeMode);return false;});
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{if(world.getBlockState(hit.getBlockPos()).getBlock() instanceof CompositeRootBlock||world.getBlockState(hit.getBlockPos()).isOf(CELL))return ActionResult.PASS;return CompositeRuntime.use(world,hit.getBlockPos(),player);});
        ServerTickEvents.END_WORLD_TICK.register(CompositeRuntime::drain);
        ServerChunkEvents.CHUNK_LOAD.register((world,chunk)->CompositeRuntime.chunkLoaded(world,chunk.getPos()));
        // Publish saved membership before prepareStartRegion starts asynchronous lighting.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.LOAD.register((server,world)->CompositeLedger.get(world));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.UNLOAD.register((server,world)->CompositeShapeSnapshots.forget(world));
        CompositeNetworking.initialize();
        dev.dreamwalker.bloodbornedw.link.MechanismLinks.initialize();
        dev.dreamwalker.bloodbornedw.link.CompositeMechanismBridge.initialize();
        dev.dreamwalker.bloodbornedw.link.MechanismBuilder.initialize();
    }
    public static void initializeClient(){CompositeClient.initialize();}
    public static CompositeRootBlock kindBlock(String path){CompositeRootBlock block=BLOCKS.get(path);if(block==null)throw new IllegalArgumentException("Unknown composite kind "+path);return block;}
    public static CompositeItem kindItem(String path){CompositeItem item=ITEMS.get(path);if(item==null)throw new IllegalArgumentException("Unknown composite kind "+path);return item;}
    public static Collection<CompositeRootBlock> blocks(){return List.copyOf(BLOCKS.values());}
    public static Identifier id(String path){return new Identifier("bloodborne_dw",path);}
}
