package daot;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class DaotPreLaunch implements PreLaunchEntrypoint {
   public void onPreLaunch() {
      IntegrityGuard.checkAndRefuse();
   }
}
