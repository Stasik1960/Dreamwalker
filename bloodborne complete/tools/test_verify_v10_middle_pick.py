"""Small evidence adversaries; synthetic fixtures are never actual-runtime proof."""
import copy
import unittest
from verify_v10_middle_pick import check_pick, check_inventory_readback, PICK_STATUS, PICK_INPUT

UUID = "b682ae91-4df9-49ab-b5b5-a6de93d4b82b"


def sample():
    stable = {"actualUuid": UUID, "actualRegistry": "bloodborne_rp:furniture_8", "Open": "0b", "Locked": "1b", "Scale": "0.8f", "VerticalOffset": "0.125d", "CustomName": '{"text":"old named instance"}'}
    scalar = lambda t, v: {"type": t, "value": v}
    compound = lambda v: scalar(10, v)
    return {"status": PICK_STATUS, "sourceUuid": UUID, "sourceRegistry": "bloodborne_rp:furniture_8", "canonicalAsset": "furniture_1", "canonicalClientModel": "geo/furniture_1.geo.json", "pickedItem": "bloodborne_rp:furniture_1_placer", "inputPath": PICK_INPUT, "authoritativePrePickItem": "minecraft:stick", "clientPrePickItem": "minecraft:stick", "actualSourceHit": {"type": "ENTITY", "entityUuid": UUID, "entityRegistry": "bloodborne_rp:furniture_8"}, "sourceStableTypedBefore": stable, "sourceStableTypedAfter": copy.deepcopy(stable), "serverClientSettingsMatch": True, "noInstanceIdentityCopied": True, "sourceUnchanged": True, "pickedTypedNbt": compound({"bloodborne_rp_object": compound({"Open": scalar(1, "0b"), "Locked": scalar(1, "1b"), "Scale": scalar(5, "0.8f")}), "display": compound({"Name": scalar(8, stable["CustomName"])})})}


class PickEvidenceTests(unittest.TestCase):
    def check(self, value):
        return check_pick(value, UUID, "furniture_8", "furniture_1", "geo/furniture_1.geo.json")

    def reject(self, mutate):
        value = sample(); mutate(value)
        with self.assertRaises(AssertionError):
            self.check(value)

    def test_complete_source_key_settings_chain(self):
        self.assertEqual(self.check(sample())["Scale"], {"type": 5, "value": "0.8f"})

    def test_preheld_placer_cannot_stand_in_for_real_middle_input(self):
        self.reject(lambda p: p.update(authoritativePrePickItem=p["pickedItem"], clientPrePickItem=p["pickedItem"]))

    def test_different_actual_ray_uuid_is_rejected(self):
        self.reject(lambda p: p["actualSourceHit"].update(entityUuid="d110f6a4-13a0-4571-b8ec-b6e9f5c3b703"))

    def test_same_numeric_value_wrong_nbt_type_is_rejected(self):
        self.reject(lambda p: p["pickedTypedNbt"]["value"]["bloodborne_rp_object"]["value"]["Scale"].update(type=6))

    def test_boolean_flags_cannot_mask_changed_source_pose(self):
        self.reject(lambda p: p["sourceStableTypedAfter"].update(VerticalOffset="0.5d"))

    def test_nested_list_cannot_hide_instance_identity(self):
        self.reject(lambda p: p["pickedTypedNbt"]["value"].update(extra={"type": 9, "value": [{"type": 10, "value": {"UUID": {"type": 11, "value": [1, 2, 3, 4]}}}]}))

    def test_custom_name_must_match_actual_source(self):
        self.reject(lambda p: p["pickedTypedNbt"]["value"]["display"]["value"]["Name"].update(value='{"text":"different name"}'))


class InventoryEvidenceTests(unittest.TestCase):
    def sample(self):
        original = {"selectedSlot": 0, "size": 1, "slots": [{"slot": 0, "item": "minecraft:air", "count": 0, "typedStack": {"type": 10, "value": {"id": {"type": 8, "value": "minecraft:air"}, "Count": {"type": 1, "value": "0b"}}}}]}
        diff = {"expectedSelectedSlot": 0, "actualSelectedSlot": 0, "selectedSlotMatches": True, "changedSlots": []}
        return {"originalInventorySnapshot": original, "serverInventorySnapshot": copy.deepcopy(original), "clientInventorySnapshot": copy.deepcopy(original), "serverInventoryDifferences": copy.deepcopy(diff), "clientInventoryDifferences": copy.deepcopy(diff)}

    def test_complete_typed_inventory_readback(self):
        check_inventory_readback(self.sample(), "synthetic restoration")

    def test_server_match_cannot_mask_residual_client_sentinel(self):
        value = self.sample(); value["clientInventorySnapshot"]["slots"][0]["item"] = "minecraft:stick"
        with self.assertRaises(AssertionError):
            check_inventory_readback(value, "synthetic restoration")

    def test_empty_slot_diffs_cannot_mask_selected_slot_change(self):
        value = self.sample(); value["clientInventoryDifferences"]["actualSelectedSlot"] = 1
        with self.assertRaises(AssertionError):
            check_inventory_readback(value, "synthetic restoration")


if __name__ == "__main__":
    unittest.main()
