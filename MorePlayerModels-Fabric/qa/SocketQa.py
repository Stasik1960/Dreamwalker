import argparse
import gzip
import os
import shutil
import socket
import struct
import subprocess
import tempfile
import time
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PROTOCOL = 763
PING = "moreplayermodels:ping"
PONG = "moreplayermodels:pong"
UPDATE = "moreplayermodels:player_data_update"
SEND = "moreplayermodels:player_data_send"
CHUNKS = "moreplayermodels:profile_chunks_v1"
MPM_WIRE_VERSION = 8
CHUNK_SIZE = 30000

def varint(n):
    assert 0 <= n <= 0x7FFFFFFF, n
    out = bytearray()
    while n >= 128:
        out.append((n & 127) | 128)
        n >>= 7
    return bytes(out + bytes([n]))

def read_varint(read):
    result = 0
    for i in range(5):
        b = read(1)[0]
        result |= (b & 127) << (i * 7)
        if not b & 128:
            return result
    raise ValueError("bad VarInt")

def string(s):
    b = s.encode("utf-8")
    return varint(len(b)) + b

def read_string(read):
    return read(read_varint(read)).decode("utf-8")

class ByteReader:
    def __init__(self, raw):
        self.raw = raw
        self.at = 0

    def read(self, count):
        end = self.at + count
        if end > len(self.raw):
            raise EOFError("packet ended")
        data = self.raw[self.at:end]
        self.at = end
        return data

    def remaining(self):
        return self.read(len(self.raw) - self.at)

def socket_read(sock, count):
    data = bytearray()
    while len(data) < count:
        buf = sock.recv(count - len(data))
        if not buf:
            raise EOFError("server closed connection")
        data.extend(buf)
    return bytes(data)

class Peer:
    def __init__(self, port, username):
        self.sock = socket.create_connection(("127.0.0.1", port), timeout=10)
        self.compression = False
        self.channels = set()
        self.username = username
        self.uuid = None
        self.login(port)

    def send(self, id, payload=b""):
        packet = varint(id) + payload
        if self.compression:
            packet = b"\0" + packet
        self.sock.sendall(varint(len(packet)) + packet)

    def recv(self):
        size = read_varint(lambda size: socket_read(self.sock, size))
        raw = socket_read(self.sock, size)
        if self.compression:
            reader = ByteReader(raw)
            actual = read_varint(reader.read)
            raw = reader.remaining()
            if actual:
                raw = zlib.decompress(raw)
                assert actual == len(raw)
        reader = ByteReader(raw)
        nr = read_varint(reader.read)
        return nr, reader

    def login(self, port):
        self.send(0, varint(PROTOCOL) + string("127.0.0.1") + struct.pack(">H", port) + varint(2))
        self.send(0, string(self.username) + b"\0")
        while True:
            nr, rd = self.recv()
            if nr == 0:
                raise RuntimeError(f"login rejected: {read_string(rd.read)}")
            if nr == 1:
                raise RuntimeError("isolated server must run with online-mode=false")
            if nr == 2:
                self.uuid = rd.read(16)
                assert read_string(rd.read) == self.username
                break
            if nr == 3:
                self.compression = True
            if nr == 4:
                query_id = read_varint(rd.read)
                self.send(2, varint(query_id) + b"\0")
        deadline = time.monotonic() + 12
        while time.monotonic() < deadline:
            self.poll()
            if PING in self.channels and UPDATE in self.channels:
                self.custom("minecraft:register", "\0".join([PONG, SEND]).encode())
                return
        raise AssertionError("MPM channels not advertised")

    def close(self):
        self.sock.close()

    def custom(self, channel, payload):
        self.send(13, string(channel) + payload)

    def poll(self):
        nr, data = self.recv()
        if nr == 23:
            channel = read_string(data.read)
            payload = data.remaining()
            if channel == "minecraft:register":
                self.channels.update(payload.decode("utf-8").split("\0"))
            return channel, payload
        if nr == 26:
            raise RuntimeError(f"play disconnected: {read_string(data.read)}")
        if nr == 35:
            self.send(18, data.read(8))
        if nr == 60:
            data.read(8 * 3 + 4 * 2 + 1)
            teleport_id = read_varint(data.read)
            self.send(0, varint(teleport_id))
        return "", b""

    def anticipate(self, label, accepted, timeout=12):
        deadline = time.monotonic() + timeout
        self.sock.settimeout(timeout)
        while time.monotonic() < deadline:
            try:
                channel, payload = self.poll()
            except socket.timeout:
                break
            if (value := accepted(channel, payload)) is not None:
                self.sock.settimeout(10)
                return value
        self.sock.settimeout(10)
        raise AssertionError("did not receive " + label)

    def activate(self, kind, nbt, fragment=True):
        payload = nbt if kind == 2 else struct.pack(">i", MPM_WIRE_VERSION) + nbt
        if not fragment:
            self.custom(PING if kind == 1 else UPDATE, payload)
            return
        for position in range(0, len(payload), CHUNK_SIZE):
            piece = payload[position:position + CHUNK_SIZE]
            self.custom(CHUNKS, struct.pack(">Bii", kind, len(payload), position) + piece)

def nbt_name(name):
    content = name.encode("ascii")
    return struct.pack(">H", len(content)) + content

def nbt_named(kind, name, content):
    return bytes([kind]) + nbt_name(name) + content

def example_bytes(size):
    motif = b"MPM test profile preserved!"
    return (motif * ((size + len(motif) - 1) // len(motif)))[:size]

def profile(mode, revision, size):
    blob = example_bytes(size)
    extra = nbt_named(7, "Example", struct.pack(">I", len(blob)) + blob) + b"\0"
    return b"\x0a\0\0" + nbt_named(10, "ExtraData", extra) + nbt_named(1, "MpmEnabled", bytes([mode])) + nbt_named(4, "MpmSyncRevision", struct.pack(">q", revision)) + b"\0"

def verify_profile(message, revision, mode, expected_size):
    expected = [
        nbt_named(1, "MpmEnabled", bytes([mode])),
        nbt_named(4, "MpmSyncRevision", struct.pack(">q", revision)),
        nbt_named(7, "Example", struct.pack(">I", expected_size) + example_bytes(expected_size))
    ]
    return all(value in message for value in expected)

def test_server(port, baseline=False, packet_limit=False):
    target = 245760 if packet_limit else 65536
    update_size = target - len(profile(True, 701, 0)) if packet_limit else target
    ping_size = target - len(profile(True, 702, 0)) - 4 if packet_limit else target
    peer = Peer(port, "MpmProtocolQA")
    try:
        assert (CHUNKS in peer.channels) != baseline, peer.channels
        if baseline:
            peer.activate(1, profile(True, 700, 32), fragment=False)
            peer.anticipate("legacy pong before toggle", lambda channel, payload: payload if channel == PONG else None)
            peer.activate(2, profile(True, 701, update_size), fragment=False)
            peer.anticipate("legacy disconnect", lambda channel, payload: None)
            raise AssertionError("baseline should disconnect")
        print("Baseline-compatible small profile handshake:")
        peer.activate(1, profile(False, 700, 32), fragment=False)
        update = None
        pong = None
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline and (pong is None or update is None):
            channel, payload = peer.poll()
            if channel == PONG:
                pong = payload
            if channel == SEND and verify_profile(payload, 700, False, 32):
                update = payload
        assert update is not None and pong == struct.pack(">i", MPM_WIRE_VERSION)
        print("  legacy pong and unchanged profile")
        print("Large MPM switch via segmented profile update:")
        peer.custom(CHUNKS, struct.pack(">Bii", 2, 245_761, 0) + bytes(CHUNK_SIZE))
        peer.custom(CHUNKS, struct.pack(">Bii", 2, 65_536, CHUNK_SIZE) + bytes(CHUNK_SIZE))
        peer.activate(2, profile(True, 701, update_size))
        update = peer.anticipate("large updated profile", lambda channel, payload: payload if channel == SEND and verify_profile(payload, 701, True, update_size) else None)
        assert verify_profile(update, 701, True, update_size)
        print("  connection survived a frame above the total limit and an out-of-order frame")
        print(f"  received mode=true, revision=701, all {update_size} ExtraData bytes intact")
    except RuntimeError as ex:
        if not baseline or "Payload may not be larger than 32767 bytes" not in str(ex):
            raise
        print("Baseline real Fabric server reproduced kick:", ex)
    finally:
        peer.close()
    if baseline:
        peer = Peer(port, "MpmProtocolQA")
        try:
            peer.activate(1, profile(True, 701, update_size), fragment=False)
            peer.anticipate("legacy reconnect disconnect", lambda channel, payload: None)
        except RuntimeError as ex:
            assert "Payload may not be larger than 32767 bytes" in str(ex), ex
            print("Baseline real Fabric server reproduced reconnect kick:", ex)
        finally:
            peer.close()
        return None
    print("Reconnect with saved large profile via segmented ping:")
    peer = Peer(port, "MpmProtocolQA")
    try:
        peer.activate(1, profile(True, 702, ping_size))
        pong = None
        update = None
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline and (pong is None or update is None):
            channel, payload = peer.poll()
            if channel == PONG:
                pong = payload
            if channel == SEND and verify_profile(payload, 702, True, ping_size):
                update = payload
        assert update is not None and pong == struct.pack(">i", MPM_WIRE_VERSION)
        print(f"  connected, received pong and intact revision=702 profile with {ping_size} ExtraData bytes")
        return peer.uuid, ping_size
    finally:
        peer.close()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--client", type=Path, required=True)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--vanilla", type=Path, required=True)
    parser.add_argument("--fabric-loader", type=Path, required=True)
    parser.add_argument("--run-root", type=Path, default=ROOT / "build" / "socket", help="QA log/world directory")
    parser.add_argument("--baseline", action="store_true")
    parser.add_argument("--packet-limit", action="store_true", help="Test exactly 245760 serialized bytes for each oversized C2S packet")
    args = parser.parse_args()
    for required in (args.jar, args.java, args.vanilla, args.fabric_loader):
        assert required.is_file(), required
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        port = probe.getsockname()[1]
    game_type = "baseline-socket" if args.baseline else "patched-socket"
    args.run_root.mkdir(parents=True, exist_ok=True)
    game = Path(tempfile.mkdtemp(prefix=game_type + "-", dir=args.run_root))
    mods = game / "mods"
    mods.mkdir()
    shutil.copy2(args.jar, mods / args.jar.name)
    api = next((args.client / "mods").glob("fabric-api-*.jar"))
    shutil.copy2(api, mods / api.name)
    cpm = next((args.client / "mods").glob("CustomPlayerModels-Fabric*.jar"))
    shutil.copy2(cpm, mods / cpm.name)
    (game / "eula.txt").write_text("eula=true\n", encoding="ascii")
    (game / "options.txt").write_text("soundCategory_master:0.0\n", encoding="ascii")
    (game / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\n"
        "network-compression-threshold=-1\nenforce-secure-profile=false\n"
        "gamemode=creative\nallow-flight=true\nlevel-type=minecraft:flat\n"
        "level-name=world\nview-distance=2\nsimulation-distance=3\n"
        'generate-structures=false\nlevel-seed=42\n'
        'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","structures":false,"features":false}\n',
        encoding="ascii"
    )
    lib = args.client / "libraries"
    libs = sorted(p for p in lib.rglob("*.jar") if "-natives" not in p.name)
    cp = os.pathsep.join(map(str, [args.fabric_loader, args.vanilla, *libs]))
    console = game / "console.log"
    with console.open("w", encoding="utf-8") as log:
        proc = subprocess.Popen(
            [str(args.java), "-Xms512m", "-Xmx1600m", "-cp", cp, "net.fabricmc.loader.impl.launch.knot.KnotServer", "nogui"],
            cwd=game, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.PIPE
        )
        try:
            deadline = time.monotonic() + 90
            while time.monotonic() < deadline:
                if proc.poll() is not None:
                    raise RuntimeError("Fabric QA server stopped, see " + str(console))
                if 'For help, type "help"' in console.read_text(encoding="utf-8", errors="replace"):
                    break
                time.sleep(0.3)
            else:
                raise RuntimeError("Fabric QA server did not start, see " + str(console))
            result = test_server(port, args.baseline, args.packet_limit)
        finally:
            if proc.poll() is None:
                try:
                    proc.stdin.write(b"stop\n")
                    proc.stdin.flush()
                    proc.wait(timeout=25)
                except (BrokenPipeError, subprocess.TimeoutExpired):
                    proc.kill()
                    proc.wait(timeout=10)
    if not args.baseline:
        import uuid as uuid_module
        uuid, survived_size = result
        profile_file = game / "world" / "playerdata" / "moreplayermodels" / (str(uuid_module.UUID(bytes=uuid)) + ".dat")
        contents = gzip.decompress(profile_file.read_bytes())
        assert verify_profile(contents, 702, True, survived_size), profile_file
        print(f"  server persisted all {survived_size} ExtraData bytes with revision=702 and MpmEnabled=true")
    print("Socket QA passed. Server log:", console)

if __name__ == "__main__":
    main()
