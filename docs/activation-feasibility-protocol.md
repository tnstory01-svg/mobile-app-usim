# eSIM Activation Feasibility Protocol

## Purpose

Validate whether an unaffiliated, signed Android app can submit an SGP.22 activation-code body through `EuiccManager.downloadSubscription` and complete Android's documented resolution callback flow without disrupting an active physical USIM.

## Safety

- Use only disposable test profiles supplied for this probe.
- Never record activation bodies, QR images, matching IDs, confirmation codes, callback intents, or derived hashes. Callback intents are never logged, persisted, or exported. The framework callback `Intent` may exist only transiently inside the explicit immutable, update-current notification `PendingIntent` used for user-mediated resolution.
- Do not delete profiles, reset network settings, change the default data SIM, or disable the physical USIM.
- Record only non-secret evidence: device model/OEM, Android release/API, eSIM capability, physical-USIM continuity, and the metadata and outcomes below.

## Evidence identity

Every physical-device matrix entry must bind the observation to all of:

- Git revision;
- APK SHA-256;
- application ID, version name/version code, and build variant;
- APK signer certificate SHA-256;
- this protocol revision;
- UTC start and end timestamps in ISO 8601 `Z` form.

Set `protocol revision` to the Git revision containing this protocol and matrix; when evidence is produced from an uncommitted worktree, record that revision plus the SHA-256 of both protocol and matrix files.

Do not reuse evidence across APKs, signers, Git revisions, variants, or protocol revisions.

## Matrix

Run on at least two OEM devices:

1. Android 10/API 29 device when available.
2. A current supported Android release.
3. Active physical USIM before submission.
4. No carrier privileges granted to the probe.
5. Online success candidate.
6. User cancellation.
7. Offline or documented error.
8. Resolvable callback followed by a terminal callback.
9. Applicable MEP/port behavior.

## Procedure

1. Capture the evidence identity and UTC start timestamp.
2. Verify the physical USIM has service and record objective before-state evidence: subscription/service state, default-data subscription ID, and a successful data or voice/SMS check appropriate to the device. Do not record IMSIs, phone numbers, or activation material.
3. Launch the signed probe and verify eSIM feature and `EuiccManager.isEnabled` results.
4. Enter a disposable encoded activation-code body without the `LPA:` envelope.
5. Submit once. Do not capture screenshots while the secret is visible.
6. Record the displayed `ProbeOutcome` immediately after dispatch. A duplicate submission must display `SubmissionInProgress`.
7. For `InitialCallbackResolvableError`, tap the probe notification to enter Android's system resolution UI. If app notifications are disabled, the notification channel has importance `NONE`, or notification delivery fails, record `LocalFailure(RESOLUTION_NOTIFICATION_UNAVAILABLE)` and the scenario's verdict is `Inconclusive`; do not leave the operation active.
8. In the system UI, complete or cancel once. `ResolutionUi` and `ResolutionCancelledOrUnknown` are non-terminal.
9. Starting at `AppDispatch`, wait five minutes for the required callback. If none arrives, record outcome `InconclusiveNoCallback` and verdict `InconclusiveNoCallback`; late callbacks are ignored. Do not infer success from system UI return.
10. Recheck the same physical-USIM objective evidence without changing SIM settings. Continuity passes only when the before and after subscription/service state and default-data subscription ID match and the same appropriate service check succeeds.
11. Record UTC end timestamp, clear the input, and close the probe.

## Deterministic verdict

- **Pass:** a scenario reaches its scenario-specific expected terminal `ProbeOutcome` within five minutes, the expected callback sequence is observed, and physical-USIM continuity passes. For the cancellation and offline/error negative rows, the documented expected error/cancellation terminal outcome is a Pass, not a product-activation success.
- **Fail:** an activation-path scenario observes `InitialCallbackError`, `TerminalCallbackError`, `TerminalCallbackResolvableError`, or any `LocalFailure`; an unexpected sequence occurs; activation changes physical-USIM continuity; carrier privilege or undocumented OEM behavior is required; or secret handling is observed. The same documented terminal error/cancellation outcome is Pass only in its matching cancellation or offline/error negative scenario.
- **InconclusiveNoCallback:** five minutes elapse after `AppDispatch` without the required callback. This is not a pass; it clears the active operation, displays `InconclusiveNoCallback`, and ignores late callbacks.
- **Inconclusive:** evidence identity or objective before/after continuity evidence is missing; `LocalFailure(RESOLUTION_NOTIFICATION_UNAVAILABLE)` occurs; or the device cannot execute the scenario without an unrelated environmental interruption. It is never a Pass.

The public payload path passes the feasibility gate only when every required matrix row is Pass.

## Failure decision

A failed or inconclusive matrix blocks product bootstrap. The owner must explicitly choose guide-only scope, provider integration, or a narrower support matrix.
