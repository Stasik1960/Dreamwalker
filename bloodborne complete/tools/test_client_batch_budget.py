"""Compile only the production's pure JSON budget code and run synthetic regression.

This does not load Minecraft, Fabric or the production outer class. It proves
oversized metadata/rows cannot displace every timing, corrupt ACKs, mutate local
source data, exceed the receiver's32-field bound or produce an oversize packet.
"""
from __future__ import annotations
import argparse
import subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'src/architecture/java/dev/dreamwalker/bloodbornedw/diagnostics/DwClientDiagnostics.java'

HARNESS=r'''
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static JsonObject fixture(){
        JsonObject source=new JsonObject();source.addProperty("schema","dw-client-diagnostics-v1");source.addProperty("session","synthetic-unit-session");source.addProperty("side","client");
        for(int i=0;i<21;i++)source.addProperty("scalar"+i,"fixed bounded scalar");
        JsonObject metadata=new JsonObject();metadata.addProperty("productionArtifactSha256","0".repeat(64));metadata.addProperty("modVersion","unit");metadata.addProperty("processId",1);
        JsonArray mods=new JsonArray();for(int i=0;i<512;i++){JsonObject mod=new JsonObject();mod.addProperty("id","nested_mod_"+i);mod.addProperty("version","large_original_version_".repeat(24));mods.add(mod);}metadata.add("modVersions",mods);source.add("runtimeMetadata",metadata);
        JsonArray timings=new JsonArray();for(int i=0;i<128;i++){JsonObject timing=new JsonObject();timing.addProperty("typeChunkSection","91086|0,0|sampled_render_section_"+i);timing.addProperty("measurements",512);timing.addProperty("totalNs",512000);timing.addProperty("retainedSamples",512);timing.addProperty("droppedSamples",0);timing.addProperty("scope","actual sampled elapsed invocations, not independent CPU; ".repeat(3));timings.add(timing);}source.add("timings",timings);
        JsonArray events=new JsonArray(),errors=new JsonArray();for(int i=0;i<256;i++){JsonObject row=new JsonObject();row.addProperty("operationNumber",i);row.addProperty("text","unicode_Ж_😀_bounded_".repeat(30));events.add(row);errors.add(row.deepCopy());}source.add("events",events);source.add("errors",errors);
        JsonArray acks=new JsonArray();for(int i=0;i<8;i++){JsonObject ack=new JsonObject();ack.addProperty("operation",i);ack.addProperty("instanceId","synthetic-owner-"+i);ack.addProperty("purpose","world-root-render");ack.addProperty("result","ACTUAL_RENDER_MODEL_SELECTED");ack.addProperty("selectedClientModel","source-authored-model_".repeat(5));acks.add(ack);}source.add("placementRenderAcknowledgements",acks);return source;
    }
    public static void main(String[] ignored){
        JsonObject full=fixture();String before=GSON.toJson(full);BatchPacket packet=budgetBatch(full,0);JsonObject wire=JsonParser.parseString(packet.json()).getAsJsonObject();
        check(packet.json().length()<=16384,"character packet bound");check(packet.json().getBytes(StandardCharsets.UTF_8).length<=60000,"UTF8 packet bound");check(wire.size()<=32,"actual server receiver field bound");
        check(!wire.getAsJsonObject("runtimeMetadata").has("modVersions"),"full metadata must not repeat on wire");check(wire.getAsJsonObject("runtimeMetadata").get("productionArtifactSha256").equals(full.getAsJsonObject("runtimeMetadata").get("productionArtifactSha256")),"exact artifact identity retained");
        JsonArray retained=wire.getAsJsonArray("timings");check(retained.size()>0,"huge mod metadata must not erase all sampled timings");
        JsonArray acks=wire.getAsJsonArray("placementRenderAcknowledgements");check(acks.size()>0&&acks.get(acks.size()-1).equals(full.getAsJsonArray("placementRenderAcknowledgements").get(7)),"newest actual rendering ACK identity preserved");
        JsonObject counts=wire.getAsJsonObject("wireBudget").getAsJsonObject("timings");check(counts.get("availableRows").getAsInt()==128,"complete source timing population");check(counts.get("omittedRows").getAsInt()==128-retained.size(),"explicit wire omitted rows");check(counts.get("omittedSampledInvocations").getAsLong()==512L*(128-retained.size()),"explicit sampled invocation omission population");
        check(before.equals(GSON.toJson(full))&&full.getAsJsonArray("timings").size()==128,"wire budgeting must not mutate/clear bounded local timing source");
        JsonObject tooMany=fixture();tooMany.addProperty("extra1",1);tooMany.addProperty("extra2",2);boolean rejected=false;try{budgetBatch(tooMany,0);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"receiver field overflow must fail before send");
        System.out.println("PASS pure client batch regression: compact512-mod metadata, timings/ACK retention, local-source preservation, UTF8/character/32-field bounds, explicit omissions.");
    }
'''


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home',type=Path,required=True)
    parser.add_argument('--gson-jar',type=Path,required=True)
    args=parser.parse_args();text=SOURCE.read_text(encoding='utf8')
    start=text.index('    private record BatchPacket(')
    end=text.index('    private static JsonObject localTimingExport()',start)
    code=text[start:end]
    directory=(ROOT/'build/client-batch-budget-unit').resolve()
    if not directory.is_relative_to((ROOT/'build').resolve()):raise ValueError('Unit output escaped own build directory')
    directory.mkdir(parents=True,exist_ok=True);source=directory/'ClientBatchBudgetHarness.java'
    source.write_text('import com.google.gson.*;\nimport java.util.*;\nimport java.nio.charset.StandardCharsets;\npublic class ClientBatchBudgetHarness {\nprivate static final Gson GSON=new Gson();\n'+code+HARNESS+'\n}\n',encoding='utf8')
    gson=args.gson_jar.resolve();javac=args.java_home.resolve()/'bin/javac.exe';java=args.java_home.resolve()/'bin/java.exe'
    subprocess.run([str(javac),'-encoding','UTF-8','--release','17','-cp',str(gson),'-d',str(directory),str(source)],check=True)
    subprocess.run([str(java),'-cp',str(directory)+';'+str(gson),'ClientBatchBudgetHarness'],check=True)


if __name__=='__main__':main()
