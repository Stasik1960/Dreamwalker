"""Synthetic negative evidence checks, never a Minecraft/client runtime claim."""
import copy
import unittest

import package_review_v10 as package


def identity(n):
    return f'00000000-0000-0000-0000-{n:012x}'


def picked(uuid,source,canonical,locked=False,name=None):
    before={'actualUuid':uuid,'actualRegistry':'bloodborne_rp:'+source,
            'Open':'0b','Locked':'1b' if locked else '0b','Scale':'0.8f',
            'actualScale':.8,'actualOpen':False,'Links':'[]','VerticalOffset':'0.125d'}
    state={'Open':{'type':1,'value':'0b'},'Locked':{'type':1,'value':before['Locked']},'Scale':{'type':5,'value':'0.8f'}}
    values={'bloodborne_rp_object':{'type':10,'value':state}}
    if name:
        before['CustomName']=name;values['display']={'type':10,'value':{'Name':{'type':8,'value':name}}}
    return {'status':'PASS_ACTUAL_MIDDLE_KEY_CANONICAL_ITEM_SETTINGS_SOURCE_UNCHANGED',
        'sourceUuid':uuid,'sourceRegistry':'bloodborne_rp:'+source,'canonicalAsset':canonical,
        'actualSourceHit':{'type':'ENTITY','entityUuid':uuid,'entityRegistry':'bloodborne_rp:'+source},
        'authoritativePrePickItem':'minecraft:stick','clientPrePickItem':'minecraft:stick',
        'inputPath':'actual pickItemKey queued once; MinecraftClient native doItemPick and Creative inventory packet',
        'pickedItem':'bloodborne_rp:'+canonical+'_placer','canonicalClientModel':'geo/'+canonical+'.geo.json',
        'pickedTypedNbt':{'type':10,'value':values},'sourceStableTypedBefore':before,
        'sourceStableTypedAfter':copy.deepcopy(before),'serverClientSettingsMatch':True,
        'noInstanceIdentityCopied':True,'sourceUnchanged':True}


class ActualMiddlePackageGateTests(unittest.TestCase):
    def setUp(self):
        self.pick=picked(identity(1),'furniture_8','furniture_1',True,'{"text":"Old fixture"}')
        self.aliases={'furniture_8':'furniture_1','furniture_3':'furniture_10','furniture_4':'furniture_11',
            'furniture_5':'furniture_12','furniture_6':'furniture_13','furniture_7':'furniture_14','furniture_9':'furniture_2'}
        names=list(self.aliases.values())+[f'canonical_{n}' for n in range(62)]
        self.models={name:'geo/'+name+'.geo.json' for name in names}
        self.cases=[{'asset':name,'uuid':identity(n+10),'actualMiddlePick':picked(identity(n+10),name,name)} for n,name in enumerate(names)]
        self.actual={'actualCanonicalMiddlePickCount':69,'actualOldAliasMiddlePickCount':0,'ordinaryAliasPickCases':[],
            'actualOriginalInventoryRestorationAtExit':{'readThread':'ACTUAL_SERVER_THREAD','serverInventoryRestored':True,'clientInventoryRestored':True,'selectedSlot':3}}
        original={'size':41,'selectedSlot':3,'slots':[{'slot':i,'item':'minecraft:air','count':0,'typedStack':{'type':10,'value':{'id':{'type':8,'value':'minecraft:air'},'Count':{'type':1,'value':'0b'}}}} for i in range(41)]}
        restored=self.actual['actualOriginalInventoryRestorationAtExit']
        for field in ['originalInventorySnapshot','serverInventorySnapshot','clientInventorySnapshot']:restored[field]=copy.deepcopy(original)
        for field in ['serverInventoryDifferences','clientInventoryDifferences']:restored[field]={'expectedSelectedSlot':3,'actualSelectedSlot':3,'selectedSlotMatches':True,'changedSlots':[]}

    def check(self,pick=None):
        return package.gate_middle_pick(pick or self.pick,identity(1),'furniture_8','furniture_1','geo/furniture_1.geo.json')

    def author(self):
        actual=copy.deepcopy(self.actual);actual['actualOldAliasMiddlePickCount']=7
        for n,(alias,canonical) in enumerate(self.aliases.items(),100):
            uuid=identity(n);pick=picked(uuid,alias,canonical,True,'{"text":"Old '+alias+'"}')
            native={'cell':{'state':'minecraft:air','blockEntity':None},'support':{'state':'minecraft:stone','blockEntity':None}}
            actual['ordinaryAliasPickCases'].append({'alias':alias,'canonical':canonical,'uuid':uuid,
                'status':'PASS_ACTUAL_OLD_REGISTRY_CLIENT_MIDDLE_KEY_CANONICAL_SETTINGS_AND_CREATIVE_CLEANUP',
                'actualMiddlePick':pick,'technicalSetupServerThread':{'readThread':'ACTUAL_SERVER_THREAD','uuid':uuid,'registry':'bloodborne_rp:'+alias,'stable':copy.deepcopy(pick['sourceStableTypedBefore']),'nativeBefore':native},
                'actualCleanupServerThread':{'readThread':'ACTUAL_SERVER_THREAD','present':False,'creativeDropCount':0,'nativeAfter':copy.deepcopy(native)}})
        row=actual['ordinaryAliasPickCases'][0];pick=row['actualMiddlePick'];native=row['technicalSetupServerThread']['nativeBefore']
        actual['actualPickedItemReinstallation']={
            'status':'PASS_ACTUAL_MIDDLE_PICK_CANONICAL_STACK_ORDINARY_REPLACE_NEW_UUID_SETTINGS_AND_CLEANUP',
            'sourceOldUuid':row['uuid'],'canonicalAsset':row['canonical'],'actualPickedItem':pick['pickedItem'],
            'actualPickedTypedNbt':copy.deepcopy(pick['pickedTypedNbt']),'newUuid':identity(900),
            'actualServerThreadPlacement':{'uuid':identity(900),'registry':'bloodborne_rp:'+row['canonical'],'open':False,'locked':True,'scale':.8,'name':pick['sourceStableTypedBefore']['CustomName'],'linksEmpty':True,'offset':0},
            'inputPath':'actual picked client stack copied only for actor inventory setup; ordinary use-key ItemUsageContext and attack-key packets',
            'nativeBefore':copy.deepcopy(native),'actualCleanupServerThread':{'readThread':'ACTUAL_SERVER_THREAD','present':False,'creativeDropCount':0,'nativeAfter':copy.deepcopy(native)}}
        return actual

    def test_real_key_and_typed_settings_source_preservation_accepted(self):self.check()

    def test_direct_getter_is_not_an_actual_client_input(self):
        value=copy.deepcopy(self.pick);value['inputPath']='getPickBlockStack called directly'
        with self.assertRaisesRegex(ValueError,'middle-button key'):self.check(value)

    def test_old_alias_pick_must_be_canonical(self):
        value=copy.deepcopy(self.pick);value['pickedItem']='bloodborne_rp:furniture_8_placer'
        with self.assertRaisesRegex(ValueError,'canonical item'):self.check(value)

    def test_actual_wrong_source_ray_rejected(self):
        value=copy.deepcopy(self.pick);value['actualSourceHit']['entityUuid']=identity(2)
        with self.assertRaisesRegex(ValueError,'exact old/new source ray'):self.check(value)

    def test_source_typed_state_change_cannot_be_hidden_by_true_flags(self):
        value=copy.deepcopy(self.pick);value['sourceStableTypedAfter']['Links']='[changed]'
        with self.assertRaisesRegex(ValueError,'complete stable source'):self.check(value)

    def test_equal_numeric_scale_with_wrong_nbt_tag_rejected(self):
        value=copy.deepcopy(self.pick);value['pickedTypedNbt']['value']['bloodborne_rp_object']['value']['Scale']={'type':6,'value':'0.8d'}
        with self.assertRaisesRegex(ValueError,'setting/type'):self.check(value)

    def test_nested_instance_identity_rejected_even_with_true_flag(self):
        value=copy.deepcopy(self.pick);value['pickedTypedNbt']['value']['extra']={'type':9,'value':[{'type':10,'value':{'UUID':{'type':11,'value':'[I;1,2,3,4]'}}}]}
        with self.assertRaisesRegex(ValueError,'instance UUID'):self.check(value)

    def test_wrong_client_canonical_model_rejected(self):
        value=copy.deepcopy(self.pick);value['canonicalClientModel']='geo/furniture_8.geo.json'
        with self.assertRaisesRegex(ValueError,'client model differ'):self.check(value)

    def test_all69_reenter_inputs_and_inventory_restoration(self):
        package.gate_actual_middle_picks(self.actual,self.cases,self.models,self.aliases,'REENTER')

    def test_missing_one_of69_middle_inputs_rejected(self):
        cases=copy.deepcopy(self.cases);cases[-1].pop('actualMiddlePick')
        with self.assertRaisesRegex(ValueError,'middle-button input'):package.gate_actual_middle_picks(self.actual,cases,self.models,self.aliases,'REENTER')

    def test_one_sided_inventory_restore_rejected(self):
        actual=copy.deepcopy(self.actual);actual['actualOriginalInventoryRestorationAtExit']['clientInventoryRestored']=False
        with self.assertRaisesRegex(ValueError,'restore on both sides'):package.gate_actual_middle_picks(actual,self.cases,self.models,self.aliases,'REENTER')

    def test_true_flags_cannot_hide_residual_client_middle_pick_item(self):
        actual=copy.deepcopy(self.actual);actual['actualOriginalInventoryRestorationAtExit']['clientInventorySnapshot']['slots'][0]['item']='minecraft:stick'
        with self.assertRaisesRegex(ValueError,'snapshots must exactly equal'):package.gate_actual_middle_picks(actual,self.cases,self.models,self.aliases,'REENTER')

    def test_equal_inventory_slots_with_wrong_selected_slot_rejected(self):
        actual=copy.deepcopy(self.actual);actual['actualOriginalInventoryRestorationAtExit']['clientInventorySnapshot']['selectedSlot']=4
        with self.assertRaisesRegex(ValueError,'snapshots must exactly equal'):package.gate_actual_middle_picks(actual,self.cases,self.models,self.aliases,'REENTER')

    def test_equal_snapshots_with_nonempty_actual_difference_rejected(self):
        actual=copy.deepcopy(self.actual);actual['actualOriginalInventoryRestorationAtExit']['clientInventoryDifferences']['changedSlots']=[{'slot':0}]
        with self.assertRaisesRegex(ValueError,'explicitly empty'):package.gate_actual_middle_picks(actual,self.cases,self.models,self.aliases,'REENTER')

    def test_boolean_inventory_restore_without_typed_snapshots_rejected(self):
        actual=copy.deepcopy(self.actual);actual['actualOriginalInventoryRestorationAtExit'].pop('originalInventorySnapshot')
        with self.assertRaisesRegex(ValueError,'complete PlayerInventory'):package.gate_actual_middle_picks(actual,self.cases,self.models,self.aliases,'REENTER')

    def test_reported_final_slot_must_equal_full_original_snapshot(self):
        actual=copy.deepcopy(self.actual);actual['actualOriginalInventoryRestorationAtExit']['selectedSlot']=4
        with self.assertRaisesRegex(ValueError,'Final reported selected slot'):package.gate_actual_middle_picks(actual,self.cases,self.models,self.aliases,'REENTER')

    def test_author7_alias_inputs_picked_replacement_and_cleanup(self):
        package.gate_actual_middle_picks(self.author(),self.cases,self.models,self.aliases,'AUTHOR')

    def test_author_replacement_cannot_reuse_any_old_uuid(self):
        actual=self.author();uuid=actual['ordinaryAliasPickCases'][1]['uuid'];actual['actualPickedItemReinstallation']['newUuid']=uuid;actual['actualPickedItemReinstallation']['actualServerThreadPlacement']['uuid']=uuid
        with self.assertRaisesRegex(ValueError,'fresh canonical UUID'):package.gate_actual_middle_picks(actual,self.cases,self.models,self.aliases,'AUTHOR')

    def test_old_alias_cleanup_native_mutation_rejected(self):
        actual=self.author();actual['ordinaryAliasPickCases'][0]['actualCleanupServerThread']['nativeAfter']={'changed':True}
        with self.assertRaisesRegex(ValueError,'preserve native pad'):package.gate_actual_middle_picks(actual,self.cases,self.models,self.aliases,'AUTHOR')

    def test_picked_replacement_drop_rejected(self):
        actual=self.author();actual['actualPickedItemReinstallation']['actualCleanupServerThread']['creativeDropCount']=1
        with self.assertRaisesRegex(ValueError,'drop proof incomplete'):package.gate_actual_middle_picks(actual,self.cases,self.models,self.aliases,'AUTHOR')


if __name__=='__main__':unittest.main()
