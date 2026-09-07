# US-031 — Edit Search with the native keyboard and controller

- Parent: [F11 — Local Search and system keyboard](../features/F11-search.md)
- Status: **Planned**
- Type: **Feature**
- Implementation agent: `gpt-5.6-terra`, reasoning `high`

## User story

As a launcher user, I want a visible editable query with predictable controller and Back behavior, so that I can enter text without losing results or triggering unrelated actions.

## Ready when

- [US-030 — Find indexed items with local query and category filters](US-030-local-search-results.md) is accepted.
- The coordinator has accepted F08 and provided the F04 shell keyboard policy and F05 input contracts. F11's final integration gate follows acceptance of its child stories.
- Physical Flip 2 keyboard and controller evidence is pending until a device is available.

## Scope

Bind the native Android IME to Compose-owned query focus and handles, including X-to-enter/focus-query behavior. Apply shell keyboard constraints so results use two columns only when readable, otherwise one column with scrolling. Apply IME-first Back and isolate editor input from shell shortcuts and result activation.

## Acceptance criteria

- [ ] **AC-01** — **Given** the user presses X outside Search or while results are focused, **when** Search is entered or already active, **then** the query receives focus and remains visibly editable through the native IME.
- [ ] **AC-02** — **Given** the native IME is visible, **when** Back is invoked, **then** it dismisses the IME before origin navigation can occur.
- [ ] **AC-03** — **Given** text is entered through the IME, **when** the editor processes it, **then** shell shortcuts do not fire and a result is not activated by typing.
- [ ] **AC-04** — **Given** available width or IME space makes two columns unreadable, **when** results render, **then** they switch to one column and scroll within the content area without changing normal shared shell geometry.

## Verification

- **AC-01:** Not run — Compose IME/focus test with X semantic action.
- **AC-02:** Not run — Compose test for IME-first Back then origin policy.
- **AC-03:** Not run — Compose keyboard event test proving shell/action isolation.
- **AC-04:** Not run — constrained-width and IME screenshot tests for two-to-one-column behavior.

**Physical acceptance for AC-01–AC-04:** Not run — repeat query entry, editing, IME-first Back, focus return and constrained layout on the Flip 2 with its installed system keyboard and controller. Record device, firmware and IME versions. Desktop/emulator evidence alone does not close these checks; an incompatible system keyboard is a recorded blocker, not grounds for adding a custom keyboard.

## Delivery notes

This uses the F11 search UI/IME binding lease. Shell and Activity IME policy changes require coordinator integration. Source coverage: F11 ordered steps 3–4. See [F11](../features/F11-search.md) and [the story standard](story-standard.md).

## Out of scope

A custom keyboard, global input interception, and claims of untested physical keyboard usability.
