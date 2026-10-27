# NPV Tunnel import support

ZX VPN's NPV adapter is content-first: the file extension is a hint, not proof of a format. Parsing runs locally; file contents are not uploaded.

## Implemented

- Legacy `NPVT1` containers: decode the three-chunk version/profile/lock layout using the legacy table-driven CTR transform, then import supported NPV profile wrappers.
- Open `NPVO1` exports: parse the JSON `configs` array and import supported profile objects.
- NPV profile JSON: recognize `v2rayProfile` wrappers and preserve embedded full Xray JSON when `v2rayJson` is populated and has a supported Xray outbound.
- Shareable profile mapping: VLESS, VMess, Trojan, Shadowsocks, and SOCKS profile fields map to ZX VPN's existing config model. Transport/security fields are carried across where the current model supports them.
- Base64-wrapped NPV JSON / NPVT text: the existing import path tries decoded text and passes recognized NPV content to the same parser.
- NPVS v5 binary envelopes: validate the header, version, unlock method, KDF iteration bounds, metadata bounds, and ciphertext length. A protected NPVS file is not mistaken for a text config.

## Known limitation — NPVS unlock

NPVS v5 supports different unlock methods (recipient key, creator passphrase, or app key). The current adapter validates the envelope and reports the declared unlock method, but it does **not** decrypt/import NPVS v5 payloads yet. A valid creator passphrase alone is not enough to safely implement this without a verified format specification or a known-good, synthetic test vector for the exact header/KDF/nonce/tag layout. Unsupported NPVS versions are rejected explicitly. Do not claim universal NPVS compatibility until an authorized unlock implementation and regression tests are available.

The `.npv` extension is not treated as a unique binary format because public references do not establish one stable, universal `.npv` wire format. The importer recognizes its content when it is an NPV JSON export, NPVT1 text, or an existing supported share link.

## Reference and attribution

The legacy NPVT1 table layout and CTR transformation were adapted from the MIT-licensed reference project [TheLouisMahdi/npvt-terminal-converter](https://github.com/TheLouisMahdi/npvt-terminal-converter). The code is used as a format-compatibility reference; this does not imply affiliation with NPV Tunnel. See `THIRD_PARTY_NOTICES.md` for the license notice.

## Validation requirements

Before calling this feature production-complete, add regression fixtures that contain only synthetic credentials and cover:
- valid and malformed NPVT1 chunks;
- NPVT1 profile round trips for each supported profile type;
- NPVO1 and direct NPV JSON exports;
- NPVS v5 header variants and truncation;
- unsupported/unknown profile fields that must not be silently discarded;
- successful Android APK and AAB builds on the final commit.

Live server reachability is separate from file-format parsing and must be tested against an authorized server.
