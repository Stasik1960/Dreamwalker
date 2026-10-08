package dev.dreamwalker.bloodbornedw.review;

import com.google.gson.*;
import java.util.*;
import java.math.BigDecimal;

/** QA only: optional Iris public API reflection; no production dependency.
 * An initialized pipeline plus advancing live counter is stronger evidence
 * than a shader ZIP/config path or a pre-constructor log message. */
public final class ReviewV10ShaderProbe {
    private ReviewV10ShaderProbe(){}
    public static JsonObject observe()throws Exception{
        Class<?> iris=Class.forName("net.irisshaders.iris.Iris");Object config=iris.getMethod("getIrisConfig").invoke(null);
        Optional<?> pack=(Optional<?>)iris.getMethod("getCurrentPack").invoke(null);Object manager=iris.getMethod("getPipelineManager").invoke(null),pipeline=manager.getClass().getMethod("getPipelineNullable").invoke(manager);
        JsonObject row=new JsonObject();row.addProperty("currentPackName",(String)iris.getMethod("getCurrentPackName").invoke(null));row.addProperty("packPresent",pack.isPresent());row.addProperty("shadersEnabled",(boolean)config.getClass().getMethod("areShadersEnabled").invoke(config));row.addProperty("fallback",(boolean)iris.getMethod("isFallback").invoke(null));row.addProperty("pipelineClass",pipeline==null?"null":pipeline.getClass().getName());
        Object counter=Class.forName("net.irisshaders.iris.uniforms.SystemTimeUniforms").getField("COUNTER").get(null);row.addProperty("frameCounter",((java.util.function.IntSupplier)counter).getAsInt());
        if(pipeline!=null&&pipeline.getClass().getName().equals("net.irisshaders.iris.pipeline.IrisRenderingPipeline")){row.addProperty("shaderMapPresent",pipeline.getClass().getMethod("getShaderMap").invoke(pipeline)!=null);row.addProperty("shouldOverrideShadersAtTick",(boolean)pipeline.getClass().getMethod("shouldOverrideShaders").invoke(pipeline));row.addProperty("skipAllRendering",(boolean)pipeline.getClass().getMethod("skipAllRendering").invoke(pipeline));}
        row.addProperty("methodScope","live Iris public reflection at client tick; render-phase override flag is informational; no screenshot/GPU timing claim");return row;
    }
    public static JsonObject verify(JsonObject marker,int initialCounter)throws Exception{
        JsonObject row=observe();
        if(!marker.get("expectedShaderPack").getAsString().equals(row.get("currentPackName").getAsString())||!row.get("packPresent").getAsBoolean()||!row.get("shadersEnabled").getAsBoolean()||row.get("fallback").getAsBoolean()||!row.get("pipelineClass").getAsString().equals("net.irisshaders.iris.pipeline.IrisRenderingPipeline")||!row.get("shaderMapPresent").getAsBoolean()||row.get("skipAllRendering").getAsBoolean())throw new IllegalStateException("Requested active Iris pipeline missing: "+row);
        if(row.get("frameCounter").getAsInt()<=initialCounter)throw new IllegalStateException("Actual Iris rendering counter did not advance");row.addProperty("frameCounterAdvanced",true);row.addProperty("initialFrameCounter",initialCounter);
        JsonArray checks=new JsonArray();row.add("effectiveOptions",checks);
        if(marker.has("expectedShaderOptions")){
            if(marker.getAsJsonObject("expectedShaderOptions").size()>128)throw new IllegalArgumentException("Shader option budget");
            Object pack=((Optional<?>)Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPack").invoke(null)).orElseThrow();Object options=pack.getClass().getMethod("getShaderPackOptions").invoke(pack),values=options.getClass().getMethod("getOptionValues").invoke(options);Class<?> api=Class.forName("net.irisshaders.iris.shaderpack.option.values.OptionValues");Object set=api.getMethod("getOptionSet").invoke(values);Map<?,?> bools=(Map<?,?>)set.getClass().getMethod("getBooleanOptions").invoke(set),strings=(Map<?,?>)set.getClass().getMethod("getStringOptions").invoke(set);
            for(var expected:marker.getAsJsonObject("expectedShaderOptions").entrySet()){
                String key=expected.getKey(),wanted=expected.getValue().getAsString(),actual=null;if(!key.matches("[A-Za-z_][A-Za-z0-9_]*"))throw new IllegalArgumentException("Invalid shader option key");
                if(bools.containsKey(key))actual=String.valueOf(api.getMethod("getBooleanValueOrDefault",String.class).invoke(values,key));else if(strings.containsKey(key))actual=(String)api.getMethod("getStringValueOrDefault",String.class).invoke(values,key);
                boolean matches=same(wanted,actual);JsonObject check=new JsonObject();check.addProperty("key",key);check.addProperty("expected",wanted);check.addProperty("actual",actual);check.addProperty("matches",matches);checks.add(check);if(!matches)throw new IllegalStateException("Actual shader getter differs: "+check);
            }
        }
        row.addProperty("optionCount",checks.size());row.addProperty("status","PASS_ACTIVE_IRIS_RENDERING_PIPELINE_AND_REQUESTED_GETTERS");return row;
    }
    private static boolean same(String wanted,String actual){if(Objects.equals(wanted,actual))return true;if(actual==null)return false;try{return new BigDecimal(wanted).compareTo(new BigDecimal(actual))==0;}catch(NumberFormatException failure){return false;}}
}
