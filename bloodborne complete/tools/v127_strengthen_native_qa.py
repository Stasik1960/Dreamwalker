"""One-time, idempotent strengthening of the opt-in client QA source only."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
def write_source(path, text):
    # Existing-file truncation can return EINVAL under Windows file monitors;
    # write an ordinary sibling and replace only this explicitly named source.
    sibling = path.with_name(path.name + '.v127-rewrite')
    assert not sibling.exists() and not path.is_symlink()
    sibling.write_text(text, encoding='utf-8')
    sibling.replace(path)

fixture = root / 'src/v127qa/java/dev/dreamwalker/bloodbornedw/v127qa/NativeV127Fixture.java'
old = 'outer:for(var block:CompositeArchitecture.blocks())if(block instanceof ImportedCompactBlock)for(int pose=0;pose<block.spec.variants.size()&&pose<16;pose++){var state=block.getDefaultState().with(CompositeRootBlock.VARIANT,pose).with(ImportedCompactBlock.COMPACT,true);if(ImportedCompactBlock.compact(state)){world.setBlockState(new BlockPos(44,64,0),state);compactId=block.spec.id.toString();compactVariant=pose;break outer;}}'
new = '''// Prefer a banked source pose so the native client proof also
                // covers art_bank selectors and their generated ALT wrapper.
                outer:for(int first:new int[]{16,0})for(var block:CompositeArchitecture.blocks())if(block instanceof ImportedCompactBlock)for(int pose=first;pose<block.spec.variants.size();pose++){
                    var state=block.getDefaultState().with(CompositeRootBlock.VARIANT,pose%16).with(ImportedCompactBlock.COMPACT,true);var bank=ImportedArtworkBlock.bankProperty(state);if(pose>=16&&bank==null)continue;if(bank!=null)state=state.with(bank,pose/16);
                    if(ImportedCompactBlock.compact(state)){world.setBlockState(new BlockPos(44,64,0),state);compactId=block.spec.id.toString();compactVariant=pose;break outer;}
                }'''
text = fixture.read_text(encoding='utf-8')
if old in text:
    assert text.count(old) == 1
    write_source(fixture, text.replace(old, new))
else:
    assert new in text

client = root / 'src/v127qa/java/dev/dreamwalker/bloodbornedw/v127qa/NativeV127ClientQa.java'
text = client.read_text(encoding='utf-8')
text = text.replace('if(frame.x==0||frame.x==6){int[] colors=client.getBlockRenderManager().getModel(state).getQuads(state,null,Random.create(0)).stream()',
                    'if(frame.x==0||frame.x==6||frame.x==44){int[] colors=allQuads(client,state).stream()')
anchor = '    private boolean placed(MinecraftClient client,Case current)'
helper = '''    private List<net.minecraft.client.render.model.BakedQuad> allQuads(MinecraftClient client,net.minecraft.block.BlockState state){
        var model=client.getBlockRenderManager().getModel(state);var quads=new ArrayList<>(model.getQuads(state,null,Random.create(0)));
        for(var face:Direction.values())quads.addAll(model.getQuads(state,face,Random.create(0)));return quads;
    }
'''
if helper not in text:
    assert text.count(anchor) == 1
    text = text.replace(anchor, helper + anchor)
old_fail = 'private void fail(Throwable failure){done=true;report.addProperty("failure",failure.toString());try{write("FAIL");}catch(Exception ignored){}failure.printStackTrace();}'
new_fail = 'private void fail(Throwable failure){done=true;report.addProperty("failure",failure.toString());try{write("FAIL");}catch(Exception ignored){}failure.printStackTrace();MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().scheduleStop());}'
if old_fail in text:
    assert text.count(old_fail) == 1
    text = text.replace(old_fail, new_fail)
else:
    assert new_fail in text
assert 'if(frame.x==0||frame.x==6||frame.x==44){int[] colors=allQuads(client,state).stream()' in text
write_source(client, text)
print('PASS_QA_SOURCE_STRENGTHENED_NATIVE_NOT_RUN')
