package dev.dreamwalker.bloodbornedw.architecture.wall;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/** Hooks called by the shared entry point; all registration is scoped to this staged wall. */
public final class PrototypeWallArchitecture {
    public static PrototypeWallBlock WALL;
    public static PrototypeWallItem ITEM;
    private static final Map<Integer,PrototypeWallBlock> MATERIAL_BLOCKS=new LinkedHashMap<>();
    public static java.util.Collection<PrototypeWallBlock> blocks(){return List.copyOf(MATERIAL_BLOCKS.values());}
    public static PrototypeWallBlock materialBlock(int material){var block=MATERIAL_BLOCKS.get(material);if(block==null)throw new IllegalArgumentException("Unknown wall material "+material);return block;}
    public static boolean isWall(BlockState state){return state.getBlock() instanceof PrototypeWallBlock;}
    public static BlockState changeMaterial(BlockState state,int material){
        if(material!=0&&material!=1&&material!=6)material=(state.get(PrototypeWallBlock.ROTATION)&1)==0?6:1;
        BlockState next=materialBlock(material).getDefaultState().with(PrototypeWallBlock.ROTATION,state.get(PrototypeWallBlock.ROTATION)).with(PrototypeWallBlock.PROFILE,state.get(PrototypeWallBlock.PROFILE))
            .with(PrototypeWallBlock.CONNECTIONS,state.get(PrototypeWallBlock.CONNECTIONS)).with(PrototypeWallBlock.POST,state.get(PrototypeWallBlock.POST)).with(net.minecraft.block.WallBlock.WATERLOGGED,state.get(net.minecraft.block.WallBlock.WATERLOGGED));
        for(var direction:net.minecraft.util.math.Direction.Type.HORIZONTAL)next=next.with(PrototypeWallBlock.property(direction),state.get(PrototypeWallBlock.property(direction)));
        return PrototypeWallBlock.canonicalForm(next);
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
        initialized=true;
    }
    public static void initializeClient(){PrototypeWallClient.initializeClient();}
}
