# Activation Feasibility Matrix

Do not place activation codes, QR contents, matching IDs, confirmation codes, or derived identifiers in this file.

| Device/OEM | Android/API | eSIM feature | eUICC enabled | Physical USIM before/after | Scenario | Initial callback | Resolution UI | Terminal callback | Verdict |
|---|---:|---|---|---|---|---|---|---|---|
| Pending | Pending | Pending | Pending | Pending | Online candidate | Pending | Pending | Pending | Pending |
| Pending | Pending | Pending | Pending | Pending | Cancellation | Pending | Pending | Pending | Pending |
| Pending | Pending | Pending | Pending | Pending | Offline/error | Pending | Pending | Pending | Pending |

## Allowed outcome vocabulary

- `AppSubmissionDispatched`
- `InitialCallbackOk`
- `InitialCallbackResolvableError`
- `InitialCallbackError`
- `ResolutionUiLaunched`
- `ResolutionUiReturned`
- `ResolutionCallbackOk`
- `ResolutionCallbackResolvableError`
- `ResolutionCallbackError`
- `PlatformOutcomeUnknown`
- `LocalFailure`

The matrix remains `Pending` until physical-device evidence is collected.
