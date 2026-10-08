"""Vanilla Java positional weighted model choice, frozen at ORIGINAL position.

Pure numerical core re-expressed from reviewed Bloodborne-Blocks
source_variant_rng.py at 6bca311; no historical resources or model catalogs.
Minecraft source-version bytecode verification is recorded separately.
"""
def signed(value,bits):
    value&=(1<<bits)-1
    return value-(1<<bits) if value&(1<<(bits-1)) else value
def position_seed(pos):
    x,y,z=pos
    value=signed(x*3129871,32)^signed(z*116129781,64)^y
    return signed(value*value*42317861+value*11,64)>>16
class JavaRandom:
    def __init__(self,seed): self.seed=(seed^25214903917)&((1<<48)-1)
    def next(self,bits):
        self.seed=(self.seed*25214903917+11)&((1<<48)-1)
        return signed(self.seed>>(48-bits),32)
    def next_long(self): return signed((self.next(32)<<32)+self.next(32),64)
def weighted_index(weights,pos,multipart=False):
    if not weights or any(type(n)!=int or n<=0 for n in weights): raise ValueError('Positive integer weights required')
    if len(weights)==1:return 0
    rng=JavaRandom(position_seed(pos))
    if multipart:rng=JavaRandom(rng.next_long())
    value=signed(rng.next_long(),32)
    if value==-(1<<31):return 0
    point=abs(value)%sum(weights)
    for i,w in enumerate(weights):
        point-=w
        if point<0:return i
    raise AssertionError('Unreachable weighting')
