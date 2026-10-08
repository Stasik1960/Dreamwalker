package dev.dreamwalker.bloodbornedw.architecture.wall;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import dev.dreamwalker.bloodbornedw.architecture.PrototypeArchitecture;
import dev.dreamwalker.bloodbornedw.architecture.BuildPermissions;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

/** Hooks called by the shared entry point; all registration is scoped to this staged wall. */
public final class PrototypeWallArchitecture {
    public static PrototypeWallBlock WALL;
    public static PrototypeWallItem ITEM;
    public static Item TOOL;
    private static final Map<Integer,PrototypeWallBlock> MATERIAL_BLOCKS=new LinkedHashMap<>();
    public static java.util.Collection<PrototypeWallBlock> blocks(){return List.copyOf(MATERIAL_BLOCKS.values());}
    public static PrototypeWallBlock materialBlock(int material){var block=MATERIAL_BLOCKS.get(material);if(block==null)throw new IllegalArgumentException("Unknown wall material "+material);return block;}
    public static boolean isWall(BlockState state){return state.getBlock() instanceof PrototypeWallBlock;}
    public static BlockState changeMaterial(BlockState state,int material){
        BlockState next=materialBlock(material).getDefaultState().with(PrototypeWallBlock.ROTATION,state.get(PrototypeWallBlock.ROTATION)).with(PrototypeWallBlock.PROFILE,state.get(PrototypeWallBlock.PROFILE))
            .with(PrototypeWallBlock.CONNECTIONS,state.get(PrototypeWallBlock.CONNECTIONS)).with(PrototypeWallBlock.POST,state.get(PrototypeWallBlock.POST)).with(net.minecraft.block.WallBlock.WATERLOGGED,state.get(net.minecraft.block.WallBlock.WATERLOGGED));
        for(var direction:net.minecraft.util.math.Direction.Type.HORIZONTAL)next=next.with(PrototypeWallBlock.property(direction),state.get(PrototypeWallBlock.property(direction)));
        return next;
    }
    private static boolean initialized;
    private PrototypeWallArchitecture(){}
    public static Identifier id(String name){return new Identifier("bloodborne_dw",name);}
    public static void initialize(){
        if(initialized)return;
        WALL=Registry.register(Registries.BLOCK,id("prototype_wall"),new PrototypeWallBlock(AbstractBlock.Settings.create().strength(2F).sounds(BlockSoundGroup.STONE).nonOpaque().pistonBehavior(PistonBehavior.BLOCK)));
        ITEM=Registry.register(Registries.ITEM,id("prototype_wall"),new PrototypeWallItem(WALL,new Item.Settings()));
        MATERIAL_BLOCKS.put(6,WALL);
        for(int material:new int[]{0,1,2,3,4,5,7}){
            Identifier registry=id("prototype_wall_skin_"+material);
            var block=Registry.register(Registries.BLOCK,registry,new FixedMaterialWallBlock(AbstractBlock.Settings.create().strength(2F).sounds(BlockSoundGroup.STONE).nonOpaque().pistonBehavior(PistonBehavior.BLOCK),material));
            Registry.register(Registries.ITEM,registry,new PrototypeWallItem(block,new Item.Settings()));MATERIAL_BLOCKS.put(material,block);
        }
        TOOL=Registry.register(Registries.ITEM,id("wall_builder"),new WallTool());
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            for(String alias:List.of("bb","bloodborne"))dispatcher.register(literal(alias).requires(source->source.hasPermissionLevel(2)).then(literal("wall")
                .then(literal("debug").executes(context->command(context.getSource(),"debug",0)))
                .then(literal("rotate").executes(context->command(context.getSource(),"rotate",0)))
                .then(literal("visual").then(literal("base").executes(context->command(context.getSource(),"base",0))).then(literal("alt").executes(context->command(context.getSource(),"alt",0))).then(literal("toggle").executes(context->command(context.getSource(),"toggle",0))))
                .then(literal("course").then(literal("low").executes(context->command(context.getSource(),"low",0))).then(literal("tall").executes(context->command(context.getSource(),"tall",0))))
                .then(literal("material").then(argument("value",IntegerArgumentType.integer(0,7)).executes(context->command(context.getSource(),"material",IntegerArgumentType.getInteger(context,"value")))))
                .then(literal("connections").then(literal("manual").executes(context->command(context.getSource(),"manual",0))).then(literal("auto").executes(context->command(context.getSource(),"auto",0))))));
        });
        initialized=true;
    }
    public static void initializeClient(){PrototypeWallClient.initializeClient();}
    private static int command(ServerCommandSource source,String operation,int value){
        if(!(source.getEntity() instanceof ServerPlayerEntity player)){source.sendError(Text.literal("Нужен игрок со стенкой под прицелом."));return 0;}
        HitResult ray=player.raycast(6,0,false);if(!(ray instanceof BlockHitResult hit)||ray.getType()!=HitResult.Type.BLOCK)return 0;
        BlockPos pos=hit.getBlockPos();World world=player.getWorld();if(!world.isChunkLoaded(pos))return 0;BlockState before=world.getBlockState(pos);if(!isWall(before))return 0;
        if(operation.equals("debug")){source.sendFeedback(()->Text.literal(DebugCatalogue.prefix(before)+" "+Registries.BLOCK.getId(before.getBlock())+"; root="+pos.toShortString()+"; "+before+"; source post/low texture=1; tall material="+PrototypeWallBlock.material(before)+"; native WallBlock AUTO; collision="+before.getCollisionShape(world,pos).getBoundingBoxes().size()+" boxes; outline="+before.getOutlineShape(world,pos).getBoundingBoxes().size()+" boxes; helpers=0; ALT fallback=BASE; bottom slab=NOT_SUPPORTED"),false);return 1;}
        BlockState next=switch(operation){
            case "rotate"->WALL.rotate45(before);
            case "base"->before.with(PrototypeWallBlock.PROFILE,PrototypeWallBlock.Profile.BASE);
            case "alt"->before.with(PrototypeWallBlock.PROFILE,PrototypeWallBlock.Profile.ALT);
            case "toggle"->before.cycle(PrototypeWallBlock.PROFILE);
            case "low"->WALL.withCourse(before.with(PrototypeWallBlock.CONNECTIONS,PrototypeWallBlock.Connections.MANUAL),PrototypeWallBlock.Course.LOW);
            case "tall"->WALL.withCourse(before.with(PrototypeWallBlock.CONNECTIONS,PrototypeWallBlock.Connections.MANUAL),PrototypeWallBlock.Course.TALL);
            case "material"->changeMaterial(before,value);
            case "manual"->before.with(PrototypeWallBlock.CONNECTIONS,PrototypeWallBlock.Connections.MANUAL);
            case "auto"->WALL.reconnect(before.with(PrototypeWallBlock.CONNECTIONS,PrototypeWallBlock.Connections.AUTO).with(PrototypeWallBlock.ROTATION,0),world,pos);
            default->before;
        };
        return edit(world,pos,before,next,player)?1:0;
    }
    public static boolean edit(World world,BlockPos pos,BlockState before,BlockState next,net.minecraft.entity.player.PlayerEntity player){
        if(!dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.enabled(world))return editInternal(world,pos,before,next,player);
        long started=dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.begin(world,false);
        try{boolean result=editInternal(world,pos,before,next,player);dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.event(world,next,dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.rootId(pos),pos,"change_model_or_orientation",dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.state(before),dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.state(world,pos),result?"COMMITTED":"REJECTED",result?"":"native_wall_edit_rejected; rights_support_entity_or_stale_state",player);return result;}
        finally{dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.finish(world,next,pos,"architecture.wall_edit",started);}
    }
    private static boolean editInternal(World world,BlockPos pos,BlockState before,BlockState next,net.minecraft.entity.player.PlayerEntity player){
        if(BuildPermissions.canEdit(world,player,pos)&&world.isChunkLoaded(pos)&&before.isOf(next.getBlock())&&world.getBlockState(pos).equals(before)&&world.getBlockEntity(pos) instanceof dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity mounted&&!mounted.contributions().isEmpty())return world.isClient||dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.transition((net.minecraft.server.world.ServerWorld)world,mounted.resident(),next,player).outcome()==dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED;
        if(!BuildPermissions.canEdit(world,player,pos)||!world.isChunkLoaded(pos)||!world.isInBuildLimit(pos)||!world.getWorldBorder().contains(pos)
            ||!isWall(before)||!isWall(next)||!world.getBlockState(pos).equals(before)||!next.canPlaceAt(world,pos))return false;
        if(!world.doesNotIntersectEntities(null,next.getCollisionShape(world,pos).offset(pos.getX(),pos.getY(),pos.getZ())))return false;
        return world.isClient||before.equals(next)||world.setBlockState(pos,next,Block.NOTIFY_ALL);
    }
    private static final class WallTool extends Item {
        WallTool(){super(new Settings().maxCount(1));}
        @Override public Text getName(ItemStack stack){return Text.literal("Инструмент ограды · 45° / профиль / материал");}
        @Override public ActionResult useOnBlock(ItemUsageContext context){
            World world=context.getWorld();BlockPos pos=context.getBlockPos();if(!world.isChunkLoaded(pos))return ActionResult.FAIL;
            BlockState before=world.getBlockState(pos);if(!isWall(before))return ActionResult.PASS;var player=context.getPlayer();if(player==null)return ActionResult.FAIL;
            BlockState next=player.isSneaking()?(context.getSide()==net.minecraft.util.math.Direction.DOWN?changeMaterial(before,(PrototypeWallBlock.material(before)+1)%8):before.cycle(PrototypeWallBlock.PROFILE)):WALL.rotate45(before);
            return edit(world,pos,before,next,player)?ActionResult.success(world.isClient):ActionResult.FAIL;
        }
        @Override public void appendTooltip(ItemStack stack,World world,List<Text> lines,net.minecraft.client.item.TooltipContext context){lines.add(Text.literal("ПКМ:45°; присесть+ПКМ:BASE/ALT; присесть+ПКМ снизу:материал"));lines.add(Text.literal("Редактирование:creative или OP2; без разрешений tool не меняет объект."));}
    }
}
