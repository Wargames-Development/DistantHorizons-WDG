#!/usr/bin/env python3

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
VERIFIER = (
    ROOT
    / "buildSrc"
    / "src"
    / "main"
    / "java"
    / "com"
    / "wargamesdevelopment"
    / "distanthorizons"
    / "gradle"
    / "RuntimeArtifactVerifier.java"
)

POLICIES = {
    "gtnhlib": {
        "prefix": "GTNHLIB",
        "primary_mod_id": "gtnhlib",
        "mod_ids": {"gtnhlib"},
        "source_required": True,
        "nested_path": "fplib_deploader.jar",
    },
    "angelica": {
        "prefix": "ANGELICA",
        "primary_mod_id": "angelica",
        "mod_ids": {"angelica", "notfine", "embeddium", "mcpatcherforge"},
        "source_required": False,
        "nested_path": None,
    },
    "unimixins": {
        "prefix": "UNIMIXINS",
        "primary_mod_id": "unimixins",
        "mod_ids": {
            "unimixins",
            "unimixins-mixin",
            "unimixins-compat",
            "mixingasm",
            "spongemixins",
            "mixinbooterlegacy",
            "gasstation",
            "gtnhmixins",
            "mixinextras",
        },
        "source_required": False,
        "nested_path": None,
    },
}


def sha256_bytes(data):
    return hashlib.sha256(data).hexdigest()


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def read_string(text, name):
    pattern = re.compile(
        rf'public static final String {re.escape(name)}\s*=\s*"([^"]*)"\s*;',
        re.DOTALL,
    )
    match = pattern.search(text)
    if match is None:
        raise SystemExit(f"Missing Java string constant: {name}")
    return match.group(1)


def read_long(text, name):
    pattern = re.compile(
        rf"public static final long {re.escape(name)}\s*=\s*([0-9_]+)L\s*;"
    )
    match = pattern.search(text)
    if match is None:
        raise SystemExit(f"Missing Java long constant: {name}")
    return int(match.group(1).replace("_", ""))


def replace_string(text, name, value):
    pattern = re.compile(
        rf'(public static final String {re.escape(name)}\s*=\s*)"[^"]*"(\s*;)',
        re.DOTALL,
    )
    updated, count = pattern.subn(
        lambda match: match.group(1) + json.dumps(value) + match.group(2),
        text,
        count=1,
    )
    if count != 1:
        raise SystemExit(f"Could not update Java string constant: {name}")
    return updated


def replace_long(text, name, value):
    pattern = re.compile(
        rf"(public static final long {re.escape(name)}\s*=\s*)[0-9_]+L(\s*;)"
    )
    formatted = f"{value:_}L"
    updated, count = pattern.subn(
        lambda match: match.group(1) + formatted + match.group(2),
        text,
        count=1,
    )
    if count != 1:
        raise SystemExit(f"Could not update Java long constant: {name}")
    return updated


def parse_mcmod(raw):
    decoded = raw.decode("utf-8-sig")

    try:
        parsed = json.loads(decoded)
        if isinstance(parsed, dict):
            parsed = parsed.get("modList", [parsed])
        if isinstance(parsed, list):
            identities = {}
            for entry in parsed:
                if not isinstance(entry, dict):
                    continue
                mod_id = str(entry.get("modid", "")).strip()
                version = str(entry.get("version", "")).strip()
                if mod_id:
                    identities[mod_id] = version
            if identities:
                return identities
    except json.JSONDecodeError:
        pass

    identities = {}
    for object_text in re.findall(r"\{(.*?)\}", decoded, re.DOTALL):
        fields = dict(
            re.findall(
                r'"([^"]+)"\s*:\s*"([^"]*)"',
                object_text,
            )
        )
        mod_id = fields.get("modid", "").strip()
        if mod_id:
            identities[mod_id] = fields.get("version", "").strip()

    if not identities:
        raise SystemExit("mcmod.info does not contain readable mod identities")

    return identities


def inspect_jar(path):
    if not path.is_file():
        raise SystemExit(f"JAR not found: {path}")

    with zipfile.ZipFile(path) as archive:
        bad_member = archive.testzip()
        if bad_member is not None:
            raise SystemExit(f"Corrupt JAR member: {bad_member}")

        try:
            identities = parse_mcmod(archive.read("mcmod.info"))
        except KeyError as error:
            raise SystemExit("JAR does not contain mcmod.info") from error

        nested = []
        for info in archive.infolist():
            if info.is_dir() or not info.filename.lower().endswith(".jar"):
                continue
            data = archive.read(info)
            nested.append(
                {
                    "path": info.filename,
                    "size": len(data),
                    "sha256": sha256_bytes(data),
                }
            )

    return {
        "filename": path.name,
        "size": path.stat().st_size,
        "sha256": sha256_file(path),
        "identities": identities,
        "nested": nested,
    }


def validate_source_zip(path):
    if not path.is_file():
        raise SystemExit(f"Source ZIP not found: {path}")

    with zipfile.ZipFile(path) as archive:
        bad_member = archive.testzip()
        if bad_member is not None:
            raise SystemExit(f"Corrupt source ZIP member: {bad_member}")


def text_targets(commands_dir):
    direct = [
        ROOT / "build.gradle.kts",
        ROOT / "dependencies.gradle",
        ROOT / "README.md",
        ROOT / "COMPILING.md",
        ROOT / "SETUP.md",
    ]

    roots = [
        ROOT / "buildSrc" / "src" / "main",
        ROOT / "buildSrc" / "src" / "test",
        ROOT / "docs",
        ROOT / "scripts",
    ]

    for path in direct:
        if path.is_file():
            yield path

    allowed = {
        ".java",
        ".kt",
        ".kts",
        ".gradle",
        ".md",
        ".sh",
        ".py",
        ".properties",
        ".txt",
        ".json",
        ".yml",
        ".yaml",
    }

    for root in roots:
        if not root.is_dir():
            continue
        for path in root.rglob("*"):
            if path.is_file() and path.suffix.lower() in allowed:
                yield path

    if commands_dir is not None and commands_dir.is_dir():
        for path in commands_dir.rglob("*"):
            if path.is_file() and path.suffix.lower() in allowed:
                yield path


def update_text_references(replacements, commands_dir):
    seen = set()

    for path in text_targets(commands_dir):
        resolved = path.resolve()
        if resolved == VERIFIER.resolve() or resolved in seen:
            continue
        seen.add(resolved)

        try:
            original = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue

        updated = original
        for old, new in replacements:
            if old:
                updated = updated.replace(old, new)

        if updated != original:
            path.write_text(updated, encoding="utf-8")


def show_catalog():
    text = VERIFIER.read_text(encoding="utf-8")

    for artifact, policy in POLICIES.items():
        prefix = policy["prefix"]
        print(f"{artifact}:")
        print(f"  version:  {read_string(text, prefix + '_VERSION')}")
        print(f"  filename: {read_string(text, prefix + '_FILENAME')}")
        print(f"  size:     {read_long(text, prefix + '_SIZE')}")
        print(f"  SHA-256:  {read_string(text, prefix + '_SHA256')}")


def update_artifact(args):
    policy = POLICIES[args.artifact]
    prefix = policy["prefix"]

    jar = args.jar.expanduser().resolve()
    inspected = inspect_jar(jar)

    actual_mod_ids = set(inspected["identities"])
    expected_mod_ids = set(policy["mod_ids"])

    if actual_mod_ids != expected_mod_ids:
        raise SystemExit(
            "Mod identity structure changed.\n"
            f"Expected: {sorted(expected_mod_ids)}\n"
            f"Actual:   {sorted(actual_mod_ids)}\n"
            "This release requires a deliberate verifier review."
        )

    primary = policy["primary_mod_id"]
    version = inspected["identities"].get(primary, "").strip()
    if not version:
        raise SystemExit(f"Primary mod identity has no version: {primary}")

    nested = inspected["nested"]
    expected_nested_path = policy["nested_path"]

    if expected_nested_path is None:
        if nested:
            raise SystemExit(
                "This artifact unexpectedly contains nested JARs:\n"
                + "\n".join(item["path"] for item in nested)
                + "\nA deliberate verifier review is required."
            )
    else:
        if len(nested) != 1 or nested[0]["path"] != expected_nested_path:
            raise SystemExit(
                "Nested-JAR structure changed.\n"
                f"Expected: [{expected_nested_path}]\n"
                f"Actual:   {[item['path'] for item in nested]}\n"
                "A deliberate verifier review is required."
            )

    source = None
    source_sha = None

    if policy["source_required"]:
        if args.source is None:
            raise SystemExit(
                f"{args.artifact} requires its matching source ZIP"
            )
        source = args.source.expanduser().resolve()
        validate_source_zip(source)
        source_sha = sha256_file(source)
    elif args.source is not None:
        raise SystemExit(
            f"{args.artifact} does not use a source ZIP in this contract"
        )

    original = VERIFIER.read_text(encoding="utf-8")
    updated = original

    old_version = read_string(original, prefix + "_VERSION")
    old_filename = read_string(original, prefix + "_FILENAME")
    old_size = read_long(original, prefix + "_SIZE")
    old_sha = read_string(original, prefix + "_SHA256")

    updated = replace_string(updated, prefix + "_VERSION", version)
    updated = replace_string(
        updated,
        prefix + "_FILENAME",
        inspected["filename"],
    )
    updated = replace_long(updated, prefix + "_SIZE", inspected["size"])
    updated = replace_string(
        updated,
        prefix + "_SHA256",
        inspected["sha256"],
    )

    replacements = [
        (old_version, version),
        (old_filename, inspected["filename"]),
        (str(old_size), str(inspected["size"])),
        (f"{old_size:,}", f"{inspected['size']:,}"),
        (old_sha, inspected["sha256"]),
    ]

    if policy["source_required"]:
        old_source_filename = read_string(
            original,
            prefix + "_SOURCE_FILENAME",
        )
        old_source_sha = read_string(
            original,
            prefix + "_SOURCE_SHA256",
        )

        updated = replace_string(
            updated,
            prefix + "_SOURCE_FILENAME",
            source.name,
        )
        updated = replace_string(
            updated,
            prefix + "_SOURCE_SHA256",
            source_sha,
        )

        replacements.extend(
            [
                (old_source_filename, source.name),
                (old_source_sha, source_sha),
            ]
        )

        old_nested_size = read_long(
            original,
            prefix + "_NESTED_JAR_SIZE",
        )
        old_nested_sha = read_string(
            original,
            prefix + "_NESTED_JAR_SHA256",
        )

        updated = replace_long(
            updated,
            prefix + "_NESTED_JAR_SIZE",
            nested[0]["size"],
        )
        updated = replace_string(
            updated,
            prefix + "_NESTED_JAR_SHA256",
            nested[0]["sha256"],
        )

        replacements.extend(
            [
                (str(old_nested_size), str(nested[0]["size"])),
                (f"{old_nested_size:,}", f"{nested[0]['size']:,}"),
                (old_nested_sha, nested[0]["sha256"]),
            ]
        )

        old_build_script = (
            ROOT
            / "scripts"
            / f"build-gtnhlib-{old_version}.sh"
        )
        new_build_script = (
            ROOT
            / "scripts"
            / f"build-gtnhlib-{version}.sh"
        )

        if (
            old_build_script.is_file()
            and old_build_script != new_build_script
        ):
            if new_build_script.exists():
                raise SystemExit(
                    f"Target build script already exists: {new_build_script}"
                )
            old_build_script.rename(new_build_script)

    VERIFIER.write_text(updated, encoding="utf-8")
    update_text_references(replacements, args.commands_dir)

    print(f"Updated {args.artifact}")
    print(f"Version:  {version}")
    print(f"Filename: {inspected['filename']}")
    print(f"Bytes:    {inspected['size']}")
    print(f"SHA-256:  {inspected['sha256']}")

    if source is not None:
        print(f"Source:   {source.name}")
        print(f"Source SHA-256: {source_sha}")


def main():
    parser = argparse.ArgumentParser(
        description=(
            "Inspect and update the pinned combined-client dependency "
            "identity while preserving structural verifier policy."
        )
    )

    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("show")

    update = subparsers.add_parser("update")
    update.add_argument(
        "artifact",
        choices=sorted(POLICIES),
    )
    update.add_argument("jar", type=Path)
    update.add_argument("source", type=Path, nargs="?")
    update.add_argument("--commands-dir", type=Path)

    args = parser.parse_args()

    if args.command == "show":
        show_catalog()
    else:
        update_artifact(args)


if __name__ == "__main__":
    main()
