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
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import org.joml.Matrix4f;
import org.joml.Vector3f;

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
  checkSelectedGuiUsesFallbackNonGui(productionBlock,productionArt);
  checkWindowVisuals(window);
  checkCityVariant(cityBlock,cityArt);
  checkGuiBoundsMathAndCache();
  checkAutumnQuadPoolPalette();
  System.out.println("ITEM MODEL SELECTION CHECKS PASSED");
 }

 private static void checkProductionVariantAndVisual(ArchitectureBlock block,BloodborneBlocks.Definition definition) {
  ItemStack stack=stack(block);String variant=nonDefault(definition,"variant");put(stack,"variant",variant);put(stack,"visual","alt");
  String key=ArchitectureCreativeCatalog.itemModelKey(block,stack);Counter fallback=new Counter("production fallback");Counter selected=new Counter("production "+key);
  Map<String,BakedModel> variants=new HashMap<>();CityVariantItemModel wrapper=new CityVariantItemModel(fallback.model(),block,variants,new GuiItemBounds.Cache());
  variants.put(key,selected.model()); // Simulates model-bake population after the inventory wrapper exists.
  emit(wrapper,stack);selected.only(fallback,selected);check(wrapper.getOverrides().apply(wrapper,stack,null,null,0) instanceof GuiItemModel,"override returns fitted selected art");check(wrapper.getTransformation().getTransformation(ModelTransformationMode.FIRST_PERSON_RIGHT_HAND)==fallback.model().getTransformation().getTransformation(ModelTransformationMode.FIRST_PERSON_RIGHT_HAND),"wrapper retains fallback inventory transformation");
  put(stack,"facing","south");put(stack,"open","true");emit(wrapper,stack);selected.only(fallback,selected);
  put(stack,"visual","missing");emit(wrapper,stack);fallback.only(fallback,selected);
 }

 private static void checkWindowVisuals(ArchitectureBlock block) {
  Counter fallback=new Counter("window fallback"),base=new Counter("window base"),alt=new Counter("window alt");Map<String,BakedModel> variants=new HashMap<>();CityVariantItemModel wrapper=new CityVariantItemModel(fallback.model(),block,variants,new GuiItemBounds.Cache());
  ItemStack baseStack=stack(block),altStack=stack(block);put(altStack,"visual","alt");
  variants.put(ArchitectureCreativeCatalog.itemModelKey(block,baseStack),base.model());variants.put(ArchitectureCreativeCatalog.itemModelKey(block,altStack),alt.model());
  emit(wrapper,baseStack);base.only(fallback,base,alt);emit(wrapper,altStack);alt.only(fallback,base,alt);
  put(altStack,"facing","west");put(altStack,"open","true");emit(wrapper,altStack);alt.only(fallback,base,alt);
  ItemStack missing=stack(block);put(missing,"visual","missing");variants.remove(ArchitectureCreativeCatalog.itemModelKey(block,missing));emit(wrapper,missing);fallback.only(fallback,base,alt);
 }

 private static void checkCityVariant(ArchitectureBlock block,BloodborneBlocks.Definition definition) {
  ItemStack stack=stack(block);put(stack,"variant",nonDefault(definition,"variant"));String key=ArchitectureCreativeCatalog.itemModelKey(block,stack);
  Counter fallback=new Counter("city fallback"),selected=new Counter("city "+key);Map<String,BakedModel> variants=new HashMap<>();CityVariantItemModel wrapper=new CityVariantItemModel(fallback.model(),block,variants,new GuiItemBounds.Cache());variants.put(key,selected.model());
  emit(wrapper,stack);selected.only(fallback,selected);put(stack,"variant","invalid");emit(wrapper,stack);fallback.only(fallback,selected);
 }

 private static void checkGuiBoundsMathAndCache(){
  GuiItemBounds.Fit oversized=GuiItemBounds.fitForBounds(new GuiItemBounds.Box(-2,-1,0,2,3,1));check(Math.abs(oversized.scale()-.21F)<1e-6&&Math.abs(oversized.translateX())<1e-6&&Math.abs(oversized.translateY()+.21F)<1e-6,"oversized off-center model fits slot with padding");
  GuiItemBounds.Fit small=GuiItemBounds.fitForBounds(new GuiItemBounds.Box(-.2F,-.1F,-1,.2F,.1F,1));check(small.scale()==1&&Math.abs(small.translateX())<1e-6&&Math.abs(small.translateY())<1e-6,"small negative-coordinate model centers without enlargement");
  Counter counter=new Counter("cache");GuiItemBounds.Cache first=new GuiItemBounds.Cache();check(first.get(counter.model())==first.get(counter.model())&&first.size()==1,"one baked model computes bounds once per reload cache");GuiItemBounds.Cache reload=new GuiItemBounds.Cache();reload.get(counter.model());check(reload.size()==1,"new reload owns a fresh bounds cache");GuiItemModel gui=new GuiItemModel(counter.model(),first.get(counter.model()));check(gui.getTransformation().getTransformation(net.minecraft.client.render.model.json.ModelTransformationMode.FIRST_PERSON_RIGHT_HAND)==ModelTransformation.NONE.getTransformation(net.minecraft.client.render.model.json.ModelTransformationMode.FIRST_PERSON_RIGHT_HAND),"GUI wrapper preserves non-GUI transforms");
  checkRendererMatrixOrder();
 }
 private static void checkRendererMatrixOrder(){
  Matrix4f parent=new Matrix4f().translation(7,-3,2).scale(2,3,1),unchanged=new Matrix4f(parent);Transformation original=new Transformation(new Vector3f(0,0,35),new Vector3f(4,-2,0),new Vector3f(2,1,1));GuiItemBounds.Fit fit=new GuiItemBounds.Fit(.4F,.15F,-.2F);
  Matrix4f actual=GuiItemBounds.rendererMatrix(parent,original,fit);MatrixStack originalStack=new MatrixStack();original.apply(false,originalStack);Matrix4f expected=new Matrix4f(parent).translate(fit.translateX(),fit.translateY(),0).scale(fit.scale()).mul(originalStack.peek().getPositionMatrix()).translate(-.5F,-.5F,-.5F);Vector3f point=new Vector3f(1,.25F,-.5F);
  check(parent.equals(unchanged),"GUI fit never mutates the parent renderer matrix");check(actual.transformPosition(new Vector3f(point)).distance(expected.transformPosition(point))<1e-5,"GUI fit composes after parent and before selected transform");
 }
 private static void checkSelectedGuiUsesFallbackNonGui(ArchitectureBlock block,BloodborneBlocks.Definition definition){
  ModelTransformation fallbackTransform=transforms(1,11),selectedTransform=transforms(2,22);Counter fallback=new Counter("fallback transform",fallbackTransform),selected=new Counter("selected transform",selectedTransform);ItemStack stack=stack(block);put(stack,"variant",nonDefault(definition,"variant"));put(stack,"visual","alt");String key=ArchitectureCreativeCatalog.itemModelKey(block,stack);Map<String,BakedModel> variants=new HashMap<>();variants.put(key,selected.model());CityVariantItemModel wrapper=new CityVariantItemModel(fallback.model(),block,variants,new GuiItemBounds.Cache());BakedModel resolved=wrapper.getOverrides().apply(wrapper,stack,null,null,0);
  for(ModelTransformationMode mode:ModelTransformationMode.values())if(mode!=ModelTransformationMode.GUI)check(resolved.getTransformation().getTransformation(mode)==fallbackTransform.getTransformation(mode),"selected GUI model retains fallback "+mode+" transform");
  Transformation gui=resolved.getTransformation().getTransformation(ModelTransformationMode.GUI);MatrixStack resolvedStack=new MatrixStack(),selectedStack=new MatrixStack(),fallbackStack=new MatrixStack();gui.apply(false,resolvedStack);selectedTransform.getTransformation(ModelTransformationMode.GUI).apply(false,selectedStack);fallbackTransform.getTransformation(ModelTransformationMode.GUI).apply(false,fallbackStack);Vector3f point=new Vector3f(.25F,-.5F,1),actual=resolvedStack.peek().getPositionMatrix().transformPosition(new Vector3f(point));
  check(gui!=selectedTransform.getTransformation(ModelTransformationMode.GUI)&&actual.distance(selectedStack.peek().getPositionMatrix().transformPosition(new Vector3f(point)))<1e-6&&actual.distance(fallbackStack.peek().getPositionMatrix().transformPosition(new Vector3f(point)))>1e-3,"selected GUI transform is fitted from selected art");
 }
 private static void checkAutumnQuadPoolPalette(){
  ModularBakedModel.QuadPool pool=new ModularBakedModel.QuadPool();int[] vertices=new int[32];int firstColor=0xC46A31,secondColor=0xD8A044;
  BakedQuad first=pool.intern(vertices,net.minecraft.util.math.Direction.UP,null,firstColor);BakedQuad identical=pool.intern(vertices.clone(),net.minecraft.util.math.Direction.UP,null,firstColor);BakedQuad different=pool.intern(vertices.clone(),net.minecraft.util.math.Direction.UP,null,secondColor);
  check(first==identical,"identical autumn geometry and palette share a pooled quad");check(first!=different,"different autumn palettes do not share a pooled quad");check(ModularBakedModel.isAutumnTint(first.getColorIndex())&&ModularBakedModel.tintColor(first.getColorIndex())==firstColor,"first pooled quad retains its encoded autumn color");check(ModularBakedModel.tintColor(different.getColorIndex())==secondColor,"second pooled quad encodes its own autumn color");
 }
 private static ModelTransformation transforms(float gui,float other){Transformation guiTransform=new Transformation(new Vector3f(),new Vector3f(gui,0,0),new Vector3f(1));Transformation otherTransform=new Transformation(new Vector3f(),new Vector3f(other,0,0),new Vector3f(1));return new ModelTransformation(otherTransform,otherTransform,otherTransform,otherTransform,otherTransform,guiTransform,otherTransform,otherTransform);}
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
  private final String name;private final BakedModel model;private final ModelTransformation transformations;private int calls;
  Counter(String name){this(name,ModelTransformation.NONE);}
  Counter(String name,ModelTransformation transformations){this.name=name;this.transformations=transformations;model=(BakedModel)Proxy.newProxyInstance(ItemModelSelectionChecks.class.getClassLoader(),new Class<?>[]{BakedModel.class,FabricBakedModel.class},this);}
  BakedModel model(){return model;}
  void only(Counter... counters){for(Counter counter:counters)check(counter.calls==(counter==this?1:0),"expected "+name+" dispatch, got "+counter.name+"="+counter.calls);for(Counter counter:counters)counter.calls=0;}
  @Override public Object invoke(Object proxy,Method method,Object[] args){
   return switch(method.getName()){
    case "emitItemQuads"->{calls++;yield null;} case "getQuads"->List.of();case "getTransformation"->transformations;case "isVanillaAdapter"->false;
    case "equals"->proxy==args[0];case "hashCode"->System.identityHashCode(proxy);case "toString"->name;default->defaultValue(method.getReturnType());
   };
  }
  private static Object defaultValue(Class<?> type){if(!type.isPrimitive())return null;if(type==boolean.class)return false;if(type==char.class)return (char)0;if(type==long.class)return 0L;if(type==float.class)return 0F;if(type==double.class)return 0D;return 0;}
 }
}
