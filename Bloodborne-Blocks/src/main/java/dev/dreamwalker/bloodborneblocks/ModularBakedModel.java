package dev.dreamwalker.bloodborneblocks;

import net.minecraft.client.render.model.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import java.util.*;
import java.util.function.Function;

/** Immutable vanilla-adapter model for a cell-local modular mesh. */
final class ModularBakedModel extends BasicBakedModel {
 private static final int AUTUMN_TINT_PREFIX=0x01000000;
 private static final float EPSILON=1.0e-6F;
 record Parts(List<BakedQuad> general,Map<Direction,List<BakedQuad>> faces) {}
 /** Composite cells share many identical faces. Keep their vertex buffers once
  * per resource reload, while preserving atlas/sprite and face distinctions. */
 static final class QuadPool {
  private final Map<QuadKey,BakedQuad> quads=new HashMap<>();
  synchronized BakedQuad intern(int[] vertices,Direction normal,Sprite sprite,int rgb){
   QuadKey key=new QuadKey(vertices,normal,sprite,rgb);
   return quads.computeIfAbsent(key,k->new BakedQuad(vertices,autumnTint(rgb),normal,sprite,true));
  }
 }
 private static final class QuadKey {
  final int[] vertices;final Direction normal;final Sprite sprite;final int rgb;final int hash;
  QuadKey(int[] vertices,Direction normal,Sprite sprite,int rgb){this.vertices=vertices;this.normal=normal;this.sprite=sprite;this.rgb=rgb;hash=31*(31*(31*Arrays.hashCode(vertices)+normal.ordinal())+System.identityHashCode(sprite))+rgb;}
  public int hashCode(){return hash;}
  public boolean equals(Object other){return other instanceof QuadKey k&&normal==k.normal&&sprite==k.sprite&&rgb==k.rgb&&Arrays.equals(vertices,k.vertices);}
 }

 static boolean isAutumnTint(int tintIndex){return (tintIndex&0xFF000000)==AUTUMN_TINT_PREFIX;}
 static int tintColor(int tintIndex){return tintIndex&0xFFFFFF;}
 private static int autumnTint(int rgb){return AUTUMN_TINT_PREFIX|(rgb&0xFFFFFF);}

 private ModularBakedModel(Parts parts,BakedModel delegate){
  super(parts.general,parts.faces,delegate.useAmbientOcclusion(),delegate.isSideLit(),delegate.hasDepth(),delegate.getParticleSprite(),delegate.getTransformation(),delegate.getOverrides());
 }

 static Parts bake(ModularMeshData.Mesh mesh,int clockwiseTurns,Function<SpriteIdentifier,Sprite> textures,QuadPool pool){
  return bake(mesh,clockwiseTurns,textures,pool,true);
 }
 static Parts bake(ModularMeshData.Mesh mesh,int clockwiseTurns,Function<SpriteIdentifier,Sprite> textures,QuadPool pool,boolean cellLocal){
  return bake(mesh,clockwiseTurns,textures,pool,cellLocal,0xFFFFFF,false,null);
 }
 static Parts bake(ModularMeshData.Mesh mesh,int clockwiseTurns,Function<SpriteIdentifier,Sprite> textures,QuadPool pool,boolean cellLocal,int rgb,boolean foliage,Identifier leafTexture){
  List<BakedQuad> general=new ArrayList<>();Map<Direction,List<BakedQuad>> faces=new EnumMap<>(Direction.class);Map<String,Sprite> sprites=new HashMap<>();
  for(Direction direction:Direction.values())faces.put(direction,new ArrayList<>());
  for(ModularMeshData.Polygon polygon:mesh.polygons){
   Sprite sprite=sprites.computeIfAbsent(polygon.texture,key->textures.apply(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,new Identifier(key))));
   int count=polygon.vertexCount();if(count==3)add(polygon,0,1,2,2,clockwiseTurns,sprite,general,faces,pool,cellLocal,rgb);
   else if(count==4&&area(polygon,0,1,2,clockwiseTurns)>=EPSILON)add(polygon,0,1,2,3,clockwiseTurns,sprite,general,faces,pool,cellLocal,rgb);
   else if(count==4){add(polygon,0,1,3,3,clockwiseTurns,sprite,general,faces,pool,cellLocal,rgb);add(polygon,1,2,3,3,clockwiseTurns,sprite,general,faces,pool,cellLocal,rgb);}
   else for(int i=1;i+1<count;i++)add(polygon,0,i,i+1,i+1,clockwiseTurns,sprite,general,faces,pool,cellLocal,rgb);
  }
  if(foliage&&leafTexture!=null)addLeaves(mesh,clockwiseTurns,textures.apply(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,leafTexture)),general,pool,cellLocal,rgb);
  Map<Direction,List<BakedQuad>> immutableFaces=new EnumMap<>(Direction.class);faces.forEach((direction,quads)->immutableFaces.put(direction,List.copyOf(quads)));
  return new Parts(List.copyOf(general),Collections.unmodifiableMap(immutableFaces));
 }
 static BakedModel withDelegate(Parts parts,BakedModel delegate){return new ModularBakedModel(parts,delegate);}

 private static void add(ModularMeshData.Polygon polygon,int i0,int i1,int i2,int i3,int turns,Sprite sprite,List<BakedQuad> general,Map<Direction,List<BakedQuad>> faces,QuadPool pool,boolean cellLocal,int rgb){
  float ax=x(polygon,i0,turns),ay=value(polygon,i0,1),az=z(polygon,i0,turns);
  float bx=x(polygon,i1,turns),by=value(polygon,i1,1),bz=z(polygon,i1,turns);
  float cx=x(polygon,i2,turns),cy=value(polygon,i2,1),cz=z(polygon,i2,turns);
  float nx=(by-ay)*(cz-az)-(bz-az)*(cy-ay),ny=(bz-az)*(cx-ax)-(bx-ax)*(cz-az),nz=(bx-ax)*(cy-ay)-(by-ay)*(cx-ax);
  float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);if(length<EPSILON)return;nx/=length;ny/=length;nz/=length;
  Direction normal=Direction.getFacing(nx,ny,nz);int packedNormal=packNormal(nx,ny,nz);int[] data=new int[32];
  for(int vertex=0;vertex<4;vertex++){
   int index=switch(vertex){case 0->i0;case 1->i1;case 2->i2;default->i3;},base=vertex*8;
   data[base]=Float.floatToRawIntBits(x(polygon,index,turns));data[base+1]=Float.floatToRawIntBits(value(polygon,index,1));data[base+2]=Float.floatToRawIntBits(z(polygon,index,turns));data[base+3]=0xFFFFFFFF;
   data[base+4]=Float.floatToRawIntBits(sprite.getFrameU(value(polygon,index,3)));data[base+5]=Float.floatToRawIntBits(sprite.getFrameV(value(polygon,index,4)));data[base+6]=0;data[base+7]=packedNormal;
  }
  BakedQuad quad=pool.intern(data,normal,sprite,rgb);Direction cull=cellLocal?cullFace(polygon,i0,i1,i2,i3,turns,normal):null;if(cull==null)general.add(quad);else faces.get(cull).add(quad);
 }

 private static void addLeaves(ModularMeshData.Mesh mesh,int turns,Sprite sprite,List<BakedQuad> general,QuadPool pool,boolean cellLocal,int rgb){
  Bounds bounds=Bounds.of(mesh,cellLocal);if(bounds==null)return;float minY=bounds.minY+(bounds.maxY-bounds.minY)*.72F,maxY=bounds.maxY;
  if(maxY-minY<EPSILON||bounds.maxX-bounds.minX<EPSILON||bounds.maxZ-bounds.minZ<EPSILON)return;
  float inset=Math.min(.03F,Math.min(bounds.maxX-bounds.minX,bounds.maxZ-bounds.minZ)*.08F);
  addLeafQuad(bounds.minX+inset,minY,bounds.maxZ-inset,bounds.maxX-inset,maxY,bounds.minZ+inset,turns,sprite,general,pool,rgb);
  addLeafQuad(bounds.minX+inset,minY,bounds.minZ+inset,bounds.maxX-inset,maxY,bounds.maxZ-inset,turns,sprite,general,pool,rgb);
  if(!cellLocal)addLeafQuad((bounds.minX+bounds.maxX)*.5F,minY,bounds.minZ+inset,(bounds.minX+bounds.maxX)*.5F,maxY,bounds.maxZ-inset,turns,sprite,general,pool,rgb);
 }
 private static void addLeafQuad(float ax,float ay,float az,float bx,float by,float bz,int turns,Sprite sprite,List<BakedQuad> general,QuadPool pool,int rgb){
  float[] point={ax,ay,az,bx,by,bz};float axr=rotateX(point[0],point[2],turns),azr=rotateZ(point[0],point[2],turns),bxr=rotateX(point[3],point[5],turns),bzr=rotateZ(point[3],point[5],turns);
  float nx=-(bzr-azr)*(by-ay),ny=0,nz=(bxr-axr)*(by-ay);float length=(float)Math.sqrt(nx*nx+nz*nz);if(length<EPSILON)return;nx/=length;nz/=length;
  int[] data=new int[32];float[] positions={axr,ay,azr,bxr,ay,bzr,bxr,by,bzr,axr,by,azr};for(int vertex=0;vertex<4;vertex++){int base=vertex*8;data[base]=Float.floatToRawIntBits(positions[vertex*3]);data[base+1]=Float.floatToRawIntBits(positions[vertex*3+1]);data[base+2]=Float.floatToRawIntBits(positions[vertex*3+2]);data[base+3]=0xFFFFFFFF;data[base+4]=Float.floatToRawIntBits(sprite.getFrameU((vertex==1||vertex==2)?16:0));data[base+5]=Float.floatToRawIntBits(sprite.getFrameV(vertex>=2?16:0));data[base+6]=0;data[base+7]=packNormal(nx,ny,nz);}
  general.add(pool.intern(data,Direction.getFacing(nx,ny,nz),sprite,rgb));int[] reverse=new int[32];for(int vertex=0;vertex<4;vertex++)System.arraycopy(data,(3-vertex)*8,reverse,vertex*8,8);int reverseNormal=packNormal(-nx,-ny,-nz);for(int vertex=0;vertex<4;vertex++)reverse[vertex*8+7]=reverseNormal;general.add(pool.intern(reverse,Direction.getFacing(-nx,-ny,-nz),sprite,rgb));
 }
 private static int abgr(int rgb){return 0xFF000000|((rgb&255)<<16)|(rgb&0xFF00)|((rgb>>>16)&255);}
 private static float rotateX(float x,float z,int turns){return switch(turns&3){case 1->1-z;case 2->1-x;case 3->z;default->x;};}
 private static float rotateZ(float x,float z,int turns){return switch(turns&3){case 1->x;case 2->1-z;case 3->1-x;default->z;};}
 private record Bounds(float minX,float minY,float minZ,float maxX,float maxY,float maxZ){
  static Bounds of(ModularMeshData.Mesh mesh,boolean cellLocal){float minX=Float.MAX_VALUE,minY=Float.MAX_VALUE,minZ=Float.MAX_VALUE,maxX=-Float.MAX_VALUE,maxY=-Float.MAX_VALUE,maxZ=-Float.MAX_VALUE;float globalMinY=Float.MAX_VALUE,globalMaxY=-Float.MAX_VALUE;for(ModularMeshData.Polygon polygon:mesh.polygons)for(int vertex=0;vertex<polygon.vertexCount();vertex++){globalMinY=Math.min(globalMinY,value(polygon,vertex,1));globalMaxY=Math.max(globalMaxY,value(polygon,vertex,1));}float cutoff=globalMinY+(globalMaxY-globalMinY)*.65F;for(ModularMeshData.Polygon polygon:mesh.polygons){float average=0;for(int vertex=0;vertex<polygon.vertexCount();vertex++)average+=value(polygon,vertex,1);if(cellLocal&&average/polygon.vertexCount()<cutoff)continue;for(int vertex=0;vertex<polygon.vertexCount();vertex++){minX=Math.min(minX,value(polygon,vertex,0));minY=Math.min(minY,value(polygon,vertex,1));minZ=Math.min(minZ,value(polygon,vertex,2));maxX=Math.max(maxX,value(polygon,vertex,0));maxY=Math.max(maxY,value(polygon,vertex,1));maxZ=Math.max(maxZ,value(polygon,vertex,2));}}return minX==Float.MAX_VALUE?null:new Bounds(minX,minY,minZ,maxX,maxY,maxZ);}
 }

 private static float area(ModularMeshData.Polygon polygon,int a,int b,int c,int turns){
  float ax=x(polygon,a,turns),ay=value(polygon,a,1),az=z(polygon,a,turns),bx=x(polygon,b,turns),by=value(polygon,b,1),bz=z(polygon,b,turns),cx=x(polygon,c,turns),cy=value(polygon,c,1),cz=z(polygon,c,turns);
  float nx=(by-ay)*(cz-az)-(bz-az)*(cy-ay),ny=(bz-az)*(cx-ax)-(bx-ax)*(cz-az),nz=(bx-ax)*(cy-ay)-(by-ay)*(cx-ax);return (float)Math.sqrt(nx*nx+ny*ny+nz*nz);
 }
 private static float value(ModularMeshData.Polygon polygon,int vertex,int component){return polygon.vertices[vertex*5+component];}
 private static float x(ModularMeshData.Polygon polygon,int vertex,int turns){float x=value(polygon,vertex,0),z=value(polygon,vertex,2);return switch(turns&3){case 1->1-z;case 2->1-x;case 3->z;default->x;};}
 private static float z(ModularMeshData.Polygon polygon,int vertex,int turns){float x=value(polygon,vertex,0),z=value(polygon,vertex,2);return switch(turns&3){case 1->x;case 2->1-z;case 3->1-x;default->z;};}
 private static int packNormal(float x,float y,float z){return (Math.round(x*127)&255)|((Math.round(y*127)&255)<<8)|((Math.round(z*127)&255)<<16);}
 private static Direction cullFace(ModularMeshData.Polygon polygon,int i0,int i1,int i2,int i3,int turns,Direction normal){
  if(normal==Direction.WEST&&all(polygon,i0,i1,i2,i3,turns,0,0))return normal;if(normal==Direction.EAST&&all(polygon,i0,i1,i2,i3,turns,0,1))return normal;
  if(normal==Direction.DOWN&&all(polygon,i0,i1,i2,i3,turns,1,0))return normal;if(normal==Direction.UP&&all(polygon,i0,i1,i2,i3,turns,1,1))return normal;
  if(normal==Direction.NORTH&&all(polygon,i0,i1,i2,i3,turns,2,0))return normal;if(normal==Direction.SOUTH&&all(polygon,i0,i1,i2,i3,turns,2,1))return normal;return null;
 }
 private static boolean all(ModularMeshData.Polygon polygon,int i0,int i1,int i2,int i3,int turns,int axis,float plane){return at(polygon,i0,turns,axis,plane)&&at(polygon,i1,turns,axis,plane)&&at(polygon,i2,turns,axis,plane)&&at(polygon,i3,turns,axis,plane);}
 private static boolean at(ModularMeshData.Polygon polygon,int index,int turns,int axis,float plane){float coordinate=axis==0?x(polygon,index,turns):axis==1?value(polygon,index,1):z(polygon,index,turns);return Math.abs(coordinate-plane)<=EPSILON;}
}
