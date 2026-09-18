package daot.verification;

import daot.compat.network.*;
import daot.compat.components.*;
import daot.compat.BackportArmorItem;
import daot.compat.AttributeModifiers;
import daot.SpinalFluidData;
import daot.LacedFoodData;
import io.netty.buffer.Unpooled;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Offline checks only: no game client, world, server or window is started. */
public final class BackportVerification {
    private static int checks;
    private static BackportArmorItem armor, durable;
    public static void registerFixtures() {
        armor = new BackportArmorItem(ArmorMaterials.IRON, ArmorItem.Type.CHESTPLATE, new Item.Settings().maxCount(1));
        durable = new BackportArmorItem(ArmorMaterials.IRON, ArmorItem.Type.CHESTPLATE, new Item.Settings().maxDamage(50));
        net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.ITEM, new Identifier("test", "armor"), armor);
        net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.ITEM, new Identifier("test", "durable"), durable);
    }
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static Object sample(Class<?> type, int seed) {
        if (type == boolean.class) return seed % 2 == 0;
        if (type == int.class) return seed + 1;
        if (type == byte.class) return (byte) (seed + 1);
        if (type == short.class) return (short) (seed + 1);
        if (type == long.class) return (long) seed + 1234567;
        if (type == float.class) return seed + 0.25f;
        if (type == double.class) return seed + 0.125d;
        if (type == String.class) return "Проверка " + seed;
        if (type == UUID.class) return new UUID(123, seed + 1);
        if (type == BlockPos.class) return new BlockPos(-13, 75, 1025);
        if (type == Vec3d.class) return new Vec3d(1.25, -20.5, 2048.75);
        if (type == Map.class) return Map.of(42, seed, 1035, seed + 1);
        if (type.isArray()) {
            Object array = Array.newInstance(type.getComponentType(), 2);
            for (int i = 0; i < 2; i++) Array.set(array, i, sample(type.getComponentType(), seed + i));
            return array;
        }
        if (type.isEnum()) return type.getEnumConstants()[0];
        throw new AssertionError("No fixture for " + type);
    }
    private static boolean equal(Object a, Object b) {
        if (a != null && b != null && a.getClass().isRecord() && a.getClass() == b.getClass()) {
            try {
                for (RecordComponent field : a.getClass().getRecordComponents())
                    if (!equal(field.getAccessor().invoke(a), field.getAccessor().invoke(b))) return false;
                return true;
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
        return Objects.deepEquals(a, b);
    }
    private static <T> T roundTrip(PacketCodec<PacketByteBuf,T> codec, T value, String name) {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        try {
            codec.encode(buffer, value);
            T copy = codec.decode(buffer);
            check(buffer.readableBytes() == 0, name + ": unread bytes");
            check(equal(value, copy), name + ": data changed: " + value + " -> " + copy);
            return copy;
        } finally { buffer.release(); }
    }
    @SuppressWarnings("unchecked")
    private static void packets(Path directory) throws Exception {
        int count = 0;
        Set<Identifier> ids = new HashSet<>();
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.toString().endsWith("Payload.java")).sorted().toList()) {
                Class<?> type = Class.forName("daot.network." + file.getFileName().toString().replace(".java", ""));
                RecordComponent[] fields = type.getRecordComponents();
                check(fields != null, type + ": expected record");
                Class<?>[] types = Arrays.stream(fields).map(RecordComponent::getType).toArray(Class[]::new);
                PacketCodec<PacketByteBuf,Object> codec = (PacketCodec<PacketByteBuf,Object>) type.getField("STREAM_CODEC").get(null);
                for (int seed = 0; seed < 2; seed++) {
                    Object[] values = new Object[fields.length];
                    for (int i = 0; i < values.length; i++) values[i] = sample(types[i], seed);
                    CustomPayload packet = (CustomPayload) type.getDeclaredConstructor(types).newInstance(values);
                    roundTrip(codec, packet, type.getSimpleName());
                    if (seed == 0) check(ids.add(packet.getId().id()), "duplicate packet identifier " + packet.getId());
                }
                count++;
            }
        }
        roundTrip(SpinalFluidData.STREAM_CODEC, new SpinalFluidData("Зик", true), "spinal fluid");
        roundTrip(LacedFoodData.STREAM_CODEC, new LacedFoodData("Эрен", false), "laced food");
        System.out.println("Packet types verified: " + count);
    }
    private static void itemData() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        ComponentType<SpinalFluidData> fluid = Components.register(new Identifier("test", "fluid"),
                ComponentType.<SpinalFluidData>builder().codec(SpinalFluidData.CODEC).build());
        ItemStack item = new ItemStack(Items.GLASS_BOTTLE);
        check(Components.getOrDefault(item, fluid, SpinalFluidData.DEFAULT).equals(SpinalFluidData.DEFAULT), "new item default");
        SpinalFluidData value = new SpinalFluidData("Зик", true);
        Components.set(item, fluid, value);
        item.getOrCreateNbt().putString("unrelated", "retained");
        ItemStack restored = ItemStack.fromNbt(item.writeNbt(new NbtCompound()));
        check(value.equals(Components.get(restored, fluid)), "NBT component persisted");
        check(restored.getNbt().getString("unrelated").equals("retained"), "unrelated data preserved");
        NbtCompound copied = Components.get(restored, DataComponentTypes.CUSTOM_DATA).copyNbt();
        copied.putString("unrelated", "changed");
        check(restored.getNbt().getString("unrelated").equals("retained"), "NBT getters must return copies");
        Components.remove(restored, fluid);
        check(Components.get(restored, fluid) == null, "component removed");
        check(restored.getNbt().getString("unrelated").equals("retained"), "removal preserves other data");
        Identifier id = new Identifier("dannys-aot", "test_modifier");
        check(AttributeModifiers.uuid(id).equals(AttributeModifiers.uuid(new Identifier(id.toString()))), "modifier UUID stable");
        check(armor.getMaxDamage() == 0, "non-durable 1.21 armor must remain non-durable");
        check(armor.getProtection() == ArmorMaterials.IRON.getProtection(ArmorItem.Type.CHESTPLATE), "armor protection preserved");
        check(durable.getMaxDamage() == 50, "explicit durability retained");
    }
    public static void main(String[] args) throws Exception {
        packets(Path.of(args[0]));
        itemData();
        System.out.println("Offline backport verification passed: " + checks + " checks");
    }
}
