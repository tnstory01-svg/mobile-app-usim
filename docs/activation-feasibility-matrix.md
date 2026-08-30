# Activation Feasibility Matrix

Do not place activation codes, QR contents, matching IDs, confirmation codes, callback intents, or derived identifiers in this file.

| Git revision | APK SHA-256 | App ID/version/variant | Signer SHA-256 | Protocol revision | UTC start/end | Device/OEM | Android/API | eSIM feature / eUICC enabled | Physical USIM before/after | Scenario | Outcomes observed | Verdict |
|---|---|---|---|---|---|---|---:|---|---|---|---|---|
| Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Online candidate: `InitialCallbackOk` or resolvable then `TerminalCallbackOk` | Pending | Pending |
| Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Cancellation: documented terminal cancellation/error outcome | Pending | Pending |
| Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Offline/error: documented terminal error outcome | Pending | Pending |
| Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Resolvable: complete system UI, terminal callback | Pending | Pending |
| Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Resolvable: cancel system UI, terminal callback | Pending | Pending |
| Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | MEP/port behavior (when device supports it) | Pending | Pending |

`Physical USIM before/after` must contain objective, non-secret evidence: subscription/service state, default-data subscription ID, and the result of the same appropriate service check before and after. `continuous=true` is valid only when all before/after values match and both service checks succeed.

For callback rows, record the exact displayed `ProbeOutcome` sequence. The persisted deadline is exactly five minutes after `AppDispatch`; if no required callback arrives by that instant, record `InconclusiveNoCallback` and verdict `InconclusiveNoCallback`. Late callbacks must not change it. `LocalFailure(RESOLUTION_NOTIFICATION_UNAVAILABLE)` is always `Inconclusive`, never Pass. Scenario-specific documented cancellation and offline/error terminal outcomes are Pass results only for those negative rows.

## Allowed outcome vocabulary

- `Ready`
- `AppDispatch`
- `SubmissionInProgress`
- `InitialCallbackOk`
- `InitialCallbackResolvableError`
- `InitialCallbackError`
- `ResolutionUi`
- `TerminalCallbackOk`
- `TerminalCallbackResolvableError`
- `TerminalCallbackError`
- `ResolutionCancelledOrUnknown`
- `InconclusiveNoCallback`
- `LocalFailure(EMPTY_ACTIVATION_CODE)`
- `LocalFailure(ESIM_UNSUPPORTED)`
- `LocalFailure(ESIM_DISABLED)`
- `LocalFailure(REQUEST_REJECTED)`
- `LocalFailure(RESOLUTION_UNAVAILABLE)`
- `LocalFailure(RESOLUTION_NOTIFICATION_UNAVAILABLE)`

Allowed verdict vocabulary: `Pass`, `Fail`, `InconclusiveNoCallback`, `Inconclusive`, and `Pending`.

The matrix remains `Pending` until physical-device evidence is collected.
