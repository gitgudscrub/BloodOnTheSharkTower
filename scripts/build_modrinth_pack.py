#!/usr/bin/env python3
"""Build a deterministic Blood on the Sharktower .mrpack.

Third-party mods are referenced directly from their Modrinth versions.
The locally-built Sharktower JAR is embedded under overrides/mods so the pack
remains a single Modrinth project for players to install and update.

Optionally (--packwiz-out) the same pack is also written as a packwiz pack,
which packwiz-installer reads on every launch to auto-update a Prism Launcher
instance, and (--prism-out) as a ready-to-import Prism instance zip whose
pre-launch command runs that update. See docs/AUTO_UPDATE.md.
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


def resolve_version(spec: dict, game_version: str, loader: str) -> dict:
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
    return version


def validate_compatibility(spec: dict, version: dict, game_version: str, loader: str, packed_projects: set) -> None:
    """Fail the build if a pinned mod does not fit the pack.

    A mod may require other mods only if those mods are themselves in the pack
    (for example Entity Culling requires Fabric API, Mod Menu requires Text
    Placeholder API). Anything else must be reviewed by a human first.
    """
    if not spec.get("validate_compatibility"):
        return
    if game_version not in version.get("game_versions", []) or loader not in version.get("loaders", []):
        raise RuntimeError(f"{spec['name']} {version.get('version_number')} does not support {loader} {game_version}")
    missing = [
        dep.get("project_id")
        for dep in version.get("dependencies", [])
        if dep.get("dependency_type") == "required" and dep.get("project_id") not in packed_projects
    ]
    if missing:
        raise RuntimeError(
            f"{spec['name']} requires Modrinth project(s) {missing} that are not in the pack; "
            "add them to modrinth/pack-config.json or drop the mod"
        )


def dependency_entry(spec: dict, version: dict) -> dict:
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


def toml_str(value: str) -> str:
    """A TOML basic string."""
    escaped = value.replace("\\", "\\\\").replace('"', '\\"')
    return f'"{escaped}"'


def packwiz_side(spec: dict) -> str:
    client = spec.get("client", "required") != "unsupported"
    server = spec.get("server", "required") != "unsupported"
    if client and server:
        return "both"
    return "client" if client else "server"


def slug(name: str) -> str:
    return "".join(ch if ch.isalnum() else "-" for ch in name.lower()).strip("-")


def write_packwiz(
    out: Path,
    config: dict,
    version: str,
    specs: list,
    versions: list,
    jar: Path,
    jar_url: str,
    overrides: Path,
) -> None:
    """Write pack.toml + index.toml + one .pw.toml per mod.

    Mods (the Sharktower JAR too) are metafiles pointing at a download URL, so
    no JAR is stored next to pack.toml. Every other override file is copied
    as-is and marked preserve, so packwiz only creates it when missing and a
    player's own edits (for example their server list) survive updates.
    """
    if out.exists():
        shutil.rmtree(out)
    (out / "mods").mkdir(parents=True)
    entries = []

    def add(rel: str, text: str | None = None, src: Path | None = None, metafile: bool = False) -> None:
        target = out / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        if text is not None:
            target.write_text(text, encoding="utf-8", newline="\n")
        else:
            shutil.copy2(src, target)
        entry = f"[[files]]\nfile = {toml_str(rel)}\nhash = {toml_str(sha(target, 'sha256'))}\n"
        entry += "metafile = true\n" if metafile else "preserve = true\n"
        entries.append((rel, entry))

    for spec, mod in zip(specs, versions):
        file = primary_file(mod)
        add(
            f"mods/{slug(spec['name'])}.pw.toml",
            text=(
                f"name = {toml_str(spec['name'])}\n"
                f"filename = {toml_str(file['filename'])}\n"
                f"side = {toml_str(packwiz_side(spec))}\n\n"
                "[download]\n"
                f"url = {toml_str(file['url'])}\n"
                'hash-format = "sha512"\n'
                f"hash = {toml_str(file['hashes']['sha512'])}\n\n"
                "[update.modrinth]\n"
                f"mod-id = {toml_str(mod['project_id'])}\n"
                f"version = {toml_str(mod['id'])}\n"
            ),
            metafile=True,
        )

    add(
        "mods/blood-on-the-sharktower.pw.toml",
        text=(
            f"name = {toml_str(config['name'])}\n"
            f"filename = {toml_str(f'blood-on-the-sharktower-{version}.jar')}\n"
            'side = "both"\n\n'
            "[download]\n"
            f"url = {toml_str(jar_url)}\n"
            'hash-format = "sha512"\n'
            f"hash = {toml_str(sha(jar, 'sha512'))}\n"
        ),
        metafile=True,
    )

    for item in sorted(overrides.rglob("*")):
        rel = item.relative_to(overrides).as_posix()
        if item.is_file() and not rel.startswith("mods/"):
            add(rel, src=item)

    index = 'hash-format = "sha256"\n\n' + "\n".join(entry for _, entry in sorted(entries))
    (out / "index.toml").write_text(index, encoding="utf-8", newline="\n")
    (out / "pack.toml").write_text(
        f"name = {toml_str(config['name'])}\n"
        f"version = {toml_str(version)}\n"
        'pack-format = "packwiz:1.1.0"\n\n'
        "[index]\n"
        'file = "index.toml"\n'
        'hash-format = "sha256"\n'
        f"hash = {toml_str(sha(out / 'index.toml', 'sha256'))}\n\n"
        "[versions]\n"
        f"minecraft = {toml_str(config['minecraft'])}\n"
        f"fabric = {toml_str(config['fabric_loader'])}\n",
        encoding="utf-8",
        newline="\n",
    )
    print(f"Wrote packwiz pack to {out} ({len(entries)} files)")


def write_prism_instance(out: Path, config: dict, pack_url: str, bootstrap: Path, icon: Path | None) -> None:
    """Write a Prism Launcher instance zip (Add Instance > Import).

    The instance only knows the pack URL; every launch runs packwiz-installer
    first, which installs or updates the mods and, when the pack moves to a
    new Minecraft or Fabric version, the instance's components too. So one
    import keeps working across all future releases.
    """
    folder = "BloodOnTheSharktower"
    # Prism reads instance.cfg with QSettings: the value is a quoted string,
    # so the inner quotes (needed when the Java path has spaces) are escaped.
    command = f'"$INST_JAVA" -jar packwiz-installer-bootstrap.jar {pack_url}'
    command_ini = '"' + command.replace("\\", "\\\\").replace('"', '\\"') + '"'
    instance_cfg = (
        "[General]\n"
        "ConfigVersion=1.2\n"
        "InstanceType=OneSix\n"
        f"name={config['name']}\n"
        "iconKey=blood_on_the_sharktower\n"
        "OverrideCommands=true\n"
        f"PreLaunchCommand={command_ini}\n"
        "OverrideMemory=true\n"
        "MinMemAlloc=1024\n"
        "MaxMemAlloc=4096\n"
    )
    mmc_pack = {
        "formatVersion": 1,
        "components": [
            {"uid": "net.minecraft", "version": config["minecraft"], "important": True},
            {"uid": "net.fabricmc.fabric-loader", "version": config["fabric_loader"]},
        ],
    }
    out.parent.mkdir(parents=True, exist_ok=True)
    if out.exists():
        out.unlink()
    with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(f"{folder}/instance.cfg", instance_cfg)
        archive.writestr(f"{folder}/mmc-pack.json", json.dumps(mmc_pack, indent=2) + "\n")
        archive.write(bootstrap, f"{folder}/minecraft/packwiz-installer-bootstrap.jar")
        if icon is not None and icon.is_file():
            archive.write(icon, f"{folder}/blood_on_the_sharktower.png")
    print(f"Wrote Prism instance {out} (pack URL {pack_url})")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--config", default="modrinth/pack-config.json", type=Path)
    parser.add_argument("--overrides", default="modrinth/overrides", type=Path)
    parser.add_argument("--packwiz-out", type=Path, help="also write a packwiz pack to this folder")
    parser.add_argument("--jar-url", help="download URL of the Sharktower JAR (with --packwiz-out)")
    parser.add_argument("--prism-out", type=Path, help="also write a Prism instance zip here")
    parser.add_argument("--pack-url", help="public URL of pack.toml (with --prism-out)")
    parser.add_argument("--bootstrap", type=Path, help="packwiz-installer-bootstrap.jar (with --prism-out)")
    parser.add_argument(
        "--icon",
        default="src/main/resources/assets/blood_on_the_sharktower/icon.png",
        type=Path,
        help="instance icon (with --prism-out)",
    )
    args = parser.parse_args()
    if args.packwiz_out and not args.jar_url:
        raise SystemExit("--packwiz-out needs --jar-url")
    if args.prism_out and not (args.pack_url and args.bootstrap and args.bootstrap.is_file()):
        raise SystemExit("--prism-out needs --pack-url and an existing --bootstrap JAR")

    if not args.jar.is_file():
        raise SystemExit(f"Built Sharktower JAR not found: {args.jar}")

    config = json.loads(args.config.read_text(encoding="utf-8"))
    specs = config["dependencies"]
    versions = [resolve_version(spec, config["minecraft"], "fabric") for spec in specs]
    packed_projects = {version.get("project_id") for version in versions}
    for spec, version in zip(specs, versions):
        validate_compatibility(spec, version, config["minecraft"], "fabric", packed_projects)
    files = [dependency_entry(spec, version) for spec, version in zip(specs, versions)]

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

    if args.packwiz_out:
        # Same override folder the .mrpack got (servers.dat, config); the
        # Sharktower JAR itself is a download metafile, not a copied file.
        write_packwiz(args.packwiz_out, config, args.version, specs, versions, args.jar, args.jar_url, overrides)
    if args.prism_out:
        write_prism_instance(args.prism_out, config, args.pack_url, args.bootstrap, args.icon)


if __name__ == "__main__":
    main()
