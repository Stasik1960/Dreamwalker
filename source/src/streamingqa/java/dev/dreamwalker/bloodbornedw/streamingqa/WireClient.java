package dev.dreamwalker.bloodbornedw.streamingqa;

import io.netty.buffer.Unpooled;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.*;
import net.minecraft.client.option.ChatVisibility;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.login.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.login.*;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.*;
import net.minecraft.util.math.ChunkPos;

/** A real loopback TCP receiver. It accepts chunk bytes but deliberately creates
 * no client world or renderer. No fake player, forced chunk or server send count
 * is substituted for a successfully received chunk packet. */
final class WireClient implements AutoCloseable {
    static final String NAME="DWStreamingQA";
    final Set<Long> loaded=ConcurrentHashMap.newKeySet();
    final AtomicLong deliveries=new AtomicLong(),unloads=new AtomicLong(),ownerPackets=new AtomicLong(),ownerBytes=new AtomicLong();
    volatile String failure;
    volatile boolean joined,closed;
    private volatile int compression=-1;
    private volatile NetworkState state=NetworkState.LOGIN;
    private Socket socket;
    private DataOutputStream output;
    private double x,y,z;

    void start(int port) {
        Thread thread=new Thread(()->{
            try {
                socket=new Socket(InetAddress.getLoopbackAddress(),port);socket.setTcpNoDelay(true);
                output=new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                send(NetworkState.HANDSHAKING,new HandshakeC2SPacket("127.0.0.1",port,NetworkState.LOGIN));
                send(NetworkState.LOGIN,new LoginHelloC2SPacket(NAME,Optional.empty()));
                DataInputStream input=new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                while(!closed){
                    int length=readVar(input);if(length<1||length>16_777_216)throw new IOException("Invalid packet length "+length);
                    byte[] bytes=input.readNBytes(length);if(bytes.length!=length)throw new EOFException();
                    if(compression>=0){
                        var compressed=new DataInputStream(new ByteArrayInputStream(bytes));int size=readVar(compressed);
                        if(size>0){if(size>16_777_216)throw new IOException("Inflated packet limit");try(var inflater=new InflaterInputStream(compressed)){bytes=inflater.readNBytes(size);if(bytes.length!=size)throw new IOException("Truncated compressed packet");}}
                        else bytes=compressed.readAllBytes();
                    }
                    var buffer=new PacketByteBuf(Unpooled.wrappedBuffer(bytes));
                    try{
                        int id=buffer.readVarInt();Class<?> type=state.getPacketIdToPacketMap(NetworkSide.CLIENTBOUND).get(id);
                        if(type==null)throw new IOException("Unknown packet "+id+" in "+state);
                        if(state==NetworkState.LOGIN){
                            if(type==LoginCompressionS2CPacket.class){compression=buffer.readVarInt();continue;}
                            if(type==LoginSuccessS2CPacket.class){state=NetworkState.PLAY;continue;}
                            if(type==LoginQueryRequestS2CPacket.class){send(NetworkState.LOGIN,new LoginQueryResponseC2SPacket(buffer.readVarInt(),null));continue;}
                            if(type==LoginDisconnectS2CPacket.class)throw new IOException("Login rejected: "+buffer.readText());
                        }else{
                            if(type==ChunkDataS2CPacket.class){loaded.add(ChunkPos.toLong(buffer.readInt(),buffer.readInt()));deliveries.incrementAndGet();continue;}
                            if(type.getSimpleName().equals("UnloadChunkS2CPacket")){loaded.remove(ChunkPos.toLong(buffer.readInt(),buffer.readInt()));unloads.incrementAndGet();continue;}
                            if(type==CustomPayloadS2CPacket.class){if(buffer.readIdentifier().toString().equals("bloodborne_dw:composite_owners")){ownerPackets.incrementAndGet();ownerBytes.addAndGet(buffer.readableBytes());}continue;}
                            if(type==GameJoinS2CPacket.class){
                                send(state,new ClientSettingsC2SPacket("en_us",8,ChatVisibility.FULL,true,127,Arm.RIGHT,false,true));
                                var channels=new PacketByteBuf(Unpooled.buffer());try{channels.writeBytes("bloodborne_dw:composite_owners".getBytes(StandardCharsets.UTF_8));send(state,new CustomPayloadC2SPacket(new Identifier("minecraft:register"),channels));}finally{channels.release();}
                                joined=true;continue;
                            }
                            if(type==KeepAliveS2CPacket.class){send(state,new KeepAliveC2SPacket(buffer.readLong()));continue;}
                            if(type==PlayerPositionLookS2CPacket.class){
                                var p=(PlayerPositionLookS2CPacket)state.getPacketHandler(NetworkSide.CLIENTBOUND,id,buffer);
                                synchronized(this){x=p.getFlags().contains(PositionFlag.X)?x+p.getX():p.getX();y=p.getFlags().contains(PositionFlag.Y)?y+p.getY():p.getY();z=p.getFlags().contains(PositionFlag.Z)?z+p.getZ():p.getZ();send(state,new TeleportConfirmC2SPacket(p.getTeleportId()));move(x,y,z);}continue;
                            }
                            if(type==DisconnectS2CPacket.class)throw new IOException("Play disconnected: "+buffer.readText());
                        }
                    }finally{buffer.release();}
                }
            }catch(Throwable problem){if(!closed){failure=problem.toString();problem.printStackTrace();}}
        },"QA TCP chunk receiver");thread.setDaemon(true);thread.start();
    }
    synchronized void move(double nextX,double nextY,double nextZ)throws IOException {x=nextX;y=nextY;z=nextZ;send(NetworkState.PLAY,new PlayerMoveC2SPacket.Full(x,y,z,0,0,false));}
    private synchronized void send(NetworkState protocol,Packet<?> packet)throws IOException {
        var buffer=new PacketByteBuf(Unpooled.buffer());
        try{
            buffer.writeVarInt(protocol.getPacketId(NetworkSide.SERVERBOUND,packet));packet.write(buffer);byte[] body=new byte[buffer.readableBytes()];buffer.readBytes(body);
            var frame=new ByteArrayOutputStream();
            if(compression>=0){
                if(body.length>=compression){writeVar(frame,body.length);try(var deflater=new DeflaterOutputStream(frame)){deflater.write(body);}}
                else{writeVar(frame,0);frame.write(body);}
                body=frame.toByteArray();
            }
            writeVar(output,body.length);output.write(body);output.flush();
        }finally{buffer.release();}
    }
    static int readVar(DataInputStream input)throws IOException {int n=0;for(int i=0;i<5;i++){int b=input.readUnsignedByte();n|=(b&127)<<(7*i);if((b&128)==0)return n;}throw new IOException("VarInt overflow");}
    static void writeVar(OutputStream output,int n)throws IOException {do{int b=n&127;n>>>=7;output.write(n==0?b:b|128);}while(n!=0);}
    @Override public void close()throws IOException {closed=true;if(socket!=null)socket.close();}
}
