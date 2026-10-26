# NPV Tunnel import support

ZX VPN's NPV adapter is content-first: the file extension is a hint, not proof of a format. Parsing runs locally; file contents are not uploaded.

## Implemented

- Legacy `NPVT1` containers: decode the three-chunk version/profile/lock layout using the legacy table-driven CTR transform, then import supported NPV profile wrappers.
- Open `NPVO1` exports: parse the JSON `configs` array and import supported profile objects.
- NPV profile JSON: recognize `v2rayProfile` wrappers and preserve embedded full Xray JSON when `v2rayJson` is populated and has a supported Xray outbound.
- Shareable profile mapping: VLESS, VMess, Trojan, Shadowsocks, and SOCKS profile fields map to ZX VPN's existing config model. Transport/security fields are carried across where the current model supports them.
- Base64-wrapped NPV JSON / NPVT text: the existing import path tries decoded text and passes recognized NPV content to the same parser.
- NPVS v5 binary envelopes: validate the header, version, unlock method, KDF iteration bounds, metadata bounds, and ciphertext length. A protected NPVS file is not mistaken for a text config.

## NPVS v5 unlock support and limitations

The Android file importer now attempts local decryption for NPVS v5 envelopes protected by the creator passphrase. It validates the envelope lengths, derives the wrapping key with PBKDF2-HMAC-SHA256, authenticates the wrapped data key and metadata with ChaCha20-Poly1305, decrypts the profile fields, resolves the NPV sentinel table, and then routes the recovered profile JSON through the normal importer. The passphrase and file contents are processed locally; they are not sent to a server.

Compatibility remains intentionally partial:
- Creator-passphrase NPVS v5: implemented, but still requires CI and real-format regression verification before being called production-complete.
- Recipient-key envelopes: unsupported because the recipient private key is required.
- App-key envelopes: unsupported because the app-key white-box derivation is not implemented.
- Other NPVS versions and malformed files: rejected explicitly.

A successful decrypt/parse means the profile was structurally imported; it does not guarantee that the remote server is reachable. Do not claim universal NPVS compatibility until CI passes and authorized synthetic fixtures cover the supported passphrase format.

The `.npv` extension is not treated as a unique binary format because public references do not establish one stable, universal `.npv` wire format. The importer recognizes content when it is an NPV JSON export, NPVT1 text, or an existing supported share link.

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


## SlipNet import (experimental)

The test branch now recognizes SlipNet share URIs documented by [anonvector/SlipNet](https://github.com/anonvector/SlipNet) and cross-checks the encrypted export envelope against [FrontierTM/Pantegnos](https://github.com/FrontierTM/Pantegnos). Processing is local; the importer does not send the selected file or password to a remote service.

Currently importable and runnable through this app's existing DNS tunnel controller:
- `slipnet://` plain exports for DNSTT and VayDNS modes;
- `slipnet-enc://` single-profile exports using SlipNet's legacy encrypted profile envelope, when the profile does not carry a profile-lock policy; locked profiles are rejected until that policy can be enforced;
- `slipnet-bundle-enc://` password-protected bundles, with a local password prompt and retry after a wrong password;
- optional SSH chaining fields when present in a supported DNSTT/VayDNS profile.

The parser intentionally rejects Slipstream, NoizDNS, standalone SSH, NaiveProxy, Tor/Snowflake, and other modes that do not yet have a matching runnable engine in ZX-VPN. It must not silently convert these into DNSTT or VayDNS. A successful import means the profile has been stored, not that a remote server is reachable. Per-resolver authoritative flags are not represented by the current `DnsTunnelProfile` model and may be normalized during import; profile-level lock policies are rejected rather than ignored. Engine parity for those modes remains separate work.

Pantegnos is AGPL-3.0 in its current releases. This branch uses it as a reference for file-format behavior and does not vendor its source tree. ZX-VPN's repository is also AGPL-3.0; retain the applicable notices when distributing builds.
