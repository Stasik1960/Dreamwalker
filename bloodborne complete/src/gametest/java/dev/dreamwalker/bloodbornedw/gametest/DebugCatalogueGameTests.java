package dev.dreamwalker.bloodbornedw.gametest;

import java.util.*;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import dev.dreamwalker.bloodbornerp.mob.RpMobEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.test.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Real presentation calls preserve typed stack/entity data and instance identity. */
public final class DebugCatalogueGameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=40,batchId="debug_catalogue")
    public void allUserTypesHaveUniqueFrozenFiveDigitNumbersAndAssociatedItemsShareThem(TestContext context){
        Set<String> numbers=new HashSet<>();int architecture=0,entities=0,items=0;
        for(var entry:DebugCatalogue.entries()){
            context.assertTrue(entry.temporaryId().matches("[0-9]{5}")&&numbers.add(entry.temporaryId()),"TEMP type numbers must be unique and exactly five digits");
            if(entry.kind().equals("architecture"))architecture++;
            if(entry.kind().startsWith("rp_")&&!entry.kind().equals("rp_item"))entities++;
        }
        for(var type:Registries.ENTITY_TYPE){Identifier id=Registries.ENTITY_TYPE.getId(type);if(!id.getNamespace().equals("bloodborne_rp"))continue;
            var entry=DebugCatalogue.entry(id);context.assertTrue(entry!=null,"RP type missing TEMP catalogue: "+id);
            Identifier placer=new Identifier(id.getNamespace(),id.getPath()+(entry.kind().equals("rp_mob")?"_spawn_egg":"_placer"));
            context.assertTrue(DebugCatalogue.entry(placer)==entry,"RP associated item must use same type number: "+placer);
        }
        for(var item:Registries.ITEM){Identifier id=Registries.ITEM.getId(item);if(id.getNamespace().equals("bloodborne_rp")){items++;context.assertTrue(DebugCatalogue.entry(id)!=null,"RP item missing catalogue: "+id);}}
        context.assertTrue(architecture==18&&entities==94&&items==98,"V10 append-only catalogue covers18 architecture types/94 preserved RP registrations/98 RP items; retired numbers stay reserved");
        context.assertTrue(DebugCatalogue.entry(new Identifier("bloodborne_dw","composite_cell"))==null,"Technical helper must use main affiliation, not receive a standalone user type number");context.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=40,batchId="debug_catalogue")
    public void namesAndTooltipsPreserveCustomNamesOpaqueStackNbtAndEntityPayload(TestContext context){
        for(String path:List.of("tree1_placer","cleric_beast_spawn_egg","saw_cleaver","blood_vial")){
            Identifier id=new Identifier("bloodborne_rp",path);ItemStack stack=Registries.ITEM.get(id).getDefaultStack();stack.getOrCreateNbt().putString("Opaque","leave links/UUID/state alone");
            NbtCompound before=stack.writeNbt(new NbtCompound());String number=DebugCatalogue.entry(id).temporaryId();
            context.assertTrue(stack.getName().getString().contains("["+number+"]"),"Default item name must expose TEMP type number: "+path);
            List<Text> tooltip=new ArrayList<>();stack.getItem().appendTooltip(stack,context.getWorld(),tooltip,net.minecraft.client.item.TooltipContext.Default.BASIC);
            context.assertTrue(tooltip.stream().anyMatch(text->text.getString().contains("TEMP ["+number+"]")),"Tooltip must mark the number temporary: "+path);
            context.assertTrue(stack.writeNbt(new NbtCompound()).equals(before),"Presentation changed typed stack NBT: "+path);
            stack.setCustomName(Text.literal("User authored name"));context.assertTrue(stack.getName().getString().equals("User authored name"),"Custom item name must remain exact");
        }
        for(String path:List.of("tree1","cleric_beast")){
            Identifier id=new Identifier("bloodborne_rp",path);var entity=Registries.ENTITY_TYPE.get(id).create(context.getWorld());context.assertTrue(entity!=null,"Actual RP factory unavailable");
            context.assertTrue(entity.getName().getString().contains("["+DebugCatalogue.entry(id).temporaryId()+"]"),"RP default name must expose type number");
            entity.setCustomName(Text.literal("CustomName intact"));NbtCompound before=entity.writeNbt(new NbtCompound());UUID uuid=entity.getUuid();
            context.assertTrue(entity.getName().getString().equals("CustomName intact"),"RP CustomName must remain exact");
            DebugCatalogue.prefix(id);context.assertTrue(entity.getUuid().equals(uuid)&&entity.writeNbt(new NbtCompound()).equals(before),"Presentation changed entity UUID/payload");
        }
        context.complete();
    }
}
