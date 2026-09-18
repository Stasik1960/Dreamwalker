package daot;

import java.util.Objects;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Item.Settings;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.event.GameEvent;

public class PureTitanSpawnEggItem extends Item {
   public PureTitanSpawnEggItem(Settings properties) {
      super(properties);
   }

   private EntityType<? extends MobEntity> getRandomTitanType(World level) {
      int roll = level.random.nextInt(74);
      if (roll < 6) {
         return DannysAot.SMALL_TITAN;
      } else if (roll < 12) {
         return DannysAot.SMALL_TITAN_2;
      } else if (roll < 18) {
         return DannysAot.FRITZ_TITAN;
      } else if (roll < 24) {
         return DannysAot.TITAN_BEARD;
      } else if (roll < 30) {
         return DannysAot.ABNORMAL_TITAN;
      } else if (roll < 36) {
         return DannysAot.CONNIE_FATHER;
      } else if (roll < 42) {
         return DannysAot.TITAN;
      } else if (roll < 48) {
         return DannysAot.TITAN_TROPICAL;
      } else if (roll < 54) {
         return DannysAot.YELLOW_TITAN;
      } else if (roll < 60) {
         return DannysAot.SAD_TITAN;
      } else if (roll < 63) {
         return DannysAot.CRAWLER_TITAN;
      } else if (roll < 66) {
         return DannysAot.CRAWLING_ABNORMAL_TITAN;
      } else if (roll < 68) {
         return DannysAot.areOgreSpawnsAllowed(level) && level.random.nextInt(3) == 0 ? DannysAot.OGRE_TITAN : DannysAot.TITAN;
      } else {
         return DannysAot.TITAN;
      }
   }

   @Override
   public ActionResult useOnBlock(ItemUsageContext context) {
      World level = context.getWorld();
      if (!(level instanceof ServerWorld serverLevel)) {
         return ActionResult.SUCCESS;
      } else {
         ItemStack itemStack = context.getStack();
         BlockPos blockPos = context.getBlockPos();
         Direction direction = context.getSide();
         BlockPos spawnPos = blockPos.offset(direction);
         EntityType<? extends MobEntity> entityType = this.getRandomTitanType(level);
         MobEntity mob = entityType.spawnFromItemStack(
            serverLevel,
            itemStack,
            context.getPlayer(),
            spawnPos,
            SpawnReason.SPAWN_EGG,
            true,
            !Objects.equals(blockPos, spawnPos) && direction == Direction.UP
         );
         if (mob != null) {
            itemStack.decrement(1);
            level.emitGameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, blockPos);
         }

         return ActionResult.CONSUME;
      }
   }

   @Override
   public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
      ItemStack itemStack = user.getStackInHand(hand);
      BlockHitResult hitResult = raycast(world, user, FluidHandling.SOURCE_ONLY);
      if (hitResult.getType() != Type.BLOCK) {
         return TypedActionResult.pass(itemStack);
      } else if (world instanceof ServerWorld serverLevel) {
         BlockPos blockPos = hitResult.getBlockPos();
         if (!(world.getBlockState(blockPos).getBlock() instanceof FluidBlock)) {
            return TypedActionResult.pass(itemStack);
         } else if (world.canPlayerModifyAt(user, blockPos) && user.canPlaceOn(blockPos, hitResult.getSide(), itemStack)) {
            EntityType<? extends MobEntity> entityType = this.getRandomTitanType(world);
            MobEntity mob = entityType.spawnFromItemStack(serverLevel, itemStack, user, blockPos, SpawnReason.SPAWN_EGG, false, false);
            if (mob == null) {
               return TypedActionResult.pass(itemStack);
            } else {
               if (!user.getAbilities().creativeMode) {
                  itemStack.decrement(1);
               }

               user.incrementStat(Stats.USED.getOrCreateStat(this));
               world.emitGameEvent(user, GameEvent.ENTITY_PLACE, mob.getPos());
               return TypedActionResult.consume(itemStack);
            }
         } else {
            return TypedActionResult.fail(itemStack);
         }
      } else {
         return TypedActionResult.success(itemStack);
      }
   }
}
