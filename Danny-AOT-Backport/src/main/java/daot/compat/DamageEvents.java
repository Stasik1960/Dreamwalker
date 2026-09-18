package daot.compat;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
public final class DamageEvents {
    private DamageEvents() {}
    public interface AfterDamage {
        void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked);
    }
    public static final Event<AfterDamage> AFTER_DAMAGE = EventFactory.createArrayBacked(AfterDamage.class,
        listeners -> (entity, source, base, taken, blocked) -> {
            for (AfterDamage listener : listeners) listener.afterDamage(entity, source, base, taken, blocked);
        });
}
