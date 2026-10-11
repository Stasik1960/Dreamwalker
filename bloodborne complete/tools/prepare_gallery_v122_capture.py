"""Native client samples of new visual-size platforms, RP seating and frames."""
from pathlib import Path

source=Path(__file__).with_name('prepare_flat_gallery_capture.py').read_text(encoding='utf8')
source=source.replace("default=24)","default=32)")
source=source.replace("    for numeric in ('93155'",'''    for registry in ('bloodborne_rp:dog_cage','bloodborne_rp:dog_cage2','bloodborne_rp:dog_cage3',
                     'bloodborne_rp:cage_obj_1','bloodborne_rp:cage_obj_2','bloodborne_rp:cage_obj_3',
                     'bloodborne_rp:chandelier_small','bloodborne_rp:chandelier_large',
                     'bloodborne_rp:stairs','bloodborne_rp:ladder',
                     'bloodborne_dw:source_addon_floor_dec_0','bloodborne_dw:source_addon_button_10','bloodborne_dw:source_addon_chains_0',
                     'bloodborne_dw:source_addon_bag_2','bloodborne_dw:source_stairs_exposed_cut_copper_bottom_outer1',
                     'bloodborne_dw:bb_tool','bloodborne_rp:boom_hammer','bloodborne_rp:cleric_beast_spawn_egg'):
        matches=[row for row in placeable if row['registry']==registry]
        if matches:add(matches[0])
    for numeric in ('93155' ''')
source=source.replace("'Every section, old native and entity examples, map-absent examples and largest exhibits. Full saved inventory is checked independently.'",
                      "'Every current section, unchanged native V12, RP dog cages/chandeliers/ladder/stairs seated by real geometry, weapon/tool/spawn egg frames, map-absent examples and largest exhibits; full saved inventory independently checked.'")
exec(compile(source,str(Path(__file__).with_name('prepare_flat_gallery_capture.py')),'exec'),globals())
