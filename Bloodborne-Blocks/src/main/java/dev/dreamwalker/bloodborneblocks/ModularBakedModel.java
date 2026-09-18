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
 private static final float EPSILON=1.0e-6F;
 record Parts(List<BakedQuad> general,Map<Direction,List<BakedQuad>> faces) {}
 /** Composite cells share many identical faces. Keep their vertex buffers once
  * per resource reload, while preserving atlas/sprite and face distinctions. */
 static final class QuadPool {
  private final Map<QuadKey,BakedQuad> quads=new HashMap<>();
  synchronized BakedQuad intern(int[] vertices,Direction normal,Sprite sprite){
   QuadKey key=new QuadKey(vertices,normal,sprite);
   return quads.computeIfAbsent(key,k->new BakedQuad(vertices,-1,normal,sprite,true));
  }
 }
 private static final class QuadKey {
  final int[] vertices;final Direction normal;final Sprite sprite;final int hash;
  QuadKey(int[] vertices,Direction normal,Sprite sprite){this.vertices=vertices;this.normal=normal;this.sprite=sprite;hash=31*(31*Arrays.hashCode(vertices)+normal.ordinal())+System.identityHashCode(sprite);}
  public int hashCode(){return hash;}
  public boolean equals(Object other){return other instanceof QuadKey k&&normal==k.normal&&sprite==k.sprite&&Arrays.equals(vertices,k.vertices);}
 }

 private ModularBakedModel(Parts parts,BakedModel delegate){
  super(parts.general,parts.faces,delegate.useAmbientOcclusion(),delegate.isSideLit(),delegate.hasDepth(),delegate.getParticleSprite(),delegate.getTransformation(),delegate.getOverrides());
 }

 static Parts bake(ModularMeshData.Mesh mesh,int clockwiseTurns,Function<SpriteIdentifier,Sprite> textures,QuadPool pool){
  List<BakedQuad> general=new ArrayList<>();Map<Direction,List<BakedQuad>> faces=new EnumMap<>(Direction.class);Map<String,Sprite> sprites=new HashMap<>();
  for(Direction direction:Direction.values())faces.put(direction,new ArrayList<>());
  for(ModularMeshData.Polygon polygon:mesh.polygons){
   Sprite sprite=sprites.computeIfAbsent(polygon.texture,key->textures.apply(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,new Identifier(key))));
   int count=polygon.vertexCount();if(count==3)add(polygon,0,1,2,2,clockwiseTurns,sprite,general,faces,pool);
   else if(count==4&&area(polygon,0,1,2,clockwiseTurns)>=EPSILON)add(polygon,0,1,2,3,clockwiseTurns,sprite,general,faces,pool);
   else if(count==4){add(polygon,0,1,3,3,clockwiseTurns,sprite,general,faces,pool);add(polygon,1,2,3,3,clockwiseTurns,sprite,general,faces,pool);}
   else for(int i=1;i+1<count;i++)add(polygon,0,i,i+1,i+1,clockwiseTurns,sprite,general,faces,pool);
  }
  Map<Direction,List<BakedQuad>> immutableFaces=new EnumMap<>(Direction.class);faces.forEach((direction,quads)->immutableFaces.put(direction,List.copyOf(quads)));
  return new Parts(List.copyOf(general),Collections.unmodifiableMap(immutableFaces));
 }
 static BakedModel withDelegate(Parts parts,BakedModel delegate){return new ModularBakedModel(parts,delegate);}

 private static void add(ModularMeshData.Polygon polygon,int i0,int i1,int i2,int i3,int turns,Sprite sprite,List<BakedQuad> general,Map<Direction,List<BakedQuad>> faces,QuadPool pool){
  float ax=x(polygon,i0,turns),ay=value(polygon,i0,1),az=z(polygon,i0,turns);
  float bx=x(polygon,i1,turns),by=value(polygon,i1,1),bz=z(polygon,i1,turns);
  float cx=x(polygon,i2,turns),cy=value(polygon,i2,1),cz=z(polygon,i2,turns);
  float nx=(by-ay)*(cz-az)-(bz-az)*(cy-ay),ny=(bz-az)*(cx-ax)-(bx-ax)*(cz-az),nz=(bx-ax)*(cy-ay)-(by-ay)*(cx-ax);
  float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);if(length<EPSILON)return;nx/=length;ny/=length;nz/=length;
  Direction normal=Direction.getFacing(nx,ny,nz);int packedNormal=packNormal(nx,ny,nz);int[] data=new int[32];
  for(int vertex=0;vertex<4;vertex++){
   int index=switch(vertex){case 0->i0;case 1->i1;case 2->i2;default->i3;},base=vertex*8;
   data[base]=Float.floatToRawIntBits(x(polygon,index,turns));data[base+1]=Float.floatToRawIntBits(value(polygon,index,1));data[base+2]=Float.floatToRawIntBits(z(polygon,index,turns));data[base+3]=-1;
   data[base+4]=Float.floatToRawIntBits(sprite.getFrameU(value(polygon,index,3)));data[base+5]=Float.floatToRawIntBits(sprite.getFrameV(value(polygon,index,4)));data[base+6]=0;data[base+7]=packedNormal;
  }
  BakedQuad quad=pool.intern(data,normal,sprite);Direction cull=cullFace(polygon,i0,i1,i2,i3,turns,normal);if(cull==null)general.add(quad);else faces.get(cull).add(quad);
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
