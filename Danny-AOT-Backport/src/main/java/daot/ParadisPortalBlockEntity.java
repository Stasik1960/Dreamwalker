package daot;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

public class ParadisPortalBlockEntity extends BlockEntity {
   public ParadisPortalBlockEntity(BlockPos pos, BlockState state) {
      super(DannysAot.PARADIS_PORTAL_BLOCK_ENTITY, pos, state);
   }
}
