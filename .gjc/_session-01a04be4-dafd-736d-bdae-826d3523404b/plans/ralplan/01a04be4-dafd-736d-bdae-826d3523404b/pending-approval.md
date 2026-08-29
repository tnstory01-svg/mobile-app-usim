# Android eSIM 초보자 지원 앱 v1 — Consensus Revision Opener 4

## Summary
`mobile-app-usim`은 `README.md`만 있는 greenfield nested repository다. 이 revision은 stage-03 SHA `259699f3714b680ea83f1984a69f8be04d7b067a148af8f7fa2d376359d0625e`의 feasibility-first activation boundary, corrected callback algebra, local-only privacy boundary, physical-USIM safety, accessibility/usability gates, single module/no baseline Hilt, and pending-approval boundary를 보존한다.

Architect P3 BLOCK와 Critic P3 ITERATE의 compatible fixes only:
1. SGP.22 v2.5 representation is split into a source QR `LPA:` envelope and the encoded activation-code **body** submitted to `DownloadableSubscription.forActivationCode`. Positional fields, explicit supported subset, fixtures, confirmation-required flag, and forward extensions are now contractual.
2. Bundled ML Kit's non-cancellable `process(InputImage)` is modeled honestly: 5 seconds is a logical UI timeout, not detector cancellation. Generation invalidation suppresses late results; one task remains in flight; completion owns backing-resource release. Correct callback algebra is unchanged.

Production product work remains blocked until the signed API29/current, two-OEM, active-physical-USIM feasibility probe proves the public payload route. Management Settings UI never transfers a payload or proves activation. Failure requires an explicit owner decision: guide-only scope, reopen `integration:carrier-provider`, or narrow the support matrix—never OEM/clipboard/permission workaround.

## Intent Diff
| Area | Resolved ground retained | Revision 4 correction |
|---|---|---|
| Activation transport | feasibility gate; Settings guide only; app dispatch/callback/resolution/user-report are distinct | no change to callback algebra |
| Activation code | deterministic but incorrect `LPA:`-as-body grammar | QR envelope is removed before transport; SGP.22 body positions and valid subset are explicit |
| Confirmation | stage 3 guessed optional fourth field as confirmation code | QR confirmation-required flag is positional; actual confirmation code is separate and system/LPA-owned, never guessed or collected by v1 |
| Decode limit | 10 MiB/4096px/16,777,216px/1024-byte bounds and one in-flight task | 5s becomes logical UI deadline; ML Kit completion, not coroutine cancellation, releases input |
| Scope/architecture | no provider/DI/OEM expansion; one module | unchanged |

## Principles
1. **Proof before promise:** no production activation implementation precedes signed physical feasibility evidence.
2. **System ownership is explicit:** app dispatch, Android callback, resolution UI return, management UI return, and user report are different facts.
3. **One use, no bridge:** raw activation information exists only for decode→validate→construct request→submit; retained UI/navigation/persistence sees safe outcomes only.
4. **Standards-valid bounded intake:** accepted source forms map deterministically to an SGP.22 body, reject safely, and constrain resource use without inventing carrier validity.
5. **Beginner physical-USIM safety:** never change/remove subscriptions or default data, overclaim connectivity, or recommend destructive reset; accessibility/comprehension are release blockers.

## Decision Drivers
1. **Documented public payload transport:** QR-body submission must be viable on API29+ for a signed unaffiliated app; a resolved management activity is not evidence.
2. **Privacy and accurate lifecycle control:** no secret/URI/hash persistence or remote handling; app only claims release of resources it controls and does not claim ML Kit hard cancellation/JVM-string/provider-image erasure.
3. **Locked real-world journey:** active physical-USIM coexistence, two-OEM assistive-tech validation, and ≥10/12 first-eSIM novice completion/understanding with zero critical misunderstanding.

## Options
### Activation transport
| Option | Facts | Decision |
|---|---|---|
| A. Public `DownloadableSubscription.forActivationCode(body)` + `EuiccManager.downloadSubscription` and documented resolution | API28 payload-bearing candidate; authorization, carrier, LPA/OEM/MEP behavior unproven. | Preferred feasibility candidate only. |
| B. `ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS` (API28) / `ACTION_MANAGE_ALL_SIM_PROFILES_SETTINGS` (API31) | no payload input/output. | Guide/recovery only after explicit guide-only scope change. |
| C. Provider provisioning | Requires provider/account/backend authority. | Deferred; reopen only by explicit owner decision after gate failure. |
| D. OEM/clipboard/manual secret relay | Undocumented/unsafe. | Rejected. |

### Capture
| Option | Decision | Constraints |
|---|---|---|
| `PickVisualMedia`, fallback `ACTION_OPEN_DOCUMENT` | Adopt | required on API29/no-GMS, no persistable URI grant |
| bundled `com.google.mlkit:barcode-scanning`, QR only | Adopt | offline first-use, asynchronous non-cancellable task lifecycle below |
| Google Code Scanner | optional other-device path | Play-services/module failure must leave image/manual paths usable; no app camera permission |

## In scope / out of scope
**In scope after feasibility pass:** Android10+ local app; QR-image first; manual body/link and optional scanner; exact public operation only on proven matrix; capability/callback facts plus independent profile/line/data/physical-USIM user reports; non-destructive recovery; non-sensitive preferences; device/accessibility/usability/release gates.

**Locked deferrals/out of scope:** `integration:carrier-provider`, account/auth/backend/purchase, iOS/sync/raw-code retention or analytics/provider auto-validation, broad storage/camera/phone-state permissions, all-OEM/automatic-success guarantee, undocumented workarounds, destructive profile/network reset guidance.

## API and compatibility contract
| Concern | Exact contract | Interpretation |
|---|---|---|
| eSIM hardware | `PackageManager.FEATURE_TELEPHONY_EUICC` | absence is `CONFIRMED_PLATFORM` device fact only |
| eUICC availability | `EuiccManager.isEnabled` (API28) | disabled/unavailable cause stays unconfirmed |
| body transport | `DownloadableSubscription.forActivationCode(encodedBody)`, `EuiccManager.downloadSubscription` (API28) | candidate only after signed gate; body does not include QR `LPA:` envelope |
| callback status | `EMBEDDED_SUBSCRIPTION_RESULT_OK`, `..._RESOLVABLE_ERROR`, `..._ERROR` | documented callback facts only |
| resolution | `EuiccManager.startResolutionActivity` (API28) from returned resolvable callback and new callback PendingIntent | Activity return is non-terminal |
| management guide | `ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS` API28; `ACTION_MANAGE_ALL_SIM_PROFILES_SETTINGS` API31 | no payload, guide/recovery only |
| carrier action | `ACTION_START_EUICC_ACTIVATION` API30 | excluded for unaffiliated raw-code v1 |

## Deterministic import contract
### Representations and transport boundary
Pinned reference: **GSMA SGP.22 v2.5 §4.1** and Android `DownloadableSubscription.forActivationCode` documentation. The plan validates only a stated v1 supported subset; validation is local shape validation, not carrier/provisioning validity.

| Representation | Accepted source / normalization | May cross boundary? |
|---|---|---|
| QR envelope | Exactly `LPA:` + encoded body. Remove a single leading UTF-8 BOM and ASCII outer SP/TAB/CR/LF before checking; no inner whitespace normalization. `LPA:` itself never enters Android body transport. | no |
| encoded activation-code body | `1$<SM-DP+ address>$<MatchingID>[$<SM-DP+ OID>[$<confirmation-required flag>]][<forward extensions>]`; string after the `LPA:` envelope, or manual/link value with no envelope. | **yes:** the normalized encoded body, preserving positional empty fields and allowed extensions, is passed to `forActivationCode` |
| manual body | exact encoded body only; manual `LPA:` envelope is rejected to keep forms unambiguous | yes after validation |
| HTTPS link | no fetch/WebView/redirect. ASCII `https://` only, no user-info/fragment/duplicate keys; exactly one `activationCode` or `lpa` parameter percent-decodes to an encoded body (not envelope). | yes after validation |
| confirmation code | not part of QR/body parser and not supplied as a guessed positional field | no v1 app collection/storage; system/LPA-owned interaction only where probe documents it |

### SGP.22 body positional rules and explicit v1 supported subset
The parser preserves body positions rather than collapsing empty fields. It recognizes the SGP.22 v2.5 structure: version `1`, SM-DP+ address, MatchingID, optional SM-DP+ OID, optional confirmation-required flag, and forward extension fields. The encoded body is at most **255 ASCII characters** after outer trim and before submission.

| Position | Standard-aware handling | v1 supported subset / rejection |
|---|---|---|
| 1 — version | exact first field `1` | other versions rejected as `ImportRejected(UnsupportedActivationCodeVersion)` |
| 2 — SM-DP+ address | positional required address | 1–253 ASCII hostname form: labels `[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?`, dot-separated, with at least one dot; otherwise `LocalFormat`. This is an explicit v1 subset, not a claim that all SGP.22 address spellings are invalid. |
| 3 — MatchingID | required position; may be zero length | empty accepted. Non-empty only uppercase `A-Z`, digits, `-`, length ≤255 and within 255 body total; lowercase/other punctuation rejected as unsupported v1 subset. |
| 4 — SM-DP+ OID | optional position | empty or dotted numeric OID with arcs `0` or nonzero digit followed by digits; no whitespace. It is **never** treated as confirmation code. |
| 5 — confirmation-required flag | optional position, including after empty OID (`$$1`) | absent or exact `1`; any other value rejected. `1` means confirmation may be required by the system flow, not that an app-provided code exists. |
| 6+ — forward extensions | delimiter and extension fields are recognized and preserved, as SGP.22 requires device forward compatibility | accept up to 4 extension fields, each 0–64 printable ASCII `0x21..0x7E` excluding `$`, total body ≤255; forward unchanged in body submission. More/malformed extensions are `ImportRejected(UnsupportedForwardExtension)`—a declared v1 supported-matrix exclusion, not silently stripped. |

Empty positional examples are valid when structurally meaningful: `1$smdp.example.com$` (empty MatchingID), `1$smdp.example.com$MATCH-001$$1` (empty OID, confirmation flag), and `1$smdp.example.com$MATCH-001$1.3.6.1.4.1.31746$1` (OID + confirmation flag). An actual confirmation code remains a separate system/LPA concern; v1 does not solicit, store, parse, or append it. If the signed feasibility probe shows a carrier requires app-supplied confirmation on API29, that is a gate failure unless an owner explicitly changes scope/API support.

### Source forms, fixtures, and failures
| Source | Representative accepted fixtures | Representative invalid fixtures / safe failure |
|---|---|---|
| QR envelope | `LPA:1$SMDP.GSMA.COM$04386-AGYFT-A74Y8-3F815$1.3.6.1.4.1.31746$1`; `LPA:1$smdp.example.com$MATCH-001$$1`; `LPA:1$smdp.example.com$`; `LPA:1$smdp.example.com$MATCH-001$1.3.6.1.4.1.31746$1$future` | `1$...` (no envelope), `LPA:2$...`, `LPA:1$smdp.example.com$match`, malformed OID, `$0`, too many/invalid extension fields → `ImportRejected(LocalFormat|UnsupportedActivationCodeVersion|UnsupportedForwardExtension)` without raw output |
| Manual body | `1$SMDP.GSMA.COM$04386-AGYFT-A74Y8-3F815$1.3.6.1.4.1.31746`; `1$smdp.example.com$MATCH-001$$1` | `LPA:1$...`, HTTP/custom URL, embedded whitespace, non-ASCII, >255 chars → safe rejection and clear field on exit/cancel |
| Manual/link QR body | `https://gift.example/e?activationCode=1%24smdp.example.com%24MATCH-001`; `https://gift.example/e?lpa=1%24smdp.example.com%24MATCH-001%24%241` | extra/duplicate query key, fragment/user-info, malformed percent encoding, envelope in query value, fetch/redirect requirement → safe rejection; URL never opened/fetched |
| scanner | same QR-envelope or link fixtures | module unavailable/cancel/no QR → `ImportUnavailable`/`ImportCanceled`; image/manual remain available |

Official fixture set is pinned in `docs/activation-code-fixtures.md` and `app/src/test/.../ActivationCodeParserTest.kt`, with citation/version and no production secret values. Fixtures cover Android's documented body example, SGP.22 OID present/absent, empty matching ID, empty OID with `$$1`, confirmation flag, supported extension, unsupported extension, 255/256-character bodies, envelope/body separation, and malformed UTF-8/percent forms. Any real carrier syntax outside this subset is a support-matrix exclusion or feasibility-gate input—not an improvised parser exception.

### Resource, timeout, and lifecycle contract
| Control | Policy | Safe behavior |
|---|---|---|
| source bytes | max 10 MiB | reject using provider metadata first or a 10 MiB+1 bounded reader; no full uncapped read |
| image dimensions | each side ≤4096px; total ≤16,777,216px | inspect before bitmap allocation; `ImageTooLarge` |
| body size | max 255 ASCII characters | `PayloadTooLarge` before request construction |
| detector concurrency | one ML Kit `process(InputImage)` task per activity | replacement request invalidates current generation; it does not start a second detector until the first task completes/releases input |
| UI deadline | 5 seconds logical timeout per generation | mark generation inactive and render `DecodeTimedOut`; do **not** claim task cancellation or release input still owned by ML Kit |
| completion ownership | ML Kit task completion listener owns source `InputImage` backing bitmap/buffer/scanner resource release | completion releases exactly once; if generation is inactive, result/error is discarded without parsing/emission/persistence |
| explicit cancel/screen exit/background | invalidate generation, remove sensitive UI, return to safe re-entry | no late emission; in-flight task completes silently, releases its owned input on completion; app retains no decoded payload/URI after exit |

A generation token is checked before every parse, request construction, UI emission, and persistence boundary. A timed-out/cancelled/background generation may not transition to a new submission even if ML Kit completes later. No raw source/result is logged/rendered, and no value is queued. ML Kit detector shutdown/release occurs only after all in-flight task completion callbacks have released their inputs. This is a logical UI responsiveness policy, not a hard compute-time cancellation guarantee.

## Activation outcome algebra (unchanged)
`ActivationTransport` emits sealed non-secret events. App-local transitions are not `CONFIRMED_PLATFORM`.

| Phase | Event | Evidence | Required behavior |
|---|---|---|---|
| pre-submit | `AppSubmissionStarted` | app-local, not evidence | accepted body enters one-use request construction |
| exception | `AppSubmissionFailedLocally` | `CONFIRMED_LOCAL` for caught invocation failure only | clear references; retry/support without callback claim |
| dispatch returns | `AppSubmissionDispatched` | app-local, not evidence | await initial callback; replaces ambiguous platform `OperationAccepted` |
| initial callback | `InitialCallbackOk` | Android callback / `CONFIRMED_PLATFORM` | exact `..._OK`; then independent user reports |
| initial callback | `InitialCallbackResolvableError` | Android callback / `CONFIRMED_PLATFORM` | exact `..._RESOLVABLE_ERROR` and documented resolution intent; may launch once |
| initial callback | `InitialCallbackError` | Android callback / `CONFIRMED_PLATFORM` | exact `..._ERROR`; recovery |
| resolution launch | `ResolutionLaunchFailedLocally` / `ResolutionUiLaunched` | former `CONFIRMED_LOCAL`, latter app-local | failure is no callback claim; launched UI is non-terminal |
| resolution UI return | `ResolutionUiReturned(resultCode)` | framework, non-terminal | wait for new callback; never success/failure |
| terminal callback | `ResolutionCallbackOk|ResolutionCallbackResolvableError|ResolutionCallbackError` | Android callback / `CONFIRMED_PLATFORM` | returned status; one documented resolution per chain, then support |
| no callback | `PlatformOutcomeUnknown` | `UNVERIFIED` | no inferred error from UI return/elapsed time; safe re-entry/support |
| management return | `ManagementUiReturned` | `UNVERIFIED` install status | guide only, never activation result |
| user confirmation | `UserReportedInstalled|LineActive|DataWorking|PhysicalUsimContinuous` | user report, cause `UNVERIFIED` | record report, not platform verification |

## File-level changes
All are proposed creations; no product sources exist. `com.example.mobileappusim` is provisional until applicationId approval.

| Path | Responsibility |
|---|---|
| `activation-feasibility-probe/settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts` | isolated signed API29/current probe, secure signing injection |
| `activation-feasibility-probe/app/src/main/.../ProbeActivity.kt`, `ActivationFeasibilityProbe.kt`, `ProbeResultReceiver.kt`, `ProbeOutcome.kt`, `ProbeEvidenceRecorder.kt` | no-secret physical matrix; unchanged exact dispatch/callback/resolution algebra |
| `docs/activation-feasibility-protocol.md`, `docs/activation-feasibility-matrix.md`, `docs/activation-decision-record.md`, `docs/activation-code-fixtures.md` | matrix/owner decision and pinned SGP.22 v2.5/Android representation fixtures |
| product `settings.gradle.kts`, root `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts` | after gate pass: single Compose app, bundled ML Kit, optional scanner, no baseline Hilt |
| `app/src/main/AndroidManifest.xml`, `app/.../app/MainActivity.kt`, `AppCompositionRoot.kt`, `AppNavHost.kt`, `JourneyRoute.kt` | single Activity, safe route data, no prohibited permissions |
| `app/.../core/model/AssistanceCase.kt`, `JourneyStage.kt`, `EvidenceLevel.kt`, `EvidenceSource.kt`, `PlatformActivationOutcome.kt`, `UserConfirmation.kt`, `PhysicalUsimContext.kt` | typed capability, dispatch/callback/resolution/guide/user facts |
| `app/.../core/platform/EsimCapabilityChecker.kt`, `ActivationTransport.kt`, `EuiccActivationTransport.kt`, `ResolutionCoordinator.kt`, `ManagementUiGuide.kt` | documented public contracts; no OEM transport |
| `app/.../core/security/SensitiveIngressPolicy.kt`, `OneUseActivationSubmission.kt`, `SensitiveResourceScope.kt`, `SensitiveValueRedactor.kt`, `SensitiveScreenPolicy.kt` | envelope/body validation, generation/lifecycle policy, one-use boundary, redaction/`FLAG_SECURE` |
| `app/.../feature/importgift/MediaPicker.kt`, `ActivationCodeParser.kt`, `QrImageDecoder.kt`, `BundledMlKitQrDecoder.kt`, `ManualActivationInput.kt`, `OptionalCodeScanner.kt`, `GiftImportScreen.kt`, `GiftImportViewModel.kt` | picker fallback, fixture-driven parsing, one-task generation state; ViewModel retains safe UI errors only |
| `app/.../feature/compatibility/CompatibilityScreen.kt`, `feature/activation/ActivationScreen.kt`, `feature/confirmation/ConfirmationScreen.kt` | capability/result algebra and physical-USIM-aware user reports |
| `app/.../feature/recovery/RecoveryDecisionTable.kt`, `RecoveryClassifier.kt`, `SupportRouter.kt`, `RecoveryScreen.kt` | source/evidence/safe-action decision table |
| `app/.../core/designsystem/UsimTheme.kt`, `AccessibleComponents.kt`, `feature/home/HomeScreen.kt`, `feature/help/HelpScreen.kt` | one-action accessible beginner shell/USIM warning |
| `app/.../data/preferences/PreferenceRepository.kt`, `DataStorePreferenceRepository.kt`, `NonSensitiveProgress.kt` | preference/stage only; excludes secret, URI, hash, request, callback intent |
| `app/src/test/.../ActivationCodeParserTest.kt`, `MlKitGenerationLifecycleTest.kt`, `SensitiveIngressPolicyTest.kt`, `OneUseActivationSubmissionTest.kt`, `ActivationTransportOutcomeTest.kt`, `RecoveryDecisionTableTest.kt` | fixtures, bounds, late suppression/release ownership, secret policy, unchanged algebra/recovery |
| `app/src/androidTest/.../AccessibleVerticalSliceTest.kt`, `JourneyNavigationTest.kt`, `SensitiveLeakTest.kt`, `PhysicalUsimJourneyTest.kt` | safe routes/lifecycle, no secret artifact, accessibility/USIM |
| `.github/workflows/android.yml`, `README.md`, `docs/privacy.md`, `docs/oem-capability-matrix.md`, `docs/release-checklist.md` | CI, Data Safety/local-only limits, matrix/release evidence |

### Dependency rule
One product Gradle app module: feature UI depends on domain ports; platform/data implement ports and never depend on Compose. `AppCompositionRoot` uses explicit constructor injection. Hilt/modules/provider integration/expanded permission require future architect review and explicit scope change.

## Sensitive lifecycle contract
| Representation | Owner/lifetime | Exit |
|---|---|---|
| picker/open-document URI | provider source, temporary app reference | no persisted permission/state/log; bounded stream closes in completion/`finally`; app cannot delete gallery source |
| bitmap/buffer/InputImage | active ML Kit task completion owner | bounds before allocation; release exactly once in completion listener, not logical timeout/coroutine cancellation |
| envelope/body/manual/scanner content | `OneUseActivationSubmission` only after current generation check | no ViewModel/Flow/snapshot/nav/SavedState/repository/DataStore/exception/test report; no JVM zeroization claim |
| IME/autofill/clipboard | transient sensitive control | disable autofill/learning where supported; never read/write clipboard; clear app field on exit; no claim to erase OS-owned content |
| external intent | none | no raw-code exported intake; reject extras without logging |
| request/callback | Android request plus non-secret correlation | protected explicit callback; clear correlation terminal/unknown exit |
| screen/recents/process | sensitive screen / lifecycle | `FLAG_SECURE` while raw input/source visible; invalidate generation on exit/background; no late result emission/persistence |

## Sequencing and dependencies
0. **Pending approval:** plan only; no implementation authorized.
1. **Signed feasibility probe before bootstrap.** Controlled profile uses normalized encoded body (not `LPA:` envelope) with `forActivationCode`/`downloadSubscription`; record app dispatch, initial callback, optional resolution UI return, and terminal callback. Exercise API29/current Android, two OEM physical eSIM devices, active physical-USIM, no carrier privilege, available/unavailable state, cancellation, offline/error, and supported MEP/port variants—without raw/derived secrets.
2. **Decision gate.** Pass only when documented payload/resolution works on declared matrix and preserves physical-USIM safety. Fail/inconclusive requires owner choice: guide-only, provider reopening, or matrix narrowing.
3. **Foundation + early accessible vertical slice after pass.** One module/domain/result algebra/import parser/generation lifecycle/design system; QR picker fallback→bounded decode→one-use body dispatch→safe callback outcome→physical-USIM confirmation. Test TalkBack, Switch Access, maximum text/display, contrast/focus on two OEMs plus 2–3 novice pilot before secondary routes.
4. **Primary journey.** Add capability facts, initial/terminal callbacks, one resolution chain, unknown/cancellation, profile/line/data/physical-USIM independent reports.
5. **Secondary inputs.** Manual body/link then optional scanner; verify fixture corpus, no-GMS/module absent, late ML Kit result after timeout/background, and all reject branches.
6. **Recovery/persistence.** Implement decision table then non-sensitive DataStore; restart never restores raw source/body/request/callback intent.
7. **Release gates.** Unit/instrumented/security, two-OEM physical matrix, accessibility, 12-person study, CI/internal Play/privacy/Data Safety, then closed testing.

Dependencies: 1→2 blocks product; 2(pass)→3; parser/generation/result algebra in 3 precede 4–6; early slice precedes secondary inputs; 7 validates integrated product.

## Recovery decision table
| Observable stage | Evidence | Wording / safe action | Success |
|---|---|---|---|
| parser/resource rejection | local parser / `CONFIRMED_LOCAL` | “We could not safely read an eSIM activation code from that input.” Retry allowed source/manual or support; never render raw value. | accepted input or support |
| picker/scanner cancellation/unavailable/logical timeout | local component / `CONFIRMED_LOCAL` | “That import method was not completed or is unavailable.” Safe re-entry; timed-out generation cannot emit late result. | alternate input/support |
| eSIM absent | Android feature / `CONFIRMED_PLATFORM` | device/carrier support route | support |
| eUICC unavailable/matrix excluded | platform fact / `CONFIRMED_PLATFORM`, conclusion `INFERRED` | limitations/support, no workaround | support |
| initial/terminal resolvable callback | Android callback / `CONFIRMED_PLATFORM` | Android needs a system step; start returned resolution once, keep USIM warning | callback/cancel/support |
| callback OK | Android callback / `CONFIRMED_PLATFORM` operation only | Android reported operation completion; ask distinct reports | reports recorded |
| callback ERROR | Android callback / `CONFIRMED_PLATFORM` | Android reported step did not finish; non-destructive retry/support | terminal/support |
| resolution return/no callback | framework return / `UNVERIFIED` | no Android confirmation; safe re-entry/support | callback/support |
| management return | framework return / `UNVERIFIED` | never activation confirmation; guide/support | retry/support |
| profile/line/data or physical-USIM report failure | user report, root cause `UNVERIFIED` | stage-specific Settings/provider support; no delete/reset/line change | retry report/support |
| unknown | none / `UNVERIFIED` | no confirmation; safe re-entry/support | terminal/support |

## Acceptance criteria
1. Signed pre-bootstrap probe records a public payload path or an explicit owner decision; management UI never satisfies feasibility.
2. Probe covers API29/current, two OEM physical eSIM devices, active physical-USIM, no carrier privilege, cancellation/offline/error and applicable MEP/port facts without secret records.
3. Only after gate pass, QR-image-first flow removes `LPA:` envelope and submits only validated encoded body through approved public operation.
4. Parser implements pinned SGP.22 v2.5 representation/supported subset: body positions, OID, empty optional positions, `$$1` flag, empty/non-empty MatchingID, bounded forward extensions, 255-char maximum, and fixture corpus; it never treats a positional field as confirmation code.
5. Confirmation-required is system/LPA-owned. v1 does not collect/store/send app-owned confirmation code; a probe-required app-owned code on API29 is a feasibility failure pending owner scope decision.
6. `PickVisualMedia` plus `ACTION_OPEN_DOCUMENT`, bundled QR decoder, manual body/link, and optional scanner work under exact contract on API29/no-GMS without storage/camera runtime permission.
7. Decoder has one task in flight, 5s logical timeout, generation invalidation/late suppression, completion-owned release, and safe background/exit re-entry with no late payload emission/persistence.
8. Raw code/link/URI/hash/payload never reaches disk/DataStore/nav/SavedState/log/analytics/crash/content descriptions/retained state/screenshot artifact; cleanup claims stay within app control.
9. Submission is app-local; only documented initial/terminal callback statuses are `CONFIRMED_PLATFORM`; resolution and management returns are non-terminal/non-confirming.
10. Active physical-USIM coexistence is required; app makes no subscription/default-data changes and tests before/after continuity plus line/data choice.
11. Every failure follows stage/source/evidence/safe action/retry-or-support recovery table.
12. Two OEMs pass TalkBack, Switch Access, maximum font/display, contrast/color-independent state, focus traversal; novice study is ≥10/12 independent completion/understanding with 0 critical misconception.
13. v1 has no account/backend/provider API/purchase/remote analytics/carrier authorization; failed feasibility is explicit stop/owner decision.

## Git commit units
Review-sized proposal only; no commit authorization.
1. `chore: add signed esim activation feasibility probe`
2. `docs: record activation feasibility protocol fixtures and decision gate`
3. `chore: bootstrap single-module kotlin compose android app`
4. `feat: add accessible navigation shell and core journey evidence models`
5. `feat: add standards-valid activation code contract and one-use secret boundary`
6. `feat: add image-first offline qr import and generation lifecycle`
7. `feat: add approved esim capability activation callback and physical-usim journey`
8. `feat: add user-confirmed installation line and data flow`
9. `feat: add secondary scanner link and manual code input paths`
10. `feat: add evidence-gated recovery taxonomy and support routing`
11. `feat: persist non-sensitive preferences and progress`
12. `test: add security accessibility device and usability coverage`
13. `build: add ci signing play distribution and release gates`

## Verification
No tests, builds, formatting, edits, or execution occur for this planning revision.

| Layer | Procedure | Pass condition |
|---|---|---|
| feasibility | signed physical protocol | exact body dispatch/callback/resolution records pass matrix or explicit owner decision stops work |
| unit | `./gradlew testDebugUnitTest` | pinned parser fixtures/limits; generation timeout/late-suppression/completion-release; callback algebra; recovery/persistence pass |
| instrumented | `./gradlew connectedDebugAndroidTest` | picker fallback/no-GMS, safe lifecycle exit/late result fixture, state routes, no secret sentinel leak |
| static/build | `./gradlew lint testDebugUnitTest assembleDebug bundleRelease` | no suppressed warnings, artifacts after gate pass |
| manifest/privacy | manifest plus sentinel capture | no prohibited permission or sensitive file/preferences/log/exception/saved-state/analytics/screenshot data |
| physical device | API29/current two OEM active-USIM, eSIM unavailable, GMS/no-GMS, callback/cancel/error, MEP/port supported | published evidence language matches observed facts |
| accessibility/usability | two OEM assistive-tech; 12-person protocol | all accessibility gates; ≥10/12 and zero critical misunderstanding |
| release | CI→internal Play→decision/privacy/Data Safety/matrix review→closed | all stop-ship evidence linked |

## Escalation / risk gate
- **Implementation block:** public body transport/resolution absent, privilege-only, inconsistent, requires app-owned confirmation on API29, or fails matrix/physical-USIM safety. Owner chooses guide-only, provider reopening, or matrix narrowing.
- **Privacy stop-ship:** sentinel body/URI/hash in any persisted/logged/retained/captured surface, or late generation emits payload after invalidation.
- **Platform stop-ship:** a UI return is represented as callback success/failure or callback variation breaks published matrix.
- **USIM stop-ship:** guidance causes removal/reset/default-line confusion or missing before/after evidence.
- **Accessibility/usability stop-ship:** either OEM traversal/reflow failure or 10/12/zero-critical gate miss.
- **Architecture gate:** Hilt/modules/provider integration/permissions need architect review and explicit changed scope.

## Verification Plan
1. Gate implementation on signed body-transport callback evidence, never Settings intent resolution or simulator behavior.
2. Run official plus declared-subset parser fixtures, 255/256 boundaries, envelope/body separation, OID/empty-position/flag/extension cases, malformed percent/UTF-8 cases, and all resource limits.
3. Simulate logical timeout/background/replacement before ML Kit completion and assert late result has no parse/request/UI/persistence effect while completion releases input exactly once.
4. Assert app dispatch, initial callback, resolution UI return, terminal callback, management return, and user report as separate recovery fixtures.
5. Run active physical-USIM early/final matrix; two-OEM accessibility and 12-person study are release blockers.

## Risks and mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| unaffiliated public path unavailable | core promise impossible under lock | signed probe then explicit owner decision |
| envelope/body or positional-field conflation | valid code rejection/incorrect transport | SGP.22 pinned representations, fixtures, explicit supported subset |
| confirmation requires app collection | impossible minimum API product path | system-owned only; probe failure gate, no guessed field |
| late ML Kit result after timeout/exit | secret lifecycle/UI race | logical deadline, generation invalidation, one task, completion ownership |
| API29/no-GMS absence | image flow unavailable | picker fallback/bundled decoder/manual route |
| physical-USIM confusion | service disruption | preserve-only guidance, matrix/usability gate |
| OEM/MEP variation | misleading support promise | feasibility/release matrix and limitations |
| false diagnosis | unsafe recovery | evidence-table wording/no destructive recommendation |

## RALPLAN-DR
**Decision:** Preserve all stage-3 resolved activation callback ground; correct intake to SGP.22 v2.5 QR-envelope/body separation and make ML Kit timeout logical rather than falsely cancellable. **Status:** revision opener; product remains blocked pending signed feasibility/owner approval. **Transport:** QR `LPA:` envelope is stripped; only normalized body reaches `forActivationCode`; confirmation code is not guessed/collected. **Lifecycle:** 5s invalidates UI generation; late results are suppressed; task completion releases input. **Locked:** Android10+, QR-image first, local/no-provider, ephemeral secret, no storage/camera permission, physical-USIM, evidence recovery, accessibility/usability. **No scope change:** no DI/provider/OEM workaround.


## Intent Reconciliation
- Consensus preserves the deep-interview contract: Android 10+, QR-image-first, active physical-USIM coexistence, local-only secret processing, no account/backend/provider API, no camera/storage runtime permission, evidence-scoped recovery, and accessibility/usability release gates.
- A signed API29+/current Android, two-OEM, active-physical-USIM feasibility probe must pass before product bootstrap.
- Management Settings UI is guide/recovery only; it never represents payload transport or activation success.
- If the public download/resolution probe fails, implementation stops for an explicit owner decision among guide-only scope, reopening provider integration, or narrowing the support matrix.
- material-open-items: none.

## ADR
### Decision
Adopt a feasibility-first Android activation boundary: validate the documented `DownloadableSubscription.forActivationCode` + `EuiccManager.downloadSubscription` and resolution callback path on the locked device matrix before building the product; use management Settings only for guidance and recovery.

### Drivers
1. Same-phone QR activation requires a real payload-bearing Android contract.
2. The app must not overclaim capabilities unavailable to a normal unaffiliated Android app.
3. Activation secrets must remain single-use, local, non-persistent, and outside navigation/UI state.
4. Active physical-USIM continuity and beginner comprehension are release-blocking requirements.

### Alternatives considered
- Settings-only handoff: rejected as payload transport; viable only after an explicit guide-only scope change.
- Provider integration: deferred because it requires account/backend/partner authority.
- Undocumented OEM intents, clipboard, or permission workarounds: rejected as unsafe and non-portable.
- Public download/resolution: selected as a gated feasibility candidate, not assumed production support.

### Why chosen
It is the only documented public route under review that accepts an encoded activation body while preserving Android-owned consent and resolution. The physical feasibility gate prevents speculative implementation when authorization or OEM behavior makes the route unavailable.

### Consequences
- The first implementation slice is an isolated signed feasibility probe, not the full app.
- Product bootstrap is conditional on recorded probe evidence.
- Platform callbacks, resolution UI returns, management UI returns, and user observations remain distinct evidence classes.
- QR parsing follows the bounded SGP.22 subset and logical ML timeout contract in this plan.

### Follow-ups
- Record probe evidence without secrets for API29+, two OEMs, active physical USIM, cancellation, offline, resolvable errors, terminal callbacks, and relevant MEP/port behavior.
- On pass, implement the smallest accessible QR-image-to-confirmation vertical slice and run the early beginner pilot.
- On fail, stop and ask the owner for the explicit scope decision documented above.

## Approval Status
`pending approval` — planning consensus does not authorize implementation, commits, deployment, or execution.
