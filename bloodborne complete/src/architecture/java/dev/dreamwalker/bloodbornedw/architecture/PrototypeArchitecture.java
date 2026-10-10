package dev.dreamwalker.bloodbornedw.architecture;

import java.util.List;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallArchitecture;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderRuntime;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import static net.minecraft.server.command.CommandManager.literal;

/** Isolated checkpoint registration; does not assign final catalog numbers. */
public final class PrototypeArchitecture {
    public static PrototypeLadderBlock LADDER;
    public static PrototypeLadderItem LADDER_ITEM;
    public static final java.util.List<PrototypeLadderBlock> LADDERS=new java.util.ArrayList<>();
    public static final java.util.List<PrototypeLadderItem> LADDER_ITEMS=new java.util.ArrayList<>();
    private static boolean initialized;
    private PrototypeArchitecture() {}
    public static void initialize() {
        if (initialized) return;
        LADDER = Registry.register(Registries.BLOCK, id("prototype_ladder"), new PrototypeLadderBlock(AbstractBlock.Settings.create()
                .strength(.4F).sounds(BlockSoundGroup.LADDER).nonOpaque().pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK)));
        LADDER_ITEM = Registry.register(Registries.ITEM, id("prototype_ladder"), new PrototypeLadderItem(LADDER, new Item.Settings()));
        LADDERS.add(LADDER);LADDER_ITEMS.add(LADDER_ITEM);
        for(int art=1;art<=2;art++){
            PrototypeLadderBlock block=Registry.register(Registries.BLOCK,id("prototype_ladder_art_"+art),new PrototypeLadderBlock(AbstractBlock.Settings.create()
                    .strength(.4F).sounds(BlockSoundGroup.LADDER).nonOpaque().pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK),art));
            LADDERS.add(block);LADDER_ITEMS.add(Registry.register(Registries.ITEM,id("prototype_ladder_art_"+art),new PrototypeLadderItem(block,new Item.Settings())));
        }
        SourceLadderRuntime.initialize();
        initialized = true;
    }
    public static void initializeClient() { PrototypeArchitectureClient.initialize(); }
    public static Identifier id(String path) { return new Identifier("bloodborne_dw", path); }
    public static PrototypeLadderItem ladderItem(int art){return LADDER_ITEMS.get(Math.max(0,Math.min(2,art)));}
    public static PrototypeLadderBlock ladderBlock(int art){return LADDERS.get(Math.max(0,Math.min(2,art)));}

    public static boolean edit(World world, BlockPos pos, BlockState before, BlockState next, net.minecraft.entity.player.PlayerEntity player) {
        return editInternal(world,pos,before,next,player);
    }
    private static boolean editInternal(World world, BlockPos pos, BlockState before, BlockState next, net.minecraft.entity.player.PlayerEntity player) {
        if(!BuildPermissions.canEdit(world,player,pos))return false;
        if(!before.isOf(next.getBlock())||!world.isChunkLoaded(pos)||!world.getBlockState(pos).equals(before))return false;
        if(world.getBlockEntity(pos) instanceof dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity mounted&&!mounted.contributions().isEmpty())return world.isClient||CompositeRuntime.transition((ServerWorld)world,mounted.resident(),next,player).outcome()==TransactionCore.Outcome.COMMITTED;
        if (before.isOf(LADDER) && before.get(PrototypeLadderBlock.SOURCE_CLONE))
            return SourceLadderRuntime.edit(world, pos, before, next, player);
        if (!(before.getBlock() instanceof PrototypeLadderBlock) || !before.isOf(next.getBlock()) || !world.isChunkLoaded(pos) || !world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos)
                || player == null || !player.getAbilities().allowModifyWorld || !world.canPlayerModifyAt(player, pos) || !world.getBlockState(pos).equals(before)) return false;
        boolean rotation = PrototypeLadderBlock.yaw(before) != PrototypeLadderBlock.yaw(next);
        if (rotation) {
            for (var direction : PrototypeLadderBlock.backingDirections(next))
                if (!world.isChunkLoaded(pos.offset(direction))) return false;
            if (!next.canPlaceAt(world, pos)) return false;
            var shape = next.getCollisionShape(world, pos);
            if(PlacementPhysics.conflict(world,shape.getBoundingBoxes().stream().map(box->box.offset(pos)).toList(),null,null,pos)!=null)return false;
        }
        if (world.isClient) return true;
        return before.equals(next) || world.setBlockState(pos, next, rotation ? Block.NOTIFY_ALL : Block.NOTIFY_LISTENERS);
    }

}
