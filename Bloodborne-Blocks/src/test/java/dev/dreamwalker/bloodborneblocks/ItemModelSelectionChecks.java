package dev.dreamwalker.bloodborneblocks;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** Bootstrap-only dispatch coverage for inventory art wrappers; no client renderer is started. */
public final class ItemModelSelectionChecks {
 private ItemModelSelectionChecks() {}

 public static void main(String[] args) {
  SharedConstants.createGameVersion();Bootstrap.initialize();
  BloodborneBlocks.Data production=BloodborneBlocks.loadDefinitions();GeometryRuntime.loadAndValidate(production);
  BloodborneBlocks.Data city=BloodborneBlocks.loadCityDefinitions();GeometryRuntime.loadCityAndValidate(city);
  BloodborneBlocks.Definition productionArt=production.blocks.stream().filter(d->d.properties.containsKey("variant")&&d.properties.containsKey("visual")).findFirst().orElseThrow();
  ArchitectureBlock productionBlock=register(productionArt,false);
  ArchitectureBlock window=register(find(production.blocks,"o_shuttered_window"),false);
  BloodborneBlocks.Definition cityArt=city.blocks.stream().filter(d->d.models!=null&&!d.whole_owner&&d.properties.containsKey("variant")).findFirst().orElseThrow();
  ArchitectureBlock cityBlock=register(cityArt,true);

  checkProductionVariantAndVisual(productionBlock,productionArt);
  checkWindowVisuals(window);
  checkCityVariant(cityBlock,cityArt);
  System.out.println("ITEM MODEL SELECTION CHECKS PASSED");
 }

 private static void checkProductionVariantAndVisual(ArchitectureBlock block,BloodborneBlocks.Definition definition) {
  ItemStack stack=stack(block);String variant=nonDefault(definition,"variant");put(stack,"variant",variant);put(stack,"visual","alt");
  String key=ArchitectureCreativeCatalog.itemModelKey(block,stack);Counter fallback=new Counter("production fallback");Counter selected=new Counter("production "+key);
  Map<String,BakedModel> variants=new HashMap<>();CityVariantItemModel wrapper=new CityVariantItemModel(fallback.model(),block,variants);
  variants.put(key,selected.model()); // Simulates model-bake population after the inventory wrapper exists.
  emit(wrapper,stack);selected.only(fallback,selected);check(wrapper.getTransformation()==fallback.model().getTransformation()&&wrapper.getTransformation()==ModelTransformation.NONE,"wrapper retains fallback inventory transformation");
  put(stack,"facing","south");put(stack,"open","true");emit(wrapper,stack);selected.only(fallback,selected);
  put(stack,"visual","missing");emit(wrapper,stack);fallback.only(fallback,selected);
 }

 private static void checkWindowVisuals(ArchitectureBlock block) {
  Counter fallback=new Counter("window fallback"),base=new Counter("window base"),alt=new Counter("window alt");Map<String,BakedModel> variants=new HashMap<>();CityVariantItemModel wrapper=new CityVariantItemModel(fallback.model(),block,variants);
  ItemStack baseStack=stack(block),altStack=stack(block);put(altStack,"visual","alt");
  variants.put(ArchitectureCreativeCatalog.itemModelKey(block,baseStack),base.model());variants.put(ArchitectureCreativeCatalog.itemModelKey(block,altStack),alt.model());
  emit(wrapper,baseStack);base.only(fallback,base,alt);emit(wrapper,altStack);alt.only(fallback,base,alt);
  put(altStack,"facing","west");put(altStack,"open","true");emit(wrapper,altStack);alt.only(fallback,base,alt);
  ItemStack missing=stack(block);put(missing,"visual","missing");variants.remove(ArchitectureCreativeCatalog.itemModelKey(block,missing));emit(wrapper,missing);fallback.only(fallback,base,alt);
 }

 private static void checkCityVariant(ArchitectureBlock block,BloodborneBlocks.Definition definition) {
  ItemStack stack=stack(block);put(stack,"variant",nonDefault(definition,"variant"));String key=ArchitectureCreativeCatalog.itemModelKey(block,stack);
  Counter fallback=new Counter("city fallback"),selected=new Counter("city "+key);Map<String,BakedModel> variants=new HashMap<>();CityVariantItemModel wrapper=new CityVariantItemModel(fallback.model(),block,variants);variants.put(key,selected.model());
  emit(wrapper,stack);selected.only(fallback,selected);put(stack,"variant","invalid");emit(wrapper,stack);fallback.only(fallback,selected);
 }

 private static ArchitectureBlock register(BloodborneBlocks.Definition definition,boolean city) {
  BloodborneBlocks.prepareDefinition(definition);ArchitectureBlock block=ArchitectureBlock.create(definition);Registry.register(Registries.BLOCK,BloodborneBlocks.id(definition.id),block);
  ArchitectureBlockItem item=new ArchitectureBlockItem(block,new Item.Settings());Registry.register(Registries.ITEM,BloodborneBlocks.id(definition.id),item);item.appendBlocks(Item.BLOCK_ITEMS,item);
  (city?BloodborneBlocks.CITY_BLOCKS:BloodborneBlocks.BLOCKS).put(definition.id,block);return block;
 }
 private static BloodborneBlocks.Definition find(List<BloodborneBlocks.Definition> definitions,String id){return definitions.stream().filter(d->d.id.equals(id)).findFirst().orElseThrow();}
 private static String nonDefault(BloodborneBlocks.Definition definition,String property){String value=definition.defaultProperties.get(property);return definition.properties.get(property).stream().filter(v->!v.equals(value)).findFirst().orElseThrow(()->new AssertionError("missing non-default "+property+" for "+definition.id));}
 private static ItemStack stack(ArchitectureBlock block){return new ItemStack(block.asItem());}
 private static void put(ItemStack stack,String key,String value){stack.getOrCreateSubNbt("BlockStateTag").putString(key,value);}
 private static void emit(CityVariantItemModel wrapper,ItemStack stack){wrapper.emitItemQuads(stack,()->null,null);}
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}

 private static final class Counter implements InvocationHandler {
  private final String name;private final BakedModel model;private int calls;
  Counter(String name){this.name=name;model=(BakedModel)Proxy.newProxyInstance(ItemModelSelectionChecks.class.getClassLoader(),new Class<?>[]{BakedModel.class,FabricBakedModel.class},this);}
  BakedModel model(){return model;}
  void only(Counter... counters){for(Counter counter:counters)check(counter.calls==(counter==this?1:0),"expected "+name+" dispatch, got "+counter.name+"="+counter.calls);for(Counter counter:counters)counter.calls=0;}
  @Override public Object invoke(Object proxy,Method method,Object[] args){
   return switch(method.getName()){
    case "emitItemQuads"->{calls++;yield null;} case "getTransformation"->ModelTransformation.NONE;case "isVanillaAdapter"->false;
    case "equals"->proxy==args[0];case "hashCode"->System.identityHashCode(proxy);case "toString"->name;default->defaultValue(method.getReturnType());
   };
  }
  private static Object defaultValue(Class<?> type){if(!type.isPrimitive())return null;if(type==boolean.class)return false;if(type==char.class)return (char)0;if(type==long.class)return 0L;if(type==float.class)return 0F;if(type==double.class)return 0D;return 0;}
 }
}
