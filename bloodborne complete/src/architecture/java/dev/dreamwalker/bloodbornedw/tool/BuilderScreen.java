package dev.dreamwalker.bloodbornedw.tool;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.architecture.BuildingTool;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** UUID-scoped client drafts are kept through tabs, resizing and unrelated server replies. */
public final class BuilderScreen extends Screen {
    private static final String[] TABS={"Объект","Связи","Телепорты","События","Диагностика","Управление"};
    private static final int ROW_HEIGHT=24;
    private static final Map<String,TargetDraft> drafts=new LinkedHashMap<>();
    private static final Map<String,RuleDraft> ruleDrafts=new LinkedHashMap<>();
    private final List<Row> rows=new ArrayList<>();
    private final List<String> rightLines=new ArrayList<>();
    private JsonObject view;
    private int tab,page,rowsPerPage,bodyTop,bodyBottom,central,columnWidth,right;
    private boolean details,commandOpen=true,closePrompt,closeAfterSave;
    private String localMessage="",confirmation="",note="",selectedLine="",lineRename="",lineRenameBase="",lineFormVersion="",lineFingerprint="";
    private boolean both=true;
    private long pendingId=-1,pendingSince;
    private int pendingSection=-1;
    private TargetDraft pendingTarget;
    private RuleDraft pendingRule;
    private boolean pendingLampName;
    private long diagnosticsReceivedAt=System.currentTimeMillis();
    private String diagnosticsSession="";
    private boolean expiryStatusRequested;
    private ButtonWidget saveButton,cancelButton,folderButton;
    private interface Row {void build(int x,int y,int width);}

    private static final class EventDraft {
        List<String> baseline=new ArrayList<>(),commands=new ArrayList<>();
        String entry="";
        int selected=-1;
        void load(JsonArray array){baseline=strings(array);commands=new ArrayList<>(baseline);entry="";selected=-1;}
        boolean dirty(){return !commands.equals(baseline)||!entry.isBlank()&&(selected<0||selected>=commands.size()||!entry.equals(commands.get(selected)));}
        void stage(){if(entry.isBlank())return;if(selected>=0&&selected<commands.size())commands.set(selected,entry);else if(commands.size()<32)commands.add(entry);entry="";selected=-1;}
    }
    private static final class TargetDraft {
        String key="",version="",formVersion="",offset="0",mount="VERTICAL",lampName="",lampBaseline="",lampFingerprint="";
        boolean manual,dogs;
        JsonObject baseline=new JsonObject();
        final EventDraft open=new EventDraft(),close=new EventDraft();
        void loadObject(JsonObject target){offset=string(target,"offset","0");mount=string(target,"mountValue","VERTICAL");manual=bool(target,"leversOnly");dogs=bool(target,"dogsVisible");baseline=target.deepCopy();}
        void loadEvents(JsonObject target){open.load(array(target,"afterOpen"));close.load(array(target,"afterClose"));}
        void updateVersions(JsonObject target,JsonObject view){key=string(target,"key","");version=string(target,"version","");if(!lampDirty())formVersion=string(target,"formVersion",string(view,"formVersion",""));}
        boolean objectDirty(){return !offset.equals(string(baseline,"offset","0"))||!mount.equals(string(baseline,"mountValue","VERTICAL"))||manual!=bool(baseline,"leversOnly")||dogs!=bool(baseline,"dogsVisible");}
        boolean eventsDirty(){return open.dirty()||close.dirty();}
        boolean lampDirty(){return !lampName.equals(lampBaseline);}
        boolean dirty(){return objectDirty()||eventsDirty()||lampDirty();}
    }
    private static final class RuleDraft {
        String id="",name="",condition="ANY",effect="TOGGLE",baseName="",baseCondition="ANY",baseEffect="TOGGLE",formVersion="",fingerprint="";
        boolean unsaved;
        void load(JsonObject rule,JsonObject view){id=string(rule,"id","");name=baseName=string(rule,"name","");condition=baseCondition=string(rule,"condition","ANY");effect=baseEffect=string(rule,"effect","TOGGLE");unsaved=bool(rule,"draft");formVersion=string(object(view,"target"),"formVersion",string(view,"formVersion",""));fingerprint=string(rule,"fingerprint","");}
        boolean dirty(){return unsaved||!name.equals(baseName)||!condition.equals(baseCondition)||!effect.equals(baseEffect);}
    }

    public BuilderScreen(JsonObject view){super(Text.literal("90009 · Настройка объектов"));this.view=view;selectedLine=string(view,"lampLine","");lineRename=lineRenameBase=selectedLine;lineFormVersion=formVersion();lineFingerprint=string(lampView(),"fingerprint","");both=!view.has("lampBoth")||bool(view,"lampBoth");syncDrafts();}
    public static void clearSessionDrafts(){drafts.clear();ruleDrafts.clear();}
    public JsonObject currentView(){return view.deepCopy();}
    private JsonObject target(){return object(view,"target");}
    private JsonObject rule(){return object(view,"rule");}
    private JsonObject lampView(){JsonObject current=object(view,"currentLamp");return current.size()>0?current:object(view,"lamps");}
    private String targetId(){return string(target(),"instance",string(target(),"key","NONE"));}
    private TargetDraft draft(){return drafts.get(targetId());}
    private RuleDraft ruleDraft(){return ruleDrafts.get(string(rule(),"id","NONE"));}
    private EventDraft event(){return commandOpen?draft().open:draft().close;}

    public void accept(JsonObject next){
        String previousTargetVersion=string(target(),"version","");
        String previousLampFingerprint=string(lampView(),"fingerprint","");
        view=next;
        diagnosticsReceivedAt=System.currentTimeMillis();String session=string(object(view,"diagnostics"),"sessionId","");if(!session.equals(diagnosticsSession)||decimal(object(view,"diagnostics"),"remainingSeconds",0)>0)expiryStatusRequested=false;diagnosticsSession=session;
        boolean answered=pendingId>=0&&number(view,"ackRequestId",-1)==pendingId;
        if(!answered&&pendingId<0&&!string(view,"message","").isBlank())localMessage="";
        if(answered){
            if(bool(view,"success")){
                if(pendingTarget!=null){
                    if(pendingSection==0)pendingTarget.loadObject(target());
                    if(pendingSection==3)pendingTarget.loadEvents(target());
                    if(pendingSection==2&&pendingLampName)pendingTarget.lampBaseline=pendingTarget.lampName;
                    pendingTarget.updateVersions(target(),view);
                }
                if(pendingSection==2&&!pendingLampName){lineRenameBase=lineRename;selectedLine=lineRename;lineFormVersion=formVersion();lineFingerprint=string(lampView(),"fingerprint","");}
                if(pendingRule!=null){if(pendingSection==6)ruleDrafts.remove(pendingRule.id);else pendingRule.load(rule(),view);}
                // Advance a dirty draft only with proof its own data was not changed remotely.
                TargetDraft current=draft();if(current!=null&&(pendingTarget==current||current.version.equals(previousTargetVersion)))current.updateVersions(target(),view);
                RuleDraft currentRule=ruleDraft();if(currentRule!=null&&!currentRule.fingerprint.isBlank()&&currentRule.id.equals(string(rule(),"id",""))&&currentRule.fingerprint.equals(string(rule(),"fingerprint","")))currentRule.formVersion=formVersion();
                String fingerprint=string(lampView(),"fingerprint","");
                if(current!=null&&!current.lampFingerprint.isBlank()&&(current.lampFingerprint.equals(fingerprint)||pendingSection==2&&current.lampFingerprint.equals(previousLampFingerprint))){current.formVersion=formVersion();current.lampFingerprint=fingerprint;}
                if(!lineFingerprint.isBlank()&&(lineFingerprint.equals(fingerprint)||pendingSection==2&&lineFingerprint.equals(previousLampFingerprint))){lineFormVersion=formVersion();lineFingerprint=fingerprint;}
                localMessage="Сохранено сервером.";
            }else{localMessage=string(view,"error",string(view,"message","Сохранение отклонено. Черновик сохранён."));closeAfterSave=false;}
            pendingId=-1;pendingTarget=null;pendingRule=null;pendingSection=-1;
        }
        syncDrafts();
        clearAndInit();
        if(answered&&bool(view,"success")&&closeAfterSave)continueCloseSave();
    }
    private void syncDrafts(){
        JsonObject t=target();if(t.size()>0){TargetDraft d=drafts.get(targetId());String name=string(object(lampView(),"node"),"name","");
            if(d==null){if(makeTargetRoom()){d=new TargetDraft();d.loadObject(t);d.loadEvents(t);d.lampName=d.lampBaseline=name;d.lampFingerprint=string(lampView(),"fingerprint","");d.updateVersions(t,view);drafts.put(targetId(),d);}else localMessage="Предел 64 черновиков. Сохраните или отмените прежние формы перед редактированием нового объекта.";}
            else{boolean dirty=d.dirty();if(!d.objectDirty())d.loadObject(t);if(!d.eventsDirty())d.loadEvents(t);if(!d.lampDirty()){d.lampName=d.lampBaseline=name;d.lampFingerprint=string(lampView(),"fingerprint","");d.formVersion=formVersion();}if(!dirty)d.updateVersions(t,view);}
        }
        JsonObject r=rule();if(r.size()>0){String id=string(r,"id","NONE");RuleDraft d=ruleDrafts.get(id);if(d==null){if(makeRuleRoom()){d=new RuleDraft();d.load(r,view);ruleDrafts.put(id,d);}else localMessage="Предел 64 черновиков правил. Сохраните или отмените прежние формы.";}else if(!d.dirty())d.load(r,view);else if(bool(r,"draft")){d.unsaved=true;d.formVersion=formVersion();}}
    }
    private boolean makeTargetRoom(){if(drafts.size()<64)return true;for(var it=drafts.entrySet().iterator();it.hasNext();)if(!it.next().getValue().dirty()){it.remove();return true;}return false;}
    private boolean makeRuleRoom(){if(ruleDrafts.size()<64)return true;for(var it=ruleDrafts.entrySet().iterator();it.hasNext();)if(!it.next().getValue().dirty()){it.remove();return true;}return false;}
    private String formVersion(){return string(target(),"formVersion",string(view,"formVersion",""));}
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){if(pendingId>=0){localMessage="Дождитесь ответа сервера перед закрытием.";return;}if(anyDirty()){closePrompt=true;clearAndInit();}else super.close();}
    @Override public void tick(){super.tick();if(saveButton!=null)saveButton.active=pendingId<0&&tabDirty(tab);if(cancelButton!=null)cancelButton.active=pendingId<0&&tabDirty(tab);if(folderButton!=null)folderButton.active=pendingId<0&&dev.dreamwalker.bloodbornedw.diagnostics.DwClientDiagnostics.lastExportPath()!=null;if(pendingId>=0&&System.currentTimeMillis()-pendingSince>15000){localMessage="Ответ сервера задерживается. Черновик сохранён; повторите после восстановления связи.";pendingId=-1;closeAfterSave=false;clearAndInit();}if(tab==4&&pendingId<0&&bool(object(view,"diagnostics"),"enabled")&&diagnosticsRemaining()==0&&!expiryStatusRequested){expiryStatusRequested=true;diagnostics("status",false);}}
    private long diagnosticsRemaining(){return Math.max(0,(long)Math.ceil(decimal(object(view,"diagnostics"),"remainingSeconds",0)-(System.currentTimeMillis()-diagnosticsReceivedAt)/1000.0));}

    @Override protected void init(){
        rows.clear();rightLines.clear();saveButton=null;cancelButton=null;folderButton=null;
        int tabRows=width>=540?1:2,tabCountPerRow=tabRows==1?6:3;
        bodyTop=58+tabRows*24;bodyBottom=height-81;
        central=12;right=width>=720?(int)(width*.70):width-12;columnWidth=Math.max(100,right-central-(width>=720?14:0));
        rowsPerPage=Math.max(1,(bodyBottom-bodyTop)/ROW_HEIGHT);
        for(int i=0;i<TABS.length;i++){int n=i,col=i%tabCountPerRow,y=54+(i/tabCountPerRow)*24,w=(width-24)/tabCountPerRow;ButtonWidget b=button((tab==i?"● ":"")+TABS[i],12+col*w,y,w-3,()->{tab=n;page=0;confirmation="";localMessage="";clearAndInit();});b.active=tab!=i&&pendingId<0;}
        if(closePrompt){closePromptRows();}else switch(tab){case 0->objectTab();case 1->rulesTab();case 2->lampsTab();case 3->eventsTab();case 4->diagnosticsTab();case 5->controlsTab();default->{}}
        page=Math.max(0,Math.min(page,Math.max(0,(rows.size()-1)/rowsPerPage)));
        int start=page*rowsPerPage,end=Math.min(rows.size(),start+rowsPerPage),y=bodyTop;
        for(Row row:rows.subList(start,end)){row.build(central,y,columnWidth);y+=ROW_HEIGHT;}
        button("↑",central,height-76,28,()->scroll(-1)).active=page>0;
        button("↓",central+columnWidth-28,height-76,28,()->scroll(1)).active=end<rows.size();
        int w=(width-28)/3;
        if(!closePrompt){saveButton=button("Сохранить",12,height-38,w-3,()->saveTab(tab,false));cancelButton=button("Отмена формы",14+w,height-38,w-3,()->discardTab(tab));button("Закрыть",16+2*w,height-38,w-3,this::close);saveButton.active=pendingId<0&&tabDirty(tab);cancelButton.active=pendingId<0&&tabDirty(tab);}
        else{button("Сохранить",12,height-38,w-3,()->{closeAfterSave=true;continueCloseSave();});button("Не сохранять",14+w,height-38,w-3,()->{discardAll();super.close();});button("Остаться",16+2*w,height-38,w-3,()->{closePrompt=false;clearAndInit();});}
        if(width>=720)rightInfo();
    }
    private void closePromptRows(){line("Есть несохранённые изменения.");line("Сохранить: дождаться принятия форм сервером.");line("Не сохранять: отбросить только черновики.");long other=drafts.entrySet().stream().filter(e->!e.getKey().equals(targetId())&&e.getValue().dirty()).count();if(other>0)line("Черновики других объектов: "+other+". Вернитесь к ним перед сохранением.");}
    private void scroll(int delta){int next=Math.max(0,Math.min(Math.max(0,(rows.size()-1)/rowsPerPage),page+delta));if(next!=page){page=next;clearAndInit();}}
    private ButtonWidget button(String label,int x,int y,int w,Runnable action){ButtonWidget button=addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(label,Math.max(15,w-12))),b->action.run()).dimensions(x,y,w,20).tooltip(Tooltip.of(Text.literal(label))).build());button.active=pendingId<0;return button;}
    private void line(String text){rows.add((x,y,w)->addDrawable((c,mx,my,d)->c.drawTextWithShadow(textRenderer,Text.literal(textRenderer.trimToWidth(text,w-6)),x+3,y+6,0xD6C8AF)));}
    private void liveLine(Supplier<String> text){rows.add((x,y,w)->addDrawable((c,mx,my,d)->c.drawTextWithShadow(textRenderer,Text.literal(textRenderer.trimToWidth(text.get(),w-6)),x+3,y+6,0xD6C8AF)));}
    private void row(String text,Runnable action){rows.add((x,y,w)->button(text,x,y,w,action));}
    private void disabled(String label,String reason){rows.add((x,y,w)->{ButtonWidget b=button(label,x,y,w,()->{});b.active=false;b.setTooltip(Tooltip.of(Text.literal(reason)));});}
    private void pair(String a,Runnable aa,String b,Runnable bb){rows.add((x,y,w)->{button(a,x,y,w/2-2,aa);button(b,x+w/2+2,y,w/2-2,bb);});}
    private void field(String label,String value,int maximum,Consumer<String> changed){line(label);rows.add((x,y,w)->{TextFieldWidget f=new TextFieldWidget(textRenderer,x,y,w,20,Text.literal(label));f.setMaxLength(maximum);f.setText(value);f.setChangedListener(changed);f.setEditable(pendingId<0);addDrawableChild(f);});}
    private void mode(BuildingTool.Action action,boolean supported){if(!supported)return;String active=string(view,"action","");boolean continueInWorld=action==BuildingTool.Action.RULE_SOURCE||action==BuildingTool.Action.RULE_TARGET;if(active.equals(action.name())&&!continueInWorld)disabled("● "+action.label,"Текущее действие: один ЛКМ в мире.");else row((continueInWorld?"В мир: ":"")+action.label,()->{if(continueInWorld&&!lineRename.equals(lineRenameBase)){localMessage="Сохраните или отмените имя линии перед выбором участников в мире.";return;}send("mode",q->{q.addProperty("action",action.name());if(action==BuildingTool.Action.LAMP_TARGET||(action==BuildingTool.Action.LINK||action==BuildingTool.Action.UNLINK)&&(lampReference(target())||lampReference(object(view,"source")))){q.addProperty("line",selectedLine);q.addProperty("both",both);}});if(continueInWorld&&pendingId<0)super.close();});}
    private void send(String op,Consumer<JsonObject> values){if(pendingId>=0){localMessage="Ожидается ответ на сохранение.";return;}JsonObject q=BuilderClient.request(op);values.accept(q);BuilderClient.send(q);}
    private void immediateObject(String field,JsonElement value){send("object",q->{q.addProperty("field",field);q.add("value",value);});}

    private void objectTab(){
        JsonObject t=target();TargetDraft d=draft();boolean found=t.size()>0,edit=bool(view,"editable")&&found;
        mode(BuildingTool.Action.SELECT,true);
        for(JsonElement e:array(view,"candidates")){JsonObject candidate=e.getAsJsonObject();row("Выбрать: "+label(candidate),()->send("select",q->q.addProperty("key",string(candidate,"key",""))));}
        if(!found){line("ЛКМ в мире закрепляет экземпляр; средний клик меняет выбор.");return;}
        if(!edit){disabled("Редактирование объекта","Недостаточно прав или объект недоступен.");return;}
        if(d==null){line("Редактирование остановлено: сохраните или отмените старые черновики.");return;}
        line("Сейчас: поворот "+string(t,"yaw",string(t,"orientation","—"))+" · Y "+string(t,"offset","0"));
        mode(BuildingTool.Action.ROTATE,true);line("Поворот: шаг "+string(t,"rotationStep","—")+"°; Shift меняет направление.");mode(BuildingTool.Action.UP,true);mode(BuildingTool.Action.DOWN,true);
        line("Шаг высоты: "+string(view,"step","0.125")+" блока (Alt + колесо)");
        rows.add((x,y,w)->{double[] steps={.0625,.125,.25,1};String[] names={"1/16","1/8","1/4","1"};for(int i=0;i<steps.length;i++){double step=steps[i];ButtonWidget b=button(names[i],x+i*w/4,y,w/4-2,()->send("step",q->q.addProperty("value",step)));b.active=pendingId<0&&Math.abs(decimal(view,"step",.125)-step)>.00001;}});
        field("Точное смещение по мировой Y, блоки",d.offset,32,v->d.offset=v);
        row("Сбросить высоту до 0",()->{d.offset="0";clearAndInit();});
        mode(BuildingTool.Action.PROFILE,bool(t,"profile"));
        // Decorative panels are an architecture capability, not any RP open state.
        mode(BuildingTool.Action.POSE,bool(t,"pose")&&!string(t,"kind","").equals("RP-объект"));
        if(bool(t,"mount")){line("Текущая установка: "+mountName(string(t,"mountValue","VERTICAL")));row("Установка: "+mountName(d.mount),()->{List<String> choices=List.of("VERTICAL","FLOOR","CEILING");d.mount=choices.get((choices.indexOf(d.mount)+1)%3);clearAndInit();});mode(BuildingTool.Action.MOUNT,true);}
        if(bool(t,"dogs")){line("Собака: "+(d.dogs?"включена":"скрыта"));row(d.dogs?"Скрыть собаку":"Показать собаку",()->{d.dogs=!d.dogs;clearAndInit();});mode(BuildingTool.Action.DOGS,true);}
        if(bool(t,"openable")||bool(t,"pulse")){line(bool(t,"pending")?"Состояние: ожидает безопасного закрытия":bool(t,"pulse")?"Однократная анимация":bool(t,"open")?"Состояние: открыто":"Состояние: закрыто");row("Управление: "+(d.manual?"Только рычагами":"Рукой и рычагами"),()->{d.manual=!d.manual;clearAndInit();});}
        line("Поля формы применяются кнопкой «Сохранить» внизу.");row("Отменить рабочий выбор",()->send("cancel_selection",q->{}));
    }
    private void rulesTab(){
        JsonObject r=rule();RuleDraft d=ruleDraft();line("Связи выбранного экземпляра");line("Быстро: «Связать» → рычаг → цели, без создания правила.");mode(BuildingTool.Action.LINK,bool(view,"editable"));mode(BuildingTool.Action.UNLINK,bool(view,"editable"));
        int index=(int)number(view,"rulePage",0)*64+1;for(JsonElement e:array(view,"rules")){JsonObject item=e.getAsJsonObject();final int local=index++;row("№"+local+" · "+string(item,"name","")+" · "+condition(string(item,"condition","ANY")),()->{if(bool(rule(),"draft")){localMessage="Сначала сохраните или отмените состав текущего черновика.";return;}send("rule_select",q->q.addProperty("id",string(item,"id","")));});}
        pagination(view,"rulePage","rulePages","rule_page","Правила");
        if(bool(view,"operator"))row(bool(view,"globalRules")?"Показать связи выбранного объекта":"Все правила администратора",()->send("rule_scope",q->q.addProperty("global",!bool(view,"globalRules"))));
        if(r.size()==0){row("Создать сложное правило (черновик)",()->send("rule_create",q->q.addProperty("name","Новое правило")));line("Пустой черновик не попадает в сохранённые правила.");return;}
        if(d==null){line("Редактирование остановлено: сохраните или отмените старые черновики.");return;}
        line((bool(r,"draft")?"Черновик: ":"Правило: ")+string(r,"name",""));line(bool(r,"incomplete")?"Неполный состав: проверьте участников.":"Логические признаки: "+string(r,"active","0")+" из "+string(r,"total","0"));
        field("Имя группового правила",d.name,80,v->d.name=v);
        row("Условие: "+condition(d.condition),()->{d.condition=d.condition.equals("ALL")?"ANY":"ALL";clearAndInit();});
        boolean pulseTargets=array(r,"targets").size()>0&&allPulse(array(r,"targets"));
        row("Действие: "+effect(d.effect),()->{List<String> choices=pulseTargets?List.of("PULSE"):List.of("TOGGLE","OPEN","CLOSE");d.effect=choices.get((choices.indexOf(d.effect)+1)%choices.size());clearAndInit();});
        mode(BuildingTool.Action.RULE_SOURCE,true);mode(BuildingTool.Action.RULE_TARGET,true);line("Закройте меню и выбирайте участников ЛКМ в мире.");
        for(boolean source:new boolean[]{true,false}){line(source?"Источники":"Цели (удаление затрагивает все источники группы)");for(JsonElement e:array(r,source?"sources":"targets")){JsonObject item=e.getAsJsonObject();String key=string(item,"key","");line(string(item,"name","Объект")+" ["+string(item,"type","?????")+"] · "+string(item,"position","")+(source?(bool(item,"active")?" · активен":" · выключен"):bool(item,"pending")?" · ожидает закрытия":""));if(!source)line(string(item,"supportedAction","")+(number(item,"conflictingRules",0)>1?" · несколько правил; порядок исполнения сохранён":""));if(!string(item,"availability","LOADED").equals("LOADED"))line("Участник недоступен / чанк выгружен; связь сохраняется.");String token="member:"+source+":"+key;row(confirmation.equals(token)?"Подтвердить: удалить участника группы":"Убрать "+(source?"источник":"цель")+"…",()->{if(confirmation.equals(token)){send("rule_remove_member",q->{q.addProperty("key",key);q.addProperty("source",source);q.addProperty("confirmed",true);});confirmation="";}else{confirmation=token;clearAndInit();}});}}
        line("«Все»: сброс общих признаков затронет групп "+string(r,"affectedResetTotal","0"));for(JsonElement e:array(r,"affectedResetNames"))line("→ "+e.getAsString());
        confirmed("Сбросить логические признаки…","Подтвердить сброс признаков","reset",()->send("rule_reset",q->q.addProperty("confirmed",true)));
        confirmed("Удалить правило и его связи…","Подтвердить удаление всего правила","delete-rule",()->deleteRule(d));
        row("Завершить рабочий выбор",()->send("cancel_selection",q->{}));
    }
    private void lampsTab(){
        JsonObject lamps=lampView(),node=object(lamps,"node");TargetDraft d=draft();boolean currentLamp=bool(target(),"lamp");
        line("Выбранный фонарь: "+string(node,"name","не выбран"));JsonObject source=object(view,"source");line("Источник связи: "+(lampReference(source)?string(object(object(view,"lamps"),"node"),"name",label(source)):"не закреплён"));line("Быстро: «Связать» → A → B/C. Связь по умолчанию двусторонняя.");mode(BuildingTool.Action.LINK,bool(view,"operator"));mode(BuildingTool.Action.LAMP_SOURCE,bool(view,"operator"));
        if(currentLamp&&d!=null)field("Имя выбранного фонаря",d.lampName,64,v->d.lampName=v);
        line("Выбранная линия: "+(selectedLine.isBlank()?"автоматическая":selectedLine));
        for(JsonElement e:array(lamps,"lines")){JsonObject l=e.getAsJsonObject();String name=string(l,"name","");row((name.equals(selectedLine)?"● ":"")+name+" · связей "+string(l,"connectionCount","0"),()->{if(!lineRename.equals(lineRenameBase)){localMessage="Сохраните или отмените введённое имя перед сменой линии.";return;}selectedLine=name;lineRename=lineRenameBase=name;lineFormVersion=formVersion();lineFingerprint=string(lampView(),"fingerprint","");send("lamp_parameters",q->{q.addProperty("line",selectedLine);q.addProperty("both",both);});clearAndInit();});}
        field(selectedLine.isBlank()?"Имя новой линии":"Новое имя выбранной линии",lineRename,64,v->{if(lineRename.equals(lineRenameBase)){lineFormVersion=formVersion();lineFingerprint=string(lampView(),"fingerprint","");}lineRename=v;});
        row("Создать новую линию из поля имени",this::createLine);
        row("Направление новых связей: "+(both?"В обе стороны":"Только к назначению"),()->{both=!both;send("lamp_parameters",q->{q.addProperty("line",selectedLine);q.addProperty("both",both);});clearAndInit();});mode(BuildingTool.Action.LAMP_TARGET,bool(view,"operator")&&node.size()>0);
        for(JsonElement e:array(lamps,"connections")){JsonObject c=e.getAsJsonObject(),a=object(c,"source"),b=object(c,"destination");String current=string(node,"entityUuid","");JsonObject dest=current.equals(string(a,"entityUuid",""))?b:a;String destination=string(dest,"entityUuid","");String route=string(c,"lineName","");line(route+": "+string(a,"name","")+(bool(c,"aToB")&&bool(c,"bToA")?" ↔ ":bool(c,"aToB")?" → ":" ← ")+string(b,"name","")+(bool(c,"reverseOtherLines")?" · другой обратный маршрут":""));pair("↔ Обе стороны",()->lampConnection("SET_DIRECTION",route,current,destination,true),"→ Только туда",()->lampConnection("SET_DIRECTION",route,current,destination,false));confirmed("Убрать пару с «"+string(dest,"name","")+"»…","Подтвердить удаление пары","pair:"+route+":"+destination,()->lampConnection("UNLINK",route,current,destination,true));}
        pagination(lamps,"page","pageCount","lamp_page","Маршруты");
        if(!selectedLine.isBlank())confirmed("Удалить всю линию «"+selectedLine+"»…","Подтвердить удаление всей линии","delete-line",()->send("lamp",q->{q.addProperty("action","DELETE_LINE");q.addProperty("line",selectedLine);q.addProperty("confirmed",true);}));
        row("Завершить выбор источника",()->send("cancel_selection",q->{}));
    }
    private void lampConnection(String action,String line,String source,String dest,boolean direction){send("lamp",q->{q.addProperty("action",action);q.addProperty("line",line);q.addProperty("source",source);q.addProperty("destination",dest);q.addProperty("both",direction);q.addProperty("confirmed",true);});}
    private void createLine(){if(pendingId>=0)return;if(lineRename.isBlank()){localMessage="Введите имя новой линии.";return;}JsonObject q=BuilderClient.request("lamp");q.addProperty("action","CREATE_LINE");q.addProperty("line",lineRename);q.addProperty("name",lineRename);pendingId=BuilderClient.reserveRequestId();q.addProperty("requestId",pendingId);pendingSection=2;pendingSince=System.currentTimeMillis();pendingLampName=false;pendingTarget=null;pendingRule=null;localMessage="Создание линии: ожидается подтверждение сервера…";if(BuilderClient.send(q)<0)sendFailed();clearAndInit();}
    private void eventsTab(){
        if(!bool(view,"admin")){disabled("Редактор команд","Нужны права администратора уровня 4; Creative их не заменяет.");return;}if(!bool(target(),"openable")){line("Выберите объект с устойчивыми состояниями открытия/закрытия.");return;}
        TargetDraft d=draft();if(d==null){line("Редактирование остановлено: сохраните или отмените старые черновики.");return;}EventDraft e=event();pair((commandOpen?"● ":"")+"После открытия",()->{commandOpen=true;clearAndInit();},(!commandOpen?"● ":"")+"После закрытия",()->{commandOpen=false;clearAndInit();});line("Открытие: "+d.open.commands.size()+" · закрытие: "+d.close.commands.size()+" команд");
        for(int i=0;i<e.commands.size();i++){int n=i;row((e.selected==i?"● ":"")+(i+1)+". "+e.commands.get(i),()->{e.selected=n;e.entry=e.commands.get(n);clearAndInit();});}
        field(e.selected<0?"Новая команда сервера":"Редактирование команды №"+(e.selected+1),e.entry,2048,v->e.entry=v);
        pair(e.selected<0?"Добавить команду":"Изменить выбранную",()->{if(e.selected<0&&e.commands.size()>=32){localMessage="Максимум 32 команды в одном списке.";return;}e.stage();clearAndInit();},"Удалить выбранную",()->{if(e.selected>=0&&e.selected<e.commands.size())e.commands.remove(e.selected);e.selected=-1;e.entry="";clearAndInit();});
        pair("Переместить выше",()->moveCommand(-1),"Переместить ниже",()->moveCommand(1));
        line("«Сохранить» сохраняет оба списка конкретного UUID.");line("Подстановки: {id}, {uuid}, {x}, {y}, {z}, {dimension}, {player}");line("Без инициатора {player} не подменяется другим игроком.");line("UUID архитектурного блока не является UUID сущности для @e.");
    }
    private void moveCommand(int amount){EventDraft e=event();int next=e.selected+amount;if(e.selected>=0&&next>=0&&next<e.commands.size()){Collections.swap(e.commands,e.selected,next);e.selected=next;clearAndInit();}}
    private void diagnosticsTab(){
        line("Показать в мире: выбор, коллизию, опору и связи.");row(BuilderClient.showGeometry()?"Скрыть подробную геометрию":"Показать подробную геометрию",()->{BuilderClient.showGeometry(!BuilderClient.showGeometry());clearAndInit();});
        line("Запись отчёта выключена по умолчанию.");if(!bool(view,"operator")){disabled("Запись и экспорт","Нужны права оператора уровня 2.");return;}
        JsonObject d=object(view,"diagnostics");liveLine(()->bool(object(view,"diagnostics"),"enabled")?(diagnosticsRemaining()>0?"Идёт запись · осталось "+diagnosticsRemaining()+" с":"Время истекло · ожидается завершение на сервере"):"Запись выключена · "+stopReason(string(object(view,"diagnostics"),"stopReason","")));line("Сеанс: "+string(d,"sessionId","NONE"));line("Область: "+filterName(object(d,"filter")));
        field("Пометка к снимку / записи",note,512,v->note=v);row("Снимок выбранного объекта",()->diagnostics("snapshot",true));pair("Запись 60 с: весь мод",()->diagnostics("start",false),"60 с: выбранный объект",()->diagnostics("start",true));pair("Остановить запись",()->diagnostics("stop",false),"Добавить пометку",()->diagnostics("mark",false));pair("Обновить статус",()->diagnostics("status",false),"Экспорт отчёта ZIP",()->diagnostics("export",false));
        line("Путь готового отчёта сервер сообщает в личный чат.");line("Клиентские кадры/FPS · серверный тик · CPU участков · память/сборка мусора");line("GPU-время: не измерено. FPS не показывает нагрузку конкретного блока.");if(details)for(var entry:d.entrySet())line(entry.getKey()+": "+entry.getValue());
        liveLine(()->{java.nio.file.Path export=dev.dreamwalker.bloodbornedw.diagnostics.DwClientDiagnostics.lastExportPath();return export==null?"Клиентский отчёт пока не экспортирован.":"Клиентский отчёт: "+export.getFileName();});rows.add((x,y,w)->{folderButton=button("Открыть папку клиентских отчётов…",x,y,w,dev.dreamwalker.bloodbornedw.diagnostics.DwClientDiagnostics::confirmOpenDiagnosticsFolder);folderButton.active=pendingId<0&&dev.dreamwalker.bloodbornedw.diagnostics.DwClientDiagnostics.lastExportPath()!=null;});
    }
    private void diagnostics(String action,boolean selected){send("diagnostics",q->{q.addProperty("action",action);q.addProperty("note",note);q.addProperty("selected",selected);});}
    private void controlsTab(){for(String hint:BuilderClient.controlHints())line(hint);List<String> conflicts=BuilderClient.conflicts();line(conflicts.isEmpty()?"Конфликтов клавиш инструмента не обнаружено.":"Конфликты клавиш:");for(String conflict:conflicts)line(conflict);row("Изменить клавиши управления",()->{if(client!=null)client.setScreen(new KeybindsScreen(this,client.options));});row(details?"Скрыть технические подробности":"Показать технические подробности",()->{details=!details;clearAndInit();});if(details){line("UUID: "+string(target(),"instance","—"));line("Registry: "+string(target(),"registry","—"));line("Измерение: "+string(target(),"dimension","—"));}line("Инструмент в любой руке защищает объект от обычного удара/разрушения.");line("Для обычного использования уберите его из обеих рук.");line("Отмена поддерживаемых изменений: последние 20 операций сеанса.");line("Команды, телепорт и игровое открытие не отменяются инструментом.");}
    private void confirmed(String label,String confirmLabel,String key,Runnable action){row(confirmation.equals(key)?confirmLabel:label,()->{if(confirmation.equals(key)){confirmation="";action.run();}else{confirmation=key;clearAndInit();}});}
    private void pagination(JsonObject data,String pageKey,String countKey,String op,String label){int current=(int)number(data,pageKey,0),pages=(int)number(data,countKey,1);if(pages>1){line(label+": страница "+(current+1)+" / "+pages);pair("Предыдущая",()->send(op,q->q.addProperty("page",Math.max(0,current-1))),"Следующая",()->send(op,q->q.addProperty("page",Math.min(pages-1,current+1))));}}

    private boolean tabDirty(int section){TargetDraft d=draft();RuleDraft r=ruleDraft();return switch(section){case 0->d!=null&&d.objectDirty();case 1->r!=null&&r.dirty();case 2->d!=null&&d.lampDirty()||!lineRename.equals(lineRenameBase);case 3->d!=null&&d.eventsDirty();default->false;};}
    private boolean anyDirty(){return drafts.values().stream().anyMatch(TargetDraft::dirty)||ruleDrafts.values().stream().anyMatch(RuleDraft::dirty)||!lineRename.equals(lineRenameBase);}
    private void discardTab(int section){TargetDraft d=draft();RuleDraft r=ruleDraft();switch(section){case 0->{if(d!=null)d.loadObject(target());}case 1->{if(r!=null){if(r.unsaved){ruleDrafts.remove(r.id);send("rule_cancel",q->{});view.remove("rule");}else r.load(rule(),view);}}case 2->{if(d!=null)d.lampName=d.lampBaseline;lineRename=lineRenameBase;}case 3->{if(d!=null)d.loadEvents(target());}default->{}}if(d!=null&&!d.dirty())d.updateVersions(target(),view);localMessage="Черновик формы отменён.";clearAndInit();}
    private void deleteRule(RuleDraft draft){if(pendingId>=0)return;JsonObject q=BuilderClient.request("rule_delete");q.addProperty("confirmed",true);q.addProperty("expectedRuleId",draft.id);q.addProperty("expectedFormVersion",draft.formVersion);pendingId=BuilderClient.reserveRequestId();q.addProperty("requestId",pendingId);pendingSection=6;pendingSince=System.currentTimeMillis();pendingRule=draft;pendingTarget=null;localMessage="Удаление: ожидается подтверждение сервера…";if(BuilderClient.send(q)<0)sendFailed();clearAndInit();}
    private void discardAll(){boolean serverDraft=bool(rule(),"draft");drafts.clear();ruleDrafts.clear();lineRename=lineRenameBase;if(serverDraft)send("rule_cancel",q->{});}
    private void continueCloseSave(){if(pendingId>=0)return;for(int section:new int[]{0,1,2,3})if(tabDirty(section)){saveTab(section,true);return;}if(anyDirty()){closeAfterSave=false;localMessage="Есть черновики других экземпляров. Вернитесь к ним или выберите «Не сохранять».";clearAndInit();return;}closeAfterSave=false;super.close();}
    private void saveTab(int section,boolean forClose){
        if(pendingId>=0||!tabDirty(section))return;if(!ClientPlayNetworking.canSend(BuilderServer.REQUEST)){localMessage="Нет соединения с серверным инструментом. Черновик сохранён.";closeAfterSave=false;return;}
        TargetDraft d=draft();RuleDraft r=ruleDraft();JsonObject q;
        if(d!=null&&(section==0||section==2||section==3)&&!d.version.equals(string(target(),"version",""))){localMessage="Объект изменён после начала формы. «Отмена формы» перечитает данные; черновик сохранён.";closeAfterSave=false;return;}
        if(section==1&&r!=null&&!r.formVersion.equals(formVersion())){localMessage="Состав или данные изменились на сервере. Перечитайте форму перед сохранением.";closeAfterSave=false;return;}
        switch(section){
            case 0->{q=BuilderClient.request("object");JsonObject values=new JsonObject();try{double value=Double.parseDouble(d.offset);if(!Double.isFinite(value))throw new NumberFormatException();values.addProperty("offset",value);}catch(NumberFormatException bad){localMessage="Высота должна быть конечным числом, например 0.125.";closeAfterSave=false;return;}if(bool(target(),"mount"))values.addProperty("mount",d.mount);if(bool(target(),"dogs"))values.addProperty("dogs",d.dogs);if(bool(target(),"openable")||bool(target(),"pulse"))values.addProperty("manual",d.manual);q.add("values",values);}
            case 1->{q=BuilderClient.request("rule_save");q.addProperty("id",r.id);q.addProperty("expectedRuleId",r.id);q.addProperty("name",r.name);q.addProperty("condition",r.condition);q.addProperty("effect",r.effect);q.addProperty("expectedFormVersion",r.formVersion);}
            case 2->{q=BuilderClient.request("lamp");pendingLampName=d!=null&&d.lampDirty();if(pendingLampName){q.addProperty("action","RENAME");q.addProperty("name",d.lampName);}else{if(selectedLine.isBlank()){localMessage="Выберите существующую линию для переименования; новую создайте отдельной кнопкой.";closeAfterSave=false;return;}q.addProperty("action","RENAME_LINE");q.addProperty("line",selectedLine);q.addProperty("name",lineRename);q.addProperty("expectedFormVersion",lineFormVersion);}}
            case 3->{d.open.stage();d.close.stage();q=BuilderClient.request("commands");q.add("afterOpen",json(d.open.commands));q.add("afterClose",json(d.close.commands));}
            default->{return;}
        }
        if(d!=null){q.addProperty("expectedKey",d.key);q.addProperty("expectedVersion",d.version);if(!q.has("expectedFormVersion"))q.addProperty("expectedFormVersion",section==0||section==3?formVersion():d.formVersion);}
        String payload=q.toString();if(payload.length()>BuilderServer.MAX_REQUEST-512||payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>31500){localMessage="Превышен общий размер формы. Сократите команды; оба черновика сохранены.";closeAfterSave=false;return;}
        pendingId=BuilderClient.reserveRequestId();q.addProperty("requestId",pendingId);pendingSince=System.currentTimeMillis();pendingSection=section;pendingTarget=section==1?null:d;pendingRule=section==1?r:null;closeAfterSave=forClose;localMessage="Сохранение: ожидается подтверждение сервера…";if(BuilderClient.send(q)<0)sendFailed();clearAndInit();
    }
    private void sendFailed(){pendingId=-1;pendingTarget=null;pendingRule=null;pendingSection=-1;closeAfterSave=false;localMessage="Запрос не отправлен. Дождитесь свободного соединения и повторите; черновик сохранён.";}
    private void rightInfo(){JsonObject t=target();rightLines.add("Выбранный экземпляр");rightLines.add(label(t));rightLines.add(bool(view,"pinned")?"Закреплён для редактирования":"Объект под прицелом");rightLines.add("Действие: "+string(view,"actionLabel","выбор"));rightLines.add("Смещение Y: "+string(t,"offset","—"));rightLines.add("Один ЛКМ — одна операция.");rightLines.add("Колесо в меню прокручивает форму.");rightLines.add("Сохранить / Отмена всегда внизу.");if(details){rightLines.add("UUID: "+string(t,"instance","—"));rightLines.add("Registry: "+string(t,"registry","—"));rightLines.add("Измерение: "+string(t,"dimension","—"));} }
    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        renderBackground(c);c.fill(4,4,width-4,height-4,0xEB17171C);c.drawCenteredTextWithShadow(textRenderer,title,width/2,9,0xDEC395);
        c.drawTextWithShadow(textRenderer,Text.literal(textRenderer.trimToWidth((bool(view,"pinned")?"Выбран: ":"Прицел: ")+label(target()),width-24)),12,24,0xD6C8AF);
        String message=localMessage.isBlank()?string(view,"message",""):localMessage;c.drawTextWithShadow(textRenderer,Text.literal(textRenderer.trimToWidth(message,width-24)),12,39,0xE1B272);
        if(width>=720){c.fill(right-5,bodyTop-2,right-4,height-80,0xFF504736);int y=bodyTop+3;for(String line:rightLines)for(var part:textRenderer.wrapLines(Text.literal(line),width-right-16)){if(y>=height-90)break;c.drawTextWithShadow(textRenderer,part,right+3,y,0xD6C8AF);y+=12;}}
        c.drawCenteredTextWithShadow(textRenderer,Text.literal("Страница "+(page+1)+" / "+Math.max(1,(rows.size()+rowsPerPage-1)/rowsPerPage)+" · колесо"),central+columnWidth/2,height-71,0xA4A4A4);
        String state=pendingId>=0?"Ожидается подтверждение сервера":anyDirty()?"Есть несохранённые изменения":"Сохранено · Esc закрывает меню";c.drawTextWithShadow(textRenderer,Text.literal(textRenderer.trimToWidth(state,width-24)),12,height-51,anyDirty()?0xE1B272:0xA8B7A5);super.render(c,mouseX,mouseY,delta);
    }
    @Override public boolean mouseScrolled(double x,double y,double amount){if(amount!=0){scroll(amount<0?1:-1);return true;}return super.mouseScrolled(x,y,amount);}
    private static JsonObject object(JsonObject o,String k){return o.has(k)&&o.get(k).isJsonObject()?o.getAsJsonObject(k):new JsonObject();}
    private static JsonArray array(JsonObject o,String k){return o.has(k)&&o.get(k).isJsonArray()?o.getAsJsonArray(k):new JsonArray();}
    private static String string(JsonObject o,String k,String fallback){return o.has(k)&&o.get(k).isJsonPrimitive()?o.get(k).getAsString():fallback;}
    private static boolean bool(JsonObject o,String k){return o.has(k)&&o.get(k).isJsonPrimitive()&&o.get(k).getAsBoolean();}
    private static long number(JsonObject o,String k,long fallback){try{return o.has(k)?o.get(k).getAsLong():fallback;}catch(RuntimeException invalid){return fallback;}}
    private static double decimal(JsonObject o,String k,double fallback){try{return o.has(k)?o.get(k).getAsDouble():fallback;}catch(RuntimeException invalid){return fallback;}}
    private static List<String> strings(JsonArray a){List<String> lines=new ArrayList<>();for(JsonElement e:a)if(e.isJsonPrimitive())lines.add(e.getAsString());return lines;}
    private static boolean allPulse(JsonArray a){for(JsonElement e:a)if(!e.isJsonObject()||!bool(e.getAsJsonObject(),"pulseOnly")&&!bool(e.getAsJsonObject(),"pulse"))return false;return true;}
    private static boolean lampReference(JsonObject target){return bool(target,"lamp")||string(target,"registry","").endsWith(":hunterlamp");}
    private static JsonArray json(List<String> list){JsonArray a=new JsonArray();list.forEach(a::add);return a;}
    private static String label(JsonObject o){return o.size()==0?"Нет выбранного объекта":string(o,"name","")+" ["+string(o,"id","?????")+"] · "+string(o,"position","");}
    private static String mountName(String mount){return switch(mount){case "FLOOR"->"Пол";case "CEILING"->"Потолок";default->"Вертикально";};}
    private static String condition(String value){return value.equals("ALL")?"Все":"Любой";}
    private static String effect(String value){return switch(value){case "OPEN"->"Только открывать";case "CLOSE"->"Только закрывать";case "PULSE"->"Однократная анимация";default->"Открывать и закрывать";};}
    private static String filterName(JsonObject filter){return switch(string(filter,"mode","all")){case "object"->"выбранный экземпляр";case "type"->"тип "+string(filter,"typeId","");case "region"->"область радиусом "+string(filter,"radius","?")+" блоков";default->"весь мод";};}
    private static String stopReason(String value){return switch(value){case "NEVER_STARTED"->"запись ещё не запускалась";case "BUILDER_MENU_STOP","COMMAND_STOP"->"остановлено вручную";case "DEADLINE","TIME_LIMIT","AUTO_TIMEOUT","TIMEOUT","AUTO_DURATION_EXPIRED"->"завершено по времени";case "SERVER_STOPPING","SERVER_STOPPED"->"сервер остановлен";case "REPLACED_BY_OPERATOR"->"заменено новой записью оператора";default->value.isBlank()?"нет активной записи":value;};}
}
