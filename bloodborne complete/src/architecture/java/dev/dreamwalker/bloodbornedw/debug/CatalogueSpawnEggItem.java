package dev.dreamwalker.bloodbornedw.debug;

import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import net.minecraft.world.World;

/** RP egg presentation only; registry/type/cosmetic colors and spawn behavior unchanged. */
public final class CatalogueSpawnEggItem extends SpawnEggItem {
    public CatalogueSpawnEggItem(EntityType<? extends MobEntity> type,int primary,int secondary,Settings settings){super(type,primary,secondary,settings);}
    @Override public Text getName(ItemStack stack){return DebugCatalogue.itemName(stack,super.getName(stack));}
    @Override public void appendTooltip(ItemStack stack,World world,List<Text> tooltip,TooltipContext context){super.appendTooltip(stack,world,tooltip,context);DebugCatalogue.itemTooltip(stack,tooltip);}
}
