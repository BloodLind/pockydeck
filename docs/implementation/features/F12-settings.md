# F12 — Basic Settings

- Status: **planned**.
- User stories: [US-033](../user-stories/US-033-controller-mapping-settings.md), [US-034](../user-stories/US-034-default-home-settings.md), [US-035](../user-stories/US-035-readability-and-supported-system-settings.md). Dispatch one ready story; this packet remains the technical ownership and feature-gate reference.
- Agent: `gpt-5.6-terra`, reasoning `high`.
- Outcome: users can configure confirm/back mapping, reach default-HOME setup, and understand launcher readability and supported system options.
- Prerequisites: F09, F10 and F11 accepted together. Runs independently beside F13; Settings must work with the shell's honest unavailable status fallback.

## Ownership and contracts

Owns `app/src/main/kotlin/dev/handheld/launcher/feature/settings/**`; matching app JVM/instrumentation test packages; `docs/implementation/evidence/F12/**`. Coordinator owns preference keys/encoding, system-action index/dispatch, HOME role attachment, shell/status DI and shared row APIs. Future source/emulator/provider panels use separate subpackages and later leases described in F15–F17.

Consumes F01/F06 typed preferences including confirm/back mapping, F08 acknowledged HOME-role request port, F03 settings controls and the supported system-action registry. Provides Settings route/state/ViewModel/screen, settings-section slots for later stages, and stable searchable basic-setting descriptors for coordinator registration. Status remains in the shared shell and is not recreated by this page.

## Ordered steps

1. Compose a category list and grouped setting rows within the existing shell content rectangle. Keep labels readable, focus transitions explicit and values state-driven.
2. Expose confirm/back mapping through the typed persisted preference. Apply the same mapping to actual input and controller glyphs after coordinator integration; do not implement a second input path.
3. Expose default-HOME setup through the platform request port with acknowledgement. Explain current selection/declined state and allow continued browsing; do not repeatedly reopen Android's role prompt.
4. Implement the approved appearance/readability surface using existing preferences and valid Android display/text settings entry points. Respect system text scaling/reduced motion and show useful current state. Do not invent a full theme editor or unsupported settings toggles to fill the page; propose any materially new user preference before adding it.
5. Supply searchable stable keys for implemented settings/actions. Validate that targets exist before offering platform shortcuts. Keep later ROM/emulator/provider sections absent until their functionality lands; expose extension slots, not nonfunctional controls.
6. Have coordinator wire Settings navigation, DI and the existing shell status interface using the current unavailable/unsupported provider when F13 is not yet integrated. F13 can replace the provider later without changing Settings layout or resetting state.

## Excludes

No replacements for Android notification/volume/brightness/power UI, performance controls, device-global customization, ROM/provider setup, decorative telemetry, or duplicate shell.

## Validation and handoff

Test mapping preference round-trip and live glyph/action agreement, role decline/acknowledgement without replay, controller access to every row, searchable setting target resolution, and larger fonts. Verify Settings works before F13 with truthful unavailable status. Capture standard and large-font layout and deliver section extension points plus route/index/DI deltas. Coordinator integrates wave 8 and reviews F13 status behavior before F14.
