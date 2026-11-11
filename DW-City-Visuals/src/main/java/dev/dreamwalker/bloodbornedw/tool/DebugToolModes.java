package dev.dreamwalker.bloodbornedw.tool;

import dev.dreamwalker.bloodbornerp.mob.RpMobEntity;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

public final class DebugToolModes {
    public enum Action {
        UP("Поднять модель на 0,5 блока"),
        DOWN("Опустить модель на 0,5 блока"),
        LEFT("Сдвинуть модель влево на 0,5 блока"),
        RIGHT("Сдвинуть модель вправо на 0,5 блока"),
        FORWARD("Сдвинуть модель вперёд на 0,5 блока"),
        BACK("Сдвинуть модель назад на 0,5 блока"),
        VISUAL_ROTATE("Повернуть модель по часовой стрелке"),
        RENDER_PRIORITY("Переключить приоритет отображения"),
        SPECIAL("Особое действие блока", BbToolItem.Mode.SPECIAL),
        COMMANDS("Привязать команды", BbToolItem.Mode.COMMANDS),
        CLEAR("Удалить связи и команды", BbToolItem.Mode.CLEAR),
        BLOCK_ROTATE("Повернуть блок по часовой стрелке"),
        PLAYER_COLLISION("Переключить коллизию для игроков"),
        BLOCK_COLLISION("Переключить коллизию для блоков"),
        NPC_SPECIAL("Особое действие NPC", BbToolItem.Mode.SPECIAL_NPC),
        NPC_CLOCKWISE("Повернуть NPC по часовой стрелке"),
        NPC_COUNTERCLOCKWISE("Повернуть NPC против часовой стрелки"),
        BLOCK_ROTATE_BACK("Повернуть блок против часовой стрелки");

        public final String title;
        public final BbToolItem.Mode legacy;

        Action(String title) { this(title, null); }
        Action(String title, BbToolItem.Mode legacy) {
            this.title = title;
            this.legacy = legacy;
        }
    }

    public enum Tool {
        MODEL("Инструмент BB", List.of(Action.UP, Action.DOWN, Action.LEFT, Action.RIGHT, Action.FORWARD, Action.BACK, Action.VISUAL_ROTATE, Action.RENDER_PRIORITY)),
        BLOCK("Печатка смотрителя", List.of(Action.SPECIAL, Action.COMMANDS, Action.CLEAR, Action.BLOCK_ROTATE,
                Action.PLAYER_COLLISION, Action.BLOCK_COLLISION, Action.BLOCK_ROTATE_BACK)),
        NPC("Жезл постановщика", List.of(Action.NPC_SPECIAL, Action.NPC_CLOCKWISE, Action.NPC_COUNTERCLOCKWISE));

        public final String title;
        public final List<Action> actions;

        Tool(String title, List<Action> actions) {
            this.title = title;
            this.actions = actions;
        }
    }

    public static final String MODE_KEY = "DwDebugToolMode";
    private static BbToolItem blockTool;
    private static BbToolItem npcTool;

    private DebugToolModes() {}

    public static void register() {
        blockTool = Registry.register(Registries.ITEM, new Identifier("bloodborne_dw", "bb_block_tool"), new BbToolItem());
        npcTool = Registry.register(Registries.ITEM, new Identifier("bloodborne_dw", "bb_npc_tool"), new BbToolItem());
        DebugAdjustments.initialize();
    }

    public static List<ItemStack> extraItems() {
        return blockTool == null ? List.of() : List.of(blockTool.getDefaultStack(), npcTool.getDefaultStack());
    }

    public static boolean isModelTool(ItemStack stack) {
        return stack.getItem() instanceof BbToolItem && tool(stack) == Tool.MODEL;
    }

    public static Tool tool(ItemStack stack) {
        if (stack.isOf(blockTool)) return Tool.BLOCK;
        if (stack.isOf(npcTool)) return Tool.NPC;
        return Tool.MODEL;
    }

    public static int index(ItemStack stack) {
        int index = stack.getNbt() == null ? 0 : stack.getNbt().getInt(MODE_KEY);
        return Math.floorMod(index, tool(stack).actions.size());
    }

    public static Action action(ItemStack stack) { return tool(stack).actions.get(index(stack)); }

    public static void cycle(ServerPlayerEntity player) {
        if (!BbToolService.canEdit(player)) {
            player.sendMessage(Text.literal("Нужны права строительства."), true);
            return;
        }
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof BbToolItem)) return;
        if (tool(stack) == Tool.MODEL && !ClickRateLimit.accept(player, 1)) return;
        Tool tool = tool(stack);
        int next = (index(stack) + 1) % tool.actions.size();
        stack.getOrCreateNbt().putInt(MODE_KEY, next);
        BbToolService.clearSelection(player);
        player.sendMessage(Text.literal(tool.title + " · " + action(stack).title), true);
    }

    /** Return false to let the pinned tool service handle its existing modes. */
    public static boolean apply(ServerPlayerEntity player, ToolTarget target) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof BbToolItem)) return false;
        Action action = action(stack);
        if (tool(stack) == Tool.MODEL && !ClickRateLimit.accept(player, 0)) return true;
        if (!BbToolService.canEdit(player) || target == null || !target.valid(player.getServer())
                || target.world(player.getServer()) != player.getServerWorld()
                || !player.getServerWorld().canPlayerModifyAt(player, BlockPos.ofFloored(target.position(player.getServer())))) return true;
        Entity entity = target.entity(player.getServer());
        boolean block = target.modTarget(player.getServer()) && (entity == null || entity instanceof RpObjectEntity);
        if (action.legacy != null) {
            if (action == Action.NPC_SPECIAL) return !(entity instanceof RpMobEntity);
            if (!block) return true;
            if (action != Action.CLEAR || entity != null || !dev.dreamwalker.bloodbornedw.architecture.DwUtilityBlocks.marker(
                    player.getServerWorld().getBlockState(BlockPos.fromLong(target.root())))) return false;
            ToolCommands.remove(player.getServer(), target);
            player.sendMessage(Text.literal("Команды удалены."), true);
            return true;
        }

        boolean changed = switch (action) {
            case UP -> block && DebugAdjustments.move(player, target, Direction.UP);
            case DOWN -> block && DebugAdjustments.move(player, target, Direction.DOWN);
            case LEFT -> block && DebugAdjustments.move(player, target, player.getHorizontalFacing().rotateYCounterclockwise());
            case RIGHT -> block && DebugAdjustments.move(player, target, player.getHorizontalFacing().rotateYClockwise());
            case FORWARD -> block && DebugAdjustments.move(player, target, player.getHorizontalFacing());
            case BACK -> block && DebugAdjustments.move(player, target, player.getHorizontalFacing().getOpposite());
            case VISUAL_ROTATE -> block && DebugAdjustments.rotate(player, target);
            case RENDER_PRIORITY -> block && DebugAdjustments.toggleRenderPriority(player, target);
            case BLOCK_ROTATE, BLOCK_ROTATE_BACK -> block && DebugBlockRotation.apply(player, target, action == Action.BLOCK_ROTATE);
            case PLAYER_COLLISION -> block && DebugAdjustments.toggle(player, target, true);
            case BLOCK_COLLISION -> block && DebugAdjustments.toggle(player, target, false);
            case NPC_CLOCKWISE -> rotateNpc(entity, true);
            case NPC_COUNTERCLOCKWISE -> rotateNpc(entity, false);
            default -> false;
        };
        if (action != Action.PLAYER_COLLISION && action != Action.BLOCK_COLLISION && action != Action.RENDER_PRIORITY || !changed) {
            player.sendMessage(Text.literal(changed ? action.title : "Действие недоступно для этой цели."), true);
        }
        return true;
    }

    private static boolean rotateNpc(Entity entity, boolean clockwise) {
        if (!(entity instanceof MobEntity mob)) return false;
        if (mob instanceof RpMobEntity rp) {
            ((ActorRotation)(Object)rp).dw$rotate(clockwise);
            return true;
        }
        if (!Registries.ENTITY_TYPE.getId(mob.getType()).getNamespace().equals("customnpcs")) return false;
        float yaw = MathHelper.wrapDegrees(mob.getYaw() + (clockwise ? 45 : -45));
        try {
            var npcType = mob.getClass();
            var ai = npcType.getField("ais").get(mob);
            var orientation = ai.getClass().getField("orientation");
            var updateClient = npcType.getField("updateClient");
            orientation.setInt(ai, Math.floorMod(Math.round(yaw), 360));
            updateClient.setBoolean(mob, true);
        } catch (ReflectiveOperationException incompatible) {
            dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Could not persist CustomNPC orientation", incompatible);
        }
        mob.getNavigation().stop();
        mob.setYaw(yaw);
        mob.setBodyYaw(yaw);
        mob.setHeadYaw(yaw);
        mob.prevYaw = yaw;
        mob.prevBodyYaw = yaw;
        mob.prevHeadYaw = yaw;
        return true;
    }

    public static boolean applyCustomNpc(ServerPlayerEntity player, Entity entity) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof BbToolItem) || tool(stack) != Tool.NPC
                || !Registries.ENTITY_TYPE.getId(entity.getType()).getNamespace().equals("customnpcs")) return false;
        Action action = action(stack);
        if (BbToolService.canEdit(player) && player.getServerWorld().canPlayerModifyAt(player, entity.getBlockPos())
                && (action == Action.NPC_CLOCKWISE || action == Action.NPC_COUNTERCLOCKWISE)) {
            if (rotateNpc(entity, action == Action.NPC_CLOCKWISE)) player.sendMessage(Text.literal(action.title), true);
        }
        return true;
    }

    public interface ActorRotation {
        void dw$rotate(boolean clockwise);
    }
}
