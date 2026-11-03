package dev.dreamwalker.bloodbornedw.editor;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.visual.CityVisualModeState;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.block.BlockState;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtTagSizeTracker;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.chunk.*;

/** Server-owned bounded editing. Untouched city cells have no editor entries. */
public final class CityEditorServer implements ModInitializer {
    public static final Identifier REQUEST=id("editor_request"), STATE=id("editor_state"), UPDATE=id("editor_update");
    public static final Item BRUSH=new Item(new Item.Settings().maxCount(1));
    public static final int MAX_CELLS=4096, MAX_HISTORY_CELLS=32768;
    private static final Set<String> MODES=Set.of("single","area","plane","fill","noise","stamp");
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,Ingress> INGRESS=new ConcurrentHashMap<>();
    private static final Map<WorldChunk,Map<Long,Integer>> EXTERNAL_UPDATES=new LinkedHashMap<>();
    private static final ThreadLocal<Integer> PLACING=ThreadLocal.withInitial(()->0);
    private static final ThreadLocal<Map<WorldChunk,Map<Long,Integer>>> PLACEMENT_UPDATES=new ThreadLocal<>();
    private static List<Region> regions;
    private static int jobCursor;
    /** Netty-side admission, before decoding or adding work to the server executor. */
    private static final class Ingress {
        private double tokens=64; private long updated=System.nanoTime(); private int pending;
        synchronized boolean acquire(){long now=System.nanoTime();tokens=Math.min(64,tokens+(now-updated)*40.0/1_000_000_000L);updated=now;if(tokens<1||pending>=64)return false;tokens--;pending++;return true;}
        synchronized void release(){pending--;}
    }
    private record Region(BlockPos min,BlockPos max,int role) {
        boolean contains(BlockPos p){return p.getX()>=min.getX()&&p.getX()<=max.getX()&&p.getY()>=min.getY()&&p.getY()<=max.getY()&&p.getZ()>=min.getZ()&&p.getZ()<=max.getZ();}
    }
    private record Change(BlockPos pos,BlockState state,int nativeIndex,int before,int after) {}
    private record Placement(BlockPos root,BlockState state,int nativeIndex,ItemStack item,BlockHitResult hit,int style,float yaw) {}
    private record Edit(RegistryKey<net.minecraft.world.World> dimension,List<Change> changes,Placement placement,long[] positions) {
        Edit(RegistryKey<net.minecraft.world.World> dimension,List<Change> changes){this(dimension,changes,null);}
        Edit(RegistryKey<net.minecraft.world.World> dimension,List<Change> changes,Placement placement){this(dimension,List.copyOf(changes),placement,placement==null?changes.stream().mapToLong(c->c.pos.asLong()).sorted().toArray():new long[]{placement.root.asLong()});}
    }
    private static final class Session {
        int material=1,radius=0,lastRequest=-100,secondary=2,accent=3,primaryWeight=80,secondaryWeight=15; String mode="single",message="Готово";
        BlockPos first,second; ItemStack stamp=ItemStack.EMPTY; int stampStyle;
        RegistryKey<net.minecraft.world.World> selectionDimension;
        final Deque<Edit> undo=new ArrayDeque<>(),redo=new ArrayDeque<>(); Job job;
        String stroke="";RegistryKey<net.minecraft.world.World> strokeDimension;boolean endingStroke;
        long requestId;boolean accepted,retry;
        final Map<Long,Change> strokeChanges=new LinkedHashMap<>();
    }
    private static final class Job {
        Edit edit; final int direction; final List<Change> done=new ArrayList<>(); int index;String error;
        Job(Edit edit,int direction){this.edit=edit;this.direction=direction;}
    }
    private static Identifier id(String path){return new Identifier("bloodborne_dw",path);}
    @Override public void onInitialize(){
        Registry.register(Registries.ITEM,id("editor_brush"),BRUSH);
        ServerPlayNetworking.registerGlobalReceiver(REQUEST,(server,player,handler,buf,sender)->{
            Ingress ingress=INGRESS.computeIfAbsent(player.getUuid(),ignored->new Ingress());
            if(!ingress.acquire()){handler.disconnect(Text.literal("Слишком много запросов редактора"));return;}
            if(buf.readableBytes()>1024){ingress.release();return;}
            NbtCompound request;
            try{request=buf.readNbt(new NbtTagSizeTracker(4096));if(request==null||buf.isReadable()){ingress.release();return;}}
            catch(RuntimeException bad){ingress.release();return;}
            server.execute(()->{try{if(!player.isDisconnected())safeRequest(player,request);}finally{ingress.release();}});
        });
        ServerTickEvents.END_SERVER_TICK.register(CityEditorServer::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{SESSIONS.remove(handler.player.getUuid());INGRESS.remove(handler.player.getUuid());});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{SESSIONS.clear();INGRESS.clear();EXTERNAL_UPDATES.clear();PLACING.remove();PLACEMENT_UPDATES.remove();});
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            var root=CommandManager.literal("dwedit").requires(s->s.hasPermissionLevel(2));
            root.executes(c->{var p=c.getSource().getPlayerOrThrow();p.giveItemStack(new ItemStack(BRUSH));state(p,session(p));return 1;});
            for(String action:List.of("sample","paint","first","second","fill","noise","reset","undo","redo","stamp","cancel"))
                root.then(CommandManager.literal(action).executes(c->{NbtCompound n=new NbtCompound();n.putString("action",action);safeRequest(c.getSource().getPlayerOrThrow(),n);return 1;}));
            root.then(CommandManager.literal("audit").executes(c->{c.getSource().sendFeedback(()->Text.literal(dev.dreamwalker.bloodbornedw.editor.catalogue.EditorCatalogue.audit().toString()),false);return 1;}));
            dispatcher.register(root);
        });
    }
    private static Session session(ServerPlayerEntity p){return SESSIONS.computeIfAbsent(p.getUuid(),ignored->new Session());}
    public static boolean editable(ServerPlayerEntity p){return p.isCreative()&&dev.dreamwalker.bloodbornedw.architecture.BuildPermissions.canEdit(p);}
    public static boolean supported(BlockState s){return Registries.BLOCK.getId(s.getBlock()).getNamespace().equals("bloodborne_dw")&&!s.isAir();}
    public static void beginPlacement(){PLACING.set(PLACING.get()+1);}
    public static void endPlacement(){endPlacement(true);}
    public static void endPlacement(boolean committed){int depth=PLACING.get()-1;if(depth<=0){PLACING.remove();var updates=PLACEMENT_UPDATES.get();PLACEMENT_UPDATES.remove();if(committed&&updates!=null)applyUpdates(updates);}else PLACING.set(depth);}
    public static Object placementIdentity(ServerWorld world,BlockPos pos){var entity=world.getBlockEntity(pos);return entity instanceof CompositeBlockEntity composite&&composite.resident()!=null?composite.resident().instanceId():entity;}
    public static void placed(ServerWorld world,BlockPos pos){
        WorldChunk chunk=loaded(world,pos);if(chunk==null||!supported(world.getBlockState(pos)))return;
        discardExternal(chunk,pos);
        if(PLACING.get()>0){var pending=PLACEMENT_UPDATES.get();if(pending==null){pending=new LinkedHashMap<>();PLACEMENT_UPDATES.set(pending);}pending.computeIfAbsent(chunk,k->new LinkedHashMap<>()).put(pos.asLong(),0);}
        else applyUpdates(Map.of(chunk,Map.of(pos.asLong(),0)));
        invalidateAt(world,pos);
    }
    /** Called after a committed physical replacement, never on a model/profile switch. */
    public static void replaced(WorldChunk chunk,BlockPos pos,BlockState old,BlockState next){
        if(!(chunk.getWorld() instanceof ServerWorld world)||old==null||old.getBlock()==next.getBlock()||(!supported(old)&&!supported(next)))return;
        discardExternal(chunk,pos);
        var data=NativeVisuals.data(chunk);var attachment=(RetainedFrameData)(Object)data;
        int oldStyle=attachment.dw$editor().style(pos);
        var pending=PLACEMENT_UPDATES.get();if(pending!=null&&pending.containsKey(chunk)&&pending.get(chunk).containsKey(pos.asLong()))oldStyle=pending.get(chunk).get(pos.asLong());
        int newStyle=PLACING.get()>0&&supported(next)?0:-1;
        if(oldStyle!=newStyle){
            if(PLACING.get()>0){if(pending==null){pending=new LinkedHashMap<>();PLACEMENT_UPDATES.set(pending);}pending.computeIfAbsent(chunk,k->new LinkedHashMap<>()).put(pos.asLong(),newStyle);}
            else applyUpdates(Map.of(chunk,Map.of(pos.asLong(),newStyle)));
        }
        invalidateAt(world,pos);
    }
    // Bounded histories are the only place we track object identity. No world-sized generation map.
    private static void invalidateAt(ServerWorld world,BlockPos pos){
        for(Session s:SESSIONS.values()){
            if(s.job!=null&&s.job.edit.dimension.equals(world.getRegistryKey())&&touches(s.job.edit,pos)){s.message="Операция остановлена: блок заменён";finish(s);}
            s.undo.removeIf(e->e.dimension.equals(world.getRegistryKey())&&touches(e,pos));
            s.redo.removeIf(e->e.dimension.equals(world.getRegistryKey())&&touches(e,pos));
            if(world.getRegistryKey().equals(s.strokeDimension))s.strokeChanges.remove(pos.asLong());
        }
    }
    private static boolean touches(Edit edit,BlockPos pos){return Arrays.binarySearch(edit.positions,pos.asLong())>=0;}
    private static void discardExternal(WorldChunk chunk,BlockPos pos){Map<Long,Integer> pending=EXTERNAL_UPDATES.get(chunk);if(pending!=null){pending.remove(pos.asLong());if(pending.isEmpty())EXTERNAL_UPDATES.remove(chunk);}}
    public static int pendingStyle(ServerWorld world,BlockPos pos){
        if(EXTERNAL_UPDATES.isEmpty())return -2;WorldChunk chunk=loaded(world,pos);if(chunk==null)return -2;Map<Long,Integer> pending=EXTERNAL_UPDATES.get(chunk);return pending==null? -2:pending.getOrDefault(pos.asLong(),-2);
    }
    public static int effectiveStyle(ServerWorld world,BlockPos pos){WorldChunk chunk=loaded(world,pos);if(chunk==null)return 0;int value=style(chunk,pos);return value<0?regional(pos,world):value;}
    public static void queueAppearance(ServerWorld world,BlockPos pos,int value){
        if(!world.getServer().isOnThread())throw new IllegalStateException("Editor writes require the server thread");
        if(value< -1||value>4||pos.getY()<world.getBottomY()||pos.getY()>=world.getTopY())throw new IllegalArgumentException("Invalid editor appearance");
        WorldChunk chunk=loaded(world,pos);if(chunk==null)throw new IllegalArgumentException("Editor target chunk is not loaded");
        invalidateAt(world,pos);
        if(style(chunk,pos)!=value)EXTERNAL_UPDATES.computeIfAbsent(chunk,k->new LinkedHashMap<>()).put(pos.asLong(),value);
    }
    private static WorldChunk loaded(ServerWorld world,BlockPos p){var chunk=world.getChunkManager().getChunk(p.getX()>>4,p.getZ()>>4,ChunkStatus.FULL,false);return chunk instanceof WorldChunk ready?ready:null;}
    private static int style(WorldChunk chunk,BlockPos p){var pending=EXTERNAL_UPDATES.get(chunk);if(pending!=null&&pending.containsKey(p.asLong()))return pending.get(p.asLong());return ((RetainedFrameData)(Object)NativeVisuals.data(chunk)).dw$editor().style(p);}
    private static BlockHitResult target(ServerPlayerEntity p){
        Vec3d eye=p.getEyePos(),end=eye.add(p.getRotationVec(1).multiply(6));
        BlockHitResult hit=p.getServerWorld().raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,p));
        return hit.getType()==HitResult.Type.BLOCK?hit:null;
    }
    public static void request(ServerPlayerEntity p,NbtCompound request){
        Session s=session(p);s.requestId=request.getLong("requestId");s.accepted=false;s.retry=false;
        if(!editable(p)){s.message="Нет разрешения на редактирование";state(p,s);return;}int now=p.getServer().getTicks();
        String action=request.getString("action");
        if(action.equals("endStroke")){s.endingStroke=true;if(s.job==null)flushStroke(s,false);state(p,s);return;}
        if(action.equals("config")){
            int material=request.contains("material")?request.getInt("material"):s.material,radius=request.contains("radius")?request.getInt("radius"):s.radius;
            int secondary=request.contains("secondary")?request.getInt("secondary"):s.secondary,accent=request.contains("accent")?request.getInt("accent"):s.accent;
            int primaryWeight=request.contains("primaryWeight")?request.getInt("primaryWeight"):s.primaryWeight,secondaryWeight=request.contains("secondaryWeight")?request.getInt("secondaryWeight"):s.secondaryWeight;
            String mode=request.contains("mode")?request.getString("mode"):s.mode;
            if(material<0||material>4||radius<0||radius>4||secondary<0||secondary>4||accent<0||accent>4||primaryWeight<0||secondaryWeight<0||primaryWeight+secondaryWeight>100||!MODES.contains(mode))return;
            s.endingStroke=true;if(s.job==null)flushStroke(s,false);
            s.material=material;s.radius=radius;s.mode=mode;s.secondary=secondary;s.accent=accent;s.primaryWeight=primaryWeight;s.secondaryWeight=secondaryWeight;
            state(p,s);return;
        }
        if(now-s.lastRequest<2){s.retry=true;state(p,s);return;}s.lastRequest=now;
        if(action.equals("cancel")){s.endingStroke=true;if(s.job!=null){s.message="Остановлено; применённая часть доступна для отмены";finish(s);}else{s.first=s.second=null;s.message="Выделение снято";}flushStroke(s,false);state(p,s);return;}
        boolean append=s.job!=null&&action.equals("paint")&&s.job.direction==0&&!s.endingStroke&&!s.stroke.isEmpty()&&s.stroke.equals(request.getString("stroke"))&&(s.mode.equals("single")||s.mode.equals("plane"));
        if(s.job!=null&&!append){s.retry=true;s.message="Дождитесь завершения или отмените операцию";state(p,s);return;}
        if(action.equals("undo")||action.equals("redo")){flushStroke(s,false);history(p,s,action.equals("redo"));state(p,s);return;}
        BlockHitResult hit=target(p);if(hit==null){s.message="Под прицелом нет блока";state(p,s);return;}
        BlockPos pos=hit.getBlockPos();ServerWorld world=p.getServerWorld();WorldChunk chunk=loaded(world,pos);
        boolean stampPlacement=action.equals("paint")&&s.mode.equals("stamp");
        if(chunk==null||(!stampPlacement&&!supported(world.getBlockState(pos)))){s.message="Выберите блок DreamWalker";state(p,s);return;}
        switch(action){
            case "sample"->{int value=style(chunk,pos);s.material=value<0?regional(pos,world):value;s.message="Материал скопирован";}
            case "first"->{s.first=pos.toImmutable();s.second=null;s.selectionDimension=world.getRegistryKey();s.message="Первая точка выделения";}
            case "second"->{if(!world.getRegistryKey().equals(s.selectionDimension))throw new IllegalArgumentException("Выберите первую точку в этом измерении");s.second=pos.toImmutable();s.message="Вторая точка выделения";}
            case "stamp"->{s.stamp=world.getBlockState(pos).getBlock().getPickStack(world,pos,world.getBlockState(pos));s.stampStyle=style(chunk,pos);if(s.stampStyle<0)s.stampStyle=regional(pos,world);s.mode="stamp";s.message=s.stamp.isEmpty()?"У этой детали нет безопасного предмета":"Образец формы взят";}
            case "paint","reset","fill","noise"->{
                String stroke=request.getString("stroke");
                if(!stroke.isEmpty()&&!stroke.matches("[0-9a-f-]{36}"))return;
                if(!stroke.equals(s.stroke)||(!stroke.isEmpty()&&!world.getRegistryKey().equals(s.strokeDimension))){flushStroke(s,false);s.stroke=stroke;s.strokeDimension=world.getRegistryKey();s.endingStroke=false;}
                if(action.equals("paint")&&s.mode.equals("stamp")){Placement placed=placeStamp(p,s,hit);if(placed!=null){s.undo.addLast(new Edit(world.getRegistryKey(),List.of(),placed));s.redo.clear();trim(s.undo);}break;}
                if(!CityVisualModeState.isAlt(p.getServer())){s.message="Покраска доступна в ALT; Original сохраняет исходный вид";break;}
                String mode=action.equals("fill")?"fill":action.equals("noise")?"noise":s.mode;
                List<BlockPos> cells=collect(world,pos,hit.getSide(),s,mode);
                List<Change> changes=new ArrayList<>();
                for(BlockPos cell:cells){var c=loaded(world,cell);if(c==null||!supported(world.getBlockState(cell)))continue;
                    if(!dev.dreamwalker.bloodbornedw.architecture.BuildPermissions.canEdit(world,p,cell))throw new IllegalArgumentException("Участок защищён от редактирования");
                    int before=style(c,cell),after=action.equals("reset")?0:s.material;
                    if(mode.equals("noise")&&!action.equals("reset")){long random=MathHelper.hashCode(cell.getX(),cell.getY(),cell.getZ());int v=Math.floorMod(random,100);after=v<s.primaryWeight?s.material:v<s.primaryWeight+s.secondaryWeight?s.secondary:s.accent;}
                    if(before!=after)changes.add(new Change(cell.toImmutable(),world.getBlockState(cell),NativeVisuals.index(world,cell),before,after));}
                if(append){
                    List<Change> combined=new ArrayList<>(s.job.edit.changes);for(Change change:changes)if(!touches(s.job.edit,change.pos))combined.add(change);
                    if(combined.size()>MAX_CELLS){s.retry=true;s.message="Очередь мазка заполнена; дождитесь обработки";break;}
                    s.job.edit=new Edit(world.getRegistryKey(),combined);s.accepted=true;s.message="В очереди: "+(combined.size()-s.job.index)+" клеток";
                }else if(changes.isEmpty()){s.accepted=true;s.message="Нет подходящих изменений";}
                else{s.job=new Job(new Edit(world.getRegistryKey(),List.copyOf(changes)),0);s.redo.clear();s.accepted=true;s.message="Обработка: "+changes.size()+" клеток";}
            }
            default->s.message="Неизвестное действие";
        }
        state(p,s);
    }
    private static void safeRequest(ServerPlayerEntity p,NbtCompound request){
        try{request(p,request);}catch(IllegalArgumentException bad){Session s=session(p);s.message=bad.getMessage();state(p,s);}
    }
    private static List<BlockPos> collect(ServerWorld world,BlockPos origin,Direction side,Session s,String mode){
        if(mode.equals("single"))return List.of(origin);
        if(mode.equals("plane")){List<BlockPos> out=new ArrayList<>();for(int a=-s.radius;a<=s.radius;a++)for(int b=-s.radius;b<=s.radius;b++)out.add(switch(side.getAxis()){case X->origin.add(0,a,b);case Y->origin.add(a,0,b);case Z->origin.add(a,b,0);});return out;}
        if(s.first==null||s.second==null||!world.getRegistryKey().equals(s.selectionDimension))throw new IllegalArgumentException("Сначала задайте обе точки выделения в этом измерении");
        BlockPos min=new BlockPos(Math.min(s.first.getX(),s.second.getX()),Math.min(s.first.getY(),s.second.getY()),Math.min(s.first.getZ(),s.second.getZ()));
        BlockPos max=new BlockPos(Math.max(s.first.getX(),s.second.getX()),Math.max(s.first.getY(),s.second.getY()),Math.max(s.first.getZ(),s.second.getZ()));
        long count=((long)max.getX()-min.getX()+1)*((long)max.getY()-min.getY()+1)*((long)max.getZ()-min.getZ()+1);
        if(count>MAX_CELLS||min.getSquaredDistance(origin)>128*128||max.getSquaredDistance(origin)>128*128)throw new IllegalArgumentException("Выделение: максимум 4096 клеток в пределах 128 блоков");
        List<BlockPos> all=new ArrayList<>();for(BlockPos p:BlockPos.iterate(min,max)){if(loaded(world,p)==null)throw new IllegalArgumentException("Выделение содержит незагруженный чанк");all.add(p.toImmutable());}
        if(!mode.equals("fill"))return all;
        if(!inside(origin,min,max))throw new IllegalArgumentException("Цель заливки вне выделения");
        int initial=effectiveStyle(world,origin),startNative=NativeVisuals.index(world,origin);BlockState start=world.getBlockState(origin);
        Set<BlockPos> seen=new HashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();List<BlockPos> filled=new ArrayList<>();queue.add(origin);
        while(!queue.isEmpty()){BlockPos p=queue.removeFirst();if(!seen.add(p)||!inside(p,min,max))continue;
            if(world.getBlockState(p)!=start||NativeVisuals.index(world,p)!=startNative||effectiveStyle(world,p)!=initial)continue;filled.add(p);
            for(Direction d:Direction.values())if(d.getAxis()!=side.getAxis())queue.add(p.offset(d));}
        return filled;
    }
    private static boolean inside(BlockPos p,BlockPos a,BlockPos b){return p.getX()>=a.getX()&&p.getX()<=b.getX()&&p.getY()>=a.getY()&&p.getY()<=b.getY()&&p.getZ()>=a.getZ()&&p.getZ()<=b.getZ();}
    private static Placement placeStamp(ServerPlayerEntity p,Session s,BlockHitResult hit){
        if(s.stamp.isEmpty()||!(s.stamp.getItem() instanceof BlockItem)){s.message="Сначала возьмите безопасный образец блока";return null;}
        var context=new ItemPlacementContext(p,Hand.MAIN_HAND,s.stamp.copy(),hit);BlockPos root=context.getBlockPos().toImmutable();ServerWorld world=p.getServerWorld();
        if(loaded(world,root)==null||!world.getBlockState(root).isAir()||!dev.dreamwalker.bloodbornedw.architecture.BuildPermissions.canEdit(world,p,root)){s.message="Место занято, защищено или не загружено";return null;}
        ItemStack held=p.getMainHandStack();ItemStack sample=s.stamp.copy();p.setStackInHand(Hand.MAIN_HAND,sample);
        try{ActionResult result;beginPlacement();try{result=sample.useOnBlock(new ItemUsageContext(p,Hand.MAIN_HAND,hit));}finally{endPlacement();}
            if(!result.isAccepted()||!supported(world.getBlockState(root))){s.message="Установка отклонена";return null;}
            WorldChunk chunk=loaded(world,root);var data=(RetainedFrameData)(Object)NativeVisuals.data(chunk);
            NativeVisuals.data(chunk,data.dw$withEditor(data.dw$editor().with(root.asLong(),s.stampStyle)));chunk.setNeedsSaving(true);publish(world,chunk);
            s.message="Образец установлен";return new Placement(root,world.getBlockState(root),NativeVisuals.index(world,root),s.stamp.copy(),hit,s.stampStyle,p.getYaw());}
        finally{p.setStackInHand(Hand.MAIN_HAND,held);}
    }
    private static void history(ServerPlayerEntity p,Session s,boolean redo){
        Deque<Edit> from=redo?s.redo:s.undo;Edit e=from.peekLast();
        if(e==null){s.message="История пуста";return;}
        if(!e.dimension.equals(p.getServerWorld().getRegistryKey())){s.message="Перейдите в измерение изменения";return;}
        if(e.placement!=null){
            Placement placement=e.placement;ServerWorld world=p.getServerWorld();
            if(loaded(world,placement.root)==null){s.message="Объект не загружен";return;}
            if(!dev.dreamwalker.bloodbornedw.architecture.BuildPermissions.canEdit(world,p,placement.root)){s.message="Участок защищён от редактирования";return;}
            if(!redo){
                if(world.getBlockState(placement.root)!=placement.state||NativeVisuals.index(world,placement.root)!=placement.nativeIndex){s.message="Объект уже изменён";return;}
                from.removeLast();
                if(p.interactionManager.tryBreakBlock(placement.root)){s.redo.addLast(e);s.message="Установка отменена";}else{from.addLast(e);s.message="Разрушение отклонено";}
            }else{
                if(!world.getBlockState(placement.root).isAir()){s.message="Место занято";return;}from.removeLast();
                ItemStack previous=s.stamp;int previousStyle=s.stampStyle;float yaw=p.getYaw();
                try{s.stamp=placement.item;s.stampStyle=placement.style;p.setYaw(placement.yaw);Placement next=placeStamp(p,s,placement.hit);if(next!=null)s.undo.addLast(new Edit(e.dimension,List.of(),next));else from.addLast(e);}
                finally{s.stamp=previous;s.stampStyle=previousStyle;p.setYaw(yaw);}
            }
            return;
        }
        for(Change c:e.changes){WorldChunk chunk=loaded(p.getServerWorld(),c.pos);int expected=redo?c.before:c.after;
            if(chunk==null||p.getServerWorld().getBlockState(c.pos)!=c.state||NativeVisuals.index(p.getServerWorld(),c.pos)!=c.nativeIndex||style(chunk,c.pos)!=expected){s.message="Отмена отклонена: участок изменён или не загружен";return;}}
        from.removeLast();s.job=new Job(e,redo?1:-1);s.message=redo?"Повтор":"Отмена";
    }
    private static void tick(MinecraftServer server){
        if(!EXTERNAL_UPDATES.isEmpty()){applyUpdates(EXTERNAL_UPDATES);EXTERNAL_UPDATES.clear();}
        List<Map.Entry<UUID,Session>> active=null;for(var entry:SESSIONS.entrySet())if(entry.getValue().job!=null){if(active==null)active=new ArrayList<>();active.add(entry);}
        if(active==null)return;
        long deadline=System.nanoTime()+1_500_000;int budget=128,start=Math.floorMod(jobCursor++,active.size());
        for(int turn=0;turn<active.size()&&budget>0&&System.nanoTime()<deadline;turn++){var entry=active.get((start+turn)%active.size());Session s=entry.getValue();ServerPlayerEntity p=server.getPlayerManager().getPlayer(entry.getKey());if(p==null)continue;
            Job j=s.job;ServerWorld world=server.getWorld(j.edit.dimension);if(world==null||!editable(p)||p.getServerWorld()!=world){s.message="Операция остановлена";finish(s);state(p,s);continue;}
            Map<WorldChunk,Map<Long,Integer>> updates=new LinkedHashMap<>();
            while(j.index<j.edit.changes.size()&&budget-->0&&System.nanoTime()<deadline){Change c=j.edit.changes.get(j.index++);WorldChunk chunk=loaded(world,c.pos);int expected=j.direction==-1?c.after:c.before;
                if(chunk==null||world.getBlockState(c.pos)!=c.state||NativeVisuals.index(world,c.pos)!=c.nativeIndex||style(chunk,c.pos)!=expected||!dev.dreamwalker.bloodbornedw.architecture.BuildPermissions.canEdit(world,p,c.pos)){j.error="Остановлено: цель изменена, защищена или выгружена";j.index=j.edit.changes.size();break;}
                updates.computeIfAbsent(chunk,k->new LinkedHashMap<>()).put(c.pos.asLong(),j.direction==-1?c.before:c.after);j.done.add(c);}
            applyUpdates(updates);
            if(j.index>=j.edit.changes.size()){int n=j.done.size();finish(s);s.message=j.error==null?"Готово: "+n+" клеток":j.error+"; изменено: "+n;state(p,s);}
        }
    }
    private static void finish(Session s){
        Job j=s.job;if(j==null)return;s.job=null;
        if(j.direction!=0&&j.done.size()<j.edit.changes.size()){
            Deque<Edit> source=j.direction==-1?s.undo:s.redo;
            source.addLast(new Edit(j.edit.dimension,List.copyOf(j.edit.changes.subList(j.done.size(),j.edit.changes.size()))));trim(source);
        }
        if(j.done.isEmpty()){if(s.endingStroke)flushStroke(s,false);return;}
        if(j.direction==0&&!s.stroke.isEmpty()){
            for(Change change:j.done){Change first=s.strokeChanges.get(change.pos.asLong());s.strokeChanges.put(change.pos.asLong(),first==null?change:new Change(change.pos,change.state,change.nativeIndex,first.before,change.after));if(s.strokeChanges.size()>=MAX_CELLS)flushStroke(s,true);}
            if(s.endingStroke)flushStroke(s,false);return;
        }
        Edit actual=new Edit(j.edit.dimension,List.copyOf(j.done));Deque<Edit> to=j.direction==-1?s.redo:s.undo;to.addLast(actual);
        trim(to);
    }
    private static void flushStroke(Session s,boolean keepOpen){
        if(!s.strokeChanges.isEmpty()){s.undo.addLast(new Edit(s.strokeDimension,List.copyOf(s.strokeChanges.values())));s.strokeChanges.clear();trim(s.undo);}
        if(!keepOpen){s.stroke="";s.strokeDimension=null;s.endingStroke=false;}
    }
    private static void trim(Deque<Edit> history){int cells=history.stream().mapToInt(e->e.changes.size()).sum();while(history.size()>50||cells>MAX_HISTORY_CELLS)cells-=history.removeFirst().changes.size();}
    private static void applyUpdates(Map<WorldChunk,Map<Long,Integer>> updates){
        for(var update:updates.entrySet()){WorldChunk chunk=update.getKey();var data=(RetainedFrameData)(Object)NativeVisuals.data(chunk);EditorCells next=data.dw$editor().withAll(update.getValue());if(next==data.dw$editor())continue;NativeVisuals.data(chunk,data.dw$withEditor(next));chunk.setNeedsSaving(true);if(chunk.getWorld() instanceof ServerWorld world)publish(world,chunk);}
    }
    private static void publish(ServerWorld world,WorldChunk chunk){
        var cells=((RetainedFrameData)(Object)NativeVisuals.data(chunk)).dw$editor();
        for(ServerPlayerEntity p:PlayerLookup.tracking(world,chunk.getPos()))if(ServerPlayNetworking.canSend(p,UPDATE)){
            var buf=PacketByteBufs.create();buf.writeIdentifier(world.getRegistryKey().getValue());buf.writeInt(chunk.getPos().x);buf.writeInt(chunk.getPos().z);NbtCompound n=new NbtCompound();n.put("Editor",cells.packed());buf.writeNbt(n);ServerPlayNetworking.send(p,UPDATE,buf);}
    }
    private static void state(ServerPlayerEntity p,Session s){
        if(!ServerPlayNetworking.canSend(p,STATE))return;NbtCompound n=new NbtCompound();n.putLong("requestId",s.requestId);n.putBoolean("accepted",s.accepted);n.putBoolean("retry",s.retry);n.putBoolean("busy",s.job!=null);n.putInt("material",s.material);n.putInt("radius",s.radius);n.putInt("secondary",s.secondary);n.putInt("accent",s.accent);n.putInt("primaryWeight",s.primaryWeight);n.putInt("secondaryWeight",s.secondaryWeight);n.putString("mode",s.mode);n.putString("message",s.message);n.putInt("undo",s.undo.size());n.putInt("redo",s.redo.size());n.putBoolean("selected",s.first!=null&&s.second!=null);var buf=PacketByteBufs.create();buf.writeNbt(n);ServerPlayNetworking.send(p,STATE,buf);
    }
    private static int regional(BlockPos pos,ServerWorld world){
        if(!world.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD))return 0;
        if(regions==null){List<Region> list=new ArrayList<>();try(var input=CityEditorServer.class.getResourceAsStream("/assets/bloodborne_dw/visuals/facades.json")){if(input==null)throw new IllegalStateException("Missing facade palette");JsonObject json=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();for(JsonElement element:json.getAsJsonArray("regions")){var r=element.getAsJsonObject();var a=r.getAsJsonArray("min");var b=r.getAsJsonArray("max");int role=switch(r.get("role").getAsString()){case "rose"->1;case "sage"->2;case "sand"->3;case "red_house"->4;default->0;};list.add(new Region(new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()),new BlockPos(b.get(0).getAsInt(),b.get(1).getAsInt(),b.get(2).getAsInt()),role));}regions=List.copyOf(list);}catch(java.io.IOException e){throw new IllegalStateException(e);}}
        for(Region r:regions)if(r.contains(pos))return r.role;return 0;
    }
}
