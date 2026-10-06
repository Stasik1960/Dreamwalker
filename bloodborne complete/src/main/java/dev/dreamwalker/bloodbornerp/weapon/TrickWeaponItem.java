package dev.dreamwalker.bloodbornerp.weapon;

import dev.dreamwalker.bloodbornerp.RpConfig;
import dev.dreamwalker.bloodbornerp.content.AssetBacked;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/** One durable, enchantable stack per weapon family; only its form is stored in NBT. */
public final class TrickWeaponItem extends Item implements GeoItem, AssetBacked {
  public static final String FORM_KEY="BloodborneRpForm";
  private static final String PROFILE_KEY="BloodborneRpWeaponProfile";
  private static final String MODIFIERS_KEY="AttributeModifiers";
  private static final String DAMAGE_NAME="bloodborne_rp_weapon_damage";
  private static final String SPEED_NAME="bloodborne_rp_weapon_speed";
  private final String familyId;
  private final WeaponProfile folded;
  private final WeaponProfile extended;
  private final boolean ignitesWhenExtended;
  private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
  private final java.util.function.Supplier<Object> renderProvider=GeoItem.makeRenderer(this);
  /** Set exclusively by the client entrypoint, keeping common code loadable on dedicated servers. */
  public static Consumer<Consumer<Object>> clientRendererConsumer;

  public TrickWeaponItem(String familyId, WeaponProfile folded, WeaponProfile extended, boolean ignitesWhenExtended) {
    super(new Settings().maxCount(1).maxDamage(650));
    this.familyId=familyId;
    this.folded=folded;
    this.extended=extended;
    this.ignitesWhenExtended=ignitesWhenExtended;
    SingletonGeoAnimatable.registerSyncedAnimatable(this);
  }

  @Override public java.util.function.Supplier<Object> getRenderProvider() { return renderProvider; }
  @Override public void createRenderer(Consumer<Object> consumer) {
    if(clientRendererConsumer!=null) clientRendererConsumer.accept(consumer);
  }
  @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
  @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
  @Override public String assetId() { return familyId+"_false"; }
  public String formId(ItemStack stack) { return familyId+"_"+form(stack).nbtValue(); }
  public String familyId() { return familyId; }
  public WeaponProfile profile(ItemStack stack) { return form(stack)==WeaponForm.FOLDED ? folded : extended; }
  public boolean ignitesWhenExtended() { return ignitesWhenExtended; }

  public static WeaponForm form(ItemStack stack) {
    return WeaponForm.fromNbt(stack.hasNbt() && stack.getNbt().getBoolean(FORM_KEY));
  }

  public static void setForm(ItemStack stack, WeaponForm form) {
    stack.getOrCreateNbt().putBoolean(FORM_KEY,form.nbtValue());
    if(stack.getItem() instanceof TrickWeaponItem weapon) weapon.applyProfile(stack);
  }

  @Override public ItemStack getDefaultStack() {
    ItemStack stack=super.getDefaultStack();
    setForm(stack,WeaponForm.FOLDED);
    return stack;
  }

  @Override public void inventoryTick(ItemStack stack, World world, net.minecraft.entity.Entity entity, int slot, boolean selected) {
    if(!world.isClient && !hasCurrentProfile(stack)) applyProfile(stack);
  }

  @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
    ItemStack stack=user.getStackInHand(hand);
    if(!world.isClient && user instanceof ServerPlayerEntity player) WeaponRegistry.transform(player,hand);
    return TypedActionResult.success(stack,world.isClient);
  }

  @Override public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
    WeaponForm current=form(stack);
    if(ignitesWhenExtended && current==WeaponForm.EXTENDED && !target.getWorld().isClient) {
      target.setOnFireFor(4);
      setForm(stack,WeaponForm.FOLDED);
      if(attacker instanceof PlayerEntity player) player.getItemCooldownManager().set(this,RpConfig.INSTANCE.weaponTransformCooldownTicks);
    }
    EquipmentSlot breakSlot=attacker.getMainHandStack()==stack ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    stack.damage(1,attacker,entity -> entity.sendEquipmentBreakStatus(breakSlot));
    return true;
  }

  private boolean hasCurrentProfile(ItemStack stack) {
    NbtCompound tag=stack.getNbt();
    return tag!=null && tag.getString(PROFILE_KEY).equals(formId(stack));
  }

  private void applyProfile(ItemStack stack) {
    NbtCompound tag=stack.getOrCreateNbt();
    NbtList modifiers=tag.getList(MODIFIERS_KEY,NbtElement.COMPOUND_TYPE);
    NbtList retained=new NbtList();
    for(int i=0;i<modifiers.size();i++) {
      NbtCompound modifier=modifiers.getCompound(i);
      if(!DAMAGE_NAME.equals(modifier.getString("Name")) && !SPEED_NAME.equals(modifier.getString("Name"))) retained.add(modifier.copy());
    }
    WeaponProfile profile=profile(stack);
    retained.add(attributeModifier("generic.attack_damage",DAMAGE_NAME,profile.attackDamage(),damageUuid()));
    retained.add(attributeModifier("generic.attack_speed",SPEED_NAME,profile.attackSpeed(),speedUuid()));
    tag.put(MODIFIERS_KEY,retained);
    tag.putString(PROFILE_KEY,formId(stack));
  }

  private NbtCompound attributeModifier(String attribute, String name, double amount, UUID uuid) {
    NbtCompound modifier=new NbtCompound();
    modifier.putString("AttributeName",attribute);
    modifier.putString("Name",name);
    modifier.putDouble("Amount",amount);
    modifier.putInt("Operation",0);
    modifier.put("UUID",NbtHelper.fromUuid(uuid));
    modifier.putString("Slot","mainhand");
    return modifier;
  }

  private UUID damageUuid() { return UUID.nameUUIDFromBytes((familyId+":damage").getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
  private UUID speedUuid() { return UUID.nameUUIDFromBytes((familyId+":speed").getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
}
