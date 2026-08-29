# Activation Decision Record

## Status

Pending physical feasibility evidence.

## Candidate

Use `DownloadableSubscription.forActivationCode(encodedBody)` with `EuiccManager.downloadSubscription`, then Android's documented resolution callback when required.

## Non-candidates

- Management Settings activities do not transport activation payloads or prove activation.
- Undocumented OEM intents, clipboard relay, and permission workarounds are prohibited.
- Provider integration remains deferred.

## Decision gate

- **Pass:** proceed with the smallest accessible QR-image-to-confirmation vertical slice.
- **Fail or inconclusive:** stop implementation and obtain an explicit owner decision for guide-only scope, provider integration, or a narrower support matrix.

No production app implementation is authorized by an unverified or partially completed matrix.
