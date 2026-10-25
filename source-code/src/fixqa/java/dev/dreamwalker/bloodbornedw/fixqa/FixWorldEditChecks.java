package dev.dreamwalker.bloodbornedw.fixqa;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.sk89q.worldedit.*;
import com.sk89q.worldedit.extent.clipboard.*;
import com.sk89q.worldedit.extent.clipboard.io.*;
import com.sk89q.worldedit.function.operation.*;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.architecture.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.*;
import net.minecraft.registry.*;
import net.minecraft.server.*;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

final class FixWorldEditChecks {
    private static final BlockPos A=new BlockPos(4096,200,4096),B=A.add(2,0,0),C=A.add(4,0,0);
    private final Path out;private final java.util.function.BiConsumer<Boolean,String> verify;
    FixWorldEditChecks(Path out,java.util.function.BiConsumer<Boolean,String> verify){this.out=out;this.verify=verify;}
    private void check(boolean value,String name){verify.accept(value,name);}
    private com.sk89q.worldedit.world.World adapter(ServerWorld world)throws Exception{
        for(var method:Class.forName("com.sk89q.worldedit.fabric.FabricAdapter").getMethods())if(method.getName().equals("adapt")&&method.getParameterCount()==1&&method.getParameterTypes()[0].isInstance(world)&&com.sk89q.worldedit.world.World.class.isAssignableFrom(method.getReturnType()))return (com.sk89q.worldedit.world.World)method.invoke(null,world);
        throw new IllegalStateException("WorldEdit Fabric adapter not found");
    }
    private static BlockVector3 vec(BlockPos p){return BlockVector3.at(p.getX(),p.getY(),p.getZ());}
    private void paste(Clipboard clipboard,EditSession session,BlockPos pos)throws Exception{Operations.complete(new ClipboardHolder(clipboard).createPaste(session).to(vec(pos)).ignoreAirBlocks(false).build());session.flushSession();}
    public void run(ServerWorld world)throws Exception{
        check(FabricLoader.getInstance().isModLoaded("worldedit"),"real WorldEdit loaded");for(var p:List.of(A,B,C))check(world.getBlockState(p).isAir(),"WorldEdit stand is free");NativeVisuals.write(world,A,14,Block.NOTIFY_ALL);NativeVisuals.write(world,B,9,Block.NOTIFY_ALL);NativeVisuals.write(world,C,9,Block.NOTIFY_ALL);check(world.getBlockState(A).equals(world.getBlockState(B)),"two materials share one physical BlockState");
        var adapter=adapter(world);var region=new CuboidRegion(vec(A),vec(A));var clipboard=new BlockArrayClipboard(region);
        try(var copy=WorldEdit.getInstance().newEditSession(adapter)){Operations.complete(new ForwardExtentCopy(copy,region,vec(A),clipboard,vec(A)));}
        try(var edit=WorldEdit.getInstance().newEditSession(adapter)){paste(clipboard,edit,B);check(NativeVisuals.index(world,B)==14,"WorldEdit paste over identical physics keeps source material");try(var undo=WorldEdit.getInstance().newEditSession(adapter)){edit.undo(undo);undo.flushSession();}check(NativeVisuals.index(world,B)==9,"WorldEdit undo restores destination material");try(var redo=WorldEdit.getInstance().newEditSession(adapter)){edit.redo(redo);redo.flushSession();}check(NativeVisuals.index(world,B)==14,"WorldEdit redo restores copied material");}
        Path file=out.resolve("native-visual.schem");try(var stream=Files.newOutputStream(file);var writer=BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(stream)){writer.write(clipboard);}Clipboard loaded;try(var stream=Files.newInputStream(file);var reader=BuiltInClipboardFormat.SPONGE_SCHEMATIC.getReader(stream)){loaded=reader.read();}try(var edit=WorldEdit.getInstance().newEditSession(adapter)){paste(loaded,edit,C);}check(NativeVisuals.index(world,C)==14,"schematic save/load retains native visual index");
        for(var p:List.of(A,B,C))world.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
    }
}
