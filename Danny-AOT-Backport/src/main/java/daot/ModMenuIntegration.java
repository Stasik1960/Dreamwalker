package daot;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.option.SimpleOption.DoubleSliderCallbacks;
import net.minecraft.client.option.SimpleOption.ValidatingIntSliderCallbacks;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {
   public ConfigScreenFactory<?> getModConfigScreenFactory() {
      return parent -> {
         MinecraftClient mc = MinecraftClient.getInstance();
         boolean isOnMultiplayerServer = mc.getCurrentServerEntry() != null && !mc.isIntegratedServerRunning();
         return (Screen)(isOnMultiplayerServer
            ? new ModMenuIntegration.MultiplayerConfigScreen(parent)
            : new ModMenuIntegration.DannysAotOptionsScreen(parent, mc.options));
      };
   }

   @Environment(EnvType.CLIENT)
   public static class DannysAotOptionsScreen extends GameOptionsScreen {
      private net.minecraft.client.gui.widget.OptionListWidget body;
      @Override protected void init() {
         body = new net.minecraft.client.gui.widget.OptionListWidget(client,width,height,32,height-32,25);
         addSelectableChild(body);
         addOptions();
         addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"),button->close()).dimensions(width/2-100,height-27,200,20).build());
      }
      @Override public void render(net.minecraft.client.gui.DrawContext ctx,int mouseX,int mouseY,float delta) {
         renderBackground(ctx);
         body.render(ctx,mouseX,mouseY,delta);
         ctx.drawCenteredTextWithShadow(textRenderer,title,width/2,12,0xFFFFFF);
         super.render(ctx,mouseX,mouseY,delta);
      }

      public DannysAotOptionsScreen(Screen parent, GameOptions options) {
         super(parent, options, Text.literal("Danny's AoT Options"));
      }

      protected void addOptions() {
         if (this.body != null) {
            ODMConfig config = ODMConfig.get();
            this.body
               .addSingleOptionEntry(
                  createDoubleOption(
                     "dualHookEaseTime",
                     500.0,
                     5000.0,
                     100.0,
                     config.dualHookEaseTime,
                     val -> config.dualHookEaseTime = val.longValue(),
                     "Dual Hook Ease Time (ms)"
                  )
               );
            this.body
               .addOptionEntry(
                  createIntOption("gasTickInterval", 1, 40, config.gasTickInterval, val -> config.gasTickInterval = val, "Gas Tick Interval"),
                  createIntOption("gasConsumptionNormal", 0, 10, config.gasConsumptionNormal, val -> config.gasConsumptionNormal = val, "Gas (Normal)")
               );
            this.body
               .addOptionEntry(createIntOption("gasConsumptionBoost", 0, 20, config.gasConsumptionBoost, val -> config.gasConsumptionBoost = val, "Gas (Boost)"), null);
            this.body
               .addOptionEntry(
                  createDoubleOption("basePullSpeed", 0.01, 0.5, 0.01, config.basePullSpeed, val -> config.basePullSpeed = val, "Base Pull Speed"),
                  createDoubleOption(
                     "dualHookPullMultiplier", 1.0, 5.0, 0.1, config.dualHookPullMultiplier, val -> config.dualHookPullMultiplier = val, "Dual Hook Pull Mult"
                  )
               );
            this.body
               .addOptionEntry(
                  createDoubleOption(
                     "dualHookBoostPullMultiplier",
                     1.0,
                     3.0,
                     0.1,
                     config.dualHookBoostPullMultiplier,
                     val -> config.dualHookBoostPullMultiplier = val,
                     "Dual Boost Pull Mult"
                  ),
                  createDoubleOption(
                     "orbitPullMultiplier", 1.0, 3.0, 0.1, config.orbitPullMultiplier, val -> config.orbitPullMultiplier = val, "Orbit Pull Mult"
                  )
               );
            this.body
               .addOptionEntry(
                  createDoubleOption("baseOrbitSpeed", 0.01, 0.3, 0.01, config.baseOrbitSpeed, val -> config.baseOrbitSpeed = val, "Base Orbit Speed"),
                  createDoubleOption(
                     "dualHookOrbitMultiplier",
                     0.1,
                     1.0,
                     0.1,
                     config.dualHookOrbitMultiplier,
                     val -> config.dualHookOrbitMultiplier = val,
                     "Dual Hook Orbit Mult"
                  )
               );
            this.body.addOptionEntry(createDoubleOption("upwardLift", 0.0, 0.2, 0.01, config.upwardLift, val -> config.upwardLift = val, "Upward Lift"), null);
            this.body
               .addOptionEntry(
                  createDoubleOption(
                     "boostPullMultiplier", 0.5, 3.0, 0.1, config.boostPullMultiplier, val -> config.boostPullMultiplier = val, "Boost Pull Mult"
                  ),
                  createDoubleOption(
                     "boostOrbitMultiplier", 1.0, 5.0, 0.1, config.boostOrbitMultiplier, val -> config.boostOrbitMultiplier = val, "Boost Orbit Mult"
                  )
               );
            this.body
               .addOptionEntry(
                  createDoubleOption("boostRampRate", 0.01, 0.2, 0.01, config.boostRampRate, val -> config.boostRampRate = val, "Boost Ramp Rate"),
                  createDoubleOption("boostDecayRate", 0.005, 0.1, 0.005, config.boostDecayRate, val -> config.boostDecayRate = val, "Boost Decay Rate")
               );
            this.body
               .addOptionEntry(
                  createDoubleOption("maxHookDistance", 50.0, 500.0, 10.0, config.maxHookDistance, val -> config.maxHookDistance = val, "Max Hook Distance"),
                  createDoubleOption(
                     "momentumPreserveTime",
                     1000.0,
                     10000.0,
                     500.0,
                     config.momentumPreserveTime,
                     val -> config.momentumPreserveTime = val.longValue(),
                     "Momentum Time (ms)"
                  )
               );
            this.body
               .addOptionEntry(
                  createDoubleOption(
                     "flightSoundVelocityThreshold",
                     0.1,
                     1.0,
                     0.1,
                     config.flightSoundVelocityThreshold,
                     val -> config.flightSoundVelocityThreshold = val,
                     "Flight Sound Threshold"
                  ),
                  null
               );
         }
      }

      @Override
      public void removed() {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.isIntegratedServerRunning() && mc.getServer() != null) {
            mc.getServer().execute(() -> {
               ODMConfig clientConfig = ODMConfig.get();
               ModConfig serverConfig = ModConfig.get();
               serverConfig.dualHookEaseTime = clientConfig.dualHookEaseTime;
               serverConfig.gasTickInterval = clientConfig.gasTickInterval;
               serverConfig.gasConsumptionNormal = clientConfig.gasConsumptionNormal;
               serverConfig.gasConsumptionBoost = clientConfig.gasConsumptionBoost;
               serverConfig.basePullSpeed = clientConfig.basePullSpeed;
               serverConfig.dualHookPullMultiplier = clientConfig.dualHookPullMultiplier;
               serverConfig.dualHookBoostPullMultiplier = clientConfig.dualHookBoostPullMultiplier;
               serverConfig.orbitPullMultiplier = clientConfig.orbitPullMultiplier;
               serverConfig.baseOrbitSpeed = clientConfig.baseOrbitSpeed;
               serverConfig.dualHookOrbitMultiplier = clientConfig.dualHookOrbitMultiplier;
               serverConfig.upwardLift = clientConfig.upwardLift;
               serverConfig.boostPullMultiplier = clientConfig.boostPullMultiplier;
               serverConfig.boostOrbitMultiplier = clientConfig.boostOrbitMultiplier;
               serverConfig.boostRampRate = clientConfig.boostRampRate;
               serverConfig.boostDecayRate = clientConfig.boostDecayRate;
               serverConfig.maxHookDistance = clientConfig.maxHookDistance;
               serverConfig.momentumPreserveTime = clientConfig.momentumPreserveTime;
               serverConfig.flightSoundVelocityThreshold = clientConfig.flightSoundVelocityThreshold;
               ModConfig.save();
            });
         }

         super.removed();
      }

      private static SimpleOption<Integer> createIntOption(String key, int min, int max, int current, Consumer<Integer> setter, String label) {
         return new SimpleOption<>(
            "dannys-aot.option." + key,
            SimpleOption.emptyTooltip(),
            (text, value) -> Text.literal(label + ": " + value),
            new ValidatingIntSliderCallbacks(min, max),
            current,
            setter::accept
         );
      }

      private static SimpleOption<Double> createDoubleOption(
         String key, double min, double max, double step, double current, Consumer<Double> setter, String label
      ) {
         return new SimpleOption<>(
            "dannys-aot.option." + key,
            SimpleOption.emptyTooltip(),
            (text, value) -> Text.literal(label + ": " + String.format("%.3f", value)),
            DoubleSliderCallbacks.INSTANCE.withModifier(d -> min + d * (max - min), v -> (v - min) / (max - min)),
            current,
            setter::accept
         );
      }
   }

   @Environment(EnvType.CLIENT)
   public static class MultiplayerConfigScreen extends Screen {
      private final Screen parent;

      public MultiplayerConfigScreen(Screen parent) {
         super(Text.literal("Danny's AoT Configuration"));
         this.parent = parent;
      }

      @Override
      protected void init() {
         super.init();
         int centerX = this.width / 2;
         this.addDrawableChild(
            new MultilineTextWidget(
                  centerX - 150,
                  80,
                  Text.literal("Configuration is controlled by the server.\n\nAsk your server administrator\nto change ODM gear settings."),
                  this.textRenderer
               )
               .setMaxWidth(300)
               .setCentered(true)
         );
         this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> this.close()).dimensions(centerX - 100, this.height - 30, 200, 20).build());
      }

      @Override
      public void close() {
         this.client.setScreen(this.parent);
      }
   }
}
