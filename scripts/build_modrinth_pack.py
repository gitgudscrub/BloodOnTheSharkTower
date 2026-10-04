#!/usr/bin/env python3
"""Build a deterministic Blood on the Sharktower .mrpack.

Third-party mods are referenced directly from their Modrinth versions.
The locally-built Sharktower JAR is embedded under overrides/mods so the pack
remains a single Modrinth project for players to install and update.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import struct
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

VERSION_API = "https://api.modrinth.com/v2/version/{}"
PROJECT_VERSIONS_API = "https://api.modrinth.com/v2/project/{}/version"
USER_AGENT = "gitgudscrub/BloodOnTheSharkTower (GitHub Actions)"


def fetch_json(url: str) -> object:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def fetch_version(version_id: str) -> dict:
    return fetch_json(VERSION_API.format(version_id))


def fetch_latest_project_version(project_id: str, game_version: str, loader: str) -> dict:
    query = urllib.parse.urlencode(
        {
            "game_versions": json.dumps([game_version]),
            "loaders": json.dumps([loader]),
        }
    )
    versions = fetch_json(f"{PROJECT_VERSIONS_API.format(project_id)}?{query}")
    if not versions:
        raise RuntimeError(
            f"No {loader} version of Modrinth project {project_id} supports Minecraft {game_version}"
        )
    return versions[0]


def primary_file(version: dict) -> dict:
    files = version.get("files") or []
    if not files:
        raise RuntimeError(f"Modrinth version {version.get('id')} has no files")
    return next((item for item in files if item.get("primary")), files[0])


def dependency_entry(spec: dict, game_version: str, loader: str) -> dict:
    if spec.get("version_id"):
        version = fetch_version(spec["version_id"])
    elif spec.get("project_id"):
        version = fetch_latest_project_version(spec["project_id"], game_version, loader)
    else:
        raise RuntimeError(f"Dependency {spec.get('name', '<unnamed>')} has no version_id or project_id")

    print(
        f"Resolved {spec['name']}: {version.get('version_number')} "
        f"(version id {version.get('id')})"
    )

    if spec.get("validate_compatibility"):
        if game_version not in version.get("game_versions", []) or loader not in version.get("loaders", []):
            raise RuntimeError(f"Incompatible performance mod: {spec['name']}")
        if any(dep.get("dependency_type") == "required" for dep in version.get("dependencies", [])):
            raise RuntimeError(f"Review required dependencies before packaging {spec['name']}")

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
    """Write an uncompressed NBT servers.dat containing the Sharktower server.

    Minecraft and the Modrinth App expect servers.dat itself to begin with the
    root TAG_Compound (0x0A). Do not gzip this file: the launcher parses this
    override while installing the pack.
    """
    server = bytearray()
    server += named_nbt_tag(8, "name", nbt_utf(server_name))
    server += named_nbt_tag(8, "ip", nbt_utf(address))
    server += named_nbt_tag(1, "hidden", b"\x00")
    server += b"\x00"

    root = bytearray()
    root += b"\x0a\x00\x00"
    root += bytes([9]) + nbt_utf("servers")
    root += bytes([10]) + struct.pack(">i", 1) + server
    root += b"\x00"

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(bytes(root))


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
    files = [
        dependency_entry(spec, config["minecraft"], "fabric")
        for spec in config["dependencies"]
    ]

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
