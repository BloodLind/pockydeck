# US-053 — Deliver an installable artifact and setup recovery guide

- Parent: [F18 — Flip 2 hardening and installable delivery](../features/F18-device-delivery.md)
- Status: **Planned**
- Type: **Gate**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a launcher owner, I want an identified installable build with clear setup and recovery instructions, so that I can install and use the supported launcher while understanding its verified limits.

## Ready when

- [US-049 — Correct metadata matches and artwork without losing overrides](US-049-manual-metadata-and-artwork-correction.md) is accepted.
- F17 is accepted and F18’s wave-13 gate applies. US-050, US-051, and US-052 remain independent physical gates required for F18 closure.
- Coordinator has approved artifact location/build variant and applicable install/update authorization; documentation preparation may proceed while physical evidence is pending.

## Scope

Build the agreed APK and record variant, version, checksum, and exact supported platform/emulator/provider versions. Verify authorized install/update and migration preservation, retain notices, and write normal/default-HOME setup, mapping, source grant, emulator/provider configuration, Android recovery, offline artwork, revoked storage, unsupported integration, and device-limit guidance.

## Acceptance criteria

- [ ] **AC-01** — **Given** an approved APK build is produced, **when** it is handed off, **then** its artifact location, build type, version, checksum, and supported integration versions are recorded; a debug build is identified honestly.
- [ ] **AC-02** — **Given** authorized install/update and applicable migrations, **when** they are tested, **then** preferences, favorites, and recency are preserved without destructive reset.
- [ ] **AC-03** — **Given** the owner follows the guide, **when** setup or recovery is needed, **then** it covers normal launch, default-HOME choice, controller mapping, sources, emulator/provider configuration, Android launcher/settings return, revoked storage, offline artwork, unsupported integrations, and known limits.
- [ ] **AC-04** — **Given** artifact and documentation handoff is reviewed, **when** physical F18 checks remain pending or failed, **then** those states are linked explicitly and artifact preparation does not close the final feature/release gate.

## Verification

- **AC-01:** Not run — build output, checksum calculation, and integration-version record.
- **AC-02:** Not run — authorized install/update and migration preservation procedure.
- **AC-03:** Not run — manual walkthrough of setup and recovery documents.
- **AC-04:** Not run — coordinator handoff review against F18 accepted/pending evidence.

## Delivery notes

F18 owns setup/recovery documents, evidence, and coordinator-approved artifact output. Build/release configuration remains central; no private signing material belongs in the artifact. Source coverage: F18 ordered steps 6–7 and final handoff. See [F18](../features/F18-device-delivery.md) and [the story standard](story-standard.md).

## Out of scope

External publishing, signing purchases, private signing material, or installing over user data without authorization.
