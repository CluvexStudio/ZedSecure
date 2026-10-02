#!/usr/bin/env python3
import argparse
import base64
import json
import subprocess
import sys
import urllib.parse

REPO = "CluvexStudio/ZedSecure"
INK = "15170B"
RAMP = ["C7F24E", "B6E23F", "A3D131", "8FBE24"]

WINDOW = "data:image/svg+xml;base64," + base64.b64encode(
    b'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path fill="#C7F24E" '
    b'd="M3 3h18a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zm1 6v10h16V9H4z'
    b'm1-4v2h2V5H5zm3 0v2h2V5H8z"/></svg>'
).decode()

PLATFORMS = [
    ("Android 7+", "Not sure which one? universal installs on every phone. arm64-v8a is smaller for 64-bit phones; armeabi-v7a is for phones with 32-bit Android.", [
        ("-universal.apk", "APK", "universal", "android"),
        ("-arm64-v8a.apk", "APK", "arm64-v8a", "android"),
        ("-armeabi-v7a.apk", "APK", "armeabi-v7a", "android"),
        ("-x86_64.apk", "APK", "x86_64", "android"),
    ]),
    ("Windows 10+", None, [
        ("-x86_64.msi", "Installer", "x64", WINDOW),
        ("-windows-x86_64.zip", "Portable", "x64", WINDOW),
    ]),
    ("macOS 12+", "Apple Silicon for M-series Macs, Intel for older ones. The first launch needs right-click, then Open.", [
        ("-macos-arm64.dmg", "DMG", "Apple Silicon", "apple"),
        ("-macos-x86_64.dmg", "DMG", "Intel", "apple"),
    ]),
    ("Linux", None, [
        ("-x86_64.AppImage", "AppImage", "x64", "appimage"),
        ("-amd64.deb", "DEB", "Debian · Ubuntu", "debian"),
        ("-x86_64.rpm", "RPM", "Fedora · RHEL", "fedora"),
        ("-linux-x86_64.tar.gz", "tar.gz", "x64", "linux"),
    ]),
]


PLAY_NOTICE = [
    "> [!NOTE]",
    "> **Installed from Google Play?** Google signs the Play version with its own key, so this APK will not install over it "
    "(\"App not installed\"). Update from Google Play, or save your configs (Servers › ⋮ › Export all), uninstall the Play "
    "version, then install this APK.",
    "",
    '<div dir="rtl">',
    "",
    "> **نسخهٔ گوگل‌پلی را نصب دارید؟** گوگل نسخهٔ پلی را با کلید خودش امضا می‌کند، برای همین این APK روی آن نصب نمی‌شود "
    "(«App not installed»). یا از گوگل‌پلی آپدیت کنید، یا اول از کانفیگ‌ها خروجی بگیرید (سرورها › ⋮ › خروجی گرفتن از همه)، "
    "نسخهٔ پلی را پاک کنید و بعد این APK را نصب کنید.",
    "",
    "</div>",
]


def gh(*args):
    out = subprocess.run(["gh", *args], capture_output=True, text=True)
    if out.returncode != 0:
        return None
    return out.stdout


def release(tag):
    raw = gh("release", "view", tag, "--repo", REPO, "--json", "tagName,assets,isPrerelease,isDraft")
    return json.loads(raw) if raw else None


def shield(text):
    return urllib.parse.quote(text.replace("-", "--").replace("_", "__"), safe="")


def badge(label, message, color, logo):
    return (
        f"https://img.shields.io/badge/{shield(label)}-{shield(message)}-{color}"
        f"?style=for-the-badge&labelColor={INK}&logoColor={RAMP[0]}"
        f"&logo={urllib.parse.quote(logo, safe='')}"
    )


def download(tag, name):
    return f"https://github.com/{REPO}/releases/download/{tag}/{name}"


def rows(tag, names):
    out = []
    for system, hint, files in PLATFORMS:
        links = []
        for suffix, label, message, logo in files:
            match = next((n for n in names if n.endswith(suffix)), None)
            if match:
                color = RAMP[min(len(links), len(RAMP) - 1)]
                links.append(
                    f'      <a href="{download(tag, match)}">'
                    f'<img src="{badge(label, message, color, logo)}" alt="{label} {message}"></a>'
                )
        if not links:
            continue
        cell = "<br>\n".join(links)
        if hint:
            cell += f"<br>\n      <sub>{hint}</sub>"
        out.append(f"  <tr>\n    <td><b>{system}</b></td>\n    <td>\n{cell}\n    </td>\n  </tr>")
    return out


def nix_row(names):
    if not any(n.endswith("-linux-x86_64.tar.gz") for n in names):
        return []
    return [
        "  <tr>\n    <td><b>NixOS</b></td>\n"
        "    <td><code>nix run github:CluvexStudio/ZedSecure</code></td>\n  </tr>"
    ]


def body(tag, notes, companion):
    data = release(tag)
    if data is None:
        sys.exit(f"no release for {tag}")
    names = [a["name"] for a in data["assets"]]
    downloads = (
        f"https://img.shields.io/github/downloads/{REPO}/{tag}/total"
        f"?style=for-the-badge&label=downloads&labelColor={INK}&color={RAMP[0]}"
        f"&logo=github&logoColor={RAMP[0]}"
    )
    parts = [
        '<div align="center">',
        "",
        f'<a href="https://github.com/{REPO}/releases/tag/{tag}"><img src="{downloads}" alt="Downloads"></a>',
        "",
        "</div>",
        "",
        "**Download for your system:**",
        "",
        '<div dir="rtl">',
        "",
        "**بر اساس سیستم‌عامل خود دانلود کنید:**",
        "",
        "</div>",
        "",
        "<table>",
        "  <thead>",
        '    <tr><th align="left">System</th><th align="left">Download</th></tr>',
        "  </thead>",
        "  <tbody>",
        *rows(tag, names),
        *nix_row(names),
        "  </tbody>",
        "</table>",
    ]
    if any(n.endswith(".apk") for n in names):
        parts += ["", *PLAY_NOTICE]
    if notes:
        parts += ["", "### What's new", "", notes.strip()]
    footer = []
    sums = [n for n in names if n.startswith("SHA256SUMS")]
    if sums:
        footer.append("Checksums: " + ", ".join(f"[{s}]({download(tag, s)})" for s in sums))
    if companion:
        other = release(companion)
        if other is not None:
            kind = "Desktop" if companion.startswith("desktop-") else "Android"
            note = " (pre-release)" if other["isPrerelease"] else ""
            footer.append(f"{kind}: [{companion}](https://github.com/{REPO}/releases/tag/{companion}){note}")
    if footer:
        parts += ["", "<sub>" + " · ".join(footer) + "</sub>"]
    return "\n".join(parts) + "\n"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("tag")
    parser.add_argument("--notes", help="markdown file with the changes")
    parser.add_argument("--companion", help="the matching Android or desktop tag")
    parser.add_argument("--apply", action="store_true", help="write the notes to the release")
    args = parser.parse_args()
    notes = open(args.notes, encoding="utf-8").read() if args.notes else ""
    text = body(args.tag, notes, args.companion)
    if not args.apply:
        sys.stdout.write(text)
        return
    done = subprocess.run(
        ["gh", "release", "edit", args.tag, "--repo", REPO, "--notes-file", "-"],
        input=text, text=True,
    )
    sys.exit(done.returncode)


if __name__ == "__main__":
    main()
