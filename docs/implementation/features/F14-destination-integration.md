# F14 — Destination integration gate

- Status: **planned**.
- User stories: [US-038](../user-stories/US-038-integrated-destination-journey-gate.md), [US-039](../user-stories/US-039-native-lifecycle-and-catalog-gate.md). Dispatch one ready story; this packet remains the technical ownership and feature-gate reference.
- Agent: `gpt-5.6-sol`, reasoning `high`; integration reviewer.
- Outcome: the native-app launcher works as one product across six destinations, details, controller input, touch and the system keyboard before ROM work begins.
- Prerequisites: F09, F10, F11, F12 and F13 integrated and individually accepted; F08 HOME loop still passes.

## Ownership and contracts

Owns `app/src/androidTest/kotlin/dev/handheld/launcher/integration/destinations/**`, `app/src/test/kotlin/dev/handheld/launcher/integration/destinations/**`, `docs/implementation/evidence/F14/**`. Read all code. The coordinator grants explicit temporary leases for any integration fix in reserved files; otherwise route each concrete bug to its existing packet owner. No blanket write lease over feature/data/theme directories.

Consumes all native-app feature contracts and evidence. Provides a reviewed integrated acceptance matrix, a bounded defect list with ownership, and a native-app stage release candidate. This packet is a real completion gate, not permission to redesign contracts or expand scope.

## Ordered steps

1. Review the integrated diff for module boundaries, one shell, one launch/recency path, one action/footer descriptor, and one input dispatch owner. Check that all six registered destinations have real behavior rather than finished-product placeholders.
2. Run the actual debug build, lint, relevant JVM tests and applicable Compose instrumentation tests. Fix integration failures through the reserved-file/packet-owner process, then rerun the affected checks.
3. Exercise a journey across Home → Library/Apps → details/favorite → Favorites → shortcut Search → IME Back → origin → Settings → Home. Include touch-to-controller changes, filter/sort changes, empty results and selected-item removal.
4. Exercise app launch/return, repeated Home intents, Activity recreation and process restoration. Validate external task separation; verify no action replay and no duplicate recent entries.
5. Exercise install/update/uninstall with the launcher foreground and inactive; failed inventory cannot empty cache. Verify cached rendering remains independent of discovery and status work.
6. Compare Home/Library/Apps/Favorites/Search/Settings normal shell anchors and focus bounds. Assess large fonts, keyboard constraints and controller reachability. Record physical Flip 2 gaps explicitly; correct verified central geometry through coordinator rather than page-specific compensations.
7. Publish the native-app gate result and concrete late-stage decision questions for supported ROM formats/emulators/provider when needed. Those later decisions do not invalidate or block completed native-app functionality.

## Excludes

No speculative architecture refactor, blanket performance promises, new product features, silently changed shared contract/schema, or premature ROM/provider implementation.

## Validation and handoff

Attach actual test output, route/interaction evidence and unresolved hardware checks. The matrix must include per-destination state, query/IME origin, availability errors, no false running status, Android controls, and continued use without default-HOME approval. Close functional blockers or identify the explicit user/device dependency; do not label unrun tests passed. F15 starts only after the native-app integration gate and its own support decision are satisfied.
