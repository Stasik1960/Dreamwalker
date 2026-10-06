package dev.dreamwalker.bloodbornerp.lamp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

final class LampState extends PersistentState {
 static final int SCHEMA=1; final Map<UUID,Node> nodes=new LinkedHashMap<>();
 static LampState get(ServerWorld world){return world.getPersistentStateManager().getOrCreate(LampState::fromNbt,LampState::new,"bloodborne_rp_lamps");}
 static LampState fromNbt(NbtCompound nbt){LampState state=new LampState();if(nbt.getInt("Schema")!=SCHEMA)return state;for(NbtElement raw:nbt.getList("Nodes",NbtElement.COMPOUND_TYPE)){if(state.nodes.size()>=256)break;NbtCompound tag=(NbtCompound)raw;try{UUID id=tag.getUuid("Id"),lamp=tag.getUuid("Lamp");String name=tag.getString("Name");String dimension=tag.getString("Dimension");if(name.isBlank()||name.length()>64||dimension.length()>128||net.minecraft.util.Identifier.tryParse(dimension)==null||state.nodes.containsKey(id))continue;Node node=new Node(id,lamp,name,dimension,BlockPos.fromLong(tag.getLong("Pos")));for(NbtElement route:tag.getList("Routes",NbtElement.INT_ARRAY_TYPE)){if(node.routes.size()>=256)break;UUID routeId=net.minecraft.nbt.NbtHelper.toUuid(route);if(!routeId.equals(id)&&!node.routes.contains(routeId))node.routes.add(routeId);}state.nodes.put(id,node);}catch(Exception ignored){}}return state;}
 @Override public NbtCompound writeNbt(NbtCompound nbt){nbt.putInt("Schema",SCHEMA);NbtList out=new NbtList();for(Node node:nodes.values()){if(out.size()>=256)break;NbtCompound tag=new NbtCompound();tag.putUuid("Id",node.id);tag.putUuid("Lamp",node.lamp);tag.putString("Name",node.name);tag.putString("Dimension",node.dimension);tag.putLong("Pos",node.pos.asLong());NbtList routes=new NbtList();for(UUID route:node.routes){if(routes.size()>=256)break;routes.add(net.minecraft.nbt.NbtHelper.fromUuid(route));}tag.put("Routes",routes);out.add(tag);}nbt.put("Nodes",out);return nbt;}
 static final class Node {final UUID id,lamp;String name,dimension;BlockPos pos;final List<UUID> routes=new ArrayList<>();Node(UUID id,UUID lamp,String name,String dimension,BlockPos pos){this.id=id;this.lamp=lamp;this.name=name;this.dimension=dimension;this.pos=pos;}}
}
