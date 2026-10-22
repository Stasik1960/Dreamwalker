package dev.dreamwalker.bloodbornerp.lamp;

import dev.dreamwalker.bloodbornedw.DreamwalkerBb;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;

/** One persistent, bidirectional partner per lamp. Old isolated pairs are migrated. */
final class LampState extends PersistentState {
 static final int SCHEMA=3,MAX_NODES=256;
 final Map<UUID,Node> nodes=new LinkedHashMap<>();
 static LampState get(ServerWorld world){return world.getPersistentStateManager().getOrCreate(LampState::fromNbt,LampState::new,"bloodborne_rp_lamps");}
 Node byEntity(UUID entity){for(Node n:nodes.values())if(n.lamp.equals(entity))return n;return null;}
 Node partner(Node node){Node partner=node==null?null:nodes.get(node.partner);return partner!=null&&node.id.equals(partner.partner)?partner:null;}
 Set<UUID> removePair(UUID entity){Node n=byEntity(entity);if(n==null)return Set.of();Node other=partner(n);Set<UUID> removed=new LinkedHashSet<>();removed.add(n.id);if(other!=null)removed.add(other.id);removed.forEach(nodes::remove);markDirty();return removed;}
 static LampState fromNbt(NbtCompound nbt){
  LampState s=new LampState();int schema=nbt.getInt("Schema");if(schema<1||schema>SCHEMA){fault("unsupported_schema");return s;}
  Map<UUID,Set<UUID>> routes=new LinkedHashMap<>();Set<UUID> entities=new HashSet<>();
  for(NbtElement raw:nbt.getList("Nodes",NbtElement.COMPOUND_TYPE)){
   if(s.nodes.size()>=MAX_NODES){fault("node_limit");break;}NbtCompound t=(NbtCompound)raw;
   try{UUID id=t.getUuid("Id"),lamp=t.getUuid("Lamp");String dim=t.getString("Dimension");if(dim.length()>128||Identifier.tryParse(dim)==null||s.nodes.containsKey(id)||!entities.add(lamp)){fault("invalid_duplicate_node");continue;}Node n=new Node(id,lamp,dim,BlockPos.fromLong(t.getLong("Pos")));
    if(t.contains("X",NbtElement.NUMBER_TYPE)&&t.contains("Y",NbtElement.NUMBER_TYPE)&&t.contains("Z",NbtElement.NUMBER_TYPE)){Vec3d p=new Vec3d(t.getDouble("X"),t.getDouble("Y"),t.getDouble("Z"));if(LampPolicy.finite(p.x,p.y,p.z))n.origin=p;else fault("nonfinite_node_position");}
    if(schema==SCHEMA&&t.containsUuid("Partner"))n.partner=t.getUuid("Partner");
    Set<UUID> destinations=new LinkedHashSet<>();for(NbtElement e:t.getList("Routes",NbtElement.INT_ARRAY_TYPE)){if(destinations.size()>=MAX_NODES)break;destinations.add(NbtHelper.toUuid(e));}routes.put(id,destinations);s.nodes.put(id,n);
   }catch(RuntimeException bad){fault("malformed_node");}
  }
  if(schema==2){routes.values().forEach(Set::clear);int connections=0;for(NbtElement raw:nbt.getList("Lines",NbtElement.COMPOUND_TYPE))for(NbtElement entry:((NbtCompound)raw).getList("Connections",NbtElement.COMPOUND_TYPE)){
   if(++connections>65536){fault("legacy_connection_limit");break;}try{NbtCompound t=(NbtCompound)entry;UUID a=t.getUuid("A"),b=t.getUuid("B");if(!a.equals(b)&&routes.containsKey(a)&&routes.containsKey(b)){if(t.getBoolean("AtoB"))routes.get(a).add(b);if(t.getBoolean("BtoA"))routes.get(b).add(a);}}catch(RuntimeException bad){fault("malformed_legacy_connection");}
  }}
  if(schema<SCHEMA){
   // Only an isolated two-way pair has the new meaning. Multi-destination/one-way networks disappear.
   Map<UUID,Set<UUID>> neighbours=new HashMap<>();routes.forEach((a,dest)->dest.forEach(b->{if(s.nodes.containsKey(b)&&!a.equals(b)){neighbours.computeIfAbsent(a,k->new HashSet<>()).add(b);neighbours.computeIfAbsent(b,k->new HashSet<>()).add(a);}}));
   for(Node n:s.nodes.values()){Set<UUID> adjacent=neighbours.getOrDefault(n.id,Set.of());if(adjacent.size()!=1)continue;UUID b=adjacent.iterator().next();if(neighbours.getOrDefault(b,Set.of()).size()==1&&routes.getOrDefault(n.id,Set.of()).contains(b)&&routes.getOrDefault(b,Set.of()).contains(n.id))n.partner=b;}
   s.markDirty();
  }
  Set<UUID> invalid=new HashSet<>();for(Node n:s.nodes.values())if(n.partner==null||n.id.equals(n.partner)||s.partner(n)==null)invalid.add(n.id);invalid.forEach(s.nodes::remove);return s;
 }
 @Override public NbtCompound writeNbt(NbtCompound nbt){nbt.putInt("Schema",SCHEMA);NbtList out=new NbtList();for(Node n:nodes.values()){NbtCompound t=new NbtCompound();t.putUuid("Id",n.id);t.putUuid("Lamp",n.lamp);t.putUuid("Partner",n.partner);t.putString("Dimension",n.dimension);t.putLong("Pos",n.pos.asLong());t.putDouble("X",n.origin.x);t.putDouble("Y",n.origin.y);t.putDouble("Z",n.origin.z);out.add(t);}nbt.put("Nodes",out);return nbt;}
 private static void fault(String reason){DreamwalkerBb.LOG.warn("Malformed saved lamp pair: {}",reason);}
 static final class Node {final UUID id,lamp;UUID partner;String dimension;BlockPos pos;Vec3d origin;Node(UUID id,UUID lamp,String dim,BlockPos pos){this.id=id;this.lamp=lamp;this.dimension=dim;this.pos=pos;this.origin=new Vec3d(pos.getX()+.5,pos.getY(),pos.getZ()+.5);}}
}
