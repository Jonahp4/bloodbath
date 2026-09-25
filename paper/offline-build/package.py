#!/usr/bin/env python3
"""Packages the Bloodbath plugin jar and resource pack zip reproducibly (fixed timestamps,
sorted entries), so the pack's SHA-1 only changes when its contents do."""
import argparse
import hashlib
import os
import zipfile

FIXED_TIME = (1980, 1, 1, 0, 0, 0)


def add(zf, name, data, compress=True):
    info = zipfile.ZipInfo(name, FIXED_TIME)
    info.compress_type = zipfile.ZIP_DEFLATED if compress else zipfile.ZIP_STORED
    info.external_attr = 0o644 << 16
    zf.writestr(info, data, compresslevel=9 if compress else None)


def files_under(root):
    out = []
    for base, dirs, files in os.walk(root):
        dirs.sort()
        for f in files:
            path = os.path.join(base, f)
            out.append((os.path.relpath(path, root).replace(os.sep, "/"), path))
    return sorted(out)


def read(path):
    with open(path, "rb") as f:
        return f.read()


def main():
    p = argparse.ArgumentParser()
    for arg in ("version", "classes", "resources", "pack", "license", "jar", "pack_zip"):
        p.add_argument("--" + arg.replace("_", "-"), required=True)
    p.add_argument("--pack-dir", help="also write the pack here as <sha1>.zip, the name the plugin's mirrors use")
    a = p.parse_args()
    for out in (a.jar, a.pack_zip):
        os.makedirs(os.path.dirname(out), exist_ok=True)

    with zipfile.ZipFile(a.pack_zip, "w") as zf:
        for name, path in files_under(a.pack):
            add(zf, name, read(path))
    pack = read(a.pack_zip)

    with zipfile.ZipFile(a.jar, "w") as zf:
        add(zf, "META-INF/MANIFEST.MF", b"Manifest-Version: 1.0\r\n\r\n")
        for name, path in files_under(a.resources):
            data = read(path)
            if name == "plugin.yml":
                data = data.replace(b"${version}", a.version.encode())
            add(zf, name, data)
        add(zf, "LICENSE_bloodbath", read(a.license))
        add(zf, "resourcepack.zip", pack, compress=False)  # already compressed
        for name, path in files_under(a.classes):
            add(zf, name, read(path))

    if a.pack_dir:
        # One file per pack build, never rewritten: a server that checked a mirror file can rely on it
        # staying exactly that file. The newest few are kept for servers still running older builds.
        os.makedirs(a.pack_dir, exist_ok=True)
        named = os.path.join(a.pack_dir, hashlib.sha1(pack).hexdigest() + ".zip")
        if not os.path.exists(named):
            with open(named, "wb") as f:
                f.write(pack)
        builds = sorted((os.path.join(a.pack_dir, n) for n in os.listdir(a.pack_dir) if n.endswith(".zip")),
                        key=os.path.getmtime, reverse=True)
        for old in builds[8:]:
            os.remove(old)
        print(f"  {os.path.relpath(named)}  (mirror copy)")

    for out in (a.jar, a.pack_zip):
        digest = hashlib.sha1(read(out)).hexdigest()
        print(f"  {os.path.relpath(out)}  {os.path.getsize(out) // 1024} KB  sha1 {digest}")


if __name__ == "__main__":
    main()
