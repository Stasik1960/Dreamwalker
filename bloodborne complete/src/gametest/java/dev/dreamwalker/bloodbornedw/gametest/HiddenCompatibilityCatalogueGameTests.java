package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.architecture.RemainingArchitecture;
import dev.dreamwalker.bloodbornedw.composite.CompositeArchitecture;
import dev.dreamwalker.bloodbornedw.composite.ImportedArtworkBlock;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornedw.debug.UnifiedCreativeCatalogue;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;

/** Hidden city compatibility keeps its registration without expanding Creative. */
public final class HiddenCompatibilityCatalogueGameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="final_hidden_compatibility_catalogue")
    public void conversionOnlyFamilyHasReservedIdAndArtBanksButNoOfferedStack(TestContext t){
        var id=new Identifier("bloodborne_dw","source_compatibility_fragment");
        t.assertTrue(Registries.BLOCK.containsId(id)&&Registries.ITEM.get(id) instanceof BlockItem,"Hidden compatibility remains a registered placing block/item");
        var block=CompositeArchitecture.kindBlock(id.getPath());
        t.assertTrue(block instanceof ImportedArtworkBlock&&block.spec.variants.size()>16,"Existing hidden source artworks retain their native art-bank states");
        var entry=DebugCatalogue.entry(id);
        t.assertTrue(entry!=null&&entry.temporaryId().equals("93234"),"Raw reserved compatibility ID93234 remains queryable");
        t.assertTrue(RemainingArchitecture.creativeVariants(new ItemStack(block.asItem())).isEmpty(),"Conversion-only raw item produces no Creative installation stack");
        t.assertTrue(UnifiedCreativeCatalogue.canonicalItems().stream().noneMatch(s->Registries.ITEM.getId(s.getItem()).equals(id)),"Unified Creative has no hidden family variants");
        t.complete();
    }
}
