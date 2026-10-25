package dev.dreamwalker.bloodbornedw.composite;
import net.minecraft.nbt.*;
import net.minecraft.nbt.scanner.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import com.sun.management.ThreadMXBean;
import java.util.*;
public final class NbtMembershipProbe {
 record Projection(MembershipNbtReader.Metadata metadata,MembershipNbtReader.Rows members,MembershipNbtReader.Rows shapes,Set<Integer> wanted){}
 static Projection filtered(Path p)throws Exception {
  try(var input=Files.newByteChannel(p,StandardOpenOption.READ)){
   var metadata=MembershipNbtReader.metadata(input);NbtCompound n=metadata.tag();Set<Integer> wanted=new HashSet<>();
   var rows=MembershipNbtReader.members(input,n.getList("ownerMembership",10).isEmpty()?Set.of():Set.of(0));
   if(n.getBoolean("Dictionary")&&!rows.entries().isEmpty()){var members=rows.entries().get(0).getList("members",10);for(int i=0;i<members.size();i++)wanted.add(members.getCompound(i).getInt("shapeRef"));}
   return new Projection(metadata,rows,MembershipNbtReader.shapes(input,wanted),wanted);
  }
 }
 public static void main(String[] args)throws Exception {
  Path source=Path.of(args[0]),out=Path.of(args[1]);
  NbtIo.readCompressed(source.toFile());Path warm=out.resolve("empty.nbt");NbtIo.writeCompressed(new NbtCompound(),warm.toFile());filtered(warm);filtered(source);
  var mx=(ThreadMXBean)ManagementFactory.getThreadMXBean();long id=Thread.currentThread().getId();
  long start=mx.getThreadAllocatedBytes(id),time=System.nanoTime();NbtCompound all=NbtIo.readCompressed(source.toFile());long oldTime=System.nanoTime()-time,oldBytes=mx.getThreadAllocatedBytes(id)-start;
  start=mx.getThreadAllocatedBytes(id);time=System.nanoTime();Projection projection=filtered(source);NbtCompound selected=projection.metadata().tag();long newTime=System.nanoTime()-time,newBytes=mx.getThreadAllocatedBytes(id)-start;
  String layout="{\"dictionary\":"+all.getBoolean("Dictionary")+",\"cells\":"+all.getList("cells",10).size()+",\"owners\":"+all.getList("ownerTable",10).size()+",\"shapes\":"+all.getList("shapeTable",10).size()+",\"memberships\":"+all.getList("ownerMembership",10).size()+"}\n";Files.writeString(out.resolve("nbt-layout.json"),layout);
  StringBuilder fields=new StringBuilder("field,encodedBytes\n");for(String key:all.getKeys()){var single=new NbtCompound();single.put(key,all.get(key));var bytes=new ByteArrayOutputStream();NbtIo.write(single,new DataOutputStream(bytes));fields.append(key).append(',').append(bytes.size()).append('\n');}Files.writeString(out.resolve("nbt-fields.csv"),fields);
  var shapes=all.getList("shapeTable",10);for(int ref:projection.wanted())if(!shapes.getCompound(ref).equals(projection.shapes().entries().get(ref)))throw new AssertionError("Selected shape changed");
  if(!projection.wanted().isEmpty()&&projection.shapes().size()!=shapes.size())throw new AssertionError("Shape reference bound changed");
  var allMembers=all.getList("ownerMembership",10);
  if(!projection.members().entries().isEmpty()&&!allMembers.getCompound(0).equals(projection.members().entries().get(0)))throw new AssertionError("Selected owner mask changed");
  for(int i=0;i<allMembers.size();i++){var row=allMembers.getCompound(i);if(row.getList("members",10).size()!=projection.metadata().memberCounts().get(i))throw new AssertionError("Unrequested owner budget changed");row.put("members",new NbtList());}
  all.remove("cells");all.remove("dataTable");all.remove("shapeTable");if(!all.equals(selected))throw new AssertionError("Retained membership fields changed");
  if(selected.contains("cells")||selected.contains("dataTable"))throw new AssertionError("Ignored trees retained");
  Files.writeString(out.resolve("nbt-probe.json"),"{\"status\":\"PASS_EXACT_RETAINED_FIELDS\",\"fullReadBytes\":"+oldBytes+",\"filteredReadBytes\":"+newBytes+",\"fullReadNs\":"+oldTime+",\"filteredReadNs\":"+newTime+"}\n");
 }
}
