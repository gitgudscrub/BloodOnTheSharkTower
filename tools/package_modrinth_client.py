#!/usr/bin/env python3
import gzip
import json
import os
import pathlib
import shutil
import struct
import urllib.parse
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
DIST = ROOT / "dist"
PACK_ROOT = ROOT / "build" / "modrinth-pack"
OVERRIDES = PACK_ROOT / "overrides"
MODS = OVERRIDES / "mods"

MINECRAFT_VERSION = "26.3"
FABRIC_LOADER_VERSION = "0.19.5"
PACK_VERSION = "1.2.0-dev-a.13.0"
SERVER_NAME = "Blood on the Sharktower"
SERVER_ADDRESS = "88.97.251.50"

# Versions matching the versions used by this project.
FABRIC_API_VERSION_ID = "BQfN2OGk"          # 0.160.6+26.3
VOICE_CHAT_VERSION_ID = "3fRw6NfP"          # fabric-2.6.23+26.3
NO_CHAT_RESTRICTIONS_PROJECT = "z440MEwJ"
NO_CHAT_RESTRICTIONS_FALLBACK = "qJSCnxEv"  # Fabric-MC26.2-v1.2.2 if 26.3 is not published yet

USER_AGENT = "BloodOnTheSharkTower-Packager/1.0 (github.com/gitgudscrub/BloodOnTheSharkTower)"


def api_json(url: str):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.load(response)


def version(version_id: str):
    return api_json(f"https://api.modrinth.com/v2/version/{version_id}")


def latest_ncr_version():
    params = urllib.parse.urlencode({
        "loaders": json.dumps(["fabric"]),
        "game_versions": json.dumps([MINECRAFT_VERSION]),
    })
    versions = api_json(
        f"https://api.modrinth.com/v2/project/{NO_CHAT_RESTRICTIONS_PROJECT}/version?{params}"
    )
    if versions:
        return versions[0]
    print("No Chat Restrictions has no 26.3-tagged build yet; using the requested 26.2 Fabric build fallback.")
    return version(NO_CHAT_RESTRICTIONS_FALLBACK)


def primary_file(version_data):
    files = version_data.get("files", [])
    for item in files:
        if item.get("primary"):
            return item
    if not files:
        raise RuntimeError(f"Modrinth version {version_data.get('id')} has no files")
    return files[0]


def modrinth_file_entry(version_data, client: str, server: str):
    item = primary_file(version_data)
    hashes = item.get("hashes", {})
    if "sha1" not in hashes or "sha512" not in hashes:
        raise RuntimeError(f"Missing required hashes for {item.get('filename')}")
    return {
        "path": f"mods/{item['filename']}",
        "hashes": {"sha1": hashes["sha1"], "sha512": hashes["sha512"]},
        "env": {"client": client, "server": server},
        "downloads": [item["url"]],
        "fileSize": item["size"],
    }


def find_release_jar():
    candidates = []
    for path in (ROOT / "build" / "libs").glob("*.jar"):
        name = path.name.lower()
        if name.endswith("-sources.jar") or "-dev.jar" in name:
            continue
        candidates.append(path)
    if len(candidates) != 1:
        raise RuntimeError(f"Expected exactly one release JAR, found: {[p.name for p in candidates]}")
    return candidates[0]


def nbt_string(value: str) -> bytes:
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def named_tag(tag_type: int, name: str, payload: bytes) -> bytes:
    return bytes([tag_type]) + nbt_string(name) + payload


def build_servers_dat(path: pathlib.Path):
    # Root compound -> list "servers" -> one server compound.
    server = b"".join([
        named_tag(8, "name", nbt_string(SERVER_NAME)),
        named_tag(8, "ip", nbt_string(SERVER_ADDRESS)),
        named_tag(1, "hidden", b"\x00"),
        named_tag(1, "acceptTextures", b"\x01"),
        b"\x00",  # end server compound
    ])
    servers_list = bytes([10]) + struct.pack(">i", 1) + server
    root = bytes([10]) + nbt_string("") + named_tag(9, "servers", servers_list) + b"\x00"
    path.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(path, "wb") as out:
        out.write(root)


def main():
    if PACK_ROOT.exists():
        shutil.rmtree(PACK_ROOT)
    MODS.mkdir(parents=True, exist_ok=True)
    DIST.mkdir(parents=True, exist_ok=True)

    release_jar = find_release_jar()
    shutil.copy2(release_jar, MODS / release_jar.name)
    build_servers_dat(OVERRIDES / "servers.dat")

    fabric_api = version(FABRIC_API_VERSION_ID)
    voice_chat = version(VOICE_CHAT_VERSION_ID)
    no_chat_restrictions = latest_ncr_version()

    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": PACK_VERSION,
        "name": "Blood on the Sharktower",
        "summary": "Blood on the Sharktower client pack with the community server preconfigured.",
        "files": [
            modrinth_file_entry(fabric_api, "required", "required"),
            modrinth_file_entry(voice_chat, "required", "required"),
            modrinth_file_entry(no_chat_restrictions, "required", "unsupported"),
        ],
        "dependencies": {
            "minecraft": MINECRAFT_VERSION,
            "fabric-loader": FABRIC_LOADER_VERSION,
        },
    }

    (PACK_ROOT / "modrinth.index.json").write_text(
        json.dumps(index, indent=2) + "\n", encoding="utf-8"
    )

    output = DIST / f"blood-on-the-sharktower-client-{PACK_VERSION}.mrpack"
    if output.exists():
        output.unlink()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(PACK_ROOT.rglob("*")):
            if file.is_file():
                archive.write(file, file.relative_to(PACK_ROOT).as_posix())

    print(f"Created {output}")
    print(f"Embedded mod: {release_jar.name}")
    print(f"Preconfigured server: {SERVER_ADDRESS}")
    print(f"No Chat Restrictions version: {no_chat_restrictions.get('version_number', no_chat_restrictions.get('id'))}")


if __name__ == "__main__":
    main()
