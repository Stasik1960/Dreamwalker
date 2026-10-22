package dev.dreamwalker.bloodbornedw.conversion;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Fixture-only fourth phase. Absolute input rows are author operation data,
 * never saved as a game registry or as per-part block entity NBT. */
final class StaticArchitectureMigration {
    record Prepared(StaticArchitectureWorldAccess access,TransactionCore.Plan plan,boolean alreadyPresent,int owners,int cells){}
    private static void need(boolean v,String message){if(!v)throw new IllegalStateException(message);}
    private static Cell cell(JsonArray a){need(a.size()==3,"Static source coordinate");return new Cell(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    private static String part(String pose,Cell origin){return pose+"/"+origin.x()+","+origin.y()+","+origin.z();}
    static Prepared prepare(ServerWorld world,JsonObject row){
        need(world.getServer().isOnThread(),"Static migration thread");
        TreeMap<Cell,Integer> placements=new TreeMap<>();
        for(var raw:row.getAsJsonArray("cells")){
            var c=raw.getAsJsonObject();Cell at=cell(c.getAsJsonArray("position"));int index=c.get("recipe").getAsInt();
            need(placements.put(at,index)==null,"Repeated static cell");StaticArchitectureCatalog.state(index);
        }
        need(!placements.isEmpty()&&placements.size()<=8192,"Static component cell budget");
        var access=new StaticArchitectureWorldAccess(world,placements.keySet(),true,0);
        boolean all=true,any=false;
        for(var entry:placements.entrySet()){
            need(access.isLoaded(entry.getKey())&&access.inBounds(entry.getKey()),"Static migration would load an unbounded chunk");
            BlockPos p=CompositeData.pos(entry.getKey());boolean match=world.getBlockState(p).equals(StaticArchitectureCatalog.state(entry.getValue()));
            all&=match;any|=match;
            if(match)need(world.getBlockEntity(p)==null&&CompositeLedger.get(world).at(entry.getKey()).isEmpty(),"Static replay contains service data");
        }
        int ownerCount=row.getAsJsonArray("owners").size();need(ownerCount>0&&ownerCount<=4096,"Static owner budget");
        if(all)return new Prepared(access,null,true,ownerCount,placements.size());
        need(!any,"Partially migrated component must not be guessed or repaired");
        Map<Cell,Set<Owner>> expectedMembers=new TreeMap<>();Map<Cell,Set<String>> expectedParts=new TreeMap<>();
        Set<Owner> owners=new HashSet<>();
        for(var raw:row.getAsJsonArray("owners")){
            var source=raw.getAsJsonObject();String poseKey=source.get("poseKey").getAsString();var pose=StaticArchitectureCatalog.pose(poseKey);
            Cell root=cell(source.getAsJsonArray("position"));var owner=new Owner(UUID.fromString(source.get("instanceUuid").getAsString()),source.get("registryId").getAsString(),root);
            need(owners.add(owner)&&placements.containsKey(root),"Repeated or omitted static source root");
            BlockPos pos=CompositeData.pos(root);need(world.getBlockState(pos).equals(pose.state()),"Static source pose changed");
            need(world.getBlockEntity(pos) instanceof CompositeBlockEntity,"Static source has no actual owner entity");
            var entity=(CompositeBlockEntity)world.getBlockEntity(pos);need(owner.equals(entity.resident()),"Static source identity mismatch");
            var payload=entity.payload();var allowed=new HashSet<>(pose.payload().getKeys());allowed.add("SourceShift");allowed.add("MountY");
            need(allowed.containsAll(payload.getKeys())&&payload.contains(ImportedTypeAliases.VERSION_KEY,NbtElement.INT_TYPE)
                    &&payload.getInt(ImportedTypeAliases.VERSION_KEY)==ImportedTypeAliases.VERSION,"Unreviewed functional/private source payload");
            var shift=CompositeSourceShift.local(payload);var wanted=pose.shift();
            need(shift.x()==wanted.x()&&shift.y()==wanted.y()&&shift.z()==wanted.z()&&payload.getDouble("MountY")==pose.mountY(),"Static source anchoring changed");
            for(var relative:pose.footprint().keySet()){
                Cell at=root.add(relative);need(placements.containsKey(at),"Static migration omits a helper cell");
                expectedMembers.computeIfAbsent(at,k->new HashSet<>()).add(owner);
                expectedParts.computeIfAbsent(at,k->new HashSet<>()).add(part(poseKey,root.subtract(at)));
            }
        }
        need(expectedMembers.keySet().equals(placements.keySet()),"Static recipe contains extraneous cells");
        TreeMap<Cell,CellSnapshot> expected=new TreeMap<>(),writes=new TreeMap<>();
        for(var item:placements.entrySet()){
            Cell at=item.getKey();CellSnapshot before=access.read(at);expected.put(at,before);
            need(before.kind()==CellSnapshot.Kind.ROOT||before.kind()==CellSnapshot.Kind.HELPER||before.kind()==CellSnapshot.Kind.AIR,"Static migration may not replace foreign blocks");
            Set<Owner> observed=new HashSet<>(before.guests());if(before.resident()!=null)observed.add(before.resident());
            need(observed.equals(expectedMembers.get(at)),"Static migration touches a nonselected or missing owner");
            if(before.kind()==CellSnapshot.Kind.HELPER)need(CompositeData.nbt(before.data().blockEntityNbt()).isEmpty(),"Helper has private payload");
            var recipe=StaticArchitectureCatalog.recipes().get(item.getValue());Set<String> parts=new HashSet<>();
            for(var fragment:recipe.fragments()){need(fragment.member(),"Unexpected visual-only static fragment");parts.add(part(fragment.pose().key(),fragment.origin()));}
            need(parts.equals(expectedParts.get(at))&&parts.size()==recipe.fragments().size(),"Finite static recipe does not match current source component");
            for(var contribution:CompositeRuntime.contributions(world,CompositeData.pos(at))){
                var matching=row.getAsJsonArray("owners"); // Validate actual persisted per-cell geometry against its shared pose.
                String poseKey=null;
                for(var raw:matching){var source=raw.getAsJsonObject();if(source.get("instanceUuid").getAsString().equals(contribution.owner().instanceId().toString())){poseKey=source.get("poseKey").getAsString();break;}}
                need(poseKey!=null,"Nonselected persisted contribution");
                var pose=StaticArchitectureCatalog.pose(poseKey);
                need(Objects.equals(contribution.shape(),pose.footprint().get(at.subtract(contribution.owner().root()))),"Current source helper geometry changed");
            }
            writes.put(at,new CellSnapshot(CellSnapshot.Kind.FOREIGN,CompositeData.data(StaticArchitectureCatalog.state(item.getValue()),new NbtCompound()),null,List.of()));
        }
        return new Prepared(access,new TransactionCore.Plan(access.dimensionKey(),expected,writes,Map.of()),false,ownerCount,placements.size());
    }
    private StaticArchitectureMigration(){}
}
