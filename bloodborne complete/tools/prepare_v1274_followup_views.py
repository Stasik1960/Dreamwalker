"""Resolve six explicit native QA camera views from the newly authored gallery.

Only a new capture-input file is written. No block, world, payload or source
preparation is changed. The primary agent owns the actual client run/review.
"""
from pathlib import Path
import argparse
import hashlib
import json
import math
from verify_v1271_final_readiness import ordinary


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gallery-input", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--production-jar", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    plan = json.loads(ordinary(args.gallery_input).read_text(encoding="utf8"))
    manifest = json.loads(ordinary(args.manifest).read_text(encoding="utf8"))
    assert plan["productionJarSha256"] == manifest["productionJarSha256"] == sha(ordinary(args.production_jar))
    frames = []
    for request in plan["nativeFollowupViewRequests"]:
        if "exhibitKey" in request:
            rows = [r for r in manifest["reviewPlatforms"] if r["key"] == request["exhibitKey"]]
            assert len(rows) == 1 and len(rows[0]["components"]) == 1
            exhibit, owner = rows[0], rows[0]["components"][0]
        else:
            rows = [r for r in manifest["exhibits"] if str(r["id"]) == request["mainId"]]
            assert len(rows) == 1
            exhibit = owner = rows[0]
        bounds = exhibit["bounds"]
        centre = [(bounds[i] + bounds[i+3])/2 for i in range(3)]
        size = [bounds[i+3] - bounds[i] for i in range(3)]
        distance = max(4, max(size)*1.25)
        camera_eye = list(centre)
        view = request["view"]
        if view == "front": camera_eye[2] = bounds[2] - distance
        elif view == "back": camera_eye[2] = bounds[5] + distance
        elif view == "left": camera_eye[0] = bounds[0] - distance
        elif view == "right": camera_eye[0] = bounds[3] + distance
        else: raise ValueError(view)
        dx, dy, dz = [centre[i] - camera_eye[i] for i in range(3)]
        camera = [camera_eye[0], camera_eye[1]-1.62, camera_eye[2],
                  math.degrees(math.atan2(-dx, dz)), -math.degrees(math.atan2(dy, math.hypot(dx,dz)))]
        frames.append({"key": request["key"], "fileName": request["key"].lower()+".png", "camera": camera,
                       "rootChecks": [{"root": owner["root"], "registry": owner["registry"],
                                       "properties": owner["placedState"], "uuid": owner["uuid"]}]})
    assert len(frames) == 6
    result = {"schema": "dreamwalker-final-gallery-native-visual-route-v1", "v1271FinalGalleryVisualTour": True,
              "v127GalleryBenchmark": False, "existingSavedWorldOnly": True,
              "productionJarSha256": manifest["productionJarSha256"], "galleryManifestSha256": sha(args.manifest),
              "gallerySourceWorld": str(args.manifest.parent.absolute()), "frames": frames,
              "nativeClientExecuted": False, "pixelReview": "PENDING_NATIVE_CAPTURE_AND_INDEPENDENT_REVIEW"}
    output = ordinary(args.output)
    assert not output.exists(), "Fresh QA capture input required"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+"\n", encoding="utf8")


if __name__ == "__main__":
    main()
