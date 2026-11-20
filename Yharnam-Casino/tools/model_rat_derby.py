"""Four-lane clockwork racetrack. The lanes point along the local +X axis."""
from math import pi
import bpy
import build_gaming_floor_blender as floor
from derby_racer import add_rat

LANES=(-.350,-.046,.258,.562)
DECK=.940

def case_glass(m):
    # Thin, low-opacity solid panes keep the wind-up racers visible in Blender and glTF.
    glass=floor.material("Showcase | pale bottle glass","9CB9AC",.18)
    shader=glass.node_tree.nodes.get("Principled BSDF")
    shader.inputs["Alpha"].default_value=.032
    shader.inputs["Specular IOR Level"].default_value=.28
    glass.diffuse_color=(*glass.diffuse_color[:3],.032)
    glass.surface_render_method="BLENDED"
    glass.use_backface_culling=True
    left,right,front,back=-1.674,1.59,-.540,.754
    upper=1.303
    xmid,ymid=(left+right)/2,(front+back)/2
    width,depth=right-left,back-front
    pane_height=upper-DECK-.021
    for y in (front,back):
        floor.cube("Case | long glass pane",(xmid,y,DECK+pane_height*.5+.008),(width-.035,.009,pane_height),glass)
    for x in (left,right):
        floor.cube("Case | end glass pane",(x,ymid,DECK+pane_height*.5+.008),(.009,depth-.033,pane_height),glass)
    floor.cube("Case | glass lid",(xmid,ymid,upper-.009),(width-.022,depth-.022,.007),glass)
    for x in (left,right):
        for y in (front,back):
            floor.cube("Case | post socket",(x,y,DECK+.025),(.072,.062,.05),m["aged"])
            floor.cube("Case | narrow post",(x,y,DECK+(upper-DECK)/2),(.033,.031,upper-DECK),m["aged"])
            floor.cube("Case | corner cap",(x,y,upper+.008),(.065,.061,.024),m["gold"])
    for y in (front,back):
        floor.cube("Case | sill",(xmid,y,DECK+.010),(width,.026,.019),m["ebony"])
        if y==back:
            floor.cube("Case | brass lid strip",(xmid,y,upper),(width,.023,.016),m["aged"])
        else:
            for x in (left+.09,right-.09):
                floor.cube("Case | front lid clip",(x,y,upper),(.18,.024,.015),m["aged"])
    for x in (left,right):
        floor.cube("Case | short sill",(x,ymid,DECK+.010),(.026,depth,.019),m["ebony"])
        floor.cube("Case | short brass lid strip",(x,ymid,upper),(.023,depth,.016),m["aged"])

def crank(m):
    x,y=1.39,-.69
    floor.cube("Starter | bolted heel",(x,y,DECK+.012),(.28,.18,.024),m["ebony"])
    for side in (-1,1):
        floor.cube("Starter | pivot cheek",(x,y+side*.058,DECK+.056),(.143,.029,.096),m["aged"])
    shaft=floor.lathe("Starter | brass axle",[(-.091,.037),(.091,.037)],m["gold"],8)
    shaft.rotation_euler.x=pi/2
    shaft.location=(x,y,DECK+.078)
    pivot=bpy.data.objects.new("Lever_Start",None)
    bpy.context.collection.objects.link(pivot)
    pivot.empty_display_size=.06
    pivot.location=shaft.location
    pivot["axis"]="Y"
    arm=floor.beam("Lever | pull arm",(0,0,0),(.176,0,.175),.036,.028,m["gold"])
    arm.parent=pivot
    handle=floor.lathe("Lever | oxblood handle",[(-.083,.033),(-.060,.044),(.060,.044),(.083,.033)],m["red"],8)
    handle.rotation_euler.x=pi/2
    handle.location=(.176,0,.175)
    handle.parent=pivot

def cabinet(m):
    floor.table_base(m)
    for obj in bpy.context.scene.objects:
        if obj.name=="Stretcher":
            for v in obj.data.vertices:v.co.x*=1.47
        elif obj.type=="MESH":
            obj.location.x*=1.47
    outline=floor.outline([26,29,30,30,30,30,30,30,30,30,30,30,30,29,26])
    floor.band("Cabinet | stepped skirt",outline,[(.92,.88,.654),(.975,.93,.696),(.975,.93,.722)],m["ebony"])
    floor.band("Cabinet | deep walnut apron",outline,[(.975,.934,.720),(.975,.934,.872)],m["wood"])
    floor.band("Cabinet | upper moulding",outline,[(.976,.94,.853),(1,1,.893),(1,1,.914)],m["ebony"])
    print_mat=floor.material("Printed derby course","384b44",.88,texture="derby-lanes")
    floor.band("Deck | lanes and betting stations",outline,[(.982,.965,.910),(.982,.965,DECK)],m["ebony"],
               print_mat,lambda x,y:(.5+x/4,.5+y/2))
    floor.band("Deck | walnut surround",outline,[(1,1,.903),(1,1,.955),(.977,.957,.955),(.977,.957,.918)],
               m["wood"],close=False)
    floor.ring("Deck | inlaid brass piping",outline,.984,.980,.956,m["aged"],.003)
    for x in (-1.28,1.28):
        floor.cube("Cabinet | apron end strap",(x,-.879,.788),(.053,.017,.128),m["aged"])
    face=floor.material("Derby | painted fascia","53363B",.62,texture="derby-plaque")
    panel=[(-.62,-.070),(.62,-.070),(.65,-.042),(.65,.042),(.62,.070),(-.62,.070),(-.65,.042),(-.65,-.042)]
    nameplate=floor.band("Cabinet | enamel race plaque",panel,[(1,1,0),(1,1,.008)],m["aged"],face,
                         lambda x,y:(.5+x/1.30,.5+y/.14))
    nameplate.location=(0,-.882,.788)
    nameplate.rotation_euler.x=pi/2
    for x in (-.682,.682):
        stud=floor.lathe("Cabinet | plaque stud",[(0,.014),(.009,.017),(.015,.010)],m["gold"],8)
        stud.rotation_euler.x=pi/2
        stud.location=(x,-.876,.788)

def build(m):
    cabinet(m)
    case_glass(m)
    crank(m)
    blue=floor.material("Runner | Prussian enamel","426677",.39,.22)
    green=floor.material("Runner | olive enamel","717A53",.40,.22)
    amber=floor.material("Runner | honey enamel","BD9260",.38,.20)
    for i,(y,x,coat) in enumerate(zip(LANES,(-.48,.60,-.02,-.91),(m["red"],blue,green,amber)),start=1):
        rat=add_rat(i,(x,y,DECK+.0015),m,coat)
        rat["race_lane"]=i
        rat["travel_axis"]="+X"
        rat["lane_center_y"]=y
    bpy.context.scene["derby_lanes"]=4
    bpy.context.scene["derby_finish_x"]=1.27
