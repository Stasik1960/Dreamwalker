package dev.dreamwalker.bloodbornedw.restorationqa;
public final class RestorationQaBootstrap implements net.fabricmc.api.ModInitializer {
 public void onInitialize(){if(System.getProperty("dw.restore.apply.plan")!=null)new CityRestorationApply().onInitialize();else if(Boolean.getBoolean("dw.restore.qa.server"))new RestorationQa().onInitialize();}
}
