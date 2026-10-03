## Summary

This PR integrates **[CluvexStudio/Aether](https://github.com/CluvexStudio/Aether)** into ZedSecure across shared Kotlin Multiplatform (KMP) domain, Compose UI, Desktop, and Android platforms. It brings high-performance MASQUE (HTTP/3 & HTTP/2), WireGuard, nested WARP-in-WARP (`gool`), MASQUE-in-MASQUE (`mim`), and carrier chaining (Psiphon & Tor) to ZedSecure, while resolving multiple Linux Desktop / Wayland (Hyprland) runtime hangs and TUN routing issues.

---

## What's Changed

### 1. Shared Domain & UI (`shared/`)
- **`AetherProfile.kt` & `ProfileSource.Aether`**: Comprehensive model supporting all Aether protocols (`masque`, `wg`, `gool`, `mim`), noise profiles, transport modes (`h3`, `h2`), scan modes (`fast`, `balanced`, `thorough`, `ironclad`, `verified`), Zero Trust Access parameters (`--team`, `--access-id`, `--access-secret`, `--access-token`, `--gateway`), ECH, TLS fragmentation, and carrier chaining modes (`psiphon`, `psiphon-reverse`, `psiphon-only`, `tor`).
- **`AetherLink.kt`**: Full cross-platform parser and builder for `aether://` URI format with RFC-compliant query and fragment handling.
- **`AetherCoreBuilder.kt`**: Builds exact command-line arguments for the Aether core, setting up port redirection for Psiphon Chain/Reverse and passing custom CLI command overrides.
- **`AetherSheet.kt`**: Material3 Compose bottom sheet with transport selector, scan modes, carrier modes, and an expandable Advanced Settings accordion (custom endpoints, noise profiles, ECH, TLS fragmentation, Zero Trust Access, CLI override).
- **`PingService.kt`**: Added Real Delay latency measurement for Aether profiles by probing custom endpoints and Cloudflare edge gateways (`tcpConnectMillis` + Cloudflare edge fallback).
- **On-Demand Psiphon Downloader**: Added `PsiphonDownloadBus.kt`, `PlatformPsiphonHost.kt`, and `PsiphonDownloadHost.kt` to prompt users with file size confirmation (~20 MB) and animated download progress bar when activating Psiphon for the first time.

### 2. Desktop Platform (`desktop/`)
- **`DesktopAether.kt`**:
  - Implemented Aether lifecycle manager running `bin/linux/aether`.
  - Added adaptive startup timeout (90s for auto-scanning modes, 25s for fixed peers).
  - Added SOCKS5 handshake verification probe (`[0x05, 0x01, 0x00]` -> `0x05` check) to verify data-plane readiness before routing traffic.
  - Added last-error output capture on process exit for detailed UI crash reporting.
- **`DesktopVpn.kt`**:
  - Pointed Aether configuration and identity directory to `~/.config/zedsecure/aether`, preserving `aether-lastconn.json` across restarts for sub-500ms `--quick-reconnect`.
  - Added Cloudflare edge CIDRs (`162.159.192.0/24`, `162.159.193.0/24`, `162.159.195.0/24`, `188.114.96.0/22`, `104.16.0.0/12`, `172.64.0.0/13`) and resolved endpoint hosts to `aetherBypass` in TUN mode to eliminate routing loop packet drops.
  - Fixed connection duration timer for Aether profiles.
- **Wayland / Hyprland Startup & Process Execution Fixes**:
  - `desktop/src/main/kotlin/dev/cluvex/zedsecure/desktop/core/Os.kt`: Made process execution asynchronous with strict timeout to prevent indefinite blocking on stalled standard streams.
  - `desktop/src/main/kotlin/dev/cluvex/zedsecure/desktop/TrayMenu.kt` & `Gui.kt`: Added Wayland detection, async D-Bus tray query with timeout, and bypassed blocking X11 geometry queries.
  - `desktop/src/main/kotlin/dev/cluvex/zedsecure/desktop/core/TunMode.kt`: Added fallback from Polkit `pkexec` (exit code 126 in minimal WMs without an authentication agent) to `sudo` password prompt, with cached timestamp ticket reuse (`sudo -n`).

### 3. Android Platform (`app/`)
- **`AetherController.kt`**: Manages execution of `libaether.so` from `context.applicationInfo.nativeLibraryDir` with persistent storage in `context.filesDir/aether`, 90s startup budget, SOCKS5 handshake probe, and environment variable setup (`AETHER_CONFIG`, `AETHER_MASQUE_CONFIG`, `AETHER_WG_CONFIG`, `TMPDIR`, `SSL_CERT_DIR`).
- **`ZedVpnService.kt` & `StartPlanner.kt`**: Wired `KIND_AETHER` (`"aether"`) to launch `AetherController` and route `HevTunCore` / `ZepTunCore` to `127.0.0.1:11819`.

### 4. Build & CI Improvements (`tools/` & `.github/`)
- **`tools/enhance-deb.sh`**:
  - Injects standalone JRE into `lib/runtime/bin/java` inside the Deb package so users without system Java can run ZedSecure out-of-the-box.
  - Generates `/opt/zedsecure/bin/zedsecure` launcher script with strict classpath (`CP_LIST`) derived from `ZedSecure.cfg` to prevent stale JAR collision.
- **`.github/workflows/build-desktop-deb.yml` & `.github/workflows/build-android-apk.yml`**:
  - Added workflows to build Linux x86_64 `.deb` and multi-arch Android APKs (`arm64-v8a`, `armeabi-v7a`, `x86_64`) with 16KB page-size alignment (`-C link-arg=-Wl,-z,max-page-size=16384`).

---

## Platform & Testing Status

| Platform | Build Status | Testing Status | Notes |
|---|---|---|---|
| **Linux Desktop (x86_64)** | ✅ Verified in CI | ✅ Tested on Arch / Hyprland | Launches out-of-the-box, tray non-blocking, Aether binary executes cleanly |
| **Android (`arm64-v8a`, `armeabi-v7a`, `x86_64`)** | ✅ Verified in CI | ⚠️ Needs Device Testing | Builds pass with 16KB page-size aligned `libaether.so` and `zedcore.aar`; requires testing on real physical Android devices with cellular carriers |
| **Windows & macOS** | ⚠️ Pending Release CI | ⚠️ Not Tested | Domain and CLI builder logic are cross-platform; requires adding native `aether.exe` and macOS `aether` binaries to release workflows |

---

## Notes for Upstream Maintainers / CI Integration

1. **Native Binaries in Release Pipelines**:
   - For `android-release.yml`: Rust build step for `libaether.so` targeting `aarch64-linux-android`, `armv7-linux-androideabi`, and `x86_64-linux-android` (with 16KB page alignment flags) can be added to the `natives` job.
   - For `desktop-release.yml`: Linux x86_64 `aether` static binary can be bundled under `desktop/src/main/resources/bin/linux/aether`.
2. **Debian Packaging**:
   - `tools/enhance-deb.sh` can be hooked after `packageDeb` to ensure the bundled JRE and classpath wrapper are packaged automatically.

---
Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
