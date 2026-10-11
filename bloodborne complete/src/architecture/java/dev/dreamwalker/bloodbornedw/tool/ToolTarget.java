package dev.dreamwalker.bloodbornedw.tool;

import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.link.MechanismLinks;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

/** Stable owner identity for BB objects; ordinary blocks use their cell and registry ID. */
public record ToolTarget(Kind kind,String dimension,UUID uuid,long root,String registry) {
    public enum Kind { RP, ARCHITECTURE, BLOCK, ENTITY }
    public ToolTarget {
        if(kind==null||Identifier.tryParse(dimension)==null||registry==null||registry.length()>256
            ||kind!=Kind.BLOCK&&uuid==null)throw new IllegalArgumentException("Invalid tool target");
    }
    public String key(){return kind+"|"+dimension+"|"+(kind==Kind.BLOCK?root:uuid);}
    public static ToolTarget entity(Entity entity){
        if(entity instanceof PlayerEntity)return null;
        return new ToolTarget(entity instanceof RpObjectEntity?Kind.RP:Kind.ENTITY,
            entity.getWorld().getRegistryKey().getValue().toString(),entity.getUuid(),entity.getBlockPos().asLong(),
            entity instanceof RpObjectEntity rp?rp.assetId():Registries.ENTITY_TYPE.getId(entity.getType()).toString());
    }
    public static ToolTarget block(ServerWorld world,BlockPos clicked,PlayerEntity player){
        var owner=CompositeRuntime.target(world,clicked,player);
        if(owner==null)owner=CompositeRuntime.soleNativeOwner(world,clicked);
        if(owner!=null)return new ToolTarget(Kind.ARCHITECTURE,world.getRegistryKey().getValue().toString(),owner.instanceId(),CompositeData.pos(owner.root()).asLong(),owner.registryId());
        var state=world.getBlockState(clicked);if(state.isAir())return null;
        var own=VerticalMount.owner(world,clicked);
        if(own!=null)return new ToolTarget(Kind.ARCHITECTURE,world.getRegistryKey().getValue().toString(),own.instanceId(),clicked.asLong(),own.registryId());
        return new ToolTarget(Kind.BLOCK,world.getRegistryKey().getValue().toString(),null,clicked.asLong(),Registries.BLOCK.getId(state.getBlock()).toString());
    }
    public ServerWorld world(MinecraftServer server){return server.getWorld(RegistryKey.of(RegistryKeys.WORLD,new Identifier(dimension)));}
    public Entity entity(MinecraftServer server){var world=world(server);return world==null||uuid==null?null:world.getEntity(uuid);}
    public boolean valid(MinecraftServer server){
        var world=world(server);if(world==null)return false;
        if(kind==Kind.RP||kind==Kind.ENTITY){var entity=entity(server);return entity!=null&&!entity.isRemoved()
            &&(kind==Kind.RP?entity instanceof RpObjectEntity rp&&rp.assetId().equals(registry):Registries.ENTITY_TYPE.getId(entity.getType()).toString().equals(registry));}
        var pos=BlockPos.fromLong(root);if(!world.isChunkLoaded(pos))return false;
        if(kind==Kind.BLOCK)return Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString().equals(registry);
        var owner=VerticalMount.owner(world,pos);return owner!=null&&owner.instanceId().equals(uuid)&&ImportedTypeAliases.equivalent(owner.registryId(),registry);
    }
    public Vec3d position(MinecraftServer server){var entity=entity(server);if(entity!=null)return entity.getPos();var world=world(server);var pos=BlockPos.fromLong(root);return Vec3d.ofBottomCenter(pos).add(0,kind==Kind.ARCHITECTURE&&world!=null?VerticalMount.offset(world,pos):0,0);}
    public MechanismLinks.TargetRef mechanism(){return kind==Kind.RP||kind==Kind.ARCHITECTURE?
        new MechanismLinks.TargetRef(kind==Kind.RP?MechanismLinks.Kind.RP:MechanismLinks.Kind.ARCHITECTURE,dimension,uuid,root,registry):null;}
    public static ToolTarget mechanism(MechanismLinks.TargetRef ref){return new ToolTarget(ref.kind()==MechanismLinks.Kind.RP?Kind.RP:Kind.ARCHITECTURE,ref.dimension(),ref.instance(),ref.root(),ref.registryId());}
    public NbtCompound write(){var n=new NbtCompound();n.putString("Kind",kind.name());n.putString("Dimension",dimension);if(uuid!=null)n.putUuid("UUID",uuid);n.putLong("Root",root);n.putString("Registry",registry);return n;}
    public static ToolTarget read(NbtCompound n){return new ToolTarget(Kind.valueOf(n.getString("Kind")),n.getString("Dimension"),n.containsUuid("UUID")?n.getUuid("UUID"):null,n.getLong("Root"),n.getString("Registry"));}
}
