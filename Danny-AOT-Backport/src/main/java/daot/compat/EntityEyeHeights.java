package daot.compat;
import daot.DannysAot;
import net.minecraft.entity.EntityType;
public final class EntityEyeHeights {
 public static Float get(EntityType<?> type) {
  if(type == DannysAot.TITAN) return 14.0F;
  if(type == DannysAot.COLOSSAL_TITAN) return 58.0F;
  if(type == DannysAot.ATTACK_TITAN) return 14.0F;
  if(type == DannysAot.OGRE_SHIFTER_TITAN) return 9.0F;
  if(type == DannysAot.TRIPLE_T_TITAN) return 14.0F;
  if(type == DannysAot.TEST_SHIFTER_TITAN) return 5.0F;
  if(type == DannysAot.CART_SHIFTER_TITAN) return 3.5F;
  if(type == DannysAot.ARMORED_TITAN) return 14.0F;
  if(type == DannysAot.FEMALE_TITAN) return 13.0F;
  if(type == DannysAot.WARHAMMER_TITAN) return 14.0F;
  if(type == DannysAot.SMALL_TITAN) return 3.5F;
  if(type == DannysAot.SMALL_TITAN_2) return 5.0F;
  if(type == DannysAot.SAD_TITAN) return 5.0F;
  if(type == DannysAot.YELLOW_TITAN) return 4.2F;
  if(type == DannysAot.CRAWLER_TITAN) return 2.5F;
  if(type == DannysAot.FRITZ_TITAN) return 14.0F;
  if(type == DannysAot.TITAN_BEARD) return 12.0F;
  if(type == DannysAot.TITAN_TROPICAL) return 11.0F;
  if(type == DannysAot.ABNORMAL_TITAN) return 10.0F;
  if(type == DannysAot.CRAWLING_ABNORMAL_TITAN) return 5.0F;
  if(type == DannysAot.CONNIE_FATHER) return 2.5F;
  if(type == DannysAot.OGRE_TITAN) return 9.0F;
  if(type == DannysAot.BEAST_TITAN) return 16.0F;
  if(type == DannysAot.FOUNDING_TITAN) return 14.0F;
  return null;
 }
}
