"""Read-only isolated reproduction of Lithium-style map copy and raw NBT order.

This is a simulation using the actual fastutil/NBT classes, not a Fabric-loaded
world or proof that the full-mod scene's actual mismatch had this exact cause.
"""
from pathlib import Path
import hashlib
import json
import subprocess

ROOT=Path(__file__).resolve().parents[1]
JDK=Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin')
LITHIUM=ROOT/'build/runtime-server-first-set-v8-final-full-author/mods/lithium-fabric-mc1.20.1-0.11.2.jar'
REPRO=r'''
import java.util.*;
import java.io.*;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.nbt.*;
var ctor=NbtCompound.class.getDeclaredConstructor(Map.class);
ctor.setAccessible(true);
var m=new Object2ObjectOpenHashMap<String,NbtElement>();
m.put("GlazingMounted",NbtByte.of(true));m.put("MountFace",NbtString.of("up"));m.put("MountY",NbtDouble.of(0));
var a=(NbtCompound)ctor.newInstance(m);
var cm=new Object2ObjectOpenHashMap<String,NbtElement>(m);
var b=(NbtCompound)ctor.newInstance(cm);
var ba=new ByteArrayOutputStream();var bb=new ByteArrayOutputStream();
NbtIo.write(a,new DataOutputStream(ba));NbtIo.write(b,new DataOutputStream(bb));
System.out.println("LITHIUM_MAP3 semantic="+a.equals(b)+" byteEqual="+Arrays.equals(ba.toByteArray(),bb.toByteArray())+" beforeKeys="+m.keySet()+" afterKeys="+cm.keySet());
System.out.println("beforeHex="+HexFormat.of().formatHex(ba.toByteArray())+" afterHex="+HexFormat.of().formatHex(bb.toByteArray()));
var m4=new Object2ObjectOpenHashMap<String,NbtElement>();m4.putAll(m);var shift=new NbtList();shift.add(NbtDouble.of(0));shift.add(NbtDouble.of(0));shift.add(NbtDouble.of(0.5));m4.put("SourceShift",shift);
var a4=(NbtCompound)ctor.newInstance(m4);var cm4=new Object2ObjectOpenHashMap<String,NbtElement>(m4);var b4=(NbtCompound)ctor.newInstance(cm4);
var ba4=new ByteArrayOutputStream();var bb4=new ByteArrayOutputStream();NbtIo.write(a4,new DataOutputStream(ba4));NbtIo.write(b4,new DataOutputStream(bb4));
System.out.println("LITHIUM_MAP4 semantic="+a4.equals(b4)+" byteEqual="+Arrays.equals(ba4.toByteArray(),bb4.toByteArray())+" beforeKeys="+m4.keySet()+" afterKeys="+cm4.keySet());
/exit
'''

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    lines=(ROOT/'build/wall-shared-client-api-check/compile.args').read_text(encoding='utf8').splitlines()
    cp=lines[lines.index('"-cp"')+1].strip('"')
    result=subprocess.run([str(JDK/'jshell.exe'),'--class-path',cp],input=REPRO,capture_output=True,text=True,timeout=30)
    log=ROOT/'reports/NBT_LITHIUM_MAP_COPY_JSHELL_SIMULATED.log'
    log.write_text(result.stdout+result.stderr,encoding='utf8')
    observations=[line.replace('\b','').strip()for line in result.stdout.splitlines()if 'LITHIUM_MAP' in line or 'beforeHex=' in line]
    assert result.returncode==0 and sum('semantic=true byteEqual=false' in line for line in observations)==2
    bytecode=subprocess.run([str(JDK/'javap.exe'),'-c','-p','-classpath',str(LITHIUM),
        'me.jellysquid.mods.lithium.mixin.alloc.nbt.NbtCompoundMixin',
        'me.jellysquid.mods.lithium.mixin.alloc.nbt.NbtCompoundMixin$Type'],capture_output=True,text=True)
    assert bytecode.returncode==0 and 'Object2ObjectOpenHashMap' in bytecode.stdout
    bytecode_path=ROOT/'reports/NBT_LITHIUM_LOCAL_BYTECODE.txt';bytecode_path.write_text(bytecode.stdout+bytecode.stderr,encoding='utf8')
    report={'schema':'dreamwalker-nbt-lithium-style-map-copy-simulation-v1',
        'status':'PASS_REPRODUCED_TYPED_EQUAL_RAW_ORDER_DIFFERENT_SIMULATED_MAP_COPY',
        'scope':'Actual namedMinecraft1.20.1 NbtIo/NbtCompound andfastutil classes; inject fastutil map via protectedconstructor and simulate map-copy. Fabric/Lithium mixins are NOT running in this JShell process.',
        'lithiumLocalJar':'build/runtime-server-first-set-v8-final-full-author/mods/'+LITHIUM.name,
        'lithiumLocalJarSha256':sha(LITHIUM),'localBytecode':'reports/'+bytecode_path.name,
        'localBytecodeSha256':sha(bytecode_path),'jshellLog':'reports/'+log.name,'jshellLogSha256':sha(log),
        'observations':observations,'worldOrSourceModified':False,'actualFullModMismatchProvenByThisSimulation':False,
        'actualRuntimeObservation':'QA ReviewSceneBootstrap nbtSerializationProbe, recorded during the root-coordinated next actual full-mod authoring run.',
        'copyPolicyFromLocalBytecode':'Lithium alloc.nbt replacesdefaultmap with Object2ObjectOpenHashMap andcopy with Object2ObjectOpenHashMap(Map.transformValues). Iteration order can differ with mapcapacity/history.',
        'diagnosticImplication':'TransactionCore compares encodedBE bytes. Same typed compound in a different key iteration order can produce write_not_exact; actual expected/actualworld data is required to confirm the failingbranch.',
        'limits':['Three/four-key default-versus-copied map examples both reorder. This does not independently explain the exact floor-versus-vertical outcome; payload construction/copy capacities matter.','No client/server/world/Gradle run, no production changes, no compatibility PASS inferred.']}
    (ROOT/'reports/NBT_LITHIUM_MAP_COPY_JSHELL_SIMULATED.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'status':report['status'],'observedSemanticEqualRawDifferentCases':2,'actualWorldCauseProven':False}))

if __name__=='__main__':main()
