package dev.dreamwalker.bloodbornerp.lamp;

import dev.dreamwalker.bloodbornedw.DreamwalkerBb;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;

/** Explicit per-line connections. Schema1 routes are imported without changing direction. */
final class LampState extends PersistentState {
 static final int SCHEMA=2,MAX_NODES=256,MAX_LINES=128,MAX_CONNECTIONS=65536;
 static final String LEGACY_LINE="Старые маршруты V9";
 final Map<UUID,Node> nodes=new LinkedHashMap<>();
 final Map<UUID,Line> lines=new LinkedHashMap<>();
 static LampState get(ServerWorld world){return world.getPersistentStateManager().getOrCreate(LampState::fromNbt,LampState::new,"bloodborne_rp_lamps");}
 Node byEntity(UUID entity){for(Node n:nodes.values())if(n.lamp.equals(entity))return n;return null;}
 Line byName(String name){for(Line line:lines.values())if(line.name.equals(name))return line;return null;}
 Line line(String name,boolean create){Line l=byName(name);if(l!=null||!create||lines.size()>=MAX_LINES||!LampPolicy.validName(name))return l;l=new Line(UUID.randomUUID(),name);lines.put(l.id,l);return l;}
 int connectionCount(){int n=0;for(Line l:lines.values())n+=l.connections.size();return n;}
 Connection connection(Line line,UUID a,UUID b){for(Connection c:line.connections.values())if(c.matches(a,b))return c;return null;}
 boolean connected(UUID from,UUID to){Node n=nodes.get(from);return n!=null&&n.routes.contains(to);}
 void rebuildRoutes(){for(Node n:nodes.values())n.routes.clear();for(Line l:lines.values())for(Connection c:l.connections.values()){Node a=nodes.get(c.a),b=nodes.get(c.b);if(a==null||b==null)continue;if(c.aToB&&!a.routes.contains(b.id))a.routes.add(b.id);if(c.bToA&&!b.routes.contains(a.id))b.routes.add(a.id);}}
 boolean setConnection(String name,UUID from,UUID to,boolean both){
  if(from.equals(to)||!nodes.containsKey(from)||!nodes.containsKey(to)||!LampPolicy.validName(name))return false;
  Line l=byName(name);Connection old=l==null?null:connection(l,from,to);
  if(old==null&&(connectionCount()>=MAX_CONNECTIONS||(l==null&&lines.size()>=MAX_LINES)))return false;
  if(l==null)l=line(name,true);Connection c=old;
  if(c==null){c=new Connection(UUID.randomUUID(),from,to,true,both);l.connections.put(c.id,c);}else if(c.a.equals(from)){c.aToB=true;c.bToA=both;}else{c.bToA=true;c.aToB=both;}
  rebuildRoutes();markDirty();return true;
 }
 boolean unlink(String name,UUID from,UUID to,boolean both){Line l=byName(name);if(l==null)return false;Connection c=connection(l,from,to);if(c==null)return false;boolean changed=both?(c.aToB||c.bToA):(c.a.equals(from)?c.aToB:c.bToA);if(!changed)return false;if(both){c.aToB=false;c.bToA=false;}else if(c.a.equals(from))c.aToB=false;else c.bToA=false;if(!c.aToB&&!c.bToA)l.connections.remove(c.id);rebuildRoutes();markDirty();return true;}
 boolean removeNode(UUID id){if(nodes.remove(id)==null)return false;for(Line l:lines.values())l.connections.values().removeIf(c->c.a.equals(id)||c.b.equals(id));rebuildRoutes();markDirty();return true;}
 static LampState fromNbt(NbtCompound nbt){
  LampState s=new LampState();int schema=nbt.getInt("Schema");if(schema!=1&&schema!=SCHEMA){fault("schema_not_1_or_2");return s;}
  for(NbtElement raw:nbt.getList("Nodes",NbtElement.COMPOUND_TYPE)){
   if(s.nodes.size()>=MAX_NODES){fault("node_limit");break;}NbtCompound t=(NbtCompound)raw;
   try{UUID id=t.getUuid("Id"),lamp=t.getUuid("Lamp");String name=t.getString("Name"),dim=t.getString("Dimension");if(!LampPolicy.validName(name)||dim.length()>128||Identifier.tryParse(dim)==null||s.nodes.containsKey(id)){fault("invalid_duplicate_node_metadata");continue;}Node n=new Node(id,lamp,name,dim,BlockPos.fromLong(t.getLong("Pos")));
    if(t.contains("X",NbtElement.NUMBER_TYPE)&&t.contains("Y",NbtElement.NUMBER_TYPE)&&t.contains("Z",NbtElement.NUMBER_TYPE)){Vec3d p=new Vec3d(t.getDouble("X"),t.getDouble("Y"),t.getDouble("Z"));if(LampPolicy.finite(p.x,p.y,p.z))n.origin=p;else fault("nonfinite_node_position");}
    for(NbtElement e:t.getList("Routes",NbtElement.INT_ARRAY_TYPE)){if(n.routes.size()>=MAX_NODES)break;UUID to=NbtHelper.toUuid(e);if(!to.equals(id)&&!n.routes.contains(to))n.routes.add(to);}s.nodes.put(id,n);
   }catch(RuntimeException bad){fault("malformed_node_uuid_or_routes");}
  }
  if(schema==1){Line legacy=s.line(LEGACY_LINE,true);for(Node n:s.nodes.values())for(UUID to:List.copyOf(n.routes))if(s.nodes.containsKey(to)){
    Connection c=s.connection(legacy,n.id,to);if(c==null){if(s.connectionCount()>=MAX_CONNECTIONS){fault("legacy_connection_limit");break;}c=new Connection(UUID.randomUUID(),n.id,to,true,false);legacy.connections.put(c.id,c);}else if(c.a.equals(n.id))c.aToB=true;else c.bToA=true;
   }s.rebuildRoutes();s.markDirty();return s;}
  for(NbtElement raw:nbt.getList("Lines",NbtElement.COMPOUND_TYPE)){
   if(s.lines.size()>=MAX_LINES){fault("line_limit");break;}NbtCompound t=(NbtCompound)raw;
   try{UUID id=t.getUuid("Id");String name=t.getString("Name");if(!LampPolicy.validName(name)||s.lines.containsKey(id)||s.byName(name)!=null){fault("invalid_duplicate_line");continue;}Line l=new Line(id,name);
    for(NbtElement rawC:t.getList("Connections",NbtElement.COMPOUND_TYPE)){
     if(s.connectionCount()+l.connections.size()>=MAX_CONNECTIONS){fault("connection_limit");break;}NbtCompound e=(NbtCompound)rawC;
     try{Connection c=new Connection(e.getUuid("Id"),e.getUuid("A"),e.getUuid("B"),e.getBoolean("AtoB"),e.getBoolean("BtoA"));if(c.a.equals(c.b)||!s.nodes.containsKey(c.a)||!s.nodes.containsKey(c.b)||(!c.aToB&&!c.bToA)||l.connections.containsKey(c.id)||s.connection(l,c.a,c.b)!=null){fault("invalid_duplicate_connection");continue;}l.connections.put(c.id,c);}catch(RuntimeException bad){fault("malformed_connection_uuid");}
    }s.lines.put(id,l);
   }catch(RuntimeException bad){fault("malformed_line_uuid");}
  }s.rebuildRoutes();return s;
 }
 @Override public NbtCompound writeNbt(NbtCompound nbt){
  nbt.putInt("Schema",SCHEMA);NbtList out=new NbtList();for(Node n:nodes.values()){if(out.size()>=MAX_NODES)break;NbtCompound t=new NbtCompound();t.putUuid("Id",n.id);t.putUuid("Lamp",n.lamp);t.putString("Name",n.name);t.putString("Dimension",n.dimension);t.putLong("Pos",n.pos.asLong());t.putDouble("X",n.origin.x);t.putDouble("Y",n.origin.y);t.putDouble("Z",n.origin.z);NbtList routes=new NbtList();for(UUID to:n.routes){if(routes.size()>=MAX_NODES)break;routes.add(NbtHelper.fromUuid(to));}t.put("Routes",routes);out.add(t);}nbt.put("Nodes",out);
  NbtList linesOut=new NbtList();int count=0;for(Line l:lines.values()){if(linesOut.size()>=MAX_LINES)break;NbtCompound t=new NbtCompound();t.putUuid("Id",l.id);t.putString("Name",l.name);NbtList entries=new NbtList();for(Connection c:l.connections.values()){if(count++>=MAX_CONNECTIONS)break;NbtCompound e=new NbtCompound();e.putUuid("Id",c.id);e.putUuid("A",c.a);e.putUuid("B",c.b);e.putBoolean("AtoB",c.aToB);e.putBoolean("BtoA",c.bToA);entries.add(e);}t.put("Connections",entries);linesOut.add(t);}nbt.put("Lines",linesOut);return nbt;
 }
 private static void fault(String reason){DreamwalkerBb.LOG.warn("Malformed saved lamp data: {}", reason);}
 static final class Node {final UUID id,lamp;String name,dimension;BlockPos pos;Vec3d origin;final List<UUID> routes=new ArrayList<>();Node(UUID id,UUID lamp,String name,String dim,BlockPos pos){this.id=id;this.lamp=lamp;this.name=name;this.dimension=dim;this.pos=pos;this.origin=new Vec3d(pos.getX()+.5,pos.getY(),pos.getZ()+.5);}}
 static final class Line {final UUID id;String name;final Map<UUID,Connection> connections=new LinkedHashMap<>();Line(UUID id,String name){this.id=id;this.name=name;}}
 static final class Connection {final UUID id,a,b;boolean aToB,bToA;Connection(UUID id,UUID a,UUID b,boolean forward,boolean reverse){this.id=id;this.a=a;this.b=b;this.aToB=forward;this.bToA=reverse;}boolean matches(UUID x,UUID y){return a.equals(x)&&b.equals(y)||a.equals(y)&&b.equals(x);}}
}
