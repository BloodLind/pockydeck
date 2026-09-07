# F05 — Controller input and focus restoration

- Status: **implemented in the native application batch**; see [integrated evidence and remaining device checks](../evidence/F14/native-application-base.md). Individual physical acceptance is not implied.
- User stories: [US-014](../user-stories/US-014-single-owner-controller-input.md), [US-015](../user-stories/US-015-page-and-modal-focus-navigation.md), [US-016](../user-stories/US-016-identity-and-scroll-restoration.md). This packet remains the scope and acceptance reference; the user-authorized combined delivery workflow is recorded in progress.
- Agent: `gpt-5.6-sol`, reasoning `high`.
- Outcome: controller and touch operate one coherent focus/action path, and page identity/scroll restore reliably.
- Prerequisites: F04 and F06 accepted. F04 fixture pages are sufficient for initial testing.

## Ownership and contracts

Initial-creation lease: `app/src/main/kotlin/dev/handheld/launcher/input/**`, `app/src/main/kotlin/dev/handheld/launcher/navigation/restoration/**`; matching app JVM/instrumentation test packages; `docs/implementation/evidence/F05/**`. Shell/navigation root, Activity, shared contracts, and DataStore encoding are coordinator-owned. Submit a precise attachment delta instead of editing them.

Consumes F01 semantic input/action and navigation snapshot contracts, F04 shell/navigation hooks, F06 preference/snapshot repository. Provides physical-input normalization, active focus scopes, UI restoration helpers, dead-zone/repeat configuration, and a single dispatch precedence. Compose owns requesters/scroll/IME; ViewModels retain stable selection/filter/query keys.

## Ordered steps

1. Normalize D-pad keys, hat axes, left stick, mapped confirm/back, and touch activation. Add dead zone, bounded held-direction repeat, and duplicate suppression for devices that report the same physical action twice. Input stops at the launcher boundary.
2. Implement the ownership order: active modal/IME handling, then focused page/control, then unhandled shell shortcuts. Connect A, B, X, Y, Start, shoulders and triggers to the same typed descriptor rendered in the footer.
3. Supply explicit focus-region transitions for header, filters, content, and dock. Shoulder destination switching may wrap; spatial grid edges do not unexpectedly wrap. L2/R2 changes filters only where present.
4. Implement restoration by stable selected ID and first-visible ID/offset. Wait until data/target composition exists; scroll the target into composition before requesting focus. Use saved ID → nearest surviving item → first item → empty-state action → active dock item.
5. Save small keys through SavedStateHandle/rememberSaveable as appropriate and durable per-destination snapshots through F06. Preserve selection across reorder, artwork, filtering, removal and touch-to-controller transitions; never retain stale numeric indices as identity.
6. Exercise Back precedence with fixture dialogs/details/Search/IME; root Home stays open. Coordinator attaches Activity key/axis callbacks and shell hooks once, then publishes final interfaces for F08 and parallel features.

## Excludes

No global input service, accessibility/overlay/usage permission, controller remapping inside emulators, custom keyboard, focus requesters in ViewModels, or alternate per-page key dispatchers.

## Validation and handoff

Focused JVM tests cover duplicate/repeat normalization and deterministic restoration fallback. Compose tests cover modal containment, empty-list recovery, offscreen selection, filter removal, dock switching, and physical-glyph/action agreement. Real Flip 2 testing covers controller event shape and system IME behavior when available; report unverified hardware behavior plainly. Hand off input attachment points, mapping preference behavior, restoration helper usage, and evidence to F08.
