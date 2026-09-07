# US-003 — Publish shared navigation, input and presentation ports

| Field | Value |
| --- | --- |
| Parent feature | [F01 — Foundation and shared contracts](../features/F01-foundation.md) |
| Status | Accepted |
| Type | Enabler |
| Implementation agent | `gpt-5.6-sol` / `high` |

## User story

As a developer, I want typed app-facing ports and fixtures before shell and feature consumers begin, so that all pages use the same actions, restoration keys and platform requests.

## Ready when

- [US-002 — Define stable catalog, persistence and launch contracts](US-002-stable-catalog-and-launch-contracts.md) and the relevant F01 parent gate are accepted.
- The approved project, design, and ownership baseline is available; it does not imply Android implementation.
- F01 wave 1, its exact lease, validation, and coordinator handoff requirements apply. F01’s completion gate is assessed after its child stories; dependent features wait for coordinator acceptance.

## Scope

Define the six fixed destination IDs; origins for details and shortcut Search; per-destination identity, anchor, query, filter, and sort snapshots; and shell action/footer descriptors. Define semantic input actions, the persisted confirm/back preference defaulting to A-confirm/B-back, dispatch precedence, and presentation-only modal lifecycle callbacks. Define acknowledged Activity requests, truthful status presentation, and shared item-to-card, launch, and navigation boundaries. Provide deterministic fixtures and publish contracts and path leases.

## Acceptance criteria

- [x] **AC-01** — **Given** a navigation fixture, **when** it represents dock-to-Home Back, shortcut Search return, or item details, **then** it preserves the initiating origin without a Compose object or complete list.
- [x] **AC-02** — **Given** a typed action descriptor, **when** a footer prompt and semantic dispatch are rendered or invoked, **then** they use the same action meaning and the A-confirm/B-back default is explicit.
- [x] **AC-03** — **Given** independent design-system modal and status consumers, **when** they use the published contracts, **then** modal callbacks require no app/domain import and status distinguishes unavailable from unsupported.
- [x] **AC-04** — **Given** an acknowledged Activity request is observed repeatedly, **when** it is consumed, **then** it does not request another launch or role prompt.

## Verification

| Criterion | Evidence | Status |
| --- | --- | --- |
| AC-01 | NavigationContractTest: dock order, origins, Back and independent compact snapshots | Passed |
| AC-02 | SharedPresentationContractTest and NavigationContractTest: shared descriptors, MENU, A/B and precedence | Passed |
| AC-03 | Independent design-system compile; available/unavailable/unsupported fixtures | Passed |
| AC-04 | ActivityRequestPortTest: competing claims, reattachment, duplicate completion and resubmission | Passed |

## Delivery notes

F01 grants the initial lease for app contract types, the Activity request-port definition, contract fixtures/tests, and `contracts.md`; the coordinator retains publication and baseline responsibility. Source coverage: F01 ordered step 4 (navigation/input/preferences/modal/presentation), step 5 (acknowledged Activity port), step 6 (fixtures), and step 7 plus validation/handoff. Interfaces and ownership must be published before F02 and F06 start.

## Out of scope

- Concrete shell navigation, input processing, or preference encoding.
- Live telemetry or production sample content.

Accepted by coordinator on 7 September 2026 after independent Sol review, [US-003 checks](../evidence/F01/US-003.md), and [final Android 13 smoke](../evidence/F01/F01-final-gate.md).
