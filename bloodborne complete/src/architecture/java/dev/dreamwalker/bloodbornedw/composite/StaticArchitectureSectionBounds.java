package dev.dreamwalker.bloodbornedw.composite;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** Only an existing native section under compilation retains this one flag;
 * there is no root/position index or per-world mesh registry. */
@Environment(EnvType.CLIENT)
public final class StaticArchitectureSectionBounds {
    public interface Section {void staticArchitectureBounds(BlockPos expectedOrigin,Box bounds);}
    private static final class Build {final BlockPos origin;Box bounds;Build(BlockPos origin){this.origin=origin.toImmutable();bounds=nativeBox(origin);}}
    private static final ThreadLocal<Build> BUILD=new ThreadLocal<>();
    private StaticArchitectureSectionBounds(){}
    public static void begin(BlockPos origin){BUILD.set(new Build(origin));}
    public static Box nativeBox(BlockPos origin){return new Box(origin.getX(),origin.getY(),origin.getZ(),origin.getX()+16,origin.getY()+16,origin.getZ()+16);}
    public static Box include(Box bounds,BlockPos cell,StaticArchitectureCatalog.Recipe recipe){
        if(recipe.resident()<0)return bounds;
        return bounds.union(recipe.fragments().get(recipe.resident()).pose().visualBounds().offset(cell));
    }
    public static void root(BlockPos cell,StaticArchitectureCatalog.Recipe recipe){Build build=BUILD.get();if(build!=null)build.bounds=include(build.bounds,cell,recipe);}
    public static void end(Section section){Build build=BUILD.get();BUILD.remove();if(build!=null)section.staticArchitectureBounds(build.origin,build.bounds);}
}
