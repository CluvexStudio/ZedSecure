# Config Finder — implementation specification

Status: initial native UI and allowlisted source fetching have been added on the feature branch. CI is still validating the shared module; do not treat the feature as release-ready until that run passes and device testing is completed.

## Goal

Add Config Finder (کانفینگ‌یاب) to the native ZX VPN flow at Servers → + → Add. The feature discovers publicly shared configuration references, validates syntax, shows provenance and test status, and lets the user explicitly select a configuration to add. Discovery, validation, and connection testing are separate states.

## User flow

1. Open Servers → + → Add → Config Finder.
2. Choose source types (GitHub repositories, public Telegram channels, direct URL, or pasted/local text).
3. Search by protocol, known country metadata, source, and optional quality filters.
4. Inspect each result: protocol, source, observed time, parser status, duplicate status, and connection-test status.
5. Open details to review sanitized metadata and provenance.
6. Explicitly select Add to Servers. Never auto-import or silently replace an existing server.
7. Allow removal and re-checking of imported entries.

## Provider design

Use a provider interface so sources can be added or disabled without changing UI code. Candidate sources requested for evaluation:
- Public Telegram channels: hddify, mitivpn (subject to public access, terms, and rate limits).
- GitHub repositories: miladtahanian/V2RayScrapeByCountry, SoliSpirit/v2ray-configs, Delta-Kronecker/V2ray-Config, Danialsamadi/v2go, morpheusadam/v2ray-config, MatinGhanbari/v2ray-configs, 0xRadikal/Free-v2ray-Configs, drmikecrypto/PulseConfigs, rtwo2/FastNodes.

These are candidate sources, not endorsed sources. Verify availability, licensing, file paths, and terms at runtime. Do not hard-code assumptions that a particular country/protocol/fast file exists.

Recommended abstraction:
- SourceProvider.search(query, filters, cursor)
- SourceProvider.fetch(reference)
- SourceProvider.health()
- ConfigParser.parse(input)
- ConfigValidator.validate(parsed)
- ConnectionTester.test(config, timeout) — native engine only; never infer real latency from metadata.

Provider adapters should return a common record with source ID, canonical source URL, observed timestamp, raw-format type, parsed protocol, known country (nullable), sanitized endpoint summary, content hash, and provenance. Keep raw config material out of analytics and logs.

## Supported input types

- URI/share links for protocols already supported by ZX VPN.
- Xray JSON and sing-box JSON.
- OpenVPN .ovpn.
- Amnezia vpn:// links.
- NPV Tunnel or other unknown formats: classify as unknown and offer export/copy only until a dedicated, reviewed parser exists. Never claim compatibility based only on a file extension.

## Search and ranking

- Search by text, protocol, country when the source provides reliable country metadata, source, and validation/test state.
- Deduplicate using normalized content hashes while preserving all source references.
- “Format valid”, “reachable”, “latency measured”, and “speed measured” must be distinct statuses.
- Never trust labels such as “fast”, “best”, or “verified” without an independently recorded test timestamp and method.
- Connection tests must use the appropriate native engine and clearly show failures/timeouts. Do not simulate a successful real-world test in production.
- Respect provider rate limits, cache results with TTLs, and provide retry/backoff and partial-result states.

## Security and privacy requirements

- Treat all fetched configurations and repository/channel content as untrusted data.
- Do not execute downloaded scripts or remote content.
- Do not fetch arbitrary URLs from the app client. Use a controlled server-side provider with strict host allowlists, redirect re-validation, private/link-local IP blocking, DNS-rebinding protections, response-size limits, content-type checks, and timeouts to prevent SSRF.
- Keep provider tokens/secrets only in server environment variables. Never ship tokens in Android resources or web client code.
- Do not send full config strings, credentials, UUIDs, passwords, private keys, or raw logs to telemetry.
- Mask sensitive values in previews and logs. Do not automatically upload user-pasted configs.
- Add only after an explicit user action and show the source URL and retrieval time.
- Apply Android network security, parser size limits, malformed-input handling, and safe error messages.
- Provide a source-report/block mechanism and disable providers that violate terms or distribute malicious content.

## Suggested implementation stages

1. Native UI and models: add a Config Finder entry under Servers → + → Add; introduce result/source/status models and loading/empty/error states.
2. Parsers and dedupe: reuse existing import parsers where possible; add unit tests for malformed, oversized, duplicate, and unsupported inputs.
3. Provider adapters: implement GitHub public repository access first, with rate limiting and ETag/conditional requests; then evaluate public Telegram access options and terms. Avoid brittle scraping.
4. Native connection tests: test only via supported engines, with bounded timeouts and measured timestamps.
5. Integration and QA: test Android UI, accessibility, cancellation, offline behavior, provider failures, privacy redaction, and regression of existing import flows.

## Acceptance criteria

- The feature is reachable from Servers → + → Add.
- Source errors do not crash or block other providers.
- Results show source/provenance and separate parse/test statuses.
- Duplicates are merged without losing source references.
- Unsupported formats are clearly marked and are never silently imported.
- No secret appears in logs or analytics.
- No configuration is added without an explicit user action.
- Automated tests cover parsers, deduplication, provider failures, and privacy redaction.

## Current delivery boundary

The Lovable prototype is a separate companion project and currently blocked by workspace credits. The feature branch now includes a native Compose entry under Servers → + → Add, direct reads from the user-provided public raw configuration lists, Telegram public-preview attempts for hddify/mitivpn, and a GitHub repository-tree scan for NPV-related paths. Real connection/throughput tests remain delegated to the existing native engine after import. CI and Android-device verification are still required; this branch has not been merged into main.
