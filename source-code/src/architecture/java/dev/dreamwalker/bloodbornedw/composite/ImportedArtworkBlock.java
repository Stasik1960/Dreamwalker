package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.block.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;

/** NEW assembly connection/art states beyond sixteen; legacy blocks keep their state space. */
public class ImportedArtworkBlock extends CompositeRootBlock implements NewStateStorage {
    public static final IntProperty ART_BANK=IntProperty.of("art_bank",0,7);
    private static final java.util.Set<String> BANKED=loadBanks();
    private static java.util.Set<String> loadBanks(){
        var input=ImportedArtworkBlock.class.getResourceAsStream("/bloodborne_dw/imported-type-aliases.json");if(input==null)return java.util.Set.of();
        try(var reader=new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)){
            var doc=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();var result=new java.util.HashSet<String>();
            for(var element:doc.getAsJsonArray("groups")){var row=element.getAsJsonObject();if(row.has("poseCount")&&row.get("poseCount").getAsInt()>16){String id=row.get("primaryRegistryId").getAsString();if(!id.startsWith("bloodborne_dw:source_")||row.get("poseCount").getAsInt()>variantBudget(id.substring("bloodborne_dw:".length())))throw new IllegalArgumentException("Unreviewed NEW art bank: "+id);result.add(id.substring("bloodborne_dw:".length()));}}
            var full=ImportedArtworkBlock.class.getResourceAsStream("/bloodborne_dw/imported-full-map-v127.json");
            if(full!=null)try(var more=new java.io.InputStreamReader(full,java.nio.charset.StandardCharsets.UTF_8)){
                for(var raw:com.google.gson.JsonParser.parseReader(more).getAsJsonObject().getAsJsonArray("groups")){var group=raw.getAsJsonObject();String id=group.get("registryId").getAsString();String path=id.substring("bloodborne_dw:".length());if(!id.startsWith("bloodborne_dw:source_")||group.get("poses").getAsInt()>variantBudget(path))throw new IllegalArgumentException("Unreviewed full-source art bank "+id);result.add(path);}
            }
            return java.util.Set.copyOf(result);
        }catch(java.io.IOException failure){throw new IllegalStateException("Cannot read NEW art banks",failure);}
    }
    public static boolean isBankedPath(String path){return BANKED.contains(path);}
    public static int variantBudget(String path){return path.equals("source_assembly_brick_wall")?480:path.equals("source_assembly_deepslate_brick_wall")?160:128;}
    public static ImportedArtworkBlock create(CompositeSpec spec){return spec.id.getPath().equals("source_assembly_brick_wall")?new Expanded(spec):spec.id.getPath().equals("source_assembly_deepslate_brick_wall")?new ExpandedDeepslate(spec):new ImportedArtworkBlock(spec);}
    public static IntProperty bankProperty(BlockState state){return (IntProperty)state.getBlock().getStateManager().getProperty("art_bank");}
    public ImportedArtworkBlock(CompositeSpec spec){super(spec);setDefaultState(getDefaultState().with(bankProperty(getDefaultState()),0));}
    protected ImportedArtworkBlock(CompositeSpec spec,Settings settings){super(spec,settings);IntProperty bank=bankProperty(getDefaultState());if(bank!=null)setDefaultState(getDefaultState().with(bank,0));}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){super.appendProperties(builder);builder.add(this instanceof Expanded?Expanded.BANK:this instanceof ExpandedDeepslate?ExpandedDeepslate.BANK:ART_BANK);}
    public static int variant(BlockState state){IntProperty bank=bankProperty(state);return (bank==null?0:state.get(bank)*16)+state.get(VARIANT);}
    /** Only the NEW brick-wall family needs thirty banks for exact authored
     * weighted/conditional unions across the complete original city. */
    private static final class Expanded extends ImportedArtworkBlock {
        private static final IntProperty BANK=IntProperty.of("art_bank",0,29);
        private Expanded(CompositeSpec spec){super(spec);}
    }
    private static final class ExpandedDeepslate extends ImportedArtworkBlock {
        private static final IntProperty BANK=IntProperty.of("art_bank",0,9);
        private ExpandedDeepslate(CompositeSpec spec){super(spec);}
    }
}
