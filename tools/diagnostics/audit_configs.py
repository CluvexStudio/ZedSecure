#!/usr/bin/env python3
"""Offline ZX VPN config-export audit. Never prints credential-bearing URI contents."""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from urllib.parse import urlsplit

SUPPORTED = {
    "vless", "vmess", "trojan", "ss", "socks", "socks5",
    "http", "https", "wireguard", "wg", "hysteria2", "hy2", "tuic", "ssh",
}
URI_RE = re.compile(r"(?i)\b(?:vless|vmess|trojan|ss|socks5?|https?|wireguard|wg|hysteria2|hy2|tuic|ssh)://[^\s<>\"']+")
CONTROL_RE = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]")


def audit_line(line: str) -> list[str]:
    issues: list[str] = []
    value = line.strip()
    if not value:
        return issues
    if CONTROL_RE.search(value):
        issues.append("contains control characters")
    match = URI_RE.search(value)
    if not match:
        issues.append("no supported proxy URI found")
        return issues
    uri = match.group(0).rstrip(".,;)")
    try:
        parsed = urlsplit(uri)
        scheme = parsed.scheme.lower()
        if scheme not in SUPPORTED:
            issues.append("unsupported scheme")
        if not parsed.netloc and scheme not in {"vmess"}:
            issues.append("missing authority/host")
        if parsed.hostname:
            try:
                port = parsed.port
            except ValueError:
                issues.append("invalid port")
            else:
                if port is not None and not (1 <= port <= 65535):
                    issues.append("port outside valid range")
        elif scheme not in {"vmess"}:
            issues.append("host could not be parsed")
        if scheme in {"vless", "trojan", "ssh"} and not parsed.username:
            issues.append("missing user/credential component")
    except ValueError:
        issues.append("URI parser rejected the entry")
    return list(dict.fromkeys(issues))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("file", type=Path, help="local text export; it is read only")
    args = parser.parse_args()
    try:
        text = args.file.read_text(encoding="utf-8-sig")
    except (OSError, UnicodeError) as exc:
        print(f"error: could not read input ({type(exc).__name__})", file=sys.stderr)
        return 2

    checked = warnings = 0
    for number, line in enumerate(text.splitlines(), 1):
        if not line.strip():
            continue
        checked += 1
        issues = audit_line(line)
        if issues:
            warnings += 1
            print(f"line {number}: " + "; ".join(issues))
        else:
            print(f"line {number}: syntax looks plausible")
    print(f"summary: {checked} non-empty line(s), {warnings} line(s) to review")
    print("Note: syntax checks do not prove that a server is reachable or trustworthy.")
    return 1 if warnings else 0


if __name__ == "__main__":
    raise SystemExit(main())
