# US-001 — Create an installable four-module launcher foundation

| Field | Value |
| --- | --- |
| Parent feature | [F01 — Foundation and shared contracts](../features/F01-foundation.md) |
| Status | Planned |
| Type | Enabler |
| Implementation agent | `gpt-5.6-sol` / `high` |

## User story

As a developer, I want an installable native project with verified tooling and explicit module boundaries, so that later feature work starts from one reproducible build.

## Ready when

- The approved project, design, and ownership baseline is available; it does not imply Android implementation.
- F01 wave 1, its exact lease, validation, and coordinator handoff requirements apply. Completing this story does not accept F01.
- The approved baseline opens F01. Its completion gate is assessed after its child stories; dependent features wait for coordinator acceptance of that gate. Apply the [common definition of done](story-standard.md).

## Scope

Record compatible pinned build versions and commands after inspecting local instructions and the installed toolchain. Scaffold only `app`, `core:domain`, `core:data`, and `core:designsystem` under `dev.handheld.launcher`, using a manual `AppContainer` and explicit ViewModel factories. Configure Room runtime/testing, KSP, DataStore, and schema export. Register an ordinary launchable Activity with a minimal composable and its acknowledged request-port attachment.

## Acceptance criteria

- [ ] **AC-01** — **Given** the recorded compatible tooling, **when** a developer runs the documented build command, **then** the four-module project builds using the recorded pinned versions.
- [ ] **AC-02** — **Given** the debug application is installed on the initial validation target, **when** it is launched normally, **then** its minimal Activity opens without requiring a feature screen.
- [ ] **AC-03** — **Given** the module dependency graph, **when** it is inspected or compiled, **then** `core:domain` has no Android or Compose dependency and `core:designsystem` imports no product module.
- [ ] **AC-04** — **Given** the initial scaffold’s persistence test fixture or configuration is compiled, **when** it uses Room processing/schema export or DataStore support, **then** those facilities are configured without requiring a later F06 implementation.

## Verification

| Criterion | Evidence | Status |
| --- | --- | --- |
| AC-01 | Planned recorded build command and build output | Not run |
| AC-02 | Planned emulator/manual launch record; physical Flip 2 check remains pending | Not run |
| AC-03 | Planned module dependency inspection and compile result | Not run |
| AC-04 | Planned initial scaffold configuration/fixture compile evidence | Not run |

## Delivery notes

Implementation remains within the F01 initial-creation lease for root/module build files, manifests, `MainActivity`, the DI skeleton, and initial value resources; the exact allowed paths remain in [F01](../features/F01-foundation.md). One F01 writer is active at a time. Source coverage: F01 ordered steps 1–3 and step 5 (normally launchable Activity and minimal composition). Record machine-local paths appropriately; do not claim physical-device validation without evidence.

## Out of scope

- Feature screens, discovery, and final default-HOME or window integration.
- Additional modules, broad permissions, or speculative frameworks.
