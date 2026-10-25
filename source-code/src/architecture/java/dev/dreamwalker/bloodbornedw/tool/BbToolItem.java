package dev.dreamwalker.bloodbornedw.tool;

import java.util.List;
import net.minecraft.item.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraft.client.item.TooltipContext;

public final class BbToolItem extends Item {
    public enum Mode {
        UP("Поднять на 1 блок"),DOWN("Опустить на 1 блок"),SPECIAL("Особое действие"),COMMANDS("Привязать команды"),CLEAR("Удалить связи и команды"),ROTATE("Поворот по часовой стрелке");
        public final String title;Mode(String title){this.title=title;}
    }
    public BbToolItem(){super(new Settings().maxCount(1));}
    public static Mode mode(ItemStack stack){return Mode.values()[Math.floorMod(stack.hasNbt()?stack.getNbt().getInt("BBMode"):0,Mode.values().length)];}
    public static void cycle(ServerPlayerEntity player){if(!BbToolService.canEdit(player)){player.sendMessage(Text.literal("Нужны права строительства."),true);return;}var stack=player.getMainHandStack();if(!(stack.getItem() instanceof BbToolItem))return;stack.getOrCreateNbt().putInt("BBMode",(mode(stack).ordinal()+1)%Mode.values().length);BbToolService.clearSelection(player);player.sendMessage(Text.literal("BB · "+mode(stack).title),true);}
    @Override public Text getName(ItemStack stack){return Text.literal("Инструмент BB · "+mode(stack).title);}
    @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity player,Hand hand){if(hand!=Hand.MAIN_HAND)return TypedActionResult.pass(player.getStackInHand(hand));if(player instanceof ServerPlayerEntity server)cycle(server);return TypedActionResult.success(player.getStackInHand(hand),world.isClient);}
    @Override public void appendTooltip(ItemStack stack,World world,List<Text> lines,TooltipContext context){lines.add(Text.literal("ПКМ — следующий режим; ЛКМ — применить к объекту."));lines.add(Text.literal("Режим: "+mode(stack).title));lines.add(Text.literal("Консольные команды: настройка только для OP4."));}
}
