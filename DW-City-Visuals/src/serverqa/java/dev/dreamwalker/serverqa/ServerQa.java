package dev.dreamwalker.serverqa;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.math.BlockVector3;
import dev.dreamwalker.bloodbornedw.composite.NativeVisualData;
import dev.dreamwalker.bloodbornedw.composite.NativeVisuals;
import dev.dreamwalker.bloodbornedw.composite.RetainedFrameData;
import dev.dreamwalker.bloodbornedw.composite.SourceFallbackCarrierV1271;
import dev.dreamwalker.bloodbornedw.editor.CityEditorServer;
import dev.dreamwalker.bloodbornedw.editor.EditorData;
import dev.dreamwalker.bloodbornedw.editor.catalogue.EditorCatalogue;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.util.math.Direction;
import java.util.List;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/** Opt-in dedicated-server WorldEdit integration smoke test on a disposable world. */
public final class ServerQa implements ModInitializer {
    private Path output;
    private final JsonObject report = new JsonObject();
    private int stage, originalSourceNative, originalDestinationNative;
    private boolean active, ownsSource, ownsDestination;
    private ServerWorld world;
    private BlockPos source, destination;
    private BlockState fixture;
    private EditSession edit;
    private com.sk89q.worldedit.world.block.BaseBlock copiedBlock;
    private com.sk89q.worldedit.world.World adapter;

    @Override public void onInitialize() {
        if (!Boolean.getBoolean("dw.editorqa.disposable")) return;
        String path = System.getProperty("dw.serverqa.output");
        if (path == null || path.isBlank()) throw new IllegalStateException("dw.serverqa.output is required");
        output = Path.of(path).toAbsolutePath().normalize();
        if (!output.startsWith(Path.of("D:\\DreamWalker").toAbsolutePath().normalize())) throw new IllegalStateException("QA output must be under D:\\DreamWalker");
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            // This isolated test process also contains third-party non-daemon config workers.
            // Exit only after Fabric confirms that the normal server shutdown has finished.
            int code="PASS".equals(report.has("status")?report.get("status").getAsString():"")?0:1;
            new Thread(()->System.exit(code),"DW-QA-process-exit").start();
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> active = true);
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }
    private void tick(MinecraftServer server) {
        if (!active) return;
        try {
            if (!server.getPlayerManager().getPlayerList().isEmpty()) throw new IllegalStateException("Dedicated QA requires no connected players");
            switch (stage++) {
                case 0 -> { if (Boolean.getBoolean("dw.serverqa.fillOnly")) fillOnly(server); else setup(server); }
                case 1 -> copy();
                case 2 -> undo();
                case 3 -> redo();
                case 4 -> verifyRedo();
                case 5 -> finish(server);
                default -> throw new IllegalStateException("Invalid QA stage");
            }
        } catch (Throwable failure) { fail(server, failure); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static BlockVector3 vector(BlockPos p) { return BlockVector3.at(p.getX(), p.getY(), p.getZ()); }
    private EditSession session() { return WorldEdit.getInstance().newEditSessionBuilder().world(adapter).build(); }
    @SuppressWarnings("unchecked")
    private List<BlockPos> collectFill(BlockPos a, BlockPos b) throws Exception {
        Class<?> type = Class.forName("dev.dreamwalker.bloodbornedw.editor.CityEditorServer$Session");
        var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
        Object session = constructor.newInstance();
        for (String name : new String[]{"first", "second", "selectionDimension"}) {
            var field = type.getDeclaredField(name); field.setAccessible(true);
            field.set(session, name.equals("first") ? a : name.equals("second") ? b : world.getRegistryKey());
        }
        var method = CityEditorServer.class.getDeclaredMethod("collect", ServerWorld.class, BlockPos.class, Direction.class, type, String.class);
        method.setAccessible(true);
        return (List<BlockPos>)method.invoke(null, world, a, Direction.UP, session, "fill");
    }
    private void fillOnly(MinecraftServer server) throws Exception {
        world = server.getOverworld();
        report.addProperty("scope", "fill selection only: real server world and session; no packet, mutation job or undo");
        BlockPos a = new BlockPos(166, world.getTopY() - 40, -812), b = a.east();
        world.getChunk(a); world.getChunk(b);
        require(world.getBlockState(a).isAir() && world.getBlockState(b).isAir(), "fill fixture must be air");
        require(EditorData.style(world, a) == -1 && EditorData.style(world, b) == -1, "fill fixture has editor data");
        require(NativeVisuals.index(world, a) == NativeVisuals.index(world, b), "fill fixture native indices differ");
        try {
            require(world.setBlockState(a, Blocks.OAK_LOG.getDefaultState(), 3), "fixture placement failed");
            require(world.setBlockState(b, Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.X), 3), "variant placement failed");
            require(collectFill(a, b).equals(List.of(a)), "different block states crossed fill mask");
            require(world.setBlockState(b, Blocks.OAK_LOG.getDefaultState(), 3), "matching fixture placement failed");
            require(CityEditorServer.effectiveStyle(world, a) == 0 && CityEditorServer.effectiveStyle(world, b) == 0, "fixture outside base palette required");
            CityEditorServer.queueAppearance(world, b, 0);
            require(collectFill(a, b).size() == 2, "automatic and manual base should match");
            report.addProperty("blockStateBoundary", true); report.addProperty("automaticAndManualBase", true);
        } finally {
            CityEditorServer.queueAppearance(world, b, -1);
            world.breakBlock(a, false); world.breakBlock(b, false);
        }
        BlockPos nativeA = null, nativeB = null;
        outer: for (int x = 160; x < 192; x++) for (int z = -832; z < -792; z++) {
            world.getChunk(new BlockPos(x, 88, z));
            for (int y = 60; y <= 110; y++) {
                BlockPos p = new BlockPos(x, y, z), q = p.east(); world.getChunk(q);
                int i = NativeVisuals.index(world, p), j = NativeVisuals.index(world, q);
                if (i >= 0 && j >= 0 && i != j && world.getBlockState(p) == world.getBlockState(q)
                        && CityEditorServer.effectiveStyle(world, p) == CityEditorServer.effectiveStyle(world, q)) {
                    nativeA = p; nativeB = q; break outer;
                }
            }
        }
        require(nativeA != null, "no adjacent native pair found in bounded city scan");
        require(collectFill(nativeA, nativeB).equals(List.of(nativeA)), "different global native indices crossed fill mask");
        report.addProperty("nativePair", nativeA + " / " + nativeB);
        report.addProperty("nativeIndices", NativeVisuals.index(world, nativeA) + " / " + NativeVisuals.index(world, nativeB));
        boolean regionalPair = false;
        for (int y = 88; y <= 100 && !regionalPair; y++) for (int z = -816; z <= -795 && !regionalPair; z++) {
            BlockPos p = new BlockPos(165, y, z), q = p.east(); world.getChunk(p); world.getChunk(q);
            if (EditorData.style(world, p) == -1 && EditorData.style(world, q) == -1
                    && world.getBlockState(p) == world.getBlockState(q) && NativeVisuals.index(world, p) == NativeVisuals.index(world, q)
                    && CityEditorServer.effectiveStyle(world, p) != CityEditorServer.effectiveStyle(world, q)) {
                require(collectFill(p, q).equals(List.of(p)), "different automatic regional colors crossed fill mask");
                report.addProperty("regionalPair", p + " / " + q); regionalPair = true;
            }
        }
        report.addProperty("regionalPairFound", regionalPair);
        require(server.saveAll(false, true, true), "world save failed");
        report.addProperty("status", "PASS"); write(); active = false; server.stop(false);
    }
    private void setup(MinecraftServer server) throws Exception {
        JsonObject audit = EditorCatalogue.audit(); report.add("catalogueAudit", audit);
        report.addProperty("scope", "WorldEdit appearance lifecycle; catalogue coverage is reported separately");
        world = server.getOverworld(); source = new BlockPos(166, world.getTopY() - 40, -812); destination = source.east(2);
        world.getChunk(source); world.getChunk(destination);
        require(world.getBlockState(source).isAir() && world.getBlockState(destination).isAir(), "fixture cells must be air");
        require(EditorData.style(world, source) == -1 && EditorData.style(world, destination) == -1, "fixture cells contain editor data");
        originalSourceNative = NativeVisuals.index(world, source); originalDestinationNative = NativeVisuals.index(world, destination);
        try (var stream = ServerQa.class.getResourceAsStream("/bloodborne_dw/source-fallback-carriers-v1271.json")) {
            require(stream != null, "missing source carrier palette");
            String id = JsonParser.parseReader(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("targetRegistryIds").get(0).getAsString();
            var block = Registries.BLOCK.get(new Identifier(id));
            require(block instanceof SourceFallbackCarrierV1271, "first carrier unavailable");
            fixture = block.getDefaultState().with(SourceFallbackCarrierV1271.SOURCE, 0).with(SourceFallbackCarrierV1271.SOURCE_BANK, 0);
        }
        require(world.setBlockState(source, fixture, 3), "source placement rejected"); ownsSource = true;
        require(NativeVisuals.index(world, source) == originalSourceNative, "source fixture changed native data");
        CityEditorServer.queueAppearance(world, source, 2);
        adapter = (com.sk89q.worldedit.world.World)Class.forName("com.sk89q.worldedit.fabric.FabricAdapter").getMethod("adapt", World.class).invoke(null, world);
        report.addProperty("source", source.toString()); report.addProperty("destination", destination.toString());
    }
    private void copy() throws Exception {
        require(EditorData.style(world, source) == 2, "source appearance did not flush");
        WorldChunk chunk = (WorldChunk)world.getChunk(source);
        NativeVisualData read = NativeVisualData.read(NativeVisuals.data(chunk).nbt(), chunk);
        require(((RetainedFrameData)(Object)read).dw$editor().style(source) == 2, "native data NBT round trip failed");
        edit = session(); var block = edit.getFullBlock(vector(source)); copiedBlock = block;
        report.add("sourceBeforeCopy", inspect(source));
        require(block.getNbtData() != null && block.getNbtData().getValue().get("DreamwalkerCityEditor") instanceof com.sk89q.jnbt.CompoundTag, "WorldEdit full read lacks appearance tag");
        var appearance = (com.sk89q.jnbt.CompoundTag)block.getNbtData().getValue().get("DreamwalkerCityEditor");
        require(appearance.getInt("Style") == 2 && appearance.getInt("Applied") == 2 && appearance.getLong("Source") == source.asLong()
                && appearance.getString("Dimension").equals(world.getRegistryKey().getValue().toString()), "WorldEdit appearance tag contents differ");
        require(block.getNbtData().getValue().size() == 1, "plain fixture unexpectedly contains other NBT");
        var nativePlain = dev.dreamwalker.bloodbornedw.editor.worldedit.AppearanceTag.forNativeWrite(block);
        require(nativePlain.getNbtData() == null && block.getNbtData().getValue().get("DreamwalkerCityEditor") == appearance,
                "appearance-only native copy must have no NBT without changing source");
        var nested = new com.sk89q.jnbt.CompoundTag(java.util.Map.of(
                "Text", new com.sk89q.jnbt.StringTag("preserved"),
                "List", new com.sk89q.jnbt.ListTag(com.sk89q.jnbt.StringTag.class,
                        java.util.List.of(new com.sk89q.jnbt.StringTag("one"), new com.sk89q.jnbt.StringTag("two")))));
        var payload = new java.util.LinkedHashMap<String, com.sk89q.jnbt.Tag>();
        payload.put("DreamwalkerCityEditor", appearance); payload.put("Nested", nested);
        payload.put("id", new com.sk89q.jnbt.StringTag("test:entity"));
        var tagged = block.toBaseBlock(new com.sk89q.jnbt.CompoundTag(payload));
        var filtered = dev.dreamwalker.bloodbornedw.editor.worldedit.AppearanceTag.forNativeWrite(tagged);
        var expected = new java.util.LinkedHashMap<>(payload); expected.remove("DreamwalkerCityEditor");
        require(filtered.getNbtData() != null && filtered.getNbtData().getValue().equals(expected)
                && tagged.getNbtData().getValue().equals(payload) && block.getNbtData().getValue().get("DreamwalkerCityEditor") == appearance,
                "native copy must retain all nested NBT and leave both inputs unchanged");
        report.addProperty("nativeWriteAppearanceOnly", true); report.addProperty("nativeWritePreservesOtherNbt", true);
        ownsDestination = true;
        require(edit.setBlock(vector(destination), block), "WorldEdit paste rejected"); edit.close();
        report.add("destinationImmediatelyAfterPaste", inspect(destination));
        report.addProperty("copyReadTag", true);
    }
    private void undo() {
        report.add("destinationAfterPasteTick", inspect(destination));
        require(world.getBlockState(destination).equals(fixture), "pasted physical state differs");
        require(EditorData.style(world, destination) == 2, "pasted editor appearance differs");
        try (EditSession read = session()) { require(read.getFullBlock(vector(destination)).toImmutableState().equals(copiedBlock.toImmutableState()), "pasted WorldEdit logical state differs"); }
        try (EditSession target = session()) { edit.undo(target); }
        report.addProperty("paste", true);
    }
    private JsonObject inspect(BlockPos pos) {
        JsonObject detail = new JsonObject();
        BlockState state = world.getBlockState(pos);
        detail.addProperty("physicalId", Registries.BLOCK.getId(state.getBlock()).toString());
        detail.addProperty("physicalState", state.toString());
        detail.addProperty("nativeIndex", NativeVisuals.index(world, pos));
        detail.addProperty("editorStyle", EditorData.style(world, pos));
        detail.addProperty("pendingStyle", CityEditorServer.pendingStyle(world, pos));
        try (EditSession read = session()) {
            var block = read.getFullBlock(vector(pos));
            detail.addProperty("logicalId", block.getBlockType().getId());
            detail.addProperty("logicalState", block.toImmutableState().toString());
            detail.addProperty("logicalEqualsSource", copiedBlock != null && block.toImmutableState().equals(copiedBlock.toImmutableState()));
            detail.addProperty("logicalNbt", block.getNbtData() == null ? "" : block.getNbtData().toString());
        }
        return detail;
    }
    private void redo() {
        require(world.getBlockState(destination).isAir() && EditorData.style(world, destination) == -1, "undo failed to restore air and clear appearance");
        try (EditSession target = session()) { edit.redo(target); }
        report.addProperty("undo", true);
    }
    private void verifyRedo() {
        report.add("destinationAfterRedoTick", inspect(destination));
        require(world.getBlockState(destination).equals(fixture) && EditorData.style(world, destination) == 2, "redo failed to restore state and appearance");
        require(world.breakBlock(source, false) && world.breakBlock(destination, false), "normal cleanup failed");
        report.addProperty("redo", true);
    }
    private void finish(MinecraftServer server) throws Exception {
        require(world.getBlockState(source).isAir() && world.getBlockState(destination).isAir(), "cleanup left blocks");
        require(EditorData.style(world, source) == -1 && EditorData.style(world, destination) == -1, "cleanup left editor data");
        require(NativeVisuals.index(world, source) == originalSourceNative && NativeVisuals.index(world, destination) == originalDestinationNative, "cleanup changed native data");
        WorldChunk chunk = (WorldChunk)world.getChunk(source); NativeVisualData read = NativeVisualData.read(NativeVisuals.data(chunk).nbt(), chunk);
        require(((RetainedFrameData)(Object)read).dw$editor().style(source) == -1 && ((RetainedFrameData)(Object)read).dw$editor().style(destination) == -1, "cleanup NBT retained appearance");
        require(server.saveAll(false, true, true), "world save failed");
        report.addProperty("cleanupAndSave", true); report.addProperty("status", "PASS"); write(); active = false; server.stop(false);
    }
    private void fail(MinecraftServer server, Throwable failure) {
        active = false;
        try { if (world != null) { if (ownsSource) world.breakBlock(source, false); if (ownsDestination) world.breakBlock(destination, false); } server.saveAll(false, true, true); }
        catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
        StringWriter trace = new StringWriter(); failure.printStackTrace(new PrintWriter(trace));
        report.addProperty("status", "FAIL"); report.addProperty("error", trace.toString());
        try { write(); } catch (Exception error) { error.printStackTrace(); }
        server.stop(false);
    }
    private void write() throws Exception { Files.createDirectories(output.getParent()); Files.writeString(output, report.toString(), StandardCharsets.UTF_8); }
}




