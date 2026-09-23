import unittest
from source_variant_rng import JavaRandom, signed, position_seed, weighted_index, guards_match


class SourceRandomTests(unittest.TestCase):
    def test_java_fixed_width_arithmetic(self):
        self.assertEqual(JavaRandom(0).next_long(), -4962768465676381896)
        self.assertEqual(signed(0xffffffff,32), -1)
        self.assertEqual(position_seed((0,0,0)),0)
        self.assertEqual(weighted_index([100,50,500],(0,0,0)),2)

    def test_guard_uses_source_position_not_master_position(self):
        weights = [100,50,500]
        origin, offset = (9123,64,-512), (0,1,-1)
        point = tuple(origin[i]+offset[i] for i in range(3))
        actual = weighted_index(weights,point)
        guards = [{'offset':offset,'weights':weights,'indices':[actual]}]
        self.assertTrue(guards_match(guards,origin))
        guards[0]['indices']=[(actual+1)%3]
        self.assertFalse(guards_match(guards,origin))

    def test_multipart_reseeds_independent_groups(self):
        p=(324,69,-155)
        seed=JavaRandom(position_seed(p)).next_long()
        expected=abs(signed(JavaRandom(seed).next_long(),32))%650
        index=0 if expected<100 else 1 if expected<150 else 2
        self.assertEqual(weighted_index([100,50,500],p,True),index)


if __name__ == '__main__': unittest.main()
