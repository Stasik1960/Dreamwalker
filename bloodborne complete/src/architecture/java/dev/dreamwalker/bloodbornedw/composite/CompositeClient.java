package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import java.util.*;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPickBlockGatherCallback;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.RotationAxis;

/** Static baked-source-part rendering: no ticking entity and no merged out-of-range JSON. */
public final class CompositeClient {
    private static boolean initialized;
    public static void initialize() {
        if(initialized)return; initialized=true;
        dev.dreamwalker.bloodbornedw.diagnostics.DwClientDiagnostics.initialize();
        dev.dreamwalker.bloodbornedw.architecture.mount.NativeMountClient.initialize();
        ModelLoadingPlugin.register(context->{Set<Identifier> models=new LinkedHashSet<>();for(var block:CompositeArchitecture.blocks())for(var variant:block.spec.variants)for(var pose:List.of(variant.closed(),variant.open()))for(var part:pose.parts()){models.add(part.model());models.add(part.altModel());}context.addModels(models);});
        BlockEntityRendererRegistry.register(CompositeArchitecture.CELL_ENTITY, context->new BlockEntityRenderer<CompositeBlockEntity>(){
            @Override public void render(CompositeBlockEntity entity,float tickDelta,MatrixStack matrices,VertexConsumerProvider consumers,int light,int overlay){
                if(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.isNative(entity.getCachedState())){dev.dreamwalker.bloodbornedw.architecture.mount.NativeMountClient.render(entity,matrices,consumers,light,overlay);return;}
                if(entity.resident()==null||!(entity.getCachedState().getBlock() instanceof CompositeRootBlock block))return;
                var shift=CompositeSourceShift.world(entity.getCachedState(),entity.payload());matrices.push();matrices.translate(shift.x(),entity.mountY()+shift.y(),shift.z());renderParts(block,entity.getCachedState(),matrices,consumers,light,overlay,entity.resident().instanceId().toString(),entity.getPos());matrices.pop();
            }
            @Override public boolean rendersOutsideBoundingBox(CompositeBlockEntity entity){return true;}
            @Override public int getRenderDistance(){return 1024;}
        });
        for(var block:CompositeArchitecture.blocks())BuiltinItemRendererRegistry.INSTANCE.register(block.asItem(),(stack,mode,matrices,consumers,light,overlay)->{
            CompositeRootBlock rendered=block instanceof ThinWindowRootBlock?GlazingTypes.itemTarget(block,stack):block;
            BlockState state=rendered instanceof ThinWindowRootBlock?GlazingTypes.itemState(block,stack):rendered.getDefaultState();NbtCompound art=stack.getSubNbt("BlockStateTag");
            if(!(rendered instanceof ThinWindowRootBlock)&&art!=null){try{int v=Integer.parseInt(art.getString("variant"));if(v>=0&&v<rendered.spec.variants.size())state=state.with(CompositeRootBlock.VARIANT,v);}catch(NumberFormatException ignored){}state=state.with(CompositeRootBlock.PROFILE,"alt".equals(art.getString("profile"))?CompositeRootBlock.Profile.ALT:CompositeRootBlock.Profile.BASE).with(CompositeRootBlock.OPEN,"true".equals(art.getString("open")));}
            double[] bounds=visualBounds(rendered,state);double span=Math.max(bounds[3]-bounds[0],Math.max(bounds[4]-bounds[1],bounds[5]-bounds[2]));float scale=(float)(.85/Math.max(1,span));
            matrices.push();matrices.translate(.5,.5,.5);matrices.scale(scale,scale,scale);matrices.translate(-(bounds[0]+bounds[3])/2,-(bounds[1]+bounds[4])/2,-(bounds[2]+bounds[5])/2);renderParts(rendered,state,matrices,consumers,light,overlay,null,null);matrices.pop();
        });
        ClientPickBlockGatherCallback.EVENT.register((player,hit)->{if(hit instanceof BlockHitResult blockHit){long start=dev.dreamwalker.bloodbornedw.diagnostics.DwClientDiagnostics.shouldSample("client-process",blockHit.getBlockPos(),"pick-target")?System.nanoTime():0;var owner=CompositeRuntime.target(player.getWorld(),blockHit.getBlockPos(),player);if(start!=0)dev.dreamwalker.bloodbornedw.diagnostics.DwClientDiagnostics.measured("client-process",blockHit.getBlockPos(),"pick-target.wallElapsed",System.nanoTime()-start);if(owner!=null)return CompositeRuntime.pick(player.getWorld(),owner);}return net.minecraft.item.ItemStack.EMPTY;});
        ClientPlayNetworking.registerGlobalReceiver(CompositeNetworking.OWNERS,(client,handler,buffer,response)->{
            dev.dreamwalker.bloodbornedw.diagnostics.DwClientDiagnostics.network("client","receive",CompositeNetworking.OWNERS.toString(),buffer.readableBytes());
            SyncContext context=new SyncContext(CompositeNetworking.OWNERS);
            try{
                OwnersPacket packet=decodeOwners(buffer,context);
                client.execute(()->{if(client.world!=null&&client.world.getRegistryKey().getValue().equals(packet.dimension()))for(var value:packet.values().entrySet()){
                    CompositeLedger.get(client.world).put(value.getKey(),value.getValue());
                    for(var entry:value.getValue())if(entry.owner().root().equals(value.getKey())&&client.world.getBlockEntity(CompositeData.pos(value.getKey())) instanceof CompositeBlockEntity own&&(!(own instanceof dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderBlockEntity)||entry.owner().equals(own.resident())))own.set(entry.owner(),value.getValue(),CompositeData.nbt(entry.rootData().blockEntityNbt()));
                    var pos=CompositeData.pos(value.getKey());var state=client.world.getBlockState(pos);client.world.updateListeners(pos,state,state,net.minecraft.block.Block.NOTIFY_LISTENERS);
                    // Offset is BE data, not a Cartesian blockstate property;
                    // invalidate the old chunk mesh even when state == state.
                    client.worldRenderer.scheduleBlockRenders(pos.getX()-1,pos.getY()-1,pos.getZ()-1,pos.getX()+1,pos.getY()+1,pos.getZ()+1);
                }});
            }catch(RuntimeException failure){context.error(failure);}
        });
        ClientPlayNetworking.registerGlobalReceiver(CompositeNetworking.OWNERS_SELECTION,(client,handler,buffer,response)->{
            dev.dreamwalker.bloodbornedw.diagnostics.DwClientDiagnostics.network("client","receive",CompositeNetworking.OWNERS_SELECTION.toString(),buffer.readableBytes());
            SyncContext context=new SyncContext(CompositeNetworking.OWNERS_SELECTION);
            try{
                SelectionPacket packet=decodeSelection(buffer,context);
                client.execute(()->{if(client.world!=null&&client.player!=null&&client.world.getRegistryKey().getValue().equals(packet.dimension()))CompositeRuntime.acceptServerSelection(client.world,client.player,packet.owner());});
            }catch(RuntimeException failure){context.error(failure);}
        });
    }
    private record OwnersPacket(Identifier dimension,Map<Cell,List<CompositeData.Contribution>> values){}
    private record SelectionPacket(Identifier dimension,dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner owner){}
    /** Decode the complete packet before scheduling any mutation. Limits match the server's
     * 64-cell batch and vanilla's 1 MiB custom S2C payload ceiling; NBT retains its reader limit. */
    private static OwnersPacket decodeOwners(PacketByteBuf buffer,SyncContext context){
        context.stage="packet-length";checkPacketLength(buffer);
        context.stage="dimension-id";Identifier dimension=buffer.readIdentifier();
        context.stage="cell-count";int count=buffer.readVarInt();if(count<0||count>64)throw new IllegalArgumentException("Invalid owner cell count");
        Map<Cell,List<CompositeData.Contribution>> values=new LinkedHashMap<>();
        for(int i=0;i<count;i++){
            context.stage="cell-position";context.root=buffer.readBlockPos();Cell cell=CompositeData.cell(context.root);
            context.instanceId="";context.registry=null;
            if(values.containsKey(cell))throw new IllegalArgumentException("Duplicate owner cell");
            context.stage="owners-nbt";NbtCompound tag=buffer.readNbt();
            // A null tag formerly cleared this cell, and remains a valid empty update.
            List<CompositeData.Contribution> entries=tag==null?List.of():decodeContributions(tag,context);
            values.put(cell,entries);
        }
        context.stage="trailing-bytes";if(buffer.isReadable())throw new IllegalArgumentException("Trailing owner packet bytes");
        return new OwnersPacket(dimension,Collections.unmodifiableMap(values));
    }
    private static SelectionPacket decodeSelection(PacketByteBuf buffer,SyncContext context){
        context.stage="packet-length";checkPacketLength(buffer);
        context.stage="dimension-id";Identifier dimension=buffer.readIdentifier();
        context.stage="selection-uuid";UUID uuid=buffer.readUuid();context.instanceId=uuid.toString();
        context.stage="selection-registry-id";Identifier id=buffer.readIdentifier();context.registry=id;
        context.stage="selection-root";context.root=buffer.readBlockPos();
        context.stage="selection-registered-kind";requireCompositeKind(id);
        context.stage="trailing-bytes";if(buffer.isReadable())throw new IllegalArgumentException("Trailing selection packet bytes");
        return new SelectionPacket(dimension,new dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner(uuid,id.toString(),CompositeData.cell(context.root)));
    }
    private static void checkPacketLength(PacketByteBuf buffer){if(buffer.readableBytes()<=0||buffer.readableBytes()>1_048_576)throw new IllegalArgumentException("Invalid custom S2C payload length");}
    private static List<CompositeData.Contribution> decodeContributions(NbtCompound tag,SyncContext context){
        requireTag(tag,"owners",NbtElement.LIST_TYPE);NbtList owners=tag.getList("owners",NbtElement.COMPOUND_TYPE);
        if(!((NbtList)tag.get("owners")).isEmpty()&&((NbtList)tag.get("owners")).getHeldType()!=NbtElement.COMPOUND_TYPE)throw new IllegalArgumentException("Owner list must contain compounds");
        List<CompositeData.Contribution> result=new ArrayList<>();Set<UUID> seen=new HashSet<>();var cellPosition=context.root;
        for(int i=0;i<owners.size();i++){
            context.instanceId="";context.registry=null;context.root=cellPosition;
            context.stage="owner-identity";NbtCompound row=owners.getCompound(i);requireTag(row,"owner",NbtElement.COMPOUND_TYPE);NbtCompound owner=row.getCompound("owner");
            requireTag(owner,"uuid",NbtElement.INT_ARRAY_TYPE);if(!owner.containsUuid("uuid"))throw new IllegalArgumentException("Invalid owner UUID length");context.instanceId=owner.getUuid("uuid").toString();
            requireTag(owner,"root",NbtElement.INT_ARRAY_TYPE);int[] root=owner.getIntArray("root");if(root.length!=3)throw new IllegalArgumentException("Invalid owner root length");context.root=new net.minecraft.util.math.BlockPos(root[0],root[1],root[2]);
            requireTag(owner,"id",NbtElement.STRING_TYPE);Identifier id=new Identifier(owner.getString("id"));context.registry=id;requireCompositeKind(id);
            if(!seen.add(owner.getUuid("uuid")))throw new IllegalArgumentException("Duplicate owner UUID in cell");
            context.stage="owner-root-data";requireTag(row,"rootData",NbtElement.COMPOUND_TYPE);NbtCompound data=row.getCompound("rootData");
            requireTag(data,"id",NbtElement.STRING_TYPE);if(!id.toString().equals(data.getString("id")))throw new IllegalArgumentException("Owner and root registry IDs differ");
            requireTag(data,"properties",NbtElement.COMPOUND_TYPE);NbtCompound properties=data.getCompound("properties");
            for(String key:properties.getKeys()){
                requireTag(properties,key,NbtElement.STRING_TYPE);var property=Registries.BLOCK.get(id).getStateManager().getProperty(key);
                if(property==null||property.parse(properties.getString(key)).isEmpty())throw new IllegalArgumentException("Invalid root state property");
            }
            requireTag(data,"payload",NbtElement.BYTE_ARRAY_TYPE);
            context.stage="owner-collision";requireBoxes(row,"collision");context.stage="owner-selection";requireBoxes(row,"selection");
            context.stage="owner-payload-decode";result.add(CompositeData.contribution(row));
        }
        return List.copyOf(result);
    }
    private static void requireCompositeKind(Identifier id){if(!Registries.BLOCK.containsId(id)||!dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.isArchitecture(Registries.BLOCK.get(id).getDefaultState()))throw new IllegalArgumentException("Unknown architectural root registry ID");}
    private static void requireTag(NbtCompound tag,String key,int type){if(!tag.contains(key,type))throw new IllegalArgumentException("Missing or incorrectly typed sync field");}
    private static void requireBoxes(NbtCompound row,String key){
        requireTag(row,key,NbtElement.LIST_TYPE);NbtList list=(NbtList)row.get(key);
        if(!list.isEmpty()&&list.getHeldType()!=NbtElement.LIST_TYPE)throw new IllegalArgumentException("Cuboid list must contain lists");
        for(int i=0;i<list.size();i++){NbtList box=(NbtList)list.get(i);if(box.size()!=6||box.getHeldType()!=NbtElement.DOUBLE_TYPE)throw new IllegalArgumentException("Cuboid must contain six doubles");}
    }
    private static final class SyncContext{
        final Identifier channel;String stage="header",instanceId="";Identifier registry;net.minecraft.util.math.BlockPos root;
        SyncContext(Identifier channel){this.channel=channel;}
        void error(RuntimeException failure){
            // Some decoder exceptions include a tag's contents. Retain its class/location,
            // never the packet or original exception message in the bounded error journal.
            RuntimeException safe=new IllegalArgumentException("Rejected composite sync ("+failure.getClass().getSimpleName()+")");safe.setStackTrace(failure.getStackTrace());
            var entry=registry==null?null:dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.entry(registry);
            String id=registry==null?"UNKNOWN":registry.toString();if(id.length()>160)id=id.substring(0,160);
            dev.dreamwalker.bloodbornedw.diagnostics.DwClientDiagnostics.error(entry==null?"UNASSIGNED":entry.temporaryId(),instanceId,root,"COMPOSITE_SYNC_DECODE","Rejected "+channel.getPath()+" at "+stage+"; registry="+id,safe);
        }
    }
    private static void renderParts(CompositeRootBlock block,BlockState state,MatrixStack matrices,VertexConsumerProvider consumers,int light,int overlay,String instanceId,net.minecraft.util.math.BlockPos root){
        MinecraftClient client=MinecraftClient.getInstance();VertexConsumer consumer=consumers.getBuffer(block.spec.translucent?RenderLayer.getTranslucent():RenderLayer.getCutout());
        boolean diagnostics=dev.dreamwalker.bloodbornedw.diagnostics.DwClientDiagnostics.enabled();
        var entry=diagnostics?dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.entry(state):null;String type=entry==null?"UNASSIGNED":entry.temporaryId();
        long started=diagnostics&&dev.dreamwalker.bloodbornedw.diagnostics.DwClientDiagnostics.shouldSample(type,instanceId,root,"composite-render-parts")?System.nanoTime():0;
        matrices.push();rotate(matrices,.5,.5,.5,-state.get(CompositeRootBlock.ROTATION)*45,false);
        if(ThinWindowRootBlock.mount(state)!=ThinWindowRootBlock.Mount.VERTICAL){
            rotate(matrices,.5,.5,.5,ThinWindowRootBlock.mount(state)==ThinWindowRootBlock.Mount.FLOOR?90:-90,true);
            rotate(matrices,.5,.5,.5,block.spec.variant(state).mountAlignmentYaw(),false);
        }
        for(var part:block.spec.pose(state).parts()){
            matrices.push();matrices.translate(part.offset().x()/16,part.offset().y()/16,part.offset().z()/16);
            rotate(matrices,part.pivot().x()/16,part.pivot().y()/16,part.pivot().z()/16,-part.yaw(),false);
            rotate(matrices,part.pivot().x()/16,part.pivot().y()/16,part.pivot().z()/16,-part.pitch(),true);
            rotate(matrices,part.extraPivot().x()/16,part.extraPivot().y()/16,part.extraPivot().z()/16,part.extraYaw(),false);
            var selectedModel=state.get(CompositeRootBlock.PROFILE)==CompositeRootBlock.Profile.ALT?part.altModel():part.model();
            var model=client.getBakedModelManager().getModel(selectedModel);
            if(diagnostics)dev.dreamwalker.bloodbornedw.diagnostics.DwClientDiagnostics.visualModel(client.player==null?null:net.minecraft.registry.Registries.ITEM.getId(client.player.getMainHandStack().getItem()).toString(),state,instanceId,root,selectedModel,model,root==null?"inventory-render":"world-root-render");
            client.getBlockRenderManager().getModelRenderer().render(matrices.peek(),consumer,state,model,1,1,1,light,overlay);matrices.pop();
        }matrices.pop();if(started!=0)dev.dreamwalker.bloodbornedw.diagnostics.DwClientDiagnostics.measured(type,root,"composite-render-parts.wallElapsed.notGPU",System.nanoTime()-started);
    }
    private static void rotate(MatrixStack matrices,double x,double y,double z,double degrees,boolean pitch){if(degrees==0)return;matrices.translate(x,y,z);matrices.multiply((pitch?RotationAxis.POSITIVE_X:RotationAxis.POSITIVE_Y).rotationDegrees((float)degrees));matrices.translate(-x,-y,-z);}
    private static double[] visualBounds(CompositeRootBlock block,BlockState state){double[] result={0,0,0,1,1,1};for(var bounds:block.spec.pose(state).selection()){result[0]=Math.min(result[0],bounds.from().x()/16);result[1]=Math.min(result[1],bounds.from().y()/16);result[2]=Math.min(result[2],bounds.from().z()/16);result[3]=Math.max(result[3],bounds.to().x()/16);result[4]=Math.max(result[4],bounds.to().y()/16);result[5]=Math.max(result[5],bounds.to().z()/16);}return result;}
    private CompositeClient(){}
}
