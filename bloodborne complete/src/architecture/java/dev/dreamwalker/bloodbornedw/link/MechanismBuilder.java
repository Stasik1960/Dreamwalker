package dev.dreamwalker.bloodbornedw.link;

import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.*;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;

/** The selected source belongs to this tool stack. All targets and edits are checked again by the server. */
public final class MechanismBuilder {
    private static final String SOURCE="BuilderLever";
    private static boolean initialized;
    private MechanismBuilder(){}
    public static void initialize(){if(initialized)return;initialized=true;UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
        if(!BuildingTool.isHeld(player))return ActionResult.PASS;
        if(hand==net.minecraft.util.Hand.MAIN_HAND&&BuildingTool.mainHeld(player)&&!world.isClient&&player instanceof net.minecraft.server.network.ServerPlayerEntity server)dev.dreamwalker.bloodbornedw.tool.BuilderServer.open(server);
        return ActionResult.SUCCESS;
    });}
    public static boolean connectionAction(BuildingTool.Action action){return action==BuildingTool.Action.LINK||action==BuildingTool.Action.CONNECTIONS||action==BuildingTool.Action.UNLINK||action==BuildingTool.Action.CANCEL;}
    public static void cancel(PlayerEntity player,ItemStack stack){if(stack.hasNbt())stack.getNbt().remove(SOURCE);player.getInventory().markDirty();message(player,"Выбор рычага отменён.");}
    public static RpObjectEntity selected(PlayerEntity player,ItemStack stack){
        if(!(player.getWorld() instanceof ServerWorld world)||!stack.hasNbt()||!stack.getNbt().contains(SOURCE,NbtElement.COMPOUND_TYPE))return null;
        NbtCompound tag=stack.getNbt().getCompound(SOURCE);try{
            if(!tag.getString("Dimension").equals(world.getRegistryKey().getValue().toString()))return null;Entity entity=world.getEntity(tag.getUuid("Instance"));
            if(entity instanceof RpObjectEntity lever&&lever.isMechanism()&&lever.assetId().equals(tag.getString("Asset"))&&!lever.isRemoved()&&lever.squaredDistanceTo(player)<=512d*512d&&BuildPermissions.canEdit(world,player,lever.getBlockPos()))return lever;
        }catch(IllegalArgumentException ignored){}return null;
    }
    public static ActionResult entity(PlayerEntity player,ItemStack stack,RpObjectEntity target){
        return entity(player,stack,target,false);
    }
    public static ActionResult entity(PlayerEntity player,ItemStack stack,RpObjectEntity target,boolean reverse){
        if(!BuildPermissions.canEdit(player.getWorld(),player,target.getBlockPos())||!inReach(player,target))return ActionResult.FAIL;
        BuildingTool.Action action=BuildingTool.action(stack);
        if(action==BuildingTool.Action.DIAGNOSTICS){var snapshot=dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.snapshot((ServerWorld)player.getWorld(),player,target,Map.of("note","tool snapshot"));message(player,label(target)+" · диагностический снимок сохранён, объект не изменён.");return ActionResult.CONSUME;}
        if(action==BuildingTool.Action.CANCEL){cancel(player,stack);return ActionResult.CONSUME;}
        if(action==BuildingTool.Action.CONNECTIONS){if(target.isMechanism())view(player,target);else if(target.canBeLinked())incoming(player,MechanismLinks.TargetRef.rp(target));else message(player,label(target)+" · статический объект, активная связь не поддерживается.");return ActionResult.CONSUME;}
        if(action==BuildingTool.Action.LINK||action==BuildingTool.Action.UNLINK){
            if(target.isMechanism()){NbtCompound source=new NbtCompound();source.putUuid("Instance",target.getUuid());source.putString("Dimension",target.getWorld().getRegistryKey().getValue().toString());source.putString("Asset",target.assetId());source.putLong("Pos",target.getBlockPos().asLong());stack.getOrCreateNbt().put(SOURCE,source);player.getInventory().markDirty();message(player,"Выбран рычаг "+label(target)+". Нажмите активный объект для "+(action==BuildingTool.Action.LINK?"связи.":"разрыва связи."));return ActionResult.CONSUME;}
            RpObjectEntity source=selected(player,stack);if(source==null){message(player,"Сначала выберите загруженный рычаг этим инструментом.");return ActionResult.FAIL;}
            if(!target.canBeLinked()){message(player,label(target)+" · статический объект, связь не поддерживается.");return ActionResult.FAIL;}
            if(action==BuildingTool.Action.LINK&&MechanismLinks.targets(source).stream().anyMatch(ref->ref.key().equals(MechanismLinks.TargetRef.rp(target).key()))){message(player,"Уже связано. Подключено целей: "+MechanismLinks.targets(source).size());return ActionResult.CONSUME;}
            boolean done=action==BuildingTool.Action.LINK?source.addLink(target):source.removeLink(target.getUuid());
            message(player,(done?"Готово: ":"Не изменено: ")+label(source)+" → "+label(target)+(action==BuildingTool.Action.UNLINK?" · связь удалена":" · связь"));return done?ActionResult.CONSUME:ActionResult.FAIL;
        }
        if(action==BuildingTool.Action.DOGS){if(!target.supportsDogVisibility()){message(player,label(target)+" · нет отдельного отображения собаки.");return ActionResult.FAIL;}target.setDogsVisible(!target.dogsVisible());message(player,label(target)+" · собака "+(target.dogsVisible()?"видна":"скрыта"));return ActionResult.CONSUME;}
        if(action==BuildingTool.Action.ROTATE){boolean changed=target.rotateByBuilder(player,target.getYaw()+(reverse?-45:45));message(player,label(target)+(changed?" · поворот "+(reverse?-45:45)+"°":" · поворот отклонён: физическое препятствие или права"));return changed?ActionResult.CONSUME:ActionResult.FAIL;}
        message(player,label(target)+" · действие "+action.label+" для этого RP-объекта не поддерживается.");return ActionResult.FAIL;
    }
    public static ActionResult architecture(PlayerEntity player,ItemStack stack,MechanismLinks.TargetRef target){
        BuildingTool.Action action=BuildingTool.action(stack);if(action==BuildingTool.Action.CANCEL){cancel(player,stack);return ActionResult.CONSUME;}
        if(!(player.getWorld() instanceof ServerWorld world))return ActionResult.SUCCESS;
        var status=MechanismLinks.inspect(world.getServer(),target);if(status.availability()!=MechanismLinks.Availability.LOADED){message(player,"Этот объект не поддерживает активную связь.");return ActionResult.FAIL;}
        if(action==BuildingTool.Action.CONNECTIONS){incoming(player,target);return ActionResult.CONSUME;}
        RpObjectEntity source=selected(player,stack);if(source==null){message(player,"Сначала выберите загруженный рычаг этим инструментом.");return ActionResult.FAIL;}
        if(action==BuildingTool.Action.LINK&&MechanismLinks.targets(source).stream().anyMatch(ref->ref.key().equals(target.key()))){message(player,"Уже связано. Подключено целей: "+MechanismLinks.targets(source).size());return ActionResult.CONSUME;}
        boolean done=action==BuildingTool.Action.LINK?MechanismLinks.link(source,target):action==BuildingTool.Action.UNLINK&&MechanismLinks.unlink(source,target.key());
        message(player,(done?"Готово: ":"Не изменено: ")+label(source)+" → "+label(target)+(action==BuildingTool.Action.UNLINK?" · связь удалена":" · связь"));return done?ActionResult.CONSUME:ActionResult.FAIL;
    }
    public static void view(PlayerEntity player,RpObjectEntity lever){
        List<MechanismLinks.TargetRef> targets=MechanismLinks.targets(lever);message(player,label(lever)+" · связей="+targets.size());
        if(player.getWorld() instanceof ServerWorld world)for(var target:targets){var status=MechanismLinks.inspect(world.getServer(),target);player.sendMessage(Text.literal("→ "+label(target)+" · "+status.availability()+" · открыто="+status.open()+" · ожидает="+MechanismLinks.pending(world.getServer(),target)),false);}
    }
    private static void incoming(PlayerEntity player,MechanismLinks.TargetRef target){if(!(player.getWorld() instanceof ServerWorld world))return;var sources=MechanismLinks.sources(world.getServer(),target);var status=MechanismLinks.inspect(world.getServer(),target);message(player,label(target)+" · рычагов="+sources.size()+" · открыто="+status.open()+" · ожидает="+MechanismLinks.pending(world.getServer(),target));for(var source:sources)player.sendMessage(Text.literal("← "+DebugCatalogue.prefix(new Identifier("bloodborne_rp",source.asset()))+" · UUID="+source.instance()+" · "+source.dimension()+" · root="+net.minecraft.util.math.BlockPos.fromLong(source.root()).toShortString()),false);}
    private static String label(RpObjectEntity entity){return DebugCatalogue.prefix(Registries.ENTITY_TYPE.getId(entity.getType()))+" · UUID="+entity.getUuid();}
    private static String label(MechanismLinks.TargetRef target){Identifier id=target.kind()==MechanismLinks.Kind.RP?new Identifier("bloodborne_rp",target.registryId().isEmpty()?"unknown":target.registryId()):new Identifier(target.registryId());return DebugCatalogue.prefix(id)+" · "+target.instance()+(target.kind()==MechanismLinks.Kind.ARCHITECTURE?" · root="+net.minecraft.util.math.BlockPos.fromLong(target.root()).toShortString():"");}
    private static void message(PlayerEntity player,String text){player.sendMessage(Text.literal(text),true);}
    private static boolean inReach(PlayerEntity player,RpObjectEntity entity){var eye=player.getEyePos();for(var box:entity.selectionBoxes()){double dx=Math.max(Math.max(box.minX-eye.x,eye.x-box.maxX),0),dy=Math.max(Math.max(box.minY-eye.y,eye.y-box.maxY),0),dz=Math.max(Math.max(box.minZ-eye.z,eye.z-box.maxZ),0);if(dx*dx+dy*dy+dz*dz<=64)return true;}return false;}
}
