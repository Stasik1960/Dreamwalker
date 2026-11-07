"""Small, checked changes to the pinned V4 editor instead of copying its vendor assets."""

import json


def replace_once(text, source, replacement):
    if text.count(source) != 1:
        raise ValueError(f"V4 editor anchor changed: {source[:100]}")
    return text.replace(source, replacement, 1)


def patch_app(app):
    edits = {
        "{objects:[],reviewed:false,edited:false}":
            "{objects:clone(state.baseline||[]),modelOffset:[0,0,0],reviewed:false,edited:false}",
        "modelRoot.scale.set(1,1,1);for":
            "modelRoot.scale.set(1,1,1);modelRoot.position.fromArray(work().modelOffset||[0,0,0]);for",
        "if(frame)frameScene();$('loading').hidden=true;":
            "if(frame)frameScene();$('loading').hidden=true;window.DW_V22?.refresh();",
        "function add(type){":
            "function add(type,mode='players'){",
        "type,mode:'both',name:(type==='seat'?'Сидение ':'Коллизия ')":
            "type,mode,name:(type==='seat'?'Сидение ':mode==='outline'?'Обводка ':'Коллизия ')",
        "o.mode==='players'?0xc294f9:0x5fdda4":
            "o.mode==='players'?0xc294f9:o.mode==='outline'?0xfa785f:0x5fdda4",
        "o.mode==='players'?0xc294f9:0x78f7bf":
            "o.mode==='players'?0xc294f9:o.mode==='outline'?0xfa785f:0x78f7bf",
        "mesh.userData.objectId=o.id;group.add(mesh);":
            "mesh.userData.objectId=o.id;group.userData.volumeMode=seat?'seat':o.mode;group.add(mesh);",
        "boxRoot.visible=$('showBoxes').checked;if(selected)":
            "boxRoot.visible=$('showBoxes').checked;window.DW_V22?.refreshVolumes();if(selected)",
        "if(!mesh||!poseMatches())":
            "if(!mesh||!mesh.visible||!boxRoot.visible||!poseMatches())",
        ".filter(x=>x.object.userData.objectId)":
            ".filter(x=>boxRoot.visible&&x.object.parent.visible&&x.object.userData.objectId)",
        "schemaVersion:3,unitsPerBlock":
            "schemaVersion:4,unitsPerBlock",
        "emptyReviewedState:'remove_player_and_block_colliders_and_seats'":
            "emptyReviewedState:'remove_player_and_block_colliders_outlines_and_seats',instanceScope:'new_placements_only'",
        "'block_support','block_selection','non_player_physics','render_geometry'":
            "'block_support','non_player_physics'",
        "reviewed:w.reviewed,replace:true,pose:":
            "reviewed:w.reviewed,replace:true,modelOffset:w.modelOffset||[0,0,0],pose:",
        "rotation:o.rotation,size:SEAT,capacity:1,trigger:":
            "rotation:o.rotation,size:SEAT,capacity:1,front:[0,0,-1],trigger:",
        "![1,2,3].includes(p.schemaVersion)":
            "![1,2,3,4].includes(p.schemaVersion)",
        "(p.schemaVersion===3&&!['both','blocks','players'].includes(b.mode))":
            "(p.schemaVersion>=3&&!(p.schemaVersion===4?['both','blocks','players','outline']:['both','blocks','players']).includes(b.mode))",
        "if(!Array.isArray(s.colliders)||!Array.isArray(s.seats))":
            "if(p.schemaVersion===4&&!vec(s.modelOffset))throw Error('Неверное смещение модели');if(!Array.isArray(s.colliders)||!Array.isArray(s.seats))",
        "edited:true,reviewed:s.reviewed,objects:":
            "edited:true,reviewed:s.reviewed,modelOffset:s.modelOffset||[0,0,0],objects:",
        "zip.file('APPLY_IN_CODEX_RU.md',window.DW_INSTRUCTIONS)":
            "zip.file('APPLY_IN_CODEX_RU.md',window.DW_V22.instructions)",
        "JSON.stringify(window.DW_SCHEMA,null,2)":
            "JSON.stringify(window.DW_V22.schema,null,2)",
        "window.DW_TEST={exportProject":
            "window.DW_TEST={setModelOffset(value){snapshot();work().modelOffset=value;modelRoot.position.fromArray(value);changed();},grid,boxRoot,exportProject",
        "dirty=true;updateStats();saveLocal();}":
            "dirty=true;modelRoot.position.fromArray(work().modelOffset||[0,0,0]);window.DW_V22?.refresh();updateStats();saveLocal();}",
    }
    for before, after in edits.items():
        app = replace_once(app, before, after)
    return app


def patch_index(html):
    edits = {
        "COLLISION STUDIO · V10": "COLLISION STUDIO · V22",
        '<option value="players">Только игроки</option>':
            '<option value="players">Только игроки</option><option value="outline">Только обводка</option>',
        '<div class="section">Коллизии и сидения</div>':
            '<div class="section">Коллизии, обводка и сидения</div>',
        '<button id="addSeat" class="seat">＋ Сидение</button></div>':
            '<button id="addSeat" class="seat">＋ Сидение</button></div><button id="addOutline">＋ Обводка</button>',
        '<script src="pack-export.js"></script><script src="assembly.js"></script>':
            '<script src="reference.js"></script><script src="assembly.js"></script><script src="studio-v22.js"></script>',
    }
    for before, after in edits.items():
        html = replace_once(html, before, after)
    return html


def patch_schema(raw):
    schema = json.loads(raw)
    schema["title"] = "Dreamwalker V22 Studio project"
    schema["properties"]["schemaVersion"] = {"const": 4}
    def visit(value):
        if isinstance(value, dict):
            if "colliders" in value.get("properties", {}):
                value["required"].append("modelOffset")
                value["properties"]["modelOffset"] = {"$ref": "#/$defs/vector"}
            if value.get("enum") == ["both", "blocks", "players"]:
                value["enum"].append("outline")
            for child in value.values():
                visit(child)
        elif isinstance(value, list):
            for child in value:
                visit(child)
    visit(schema)
    return json.dumps(schema, ensure_ascii=False, indent=2) + "\n"
