package dev.dreamwalker.bloodbornedw.debug;

import java.util.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.*;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import dev.dreamwalker.bloodbornerp.mob.RpMobEntity;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;

/** Read-only six-block target inspection. No NBT/name/registry/UUID mutations. */
public final class CatalogueDebug {
    private CatalogueDebug(){}
    private static String bounds(dev.dreamwalker.bloodbornedw.runtime.ObjectInstance object,boolean selection){
        if(object==null)return "unavailable";
        double[] low={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
        double[] high={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};int count=0;
        for(var cell:object.cells().entrySet())for(var box:selection?cell.getValue().selection():cell.getValue().collision()){
            double[] from={object.owner().root().x()+cell.getKey().x()+box.minX(),object.owner().root().y()+cell.getKey().y()+box.minY(),object.owner().root().z()+cell.getKey().z()+box.minZ()};
            double[] to={object.owner().root().x()+cell.getKey().x()+box.maxX(),object.owner().root().y()+cell.getKey().y()+box.maxY(),object.owner().root().z()+cell.getKey().z()+box.maxZ()};
            for(int axis=0;axis<3;axis++){low[axis]=Math.min(low[axis],from[axis]);high[axis]=Math.max(high[axis],to[axis]);}count++;
        }
        return count==0?"empty":Arrays.toString(low)+" -> "+Arrays.toString(high)+" (summary only, no filled AABB)";
    }
    public static Entity entityTarget(ServerPlayerEntity player,double maxSquared){
        Vec3d start=player.getEyePos(),end=start.add(player.getRotationVec(1).multiply(6));
        Entity selected=null;double distance=maxSquared;
        for(Entity entity:player.getWorld().getOtherEntities(player,player.getBoundingBox().stretch(end.subtract(start)).expand(1),
                entity->(entity instanceof RpObjectEntity||entity instanceof RpMobEntity)&&entity.isAlive()&&!entity.isSpectator()&&entity.canHit())){
            var hit=entity.getBoundingBox().expand(entity.getTargetingMargin()).raycast(start,end);
            double actual=entity.getBoundingBox().contains(start)?0:hit.map(pos->pos.squaredDistanceTo(start)).orElse(Double.POSITIVE_INFINITY);
            if(actual<distance||(actual==distance&&selected!=null&&entity.getUuid().compareTo(selected.getUuid())<0)){distance=actual;selected=entity;}
        }
        return selected;
    }
    public static int inspect(ServerCommandSource source){
        if(!(source.getEntity() instanceof ServerPlayerEntity player)){source.sendError(Text.literal("Для /bb debug нужен игрок."));return 0;}
        HitResult ray=player.raycast(6,0,false);boolean blockHit=ray instanceof BlockHitResult&&ray.getType()==HitResult.Type.BLOCK;
        double limit=blockHit?ray.getPos().squaredDistanceTo(player.getEyePos()):36;
        Entity entity=entityTarget(player,limit);
        if(entity!=null){Identifier id=Registries.ENTITY_TYPE.getId(entity.getType());String behavior=entity instanceof RpObjectEntity object
                ?"open="+object.isOpen()+", locked="+object.isLocked()+", scale="+object.objectScale()+", art="+object.assetId()
                :"art="+((RpMobEntity)entity).assetId()+", AI_disabled="+((RpMobEntity)entity).isAiDisabled();
            String message=DebugCatalogue.prefix(id)+"; name="+entity.getName().getString()+"; registry="+id+"; instance UUID="+entity.getUuid()
                +"; pos="+entity.getPos()+"; yaw="+entity.getYaw()+"; pitch="+entity.getPitch()+"; "+behavior
                +"; BASE/ALT=RP native (no architecture profile); main=self; physics bounds="+entity.getBoundingBox()+"; isCollidable="+entity.isCollidable()
                +"; selection=entity bounds "+entity.getBoundingBox()+" + targeting margin "+entity.getTargetingMargin();
            source.sendFeedback(()->Text.literal(message),false);return 1;
        }
        if(!blockHit){source.sendError(Text.literal("В пределах6 блоков нет доступного объекта каталога."));return 0;}
        BlockPos hit=((BlockHitResult)ray).getBlockPos();var world=player.getWorld();if(!world.isChunkLoaded(hit))return 0;
        BlockState carrier=world.getBlockState(hit);var owner=CompositeRuntime.targetReadOnly(world,hit,player);
        if(owner!=null){BlockPos root=CompositeData.pos(owner.root());BlockState state=world.getBlockState(root);
            if(!(state.getBlock() instanceof CompositeRootBlock block))return 0;
            Identifier id=new Identifier(owner.registryId());var pose=block.spec.pose(state);
            var object=world.getBlockEntity(root) instanceof CompositeBlockEntity be
                ?CompositeRuntime.instance(root,state,owner.instanceId(),be.payload()):null;
            int cells=object==null?-1:object.cells().size();
            long collisionFragments=object==null?-1:object.cells().values().stream().mapToLong(cell->cell.collision().size()).sum();
            long selectionFragments=object==null?-1:object.cells().values().stream().mapToLong(cell->cell.selection().size()).sum();
            boolean mounted=state.getEntries().entrySet().stream().anyMatch(property->property.getKey().getName().equals("mount")&&!property.getValue().toString().equalsIgnoreCase("vertical"));
            int geometryCollision=mounted?1:pose.collision().size(),geometrySelection=mounted?1:pose.selection().size();
            String alternatives=CompositeRuntime.targetsReadOnly(world,hit,player).stream().map(candidate->{BlockPos candidateRoot=CompositeData.pos(candidate.root());BlockState candidateState=world.isChunkLoaded(candidateRoot)?world.getBlockState(candidateRoot):null;String prefix=candidateState!=null&&candidateState.getBlock() instanceof CompositeRootBlock?DebugCatalogue.prefix(candidateState):DebugCatalogue.prefix(new Identifier(candidate.registryId()));return prefix+" UUID="+candidate.instanceId()+" root="+candidateRoot.toShortString();}).toList().toString();
            var entry=DebugCatalogue.entry(state);
            String message=DebugCatalogue.prefix(state)+"; name="+(entry==null?id:entry.name())+"; registry="+id+"; state="+state
                +"; root="+root.toShortString()+"; hit="+hit.toShortString()+"; hit carrier="+Registries.BLOCK.getId(carrier.getBlock())
                +"; affiliation="+(hit.equals(root)?"main root":"technical/foreign carrier contribution")+"; owner UUID="+owner.instanceId()
                +"; BASE/ALT="+state.get(CompositeRootBlock.PROFILE).asString()+"; active geometry physical/selection volumes="+geometryCollision+"/"+geometrySelection
                +"; vertical authored physical/selection volumes="+pose.collision().size()+"/"+pose.selection().size()
                +"; cached per-cell collision/selection fragments="+collisionFragments+"/"+selectionFragments
                +"; owned collision world bounds="+bounds(object,false)+"; owned selection world bounds="+bounds(object,true)
                +"; sparse owned cells="+cells+"; overlap candidates="+alternatives;
            source.sendFeedback(()->Text.literal(message),false);return 1;
        }
        BlockPos ladderRoot=SourceLadderRuntime.resolveRoot(world,hit);BlockPos root=ladderRoot==null?hit:ladderRoot;
        BlockState state=world.getBlockState(root);Identifier id=Registries.BLOCK.getId(state.getBlock());
        boolean light=id.toString().equals("bloodborne_dw:source_hunter_lamp_light");Identifier type=light?new Identifier("bloodborne_rp","hunterlamp"):id;
        var entry=light?DebugCatalogue.entry(type):DebugCatalogue.entry(state);if(entry==null){source.sendError(Text.literal("Нет зарегистрированного пользовательского типа: "+id));return 0;}
        String affiliation=world.getBlockEntity(root) instanceof SourceLadderBlockEntity sourceLadder
            ?"source ladder pair owner="+sourceLadder.owner()+", main="+sourceLadder.root().toShortString()+", fixed backing="+sourceLadder.backing().toShortString()
            :light?"technical lamp light; associated type=hunterlamp; no instance UUID stored":"main=self";
        String message=(light?DebugCatalogue.prefix(type):DebugCatalogue.prefix(state))+"; name="+entry.name()+"; registry="+id+"; state="+state+"; root="+root.toShortString()
            +"; hit="+hit.toShortString()+"; hit carrier="+Registries.BLOCK.getId(carrier.getBlock())+"; "+affiliation
            +"; BASE/ALT="+state.getEntries().entrySet().stream().filter(e->e.getKey().getName().equals("profile")).map(e->e.getValue().toString()).findFirst().orElse("native")
            +"; physical volumes="+state.getCollisionShape(world,root).getBoundingBoxes().size()
            +"; selection volumes="+state.getOutlineShape(world,root).getBoundingBoxes().size()+"; registered states="+state.getBlock().getStateManager().getStates().size();
        source.sendFeedback(()->Text.literal(message),false);return 1;
    }
}
