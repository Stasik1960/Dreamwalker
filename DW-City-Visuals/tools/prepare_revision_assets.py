"""Generate v7 assets before indexing and hashing the delivered profiles."""
from pathlib import Path
import subprocess
import sys

TOOLS = Path(__file__).resolve().parent
for script in ('import_tree_project.py', 'repair_sprite_tree.py', 'repair_facade_assets.py'):
    subprocess.run([sys.executable, str(TOOLS / script)], check=True)
