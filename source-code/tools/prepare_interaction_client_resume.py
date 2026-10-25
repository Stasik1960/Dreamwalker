from pathlib import Path
P=Path(__file__).resolve().parents[1];path=P/'src/restorationqa/java/dev/dreamwalker/bloodbornedw/restorationqa/InteractionClientQa.java';s=path.read_text(encoding='utf-8')
assert 'GLFW.GLFW_KEY_F8' in s
s=s.replace('GLFW.GLFW_KEY_F8','GLFW.GLFW_KEY_F24')
old='    private void press(){KeyBinding.onKeyPressed(InputUtil.fromKeyCode(GLFW.GLFW_KEY_F24,0));}'
new='''    private void press() throws Exception {KeyBinding.onKeyPressed(InputUtil.fromKeyCode(GLFW.GLFW_KEY_F24,0));int pending=0;for(var f:KeyBinding.class.getDeclaredFields())if(f.getType()==int.class&&!java.lang.reflect.Modifier.isStatic(f.getModifiers())){f.setAccessible(true);pending+=f.getInt(NoCollideKeyClient.KEY);}check(pending>0,"native key dispatch reaches configured binding");}
    private void debugKey(MinecraftClient c) throws Exception {var j=new JsonObject();j.addProperty("stage",stage);j.addProperty("lease",lease);j.addProperty("screen",c.currentScreen==null?"none":c.currentScreen.getClass().getName());j.addProperty("held",net.minecraft.registry.Registries.ITEM.getId(c.player.getMainHandStack().getItem()).toString());var texts=new JsonArray();for(var f:c.inGameHud.getClass().getDeclaredFields())if(Text.class.isAssignableFrom(f.getType())){f.setAccessible(true);var text=(Text)f.get(c.inGameHud);if(text!=null)texts.add(text.getString());}j.add("hudTexts",texts);c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());j.addProperty("permission2",p.hasPermissionLevel(2));j.addProperty("serverHeld",net.minecraft.registry.Registries.ITEM.getId(p.getMainHandStack().getItem()).toString());try{Files.writeString(out.resolve("key-diagnostics.json"),new GsonBuilder().setPrettyPrinting().create().toJson(j));}catch(Exception error){throw new RuntimeException(error);}});}
'''
assert old in s;s=s.replace(old,new)
point='        if(stage==1&&ready){move(c,b.getX()-5.5,b.getY(),b.getZ()-5,0,0);stage=2;return;}'
resume='''        if(stage==1&&ready&&Boolean.getBoolean("dw.interaction.qa.resume")){move(c,b.getX()+6.5,b.getY(),b.getZ()-4,0,0);ready=false;c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());p.getInventory().setStack(p.getInventory().selectedSlot,new ItemStack(CompositeArchitecture.kindBlock("source_andesite")));p.playerScreenHandler.sendContentUpdates();ready=true;});stage=8;age=0;return;}
'''
assert point in s;s=s.replace(point,resume+point)
point='        if(stage==9){c.getServer().execute('
assert point in s;s=s.replace(point,'        if(stage==9){if(age%100==0)debugKey(c);c.getServer().execute(')
path.write_text(s,encoding='utf-8')
