package dev.dreamwalker.bloodbornedw.architecture.wall;

import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.WallShape;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Art freezes once; only validated own form NBT survives pick, drop and new placement. */
public final class PrototypeWallItem extends BlockItem {
    public PrototypeWallItem(PrototypeWallBlock block,Settings settings){super(block,settings);}
    @Override public ActionResult place(ItemPlacementContext context) {
        int art=material(context.getStack());
        NbtCompound oldForm=context.getStack().getSubNbt("BlockStateTag");
        if(art!=0)art=(oldForm!=null&&oldForm.contains("rotation",8)?(integer(oldForm,"rotation",7)&1)!=0:art==1)?1:6;
        PrototypeWallBlock canonical=PrototypeWallArchitecture.materialBlock(art);
        if(canonical!=getBlock()){
            ItemStack converted=new ItemStack(canonical.asItem(),context.getStack().getCount());
            if(context.getStack().getNbt()!=null)converted.setNbt(context.getStack().getNbt().copy());
            converted.removeSubNbt("BlockEntityTag");
            ItemPlacementContext redirected=new ItemPlacementContext(context){@Override public ItemStack getStack(){return converted;}};
            ActionResult result=((PrototypeWallItem)canonical.asItem()).place(redirected);
            if(result.isAccepted())context.getStack().decrement(Math.max(0,context.getStack().getCount()-converted.getCount()));
            return result;
        }
        ItemStack working=context.getStack().copy();NbtCompound tag=working.getOrCreateSubNbt("BlockStateTag");
        tag.putString("material",Integer.toString(art));
        if(!List.of("base","alt").contains(tag.getString("profile")))tag.putString("profile","base");
        if(!List.of("auto","manual").contains(tag.getString("connections")))tag.putString("connections","auto");
        if(tag.getString("connections").equals("manual")) {
            if(!validInteger(tag,"rotation",7))tag.putString("rotation","0");
            if(!List.of("true","false").contains(tag.getString("up")))tag.putString("up",List.of("true","false").contains(tag.getString("post"))?tag.getString("post"):"true");
            for(Direction direction:Direction.Type.HORIZONTAL){String name=direction.asString(),value=tag.getString(name);if(value.equals("true"))value=tag.getString("course").equals("tall")?"tall":"low";if(!List.of("none","low","tall").contains(value))value="none";tag.putString(name,value);}
        } else {
            // Auto connection flags cannot be smuggled in from the old placement.
            for(String name:List.of("north","east","south","west","up","rotation","connections"))tag.remove(name);
        }
        tag.remove("post");tag.remove("course");
        tag.remove("waterlogged");
        if(art==1) {
            int yaw=oldForm!=null&&oldForm.contains("rotation",8)?integer(oldForm,"rotation",7)|1:(((int)Math.floor(context.getPlayerYaw()/90)+4)*2+1)&7;
            tag.putString("rotation",Integer.toString(yaw));tag.putString("connections","manual");tag.putString("up","true");
            for(Direction direction:Direction.Type.HORIZONTAL)tag.putString(direction.asString(),"none");
        } else if(art==6&&tag.contains("rotation",8))tag.putString("rotation",Integer.toString(integer(tag,"rotation",7)&6));
        ItemPlacementContext prepared=new ItemPlacementContext(context){@Override public ItemStack getStack(){return working;}};
        ActionResult result=super.place(prepared);
        if(result.isAccepted())context.getStack().decrement(Math.max(0,context.getStack().getCount()-working.getCount()));
        return result;
    }
    @Override protected BlockState getPlacementState(ItemPlacementContext context) {
        BlockPos pos=context.getBlockPos();if(!context.getWorld().isChunkLoaded(pos))return null;
        BlockState state=getBlock().getPlacementState(context);if(state==null)return null;
        NbtCompound tag=context.getStack().getSubNbt("BlockStateTag");
        if(state.contains(PrototypeWallBlock.MATERIAL))state=state.with(PrototypeWallBlock.MATERIAL,material(context.getStack()));
        state=state.with(PrototypeWallBlock.PROFILE,profile(context.getStack()));
        if(tag!=null&&"manual".equals(tag.getString("connections"))) {
            state=state.with(PrototypeWallBlock.CONNECTIONS,PrototypeWallBlock.Connections.MANUAL).with(PrototypeWallBlock.ROTATION,integer(tag,"rotation",7)).with(PrototypeWallBlock.POST,"true".equals(tag.getString("up")));
            for(Direction direction:Direction.Type.HORIZONTAL)state=state.with(PrototypeWallBlock.property(direction),WallShape.valueOf(tag.getString(direction.asString()).toUpperCase(java.util.Locale.ROOT)));
        } else {
            int yaw=PrototypeWallBlock.diagonalPost(state)?(((int)Math.floor(context.getPlayerYaw()/90)+4)*2+1)&7:(((int)Math.floor(context.getPlayerYaw()/90+0.5)+2)*2)&7;
            if((yaw&1)==0)state=((PrototypeWallBlock)getBlock()).reconnect(state.with(PrototypeWallBlock.ROTATION,yaw),context.getWorld(),pos);
            else state=state.with(PrototypeWallBlock.CONNECTIONS,PrototypeWallBlock.Connections.MANUAL).with(PrototypeWallBlock.ROTATION,yaw);
        }
        state=PrototypeWallBlock.canonicalForm(state);
        if(!canPlace(context,state))return null;
        if(!context.getWorld().doesNotIntersectEntities(null,state.getCollisionShape(context.getWorld(),pos).offset(pos.getX(),pos.getY(),pos.getZ())))return null;
        String physicalConflict=dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics.ordinaryPlacementConflict(context.getWorld(),
                state.getCollisionShape(context.getWorld(),pos).getBoundingBoxes().stream().map(box->box.offset(pos)).toList(),null);
        if(physicalConflict!=null){if(context.getPlayer()!=null&&!context.getWorld().isClient)context.getPlayer().sendMessage(Text.literal("Ограда не установлена: твёрдое пересечение · "+physicalConflict),true);return null;}
        return state;
    }
    private static boolean validInteger(NbtCompound tag,String name,int maximum) {
        if(!tag.contains(name,8))return false;try{int value=Integer.parseInt(tag.getString(name));return value>=0&&value<=maximum;}catch(NumberFormatException failure){return false;}
    }
    private static int integer(NbtCompound tag,String name,int maximum){return tag!=null&&validInteger(tag,name,maximum)?Integer.parseInt(tag.getString(name)):0;}
    public static int material(ItemStack stack){if(stack.getItem() instanceof BlockItem item&&item.getBlock() instanceof FixedMaterialWallBlock fixed)return fixed.material();NbtCompound tag=stack.getSubNbt("BlockStateTag");return tag!=null&&validInteger(tag,"material",7)?integer(tag,"material",7):6;}
    public static PrototypeWallBlock.Profile profile(ItemStack stack){NbtCompound tag=stack.getSubNbt("BlockStateTag");return tag!=null&&"alt".equals(tag.getString("profile"))?PrototypeWallBlock.Profile.ALT:PrototypeWallBlock.Profile.BASE;}
    public static PrototypeWallBlock.Course course(ItemStack stack){NbtCompound tag=stack.getSubNbt("BlockStateTag");if(tag!=null){if("tall".equals(tag.getString("course")))return PrototypeWallBlock.Course.TALL;for(Direction direction:Direction.Type.HORIZONTAL)if("tall".equals(tag.getString(direction.asString())))return PrototypeWallBlock.Course.TALL;}return PrototypeWallBlock.Course.LOW;}
    @Override public Text getName(ItemStack stack){return DebugCatalogue.itemName(stack,Text.literal("Ограда из полированного глубинного сланца"));}
    @Override public void appendTooltip(ItemStack stack,net.minecraft.world.World world,List<Text> lines,TooltipContext context) {
        super.appendTooltip(stack,world,lines,context);
        DebugCatalogue.itemTooltip(stack,lines);
        lines.add(Text.literal("Профиль "+profile(stack).asString().toUpperCase()+"; ALT без отдельного пакета использует BASE").formatted(Formatting.GRAY));
        lines.add(Text.literal(material(stack)==1?"Диагональный столб: только 45°, без соединения с соседями.":"Обычная ограда: соединения и высота сторон по соседям.").formatted(Formatting.GRAY));
    }
}
