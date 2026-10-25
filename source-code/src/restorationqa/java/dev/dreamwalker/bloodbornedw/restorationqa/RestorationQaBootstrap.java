package dev.dreamwalker.bloodbornedw.restorationqa;
public final class RestorationQaBootstrap implements net.fabricmc.api.ModInitializer {
 public void onInitialize(){if(Boolean.getBoolean("dw.npc.qa.server"))new NpcServerQa().onInitialize();else if(System.getProperty("dw.interaction.apply.plan")!=null)new InteractionWindowApply().onInitialize();else if(Boolean.getBoolean("dw.interaction.qa.server"))new InteractionQa().onInitialize();else if(System.getProperty("dw.restore.apply.plan")!=null)new CityRestorationApply().onInitialize();else if(Boolean.getBoolean("dw.restore.qa.server"))new RestorationQa().onInitialize();}
}
