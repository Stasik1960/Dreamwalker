package dev.dreamwalker.bloodbornedw.tool;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.link.MechanismLinks;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import java.util.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

/** Actual aliases, typed item reads, saved ledger and command identity through canonicalization. */
public final class ImportedTypeCompatibilityGameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=200,batchId="v124_type_compatibility")
    public void everyMappingKeepsTypedItemDataAndCanonicalReadIsIdempotent(TestContext c) {
        c.assertTrue(!ImportedTypeAliases.mappings().isEmpty(), "Release contains reviewed state equivalences");
        for (var mapping : ImportedTypeAliases.mappings()) {
            ItemStack old = new ItemStack(Registries.ITEM.get(new Identifier(mapping.oldRegistryId())), 7);
            NbtCompound metadata = new NbtCompound(); metadata.putIntArray("numbers", new int[]{1, -2, 3});
            metadata.putDouble("exact", .375); metadata.putUuid("private_uuid", UUID.randomUUID());
            old.getOrCreateNbt().put("UnrelatedTypedMetadata", metadata);
            NbtCompound art = old.getOrCreateSubNbt("BlockStateTag"); art.putString("variant", Integer.toString(mapping.oldVariant()));
            art.putString("profile", "alt"); art.putString("open", "true"); art.putString("rotation", "5");
            NbtCompound oldNbt = old.getNbt().copy();
            ItemStack canonical = ImportedTypeAliases.canonicalStack(old);
            c.assertTrue(old.getNbt().equals(oldNbt), "Item conversion never edits its caller's NBT: " + mapping.oldId());
            c.assertTrue(canonical.getCount() == 7 && Registries.ITEM.getId(canonical.getItem()).toString().equals(mapping.newRegistryId()), "Canonical actual item and count: " + mapping.oldId());
            c.assertTrue(canonical.getNbt().getCompound("UnrelatedTypedMetadata").equals(metadata), "Typed unrelated item fields retained: " + mapping.oldId());
            boolean nativeStair = Registries.BLOCK.get(new Identifier(mapping.newRegistryId())) instanceof ImportedStairBlock;
            if (nativeStair) c.assertTrue(canonical.getNbt().getString("ImportedStairLegacyArt").equals(mapping.oldRegistryId() + "|" + mapping.oldVariant()), "Legacy source pose/UV selector survives item canonicalization: " + mapping.oldId());
            NbtCompound nextArt = canonical.getSubNbt("BlockStateTag");
            c.assertTrue(nextArt.getString("variant").equals(Integer.toString(mapping.newVariant())) && nextArt.getString("profile").equals("alt") && nextArt.getString("open").equals("true"), "Mapped artistic state retains profile and pose: " + mapping.oldId());
            c.assertTrue(nextArt.getString("rotation").equals(Integer.toString((5 + mapping.rotationDelta()) & 7)), "Saved artistic rotation mapped: " + mapping.oldId());
            for (var property : mapping.properties().entrySet()) c.assertTrue(nextArt.getString(property.getKey()).equals(property.getValue()), "Explicit artistic state property preserved: " + mapping.oldId() + "/" + property.getKey());
            c.assertTrue(ImportedTypeAliases.canonicalStack(canonical) == canonical, "Canonical item conversion is an identity operation: " + mapping.oldId());
            NbtCompound encoded = old.writeNbt(new NbtCompound());
            ItemStack loaded = ItemStack.fromNbt(encoded);
            // The unchanged V12.4 artistic-alias stage still produces the
            // precise former fragment above. Native reads then apply the
            // separately approved V12.6 whole-object installation policy.
            ItemStack expectedRead = expectedFunctionalRead(canonical);
            c.assertTrue(ItemStack.areEqual(loaded, expectedRead), "Native ItemStack.fromNbt applies exact source aliases followed by the independently approved functional installer: " + mapping.oldId());
            c.assertTrue(ItemStack.areEqual(loaded,ItemStack.fromNbt(loaded.writeNbt(new NbtCompound()))),"Native whole-object item read remains idempotent: "+mapping.oldId());
        }
        for (var id : Registries.BLOCK.getIds()) if (!id.getPath().startsWith("source_")) {
            BlockState old = Registries.BLOCK.get(id).getDefaultState();
            c.assertTrue(ImportedTypeAliases.target(old) == old && ImportedTypeAliases.canonicalRegistry(id.toString()).equals(id.toString()), "Original V12/vanilla registry untouched: " + id);
        }
        c.complete();
    }

    private record Installer(String registry,int pose) {}
    /** QA-only independent V12.5-approved composition manifest, rather than
     * asking the production canonicalStack implementation to predict itself. */
    private static Map<String,Installer> approvedInstallers() {
        var input=ImportedTypeCompatibilityGameTests.class.getResourceAsStream("/bloodborne_dw/v126-assembly-expectations.json");
        if(input==null)throw new IllegalStateException("Missing approved V12.6 installer reference");
        Map<String,Installer> result=new HashMap<>();Map<String,Installer> numbers=new HashMap<>();
        try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
            for(var raw:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("assemblies")) {
                var row=raw.getAsJsonObject();var installer=new Installer(row.get("primaryRegistryId").getAsString(),row.get("installationPose").getAsInt());
                result.put(installer.registry,installer);numbers.put(row.get("primaryId").getAsString(),installer);
            }
        }catch(Exception failure){throw new IllegalStateException("Invalid approved V12.6 installer reference",failure);}
        for(var entry:Map.of("93069","93066","90178","90179","90251","90301","90142","90143","90799","90797","90623","90031","90479","90001").entrySet()) {
            String old=dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.entries().stream().filter(row->row.temporaryId().equals(entry.getKey())).findFirst().orElseThrow().registryId().toString();
            Installer target=entry.getValue().equals("90001")?new Installer("bloodborne_dw:prototype_double_door",0):numbers.get(entry.getValue());
            if(target==null)throw new IllegalStateException("Missing primary installer "+entry.getValue());result.put(old,target);
        }
        return Map.copyOf(result);
    }
    private static final Map<String,Installer> INSTALLERS=approvedInstallers();
    private static ItemStack expectedFunctionalRead(ItemStack artAlias) {
        var installer=INSTALLERS.get(Registries.ITEM.getId(artAlias.getItem()).toString());if(installer==null)return artAlias;
        var expected=new ItemStack(Registries.ITEM.get(new Identifier(installer.registry)),artAlias.getCount());expected.setNbt(artAlias.getNbt().copy());
        var tag=expected.getOrCreateSubNbt("BlockStateTag");tag.putString("variant",Integer.toString(installer.pose%16));if(installer.pose>=16)tag.putString("art_bank",Integer.toString(installer.pose/16));else tag.remove("art_bank");
        expected.getOrCreateNbt().putInt("ImportedAssemblyItemVersion",1);return expected;
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=200,batchId="v124_type_compatibility")
    public void everyYawMapsSourceShiftOnceWithoutChangingInstancePayload(TestContext c) {
        for (var mapping : ImportedTypeAliases.mappings()) for (int yaw = 0; yaw < 8; yaw++) {
            CompositeRootBlock block = (CompositeRootBlock)Registries.BLOCK.get(new Identifier(mapping.oldRegistryId()));
            BlockState old = block.getDefaultState().with(CompositeRootBlock.VARIANT, mapping.oldVariant()).with(CompositeRootBlock.ROTATION, yaw).with(CompositeRootBlock.PROFILE, CompositeRootBlock.Profile.ALT);
            NbtCompound payload = new NbtCompound(); payload.putDouble("VerticalOffset", 1.375); payload.putDouble("MountY", -.125);
            payload.putBoolean("MountEdited", true); payload.putLong("CommandLimit", 9123L);
            NbtCompound settings = new NbtCompound(); settings.putString("event", "on_use"); settings.putUuid("link", UUID.randomUUID()); payload.put("OpaqueSettings", settings);
            NbtList shift = new NbtList(); shift.add(NbtDouble.of(.375)); shift.add(NbtDouble.of(1.125)); shift.add(NbtDouble.of(-.625)); payload.put("SourceShift", shift);
            NbtCompound original = payload.copy();
            BlockState canonical = ImportedTypeAliases.target(old); NbtCompound converted = ImportedTypeAliases.transformPayload(old, payload);
            if (canonical.getBlock() instanceof ImportedStairBlock) c.assertTrue(converted.getString("ImportedStairLegacyArt").equals(mapping.oldRegistryId() + "|" + mapping.oldVariant()), "Every legacy yaw retains its original authored geometry/UV selector: " + mapping.oldId());
            c.assertTrue(Registries.BLOCK.getId(canonical.getBlock()).toString().equals(mapping.newRegistryId()) && canonical.get(CompositeRootBlock.ROTATION) == ((yaw + mapping.rotationDelta()) & 7), "Canonical block and yaw: " + mapping.oldId() + "/" + yaw);
            CompositeSourceShift.Shift expected = new CompositeSourceShift.Shift(.375, 1.125, -.625).rotate(-mapping.rotationDelta());
            CompositeSourceShift.Shift actual = CompositeSourceShift.local(converted), adjustment = mapping.sourceShift();
            c.assertTrue(close(actual.x(), expected.x() + adjustment.x()) && close(actual.y(), expected.y() + adjustment.y()) && close(actual.z(), expected.z() + adjustment.z()), "Local canonical shift keeps the former world anchor: " + mapping.oldId() + "/" + yaw);
            c.assertTrue(payload.equals(original) && converted.getDouble("VerticalOffset") == 1.375 && converted.getDouble("MountY") == -.125 && converted.getBoolean("MountEdited") && converted.getLong("CommandLimit") == 9123L && converted.getCompound("OpaqueSettings").equals(settings), "Entire unrelated typed instance payload retained: " + mapping.oldId());
            c.assertTrue(!ImportedTypeAliases.needsMigration(canonical, converted), "Canonical saved state cannot be converted twice: " + mapping.oldId());
        }
        c.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=240,batchId="v124_saved_alias")
    public void savedOwnerMigrationRetainsUuidCellsAndOldCommandLimits(TestContext c) {
        var world = c.getWorld(); var server = world.getServer();
        var mapping = ImportedTypeAliases.mappings().stream().filter(m -> !m.oldRegistryId().equals(m.newRegistryId()))
                .filter(m -> !(Registries.BLOCK.get(new Identifier(m.newRegistryId())) instanceof ImportedStairBlock))
                .filter(m -> ((CompositeRootBlock)Registries.BLOCK.get(new Identifier(m.oldRegistryId()))).spec.footprint(((CompositeRootBlock)Registries.BLOCK.get(new Identifier(m.oldRegistryId()))).getDefaultState().with(CompositeRootBlock.VARIANT, m.oldVariant()), 0).size() <= 16)
                .findFirst().orElseThrow(() -> new IllegalStateException("Need a small real decorative legacy alias fixture"));
        CompositeRootBlock oldBlock = (CompositeRootBlock)Registries.BLOCK.get(new Identifier(mapping.oldRegistryId()));
        BlockState old = oldBlock.getDefaultState().with(CompositeRootBlock.VARIANT, mapping.oldVariant()).with(CompositeRootBlock.ROTATION, 2);
        BlockPos root = c.getAbsolutePos(new BlockPos(4, 25, 4)); UUID uuid = UUID.randomUUID();
        NbtCompound payload = new NbtCompound(); payload.putDouble("VerticalOffset", .375); payload.putBoolean("MountEdited", true);
        payload.putString("CustomEvent", "retained"); payload.putUuid("LinkedInstance", UUID.randomUUID());
        var legacy = CompositeRuntime.instance(world, root, old, uuid, payload);
        var placement = new FabricCompositeWorld(world, Map.of(legacy.owner(), legacy));
        c.assertTrue(CompositeRuntime.execute(placement, CompositeRuntime.core().placement(placement, legacy, List.of())).outcome() == TransactionCore.Outcome.COMMITTED, "Install real saved-style alias owner without placement canonicalization");
        var oldTarget = new ToolTarget(ToolTarget.Kind.ARCHITECTURE, world.getRegistryKey().getValue().toString(), uuid, root.asLong(), mapping.oldRegistryId());
        var manager = server.getOverworld().getPersistentStateManager(); ToolCommands originalCommands = ToolCommands.get(server);
        ServerPlayerEntity spent = player(c), fresh = player(c);
        String objective = "dd" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        command(c, "scoreboard objectives add " + objective + " dummy");
        try {
            NbtCompound saved = new NbtCompound(), binding = new NbtCompound(); NbtList bindings = new NbtList();
            binding.put("Target", oldTarget.write()); binding.putString("Commands", "scoreboard players add retained " + objective + " 1");
            binding.putInt("PersonalLimit", 1); binding.putInt("GlobalLimit", 3); binding.putInt("GlobalUsed", 1);
            NbtList uses = new NbtList(); NbtCompound used = new NbtCompound(); used.putUuid("UUID", spent.getUuid()); used.putInt("Used", 1); uses.add(used); binding.put("Players", uses); bindings.add(binding); saved.put("Entries", bindings);
            manager.set("bloodborne_dw_tool_commands", ToolCommands.read(saved)); ToolCommands.get(server);
            // Re-index these fixture bytes exactly as a fresh process reads the saved ledger.
            ImportedTypeMigration.forget(world); ImportedCollisionMigration.forget(world);
            ImportedTypeMigration.loadedRoot(world, legacy.owner());
            c.assertTrue(world.getBlockEntity(root) instanceof CompositeBlockEntity, "Canonical root BE exists");
            CompositeBlockEntity migrated = (CompositeBlockEntity)world.getBlockEntity(root);
            c.assertTrue(migrated.resident().instanceId().equals(uuid) && migrated.resident().root().equals(legacy.owner().root()) && migrated.resident().registryId().equals(mapping.newRegistryId()), "UUID and root retained, actual registry canonicalized");
            NbtCompound expected = ImportedTypeAliases.transformPayload(old, payload);
            c.assertTrue(migrated.payload().equals(expected), "Typed instance settings and mount offset retained by the actual transaction");
            var canonical = CompositeRuntime.instance(world, root, world.getBlockState(root), uuid, expected);
            for (Cell cell : CompositeLedger.get(world).cells()) {
                c.assertTrue(CompositeLedger.get(world).at(cell).stream().noneMatch(entry -> entry.owner().equals(legacy.owner())), "No stale retired owner contribution remains: " + cell);
            }
            for (var entry : canonical.cells().entrySet()) c.assertTrue(CompositeLedger.get(world).at(canonical.owner().root().add(entry.getKey())).stream().anyMatch(contribution -> contribution.owner().equals(canonical.owner()) && contribution.shape().equals(entry.getValue())), "Canonical footprint written exactly at every owned cell");
            c.assertTrue(oldTarget.valid(server), "Old selected/debug target still resolves the same UUID");
            ToolTarget current = new ToolTarget(ToolTarget.Kind.ARCHITECTURE, oldTarget.dimension(), uuid, root.asLong(), mapping.newRegistryId());
            c.assertTrue(!ToolCommands.fire(spent, current) && ToolCommands.view(server, current).globalUsed() == 1, "Spent personal use survives registry migration");
            c.assertTrue(ToolCommands.fire(fresh, current) && ToolCommands.view(server, current).globalUsed() == 2 && command(c, "scoreboard players get retained " + objective) == 1, "Old command binding executes against canonical target without resetting its shared counter");
            NbtCompound once = migrated.createNbt(); ImportedTypeMigration.loadedRoot(world, canonical.owner());
            c.assertTrue(migrated.createNbt().equals(once), "Loaded canonical state is idempotent");
            c.complete();
        } finally {
            var own = world.getBlockEntity(root) instanceof CompositeBlockEntity be ? be.resident() : null;
            if (own != null) CompositeRuntime.remove(world, own, null, false);
            manager.set("bloodborne_dw_tool_commands", originalCommands); ToolCommands.get(server);
            for (var p : List.of(spent, fresh)) { ToolCommands.forgetPlayer(server, p.getUuid()); p.discard(); }
            command(c, "scoreboard objectives remove " + objective);
        }
    }
    private static boolean close(double a, double b) { return Math.abs(a - b) < 1e-9; }
    private static int command(TestContext c, String command) { return c.getWorld().getServer().getCommandManager().executeWithPrefix(c.getWorld().getServer().getCommandSource().withLevel(4).withSilent(), command); }
    private static ServerPlayerEntity player(TestContext c) {
        var server = c.getWorld().getServer(); var p = new ServerPlayerEntity(server, c.getWorld(), new GameProfile(UUID.randomUUID(), "Dedup" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)));
        var connection = new ClientConnection(NetworkSide.SERVERBOUND) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, PacketCallbacks callbacks) {}
        };
        p.networkHandler = new ServerPlayNetworkHandler(server, connection, p); p.changeGameMode(GameMode.CREATIVE);
        return p;
    }
}
