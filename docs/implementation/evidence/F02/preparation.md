# F02 preparation note

Planning-only readiness record. F01 is not accepted; no F02 story is started by
this note. Physical Flip 2 calibration is explicitly unverified.

## Readiness and sequencing

- F02 and F06 are eligible for the same parallel wave only after the coordinator
  accepts F01 and its integrated evidence. The user-story gates also name US-003;
  F01 acceptance must include that shared-contract handoff.
- F02 stories are sequential: US-004 (theme/font) → US-005 (ShellMetrics) →
  US-006 (foundation/glyph primitives). US-005 consumes the accepted US-004
  theme; US-006 consumes accepted US-005 metrics.
- F06 stories are sequential: US-017 (Room catalog/cache) → US-018 (recency) →
  US-019 (DataStore preferences/snapshots). F06 may run beside F02, but its
  persistence stories must not run concurrently with one another.

## Initial contracts and leases

F02's one-time creation lease is the design-system theme, foundation, glyph, and
font resources under:

```text
core/designsystem/src/main/kotlin/dev/handheld/launcher/core/designsystem/theme/**
core/designsystem/src/main/kotlin/dev/handheld/launcher/core/designsystem/foundation/**
core/designsystem/src/main/kotlin/dev/handheld/launcher/core/designsystem/glyphs/**
core/designsystem/src/main/res/font/**
core/designsystem/src/main/assets/font-notices/**
matching design-system src/test/kotlin and src/androidTest/kotlin packages
docs/implementation/evidence/F02/**
```

US-004 initially publishes `LauncherTheme` semantic colors, typography, spacing,
shapes, depth, motion, one gradient recipe, bundled Plus Jakarta Sans, and its
notice. US-005 then publishes root-computed `ShellMetrics` from bounds, density,
and font scale, including shared status/content/dock/footer bounds, gutter, card
geometry, reserved focus/lift space, and compact-layout selection. US-006 then
publishes `LauncherText`, `LauncherIcon`, `LauncherSurface`, `FocusFrame`, and
semantic glyph primitives with distinct selected/focused/pressed/unavailable
states and decorative-content semantics. Final names/signatures are registered
by the coordinator in `contracts.md`; later token/signature changes use the
coordinator delta process.

F06's one-time creation lease is:

```text
core/data/src/main/kotlin/dev/handheld/launcher/core/data/local/**
core/data/src/main/kotlin/dev/handheld/launcher/core/data/repository/**
core/data/schemas/**
matching data JVM/instrumentation test packages
docs/implementation/evidence/F06/**
```

US-017 initially owns Room catalog/favorite-reference storage, provenance and
override separation, observable cached queries, completed-inventory atomic
writes, active availability filtering, schema export, and migration setup. US-018
adds serialized successful-open recency and deterministic unopened ordering while
honoring F01 acknowledged-launch semantics. US-019 adds typed confirm/back
preferences (default A-confirm/B-back) and versioned compact navigation snapshots
with safe defaults for malformed/obsolete optional data. Domain interfaces,
Gradle/dependency changes, manifests, and app DI remain coordinator-owned.

## Home crop measurements

Measured from `docs/references/home.png` (1452×837 pixels):

- Launcher content rectangle is approximately x=32..1426 and y=21..805
  inclusive, giving about 1395×785 px. The surrounding dotted editor area and
  black area above are excluded from app comparison.
- The inner left/right shell gutter is about 53 px from the crop edge to the
  first content anchor, or about 3.8% of crop width. This agrees with the
  documented initial target of 3.7–3.8%.
- The visible focused-card amber frame begins near x=85 and y=302 and the
  carousel stage extends to approximately y=556. The first card is about 259 px
  wide; use this only as a screenshot proportion check, not a native dp value.
- The design contract's other initial visual targets remain the source of truth:
  card outer width 18.6–18.8% of available width, card gap about 1.9%, badge top
  about 14.9% of height, focused-card top about 35.6%, dock center about 85.2%,
  and footer top about 92.8%.

## Font/reference checks and limits

Coordinator contrast precheck (opaque sRGB foreground/background pairs, prior to native rendering): `textPrimary` measures 11.80:1 against `backgroundTop`; `textSecondary` measures 5.65:1. The reference `textMuted` measures 3.55:1 against `backgroundTop`, 4.06:1 against `surfaceCard`, and 4.40:1 against `surfaceDock`. Keep essential small text on primary/secondary roles; do not treat the reference muted role as sufficient for essential labels. Native compositing and font-scale rendering still need review in US-004.

The local reference tree contains no `.ttf`, `.otf`, `.woff`, `.woff2`, or `.ttc`
font asset in its launcher files. `docs/references/design-source.txt` only
references the web-loaded Plus Jakarta Sans family at weights 400–800; it also
requests a heavier style, for which the design contract directs an explicit 800
start rather than synthesized 900. The Argosy reference uses generic `SansSerif`
and has unrelated token/layout architecture, so it is not a source for launcher
geometry or persistence contracts.

No device density, font scale, inset behavior, Compose rendering, or physical
Flip 2 screenshot calibration was available in this read-only preparation. Those
checks must remain pending during F02 implementation and handoff.

## Prepared Plus Jakarta Sans assets

The approved local preparation assets are present under the ignored
`.reference/plus-jakarta-sans/` directory. They were fetched from the upstream
Tokotype repository at commit
`18d1cd2f7ea10481919d2f05c1f7064b7307fc26` (the `master` tip observed on 7
September 2026), using the raw URLs below:

```text
https://raw.githubusercontent.com/tokotype/PlusJakartaSans/18d1cd2f7ea10481919d2f05c1f7064b7307fc26/fonts/ttf/PlusJakartaSans-Regular.ttf
https://raw.githubusercontent.com/tokotype/PlusJakartaSans/18d1cd2f7ea10481919d2f05c1f7064b7307fc26/fonts/ttf/PlusJakartaSans-Medium.ttf
https://raw.githubusercontent.com/tokotype/PlusJakartaSans/18d1cd2f7ea10481919d2f05c1f7064b7307fc26/fonts/ttf/PlusJakartaSans-SemiBold.ttf
https://raw.githubusercontent.com/tokotype/PlusJakartaSans/18d1cd2f7ea10481919d2f05c1f7064b7307fc26/fonts/ttf/PlusJakartaSans-Bold.ttf
https://raw.githubusercontent.com/tokotype/PlusJakartaSans/18d1cd2f7ea10481919d2f05c1f7064b7307fc26/fonts/ttf/PlusJakartaSans-ExtraBold.ttf
https://raw.githubusercontent.com/tokotype/PlusJakartaSans/18d1cd2f7ea10481919d2f05c1f7064b7307fc26/OFL.txt
```

The static TTF files and SHA-256 hashes are:

| File | Weight usable by US-004 | SHA-256 |
|---|---:|---|
| `PlusJakartaSans-Regular.ttf` | 400 | `BD6276D4060E3B1EBC45047469E0BB86B08F301BA681CDF1CEB6245EA10478D2` |
| `PlusJakartaSans-Medium.ttf` | 500 | `C77BAB757D7402EC6D9341D5F7DDAAFB2474E17026792697BA4624C7DC89CAF7` |
| `PlusJakartaSans-SemiBold.ttf` | 600 | `65DBCEDB6596A41C30869729EB31CB57D1F5EDFE365684314BBA8A1994EAA4CB` |
| `PlusJakartaSans-Bold.ttf` | 700 | `5F5342EF76862B5B5365D1DFF1A667629DFA484E388DD602552F647219C3870F` |
| `PlusJakartaSans-ExtraBold.ttf` | 800 | `7D60D21B5DEC501C77437E80AABD539F1D7A7B0AC7D4ADA361D4D42ABC7C55EA` |
| `OFL.txt` | license notice | `995C7199CAB65954F545996326755DAEE7B63CC6B42B06C13DA1F9502AB08A99` |

The upstream directory also exposes `fonts/variable/PlusJakartaSans[wght].ttf`,
but the prepared assets intentionally use the upstream static TTFs: the files
are directly usable by Android `Font(R.font...)` resources and map exactly to
the required 400–800 family weights, including the contract's initial explicit
800 heavy style. A simple SFNT table scan found no `fvar` table in the five
prepared files, confirming they are static instances rather than variable fonts.
This avoids requiring variable-font axis handling in the pinned Android 13 /
Compose 2.1.21 / Compose BOM 2024.12.01 setup. The OFL 1.1 text is included as
the required bundled notice; it identifies copyright 2020 The Plus Jakarta Sans
Project Authors and permits embedding/redistribution with the license retained.

Google Fonts' official metadata at
`https://github.com/google/fonts/blob/main/ofl/plusjakartasans/METADATA.pb`
confirms Tokotype as designer, OFL licensing, the same upstream commit, and a
normal `wght` axis from 200 through 800. Its canonical distribution is the
variable `PlusJakartaSans[wght].ttf`; the prepared static instances are from the
same pinned upstream commit and cover the exact 400, 500, 600, 700, and 800
styles required by the design contract. Compose can load either TTF form, but
the static set is the lower-risk choice for the pinned Android 13 toolchain: it
needs no axis-selection or variable-font API behavior and makes each resource's
weight explicit. This is an implementation recommendation for US-004, not a
new visual/style decision.
