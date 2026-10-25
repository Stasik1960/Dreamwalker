package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.composite.mixin.StaticArchitectureSpriteAccess;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.*;
import net.minecraft.client.render.model.json.*;
import net.minecraft.client.texture.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.joml.Vector3f;

/** Resource-sized metadata only. A face is removed only when its complete
 * outward side is inside a proven closed opaque box. Collision/opacity alone
 * are deliberately insufficient: a window or an invisible helper is not a wall.
 */
public final class StaticArchitectureGeometry {
    private static final double EPS=0.00002;
    private static final double COVER_EPS=0.0000001;
    private static final Map<StaticArchitectureCatalog.Pose,Geometry> CACHE=new ConcurrentHashMap<>();
    /** Baked quads are resource objects, so this cache stays resource-sized and
     * is discarded with the model reload. Identity is intentional: equal vertex
     * arrays can belong to different sprites or reload generations. */
    private static final Map<BakedQuad,Boolean> OPAQUE_QUADS=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<SpriteContents,Boolean> OPAQUE_SPRITES=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<BakedModel,Map<BlockState,Boolean>> NATIVE_CUBES=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final LongAdder tested=new LongAdder(),hidden=new LongAdder();
    private record Face(int axis,int sign,double plane,double a,double b,double c,double d){}
    private record Rect(double a,double b,double c,double d){}
    private record Quad(BakedQuad source,Vector3f[] points,Vector3f normal,Face face){}
    public record Part(BakedModel model,Map<Direction,List<Quad>> sides,List<Quad> general){}
    private record Geometry(List<Part> parts,List<Box> closed,boolean opaque){}
    private StaticArchitectureGeometry(){}
    public static void reload(){CACHE.clear();OPAQUE_QUADS.clear();OPAQUE_SPRITES.clear();NATIVE_CUBES.clear();SourceFlatLight.reload();tested.reset();hidden.reset();}
    public static Map<String,Long> metrics(){
        long opaquePoses=0,closedBoxes=0;
        for(Geometry geometry:CACHE.values()){if(geometry.opaque)opaquePoses++;closedBoxes+=geometry.closed.size();}
        return Map.of("testedQuads",tested.sum(),"hiddenQuads",hidden.sum(),"cachedPoses",(long)CACHE.size(),"opaquePoses",opaquePoses,"closedBoxes",closedBoxes);
    }
    public static boolean opaque(StaticArchitectureCatalog.Pose pose){return geometry(pose).opaque;}
    /** A conclusive per-quad layer proof: both every sampled texture texel and
     * every baked vertex alpha are opaque. Ambiguous layouts remain cutout. */
    public static boolean opaqueQuad(BakedQuad quad){
        if(!opaque(quad))return false;int[] data=quad.getVertexData();
        if(data.length%4!=0)return false;int stride=data.length/4;if(stride<4)return false;
        for(int vertex=0;vertex<4;vertex++)if((data[vertex*stride+3]>>>24)!=255)return false;
        return true;
    }
    private static Geometry geometry(StaticArchitectureCatalog.Pose pose){return CACHE.computeIfAbsent(pose,StaticArchitectureGeometry::bake);}
    private static boolean opaque(BakedQuad quad){return OPAQUE_QUADS.computeIfAbsent(quad,StaticArchitectureGeometry::calculateOpaque);}
    private static boolean calculateOpaque(BakedQuad quad){
        Sprite sprite=quad.getSprite();SpriteContents contents=sprite.getContents();
        NativeImage[] images=((StaticArchitectureSpriteAccess)(Object)contents).staticArchitectureMipmapLevelsImages();
        if(images==null||images.length==0)return false;
        int[] vertices=quad.getVertexData();int stride=vertices.length/4;
        if(vertices.length%4!=0||stride<6)return false;
        double spanU=sprite.getMaxU()-sprite.getMinU(),spanV=sprite.getMaxV()-sprite.getMinV();
        if(!(spanU>0)||!(spanV>0)||!Double.isFinite(spanU)||!Double.isFinite(spanV))return false;
        double minU=Double.POSITIVE_INFINITY,maxU=Double.NEGATIVE_INFINITY,minV=Double.POSITIVE_INFINITY,maxV=Double.NEGATIVE_INFINITY;
        for(int vertex=0;vertex<4;vertex++){
            double u=(Float.intBitsToFloat(vertices[vertex*stride+4])-sprite.getMinU())/spanU;
            double v=(Float.intBitsToFloat(vertices[vertex*stride+5])-sprite.getMinV())/spanV;
            // UV outside this sprite can sample another atlas entry. It is not
            // evidence that this quad is opaque even when the local image is.
            if(!Double.isFinite(u)||!Double.isFinite(v)||u<0||u>=1||v<0||v>=1)return false;
            minU=Math.min(minU,u);maxU=Math.max(maxU,u);minV=Math.min(minV,v);maxV=Math.max(maxV,v);
        }
        // Preserve the cheap, conclusive case. Atlas padding is derived from
        // the opaque edge when every pixel of every supplied mip level is opaque.
        boolean wholeSpriteOpaque=OPAQUE_SPRITES.computeIfAbsent(contents,ignored->{
            for(NativeImage image:images){
                if(image==null)return false;
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getColor(x,y)>>>24)!=255)return false;
            }
            return true;
        });
        if(wholeSpriteOpaque)return true;

        int baseWidth=contents.getWidth(),baseHeight=contents.getHeight();
        if(baseWidth<=0||baseHeight<=0)return false;
        for(int level=0;level<images.length;level++){
            NativeImage image=images[level];
            if(image==null)return false;
            int frameWidth=Math.max(1,baseWidth>>level),frameHeight=Math.max(1,baseHeight>>level);
            if(image.getWidth()%frameWidth!=0||image.getHeight()%frameHeight!=0)return false;
            int tilesX=image.getWidth()/frameWidth,tilesY=image.getHeight()/frameHeight;
            if(tilesX<=0||tilesY<=0)return false;
            // The three standard terrain layers bind the block atlas with
            // blur=false. Their minification is NEAREST or
            // NEAREST_MIPMAP_LINEAR, so each actual mip samples the nearest
            // texel and interpolation occurs only between mip levels. Inspect
            // the exact nearest-texel interval at every supplied level.
            int fromX=(int)Math.floor(minU*frameWidth),toX=(int)Math.floor(maxU*frameWidth);
            int fromY=(int)Math.floor(minV*frameHeight),toY=(int)Math.floor(maxV*frameHeight);
            if(fromX<0||fromY<0||toX>=frameWidth||toY>=frameHeight)return false;
            // Inspect every physical tile, including animation frames not first
            // in the sheet. Any ambiguous or translucent sample keeps the quad
            // on its original render layer and out of closed-box occlusion.
            for(int tileY=0;tileY<tilesY;tileY++)for(int tileX=0;tileX<tilesX;tileX++)
                for(int y=fromY;y<=toY;y++)for(int x=fromX;x<=toX;x++)
                    if((image.getColor(tileX*frameWidth+x,tileY*frameHeight+y)>>>24)!=255)return false;
        }
        return true;
    }
    private static Geometry bake(StaticArchitectureCatalog.Pose pose){
        List<Part> parts=new ArrayList<>();List<Face> faces=new ArrayList<>();boolean allOpaque=!pose.block().spec.translucent;
        MatrixStack matrices=new MatrixStack();var shift=pose.shift().rotate(pose.state().get(CompositeRootBlock.ROTATION));
        matrices.translate(shift.x(),pose.mountY()+shift.y(),shift.z());rotate(matrices,.5,.5,.5,-pose.state().get(CompositeRootBlock.ROTATION)*45,false);
        for(var source:pose.parts()){
            matrices.push();matrices.translate(source.offset().x()/16,source.offset().y()/16,source.offset().z()/16);
            rotate(matrices,source.pivot().x()/16,source.pivot().y()/16,source.pivot().z()/16,-source.yaw(),false);
            rotate(matrices,source.pivot().x()/16,source.pivot().y()/16,source.pivot().z()/16,-source.pitch(),true);
            rotate(matrices,source.extraPivot().x()/16,source.extraPivot().y()/16,source.extraPivot().z()/16,source.extraYaw(),false);
            BakedModel model=MinecraftClient.getInstance().getBakedModelManager().getModel(source.model());
            BlockState renderedState=source.model().getPath().startsWith("remaining/source_emissive/")?pose.state():ImportedSourceLightingV127.renderCarrier(pose.block().spec,pose.state());
            Map<Direction,List<Quad>> sides=new EnumMap<>(Direction.class);List<Quad> general=new ArrayList<>();
            for(int side=-1;side<6;side++){
                Direction direction=side<0?null:Direction.values()[side];List<Quad> quads=new ArrayList<>();
                for(var quad:model.getQuads(renderedState,direction,Random.create(42))){
                    Quad q=quad(quad,matrices.peek());quads.add(q);boolean solid=opaqueQuad(quad);allOpaque&=solid;
                    allOpaque&=solid;
                    if(solid&&!pose.block().spec.translucent&&q.face!=null)faces.add(q.face);
                }
                if(direction==null)general=List.copyOf(quads);else sides.put(direction,List.copyOf(quads));
            }
            parts.add(new Part(model,Map.copyOf(sides),general));matrices.pop();
        }
        Set<Face> planes=new HashSet<>(faces);Set<Box> closed=new HashSet<>();
        // Six matching full rectangles prove a closed cuboid. Inset, diagonal,
        // cutout and incomplete surfaces simply stay in the original mesh.
        for(Face left:faces)if(left.axis==0&&left.sign<0)for(Face right:faces)if(right.axis==0&&right.sign>0&&left.plane<right.plane&&left.a==right.a&&left.b==right.b&&left.c==right.c&&left.d==right.d){
            double x=left.plane,X=right.plane,y=left.a,Y=left.b,z=left.c,Z=left.d;
            if(planes.contains(new Face(1,-1,y,x,X,z,Z))&&planes.contains(new Face(1,1,Y,x,X,z,Z))&&planes.contains(new Face(2,-1,z,x,X,y,Y))&&planes.contains(new Face(2,1,Z,x,X,y,Y)))closed.add(new Box(x,y,z,X,Y,Z));
        }
        return new Geometry(List.copyOf(parts),List.copyOf(closed),allOpaque);
    }
    private static double snap(double n){double value=Math.rint(n*100000)/100000;return value==0?0:value;}
    private static Quad quad(BakedQuad quad,MatrixStack.Entry matrix){
        Vector3f[] points=new Vector3f[4];int[] data=quad.getVertexData();int stride=data.length/4;
        for(int i=0;i<4;i++){var p=new Vector3f(Float.intBitsToFloat(data[i*stride]),Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2]));matrix.getPositionMatrix().transformPosition(p);points[i]=p;}
        Vector3f normal=new Vector3f(quad.getFace().getOffsetX(),quad.getFace().getOffsetY(),quad.getFace().getOffsetZ());matrix.getNormalMatrix().transform(normal).normalize();
        Face face=null;
        for(int axis=0;axis<3;axis++){
            double plane=snap(points[0].get(axis));boolean same=true;for(var p:points)same&=Math.abs(p.get(axis)-plane)<EPS;
            if(!same)continue;int a=axis==0?1:0,b=axis==2?1:2;
            double minA=Double.POSITIVE_INFINITY,maxA=Double.NEGATIVE_INFINITY,minB=Double.POSITIVE_INFINITY,maxB=Double.NEGATIVE_INFINITY;
            for(var p:points){minA=Math.min(minA,snap(p.get(a)));maxA=Math.max(maxA,snap(p.get(a)));minB=Math.min(minB,snap(p.get(b)));maxB=Math.max(maxB,snap(p.get(b)));}
            boolean rectangle=minA<maxA&&minB<maxB;
            Set<String> corners=new HashSet<>();for(var p:points){double u=snap(p.get(a)),v=snap(p.get(b));rectangle&=(u==minA||u==maxA)&&(v==minB||v==maxB);corners.add(u+":"+v);}
            if(rectangle&&corners.size()==4&&Math.abs(normal.get(axis))>0.99999)face=new Face(axis,normal.get(axis)<0?-1:1,plane,minA,maxA,minB,maxB);
        }
        return new Quad(quad,points,normal,face);
    }
    public static BakedModel visible(StaticArchitectureCatalog.Pose pose,int index,BlockRenderView world,BlockPos root){
        Part part=geometry(pose).parts.get(index);return new BakedModel(){
            public List<BakedQuad> getQuads(BlockState state,Direction side,Random random){
                List<BakedQuad> result=new ArrayList<>();for(Quad q:side==null?part.general:part.sides.get(side)){
                    tested.increment();if(occluded(q,world,root))hidden.increment();else result.add(q.source);
                }return result;
            }
            public boolean useAmbientOcclusion(){return part.model.useAmbientOcclusion();}public boolean hasDepth(){return part.model.hasDepth();}public boolean isSideLit(){return part.model.isSideLit();}public boolean isBuiltin(){return false;}
            public Sprite getParticleSprite(){return part.model.getParticleSprite();}public ModelTransformation getTransformation(){return part.model.getTransformation();}public ModelOverrideList getOverrides(){return part.model.getOverrides();}
        };
    }
    private static boolean occluded(Quad q,BlockRenderView world,BlockPos root){
        if(q.face==null)return false;
        Face face=q.face;int firstA=firstCell(root,face.axis,face.a,face.c,true),lastA=firstCell(root,face.axis,face.b,face.d,false);
        int firstB=secondCell(root,face.axis,face.a,face.c,true),lastB=secondCell(root,face.axis,face.b,face.d,false);
        long cells=(long)(lastA-firstA+1)*(lastB-firstB+1);if(cells<=0||cells>64)return false;
        int planeCell=(int)Math.floor(axis(root,face.axis)+face.plane+face.sign*.0001);Rect target=new Rect(face.a,face.b,face.c,face.d);List<Rect> rectangles=new ArrayList<>();
        for(int a=firstA;a<=lastA;a++)for(int b=firstB;b<=lastB;b++){
            BlockPos cell=cell(face.axis,planeCell,a,b);BlockState state=world.getBlockState(cell);
            if(state.getBlock() instanceof StaticArchitectureBlock){
                for(var fragment:StaticArchitectureCatalog.recipe(state).fragments()){
                    if(!fragment.member())continue;var offset=fragment.origin();var actualRoot=cell.add(offset.x(),offset.y(),offset.z());
                    if(!StaticArchitectureCatalog.matchesRoot(world.getBlockState(actualRoot),fragment.pose()))continue;
                    double x=actualRoot.getX()-root.getX(),y=actualRoot.getY()-root.getY(),z=actualRoot.getZ()-root.getZ();
                    for(Box box:geometry(fragment.pose()).closed)addIntersection(rectangles,target,face,box.offset(x,y,z));
                }
            }else if(nativeCube(state,world,cell)){
                addIntersection(rectangles,target,face,new Box(cell.getX()-root.getX(),cell.getY()-root.getY(),cell.getZ()-root.getZ(),cell.getX()-root.getX()+1,cell.getY()-root.getY()+1,cell.getZ()-root.getZ()+1));
            }
        }
        return covered(target,rectangles);
    }
    private static int firstCell(BlockPos root,int axis,double a,double c,boolean first){double value=axis==0?root.getY()+a:root.getX()+a;return (int)Math.floor(Math.nextAfter(value,first?Double.POSITIVE_INFINITY:Double.NEGATIVE_INFINITY));}
    private static int secondCell(BlockPos root,int axis,double a,double c,boolean first){double value=(axis==2?root.getY():root.getZ())+c;return (int)Math.floor(Math.nextAfter(value,first?Double.POSITIVE_INFINITY:Double.NEGATIVE_INFINITY));}
    private static int axis(BlockPos pos,int axis){return axis==0?pos.getX():axis==1?pos.getY():pos.getZ();}
    private static BlockPos cell(int axis,int plane,int a,int b){return axis==0?new BlockPos(plane,a,b):axis==1?new BlockPos(a,plane,b):new BlockPos(a,b,plane);}
    private static void addIntersection(List<Rect> out,Rect target,Face face,Box box){
        double plane=face.plane+face.sign*.0001,min=face.axis==0?box.minX:face.axis==1?box.minY:box.minZ,max=face.axis==0?box.maxX:face.axis==1?box.maxY:box.maxZ;
        if(plane<=min+EPS||plane>=max-EPS)return;
        double a=face.axis==0?box.minY:box.minX,b=face.axis==0?box.maxY:box.maxX,c=face.axis==2?box.minY:box.minZ,d=face.axis==2?box.maxY:box.maxZ;
        Rect clipped=new Rect(Math.max(target.a,a),Math.min(target.b,b),Math.max(target.c,c),Math.min(target.d,d));if(clipped.a<clipped.b-EPS&&clipped.c<clipped.d-EPS)out.add(clipped);
    }
    /** Exact rectangle-union proof by interval boundaries, with no samples. */
    private static boolean covered(Rect target,List<Rect> rectangles){
        for(Rect rectangle:rectangles)if(rectangle.a<=target.a+COVER_EPS&&rectangle.b>=target.b-COVER_EPS&&rectangle.c<=target.c+COVER_EPS&&rectangle.d>=target.d-COVER_EPS)return true;
        if(rectangles.isEmpty())return false;TreeSet<Double> xs=new TreeSet<>();xs.add(target.a);xs.add(target.b);for(Rect rectangle:rectangles){xs.add(Math.max(target.a,rectangle.a));xs.add(Math.min(target.b,rectangle.b));}
        Double previous=null;for(double next:xs){if(previous==null){previous=next;continue;}if(next<=previous+COVER_EPS){previous=next;continue;}List<Rect> strips=new ArrayList<>();for(Rect rectangle:rectangles)if(rectangle.a<=previous+COVER_EPS&&rectangle.b>=next-COVER_EPS)strips.add(rectangle);strips.sort(Comparator.comparingDouble(Rect::c));double edge=target.c;for(Rect strip:strips){if(strip.d<=edge+COVER_EPS)continue;if(strip.c>edge+COVER_EPS)return false;edge=Math.max(edge,strip.d);if(edge>=target.d-COVER_EPS)break;}if(edge<target.d-COVER_EPS)return false;previous=next;}return true;
    }
    private static boolean nativeCube(BlockState state,BlockRenderView world,BlockPos pos){
        if(state.getRenderType()!=net.minecraft.block.BlockRenderType.MODEL||!state.isOpaqueFullCube(world,pos))return false;
        BakedModel model=MinecraftClient.getInstance().getBlockRenderManager().getModel(state);synchronized(NATIVE_CUBES){Map<BlockState,Boolean> states=NATIVE_CUBES.computeIfAbsent(model,ignored->new HashMap<>());return states.computeIfAbsent(state,ignored->calculateNativeCube(model,state));}
    }
    private static boolean calculateNativeCube(BakedModel model,BlockState state){
        var manager=MinecraftClient.getInstance().getBakedModelManager();List<BakedModel> selected=new ArrayList<>();
        if(model.getClass()==BasicBakedModel.class)selected.add(model);
        else if(model.getClass()==MultipartBakedModel.class&&model instanceof MultipartBuilderData graph){
            try{for(var component:graph.dreamwalker$components())if(component.getLeft().test(state)){BakedModel child=component.getRight();if(child==null||child==manager.getMissingModel()||child.getClass()!=BasicBakedModel.class||child.isBuiltin())return false;selected.add(child);}}
            catch(RuntimeException uncertain){return false;}
        }else return false;
        if(selected.isEmpty())return false;for(BakedModel child:selected)if(child==manager.getMissingModel()||child.isBuiltin())return false;
        @SuppressWarnings("unchecked") List<Rect>[][] faces=(List<Rect>[][])new List<?>[3][2];for(int axis=0;axis<3;axis++)for(int sign=0;sign<2;sign++)faces[axis][sign]=new ArrayList<>();
        int count=0;var directions=new ArrayList<Direction>(List.of(Direction.values()));directions.add(null);var identity=new MatrixStack().peek();
        for(BakedModel child:selected)for(Direction direction:directions){var random=Random.create(42);for(BakedQuad source:child.getQuads(state,direction,random)){
            if(++count>256||!opaqueQuad(source))return false;
            Face face=quad(source,identity).face;if(face==null||Math.abs(face.plane-(face.sign<0?0:1))>EPS||face.a<-EPS||face.b>1+EPS||face.c<-EPS||face.d>1+EPS)return false;
            faces[face.axis][face.sign<0?0:1].add(new Rect(face.a,face.b,face.c,face.d));
        }}
        if(count==0)return false;Rect unit=new Rect(0,1,0,1);for(int axis=0;axis<3;axis++)for(int sign=0;sign<2;sign++)if(!covered(unit,faces[axis][sign]))return false;return true;
    }
    private static void rotate(MatrixStack matrices,double x,double y,double z,double degrees,boolean pitch){if(degrees==0)return;matrices.translate(x,y,z);matrices.multiply((pitch?RotationAxis.POSITIVE_X:RotationAxis.POSITIVE_Y).rotationDegrees((float)degrees));matrices.translate(-x,-y,-z);}
}
