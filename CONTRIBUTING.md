# Contributing to PockyDeck

Thanks for helping improve the launcher. Small, focused contributions are easiest to review.

## Report a problem

Use the [issue templates](https://github.com/BloodLind/pockydeck/issues/new/choose). Include the app version, device, Android version, UI/font scale, input method, and steps to reproduce. For ROM issues, include the console folder layout, file extensions, emulator package/version, and whether the same game opens directly in that emulator.

Screenshots and short recordings help with layout and focus bugs. Redact account details, notifications, and personal paths. Do not upload games, BIOS files, keys, save files, or private library databases. Synthetic files or a small directory listing are usually enough.

Report security vulnerabilities through [the private reporting process](SECURITY.md).

## Propose a change

Open an issue before a substantial feature, new dependency, storage migration, permission, network service, or emulator integration. Explain the user problem, expected behavior, and relevant tradeoffs. Small fixes and documentation corrections can go straight to a pull request.

Fork the repository and branch from `main`. Keep changes focused and preserve unrelated work. Follow existing Kotlin formatting and Compose patterns; no separate formatter is required.

## Development conventions

- Keep Android-independent policies in `core/domain`, persistence/platform adapters in `core/data`, reusable visuals in `core/designsystem`, and application orchestration in `app`.
- Follow the [architecture contracts](docs/architecture.md) and [design system](docs/design-system.md). Reuse shared controls instead of introducing a second visual language.
- Preserve stable item IDs, favorites, recency, source grants, and existing Room/DataStore state. Schema changes need exported schemas and migration coverage.
- Reuse semantic controller actions. Check touch, controller, long holds, focus restoration, and cancellation for changed interactions.
- Keep storage, artwork decoding, scanning, and network work off the UI thread. Bound memory and I/O; release images when their cards leave the viewport.
- An emulator adapter needs a primary upstream launch contract, explicit package/activity checks, supported formats, and tests. Distinguish intent acceptance from a game actually booting.
- Add only assets you can contribute under the project license, or include the original third-party license and attribution. Debug examples should use synthetic content.

## Before opening a pull request

Run the relevant checks from [the development guide](docs/development.md). For a broad change, run the combined JVM/lint/build command. UI or platform behavior needs real-device verification when available; describe any untested devices or paths honestly. Documentation-only changes need link and content checks, not a full device test run.

In the pull request, explain the problem, the resulting behavior, and what you verified. Include before/after screenshots for visible changes. Do not check in build outputs, APKs, local settings, signing keys, personal libraries, or large test logs.

Maintainers may request adjustments or defer work that expands the current scope. Contributions are accepted under [Apache 2.0](LICENSE), with existing third-party notices preserved. Follow the [code of conduct](CODE_OF_CONDUCT.md).
