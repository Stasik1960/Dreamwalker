package dev.dreamwalker.bloodbornedw.gametest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornerp.mob.MobRegistry;
import dev.dreamwalker.bloodbornerp.mob.RpMobEntity;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

/** Actual NPC tracking callbacks and native packet capture; unrelated boss bars remain active. */
public final class NpcBossBarV127GameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v127_npc_ui",tickLimit=80)
    public void allOwnNpcsHaveNoBossBarButKeepHealthAndUnrelatedUi(TestContext c)throws Exception {
        var world=c.getWorld();var packets=new ArrayList<Packet<?>>();
        var player=new ServerPlayerEntity(world.getServer(),world,new GameProfile(UUID.randomUUID(),"NpcUi127"));
        var connection=new ClientConnection(NetworkSide.SERVERBOUND){
            @Override public void send(Packet<?> packet){packets.add(packet);}
            @Override public void send(Packet<?> packet,PacketCallbacks callbacks){packets.add(packet);}
        };
        player.networkHandler=new ServerPlayNetworkHandler(world.getServer(),connection,player);
        var report=new JsonObject();var rows=new JsonArray();report.add("npcs",rows);
        try {
            c.assertTrue(MobRegistry.TYPES.size()==18,"All 18 actual registered own NPC types participate");
            nativeControl(c,player,packets);packets.clear();
            var pos=c.getAbsolutePos(new BlockPos(3,5,3));
            for(var entry:MobRegistry.TYPES.entrySet()){
                RpMobEntity mob=entry.getValue().create(world),copy=null;
                c.assertTrue(mob!=null,"Actual registered NPC factory creates "+entry.getKey());
                try {
                    mob.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
                    mob.setAiDisabled(true);mob.setFrozen(true);
                    var max=mob.getMaxHealth();c.assertTrue(max==RpMobEntity.maxHealthFor(entry.getKey()),"Original health profile remains active: "+entry.getKey());
                    mob.setHealth(max*.75f);mob.onStartedTrackingBy(player);mob.tick();
                    c.assertTrue(mob.getHealth()==max*.75f&&mob.getMaxHealth()==max,"Tick preserves actual NPC health and max health: "+entry.getKey());
                    mob.setHealth(max*.5f);mob.tick();mob.onStoppedTrackingBy(player);
                    var saved=mob.writeNbt(new NbtCompound());copy=entry.getValue().create(world);copy.readNbt(saved.copy());
                    c.assertTrue(copy.getMaxHealth()==max&&copy.getHealth()==max*.5f&&copy.isFrozen()&&copy.isAiDisabled(),"Native entity save/reload preserves health, freeze and AI settings: "+entry.getKey());
                    copy.onStartedTrackingBy(player);copy.tick();copy.onStoppedTrackingBy(player);
                    c.assertTrue(bossPackets(packets)==0,"This NPC emits no health boss-bar packets before or after native saved reload: "+entry.getKey());
                    var row=new JsonObject();row.addProperty("id",entry.getKey());row.addProperty("maxHealth",max);row.addProperty("savedHealth",copy.getHealth());row.addProperty("bossBarPackets",0);row.addProperty("status","PASS_NATIVE_TRACKING_TICK_AND_ENTITY_NBT_RELOAD");rows.add(row);
                }finally{mob.discard();if(copy!=null)copy.discard();}
            }
            c.assertTrue(bossPackets(packets)==0,"All own NPC paths remain free of boss-bar traffic");
            nativeControl(c,player,packets);
            report.addProperty("status","PASS_ALL_18_OWN_NPC_BOSS_UI_DISABLED");report.addProperty("npcBossBarPackets",0);report.addProperty("unrelatedNativeBossBarAddUpdateRemovePackets",3);report.addProperty("method","Actual registered NPC factories, onStartedTrackingBy/onStoppedTrackingBy, native entity ticks and NBT save/reload; ServerBossBar add/update/remove control through real ServerPlayNetworkHandler");
            Files.writeString(world.getServer().getSavePath(WorldSavePath.ROOT).resolve("v127-npc-boss-ui-native-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));c.complete();
        }finally{player.discard();}
    }
    private static long bossPackets(List<Packet<?>> packets){return packets.stream().filter(packet->packet instanceof BossBarS2CPacket).count();}
    private static void nativeControl(TestContext c,ServerPlayerEntity player,List<Packet<?>> packets){
        long before=bossPackets(packets);var bar=new ServerBossBar(Text.literal("Unrelated native QA bar"),BossBar.Color.PURPLE,BossBar.Style.PROGRESS);
        bar.addPlayer(player);bar.setPercent(.5f);bar.removePlayer(player);
        c.assertTrue(bossPackets(packets)==before+3,"The actual network capture observes unrelated native boss-bar add, health update and removal packets");
    }
}
