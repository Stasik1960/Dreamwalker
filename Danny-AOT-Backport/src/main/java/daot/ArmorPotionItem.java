package daot;

import daot.network.ArmorPotionDrinkPayload;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Item.Settings;
import net.minecraft.world.World;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class ArmorPotionItem extends Item implements GeoItem {
   private final java.util.function.Supplier<Object> renderProvider = GeoItem.makeRenderer(this);
   @Override public java.util.function.Supplier<Object> getRenderProvider() { return renderProvider; }
   public static final String HARDENING_TAG = "has_hardening";
   private static final Map<UUID, Long> REJECTION_COOLDOWN_TICK = new ConcurrentHashMap<>();
   private static final long REJECTION_COOLDOWN_TICKS = 20L;
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   public static Consumer<Consumer<RenderProvider>> clientRendererConsumer;

   public ArmorPotionItem(Settings properties) {
      super(properties);
      SingletonGeoAnimatable.registerSyncedAnimatable(this);
   }

   @Override
   public Text getName(ItemStack stack) {
      return Text.translatable(this.getTranslationKey(stack)).formatted(Formatting.DARK_PURPLE).formatted(Formatting.BOLD);
   }

   @Override
   public void appendTooltip(ItemStack stack, World context, List<Text> tooltip, TooltipContext type) {
      tooltip.add(Text.literal("Grants Hardening abilities to certain shifters").formatted(Formatting.DARK_PURPLE));
      super.appendTooltip(stack, context, tooltip, type);
   }

   @Override
   public ActionResult useOnBlock(ItemUsageContext context) {
      PlayerEntity player = context.getPlayer();
      if (player != null && player.isSneaking()) {
         World level = context.getWorld();
         Direction face = context.getSide();
         BlockPos placePos = context.getBlockPos().offset(face);
         BlockState existing = level.getBlockState(placePos);
         if (!existing.isReplaceable()) {
            return ActionResult.PASS;
         } else {
            BlockState placeState = DannysAot.ARMOR_POTION_BLOCK.getDefaultState();
            if (!placeState.canPlaceAt(level, placePos)) {
               return ActionResult.PASS;
            } else {
               if (!level.isClient()) {
                  level.setBlockState(placePos, placeState, 3);
                  BlockSoundGroup st = placeState.getSoundGroup();
                  level.playSound(null, placePos, st.getPlaceSound(), SoundCategory.BLOCKS, (st.getVolume() + 1.0F) / 2.0F, st.getPitch() * 0.8F);
                  if (!player.getAbilities().creativeMode) {
                     context.getStack().decrement(1);
                  }
               }

               return ActionResult.success(level.isClient());
            }
         }
      } else {
         return ActionResult.PASS;
      }
   }

   @Override
   public UseAction getUseAction(ItemStack stack) {
      return UseAction.DRINK;
   }

   @Override
   public int getMaxUseTime(ItemStack stack) {
      return 32;
   }

   @Override
   public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
      ItemStack stack = user.getStackInHand(hand);
      if (world.isClient()) {
         user.setCurrentHand(hand);
         return TypedActionResult.consume(stack);
      } else if (user.getCommandTags().contains("has_hardening")) {
         return TypedActionResult.pass(stack);
      } else if (canUseHardening(user)) {
         user.setCurrentHand(hand);
         return TypedActionResult.consume(stack);
      } else {
         long now = world.getTime();
         Long last = REJECTION_COOLDOWN_TICK.get(user.getUuid());
         if (last == null || now - last >= 20L) {
            REJECTION_COOLDOWN_TICK.put(user.getUuid(), now);
            String msg = hasAnyShifterPower(user) ? "Shifter cannot use Hardening" : "Shifter Power Required";
            user.sendMessage(Text.literal(msg).formatted(Formatting.RED), false);
         }

         user.clearActiveItem();
         return TypedActionResult.fail(stack);
      }
   }

   private static boolean canUseHardening(PlayerEntity player) {
      Set<String> tags = player.getCommandTags();
      return tags.contains("female") || tags.contains("attack") || tags.contains("jaw");
   }

   private static boolean hasAnyShifterPower(PlayerEntity player) {
      Set<String> tags = player.getCommandTags();
      return tags.contains("attack")
         || tags.contains("armored")
         || tags.contains("colossal")
         || tags.contains("beast")
         || tags.contains("warhammer")
         || tags.contains("founder")
         || tags.contains("triple_t")
         || tags.contains("ogre_shifter")
         || tags.contains("jaw");
   }

   @Override
   public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
      if (user instanceof PlayerEntity player) {
         SoundEvent drinkSound = SoundEvents.ENTITY_TURTLE_EGG_BREAK;
         world.playSound(null, player.getX(), player.getY(), player.getZ(), drinkSound, SoundCategory.PLAYERS, 1.0F, 0.5F);
         if (!world.isClient() && player instanceof ServerPlayerEntity sp) {
            if (canUseHardening(sp) && !sp.getCommandTags().contains("has_hardening")) {
               sp.addCommandTag("has_hardening");
               sp.sendMessage(Text.literal("Shifter Hardening Acquired").formatted(Formatting.LIGHT_PURPLE), false);
               ServerPlayNetworking.send(sp, new ArmorPotionDrinkPayload(true, true));
            }

            if (!sp.getAbilities().creativeMode) {
               stack.decrement(1);
            }
         }

         return stack;
      } else {
         return stack;
      }
   }

   public void createRenderer(Consumer consumer) {
      if (clientRendererConsumer != null) {
         clientRendererConsumer.accept(consumer);
      }
   }

   public void registerControllers(ControllerRegistrar controllers) {
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }
}
