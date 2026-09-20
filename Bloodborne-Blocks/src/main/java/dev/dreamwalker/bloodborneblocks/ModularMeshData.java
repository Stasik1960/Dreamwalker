package dev.dreamwalker.bloodborneblocks;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.util.Identifier;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Client-loaded source polygons for the v2 palette. This class has no client API so data tests can validate it. */
final class ModularMeshData {
 static final class Mesh {final List<Polygon> polygons;Mesh(List<Polygon> polygons){this.polygons=List.copyOf(polygons);}}
 static final class Polygon {
  final String texture;final float[] vertices;
  Polygon(String texture,float[] vertices){this.texture=texture;this.vertices=vertices;}
  int vertexCount(){return vertices.length/5;}
 }

 private ModularMeshData() {}

 static Map<String,Mesh> loadAndValidate(){
  return loadAndValidate("/bloodborne_blocks/v2/meshes.json.gz","m_",0,1);
 }
 /** Logical objects intentionally retain uncut, multi-cell coordinates. */
 static Map<String,Mesh> loadLogicalAndValidate(){
  return loadAndValidate("/bloodborne_blocks/logical/meshes.json.gz","",-64,64);
 }
 private static Map<String,Mesh> loadAndValidate(String path,String prefix,float minimum,float maximum){
  Map<String,String> textures=new HashMap<>();Map<String,Mesh> meshes=new LinkedHashMap<>();
  try(InputStream stream=ModularMeshData.class.getResourceAsStream(path)){
   if(stream==null)throw new IOException("Missing modular meshes.json.gz");
   try(JsonReader reader=new JsonReader(new InputStreamReader(new GZIPInputStream(stream),StandardCharsets.UTF_8))){
    require(reader.peek()==JsonToken.BEGIN_OBJECT,"Invalid modular meshes: expected object");reader.beginObject();
    while(reader.hasNext()){
     String id=reader.nextName();require(prefix.isEmpty()||id.startsWith(prefix),"Invalid modular mesh "+id);
     Mesh mesh=readMesh(reader,id,textures,minimum,maximum);if(meshes.putIfAbsent(id,mesh)!=null)throw new IllegalStateException("Duplicate modular mesh "+id);
    }
    reader.endObject();require(reader.peek()==JsonToken.END_DOCUMENT,"Trailing modular mesh data");
   }
  }catch(IOException|RuntimeException e){throw new IllegalStateException("Cannot load modular meshes",e);}
  return Collections.unmodifiableMap(meshes);
 }

 private static Mesh readMesh(JsonReader reader,String id,Map<String,String> textures,float minimum,float maximum)throws IOException{
  require(reader.peek()==JsonToken.BEGIN_OBJECT,"Invalid modular mesh "+id);reader.beginObject();List<Polygon> polygons=null;
  while(reader.hasNext()){
   String name=reader.nextName();
   if(name.equals("polygons")){require(polygons==null,"Duplicate polygons field "+id);polygons=readPolygons(reader,id,textures,minimum,maximum);}
   else reader.skipValue();
  }
  reader.endObject();require(polygons!=null,"Missing polygons "+id);return new Mesh(polygons);
 }

 private static List<Polygon> readPolygons(JsonReader reader,String id,Map<String,String> textures,float minimum,float maximum)throws IOException{
  require(reader.peek()==JsonToken.BEGIN_ARRAY,"Invalid polygons "+id);reader.beginArray();List<Polygon> result=new ArrayList<>();int index=0;
  while(reader.hasNext())result.add(readPolygon(reader,id,index++,textures,minimum,maximum));reader.endArray();return result;
 }

 private static Polygon readPolygon(JsonReader reader,String id,int polygonIndex,Map<String,String> textures,float minimum,float maximum)throws IOException{
  require(reader.peek()==JsonToken.BEGIN_OBJECT,"Invalid polygon "+label(id,polygonIndex));reader.beginObject();String texture=null;float[] vertices=null;
  while(reader.hasNext()){
   String name=reader.nextName();
   if(name.equals("texture")){require(texture==null&&reader.peek()==JsonToken.STRING,"Invalid texture "+label(id,polygonIndex));texture=reader.nextString();}
   else if(name.equals("vertices")){require(vertices==null,"Duplicate vertices "+label(id,polygonIndex));vertices=readVertices(reader,id,polygonIndex,minimum,maximum);}
   else reader.skipValue();
  }
  reader.endObject();require(texture!=null&&Identifier.tryParse(texture)!=null,"Invalid texture "+label(id,polygonIndex));require(vertices!=null&&vertices.length>=15,"Invalid vertices "+label(id,polygonIndex));
  return new Polygon(textures.computeIfAbsent(texture,key->key),vertices);
 }

 private static float[] readVertices(JsonReader reader,String id,int polygonIndex,float minimum,float maximum)throws IOException{
  require(reader.peek()==JsonToken.BEGIN_ARRAY,"Invalid vertices "+label(id,polygonIndex));reader.beginArray();FloatBuilder packed=new FloatBuilder();int vertexIndex=0;
  while(reader.hasNext()){
   require(reader.peek()==JsonToken.BEGIN_ARRAY,"Invalid vertex "+label(id,polygonIndex)+"["+vertexIndex+"]");reader.beginArray();
   for(int component=0;component<5;component++){
    require(reader.hasNext()&&reader.peek()==JsonToken.NUMBER,"Invalid vertex "+label(id,polygonIndex)+"["+vertexIndex+"]");double value=reader.nextDouble();
    require(Double.isFinite(value),"Non-finite vertex "+label(id,polygonIndex)+"["+vertexIndex+"]");
    require(component<3?(value>=minimum&&value<=maximum):(value>=0&&value<=16),(component<3?"Out-of-range vertex ":"Out-of-range UV ")+label(id,polygonIndex)+"["+vertexIndex+"]");packed.add((float)value);
   }
   require(!reader.hasNext(),"Invalid vertex length "+label(id,polygonIndex)+"["+vertexIndex+"]");reader.endArray();vertexIndex++;
  }
  reader.endArray();require(vertexIndex>=3,"Polygon has fewer than three vertices "+label(id,polygonIndex));return packed.toArray();
 }

 private static String label(String id,int polygon){return id+"["+polygon+"]";}
 private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
 private static final class FloatBuilder {
  private float[] values=new float[20];private int size;
  void add(float value){if(size==values.length)values=Arrays.copyOf(values,size*2);values[size++]=value;}
  float[] toArray(){return Arrays.copyOf(values,size);}
 }
}
