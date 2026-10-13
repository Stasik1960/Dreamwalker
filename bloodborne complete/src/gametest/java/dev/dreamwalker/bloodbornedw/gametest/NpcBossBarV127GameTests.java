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
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

/** Actual NPC tracking callbacks and native packet capture; unrelated boss bars remain active. */
public final class NpcBossBarV127GameTests implements FabricGameTest {
    private static final Map<RpMobEntity,JsonArray> DAMAGE_CAPTURE=new IdentityHashMap<>();
    public static void captureDamage(RpMobEntity mob,DamageSource source,float amount){
        var events=DAMAGE_CAPTURE.get(mob);if(events==null)return;
        var event=new JsonObject();event.addProperty("type",source.getTypeRegistryEntry().getKey().map(key->key.getValue().toString()).orElse(source.getName()));event.addProperty("amount",amount);event.addProperty("invulnerable",mob.isInvulnerable());
        var stack=new JsonArray();for(var frame:Thread.currentThread().getStackTrace())if(!frame.getClassName().equals(Thread.class.getName())){stack.add(frame.toString());if(stack.size()==14)break;}event.add("actualStack",stack);events.add(event);
    }
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
            var originalPos=c.getAbsolutePos(new BlockPos(3,5,3));
            // NPC dimensions exceed the small shared GameTest structure. Tick
            // health checks must be independent of nearby terrain/fixtures.
            var pos=new BlockPos(originalPos.getX(),world.getTopY()+16,originalPos.getZ());
            var probe=MobRegistry.TYPES.get("cleric_beast").create(world);var probeDamage=new JsonArray();DAMAGE_CAPTURE.put(probe,probeDamage);
            try {
                probe.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);probe.setNoGravity(true);probe.setAiDisabled(true);probe.setFrozen(true);probe.setHealth(probe.getMaxHealth()*.75f);
                var diagnostic=new JsonObject();diagnostic.addProperty("healthBefore",probe.getHealth());diagnostic.addProperty("insideWallBefore",probe.isInsideWall());probe.tick();diagnostic.addProperty("healthAfter",probe.getHealth());diagnostic.add("uncancelledNativeDamageAttempts",probeDamage);report.add("ordinaryNonInvulnerableTickDiagnostic",diagnostic);
            }finally{DAMAGE_CAPTURE.remove(probe);probe.discard();}
            for(var entry:MobRegistry.TYPES.entrySet()){
                RpMobEntity mob=entry.getValue().create(world),copy=null;
                c.assertTrue(mob!=null,"Actual registered NPC factory creates "+entry.getKey());
                try {
                    var row=new JsonObject();row.addProperty("id",entry.getKey());rows.add(row);
                    var observedDamage=new JsonArray();DAMAGE_CAPTURE.put(mob,observedDamage);row.add("uncancelledNativeDamageAttempts",observedDamage);
                    mob.refreshPositionAndAngles(originalPos.getX()+.5,originalPos.getY(),originalPos.getZ()+.5,0,0);
                    row.addProperty("originalFixtureInsideWall",mob.isInsideWall());row.addProperty("originalFixtureY",mob.getY());row.addProperty("originalFixtureEyeBlock",Registries.BLOCK.getId(world.getBlockState(BlockPos.ofFloored(mob.getX(),mob.getEyeY(),mob.getZ())).getBlock()).toString());
                    mob.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
                    mob.setInvulnerable(true);mob.setNoGravity(true);mob.setAiDisabled(true);mob.setFrozen(true);row.addProperty("testOnlyInvulnerableHealthControl",true);
                    c.assertTrue(!mob.isInsideWall()&&!mob.isTouchingWater(),"Native NPC tick fixture is clear of wall/fluid environmental damage: "+entry.getKey());
                    var max=mob.getMaxHealth();c.assertTrue(max==RpMobEntity.maxHealthFor(entry.getKey()),"Original health profile remains active: "+entry.getKey());
                    mob.setHealth(max*.75f);mob.onStartedTrackingBy(player);mob.tick();
                    row.addProperty("difficulty",world.getDifficulty().getName());row.addProperty("isolatedFixtureY",mob.getY());row.addProperty("firstTickExpectedHealth",max*.75f);row.addProperty("firstTickActualHealth",mob.getHealth());row.addProperty("firstTickMaxHealth",mob.getMaxHealth());
                    c.assertTrue(mob.getHealth()==max*.75f&&mob.getMaxHealth()==max,"Tick preserves actual NPC health and max health: "+entry.getKey()+" expectedHealth="+(max*.75f)+" actualHealth="+mob.getHealth()+" maxBefore="+max+" maxAfter="+mob.getMaxHealth()+" insideWall="+mob.isInsideWall()+" difficulty="+world.getDifficulty()+" y="+mob.getY());
                    mob.setHealth(max*.5f);mob.tick();mob.onStoppedTrackingBy(player);
                    var saved=mob.writeNbt(new NbtCompound());copy=entry.getValue().create(world);copy.readNbt(saved.copy());DAMAGE_CAPTURE.put(copy,observedDamage);
                    c.assertTrue(copy.getMaxHealth()==max&&copy.getHealth()==max*.5f&&copy.isFrozen()&&copy.isAiDisabled()&&copy.isInvulnerable(),"Native entity save/reload preserves exact controlled health, freeze, AI and fixture invulnerability settings: "+entry.getKey());
                    copy.onStartedTrackingBy(player);copy.tick();copy.onStoppedTrackingBy(player);
                    c.assertTrue(bossPackets(packets)==0,"This NPC emits no health boss-bar packets before or after native saved reload: "+entry.getKey());
                    row.addProperty("maxHealth",max);row.addProperty("savedHealth",copy.getHealth());row.addProperty("bossBarPackets",0);row.addProperty("status","PASS_NATIVE_TRACKING_TICK_AND_ENTITY_NBT_RELOAD");
                }finally{DAMAGE_CAPTURE.remove(mob);mob.discard();if(copy!=null){DAMAGE_CAPTURE.remove(copy);copy.discard();}}
            }
            c.assertTrue(bossPackets(packets)==0,"All own NPC paths remain free of boss-bar traffic");
            nativeControl(c,player,packets);
            report.addProperty("status","PASS_ALL_18_OWN_NPC_BOSS_UI_DISABLED");report.addProperty("npcBossBarPackets",0);report.addProperty("unrelatedNativeBossBarAddUpdateRemovePackets",3);report.addProperty("damageCaptureChangesOrCancelsDamage",false);report.addProperty("testOnlyInvulnerableHealthControl",true);report.addProperty("method","Actual registered NPC factories, onStartedTrackingBy/onStoppedTrackingBy, native entity ticks and NBT save/reload; test-only invulnerable health controls; non-cancelling native damage capture plus ordinary non-invulnerable diagnostic; ServerBossBar add/update/remove control through real ServerPlayNetworkHandler");
            Files.writeString(world.getServer().getSavePath(WorldSavePath.ROOT).resolve("v127-npc-boss-ui-native-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));c.complete();
        }catch(Exception|Error failure){report.addProperty("status","FAIL_NATIVE_NPC_UI_DIAGNOSTIC");report.addProperty("failure",failure.toString());Files.writeString(world.getServer().getSavePath(WorldSavePath.ROOT).resolve("v127-npc-boss-ui-native-diagnostic.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));throw failure;}finally{player.discard();}
    }
    private static long bossPackets(List<Packet<?>> packets){return packets.stream().filter(packet->packet instanceof BossBarS2CPacket).count();}
    private static void nativeControl(TestContext c,ServerPlayerEntity player,List<Packet<?>> packets){
        long before=bossPackets(packets);var bar=new ServerBossBar(Text.literal("Unrelated native QA bar"),BossBar.Color.PURPLE,BossBar.Style.PROGRESS);
        bar.addPlayer(player);bar.setPercent(.5f);bar.removePlayer(player);
        c.assertTrue(bossPackets(packets)==before+3,"The actual network capture observes unrelated native boss-bar add, health update and removal packets");
    }
}
