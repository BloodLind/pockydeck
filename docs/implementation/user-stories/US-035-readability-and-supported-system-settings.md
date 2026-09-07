# US-035 — Access supported readability and system settings

- Parent: [F12 — Basic Settings](../features/F12-settings.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-terra`, reasoning `high`

## User story

As a launcher user, I want clear readability options and valid Android display/text entry points, so that I can adjust supported presentation without encountering fake controls.

## Ready when

- [US-034 — Reach Android default-HOME setup from Settings](US-034-default-home-settings.md) is accepted.
- The coordinator has accepted F09, F10, and F11 and supplied existing approved readability preferences and supported Android settings targets.
- F12’s wave-8 lease applies, while final F12 acceptance follows all of its child stories; F13 may still provide the truthful unavailable shell-status provider.

## Scope

Use existing approved appearance/readability preferences and valid Android display/text setting entry points. Respect system font scaling and reduced-motion intent; register stable searchable keys only for implemented actions. Hand route/index/DI requests to the coordinator and preserve Settings structure and selection when the shell status provider changes later.

## Acceptance criteria

- [ ] **AC-01** — **Given** larger system font scaling or reduced-motion intent, **when** Settings renders and navigates, **then** implemented rows remain readable and reachable while honoring those supplied preferences.
- [ ] **AC-02** — **Given** a display or text setting target is available and supported, **when** Settings presents it, **then** activating it uses its valid registered Android entry point.
- [ ] **AC-03** — **Given** a target is unavailable or unsupported, **when** Settings renders, **then** it is not offered as a functional control and no full theme editor or unsupported toggle appears.
- [ ] **AC-04** — **Given** an implemented readability or system action is searched, **when** its stable key resolves, **then** Search can reach the same supported action.
- [ ] **AC-05** — **Given** F13 later replaces the current unavailable status provider, **when** the provider changes, **then** Settings retains its layout and valid selection and represents the current unavailable status truthfully beforehand.

## Verification

- **AC-01:** Not run — Compose standard and large-font/reduced-motion screenshot and controller traversal checks.
- **AC-02:** Not run — Android emulator/device manual test for each registered valid entry point.
- **AC-03:** Not run — supported-target fixture test and manual absence review.
- **AC-04:** Not run — stable-key index and Search resolution integration test.
- **AC-05:** Not run — Settings state restoration test using unavailable then replacement provider fixtures.

## Delivery notes

The F12 lease covers readability/system presentation, descriptors, tests, and evidence. Preferences, platform registry, shell status, and DI stay with the coordinator. Source coverage: F12 ordered steps 4–6 and remaining validation/handoff. See [F12](../features/F12-settings.md) and [the story standard](story-standard.md).

## Out of scope

Replacement volume, brightness, or power controls; a new theme editor; and device-global customization.
