"""Compact catalog entry point for the existing editable world/gallery format."""
import argparse,json
from pathlib import Path
from build_editable_city_gallery import build as build_editable

def build(source,output,city,city_bounds=None):
    city=Path(city)
    if not city.is_dir()or not(city/'definitions.json').exists():
        raise ValueError('native city resource directory required')
    return build_editable(source,output,city,citybounds=city_bounds)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--source',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True);p.add_argument('--city',type=Path,required=True)
    p.add_argument('--city-bounds',type=int,nargs=6);a=p.parse_args()
    m=build(a.source,a.output,a.city,a.city_bounds);print(json.dumps(m['coverage']))
