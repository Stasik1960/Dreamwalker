package daot;

import daot.network.StrwsFireShakePayload;
import java.util.Arrays;
import java.util.UUID;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class StrwsBlockEntity extends BlockEntity implements GeoBlockEntity, Inventory, NamedScreenHandlerFactory {
   private static final int FUEL_INTERVAL_TICKS = 5;
   private static final int FIRE_INTERVAL_TICKS = 3;
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private float aimYaw = 0.0F;
   private float aimPitch = 0.0F;
   private UUID controller = null;
   private final DefaultedList<ItemStack> items = DefaultedList.ofSize(1, ItemStack.EMPTY);
   private boolean running = false;
   private boolean prevPowered = false;
   private final boolean[] wireActive = new boolean[6];
   private final double[] wireX = new double[6];
   private final double[] wireY = new double[6];
   private final double[] wireZ = new double[6];
   private final int[] wireEntityId = new int[6];
   private final double[] wireOffX = new double[6];
   private final double[] wireOffY = new double[6];
   private final double[] wireOffZ = new double[6];
   private int[] fireOrder = null;
   private int fireIndex = 0;
   private int fireCooldown = 0;
   private int fuelCooldown = 0;
   public float clientRenderYaw = 0.0F;
   public float clientRenderPitch = 0.0F;
   public boolean clientRenderInit = false;
   private final PropertyDelegate dataAccess = new PropertyDelegate() {
      @Override
      public int get(int index) {
         return switch (index) {
            case 0 -> StrwsBlockEntity.this.running ? 1 : 0;
            case 1 -> {
               ItemStack c = StrwsBlockEntity.this.items.get(0);
               yield c.getItem() instanceof GasCanisterItem ? GasCanisterItem.getGas(c) : 0;
            }
            default -> 0;
         };
      }

      @Override
      public void set(int index, int value) {
      }

      @Override
      public int size() {
         return 2;
      }
   };

   public StrwsBlockEntity(BlockPos pos, BlockState state) {
      super(DannysAot.STRWS_BLOCK_ENTITY, pos, state);
      Arrays.fill(this.wireEntityId, -1);
   }

   public float getAimYaw() {
      return this.aimYaw;
   }

   public float getAimPitch() {
      return this.aimPitch;
   }

   public void setAim(float yaw, float pitch) {
      this.aimYaw = yaw;
      this.aimPitch = pitch;
      this.markDirty();
   }

   public UUID getController() {
      return this.controller;
   }

   public void setController(UUID controller) {
      this.controller = controller;
   }

   public boolean isWireActive(int i) {
      return this.wireActive[i];
   }

   public Vec3d getWireLatch(int i) {
      return new Vec3d(this.wireX[i], this.wireY[i], this.wireZ[i]);
   }

   public boolean isWireOnEntity(int i) {
      return this.wireEntityId[i] >= 0;
   }

   public int getWireEntityId(int i) {
      return this.wireEntityId[i];
   }

   public Vec3d getWireOffset(int i) {
      return new Vec3d(this.wireOffX[i], this.wireOffY[i], this.wireOffZ[i]);
   }

   public void serverDetachWire(int bone) {
      if (this.wireActive[bone]) {
         this.wireActive[bone] = false;
         this.wireEntityId[bone] = -1;
         if (this.world != null) {
            this.sync(this.world, this.pos, this.getCachedState());
         }
      }
   }

   public boolean isRunning() {
      return this.running;
   }

   public void startRunning(World level, BlockPos pos, BlockState state) {
      if (!this.running) {
         ItemStack canister = this.items.get(0);
         if (canister.getItem() instanceof GasCanisterItem && GasCanisterItem.getGas(canister) > 0) {
            this.running = true;
            this.fireOrder = new int[6];
            int i = 0;

            while (i < this.fireOrder.length) {
               this.fireOrder[i] = i++;
            }

            for (int ix = this.fireOrder.length - 1; ix > 0; ix--) {
               int j = level.random.nextInt(ix + 1);
               int tmp = this.fireOrder[ix];
               this.fireOrder[ix] = this.fireOrder[j];
               this.fireOrder[j] = tmp;
            }

            this.fireIndex = 0;
            this.fireCooldown = 0;
            this.fuelCooldown = 0;

            for (int ix = 0; ix < this.wireActive.length; ix++) {
               this.wireActive[ix] = false;
               this.wireEntityId[ix] = -1;
            }

            this.sync(level, pos, state);
         }
      }
   }

   public void stopRunning(World level, BlockPos pos, BlockState state) {
      if (this.running || this.anyWireActive()) {
         this.running = false;
         if (level instanceof ServerWorld sl) {
            StrwsRestraintTracker.clearController(sl, pos);
         }

         for (int i = 0; i < this.wireActive.length; i++) {
            this.wireActive[i] = false;
            this.wireEntityId[i] = -1;
         }

         level.playSound(null, pos, ModSounds.HOOK_RETRACT, SoundCategory.BLOCKS, 0.8F, 1.0F);
         this.sync(level, pos, state);
      }
   }

   private boolean anyWireActive() {
      for (boolean a : this.wireActive) {
         if (a) {
            return true;
         }
      }

      return false;
   }

   public static void serverTick(World level, BlockPos pos, BlockState state, StrwsBlockEntity be) {
      boolean powered = StrwsMultiblock.isPowered(level, pos, state);
      if (powered != be.prevPowered) {
         be.prevPowered = powered;
         if (powered) {
            be.startRunning(level, pos, state);
         } else {
            be.stopRunning(level, pos, state);
         }
      }

      if (be.running) {
         ItemStack canister = be.items.get(0);
         if (canister.getItem() instanceof GasCanisterItem && GasCanisterItem.getGas(canister) > 0) {
            if (be.fuelCooldown <= 0) {
               GasCanisterItem.setGas(canister, GasCanisterItem.getGas(canister) - 1);
               be.markDirty();
               be.fuelCooldown = 5;
            } else {
               be.fuelCooldown--;
            }

            if (be.fireIndex < 6) {
               if (be.fireCooldown <= 0) {
                  int bone = be.fireOrder[be.fireIndex++];
                  be.fireWire(level, pos, state, bone);
                  be.fireCooldown = 3;
               } else {
                  be.fireCooldown--;
               }
            }
         } else {
            be.stopRunning(level, pos, state);
         }
      }
   }

   private void fireWire(World level, BlockPos pos, BlockState state, int bone) {
      Direction facing = state.get(Properties.HORIZONTAL_FACING);
      Vec3d origin = StrwsAim.boneWorldPos(pos, facing, this.aimYaw, this.aimPitch, bone);
      Vec3d dir = StrwsAim.aimDirection(facing, this.aimYaw, this.aimPitch);
      Vec3d blockEnd = StrwsAim.latch(level, origin, dir);
      LivingEntity titan = findTitanHit(level, origin, blockEnd);
      Vec3d end;
      if (titan != null) {
         end = titan.getBoundingBox().raycast(origin, blockEnd).orElse(origin);
         this.wireEntityId[bone] = titan.getId();
         Vec3d off = end.subtract(titan.getPos());
         this.wireOffX[bone] = off.x;
         this.wireOffY[bone] = off.y;
         this.wireOffZ[bone] = off.z;
         if (level instanceof ServerWorld sl) {
            StrwsRestraintTracker.attach(sl, pos, bone, titan.getUuid());
            sl.spawnParticles(
               new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.getDefaultState()), end.x, end.y, end.z, 20, 0.3, 0.3, 0.3, 0.05
            );
         }
      } else {
         end = blockEnd;
         this.wireEntityId[bone] = -1;
      }

      this.wireActive[bone] = true;
      this.wireX[bone] = end.x;
      this.wireY[bone] = end.y;
      this.wireZ[bone] = end.z;
      level.playSound(
         null,
         pos,
         level.random.nextBoolean() ? ModSounds.HOOK_SHOOT_1 : ModSounds.HOOK_SHOOT_2,
         SoundCategory.BLOCKS,
         0.5F,
         1.7F + level.random.nextFloat() * 0.2F
      );
      level.playSound(null, pos, ModSounds.STEAM_POOF, SoundCategory.BLOCKS, 1.0F, 2.0F);
      level.playSound(null, pos, ModSounds.GUN_SHOOT_APG, SoundCategory.BLOCKS, 1.0F, 2.0F);
      if (level instanceof ServerWorld sl) {
         sl.spawnParticles(ParticleTypes.POOF, origin.x, origin.y, origin.z, 8, 0.1, 0.1, 0.1, 0.02);
         double radiusSq = 1024.0;

         for (ServerPlayerEntity p : sl.getPlayers()) {
            if (p.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= radiusSq) {
               ServerPlayNetworking.send(p, new StrwsFireShakePayload(1.0F));
            }
         }
      }

      this.sync(level, pos, state);
   }

   private static LivingEntity findTitanHit(World level, Vec3d origin, Vec3d blockEnd) {
      Box rayBox = new Box(origin, blockEnd).expand(1.0);
      LivingEntity best = null;
      double bestDistSq = origin.squaredDistanceTo(blockEnd);

      for (Entity e : level.getOtherEntities((Entity)null, rayBox, StrwsRestraintTracker.IS_RESTRAINABLE)) {
         if (e instanceof LivingEntity le) {
            Box box = e.getBoundingBox();
            Vec3d p = box.raycast(origin, blockEnd).orElse(box.contains(origin) ? origin : null);
            if (p != null) {
               double d = origin.squaredDistanceTo(p);
               if (d < bestDistSq) {
                  bestDistSq = d;
                  best = le;
               }
            }
         }
      }

      return best;
   }

   private void sync(World level, BlockPos pos, BlockState state) {
      this.markDirty();
      if (!level.isClient) {
         level.updateListeners(pos, state, state, 2);
      }
   }

   @Override
   public Text getDisplayName() {
      return Text.translatable("block.dannys-aot.strws");
   }

   @Nullable
   @Override
   public ScreenHandler createMenu(int containerId, PlayerInventory playerInventory, PlayerEntity player) {
      return new StrwsMenu(containerId, playerInventory, this, this, this.dataAccess);
   }

   @Override
   public int size() {
      return 1;
   }

   @Override
   public boolean isEmpty() {
      return this.items.get(0).isEmpty();
   }

   @Override
   public ItemStack getStack(int slot) {
      return this.items.get(slot);
   }

   @Override
   public ItemStack removeStack(int slot, int amount) {
      return Inventories.splitStack(this.items, slot, amount);
   }

   @Override
   public ItemStack removeStack(int slot) {
      return Inventories.removeStack(this.items, slot);
   }

   @Override
   public void setStack(int slot, ItemStack stack) {
      this.items.set(slot, stack);
      this.markDirty();
   }

   @Override
   public boolean canPlayerUse(PlayerEntity player) {
      return Inventory.canPlayerUse(this, player);
   }

   @Override
   public void clear() {
      this.items.clear();
   }

   public void registerControllers(ControllerRegistrar controllers) {
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   @Override
   protected void writeNbt(NbtCompound nbt) {
      super.writeNbt(nbt);
      nbt.putFloat("AimYaw", this.aimYaw);
      nbt.putFloat("AimPitch", this.aimPitch);
      Inventories.writeNbt(nbt, this.items);
   }

   @Override
   public void readNbt(NbtCompound nbt) {
      super.readNbt(nbt);
      this.aimYaw = nbt.getFloat("AimYaw");
      this.aimPitch = nbt.getFloat("AimPitch");
      Inventories.readNbt(nbt, this.items);
      this.readWireTag(nbt);
   }

   @Override
   public NbtCompound toInitialChunkDataNbt() {
      NbtCompound tag = super.toInitialChunkDataNbt();
      tag.putFloat("AimYaw", this.aimYaw);
      tag.putFloat("AimPitch", this.aimPitch);
      tag.putBoolean("Running", this.running);
      long activeMask = 0L;

      for (int i = 0; i < this.wireActive.length; i++) {
         if (this.wireActive[i]) {
            activeMask |= 1L << i;
            tag.putDouble("WX" + i, this.wireX[i]);
            tag.putDouble("WY" + i, this.wireY[i]);
            tag.putDouble("WZ" + i, this.wireZ[i]);
            tag.putInt("WE" + i, this.wireEntityId[i]);
            if (this.wireEntityId[i] >= 0) {
               tag.putDouble("WOX" + i, this.wireOffX[i]);
               tag.putDouble("WOY" + i, this.wireOffY[i]);
               tag.putDouble("WOZ" + i, this.wireOffZ[i]);
            }
         }
      }

      tag.putLong("WireMask", activeMask);
      return tag;
   }

   private void readWireTag(NbtCompound tag) {
      this.running = tag.getBoolean("Running");
      long mask = tag.getLong("WireMask");

      for (int i = 0; i < this.wireActive.length; i++) {
         boolean active = (mask & 1L << i) != 0L;
         this.wireActive[i] = active;
         this.wireEntityId[i] = -1;
         if (active) {
            this.wireX[i] = tag.getDouble("WX" + i);
            this.wireY[i] = tag.getDouble("WY" + i);
            this.wireZ[i] = tag.getDouble("WZ" + i);
            this.wireEntityId[i] = tag.contains("WE" + i) ? tag.getInt("WE" + i) : -1;
            if (this.wireEntityId[i] >= 0) {
               this.wireOffX[i] = tag.getDouble("WOX" + i);
               this.wireOffY[i] = tag.getDouble("WOY" + i);
               this.wireOffZ[i] = tag.getDouble("WOZ" + i);
            }
         }
      }
   }

   @Override
   public Packet<ClientPlayPacketListener> toUpdatePacket() {
      return BlockEntityUpdateS2CPacket.create(this);
   }
}
