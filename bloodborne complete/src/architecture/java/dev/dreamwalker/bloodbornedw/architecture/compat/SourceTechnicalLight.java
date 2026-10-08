package dev.dreamwalker.bloodbornedw.architecture.compat;

import dev.dreamwalker.bloodbornedw.architecture.PrototypeArchitecture;
import net.minecraft.block.AirBlock;
import net.minecraft.block.MapColor;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Narrow compatibility for the two original Hunter Lamp technical light cells.
 * Original Bloodborne 6.0 constructs AirBlock(Material.AIR, lightLevel=9),
 * without Properties.air(). Its isAir=false identity is significant to preservation.
 * No item, block entity, ticker, architecture catalog entry or lamp behavior is added.
 */
public final class SourceTechnicalLight {
    public static final Identifier ID=PrototypeArchitecture.id("source_hunter_lamp_light");
    public static final LegacyAirBlock SOURCE_LIGHT=new LegacyAirBlock();
    private static boolean initialized;
    private SourceTechnicalLight(){}
    public static void initialize(){
        if(initialized)return;
        Registry.register(Registries.BLOCK,ID,SOURCE_LIGHT);
        initialized=true;
    }
    public static final class LegacyAirBlock extends AirBlock {
        private LegacyAirBlock(){
            // Translate removed Material.AIR fields explicitly; preserve original
            // default hasCollision/canOcclude and NORMAL piston settings. AirBlock's
            // empty outline supplies empty inherited collision and occlusion shapes.
            super(Settings.create().mapColor(MapColor.CLEAR).replaceable().notSolid()
                .solidBlock((state,world,pos)->false)
                .suffocates((state,world,pos)->false)
                .blockVision((state,world,pos)->false)
                .luminance(state->9).dropsNothing());
        }
    }
}
