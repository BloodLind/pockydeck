# F17 — Metadata and artwork enrichment

- Status: **Artwork implemented in 0.4.0; manual metadata/match correction and remaining acceptance checks are open**. See [artwork delivery evidence](../evidence/F17/artwork-040.md).
- User stories: [US-047](../user-stories/US-047-durable-metadata-enrichment.md), [US-048](../user-stories/US-048-progressive-cached-artwork.md), [US-049](../user-stories/US-049-manual-metadata-and-artwork-correction.md). Dispatch one ready story; this packet remains the technical ownership and feature-gate reference.
- Agent: `gpt-5.6-sol`, reasoning `high`.
- Outcome: locally launchable items gain cached metadata/artwork progressively, with correction controls and reliable offline behavior.
- Prerequisites: F16 accepted; one named provider chosen with terms/attribution, credential approach, data sent, limits and cost understood. Prepare concrete options and ask the user before a materially different provider/network/security choice; planning and existing offline functionality remain usable meanwhile.

The user's integrated delivery workflow supersedes the former per-story dispatch gates. On 8 September the user explicitly approved: “Allow Libretro downloads and install.” Version 0.4.0 reuses ES-DE media and uses only Libretro's public thumbnail host for missing ROM artwork. The [request, cache and preservation contract](../evidence/F17/artwork-040.md) records the implemented bounds. This does not approve a second provider or close US-049.

## Ownership and contracts

Owns `core/data/src/main/kotlin/dev/handheld/launcher/core/data/metadata/**`, `app/src/main/kotlin/dev/handheld/launcher/ui/artwork/enriched/**`, `app/src/main/kotlin/dev/handheld/launcher/feature/settings/metadata/**`, `app/src/main/kotlin/dev/handheld/launcher/feature/details/artwork/**`; matching data/app JVM/instrumentation test packages; `docs/implementation/evidence/F17/**`. Shared item/presentation signatures, artwork cache schema/DAO/migrations, credentials integration, Internet/dependency manifests, WorkManager wiring, DI and settings/details registration are coordinator-owned. F08's local artwork loader remains outside this lease.

Consumes stable item/source identities, local icon/fallback presentation, existing shared launch rules and settings/details slots. Provides one provider adapter, durable enrichment jobs/cache/provenance, user-correction operations and an artwork presentation adapter. Coordinator accepts concrete provider/cache/correction contracts and preservation semantics before consumers proceed.

## Ordered steps

1. Record the approved provider's request contract, matching rules, rate limits, attribution and authentication requirements. Do not hardcode or expose credentials. Define no-match/ambiguous/offline behavior and which local fields or identifiers leave the device.
2. Submit a bounded schema/cache/queue proposal. Keep fetched fields, provenance, discovery data and manual overrides separate; rescan/provider refresh must not erase a correction, favorite or recent history.
3. Implement asynchronous requests with bounded retries/backoff, cancellation and rate-limit handling. Persist deferrable jobs through WorkManager; do not make initial catalog rendering or launching wait for workers/network.
4. Persist reusable metadata/artwork references and a bounded image cache. Decode near displayed dimensions; use existing local fallback immediately. Late/failed images replace only content inside fixed bounds and cannot reorder items or steal focus.
5. Add provider configuration/status and functional manual match/artwork correction through shared settings/details controls. Preserve correction provenance and origin focus; do not silently overwrite an explicit correction on retry.
6. Ask coordinator to integrate the provider and enriched artwork adapter behind the existing presentation contract, Internet permission and dependencies. Keep local icons/fallback and launch paths independently operational.

## Excludes

No second provider, online/global Search, analytics, blocking scrape screens as normal maintenance, cloud saves, arbitrary unreviewed user-data upload, or provider failure disabling local launch.

## Validation and handoff

Test offline/no match/ambiguous/rate-limited responses, bounded retries, cancellation/process restart, cache reuse/eviction, manual override preservation and late image arrival during focus/scroll. Verify local Android and ROM launches with network unavailable. Capture fixed card geometry before/after artwork and provide provider request/privacy/attribution notes plus actual migration/job tests. Coordinator accepts integrated contracts and offline behavior before final delivery work.
