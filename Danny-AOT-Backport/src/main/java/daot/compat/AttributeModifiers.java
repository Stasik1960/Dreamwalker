package daot.compat;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.util.Identifier;
public final class AttributeModifiers {
    private AttributeModifiers() {}
    public static UUID uuid(Identifier id) { return UUID.nameUUIDFromBytes(id.toString().getBytes(StandardCharsets.UTF_8)); }
    public static EntityAttributeModifier create(Identifier id, double value, EntityAttributeModifier.Operation operation) {
        return new EntityAttributeModifier(uuid(id), id.toString(), value, operation);
    }
    public static EntityAttributeModifier getModifier(EntityAttributeInstance attr, Identifier id) { return attr.getModifier(uuid(id)); }
    public static boolean hasModifier(EntityAttributeInstance attr, Identifier id) { return getModifier(attr,id)!=null; }
    public static void removeModifier(EntityAttributeInstance attr, Identifier id) { attr.removeModifier(uuid(id)); }
}
