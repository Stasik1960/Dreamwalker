package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.block.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;

/** NEW assembly connection/art states beyond sixteen; legacy blocks keep their state space. */
public final class ImportedArtworkBlock extends CompositeRootBlock {
    public static final IntProperty ART_BANK=IntProperty.of("art_bank",0,7);
    private static final java.util.Set<String> BANKED=loadBanks();
    private static java.util.Set<String> loadBanks(){
        var input=ImportedArtworkBlock.class.getResourceAsStream("/bloodborne_dw/imported-type-aliases.json");if(input==null)return java.util.Set.of();
        try(var reader=new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)){
            var doc=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();var result=new java.util.HashSet<String>();
            for(var element:doc.getAsJsonArray("groups")){var row=element.getAsJsonObject();if(row.has("poseCount")&&row.get("poseCount").getAsInt()>16){String id=row.get("primaryRegistryId").getAsString();if(!id.startsWith("bloodborne_dw:source_")||row.get("poseCount").getAsInt()>128)throw new IllegalArgumentException("Unreviewed NEW art bank: "+id);result.add(id.substring("bloodborne_dw:".length()));}}
            return java.util.Set.copyOf(result);
        }catch(java.io.IOException failure){throw new IllegalStateException("Cannot read NEW art banks",failure);}
    }
    public static boolean isBankedPath(String path){return BANKED.contains(path);}
    public ImportedArtworkBlock(CompositeSpec spec){super(spec);setDefaultState(getDefaultState().with(ART_BANK,0));}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){super.appendProperties(builder);builder.add(ART_BANK);}
    public static int variant(BlockState state){return state.get(ART_BANK)*16+state.get(VARIANT);}
}
