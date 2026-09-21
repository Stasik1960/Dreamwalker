package dev.dreamwalker.bloodborneblocks;

import com.google.gson.Gson;
import net.minecraft.util.math.BlockPos;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared integer-cell transform contract. Mesh coordinates are never rotated here. */
public final class LogicalTransform {
 private static final Gson GSON=new Gson();
 static final class Data {int schemaVersion;Map<String,int[][]> rotations;List<Vector> vectors;}
 static final class Vector {int rotation;int[] placement_cell,anchor_cell,expected_master;}
 private static volatile Data data;
 private LogicalTransform() {}
 static void loadAndValidate(){
  try(InputStream in=LogicalTransform.class.getResourceAsStream("/bloodborne_blocks/logical/transform-v2.json")){
   if(in==null)throw new IllegalStateException("Missing logical transform-v2.json");Data loaded=GSON.fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),Data.class);
   if(loaded==null||loaded.schemaVersion!=2||loaded.rotations==null||loaded.vectors==null)throw new IllegalStateException("Invalid transform-v2 schema");
   for(int rotation:List.of(0,90,180,270)){int[][] matrix=loaded.rotations.get(Integer.toString(rotation));if(!validMatrix(matrix)||!Arrays.deepEquals(matrix,expected(rotation)))throw new IllegalStateException("Invalid transform matrix "+rotation);}
   data=loaded;for(Vector vector:loaded.vectors){requireCell(vector.placement_cell,"placement");requireCell(vector.anchor_cell,"anchor");requireCell(vector.expected_master,"expected");if(!List.of(0,90,180,270).contains(vector.rotation))throw new IllegalStateException("Invalid transform vector rotation");BlockPos actual=masterOrigin(vector.placement_cell,vector.anchor_cell,vector.rotation);if(actual.getX()!=vector.expected_master[0]||actual.getY()!=vector.expected_master[1]||actual.getZ()!=vector.expected_master[2])throw new IllegalStateException("Invalid transform vector");}
  }catch(IOException|RuntimeException e){throw e instanceof IllegalStateException?(IllegalStateException)e:new IllegalStateException("Cannot load transform-v2",e);}
 }
 private static boolean validMatrix(int[][] matrix){return matrix!=null&&matrix.length==3&&Arrays.stream(matrix).allMatch(row->row!=null&&row.length==3);}
 private static int[][] expected(int r){return switch(r){case 0->new int[][]{{1,0,0},{0,1,0},{0,0,1}};case 90->new int[][]{{0,0,-1},{0,1,0},{1,0,0}};case 180->new int[][]{{-1,0,0},{0,1,0},{0,0,-1}};default->new int[][]{{0,0,1},{0,1,0},{-1,0,0}};};}
 static BlockPos masterOrigin(BlockPos placement,int[] anchor,int rotation){return masterOrigin(new int[]{placement.getX(),placement.getY(),placement.getZ()},anchor,rotation);}
 static BlockPos masterOrigin(int[] placement,int[] anchor,int rotation){requireCell(placement,"placement");requireCell(anchor,"anchor");int[][] matrix=matrix(rotation);int x=matrix[0][0]*anchor[0]+matrix[0][1]*anchor[1]+matrix[0][2]*anchor[2],y=matrix[1][0]*anchor[0]+matrix[1][1]*anchor[1]+matrix[1][2]*anchor[2],z=matrix[2][0]*anchor[0]+matrix[2][1]*anchor[1]+matrix[2][2]*anchor[2];return new BlockPos(placement[0]-x,placement[1]-y,placement[2]-z);}
 private static int[][] matrix(int rotation){Data current=data;if(current==null)throw new IllegalStateException("Logical transforms not loaded");int[][] result=current.rotations.get(Integer.toString(rotation));if(result==null)throw new IllegalArgumentException("Unsupported rotation "+rotation);return result;}
 static void requireCell(int[] cell,String name){if(cell==null||cell.length!=3)throw new IllegalStateException("Invalid "+name+" cell");}
}
