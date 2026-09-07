# User-story standard

This backlog breaks the F01–F18 feature packets into smaller, independently testable stories. Features remain the ownership and integration groups; a story is the smallest useful outcome assigned within one group. Stories may depend on earlier stories. Independent acceptance does not imply that they can all run in parallel.

## Story types

- **Feature:** a launcher user can perform or observe a useful behavior.
- **Enabler:** a developer gains a concrete capability needed to deliver the launcher, such as a reproducible build or persistent catalog. Use the developer role honestly.
- **Gate:** a tester or owner verifies an integrated product outcome against recorded evidence. Do not describe this as a new end-user screen.

Each story has one primary outcome. Its error, empty, unavailable, restoration, and accessibility cases belong in that story when relevant; do not create separate stories solely to test an existing requirement.

## Required content

1. Stable US ID and title, parent feature link, type, planned status, and assigned implementation model/effort.
2. User statement: “As a [role], I want [capability], so that [benefit].”
3. Dependencies and readiness inputs, including prerequisite-feature and wave gates. The owning feature's final integration gate follows its child stories; it is not a prerequisite of those same children. A future platform/provider choice is a readiness input, not an invented decision.
4. Bounded scope and explicit exclusions where needed to distinguish sibling stories.
5. Numbered, unchecked acceptance criteria with observable Given/When/Then conditions.
6. Verification evidence mapped to the criteria; distinguish automated, manual, and physical-device checks.
7. Delivery notes linking to the parent packet's owned paths and contracts. The smaller story does not grant new write ownership.

## Acceptance criteria

Use this pattern:

```text
- [ ] AC-01 — Given <specific starting state>, when <action or event>,
  then <observable, testable result>.
```

Write enough criteria to accept or reject the story without guessing its behavior. Typically four to eight are sufficient, but the behavior determines the count.

Prefer concrete examples such as `[C, B, A] → open B → [B, C, A]`, no duplicate B, or “the last successful catalog remains visible after a cancelled scan.” Avoid “works correctly,” “fast,” “polished,” or “all edge cases handled” as acceptance.

Implementation steps, agent models, file ownership, code review, and general build commands are delivery requirements. Keep them outside user-behavior acceptance unless the story is specifically an enabler or verification gate.

Preserve the approved boundaries:

- Custom launcher strip and persistent shell; Android owns its notification shade, sound/brightness/power controls, and other applications' windows.
- Most-recently-opened ordering records successful launcher-initiated app/ROM dispatch; it does not establish a running process or guaranteed resume.
- Retain stable selection and valid scroll anchors when the story changes content, routes, or availability.
- Failed or incomplete discovery does not erase cached catalog data or user-owned references.
- Design fixtures never become production status readings. Home is the geometry reference; CSS values are not final native dp/sp constants.
- No HUD, analytics, usage monitoring, process management, or unapproved compatibility expansion.

## Evidence and completion

All stories start **planned** with unchecked criteria. Documentation of a criterion is not evidence that it passes.

For each criterion, record the relevant test result, screenshot comparison, or manual procedure/outcome. Provide device/OS/emulator versions where compatibility matters. Use a current source baseline so results can be tied to the reviewed changes.

An Android emulator or desktop build cannot prove physical Flip 2 controller events, system-keyboard usability, lid behavior, firmware cleanup, SD removal, or display calibration. Mark those checks pending until the required device evidence exists. Do not invent performance targets or measured results; a performance gate uses recorded baseline measurements and explicitly accepted limits.

A story can be code-integrated while a required device criterion is still pending. It is not **accepted/done** until all required criteria and applicable delivery checks have evidence. Report a blocked criterion separately so unaffected stories can progress under the feature dependency rules.

The coordinator accepts stories and updates progress. Workers do not mark their own work accepted. Parent Fxx is complete only when its child stories and parent integration gate are accepted; retain the existing agent, shared-file, and three-worker rules from [the workflow](../agent-workflow.md).
