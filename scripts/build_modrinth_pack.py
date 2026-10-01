#!/usr/bin/env python3
"""Build a deterministic Blood on the Sharktower .mrpack.

Third-party mods are referenced directly from their pinned Modrinth versions.
The locally-built Sharktower JAR is embedded under overrides/mods so the pack
remains a single Modrinth project for players to install and update.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import shutil
import struct
import urllib.request
import zipfile
from pathlib import Path

API = "https://api.modrinth.com/v2/version/{}"
USER_AGENT = "gitgudscrub/BloodOnTheSharkTower (GitHub Actions)"


def fetch_version(version_id: str) -> dict:
    request = urllib.request.Request(
        API.format(version_id),
        headers={"User-Agent": USER_AGENT},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def primary_file(version: dict) -> dict:
    files = version.get("files") or []
    if not files:
        raise RuntimeError(f"Modrinth version {version.get('id')} has no files")
    return next((item for item in files if item.get("primary")), files[0])


def dependency_entry(spec: dict) -> dict:
    version = fetch_version(spec["version_id"])
    file = primary_file(version)
    hashes = file.get("hashes") or {}
    if not hashes.get("sha1") or not hashes.get("sha512"):
        raise RuntimeError(f"Missing required hashes for {spec['name']}")

    return {
        "path": f"mods/{file['filename']}",
        "hashes": {
            "sha1": hashes["sha1"],
            "sha512": hashes["sha512"],
        },
        "env": {
            "client": spec.get("client", "required"),
            "server": spec.get("server", "required"),
        },
        "downloads": [file["url"]],
        "fileSize": file["size"],
    }


def copy_overrides(source: Path, destination: Path) -> None:
    if not source.exists():
        return
    for item in source.rglob("*"):
        if item.is_dir() or item.name == ".gitkeep":
            continue
        relative = item.relative_to(source)
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(item, target)


def nbt_utf(value: str) -> bytes:
    encoded = value.encode("utf-8")
    if len(encoded) > 65535:
        raise ValueError("NBT string is too long")
    return struct.pack(">H", len(encoded)) + encoded


def named_nbt_tag(tag_type: int, name: str, payload: bytes) -> bytes:
    return bytes([tag_type]) + nbt_utf(name) + payload


def write_servers_dat(path: Path, server_name: str, address: str) -> None:
    """Write a minimal Minecraft servers.dat containing the Sharktower server.

    servers.dat is a gzip-compressed NBT compound. Only stable fields needed for
    a normal saved multiplayer entry are emitted so Minecraft can populate the
    remaining transient ping/icon information itself.
    """
    server = bytearray()
    server += named_nbt_tag(8, "name", nbt_utf(server_name))
    server += named_nbt_tag(8, "ip", nbt_utf(address))
    server += named_nbt_tag(1, "hidden", b"\x00")
    server += b"\x00"  # TAG_End for this server compound.

    root = bytearray()
    root += b"\x0a\x00\x00"  # Root TAG_Compound with an empty name.
    root += bytes([9]) + nbt_utf("servers")
    root += bytes([10]) + struct.pack(">i", 1) + server
    root += b"\x00"  # TAG_End for the root compound.

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(bytes(root), mtime=0))


def sha(path: Path, algorithm: str) -> str:
    digest = hashlib.new(algorithm)
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--config", default="modrinth/pack-config.json", type=Path)
    parser.add_argument("--overrides", default="modrinth/overrides", type=Path)
    args = parser.parse_args()

    if not args.jar.is_file():
        raise SystemExit(f"Built Sharktower JAR not found: {args.jar}")

    config = json.loads(args.config.read_text(encoding="utf-8"))
    files = [dependency_entry(spec) for spec in config["dependencies"]]

    work = Path("build/modrinth-pack")
    if work.exists():
        shutil.rmtree(work)
    overrides = work / "overrides"
    overrides.mkdir(parents=True)
    copy_overrides(args.overrides, overrides)

    server_config = config.get("server")
    if server_config:
        server_name = server_config.get("name", config["name"])
        server_address = server_config.get("address")
        if not server_address:
            raise RuntimeError("Pack server configuration is missing an address")
        write_servers_dat(overrides / "servers.dat", server_name, server_address)

    mods_dir = overrides / "mods"
    mods_dir.mkdir(parents=True, exist_ok=True)
    sharktower_name = f"blood-on-the-sharktower-{args.version}.jar"
    sharktower_target = mods_dir / sharktower_name
    shutil.copy2(args.jar, sharktower_target)

    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": args.version,
        "name": config["name"],
        "summary": config.get("summary", ""),
        "files": files,
        "dependencies": {
            "minecraft": config["minecraft"],
            "fabric-loader": config["fabric_loader"],
        },
    }
    (work / "modrinth.index.json").write_text(
        json.dumps(index, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    if args.output.exists():
        args.output.unlink()
    with zipfile.ZipFile(args.output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for item in sorted(work.rglob("*")):
            if item.is_file():
                archive.write(item, item.relative_to(work).as_posix())

    print(f"Built {args.output}")
    print(f"  version: {args.version}")
    print(f"  minecraft: {config['minecraft']}")
    print(f"  loader: fabric-loader {config['fabric_loader']}")
    print(f"  remote dependencies: {len(files)}")
    print(f"  embedded Sharktower JAR: {sharktower_name}")
    if server_config:
        print(f"  multiplayer server: {server_config.get('name', config['name'])} ({server_config['address']})")
    print(f"  mrpack sha1: {sha(args.output, 'sha1')}")
    print(f"  mrpack sha512: {sha(args.output, 'sha512')}")


if __name__ == "__main__":
    main()
