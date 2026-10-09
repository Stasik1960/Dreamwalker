package dev.dreamwalker.bloodbornedw.debug;

import java.util.*;
import dev.dreamwalker.bloodbornerp.object.ObjectRegistry;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** One offered catalogue for the combined distribution; old aliases remain registered only. */
public final class UnifiedCreativeCatalogue {
    public static final Identifier ID=new Identifier("bloodborne_dw","catalogue");
    private static boolean initialized;
    private UnifiedCreativeCatalogue(){}
    public static void initialize(){
        if(initialized)return;initialized=true;
        Registry.register(Registries.ITEM_GROUP,ID,FabricItemGroup.builder().displayName(Text.literal("Bloodborne · строительство и предметы"))
            .icon(()->new ItemStack(Registries.ITEM.get(new Identifier("bloodborne_dw","composite_builder"))))
            .entries((context,entries)->canonicalItems().forEach(entries::add)).build());
    }
    private static int category(String kind){return switch(kind){case "architecture"->0;case "technical_tool"->1;case "rp_object"->2;case "rp_item"->3;case "rp_mob"->4;default->5;};}
    public static List<ItemStack> canonicalItems(){
        LinkedHashMap<Identifier,ItemStack> offered=new LinkedHashMap<>();
        DebugCatalogue.entries().stream().sorted(Comparator.comparingInt((DebugCatalogue.Entry e)->category(e.kind())).thenComparing(DebugCatalogue.Entry::temporaryId)).forEach(entry->{
            // A retired number still appears in the append-only table, but its
            // logical alias resolves to another canonical offered type.
            if(DebugCatalogue.entry(entry.registryId())!=entry||entry.registryId().equals(dev.dreamwalker.bloodbornedw.architecture.BuilderToolMigration.LEGACY_ID))return;
            Identifier item=switch(entry.kind()){
                case "rp_object"->new Identifier(entry.registryId().getNamespace(),entry.registryId().getPath()+"_placer");
                case "rp_mob"->new Identifier(entry.registryId().getNamespace(),entry.registryId().getPath()+"_spawn_egg");
                default->entry.registryId();
            };
            if(Registries.ITEM.containsId(item)&&Registries.ITEM.get(item)!=Items.AIR)offered.putIfAbsent(item,new ItemStack(Registries.ITEM.get(item)));
        });
        // Keep already existing RP weapons/items/eggs that have no TEMP row.
        // No helper, technical architecture block or retired RP placer enters.
        Registries.ITEM.getIds().stream().filter(id->id.getNamespace().equals("bloodborne_rp")).sorted(Comparator.comparing(Identifier::toString)).forEach(id->{
            if(id.getPath().endsWith("_placer")&&!ObjectRegistry.isCanonicalPlacementItem(id.getPath().substring(0,id.getPath().length()-7)))return;
            offered.putIfAbsent(id,new ItemStack(Registries.ITEM.get(id)));
        });
        return offered.values().stream().map(ItemStack::copy).toList();
    }
}
