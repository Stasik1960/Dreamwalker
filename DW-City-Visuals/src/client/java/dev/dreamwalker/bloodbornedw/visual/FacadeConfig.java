package dev.dreamwalker.bloodbornedw.visual;

import com.google.gson.*;
import java.io.Reader;
import java.util.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Sparse, inclusive regions; no world enumeration or per-position cache. */
final class FacadeConfig {
    static final Identifier RESOURCE = new Identifier("bloodborne_dw", "visuals/facades.json");
    record Region(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int role) {
        boolean contains(BlockPos p) { return p.getX() >= minX && p.getX() <= maxX && p.getY() >= minY && p.getY() <= maxY && p.getZ() >= minZ && p.getZ() <= maxZ; }
    }
    final Identifier dimension;
    final Long2ObjectMap<List<Region>> regionChunks;
    final Set<Identifier> bricks;
    final Map<Identifier, Identifier> rose;
    final float[][] multipliers;
    private FacadeConfig(Identifier d, List<Region> regions, Set<Identifier> b, Map<Identifier, Identifier> m, float[][] c) {
        dimension=d; bricks=Set.copyOf(b); rose=Map.copyOf(m); multipliers=c;
        Long2ObjectMap<List<Region>> chunks=new Long2ObjectOpenHashMap<>();
        for(Region r:regions) for(int x=r.minX>>4;x<=r.maxX>>4;x++) for(int z=r.minZ>>4;z<=r.maxZ>>4;z++) chunks.computeIfAbsent(key(x,z),ignored->new ArrayList<>()).add(r);
        chunks.replaceAll((key,list)->List.copyOf(list)); regionChunks=Long2ObjectMaps.unmodifiable(chunks);
    }
    private static long key(int x,int z) { return ((long)x<<32) | (z&0xffffffffL); }
    static FacadeConfig load() {
        try (Reader reader = MinecraftClient.getInstance().getResourceManager().getResourceOrThrow(RESOURCE).getReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            List<Region> regions = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray("regions")) {
                JsonObject region=element.getAsJsonObject(); JsonArray a=region.getAsJsonArray("min"), b=region.getAsJsonArray("max");
                int role=switch(region.get("role").getAsString()) { case "rose" -> 1; case "sage" -> 2; case "sand" -> 3; default -> throw new IllegalArgumentException("Unknown facade role"); };
                Region r=new Region(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt(),b.get(0).getAsInt(),b.get(1).getAsInt(),b.get(2).getAsInt(),role);
                if(r.minX>r.maxX||r.minY>r.maxY||r.minZ>r.maxZ) throw new IllegalArgumentException("Invalid facade bounds"); regions.add(r);
            }
            Set<Identifier> bricks=new HashSet<>(); for(JsonElement e:json.getAsJsonArray("brickSprites")) bricks.add(new Identifier(e.getAsString()));
            Map<Identifier,Identifier> rose=new HashMap<>(); for(var e:json.getAsJsonObject("roseSprites").entrySet()) rose.put(new Identifier(e.getKey()),new Identifier(e.getValue().getAsString()));
            float[][] colors={{1,1,1},{1,1,1},{1,1,1},{1,1,1}}; JsonObject multipliers=json.getAsJsonObject("roleMultipliers");
            for(int role=2;role<=3;role++) { JsonArray values=multipliers.getAsJsonArray(role==2?"sage":"sand"); for(int i=0;i<3;i++){ float v=values.get(i).getAsFloat(); if(!Float.isFinite(v)||v<0||v>1)throw new IllegalArgumentException("Invalid facade multiplier"); colors[role][i]=v; } }
            return new FacadeConfig(new Identifier(json.get("dimension").getAsString()),regions,bricks,rose,colors);
        } catch (Exception ex) { throw new IllegalStateException("Cannot load facade configuration " + RESOURCE,ex); }
    }
    int role(BlockPos pos) { List<Region> regions=regionChunks.get(key(pos.getX()>>4,pos.getZ()>>4)); if(regions==null)return 0; int result=0; for(Region region:regions) if(region.contains(pos)) { if(region.role==1)return 1; if(result==0)result=region.role; } return result; }
}

