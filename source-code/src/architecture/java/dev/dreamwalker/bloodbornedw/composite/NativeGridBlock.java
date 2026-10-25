package dev.dreamwalker.bloodbornedw.composite;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.*;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.registry.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

/** Fixed city content in vanilla chunk palettes. No block entity or instance cache. */
public final class NativeGridBlock extends Block implements NewStateStorage,NativeCellBlock {
    public static final IntProperty PART = IntProperty.of("part", 0, NativeGridCatalog.BANK_SIZE - 1);
    private static final List<NativeGridBlock> BANKS = new ArrayList<>();
    private static final List<NativeGridBlock> BANK_VIEW = Collections.unmodifiableList(BANKS);
    private static final Map<Integer,NativeGridBlock> BY_BANK=new HashMap<>();
    private static final ThreadLocal<Integer> CONSTRUCTING = new ThreadLocal<>();
    private final int bank;
    private NativeGridBlock(int bank) {
        super(Settings.create().strength(.4F).dropsNothing()
                .solidBlock((state, world, pos) -> at(bank, state).optics().solidBlock())
                .suffocates((state, world, pos) -> at(bank, state).optics().suffocates())
                .blockVision((state, world, pos) -> at(bank, state).optics().blockVision())
                .luminance(state -> at(bank, state).optics().luminance())
                .pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK));
        this.bank = bank;
        setDefaultState(getDefaultState().with(NativeVisualLayout.part(bank), NativeVisualLayout.wholeBanks().get(bank).get(0)));
    }
    static void initialize(int count) {
        if (!BANKS.isEmpty()) throw new IllegalStateException("Native grid banks already registered");
        for (int bank : NativeVisualLayout.wholeBanks().keySet()) {
            Identifier id = new Identifier("bloodborne_dw", "native_grid_" + bank);
            NativeGridBlock block;
            CONSTRUCTING.set(bank);
            try { block = new NativeGridBlock(bank); } finally { CONSTRUCTING.remove(); }
            Registry.register(Registries.BLOCK, id, block);
            BANKS.add(block);
            BY_BANK.put(bank,block);
            Registry.register(Registries.ITEM, id, new NativeGridItem(block));
        }
    }
    private static NativeGridCatalog.Cell at(int bank, BlockState state) {
        int index=bank*NativeGridCatalog.BANK_SIZE+state.get(NativeVisualLayout.part(bank));
        if(NativeGridCatalog.membership(index)==null)index=bank*NativeGridCatalog.BANK_SIZE+NativeVisualLayout.wholeBanks().get(bank).get(0);
        return NativeGridCatalog.cell(index);
    }
    public NativeGridCatalog.Cell cell(BlockState state) {
        Integer constructing = CONSTRUCTING.get();
        return at(constructing == null ? bank : constructing, state);
    }
    public static List<NativeGridBlock> banks() { return BANK_VIEW; }
    public static BlockState state(int index){int bank=index/128;return BY_BANK.get(bank).getDefaultState().with(NativeVisualLayout.part(bank),index%128);}
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(NativeVisualLayout.part(CONSTRUCTING.get())); }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return cell(state).collision(); }
    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return cell(state).outline(); }
    @Override public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) { return cell(state).culling(); }
    @Override public int getOpacity(BlockState state, BlockView world, BlockPos pos) { return cell(state).optics().opacity(); }
    @Override public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) { return cell(state).optics().ambientOcclusion(); }
    @Override public boolean hasSidedTransparency(BlockState state) { return cell(state).optics().sidedTransparency(); }
    @Override public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) { return cell(state).optics().transparent(); }
    @Override public boolean isShapeFullCube(BlockState state, BlockView world, BlockPos pos) { return cell(state).optics().opaqueFullCube(); }
    @Override public FluidState getFluidState(BlockState state) { return cell(state).fluid(); }
    @Override public BlockState getStateForNeighborUpdate(BlockState state, Direction side, BlockState neighbor, WorldAccess world, BlockPos pos, BlockPos other) {
        FluidState fluid = cell(state).fluid();
        if (!fluid.isEmpty()) world.scheduleFluidTick(pos, fluid.getFluid(), fluid.getFluid().getTickRate(world));
        return state;
    }
    @Override public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state) { return NativeGridRuntime.item(cell(state).index()); }
    @Override public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder context) { return List.of(NativeGridRuntime.item(cell(state).index())); }
    @Override public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        if (!state.equals(next)) NativeGridRuntime.replaced(world, pos, cell(state).index());
        super.onStateReplaced(state, world, pos, next, moved);
    }
    @Override public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        int alt = cell(state).alt();
        if (alt < 0 || NativeGridCatalog.membership(cell(state).index()) != null || !player.isSneaking() || !player.getStackInHand(hand).isEmpty()) return ActionResult.PASS;
        if (!world.isClient && dev.dreamwalker.bloodbornedw.architecture.BuildPermissions.canEdit(world, player, pos)) world.setBlockState(pos, NativeGridCatalog.state(alt), Block.NOTIFY_ALL);
        return ActionResult.success(world.isClient);
    }
    private static final class NativeGridItem extends BlockItem {
        NativeGridItem(NativeGridBlock block) { super(block, new Item.Settings()); }
        private int selected(ItemStack stack) {
            int part = 0;
            var tag = stack.getSubNbt("BlockStateTag");
            if (tag != null && tag.contains("part", 8)) try { part = Integer.parseInt(tag.getString("part")); } catch (NumberFormatException bad) { return -1; }
            if (part < 0 || part >= NativeGridCatalog.BANK_SIZE) return -1;
            NativeGridBlock block = (NativeGridBlock) getBlock();
            int index = block.bank * NativeGridCatalog.BANK_SIZE + part;
            return index < NativeGridCatalog.cells().size() && NativeGridCatalog.membership(index)!=null ? index : -1;
        }
        @Override protected BlockState getPlacementState(ItemPlacementContext context) {
            int index = selected(context.getStack());
            if (index < 0 || !NativeGridRuntime.canPlace(context, index)) return null;
            BlockState state = NativeGridCatalog.state(index);
            return canPlace(context, state) ? state : null;
        }
        @Override public ActionResult place(ItemPlacementContext context) {
            int index = selected(context.getStack());
            if (index < 0) return ActionResult.FAIL;
            return NativeGridCatalog.membership(index) == null ? super.place(context) : NativeGridRuntime.placeWhole(context, index);
        }
        @Override public Text getName(ItemStack stack) {
            int index = selected(stack);
            if (index < 0) return Text.literal("Городской фрагмент");
            var member = NativeGridCatalog.membership(index);
            return Text.literal(member == null ? NativeGridCatalog.cell(index).displayName() : member.object().displayName());
        }
    }
}
