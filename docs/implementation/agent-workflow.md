# Implementing Handheld Launcher with agents

Prepared 7 September 2026. This is an execution guide, not a record of implemented features. The feature decomposition was delegated to `gpt-6-astra` with `ultra` reasoning as requested. Implementation is underway; see [progress](progress.md) for the current reviewed state.

Read [the feature plan](feature-plan.md) for the exact task packets, dependency graph, file ownership, and acceptance criteria. Keep [the project plan](../project-plan.md) and [design system](../design-system.md) as the product/design baseline.

The [user-story backlog](user-stories/README.md) defines acceptance and the parent packet defines technical ownership. The user's 8 September instruction supersedes the original per-story and wave schedule below: build a complete native application base, then run a large integrated verification batch and reconcile the documentation. Parallel workers receive disjoint directory leases. Do not repeat approval gates or per-feature test cycles that this instruction replaced. Report unrun physical checks separately from implemented behavior.

## 1. Model assignments

Use Astra Ultra for this decomposition, then use the following implementation settings. Select the model and reasoning effort in the task configuration or agent dispatch; writing a model name inside a prompt does not change the running model.

| Job | Model ID | Reasoning effort | When to use it |
|---|---|---|---|
| Feature decomposition | `gpt-6-astra` | `ultra` | This planning pass; do not rerun for every task |
| Coordinator | `gpt-5.6-terra` | `high` | Select ready stories, dispatch agents, manage ownership, update progress |
| Normal feature implementation | `gpt-5.6-terra` | `high` | Page composition, ViewModels, catalog queries, feature integration |
| Small defined UI/documentation work | `gpt-5.6-luna` | `medium` | Token application, bounded control variants, preview/gallery additions, delivery docs |
| Android/state/storage-critical work | `gpt-5.6-sol` | `high` | HOME/task behavior, input/focus, persistence/reconciliation, SAF/emulator adapters, integration gates |
| Read-only feature review | `gpt-5.6-terra` | `medium` | Ordinary packet review; use Sol High for critical lifecycle/data changes |

Each story inherits its parent feature's model assignment unless an explicitly recorded assignment says otherwise. These assignments take precedence over this general table. Keep reviewers separate from the author where possible, but do not create a review swarm for a small change.

These model choices aim to reduce cost while keeping difficult Android work on a stronger model. OpenAI describes [Terra](https://developers.openai.com/api/docs/models/gpt-5.6-terra) as balancing intelligence and cost and [Luna](https://developers.openai.com/api/docs/models/gpt-5.6-luna) as suited to cost-sensitive workloads. Their published API token prices, and those of [Sol](https://developers.openai.com/api/docs/models/gpt-5.6-sol), are below [Astra's](https://developers.openai.com/api/docs/models/gpt-6-astra) at this writing. That comparison is not a project-cost estimate or a promise about Codex subscription accounting. The `ultra` setting used here is supported by this Codex agent runtime; do not assume it is an API reasoning-effort value.

## 2. Working arrangement

Use one coordinator and up to three concurrent workers. This session has four available active-agent slots in total. Independent agents can work in parallel only when both prerequisites and write ownership permit it.

Default to subagents of the coordinator for individual feature work. They share the workspace: edits appear immediately, so assigning different tasks does not itself isolate files. A packet is an ownership boundary, not an isolated checkout.

The coordinator maintains the authoritative implementation status for each US and a summary for its parent Fxx. Workers return a handoff report; they do not all edit a common progress table. Use these states:

- `planned`: not yet ready or not dispatched.
- `ready`: required story/feature dependencies accepted and ownership available.
- `in_progress`: one assigned owner is working.
- `review`: implementation handed off; checks/review pending.
- `integrated`: code reviewed and integrated against the current combined source state; outstanding acceptance evidence remains visible.
- `accepted`: all story acceptance criteria and applicable delivery checks have evidence.
- `blocked`: a specific dependency, environment requirement, or user decision prevents completion.

Record physical-device acceptance separately. A story may have its code integrated while a required device criterion remains unverified; it is not accepted/done in that state. Any later story/feature explicitly depending on that evidence must wait. A passing desktop/emulator test is not a passing Flip 2 test. Close the parent feature only after its child stories and feature gate are accepted.

At implementation kickoff, the coordinator should create a small `progress.md` in this directory containing story state, parent-feature summary, owner/model, baseline, evidence, device-gate state, and blockers. It is intentionally not populated with fictitious completion records during planning.

## 3. Step-by-step execution

### Step 1 — Begin with US-001 under F01

Have a Sol High agent implement US-001, then proceed through the remaining ready F01 stories one at a time. Confirm the proposed contracts against the approved plan and establish the four-module build, basic tests/fakes, and ownership map before other feature agents write production code. Do not treat completion of US-001 as completion of all F01 work.

Check the existing repository state first. At planning time the repository had no commits and only documentation. Preserve unrelated user changes. If using worktrees later, establish a reviewed local commit baseline first; do not try to branch worktrees from an unborn HEAD. No push, publication, or remote task creation is implied by this guide.

### Step 2 — Run the ready feature wave

Use the wave table in [feature-plan.md](feature-plan.md). Within its ready parent features, dispatch one worker per ready story with its specified model and effort. Pass the story, parent packet, baseline documents, current contracts, and dependency handoffs. A story's dependencies and prerequisite-feature/wave gates must be satisfied. The owning feature's final integration gate follows its children; it is not a prerequisite of those same stories.

The planned sequence is:

```text
F01
  → F02 + F06
  → F03 + F07
  → F04
  → F05
  → F08                 first working HOME loop
  → F09 + F10 + F11
  → F12 + F13
  → F14                 integrated destination gate
  → F15
  → F16
  → F17
  → F18                 physical-device hardening and delivery
```

`+` means eligible for parallel work, not mandatory concurrency. The coordinator may serialize a wave if ownership, machine resources, or a discovered dependency makes parallel execution unsafe. Dispatch ready stories inside the current wave rather than all feature groups at once.

### Step 3 — Keep shared-file changes serialized

Honor each packet's owned paths. F01/F02 and the other explicit creation leases in the feature plan establish their initial shared definitions. Those leases last only for the assigned packet; the coordinator remains the integration owner, and later workers consume the definitions rather than inventing new variants independently.

When a worker needs a shared change outside its ownership, it sends the coordinator a small proposed delta: file/symbol, reason, affected consumers, and validation impact. The designated integration owner applies and validates the change while other writers to that surface are paused. This is a coordination step, not an automatic request for user permission.

Shared surfaces include Gradle/version catalogs, Manifest/Activity setup, dependency wiring, route registration, shared domain contracts, Room database/schema/migrations, durable preference keys, and theme tokens. File ownership can be temporarily reassigned for a bounded integration task; never leave two active writers to the same surface.

### Step 4 — Review and integrate each completed story

The author returns the handoff below. A separate reviewer checks scope, actual diff, contract compatibility, and evidence. The coordinator then integrates the shared wiring and runs the smallest meaningful checks on the combined source state.

Shared-workspace integration means reviewing already-visible edits and completing wiring; do not cherry-pick those same changes. Worktree integration, if chosen later, uses reviewed commits applied by one integrator. In both arrangements, recheck the current baseline before declaring the story integrated, and its evidence before declaring it accepted.

Only one owner runs a broad integration build against a stable wave result. Workers may run focused tests for their own changes. Coordinate repository-wide Gradle tasks and generated/shared test artifacts so concurrent tools do not invalidate each other's results.

### Step 5 — Pass the gate before moving on

F08 establishes a real default-HOME → launch app → return → reordered Recent/restored selection loop. F14 checks the complete launcher navigation before storage/network features expand it. F18 verifies the final device behavior and deliverables.

Do not mark a physical gate complete without a connected Flip 2 and recorded evidence. If the device is unavailable, finish independent code/test work and report the missing checks precisely. The gate owner decides which dependent work is ready according to the packet; do not silently replace physical acceptance with emulator results.

For a stepwise workflow, stop after the requested story or wave and report its status. For a later explicitly authorized full implementation run, the coordinator may continue through ready stories/waves without repeatedly asking permission for routine work.

## 4. Copy-ready coordinator prompt

Configure the coordinator as **GPT-5.6 Terra / High**, then use:

```text
Implement the next ready user story of Handheld Launcher using the backlog in
docs/implementation/user-stories/README.md, its parent feature packet, and
docs/implementation/agent-workflow.md.

Read the current source and implementation progress first. If implementation has
not started, dispatch US-001 only. Do not repeat the Astra decomposition.

Use each story's exact model and reasoning effort. Spawn subagents for bounded
story work, with at most three active workers alongside you and one writer per
parent feature. Explicitly set the model/effort at dispatch and give each worker
its story, parent packet, and owned paths. Do not
inherit an expensive model accidentally and do not create separate user-owned
Codex tasks for these subtasks.

Keep shared contracts, Gradle, Manifest, DI, navigation, database/schema, and theme
changes under one designated owner at a time. Preserve the agreed MVVM and shared
shell design. Home is the only exact layout reference. Custom launcher strip is
required; native system controls stay Android-owned. Home recency is most recently
opened first with stable selection/scroll restoration. Do not add HUDs, analytics,
running-app management, or other excluded features.

Review each story's changes and acceptance evidence before integration. Update progress
yourself. Do not claim tests/device checks passed unless actually run. Ask me only
when a newly discovered material ambiguity requires a product/API/schema/reliability
decision under my AGENTS.md rule; routine agreed implementation does not need
reapproval. Never escalate to Astra automatically.

Stop after this story's integration report. Include accepted/integrated/blocked
stories, criterion-level evidence, unresolved device checks, and the next ready US.
```

To start a particular ready story, name its US ID in the opening instruction. To authorize a whole feature wave later, request its child stories explicitly and retain one writer per parent feature plus the three-worker limit. Keep the ownership and validation rules unchanged.

## 5. Copy-ready worker prompt

The coordinator fills in all placeholders and sets the actual model/effort before dispatch:

```text
Implement only <US ID — story title> from <story path>.
Parent feature and technical scope: <Fxx packet path>.

Assigned model/effort: <model ID> / <effort>.
Baseline: <commit or recorded shared-workspace state>.
Dependency handoffs: <accepted story/feature IDs and contract/evidence paths>.
Owned paths: <story subset of the parent packet's exact paths>.
Integration owner: <coordinator or named gate owner>.

Read the story, parent packet, docs/project-plan.md, docs/design-system.md, and the
contracts it consumes. Implement the story's scope using the relevant parent
steps. Satisfy every acceptance criterion and map each to actual evidence.
Use the current files as truth; do not assume planning examples are implemented.

Modify only owned paths. Report any needed shared-file change as a proposed delta
to the integration owner. Do not redefine shell geometry, repository interfaces,
database schema, theme tokens, or controller behavior from within a feature.
Do not implement a sibling story or spawn more implementation workers.

Preserve other agents' work. Run the focused checks required for this story and
provide the standard handoff report. Distinguish passed, failed, and not-run checks.
Stop when this story is ready for review or when a specific blocker prevents it.
Do not mark the story accepted or its parent feature complete yourself.
```

## 6. Copy-ready review prompt

Use **Terra Medium** for ordinary features and **Sol High** for lifecycle/input/storage changes and gates:

```text
Review story <US ID> against <story path>, its parent feature packet, and the
current combined source state. Check every numbered acceptance criterion and
its supplied evidence.
This is read-only review; do not edit files or repeat the entire implementation.

Read the author's handoff and inspect the actual diff. Check scope, owned paths,
shared API compatibility, MVVM/control separation, and the packet's failure cases.
For UI, verify reuse of the Home-derived shell/theme and controller focus rules.
For lifecycle/data work, inspect ordering, cancellation, task isolation, and
restoration/reconciliation behavior.

Report only actionable findings with file/line and consequence. Identify missing
evidence without claiming an unrun test failed. If the source changed since the
author's checks, state which checks need rerunning on the new state. If there are
no actionable findings, say so and list remaining unverified device gates.
```

## 7. Handoff report

Every worker returns this compact structure, using repository-relative paths inside the report:

```text
Story: <US ID/title>
Parent feature: <Fxx>
Model/effort: <actual dispatch settings>
Result: ready for review | blocked
Baseline: <revision or coordinator baseline>

Behavior delivered:
- <what now works>

Changed paths:
- <path and responsibility>

Shared changes requested:
- <none, or exact proposed file/symbol delta and consumers>

Validation:
- <AC ID(s)> — <command/check> — passed | failed | not run — <evidence/reason>
- <screenshots/device steps where required>

Known limits/blockers:
- <concrete issue; state who/what can resolve it>

Next handoff:
- <provided symbols/contracts and stories now potentially ready>
```

For failed checks, preserve the failure and the final result after a fix. Do not fill the handoff with full logs; link or point to the useful output. Do not mark instrumentation, reboot, lid, physical-controller, or firmware tests as passed merely because a debug APK builds.

## 8. Cost and escalation rules

- Give each worker a fresh bounded task with file references and a short dependency handoff. Avoid copying the entire conversation or generated PRD into every prompt.
- Let the packet author fix a concrete review finding. Escalate after evidence shows a capability gap, not just because a tool call is slow.
- For a stalled UI packet, narrow the issue and move from Luna to Terra High. For a demonstrated Android/lifecycle/data-integrity problem, use Sol High with the failing case and current diff.
- Do not automatically move to Astra Ultra. Bring a genuinely unresolved architectural decision to the user with concrete options; the user can choose whether another Astra pass is useful.
- Classify blockers as code, environment, missing capability/device, or material decision. Higher reasoning effort will not connect a missing device or install an unavailable SDK by itself.
- Do not run broad searches, reread every document, or rerun every test after a local change unless new evidence warrants it.
- Keep independent work moving when one packet is blocked. Pause the dependent path, not unrelated ready packets.

## 9. When the coordinator asks the user

The supplied AGENTS.md requires a pause for meaningful conflicts and changes affecting product behavior, shared API contracts, schema/migrations, security, reliability, or destructive actions. Apply that rule to new material choices, not to work already specified by these approved plans.

First make the issue concrete: identify the affected packet/contract, explain the consequence, and present one to three options with a recommendation. Continue unaffected work where possible. Once answered, record the decision and do not ask again.

Expected later inputs, only when their packets become actionable, include the actual ROM root granted on the device, installed emulator choices where there is no obvious compatible default, and metadata-provider access. Missing those inputs does not block the design library or native Android launch loop.
