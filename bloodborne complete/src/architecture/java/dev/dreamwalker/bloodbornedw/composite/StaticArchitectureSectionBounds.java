package dev.dreamwalker.bloodbornedw.composite;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;

/** Only an existing native section under compilation retains this one flag;
 * there is no root/position index or per-world mesh registry. */
@Environment(EnvType.CLIENT)
public final class StaticArchitectureSectionBounds {
    public interface Section {void staticArchitectureBounds(BlockPos expectedOrigin,boolean present);}
    private static final class Build {final BlockPos origin;boolean present;Build(BlockPos origin){this.origin=origin.toImmutable();}}
    private static final ThreadLocal<Build> BUILD=new ThreadLocal<>();
    private StaticArchitectureSectionBounds(){}
    public static void begin(BlockPos origin){BUILD.set(new Build(origin));}
    public static void root(){Build build=BUILD.get();if(build!=null)build.present=true;}
    public static void end(Section section){Build build=BUILD.get();BUILD.remove();if(build!=null&&build.present)section.staticArchitectureBounds(build.origin,true);}
}
