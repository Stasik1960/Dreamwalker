package daot.mixin;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.command.argument.ItemStackArgumentType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStackArgumentType.class)
public class HiddenItemSuggestionMixin {
   private static final Set<String> HIDDEN_ITEMS = Set.of("dannys-aot:odm_apg", "dannys-aot:apg_suit", "dannys-aot:vons_dread", "dannys-aot:kennyhat");

   @Inject(method = "listSuggestions", at = @At("RETURN"), cancellable = true)
   private void daot$filterHiddenItems(CommandContext<?> context, SuggestionsBuilder builder, CallbackInfoReturnable<CompletableFuture<Suggestions>> cir) {
      CompletableFuture<Suggestions> original = (CompletableFuture<Suggestions>)cir.getReturnValue();
      cir.setReturnValue(original.thenApply(suggestions -> {
         List<Suggestion> filtered = suggestions.getList().stream().filter(s -> !HIDDEN_ITEMS.contains(s.getText())).toList();
         return new Suggestions(suggestions.getRange(), filtered);
      }));
   }
}
