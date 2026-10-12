"""Read pinned official installer sources without executing Java."""
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
out = ROOT / 'build/v127-server-bootstrap'
chosen = '73c80c6457ac0acf6412979dc3d23a0ba2aaf2da'
with urllib.request.urlopen(f'https://raw.githubusercontent.com/FabricMC/fabric-installer/{chosen}/build.gradle', timeout=40) as response:
    assert "version = '1.0.1'" in response.read().decode('utf8')
for name in ('ServerLauncher.java', 'server/ServerInstaller.java'):
    url = f"https://raw.githubusercontent.com/FabricMC/fabric-installer/{chosen}/src/main/java/net/fabricmc/installer/{name}"
    with urllib.request.urlopen(url, timeout=40) as response:
        assert response.geturl().startswith('https://raw.githubusercontent.com/FabricMC/')
        data = response.read()
        (out / Path(name).name).write_bytes(data)
    print(url)
    print(data.decode('utf8'))
