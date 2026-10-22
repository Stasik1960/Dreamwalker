package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.shape.VoxelShape;

/** A finite resource catalog of reusable cell parts. There are no world
 * positions, identities, histories, reverse-conversion data or world registry.
 * Native chunk palettes store only a bank and a part number. */
public final class StaticArchitectureCatalog {
    public static final int BANK_SIZE=128;
    public static final String RESOURCE="/bloodborne_dw/static-architecture-v1274.json";
    public static final class Pose {
        private final String key;
        private final CompositeRootBlock block;
        private final BlockState state;
        private final CompositeSourceShift.Shift shift;
        private final double mountY;
        private final Map<Cell,Footprint> footprint;
        private final Map<Cell,List<Box>> outline;
        private final net.minecraft.util.math.Box visualBounds;
        private Pose(JsonObject row){
            key=row.get("key").getAsString();
            Identifier id=new Identifier(row.get("registryId").getAsString());
            if(!id.getNamespace().equals("bloodborne_dw")||!id.getPath().startsWith("source_"))throw new IllegalArgumentException("Static architecture source scope");
            block=CompositeArchitecture.kindBlock(id.getPath());
            if(block instanceof ImportedStairBlock||block instanceof ImportedLadderBlock||block instanceof ThinWindowRootBlock||block.spec.openable||block.spec.functionalDoorPhysics||block.spec.importedClimbingBounds!=null||block.spec.requiredSupport)throw new IllegalArgumentException("Functional architecture cannot become a static part: "+id);
            Map<String,String> properties=new TreeMap<>();
            row.getAsJsonObject("properties").entrySet().forEach(e->properties.put(e.getKey(),e.getValue().getAsString()));
            state=CompositeData.state(new dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.BlockData(id.toString(),properties,new byte[0]));
            if(state.get(CompositeRootBlock.OPEN)||state.get(CompositeRootBlock.PROFILE)!=CompositeRootBlock.Profile.BASE)throw new IllegalArgumentException("Only reviewed closed base artwork may become static");
            var values=row.getAsJsonArray("shift");
            if(values.size()!=3)throw new IllegalArgumentException("Static source shift");
            shift=new CompositeSourceShift.Shift(values.get(0).getAsDouble(),values.get(1).getAsDouble(),values.get(2).getAsDouble());
            mountY=row.get("mountY").getAsDouble();
            if(!Double.isFinite(mountY)||Math.abs(mountY)>32)throw new IllegalArgumentException("Static mount offset");
            var visual=row.getAsJsonArray("reviewedVisualBounds");
            if(visual.size()!=2||visual.get(0).getAsJsonArray().size()!=3||visual.get(1).getAsJsonArray().size()!=3)throw new IllegalArgumentException("Reviewed static visual bounds");
            double[] minimum={-16,-32,-16},maximum={17,33,17};
            for(int axis=0;axis<3;axis++){double from=visual.get(0).getAsJsonArray().get(axis).getAsDouble(),to=visual.get(1).getAsJsonArray().get(axis).getAsDouble();if(!Double.isFinite(from)||!Double.isFinite(to)||from>to||from<minimum[axis]||to>maximum[axis])throw new IllegalArgumentException("Static model exceeds its section-culling neighbourhood");}
            var from=visual.get(0).getAsJsonArray();var to=visual.get(1).getAsJsonArray();
            visualBounds=new net.minecraft.util.math.Box(from.get(0).getAsDouble(),from.get(1).getAsDouble(),from.get(2).getAsDouble(),to.get(0).getAsDouble(),to.get(1).getAsDouble(),to.get(2).getAsDouble());
            footprint=CompositeSourceShift.footprint(block.spec,state,payload());
            outline=block.spec.simplePhysicalCollision?ImportedSelectionShapes.footprint(block,state,payload()):footprint.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,e->e.getValue().selection()));
        }
        public String key(){return key;}
        public CompositeRootBlock block(){return block;}
        public BlockState state(){return state;}
        /** Authored local shift; world shift is obtained by rotating the pose. */
        public CompositeSourceShift.Shift shift(){return shift;}
        public double mountY(){return mountY;}
        /** Authored geometry bounds already include shift, mount and rotation. */
        public net.minecraft.util.math.Box visualBounds(){return visualBounds;}
        public Map<Cell,Footprint> footprint(){return footprint;}
        public Map<Cell,List<Box>> outline(){return outline;}
        public List<CompositeSpec.Part> parts(){return block.spec.pose(state).parts();}
        /** Fresh operation input, never retained per placed cell. */
        public NbtCompound payload(){
            NbtCompound tag=ImportedTypeAliases.markPayload(null);
            if(shift.x()!=0||shift.y()!=0||shift.z()!=0){NbtList list=new NbtList();list.add(NbtDouble.of(shift.x()));list.add(NbtDouble.of(shift.y()));list.add(NbtDouble.of(shift.z()));tag.put("SourceShift",list);}
            if(mountY!=0)tag.putDouble("MountY",mountY);
            return tag;
        }
    }
    public record Fragment(Pose pose,Cell origin,boolean member){}
    private record Geometry(List<Box> boxes,VoxelShape shape){}
    /** Initialization-only dictionaries. Recipes retain the canonical values,
     * so the dictionaries themselves are discarded before chunks can load. */
    private static final class CatalogValues {
        final Map<List<Box>,Geometry> geometry=new HashMap<>();
        final Map<List<Fragment>,List<Fragment>> fragments=new HashMap<>();
        Geometry geometry(JsonArray rows){
            List<Box> boxes=boxes(rows);
            return geometry.computeIfAbsent(boxes,key->new Geometry(key,ImportedPhysicalShapes.buildExact(key)));
        }
        Geometry geometry(List<Box> boxes){return geometry.computeIfAbsent(List.copyOf(boxes),key->new Geometry(key,ImportedPhysicalShapes.buildExact(key)));}
        List<Fragment> fragments(List<Fragment> list){
            List<Fragment> immutable=List.copyOf(list);return fragments.computeIfAbsent(immutable,key->key);
        }
    }
    public static final class Recipe {
        private final int index,opacity,luminance,resident;
        private final boolean translucent;
        private final String key;
        private final Geometry collision,selection;
        private final List<Fragment> fragments;
        private final int[] removal;
        private Recipe(int index,JsonObject row,Map<String,Pose> poses,CatalogValues shared){
            this.index=index;key=row.get("key").getAsString();selection=shared.geometry(row.getAsJsonArray("selection"));
            opacity=row.get("opacity").getAsInt();luminance=row.get("luminance").getAsInt();resident=row.get("resident").getAsInt();
            if(opacity<0||opacity>15||luminance<0||luminance>15)throw new IllegalArgumentException("Static optics range");
            List<Fragment> list=new ArrayList<>();
            for(var value:row.getAsJsonArray("fragments")){
                var fragment=value.getAsJsonObject();Pose pose=Objects.requireNonNull(poses.get(fragment.get("pose").getAsString()),"Unknown static pose");var origin=fragment.getAsJsonArray("origin");
                if(origin.size()!=3)throw new IllegalArgumentException("Static relative origin");
                int x=integer(origin.get(0)),y=integer(origin.get(1)),z=integer(origin.get(2));
                if(Math.abs(x)>16||Math.abs(y)>32||Math.abs(z)>16)throw new IllegalArgumentException("Static part exceeds a chunk render neighbourhood");
                list.add(new Fragment(pose,new Cell(x,y,z),fragment.get("member").getAsBoolean()));
            }
            fragments=shared.fragments(list);
            if(fragments.stream().anyMatch(f->WoodFloorCollision.supports(f.pose.block.spec,f.pose.state))){
                List<Box> physical=new ArrayList<>();
                for(var fragment:fragments){var origin=fragment.origin();var shape=fragment.pose.footprint().get(new Cell(-origin.x(),-origin.y(),-origin.z()));if(shape!=null)physical.addAll(shape.collision());}
                collision=shared.geometry(physical);
            }else collision=shared.geometry(row.getAsJsonArray("collision"));
            translucent=fragments.stream().allMatch(fragment->fragment.pose().block().spec.translucent);
            if(fragments.isEmpty()||fragments.size()>32||resident< -1||resident>=fragments.size())throw new IllegalArgumentException("Static recipe budget/resident");
            if(resident>=0&&!fragments.get(resident).origin().equals(Cell.ORIGIN))throw new IllegalArgumentException("Static resident root must be this cell");
            if(fragments.stream().filter(f->f.origin.equals(Cell.ORIGIN)).count()>(resident>=0?1:0))throw new IllegalArgumentException("Static cell root must have a unique resident");
            if(new HashSet<>(fragments).size()!=fragments.size())throw new IllegalArgumentException("Repeated static component in one cell");
            var removed=row.getAsJsonArray("remove");if(removed.size()!=fragments.size())throw new IllegalArgumentException("Incomplete finite static removal closure");
            removal=new int[removed.size()];for(int i=0;i<removal.length;i++)removal[i]=integer(removed.get(i));
        }
        public int index(){return index;} public String key(){return key;}
        public List<Box> collision(){return collision.boxes;} public List<Box> selection(){return selection.boxes;}
        public int opacity(){return opacity;} public int luminance(){return luminance;} public int resident(){return resident;}
        /** Migrated components are layer-homogeneous. Mixed legacy recipes are
         * never emitted and retain the previous cutout fallback. */
        public boolean translucent(){return translucent;}
        public List<Fragment> fragments(){return fragments;}
        public VoxelShape collisionShape(){return collision.shape;} public VoxelShape outlineShape(){return selection.shape;}
        public int removal(int fragment){return removal[fragment];}
        public BlockState without(int fragment){int next=removal(fragment);return next<0?Blocks.AIR.getDefaultState():StaticArchitectureCatalog.state(next);}
        public BlockState renderCarrier(){
            if(resident>=0){Pose pose=fragments.get(resident).pose();return ImportedSourceLightingV127.renderCarrier(pose.block.spec,pose.state);}
            return opacity==15?Blocks.STONE.getDefaultState():StaticArchitectureCatalog.state(index);
        }
        public BlockState renderCarrier(BlockState actual){return renderCarrier();}
    }
    private static List<Pose> POSES;
    private static List<Recipe> RECIPES;
    private static String RESOURCE_SHA256;
    private StaticArchitectureCatalog(){}
    public static synchronized void initialize(){
        if(RECIPES!=null)return;
        try(var stream=StaticArchitectureCatalog.class.getResourceAsStream(RESOURCE)){
            if(stream==null)throw new IllegalStateException("Missing finite static architecture catalog");
            byte[] bytes=stream.readAllBytes();RESOURCE_SHA256=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            var doc=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
            if(doc.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Static architecture schema");
            Map<String,Pose> poses=new LinkedHashMap<>();for(var value:doc.getAsJsonArray("poses")){Pose pose=new Pose(value.getAsJsonObject());if(poses.put(pose.key,pose)!=null)throw new IllegalArgumentException("Duplicate static pose");}
            if(poses.isEmpty()||poses.size()>4096)throw new IllegalArgumentException("Static pose budget");
            CatalogValues shared=new CatalogValues();
            List<Recipe> recipes=new ArrayList<>();Set<String> keys=new HashSet<>();for(var value:doc.getAsJsonArray("recipes")){Recipe recipe=new Recipe(recipes.size(),value.getAsJsonObject(),poses,shared);if(!keys.add(recipe.key))throw new IllegalArgumentException("Duplicate static recipe");recipes.add(recipe);}
            if(recipes.isEmpty()||recipes.size()>32768)throw new IllegalArgumentException("Static recipe budget");
            for(Recipe recipe:recipes)for(int i=0;i<recipe.removal.length;i++){
                int next=recipe.removal[i];List<Fragment> remaining=new ArrayList<>(recipe.fragments);remaining.remove(i);
                if(remaining.isEmpty()){if(next!= -1)throw new IllegalArgumentException("Empty static recipe must become native air");}
                else if(next<0||next>=recipes.size()||!new HashSet<>(remaining).equals(new HashSet<>(recipes.get(next).fragments)))throw new IllegalArgumentException("Static removal closure changes a neighbouring component");
            }
            POSES=List.copyOf(poses.values());RECIPES=List.copyOf(recipes);
            StaticArchitectureBlock.initialize((RECIPES.size()+BANK_SIZE-1)/BANK_SIZE);
            for(Recipe recipe:RECIPES)if(state(recipe.index()).getLuminance()!=recipe.luminance())throw new IllegalStateException("Static native state luminance differs from its shared recipe: "+recipe.index());
        }catch(Exception failure){throw new IllegalStateException("Cannot load finite static architecture catalog",failure);}
    }
    public static List<Pose> poses(){return Objects.requireNonNull(POSES,"Static catalog not initialized");}
    public static String resourceSha256(){return Objects.requireNonNull(RESOURCE_SHA256,"Static catalog not initialized");}
    public static List<Recipe> recipes(){return Objects.requireNonNull(RECIPES,"Static catalog not initialized");}
    public static Pose pose(String key){return poses().stream().filter(p->p.key.equals(key)).findFirst().orElseThrow();}
    public static boolean contains(BlockState state){return state.getBlock() instanceof StaticArchitectureBlock||MaterialSlabs.hasOverlay(state);}
    public static Recipe recipe(BlockState state){if(MaterialSlabs.hasOverlay(state))return recipes().get(MaterialSlabs.recipeIndex(state));if(!(state.getBlock() instanceof StaticArchitectureBlock block))throw new IllegalArgumentException("Not static architecture");return block.recipe(state);}
    public static BlockState without(BlockState state,int fragment){int next=recipe(state).removal(fragment);if(MaterialSlabs.hasOverlay(state))return MaterialSlabs.withRecipe(state,next);boolean wet=state.contains(StaticArchitectureBlock.WATERLOGGED)&&state.get(StaticArchitectureBlock.WATERLOGGED);return next<0?(wet?net.minecraft.fluid.Fluids.WATER.getStill(false).getBlockState():Blocks.AIR.getDefaultState()):state(next).with(StaticArchitectureBlock.WATERLOGGED,wet);}
    public static BlockState state(int index){if(index<0||index>=recipes().size())throw new IllegalArgumentException("Unknown static recipe");return StaticArchitectureBlock.banks().get(index/BANK_SIZE).getDefaultState().with(StaticArchitectureBlock.PART,index%BANK_SIZE);}
    /** Fixture compatibility helper: presence is already encoded by the
     * finite recipe, never by an additional dynamic state or root lookup. */
    public static BlockState state(int index,boolean resident){if((recipes().get(index).resident>=0)!=resident)throw new IllegalArgumentException("Resident presence is encoded in the static recipe");return state(index);}
    public static boolean matchesRoot(BlockState state,Pose pose){if(!contains(state))return false;Recipe recipe=recipe(state);return recipe.resident>=0&&recipe.fragments.get(recipe.resident).pose==pose;}
    private static int integer(JsonElement value){double number=value.getAsDouble();int integer=value.getAsInt();if(number!=integer)throw new IllegalArgumentException("Relative cell must be integral");return integer;}
    private static List<Box> boxes(JsonArray rows){List<Box> values=new ArrayList<>();if(rows.size()>2048)throw new IllegalArgumentException("Static box budget");for(var raw:rows){var a=raw.getAsJsonArray();if(a.size()!=6)throw new IllegalArgumentException("Static cell box");values.add(new Box(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble(),a.get(3).getAsDouble(),a.get(4).getAsDouble(),a.get(5).getAsDouble()));}return List.copyOf(values);}
}

