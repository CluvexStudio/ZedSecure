# NPV import support

This note records the current NPV import boundary on the test branch. It does not claim that every NPV container format is supported.

## Supported by the current parser

- `NPVO1` open exports: parses the `configs` array and extracts supported profiles.
- `NPVT1` and `NPVTSUB1`: decodes legacy white-box chunks, then searches decoded chunks for supported profile JSON.
- Raw Xray JSON embedded in an NPV profile: retained as raw JSON rather than flattened into a share link.

## NPVS v5 encrypted containers

The parser can inspect an NPVS v5 envelope and report its unlock method, but inspection is not decryption or import.

Unlock methods described by the v5 envelope are:

- Method 0: recipient private key required.
- Method 1: creator passphrase, using PBKDF2-HMAC-SHA256.
- Method 2: App Key, using the Gen-2 white-box derivation.

A method-2 file cannot be opened by asking for the creator's passphrase. The Gen-2 App Key path requires reference table data and the v5 envelope/field decryption sequence; the UI must not report successful import until decryption, sentinel substitution, and profile extraction have succeeded.

## Safety and compatibility requirements

1. Validate offsets and declared lengths before slicing or allocating.
2. Do not log or expose derived keys, decrypted credentials, or private profile fields.
3. Keep recipient-private-key envelopes unsupported unless a proper key workflow exists.
4. Preserve embedded Xray JSON when it is authoritative.
5. Reject empty or unsupported profile sets instead of reporting a successful import.
6. Keep work on the NPV test branch until real `.npvt` and `.npvs` fixtures pass automated tests and an Android build is verified.

## Reference implementation and licensing

[FrontierTM/Pantegnos](https://github.com/FrontierTM/Pantegnos) contains a reference implementation for NPVS v5 Gen-2/App Key envelopes and declares AGPL-3.0. Any port or copied assets must retain required copyright/license notices and comply with the applicable license. A reference implementation is not evidence that this app has implemented or tested that path.
