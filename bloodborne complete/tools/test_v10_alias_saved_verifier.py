"""Small synthetic negative checks for the independent typed disk verifier.

No Minecraft/world/runtime evidence is manufactured or claimed by these tests.
Actual saved regions remain a separate mandatory runtime gate.
"""
import base64, copy, hashlib, unittest, uuid
from pathlib import Path
from unittest.mock import patch
from world_io import Tag, NbtFile, encode_nbt, TAG_BYTE, TAG_INT, TAG_FLOAT, TAG_DOUBLE, TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, compound
import verify_review_v10_alias_saved as verifier


def uid_tag(uid):
    values=[(uid.int>>shift)&0xffffffff for shift in (96,64,32,0)]
    return Tag(TAG_INT_ARRAY,[x-(1<<32) if x>=(1<<31) else x for x in values])
def uuid_for(text):return uuid.uuid5(uuid.NAMESPACE_URL,text)


def fixture():
    expected={};rows=[];raw_rows=[];probes=[];items=[];raw_items=[]
    for i,(alias,canonical) in enumerate(verifier.ALIASES.items()):
        for n in range(2):
            uid=uuid_for(alias+str(n));position=Tag(TAG_LIST,[Tag(TAG_DOUBLE,x) for x in (-42.0+n,64.125+n*.375,16.875+n*8)],TAG_DOUBLE)
            opaque=Tag(TAG_COMPOUND,{'Role':Tag(TAG_STRING,'opaque'),'InstanceIndex':Tag(TAG_INT,n),'TypedBytes':Tag(TAG_BYTE_ARRAY,bytes((n,1,255)))})
            original=Tag(TAG_COMPOUND,{'id':Tag(TAG_STRING,'bloodborne_rp:'+alias),'UUID':uid_tag(uid),'OpaqueAliasProbe':opaque})
            stable=Tag(TAG_COMPOUND,{'id':Tag(TAG_STRING,'bloodborne_rp:'+alias),'UUID':uid_tag(uid),'Pos':position,
                'Rotation':Tag(TAG_LIST,[Tag(TAG_FLOAT,90.0*n),Tag(TAG_FLOAT,0.0)],TAG_FLOAT),'Open':Tag(TAG_BYTE,n),
                'Locked':Tag(TAG_BYTE,1-n),'Scale':Tag(TAG_FLOAT,1.0+.25*n),'VerticalOffset':Tag(TAG_DOUBLE,.125+.375*n),
                'CustomName':Tag(TAG_STRING,'distinct '+alias+' '+str(n)),
                'Links':Tag(TAG_LIST,[uid_tag(uuid_for(alias+'link'+str(n)))],TAG_INT_ARRAY),
                'SourceLegacyPayload':Tag(TAG_COMPOUND,{'Schema':Tag(TAG_INT,1),'Original':original}), 'OpaqueAliasProbe':opaque})
            encoded=encode_nbt(NbtFile('',stable));expected[str(uid)]=stable
            rows.append(Tag(TAG_COMPOUND,{'Alias':Tag(TAG_STRING,alias),'UUID':uid_tag(uid),'Stable':stable}))
            raw_rows.append({'alias':alias,'canonical':canonical,'uuid':str(uid),'temporaryId':'91000',
                'expectedTypedNbtBase64':base64.b64encode(encoded).decode(),'stableTypedSha256':hashlib.sha256(encoded).hexdigest(),
                'currentAndExpectedTypedEqual':True,'policyLookupKey':'RP|minecraft:overworld|'+str(uid),
                'actualDebugCommand':'bb debug','actualDebugResult':1,'exactCommandEntityRay':True,'canonicalModelSelected':'model:'+canonical})
        held=Tag(TAG_COMPOUND,{'id':Tag(TAG_STRING,'bloodborne_rp:'+alias+'_placer'),'Count':Tag(TAG_BYTE,1),
            'tag':Tag(TAG_COMPOUND,{'display':Tag(TAG_COMPOUND,{'Name':Tag(TAG_STRING,'saved old '+alias)}),
                'bloodborne_rp_object':Tag(TAG_COMPOUND,{'Open':Tag(TAG_BYTE,i&1),'Locked':Tag(TAG_BYTE,1-(i&1)),'Scale':Tag(TAG_FLOAT,.875+i*.0625)}),
                'UUID':uid_tag(uuid_for(alias+'0')),'Links':Tag(TAG_LIST,[uid_tag(uuid_for(alias+'link0'))],TAG_INT_ARRAY),
                'AliasInventoryOpaque':Tag(TAG_COMPOUND,{'TypedBytes':Tag(TAG_BYTE_ARRAY,b'\x00\xff')})})})
        encoded=encode_nbt(NbtFile('',held));items.append(Tag(TAG_COMPOUND,{'Alias':Tag(TAG_STRING,alias),'Stack':held}))
        raw_items.append({'aliasItem':'bloodborne_rp:'+alias+'_placer','savedTypedStackNbtBase64':base64.b64encode(encoded).decode(),
                          'savedTypedStackSha256':hashlib.sha256(encoded).hexdigest(),'parsedAndSerializedTypedEqual':True})
        old,new=str(uuid_for(alias+'temporary-old')),str(uuid_for(alias+'temporary-new'))
        probes.append({'aliasItem':'bloodborne_rp:'+alias+'_placer','status':'PASS_SERVER_OLD_ITEM_CANONICAL_PICK_FRESH_UUID_TYPED_SETTINGS_AND_RAY_VALIDATED_CREATIVE_ATTACK',
            'newRegistry':'bloodborne_rp:'+canonical,'pickCanonical':True,'heldTypedNbtPreserved':True,'newLinksEmpty':True,
            'oldInventoryDiskRoundtrip':True,'oldInventoryIdentityNotCopied':True,
            'oldAndCanonicalCreativeDrops':0,'exactTwoTemporaryInstancesRemoved':True,'permanentFourteenUnchanged':True,
            'oldRegistryTemporaryProbeActualRayAndDamageGuard':True,'newCanonicalTemporaryProbeActualRayAndDamageGuard':True,
            'oldRegistryTemporaryProbeUuid':old,'newCanonicalTemporaryProbeUuid':new,'newUuid':new})
    saved={'Rows':Tag(TAG_LIST,rows,TAG_COMPOUND),'Items':Tag(TAG_LIST,items,TAG_COMPOUND)}
    phases=[]
    for n in range(3):
        phases.append({'world':Path('/synthetic/world/'+str(n)),'saved':copy.deepcopy(saved),
            'raw':{'fixtures':copy.deepcopy(raw_rows),'probes':copy.deepcopy(probes),'savedOldInventoryStacks':copy.deepcopy(raw_items),'permanentFourteenBeforeSha256':'same','permanentFourteenAfterSha256':'same'},
            'reportRecord':{'path':'synthetic-'+str(n)},'consoleRecord':{'path':'synthetic-console-'+str(n)}})
    inventories=[copy.deepcopy(expected) for _ in range(3)]
    return phases,inventories


class TypedAliasVerifierTests(unittest.TestCase):
    def run_fixture(self,phases,inventories):
        with patch.object(verifier,'check_copy'),patch.object(verifier,'full_rp_entities',side_effect=inventories),patch.object(verifier,'actual_item_drops',return_value=[]):
            return verifier.verify(*phases,'0'*64)
    def test_valid_distinct_typed_fixture_and_fresh_probes(self):
        phases,inventory=fixture();result=self.run_fixture(phases,inventory)
        self.assertEqual(result['permanentInstances'],14);self.assertEqual(result['savedTemporaryRpInstancesOrItemDrops'],0)
    def test_exact_numeric_tag_type_change_is_rejected(self):
        phases,inventory=fixture();first=next(iter(inventory[1]));compound(inventory[1][first])['Scale']=Tag(TAG_DOUBLE,1.0)
        with self.assertRaisesRegex(ValueError,'Actual saved typed alias fields changed'):self.run_fixture(phases,inventory)
    def test_opaque_original_loss_is_rejected(self):
        phases,inventory=fixture();first=next(iter(inventory[2]));compound(compound(inventory[2][first])['SourceLegacyPayload'])['Original']=Tag(TAG_COMPOUND,{})
        with self.assertRaisesRegex(ValueError,'Actual saved typed alias fields changed'):self.run_fixture(phases,inventory)
    def test_instance_dedup_on_disk_is_rejected(self):
        phases,inventory=fixture();inventory[1].pop(next(iter(inventory[1])))
        with self.assertRaisesRegex(ValueError,'Actual saved typed alias fields changed'):self.run_fixture(phases,inventory)
    def test_old_item_reusing_permanent_uuid_is_rejected(self):
        phases,inventory=fixture();uid=next(iter(inventory[0]));probe=phases[1]['raw']['probes'][0];probe['newUuid']=probe['newCanonicalTemporaryProbeUuid']=uid
        with self.assertRaisesRegex(ValueError,'copied UUID'):self.run_fixture(phases,inventory)
    def test_nonzero_creative_drop_claim_is_rejected(self):
        phases,inventory=fixture();phases[1]['raw']['probes'][0]['oldAndCanonicalCreativeDrops']=1
        with self.assertRaisesRegex(ValueError,'Creative guard proof incomplete'):self.run_fixture(phases,inventory)
    def test_leaked_temporary_instance_is_rejected(self):
        phases,inventory=fixture();inventory[2]['11111111-1111-1111-1111-111111111111']=next(iter(inventory[2].values()))
        with self.assertRaisesRegex(ValueError,'leaked temporary'):self.run_fixture(phases,inventory)
    def test_disk_old_inventory_registry_canonicalized_is_rejected(self):
        phases,inventory=fixture()
        for phase in phases:compound(compound(phase['saved']['Items'].value[0])['Stack'])['id']=Tag(TAG_STRING,'bloodborne_rp:furniture_1_placer')
        with self.assertRaisesRegex(ValueError,'old ItemStack id/count/full typed roundtrip'):self.run_fixture(phases,inventory)
    def test_disk_old_inventory_opaque_data_loss_is_rejected(self):
        phases,inventory=fixture();stack=compound(compound(phases[2]['saved']['Items'].value[0])['Stack']);compound(stack['tag']).pop('AliasInventoryOpaque')
        with self.assertRaisesRegex(ValueError,'fixture journal changed'):self.run_fixture(phases,inventory)


if __name__=='__main__':unittest.main()
