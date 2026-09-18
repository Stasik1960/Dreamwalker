package daot.advancement;
import com.google.gson.JsonObject;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.advancement.criterion.AbstractCriterionConditions;
import net.minecraft.predicate.entity.AdvancementEntityPredicateDeserializer;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class TitanShiftTrigger extends AbstractCriterion<TitanShiftTrigger.TriggerInstance> {
    private final Identifier id;
    
    public TitanShiftTrigger(Identifier id) { this.id=id; }
    @Override public Identifier getId() { return id; }
    @Override protected TriggerInstance conditionsFromJson(JsonObject json, LootContextPredicate player, AdvancementEntityPredicateDeserializer context) {
        return new TriggerInstance(id, player);
    }
    public void trigger(ServerPlayerEntity player) { this.trigger(player, instance -> true); }
    public static final class TriggerInstance extends AbstractCriterionConditions {
        public TriggerInstance(Identifier id, LootContextPredicate player) { super(id, player); }
        
    }
}
