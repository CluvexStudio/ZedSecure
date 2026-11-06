# Aether integration

The Servers → Add menu includes **Aether (WARP)**. Create a profile, select it, and
use the existing Connect button. Long-press to edit or share it. Settings survive
restarts and round-trip through `zedsecure://` links and the existing vault.

Supported transports: MASQUE over HTTP/3 or HTTP/2, WireGuard, WireGuard over MASQUE
(`gool`), WireGuard in WireGuard (`gool-classic`), and MASQUE in MASQUE (`mim`).
The sheet exposes scan mode, IP family, noise profile, an optional numeric peer,
TLS fragmentation, ECH and gateway certificate verification (on by default).
Classic gool discovers both peers automatically. These profiles run directly on
Aether, outside the app's Xray/sing-box and cross-chain builders.

## Packaging

`tools/get-cores.sh` also calls `tools/get-aether.sh`. It downloads **v2.4.0** from
[CluvexStudio/Aether](https://github.com/CluvexStudio/Aether/releases/tag/v2.4.0)
and verifies each archive against the committed `tools/aether-sha256.txt` before
installing it. The hashes were checked against the release asset SHA-256 digests.
Linux uses the x86_64 musl build. Windows uses x86_64; macOS has separate arm64 and
x86_64 archives. No runtime downloads or updates are performed.

Android packages the PIE executable as `libaether.so` in each of arm64-v8a,
armeabi-v7a and x86_64. It is executed from `applicationInfo.nativeLibraryDir`,
with the existing legacy native-library extraction enabled. It is not JNI and
must never be loaded with `System.loadLibrary`. The upstream release targets
Android API 29; only Aether is gated to Android 10+, retaining the app's API 24
minimum. The RELR check exempts this API 29 executable, as it already does zeptun.

Upstream `LICENSES` accompanies each core: Android assets under `aether/<abi>`,
desktop resources under `bin/<os>/aether-licenses`. Optional upstream Tor and
Psiphon helper binaries are not copied: this adapter offers Aether's WARP
transports, with the app's existing Tor and Psiphon engines kept independent.

## Lifecycle and routing

The common JVM process owner clears inherited `AETHER_*` settings and constructs
an argument list, without a shell. It uses a loopback SOCKS listener on a free
port. The application waits for a real SOCKS5 greeting (up to 180 seconds), not
just an open TCP port: Aether reserves its listener before its scan completes.
An error, cancellation or timeout reaps the process; disconnect stops Aether
alongside the front core and the TUN bridge. The Android stats loop and desktop
process watcher detect unexpected exits.

On Android the existing per-app VPN rules exclude the application's UID, including
its Aether child. This is essential because the executable has no `protect(fd)`
callback. The existing routing front, local proxy authentication, TUN bridge and
kill switch remain in use. Proxy-only mode is also supported.

Desktop uses the existing routing front and Linux engine cgroup bypass. As with
Tor and Psiphon, if the system cannot keep a dynamically selected upstream out of
the TUN (Windows/macOS or Linux without a suitable cgroup), the existing desktop
router falls back to system proxy and records this in the log. Proxy-only mode
leaves system settings alone.

WARP identities stay in Android private `filesDir/aether` or the desktop user's
configuration directory `aether` subdirectory. They survive restart, are not
stored in the repository, and are not embedded in exported profiles. The identity
directory is restricted to its owner where the filesystem supports it.

## Validation

Run `python3 tools/test-aether-packaging.py` for offline archive/checksum tests.
Run `./gradlew :desktop:test :app:assembleDebug` after fetching the cores for
profile persistence/share and process readiness/cancellation tests plus app
compilation. `.github/workflows/aether-check.yml` runs these and a real Linux
Aether CLI smoke check, and saves debug APKs as workflow artifacts.

A real-device WARP connectivity test on a filtered network is still required;
offline checks and compilation cannot establish that a particular ISP passes
the selected transport. Windows/macOS system-proxy behavior and Android per-app
routing also need device acceptance before a production release.

### Current verification result (2026-11-06)

- Six offline packaging integration tests: PASS.
- Shell syntax, workflow YAML, FA/EN Aether resource references and diff whitespace: PASS.
- Kotlin lifecycle/profile tests: added (nine tests), not executed here.
- Full Android/desktop build: BLOCKED; the Gradle distribution cannot be downloaded
  in this environment (`Network is unreachable`).
- GitHub write access: rechecked successfully after connection permissions were
  updated; changes are prepared on `feat/aether-core`, with build/CI results to
  be verified separately.
- Real Android/desktop WARP connectivity: not tested.

The implementation is a reviewable candidate, not a verified release or APK.
