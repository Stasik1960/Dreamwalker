package dev.dreamwalker.bloodbornedw.remainingqa;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

/** Opt-in ordinary client interaction packets, or read-only rejoin verification,
 * in a SHA-bound fresh test world.
 * This is engine/network acceptance, never physical mouse/keyboard acceptance. */
final class RemainingTransferNetworkQa {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private Path output;
    private final JsonObject report=new JsonObject();
    private final JsonArray evidence=new JsonArray();
    private final JsonArray cases;
    private JsonObject current,row;
    private CompositeRootBlock block;
    private BlockPos root,copy,target;
    private ItemStack picked;
    private String originalUuid,copyUuid,phase="START",next;
    private int index,rotationIndex,variant,yaw,ticks,settled;
    private int[] rotations;
    private double[] camera;
    private BlockHitResult hit;
    private boolean done,verifyRejoin;

    RemainingTransferNetworkQa(MinecraftClient client,Path gameDir,String bound)throws Exception {
        output=gameDir.resolve("remaining-transfer-client-network.json");
        Path markerPath=gameDir.resolve("remaining-transfer-network-input.json");
        if(!Files.isRegularFile(markerPath,LinkOption.NOFOLLOW_LINKS)){cases=new JsonArray();done=true;return;}
        require(Files.size(markerPath)<=1048576,"Bounded network marker required");
        var marker=JsonParser.parseString(Files.readString(markerPath)).getAsJsonObject();
        String mode=marker.has("mode")?marker.get("mode").getAsString():"INTERACT";
        require(mode.equals("INTERACT")||mode.equals("VERIFY_REJOIN"),"Explicit supported network QA mode required");
        verifyRejoin=mode.equals("VERIFY_REJOIN");
        if(verifyRejoin)output=gameDir.resolve("remaining-transfer-client-rejoin.json");
        require(!Files.exists(output),"Preserving existing network/rejoin evidence; use fresh client directory");
        require(bound.equals(marker.get("productionJarSha256").getAsString()),"Network marker production SHA mismatch");
        require(marker.get("freshTestWorldOnly").getAsBoolean(),"Network actions require explicit fresh-test-world marker");
        cases=marker.getAsJsonArray(verifyRejoin?"expectedCases":"cases");require(cases!=null&&cases.size()>0&&cases.size()<=16,"Network scene requires 1..16 cases");
        Set<BlockPos> positions=new HashSet<>();
        for(var entry:cases){var value=entry.getAsJsonObject();require(value.get("registryId").getAsString().startsWith("bloodborne_dw:source_"),"Only new source objects allowed");require(positions.add(pos(value,"root")),"Distinct new test stations required");if(verifyRejoin){UUID.fromString(value.get("expectedOwnerUuid").getAsString());require(value.get("rotation").getAsInt()>=0&&value.get("rotation").getAsInt()<8,"Exact rejoin rotation required");value.get("open").getAsBoolean();}else require(positions.add(pos(value,"copyRoot")),"Distinct new copy stations required");}
        report.addProperty("schema",verifyRejoin?"dreamwalker-remaining-transfer-client-rejoin-v1":"dreamwalker-remaining-transfer-client-network-v1");report.addProperty("productionJarSha256",bound);report.addProperty("mode",mode);
        report.addProperty("manualMouseKeyboardInput","NOT_RUN");report.addProperty("scope",verifyRejoin?"Read-only actual client synchronized owner UUID/id/variant/rotation/open after server restart; camera tp only; no place/use/pick/attack":"Actual MinecraftClientInteractionManager interactBlock/attackBlock and actual MinecraftClient doItemPick, localhost server acknowledgement by synced owner UUID; no Windows input injection or source world");
        if(verifyRejoin&&marker.has("firstNetworkReportSha256"))report.add("firstNetworkReportSha256",marker.get("firstNetworkReportSha256").deepCopy());
        report.add("cases",evidence);write("WAITING_FOR_WORLD");
    }
    boolean complete(){return done;}
    void tick(MinecraftClient client) {
        if(done||client.player==null||client.world==null||client.currentScreen!=null||client.isPaused())return;
        try {
            require(++ticks<=800,"Network stage timed out: "+phase+" case="+index);
            if(ticks%40==0)persist();
            switch(phase) {
                case "START" -> {
                    current=cases.get(index).getAsJsonObject();root=pos(current,"root");variant=current.get("variant").getAsInt();
                    var registered=Registries.BLOCK.get(new Identifier(current.get("registryId").getAsString()));require(registered instanceof CompositeRootBlock,"Registered new composite required");block=(CompositeRootBlock)registered;
                    require(variant>=0&&variant<block.spec.variants.size(),"Actual source variant required");
                    row=new JsonObject();row.addProperty("case",current.get("case").getAsString());row.addProperty("id",block.spec.id.toString());row.addProperty("variant",variant);row.add("operations",new JsonArray());row.addProperty("status","RUNNING");evidence.add(row);
                    if(verifyRejoin){row.add("root",current.get("root").deepCopy());row.addProperty("expectedOwnerUuid",current.get("expectedOwnerUuid").getAsString());row.addProperty("expectedRotation",current.get("rotation").getAsInt());row.addProperty("expectedOpen",current.get("open").getAsBoolean());navigate(client,placement(root,0),"VERIFY_REJOIN");break;}
                    copy=pos(current,"copyRoot");
                    require(client.world.getBlockState(root).isAir()&&client.world.getBlockState(copy).isAir(),"Fresh empty root/copy stations required; no overwrites");
                    rotations=index<4?new int[]{0,1,2,3,4,5,6,7}:new int[]{0};rotationIndex=0;yaw=rotations[0];
                    navigate(client,placement(root,yaw),"EQUIP_INITIAL");
                }
                case "NAVIGATE" -> {
                    settled=at(client,camera)?settled+1:0;if(settled>=12)stage(next);
                }
                case "VERIFY_REJOIN" -> {
                    String owner=ack(client,root,current.get("rotation").getAsInt(),current.get("open").getAsBoolean());if(owner==null)return;
                    require(owner.equals(current.get("expectedOwnerUuid").getAsString()),"Rejoined server owner UUID differs from first network PASS");record(client,"synced_server_rejoin_owner_state",owner,root);row.addProperty("actualOwnerUuid",owner);row.addProperty("actualRotation",current.get("rotation").getAsInt());row.addProperty("actualOpen",current.get("open").getAsBoolean());row.addProperty("status","PASS");write("RUNNING");
                    if(++index==cases.size()){done=true;write("PASS");}else stage("START");
                }
                case "EQUIP_INITIAL" -> {equip(client,art(false));stage("PLACE_INITIAL");}
                case "PLACE_INITIAL" -> {if(ticks<8)return;place(client,root);stage("WAIT_INITIAL");}
                case "WAIT_INITIAL" -> {
                    String uuid=ack(client,root,yaw,false);if(uuid==null)return;originalUuid=uuid;record(client,"server_ack_initial",uuid,root);
                    if(rotationIndex+1<rotations.length)aim(client,root,"BREAK_ROTATION");
                    else if(block.spec.openable)aim(client,root,"OPEN_ORIGINAL");else aim(client,root,"PICK");
                }
                case "BREAK_ROTATION" -> {attack(client);stage("WAIT_ROTATION_REMOVED");}
                case "WAIT_ROTATION_REMOVED" -> {
                    if(ticks<12||!client.world.getBlockState(root).isAir())return;record(client,"remove_before_rotation",originalUuid,root);yaw=rotations[++rotationIndex];navigate(client,placement(root,yaw),"EQUIP_INITIAL");
                }
                case "OPEN_ORIGINAL" -> {equip(client,ItemStack.EMPTY);stage("OPEN_PACKET");}
                case "OPEN_PACKET" -> {if(ticks<8)return;interact(client);stage("WAIT_OPEN");}
                case "WAIT_OPEN" -> {if(ack(client,root,yaw,true)==null)return;require(originalUuid.equals(uuid(client,root)),"Opening retains server owner UUID");record(client,"open_ack",originalUuid,root);aim(client,root,"PICK");}
                case "PICK" -> {
                    client.crosshairTarget=hit;client.targetedEntity=null;
                    String method=FabricLoader.getInstance().getMappingResolver().mapMethodName("intermediary","net.minecraft.class_310","method_1511","()V");
                    var pickMethod=MinecraftClient.class.getDeclaredMethod(method);pickMethod.setAccessible(true);pickMethod.invoke(client);
                    picked=client.player.getMainHandStack().copy();require(picked.isOf(block.asItem()),"Actual vanilla doItemPick resolves exact composite item");
                    var tags=picked.getSubNbt("BlockStateTag");require(tags!=null&&tags.getString("variant").equals(Integer.toString(variant)),"Native pick retains exact variant");
                    require(picked.getSubNbt("CompositePayload")==null&&picked.getSubNbt("BlockEntityTag")==null,"Native pick is art only");
                    if(block.spec.openable)require(tags.getString("open").equals("true"),"Pick retains open art");record(client,"actual_doItemPick",originalUuid,root);
                    if(block.spec.openable)aim(client,root,"CLOSE_ORIGINAL");else {yaw=(yaw+2)%8;navigate(client,placement(copy,yaw),"EQUIP_COPY");}
                }
                case "CLOSE_ORIGINAL" -> {equip(client,ItemStack.EMPTY);stage("CLOSE_PACKET");}
                case "CLOSE_PACKET" -> {if(ticks<8)return;interact(client);stage("WAIT_CLOSED");}
                case "WAIT_CLOSED" -> {if(ack(client,root,yaw,false)==null)return;require(originalUuid.equals(uuid(client,root)),"Closing retains server owner UUID");record(client,"close_ack",originalUuid,root);yaw=(yaw+2)%8;navigate(client,placement(copy,yaw),"EQUIP_COPY");}
                case "EQUIP_COPY" -> {equip(client,picked);stage("PLACE_COPY");}
                case "PLACE_COPY" -> {if(ticks<8)return;place(client,copy);stage("WAIT_COPY");}
                case "WAIT_COPY" -> {String uuid=ack(client,copy,yaw,block.spec.openable);if(uuid==null)return;copyUuid=uuid;require(!uuid.equals(originalUuid),"Copied art has a fresh server UUID");record(client,"server_ack_copy",uuid,copy);aim(client,root,"BREAK_ORIGINAL");}
                case "BREAK_ORIGINAL" -> {attack(client);stage("WAIT_ORIGINAL_REMOVED");}
                case "WAIT_ORIGINAL_REMOVED" -> {if(ticks<12||!client.world.getBlockState(root).isAir())return;record(client,"remove_original_ack",originalUuid,root);aim(client,copy,"BREAK_COPY");}
                case "BREAK_COPY" -> {attack(client);stage("WAIT_COPY_REMOVED");}
                case "WAIT_COPY_REMOVED" -> {if(ticks<12||!client.world.getBlockState(copy).isAir())return;record(client,"remove_copy_ack",copyUuid,copy);yaw=0;navigate(client,placement(root,yaw),"EQUIP_FINAL");}
                case "EQUIP_FINAL" -> {equip(client,art(false));stage("PLACE_FINAL");}
                case "PLACE_FINAL" -> {if(ticks<8)return;place(client,root);stage("WAIT_FINAL");}
                case "WAIT_FINAL" -> {
                    String uuid=ack(client,root,0,false);if(uuid==null)return;require(!uuid.equals(originalUuid)&&!uuid.equals(copyUuid),"Reusing removed station creates another server UUID");record(client,"retained_for_save_rejoin",uuid,root);
                    row.addProperty("retainedOwnerUuid",uuid);row.add("root",current.get("root").deepCopy());row.addProperty("retainedRotation",0);row.addProperty("retainedOpen",false);row.addProperty("status","PASS");write("RUNNING");
                    if(++index==cases.size()){done=true;write("PASS");}else stage("START");
                }
                default -> throw new IllegalStateException("Unknown network QA stage");
            }
        } catch(Throwable failure) {done=true;report.addProperty("failure",failure.toString());if(row!=null)row.addProperty("status","FAIL");try{write("FAIL");}catch(Exception ignored){}failure.printStackTrace();}
    }
    private void stage(String value){phase=value;ticks=0;settled=0;persist();}
    private void persist(){try{write("RUNNING");}catch(Exception failure){throw new IllegalStateException("Cannot preserve QA progress",failure);}}
    private void navigate(MinecraftClient client,double[] view,String after) {
        if(!verifyRejoin&&!client.player.getAbilities().flying){require(client.player.getAbilities().allowFlying,"Creative test navigation requires actual flying ability");client.player.getAbilities().flying=true;client.player.sendAbilitiesUpdate();record(client,"creative_flight_navigation_packet",null,root);}
        camera=view;next=after;client.player.networkHandler.sendChatCommand("tp @s "+view[0]+" "+view[1]+" "+view[2]+" "+view[3]+" "+view[4]);stage("NAVIGATE");
    }
    private void aim(MinecraftClient client,BlockPos at,String after) {
        var state=client.world.getBlockState(at);require(state.isOf(block),"Source owner state is available for native selection");
        for(var entry:block.spec.footprint(state,0).entrySet())if(!entry.getValue().selection().isEmpty()) {
            var box=entry.getValue().selection().get(0);var cell=at.add(entry.getKey().x(),entry.getKey().y(),entry.getKey().z());
            double x=cell.getX()+(box.minX()+box.maxX())/2,y=cell.getY()+(box.minY()+box.maxY())/2,z=cell.getZ()+box.minZ();
            double feetY=Math.max(at.getY(),y-1.62),pitch=Math.toDegrees(Math.atan2(feetY+1.62-y,2));
            hit=new BlockHitResult(new Vec3d(x,y,z),Direction.NORTH,cell,false);target=at;navigate(client,new double[]{x,feetY,z-2,0,pitch},after);return;
        }
        throw new IllegalStateException("No visible selectable source part");
    }
    private ItemStack art(boolean open){var state=block.getDefaultState().with(CompositeRootBlock.VARIANT,variant).with(CompositeRootBlock.OPEN,open);return block.art(state,new NbtCompound());}
    private void equip(MinecraftClient client,ItemStack stack) {client.player.getInventory().selectedSlot=0;client.player.getInventory().setStack(0,stack.copy());client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(0));client.interactionManager.clickCreativeStack(stack.copy(),36);}
    private void place(MinecraftClient client,BlockPos at) {var floor=new BlockHitResult(Vec3d.ofCenter(at.down()).add(0,.5,0),Direction.UP,at.down(),false);require(client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,floor).isAccepted(),"Ordinary client block placement packet accepted locally");record(client,"interactBlock_place_packet",null,at);}
    private void interact(MinecraftClient client){require(client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,hit).isAccepted(),"Ordinary client block use accepted locally");record(client,"interactBlock_use_packet",null,target);}
    private void attack(MinecraftClient client){require(client.interactionManager.attackBlock(hit.getBlockPos(),hit.getSide()),"Ordinary client attackBlock packet accepted locally");record(client,"attackBlock_packet",null,target);}
    private String ack(MinecraftClient client,BlockPos at,int expectedYaw,boolean open){var state=client.world.getBlockState(at);if(!state.isOf(block)||state.get(CompositeRootBlock.VARIANT)!=variant||state.get(CompositeRootBlock.ROTATION)!=expectedYaw||state.get(CompositeRootBlock.OPEN)!=open)return null;return uuid(client,at);}
    private static String uuid(MinecraftClient client,BlockPos at){return client.world.getBlockEntity(at) instanceof CompositeBlockEntity entity&&entity.resident()!=null?entity.resident().instanceId().toString():null;}
    private void record(MinecraftClient client,String operation,String uuid,BlockPos at){var value=new JsonObject();value.addProperty("operation",operation);value.addProperty("clientWorldTick",client.world.getTime());value.add("at",JSON.toJsonTree(List.of(at.getX(),at.getY(),at.getZ())));if(uuid!=null)value.addProperty("ownerUuid",uuid);row.getAsJsonArray("operations").add(value);persist();}
    private void write(String status)throws Exception{report.addProperty("status",status);report.addProperty("stage",phase);report.addProperty("caseIndex",index);report.addProperty("stageTicks",ticks);report.addProperty("updatedUtc",Instant.now().toString());Files.writeString(output,JSON.toJson(report));}
    private static BlockPos pos(JsonObject row,String key){var values=row.getAsJsonArray(key);require(values!=null&&values.size()==3,"Three integer coordinates required");for(var n:values)require(n.getAsDouble()==n.getAsInt()&&Math.abs(n.getAsInt())<=10000,"Bounded exact test coordinates required");return new BlockPos(values.get(0).getAsInt(),values.get(1).getAsInt(),values.get(2).getAsInt());}
    private static double[] placement(BlockPos at,int yaw){double angle=Math.toRadians(yaw*45),x=at.getX()+.5+3*Math.sin(angle),z=at.getZ()+.5-3*Math.cos(angle);return new double[]{x,at.getY(),z,yaw*45,Math.toDegrees(Math.atan2(1.62,3))};}
    private static boolean at(MinecraftClient client,double[] camera){double dx=client.player.getX()-camera[0],dy=client.player.getY()-camera[1],dz=client.player.getZ()-camera[2];return dx*dx+dy*dy+dz*dz<.0025&&Math.abs(MathHelper.wrapDegrees(client.player.getYaw()-(float)camera[3]))<.5&&Math.abs(client.player.getPitch()-camera[4])<.5;}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
