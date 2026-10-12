package dev.dreamwalker.bloodbornedw.architecture;

import java.util.*;
import java.util.function.Supplier;
import dev.dreamwalker.bloodbornerp.object.ObjectRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import static net.minecraft.server.command.CommandManager.literal;

/** One-use server lease on the actual held stack. Never serialized into items or objects. */
public final class NoCollidePlacement {
    private record Lease(ItemStack stack,int slot) {}
    private static final Map<UUID,Lease> LEASES=new HashMap<>();
    private static final ThreadLocal<Integer> DEPTH=ThreadLocal.withInitial(()->0);
    private static final ThreadLocal<UUID> OWNER=new ThreadLocal<>();
    private static boolean initialized;
    private NoCollidePlacement() {}
    public static void initialize(){
        if(initialized)return;initialized=true;
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(literal("bb")
            .then(literal("no-collide").requires(source->source.hasPermissionLevel(2)).executes(context->{
                ServerPlayerEntity player=context.getSource().getPlayerOrThrow();
                if(!arm(player)){context.getSource().sendError(Text.literal("Нужен размещаемый блок Dreamwalker BB в основной руке."));return 0;}
                context.getSource().sendFeedback(()->Text.literal("Следующая установка этого предмета разрешает пересечение блоков. Перемещение предмета сбросит разрешение."),false);return 1;
            }))));
        ServerTickEvents.END_SERVER_TICK.register(server->LEASES.entrySet().removeIf(entry->{
            var player=server.getPlayerManager().getPlayer(entry.getKey());return player==null||!valid(player,entry.getValue());
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->invalidate(handler.player));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server->LEASES.clear());
    }
    public static boolean isModPlacement(ItemStack stack){
        if(stack.isEmpty())return false;
        var id=net.minecraft.registry.Registries.ITEM.getId(stack.getItem());
        if(!Set.of("bloodborne_dw","bloodborne_rp").contains(id.getNamespace()))return false;
        return stack.getItem() instanceof BlockItem||ObjectRegistry.isPlacementItem(stack);
    }
    public static boolean arm(ServerPlayerEntity player){
        if(!isModPlacement(player.getMainHandStack())){invalidate(player);return false;}
        LEASES.put(player.getUuid(),new Lease(player.getMainHandStack(),player.getInventory().selectedSlot));return true;
    }
    private static boolean valid(PlayerEntity player,Lease lease){
        return player.isAlive()&&player.getInventory().selectedSlot==lease.slot&&player.getMainHandStack()==lease.stack&&isModPlacement(lease.stack);
    }
    public static boolean armed(PlayerEntity player){Lease lease=player==null?null:LEASES.get(player.getUuid());return lease!=null&&valid(player,lease);}
    public static void invalidate(PlayerEntity player){if(player!=null)LEASES.remove(player.getUuid());}
    public static boolean ignoringBlocks(){return DEPTH.get()>0;}
    public static boolean owner(UUID uuid){return ignoringBlocks()&&uuid!=null&&uuid.equals(OWNER.get());}
    public static boolean enter(PlayerEntity player){if(!armed(player))return false;DEPTH.set(DEPTH.get()+1);return true;}
    public static void exit(PlayerEntity player,boolean entered,boolean accepted){if(!entered)return;int next=DEPTH.get()-1;if(next<0)throw new IllegalStateException("Unbalanced no-collide placement");DEPTH.set(next);if(accepted)invalidate(player);}
    public static <T> T forOwner(UUID uuid,Supplier<T> operation){UUID before=OWNER.get();OWNER.set(uuid);try{return operation.get();}finally{if(before==null)OWNER.remove();else OWNER.set(before);}}
    public static ActionResult place(ItemPlacementContext context,Supplier<ActionResult> operation){
        boolean entered=context.getHand()==net.minecraft.util.Hand.MAIN_HAND&&enter(context.getPlayer());boolean accepted=false;
        try{ActionResult result=operation.get();accepted=result.isAccepted();return result;}
        finally{exit(context.getPlayer(),entered,accepted);}
    }
}
