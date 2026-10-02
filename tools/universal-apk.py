#!/usr/bin/env python3
import sys
import zipfile


def main():
    if len(sys.argv) < 4:
        sys.exit("usage: universal-apk.py <base.apk> <out.apk> <other-abi.apk>...")
    base, out, others = sys.argv[1], sys.argv[2], sys.argv[3:]
    seen = set()
    with zipfile.ZipFile(base) as src, zipfile.ZipFile(out, "w") as dst:
        for info in src.infolist():
            if info.filename.startswith("META-INF/") and info.filename.endswith((".SF", ".RSA", ".EC", ".DSA")):
                continue
            data = src.read(info.filename)
            info.extra = b""
            dst.writestr(info, data)
            seen.add(info.filename)
        for other in others:
            with zipfile.ZipFile(other) as extra:
                added = 0
                for info in extra.infolist():
                    if not info.filename.startswith("lib/") or info.filename in seen:
                        continue
                    data = extra.read(info.filename)
                    info.extra = b""
                    dst.writestr(info, data)
                    seen.add(info.filename)
                    added += 1
                if added == 0:
                    sys.exit(f"{other} added no native libraries")
    print(f"{out}: {len(seen)} entries")


if __name__ == "__main__":
    main()
