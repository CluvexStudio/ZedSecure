# ZX VPN diagnostic tools

These are additive developer/operator tools. They do not change the VPN engine, connection lifecycle, profiles, or existing app behavior.

## Python: offline config audit

Run against a local text export:

```sh
python3 tools/diagnostics/audit_configs.py exported-configs.txt
python3 -m unittest tools/diagnostics/test_audit_configs.py
```

The script never prints config URIs or credentials. It checks obvious URI/host/port syntax only; it does not connect to servers or claim that a configuration is safe or working.

## C: URI shape checker

Build with a C11 compiler:

```sh
cc -std=c11 -Wall -Wextra -Werror -O2 tools/diagnostics/config_uri_check.c -o zx-config-check
./zx-config-check < exported-configs.txt
```

The checker reports line numbers and broad outcomes only, not secret-bearing input values. It is a syntax pre-check, not a replacement for the app's protocol parsers.

## Java: Android network metadata report

`app/src/main/java/dev/cluvex/zedsecure/core/NetworkDiagnostics.java` exposes `NetworkDiagnostics.createReport(Context)` for Android diagnostics UI or support flows. It reports local transport, validated connectivity, DNS-server count, route-family counts, and proxy presence. It does not probe remote hosts, enumerate DNS addresses, or run a VPN leak test.

## Scope and limitations

- The C and Python tools are optional and are not part of the APK build/runtime path.
- The Java helper is read-only and does not alter network settings.
- Syntax plausibility is not proof of server reachability, security, or successful VPN traffic.
- No Aether/WARP integration is included.
