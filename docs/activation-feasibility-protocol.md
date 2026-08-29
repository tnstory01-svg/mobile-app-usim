# eSIM Activation Feasibility Protocol

## Purpose

Validate whether an unaffiliated, signed Android app can submit an SGP.22 activation-code body through `EuiccManager.downloadSubscription` and complete Android's documented resolution callback flow without disrupting an active physical USIM.

## Safety

- Use only disposable test profiles supplied for this probe.
- Never record activation bodies, QR images, matching IDs, confirmation codes, callback intents, or derived hashes.
- Do not delete profiles, reset network settings, change the default data SIM, or disable the physical USIM.
- Record only the non-secret outcome enum, device model, Android version, OEM, eSIM capability, physical-USIM continuity, and timestamp.

## Matrix

Run on at least two OEM devices:

1. Android 10/API 29 device when available.
2. A current supported Android release.
3. Active physical USIM before submission.
4. No carrier privileges granted to the probe.
5. Online success candidate.
6. User cancellation.
7. Offline or documented error.
8. Resolvable error and terminal callback.
9. Applicable MEP/port behavior.

## Procedure

1. Verify the physical USIM has service and record only `continuous=true|false`.
2. Launch the signed probe and verify eSIM feature and `EuiccManager.isEnabled` results.
3. Enter a disposable encoded activation-code body without the `LPA:` envelope.
4. Submit once. Do not capture screenshots while the secret is visible.
5. For a resolvable callback, complete or cancel Android's system UI once.
6. Wait for the terminal callback; the system UI return alone is not a terminal result.
7. Recheck physical-USIM service without changing SIM settings.
8. Clear the input and close the probe.

## Pass condition

The public payload path must produce documented callbacks across the declared matrix, preserve physical-USIM continuity, require no carrier privilege or undocumented OEM behavior, and expose no secret in app persistence or logs.

## Failure decision

A failed or inconclusive matrix blocks product bootstrap. The owner must explicitly choose guide-only scope, provider integration, or a narrower support matrix.
