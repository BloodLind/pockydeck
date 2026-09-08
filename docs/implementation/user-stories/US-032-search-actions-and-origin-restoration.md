# US-032 — Act on Search results and restore the correct origin

- Parent: [F11 — Local Search and system keyboard](../features/F11-search.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-terra`, reasoning `high`

## User story

As a launcher user, I want to open a result and return to my query or initiating page, so that searching does not lose my place or distort recent items.

## Ready when

- [US-031 — Edit Search with the native keyboard and controller](US-031-native-keyboard-search-editing.md) is accepted.
- The coordinator has accepted F08 and made shared launch, details, navigation, and system-action ports available; F11’s wave-7 lease applies, while final F11 acceptance follows all of its child stories.
- The Search origin has been supplied as either a shortcut origin or dock origin through the existing route policy.

## Scope

Wire item results to common launch and details actions and supported system results to the registered internal/platform port. Restore Search query, category, platform, selected ID, and anchor after details or external dispatch. Apply the existing shortcut-origin Back rule and dock-Search-to-Home Back rule, then publish route/DI/registry integration requests.

The 8 September 2026 [interaction revision](../contracts.md#september-page-membership-search-and-interaction-revision) also applies to page selection and reselection: populated Search focuses the selected surviving result or first result after placement; empty Search focuses the query. The Search shortcut explicitly focuses editing and can summon the IME. Later result changes do not take focus away from typing. A completed touch selects its result before activation. These requirements do not change the historical acceptance status or verification records below.

## Acceptance criteria

- [ ] **AC-01** — **Given** Android or ROM search results, **when** a supported launch action is invoked, **then** it uses the common acknowledged launch path rather than an independent Search launch path.
- [ ] **AC-02** — **Given** a supported internal or system result, **when** it is activated, **then** it dispatches its registered behavior and does not promote Home recency.
- [ ] **AC-03** — **Given** an unresolvable action or unavailable target, **when** Search renders results, **then** it does not offer that result as a working shortcut; available action labels and enabled state match actual behavior.
- [ ] **AC-04** — **Given** the user returns from Details or an external launch and the result still exists, **when** Search restores, **then** query, category, platform, selected ID, and anchor are retained; **given** it no longer exists, **then** normal fallback uses a matching result or the empty-state query, never unrelated catalog content.
- [ ] **AC-05** — **Given** Search was opened by a shortcut, **when** IME handling is complete and Back is used, **then** the initiating destination state is restored; **given** Search was opened from the dock, **when** Back is used, **then** it returns Home.

## Verification

- **AC-01:** Not run — integration test showing Search item actions invoke the common launch port.
- **AC-02:** Not run — system-action dispatch and no-recency-mutation test.
- **AC-03:** Not run — Compose fixtures for unavailable/unresolvable action visibility and footer agreement.
- **AC-04:** Not run — ViewModel restoration tests for Details, external return, and missing-result fallback.
- **AC-05:** Not run — Compose navigation tests for shortcut and dock origins after IME dismissal.

## Delivery notes

F11 owns Search action/origin binding and evidence. The coordinator owns shared registry, navigation, and DI changes. Source coverage: F11 ordered steps 5–6 and remaining validation/handoff. See [F11](../features/F11-search.md) and [the story standard](story-standard.md).

## Out of scope

Independent recency logic, speculative system actions, and new platform intents outside the supported registry.
