# US-047 — Enrich metadata through a durable bounded provider queue

- Parent: [F17 — Metadata and artwork enrichment](../features/F17-metadata-artwork.md)
- Status: **Implemented for artwork; acceptance coverage partial**
- Type: **Enabler**
- Implementation agent: `gpt-5.6-sol`, reasoning `high`

## User story

As a developer, I want one approved provider adapter with persistent non-blocking work, so that enrichment can recover from network and process failures while local use continues.

## Ready when

- [US-046 — Choose default and item-specific emulators](US-046-emulator-default-and-item-choice.md) is accepted.
- F16 is accepted; F17’s wave-12 gate remains mandatory.
- One provider’s terms, attribution, credentials approach, data sent, rate limits/cost, and offline behavior are recorded and approved. Any missing provider decision remains a readiness input.

## Scope

Record the approved request, matching, authentication, rate-limit, attribution, and data-sharing contract. Publish cache/provenance/queue separation for fetched, discovery, and manual fields. Implement asynchronous bounded retry/backoff, cancellation, rate-limit handling, and durable WorkManager jobs without blocking local rendering or launch.

## Acceptance criteria

- [ ] **AC-01** — **Given** offline, no-match, ambiguous, or rate-limited enrichment, **when** a job runs, **then** it reports explicit recoverable state without blocking local catalog rendering or launch.
- [ ] **AC-02** — **Given** cancellation or process restart during a request, **when** durable work resumes, **then** it recovers with bounded retry/backoff and does not create uncontrolled duplicate requests.
- [ ] **AC-03** — **Given** fetched metadata, discovery data, manual correction, favorite, or recent state, **when** cache/migration operations occur, **then** provenance and user-owned fields remain separate and preserved.
- [ ] **AC-04** — **Given** a provider request is emitted, **when** it is inspected, **then** it follows only the approved provider/data/authentication contract and exposes no hardcoded credential.

## Verification

- **AC-01:** Offline local reuse, transient retry state, deterministic edition/sequel matching and no-match behavior exercised. Live Libretro downloads verified on the Flip 2. Full HTTP 429/503 transport coverage remains open.
- **AC-02:** Room restart preserves pause/retry/backoff state; physical restart and live WorkManager continuation inspected. Cancellation at every network/cache boundary and the complete duplicate-work matrix remain open.
- **AC-03:** Artwork uses a separate version-1 database; the catalog schema is unchanged. Tests exercise local media preservation and ready-cache preservation on retry. Existing manual artwork takes precedence in presentation and is excluded from the automatic queue; full correction acceptance belongs to US-049.
- **AC-04:** Reviewed allowlisted HTTPS GETs for console indexes and matched public filenames, with no credentials, request bodies, ROM/save files or local paths sent. User explicitly approved Libretro downloads and installation. See [0.4.0 evidence](../evidence/F17/artwork-040.md).

## Delivery notes

F17 owns metadata adapter/jobs and evidence; schema, credentials, Internet, WorkManager wiring, and DI remain coordinator owned. Source coverage: F17 ordered steps 1–3. See [F17](../features/F17-metadata-artwork.md) and [the story standard](story-standard.md).

## Out of scope

A second provider, arbitrary uploads, analytics, and blocking scrape screens.
