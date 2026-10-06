package dev.dreamwalker.bloodbornerp.client.weapon;

import dev.dreamwalker.bloodbornerp.weapon.TrickWeaponItem;
import dev.dreamwalker.bloodbornerp.weapon.WeaponRegistry;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;
import software.bernie.geckolib.animatable.client.RenderProvider;

/** Client-only input and GeoItem renderer registration. */
@Environment(EnvType.CLIENT)
public final class WeaponClient {
  private static KeyBinding transformKey;
  private WeaponClient() {}

  public static void initialize() {
    TrickWeaponItem.clientRendererConsumer=WeaponClient::installRenderer;
    for(var weapon:java.util.List.of(WeaponRegistry.SAW_CLEAVER,WeaponRegistry.SAW_SPEAR,WeaponRegistry.BOOM_HAMMER)){
      net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry.register(weapon,new net.minecraft.util.Identifier("bloodborne_rp","extended"),
       (stack,world,entity,seed)->TrickWeaponItem.form(stack)==dev.dreamwalker.bloodbornerp.weapon.WeaponForm.EXTENDED?1f:0f);
    }
    transformKey=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.bloodborne_rp.transform_weapon",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_R,"category.bloodborne_rp"));
    ClientTickEvents.END_CLIENT_TICK.register(client -> {
      while(transformKey.wasPressed()) sendTransform(client.player==null ? null : handFor(client.player.getMainHandStack(),client.player.getOffHandStack()));
    });
  }

  private static void installRenderer(java.util.function.Consumer<Object> consumer) {
    consumer.accept(new RenderProvider() {
      private TrickWeaponRenderer renderer;
      @Override public net.minecraft.client.render.item.BuiltinModelItemRenderer getCustomRenderer() {
        if(renderer==null) renderer=new TrickWeaponRenderer();
        return renderer;
      }
    });
  }

  private static Hand handFor(net.minecraft.item.ItemStack main, net.minecraft.item.ItemStack off) {
    if(main.getItem() instanceof TrickWeaponItem) return Hand.MAIN_HAND;
    return off.getItem() instanceof TrickWeaponItem ? Hand.OFF_HAND : null;
  }

  private static void sendTransform(Hand hand) {
    if(hand==null || !ClientPlayNetworking.canSend(WeaponRegistry.TRANSFORM_PACKET)) return;
    PacketByteBuf payload=new PacketByteBuf(Unpooled.buffer(1));
    payload.writeByte(hand==Hand.MAIN_HAND ? 0 : 1);
    ClientPlayNetworking.send(WeaponRegistry.TRANSFORM_PACKET,payload);
  }
}
