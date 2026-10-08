"""Regression checks for preserving typed data and selecting NBT contexts."""
import copy
import unittest
from world_io import Tag, NbtFile, encode_nbt, TAG_COMPOUND as C, TAG_LIST as L, TAG_STRING as S
from rp_migration import migrate_entity, migrate_item, migrate_inventory_record, migrate_legacy_lamp_state, registry_schema


def compound(**values):
    return Tag(C, values)


def wire(tag):
    return encode_nbt(NbtFile("", tag))


class RpMigrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.schema = registry_schema()

    def test_mob_uuid_health_attributes_brain_and_opaque_forge_data_preserved(self):
        original = compound(id=Tag(S, "bloodborne:cleric_beast"), UUID=Tag(11, [1, 2, 3, 4]),
            Pos=Tag(L, [Tag(6, 10.25), Tag(6, 88.5), Tag(6, -24.75)], 6),
            Rotation=Tag(L, [Tag(5, 90), Tag(5, 1)], 5), Health=Tag(5, 48),
            Attributes=Tag(L, [compound(Name=Tag(S, "minecraft:generic.max_health"), Base=Tag(6, 80))], C),
            Brain=compound(memories=compound()), ForgeCaps=compound(custom=compound(id=Tag(S, "bloodborne:hunterlamp"))),
            NoAI=Tag(1, 0), Scale=Tag(5, 1.5), CustomName=Tag(S, '{"text":"test"}'))
        before = wire(original)
        result = migrate_entity(original, schema=self.schema)
        self.assertEqual(result.tag.value["id"].value, "bloodborne_rp:cleric_beast")
        expected = copy.deepcopy(original)
        expected.value["id"].value = "bloodborne_rp:cleric_beast"
        self.assertEqual(wire(result.tag), wire(expected))
        self.assertEqual(wire(original), before)
        self.assertNotIn("bloodborne_rp_frozen", result.tag.value)
        self.assertEqual(result.issues, [])

    def test_gate_nbt_and_directed_mechanism_links_survive(self):
        original = compound(id=Tag(S, "bloodborne:main_gate"), EntityState=Tag(3, 1),
            Locked=Tag(1, 1), Scale=Tag(5, 2), ConnectionId=Tag(S, "east gate"),
            Links=Tag(L, [Tag(11, [-1, 1, -2, 2])], 11), LeverPulseTicks=Tag(3, 42))
        result = migrate_entity(original, schema=self.schema)
        expected = copy.deepcopy(original)
        expected.value["id"].value = "bloodborne_rp:main_gate"
        self.assertEqual(result.tag, expected)
        self.assertEqual(result.issues, [])

    def test_unknown_entity_is_not_partially_rewritten(self):
        original = compound(id=Tag(S, "bloodborne:bullet"),
            HandItems=Tag(L, [compound(id=Tag(S, "bloodborne:sawcleaver_true"))], C))
        result = migrate_entity(original, schema=self.schema)
        self.assertEqual(wire(result.tag), wire(original))
        self.assertFalse(result.changed)
        self.assertEqual(result.issues[0]["code"], "UNMAPPED_FORGE_ENTITY")

    def test_item_stack_context_changes_weapon_form_preserving_custom_data(self):
        original = compound(id=Tag(S, "bloodborne:sawcleaver_true"), Count=Tag(1, 1),
            tag=compound(Damage=Tag(3, 12), CustomModelData=Tag(3, 31),
                         display=compound(Name=Tag(S, '{"text":"heirloom"}'))))
        result = migrate_item(original, schema=self.schema)
        self.assertEqual(result.tag.value["id"].value, "bloodborne_rp:saw_cleaver")
        self.assertEqual(result.tag.value["tag"].value["BloodborneRpForm"], Tag(1, 1))
        for key, value in original.value["tag"].value.items():
            self.assertEqual(result.tag.value["tag"].value[key], value)

    def test_conflicting_weapon_form_rejected_without_changes(self):
        original = compound(id=Tag(S, "bloodborne:sawspear_true"), tag=compound(BloodborneRpForm=Tag(1, 0)))
        result = migrate_item(original, schema=self.schema)
        self.assertEqual(result.tag, original)
        self.assertFalse(result.changed)
        self.assertEqual(result.issues[0]["code"], "WEAPON_FORM_CONFLICT")

    def test_arbitrary_compounds_and_block_entity_id_are_preserved(self):
        original = compound(id=Tag(S, "bloodborne:hunterlamp"),
            Items=Tag(L, [compound(id=Tag(S, "bloodborne:sawspear_false"))], C),
            OtherData=compound(id=Tag(S, "bloodborne:main_gate")))
        result = migrate_inventory_record(original, kind="block_entity", schema=self.schema)
        self.assertEqual(result.tag.value["id"], original.value["id"])
        self.assertEqual(result.tag.value["OtherData"], original.value["OtherData"])
        self.assertEqual(result.tag.value["Items"].value[0].value["id"].value, "bloodborne_rp:saw_spear")

    def test_vanilla_carrier_passengers_and_item_entity_are_explicit_contexts(self):
        original = compound(id=Tag(S, "minecraft:boat"),
            Passengers=Tag(L, [compound(id=Tag(S, "bloodborne:hunterlamp"))], C),
            Item=compound(id=Tag(S, "bloodborne:boomhammer_false")),
            CustomData=compound(id=Tag(S, "bloodborne:cleric_beast")))
        result = migrate_entity(original, schema=self.schema)
        self.assertEqual(result.tag.value["id"].value, "minecraft:boat")
        self.assertEqual(result.tag.value["Passengers"].value[0].value["id"].value, "bloodborne_rp:hunterlamp")
        self.assertEqual(result.tag.value["Item"].value["id"].value, "bloodborne_rp:boom_hammer")
        self.assertEqual(result.tag.value["CustomData"], original.value["CustomData"])

    def test_second_pass_changes_nothing(self):
        original = compound(id=Tag(S, "bloodborne:hunterlamp"),
            HandItems=Tag(L, [compound(id=Tag(S, "bloodborne:sawcleaver_true"))], C))
        first = migrate_entity(original, schema=self.schema)
        second = migrate_entity(first.tag, schema=self.schema)
        self.assertEqual(wire(first.tag), wire(second.tag))
        self.assertEqual(second.changes, [])
        self.assertEqual(second.issues, [])

    def test_wrong_inventory_tag_type_and_unreviewed_alias_are_visible(self):
        original = compound(id=Tag(S, "bloodborne:door_1"), IsOpen=Tag(1, 1), Items=Tag(S, "opaque"))
        result = migrate_entity(original, schema=self.schema)
        self.assertEqual({issue["code"] for issue in result.issues},
                         {"EXPECTED_ITEM_LIST", "UNREVIEWED_LEGACY_STATE_ALIAS"})
        self.assertEqual(result.tag.value["IsOpen"], original.value["IsOpen"])

    def test_lamp_registration_id_and_entity_uuid_remain_distinct_no_routes_invented(self):
        legacy_id = [2119845798, -1233039827, -1132772455, -1113870321]
        entity_uuid = [614039691, 1402487662, -1386688151, 1241422566]
        row = compound(area=Tag(S, "area.bloodborne.name.0"), X=Tag(3, 229), Y=Tag(3, 69), Z=Tag(3, -931),
                       hunterLampId=Tag(11, legacy_id))
        original = compound(data=compound(HunterLamps=Tag(L, [row, copy.deepcopy(row)], C)), DataVersion=Tag(3, 2975))
        lamp = compound(id=Tag(S, "bloodborne:hunterlamp"), Id=Tag(11, legacy_id), UUID=Tag(11, entity_uuid),
                        Pos=Tag(L, [Tag(6, 229), Tag(6, 69), Tag(6, -931)], 6))
        before = wire(original)
        result = migrate_legacy_lamp_state(original, entities=[("minecraft:overworld", lamp)])
        nodes = result.tag.value["data"].value["Nodes"].value
        self.assertEqual(len(nodes), 1)
        self.assertEqual(nodes[0].value["Id"].value, legacy_id)
        self.assertEqual(nodes[0].value["Lamp"].value, entity_uuid)
        self.assertEqual(nodes[0].value["Dimension"].value, "minecraft:overworld")
        self.assertEqual(nodes[0].value["Routes"].value, [])
        self.assertEqual(nodes[0].value["Name"].value, "area.bloodborne.name.0")
        self.assertEqual(result.issues[0]["code"], "SOURCE_DUPLICATE_LAMP_REGISTRATION")
        self.assertEqual(wire(original), before)

    def test_lamp_unresolved_entity_does_not_create_a_fake_node(self):
        original = compound(data=compound(HunterLamps=Tag(L, [compound(area=Tag(S, "test"), X=Tag(3, 0),
            Y=Tag(3, 64), Z=Tag(3, 0), hunterLampId=Tag(11, [1, 2, 3, 4]))], C)))
        result = migrate_legacy_lamp_state(original, entities=[])
        self.assertEqual(result.tag, original)
        self.assertFalse(result.changed)
        self.assertEqual(result.issues[0]["code"], "LEGACY_LAMP_ENTITY_NOT_UNIQUE")

    def test_legacy_object_item_maps_to_registered_placer_and_unknown_tool_stays(self):
        object_item = compound(id=Tag(S, "bloodborne:small_gate"), Count=Tag(1, 3),
                              tag=compound(display=compound(Name=Tag(S, "gate"))))
        result = migrate_item(object_item, schema=self.schema)
        self.assertEqual(result.tag.value["id"].value, "bloodborne_rp:small_gate_placer")
        self.assertIn(result.tag.value["id"].value, self.schema["item_ids"])
        self.assertEqual(result.tag.value["Count"], object_item.value["Count"])
        self.assertEqual(result.tag.value["tag"], object_item.value["tag"])
        unknown = compound(id=Tag(S, "bloodborne:delete"), Count=Tag(1, 1))
        refused = migrate_item(unknown, schema=self.schema)
        self.assertEqual(refused.tag, unknown)
        self.assertFalse(refused.changed)
        self.assertEqual(refused.issues[0]["code"], "UNMAPPED_FORGE_ITEM")


if __name__ == "__main__":
    unittest.main()
