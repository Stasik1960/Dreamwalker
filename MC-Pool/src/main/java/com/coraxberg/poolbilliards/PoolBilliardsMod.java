package com.coraxberg.poolbilliards;

import com.coraxberg.poolbilliards.block.BilliardsTableBlock;
import com.coraxberg.poolbilliards.block.BilliardsTableBlockEntity;
import com.coraxberg.poolbilliards.block.BilliardsTablePartBlock;
import com.coraxberg.poolbilliards.net.PoolPackets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.Block;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class PoolBilliardsMod implements ModInitializer {
    public static final String MOD_ID = "poolbilliards";

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    public static final Block BILLIARDS_TABLE = new BilliardsTableBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.DARK_GREEN)
            .strength(2.0f, 6.0f)
            .sounds(BlockSoundGroup.WOOD)
            .nonOpaque());

    public static final Block BILLIARDS_TABLE_PART = new BilliardsTablePartBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.DARK_GREEN)
            .strength(2.0f, 6.0f)
            .sounds(BlockSoundGroup.WOOD)
            .nonOpaque());

    public static final Item BILLIARDS_TABLE_ITEM = new BlockItem(BILLIARDS_TABLE, new Item.Settings());
    public static final Item BILLIARDS_CUE = new Item(new Item.Settings().maxCount(1));

    public static BlockEntityType<BilliardsTableBlockEntity> BILLIARDS_TABLE_ENTITY;

    public static ItemGroup POOL_GROUP;

    @Override
    public void onInitialize() {
        Registry.register(Registries.BLOCK, id("billiards_table"), BILLIARDS_TABLE);
        Registry.register(Registries.BLOCK, id("billiards_table_part"), BILLIARDS_TABLE_PART);
        Registry.register(Registries.ITEM, id("billiards_table"), BILLIARDS_TABLE_ITEM);
        Registry.register(Registries.ITEM, id("billiards_cue"), BILLIARDS_CUE);

        BILLIARDS_TABLE_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                id("billiards_table"),
                BlockEntityType.Builder.create(BilliardsTableBlockEntity::new, BILLIARDS_TABLE).build(null)
        );

        POOL_GROUP = Registry.register(Registries.ITEM_GROUP, id("pool_billiards"), FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.poolbilliards.pool_billiards"))
                .icon(() -> new ItemStack(BILLIARDS_TABLE_ITEM))
                .entries((context, entries) -> {
                    entries.add(BILLIARDS_TABLE_ITEM);
                    entries.add(BILLIARDS_CUE);
                })
                .build());

        PoolPackets.registerServerReceivers();
    }
}
