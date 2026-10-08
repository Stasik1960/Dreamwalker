package dev.dreamwalker.bloodbornedw.architecture.ladder_source;

import net.fabricmc.api.*;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.util.math.Direction;

@Environment(EnvType.CLIENT)
public final class SourceLadderClient {
    private SourceLadderClient(){}
    /** Original mounting transform for ordinary placed sections and source pairs, before the optional45 turn. */
    public static BakedModel wrap(BakedModel model,ModelIdentifier id){
        if(model==null||!id.getNamespace().equals("bloodborne_dw")||!(id.getPath().equals("prototype_ladder")||id.getPath().startsWith("prototype_ladder_art_")))return model;
        // Fabric also offers the item root as prototype_ladder#inventory.
        // It has no physical facing/backing; retain its authored display and overrides.
        if(id.getVariant().equals("inventory"))return model;
        java.util.List<String> properties=java.util.Arrays.asList(id.getVariant().split(","));
        for(Direction direction:Direction.Type.HORIZONTAL)if(properties.contains("facing="+direction.asString()))return new SourceLadderBakedModel(model,direction);
        throw new IllegalStateException("Ladder mount model lacks physical facing: "+id);
    }
}
