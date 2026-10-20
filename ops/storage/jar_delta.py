#!/usr/bin/env python3
"""Transfer only changed jar entries; verify complete entry hashes before publishing."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def inventory(path):
    with zipfile.ZipFile(path) as jar:
        return {entry.filename: hashlib.sha256(jar.read(entry)).hexdigest() for entry in jar.infolist()}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["inventory", "pack", "apply"])
    parser.add_argument("jar", type=Path)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--patch", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.mode == "inventory":
        print(json.dumps(inventory(args.jar)))
    elif args.mode == "pack":
        base = json.loads(args.baseline.read_text())
        expected = inventory(args.jar)
        with zipfile.ZipFile(args.jar) as source, zipfile.ZipFile(args.patch, "w", zipfile.ZIP_DEFLATED) as patch:
            patch.writestr("expected.json", json.dumps(expected))
            patch.writestr("baseline.json", json.dumps(base))
            for name, sha in expected.items():
                if base.get(name) != sha:
                    patch.writestr("entries/" + name, source.read(name))
        print(json.dumps({"patchBytes": args.patch.stat().st_size, "entries": len(expected)}))
    else:
        if args.output.exists():
            raise ValueError("Refusing to overwrite output")
        with zipfile.ZipFile(args.jar) as old, zipfile.ZipFile(args.patch) as patch:
            expected = json.loads(patch.read("expected.json"))
            if inventory(args.jar) != json.loads(patch.read("baseline.json")):
                raise ValueError("Running baseline changed; patch cannot be applied")
            names = set(patch.namelist())
            with zipfile.ZipFile(args.output, "w") as target:
                for name in expected:
                    delta = "entries/" + name
                    if delta in names:
                        target.writestr(name, patch.read(delta), compress_type=zipfile.ZIP_STORED)
                    else:
                        target.writestr(old.getinfo(name), old.read(name))
            if inventory(args.output) != expected:
                raise ValueError("Final artifact verification failed")
        print(json.dumps({"verifiedEntries": len(expected), "artifactBytes": args.output.stat().st_size}))


if __name__ == "__main__":
    main()
