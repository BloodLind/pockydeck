# F16 — Emulator selection and ROM launch

- Status: **planned**.
- User stories: [US-044](../user-stories/US-044-supported-emulator-resolution.md), [US-045](../user-stories/US-045-supported-rom-launch.md), [US-046](../user-stories/US-046-emulator-default-and-item-choice.md). Dispatch one ready story; this packet remains the technical ownership and feature-gate reference.
- Agent: `gpt-5.6-sol`, reasoning `high`.
- Outcome: choose a supported external emulator and launch an indexed ROM, then return with correct item selection and recency.
- Prerequisites: F15 accepted; exact first emulator apps/versions and supported platform/format combinations selected. Compatibility and URI strategy are material support choices; resolve them before dependent implementation.

## Ownership and contracts

Owns `core/data/src/main/kotlin/dev/handheld/launcher/core/data/rom/emulator/**`, `app/src/main/kotlin/dev/handheld/launcher/feature/settings/emulators/**`, `app/src/main/kotlin/dev/handheld/launcher/feature/details/emulator/**`; matching data/app JVM/instrumentation test packages; `docs/implementation/evidence/F16/**`. Source/index internals, shared launch dispatcher, domain policies/contracts, persisted preference/schema changes, intent queries/permissions, DI and details/settings registration require coordinator integration. Existing F10/F12 leases are closed before these new subpackage leases start.

Consumes F15 ROM/document identities and source availability, F07 installed-app inventory, F08 common launch pipeline and F12/F10 extension points. Provides emulator capability detection, supported launch adapters, default/per-item selection presentation and actionable unsupported-target errors. Coordinator publishes the exact emulator-resolution/default/override contract and preservation rules before consumer code lands.

## Ordered steps

1. Write a compatibility table naming package/component/version, platform/formats, documented launch action/extras/flags, URI grant requirements and validation device. Detect installed supported apps using the shared visibility policy; an installed package is not sufficient evidence that every ROM format works.
2. Define deterministic default resolution and explicit user choice for ambiguous/missing options. Persist only accepted defaults/overrides through centrally reviewed schema/preferences changes. Do not silently change emulator selection behavior when an app updates.
3. Implement one adapter for each accepted launch contract. Validate target availability, URI permissions and external task placement immediately before dispatch; forward read grants only as required. If an emulator cannot consume SAF input, stop and resolve the supported storage strategy rather than silently copying ROMs or inventing paths.
4. Integrate adapters into the existing F08 launch coordinator through a shared delta. Successful ROM dispatch promotes the same ID once; failure preserves order and offers a useful selection/source recovery action. Keep system action launches outside Home recency.
5. Add functional default/emulator-settings and item-specific choice UI using shared settings/modal controls. Preserve origin selection and controller focus after resolving the choice or dismissing the dialog.
6. Reuse Argosy mappings only selectively if their license is compatible and the actual contract is validated. Record source revision/path and retain notices/attribution; do not import Argosy's architecture or broader feature set.

## Excludes

No emulator controller remapping, BIOS setup, save states, emulator installation purchases, guaranteed suspension/resume, hidden ROM copying, process monitoring or unverified broad compatibility claims.

## Validation and handoff

Run each supported app/version/platform/format row on device: successful open, unavailable app, revoked source grant, unsupported format, repeated open, return Home, task separation and identity/anchor continuity. Test deterministic default/override selection and failure leaving recency unchanged. Record exact compatibility evidence and attribution. Coordinator reviews integrated launch/persistence changes and native-app regression checks before F17 starts.
