# Security policy

## Supported versions

Security fixes currently target the latest 0.x preview and `main`. Older previews do not have a separate backport policy. The project has not undergone an independent security audit.

## Report a vulnerability

Use [GitHub's private vulnerability reporting](https://github.com/BloodLind/pockydeck/security/advisories/new). Include the affected version, reproduction steps, likely impact, and a minimal synthetic example.

Do not put exploit details, personal files, tokens, or live game libraries in a public issue. If private reporting is unavailable, open an issue requesting a private contact channel without disclosing the vulnerability.

There is no guaranteed response time. Please allow time to investigate and coordinate a fix before public disclosure.

## Relevant boundaries

The launcher can read selected folders, or shared storage after explicit All files access approval. It exposes registered/prepared ROM content through read-only document grants for emulator launches. Archive preparation has path, entry, byte, free-space, and decoder-memory limits.

Online artwork is optional. Shizuku process readings and the notification indicator require separate opt-in setup. No emulator, ROM, BIOS, or core is bundled.

Release downloads include SHA-256 checksums and signing-certificate details. Preview signing and update compatibility are documented in [getting started](docs/getting-started.md#install-the-preview). Signing keys and local configuration must never be committed.
