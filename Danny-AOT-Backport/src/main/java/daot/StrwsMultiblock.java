package daot;

import daot.network.StrwsControlPayload;
import java.util.UUID;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public final class StrwsMultiblock {
   public static final int[][] LOCAL_OFFSETS = new int[][]{{-1, 0}, {0, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}, {-1, 2}, {0, 2}, {1, 2}};

   private StrwsMultiblock() {
   }

   public static BlockPos worldOffset(Direction facing, int lateral, int vertical) {
      Direction latDir = facing.rotateYClockwise();
      return new BlockPos(latDir.getOffsetX() * lateral, vertical, latDir.getOffsetZ() * lateral);
   }

   public static boolean isStructureBlock(BlockState state) {
      return state.isOf(DannysAot.STRWS_BLOCK) || state.isOf(DannysAot.STRWS_PART_BLOCK);
   }

   public static boolean isPowered(World level, BlockPos controllerPos, BlockState controllerState) {
      Direction facing = controllerState.get(Properties.HORIZONTAL_FACING);

      for (int[] o : LOCAL_OFFSETS) {
         if (level.isReceivingRedstonePower(controllerPos.add(worldOffset(facing, o[0], o[1])))) {
            return true;
         }
      }

      return false;
   }

   public static BlockPos getControllerPos(BlockPos pos, BlockState state) {
      if (state.isOf(DannysAot.STRWS_PART_BLOCK)) {
         Direction facing = state.get(Properties.HORIZONTAL_FACING);
         int lateral = state.get(StrwsPartBlock.LAT_OFF) - 1;
         int vertical = state.get(StrwsPartBlock.Y_OFF);
         return pos.subtract(worldOffset(facing, lateral, vertical));
      } else {
         return pos;
      }
   }

   public static boolean canPlace(World level, BlockPos base, Direction facing) {
      for (int[] o : LOCAL_OFFSETS) {
         if (!level.getBlockState(base.add(worldOffset(facing, o[0], o[1]))).isReplaceable()) {
            return false;
         }
      }

      return true;
   }

   public static void place(World level, BlockPos base, Direction facing) {
      level.setBlockState(base, DannysAot.STRWS_BLOCK.getDefaultState().with(Properties.HORIZONTAL_FACING, facing), 3);

      for (int[] o : LOCAL_OFFSETS) {
         int lateral = o[0];
         int vertical = o[1];
         if (lateral != 0 || vertical != 0) {
            BlockState part = DannysAot.STRWS_PART_BLOCK
               .getDefaultState()
               .with(Properties.HORIZONTAL_FACING, facing)
               .with(StrwsPartBlock.LAT_OFF, lateral + 1)
               .with(StrwsPartBlock.Y_OFF, vertical);
            level.setBlockState(base.add(worldOffset(facing, lateral, vertical)), part, 3);
         }
      }
   }

   public static void destroy(World level, BlockPos origin, BlockState originState, PlayerEntity player) {
      if (!level.isClient) {
         Direction facing = originState.get(Properties.HORIZONTAL_FACING);
         BlockPos controller = getControllerPos(origin, originState);
         if (level instanceof ServerWorld sl) {
            StrwsRestraintTracker.clearController(sl, controller);
         }

         if (level.getBlockEntity(controller) instanceof StrwsBlockEntity be) {
            clearControl(level, controller, be);
            ItemStack canister = be.getStack(0);
            if (!canister.isEmpty()) {
               Block.dropStack(level, controller, canister);
               be.setStack(0, ItemStack.EMPTY);
            }
         }

         for (int[] o : LOCAL_OFFSETS) {
            BlockPos p = controller.add(worldOffset(facing, o[0], o[1]));
            if (!p.equals(origin)) {
               BlockState s = level.getBlockState(p);
               if (isStructureBlock(s)) {
                  level.syncWorldEvent(2001, p, Block.getRawIdFromState(s));
                  level.setBlockState(p, Blocks.AIR.getDefaultState(), 35);
               }
            }
         }

         if (player == null || !player.isCreative()) {
            Block.dropStack(level, controller, new ItemStack(DannysAot.STRWS_ITEM));
         }
      }
   }

   public static ActionResult interact(World level, BlockPos clickedPos, BlockState clickedState, PlayerEntity player) {
      if (level.isClient) {
         return ActionResult.success(true);
      } else if (player instanceof ServerPlayerEntity sp) {
         BlockPos controller = getControllerPos(clickedPos, clickedState);
         if (level.getBlockEntity(controller) instanceof StrwsBlockEntity be) {
            UUID var9 = sp.getUuid();
            UUID current = be.getController();
            if (var9.equals(current)) {
               be.setController(null);
               StrwsAimServerState.clear(var9);
               ServerPlayNetworking.send(sp, new StrwsControlPayload(controller, false));
               return ActionResult.success(false);
            } else if (current != null) {
               sp.sendMessage(Text.literal("Someone is already aiming this").formatted(Formatting.RED), true);
               return ActionResult.FAIL;
            } else if (StrwsAimServerState.get(var9) != null) {
               return ActionResult.FAIL;
            } else {
               be.setController(var9);
               StrwsAimServerState.put(var9, controller);
               ServerPlayNetworking.send(sp, new StrwsControlPayload(controller, true));
               return ActionResult.success(false);
            }
         } else {
            return ActionResult.PASS;
         }
      } else {
         return ActionResult.PASS;
      }
   }

   public static ActionResult openMenu(World level, BlockPos clickedPos, BlockState clickedState, PlayerEntity player) {
      if (level.isClient) {
         return ActionResult.success(true);
      } else {
         BlockPos controller = getControllerPos(clickedPos, clickedState);
         if (level.getBlockEntity(controller) instanceof StrwsBlockEntity be && player instanceof ServerPlayerEntity sp) {
            sp.openHandledScreen(be);
            return ActionResult.success(false);
         } else {
            return ActionResult.PASS;
         }
      }
   }

   public static void clearControl(World level, BlockPos controllerPos, StrwsBlockEntity be) {
      UUID uuid = be.getController();
      if (uuid != null) {
         be.setController(null);
         StrwsAimServerState.clear(uuid);
         MinecraftServer server = level.getServer();
         if (server != null) {
            ServerPlayerEntity sp = server.getPlayerManager().getPlayer(uuid);
            if (sp != null) {
               ServerPlayNetworking.send(sp, new StrwsControlPayload(controllerPos, false));
            }
         }
      }
   }

   public static void handleDisconnect(ServerPlayerEntity player) {
      BlockPos controlled = StrwsAimServerState.get(player.getUuid());
      if (controlled != null) {
         if (player.getWorld().getBlockEntity(controlled) instanceof StrwsBlockEntity be) {
            be.setController(null);
         }

         StrwsAimServerState.clear(player.getUuid());
      }
   }
}
