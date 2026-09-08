# Development

## Requirements

- JDK 17.
- Android SDK Platform 34 and Build Tools 34.0.0.
- Gradle 8.9, supplied by the checked-in wrapper.
- An Android 13+ physical device for platform and controller checks.

Open the project in Android Studio or configure `ANDROID_HOME`. A local SDK path can also go in `local.properties`, which is ignored. Download the SDK packages with Android Studio's SDK Manager, or `sdkmanager "platforms;android-34" "build-tools;34.0.0"` after accepting the Android SDK licenses.

## Build and verify

Run from the repository root:

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest :core:designsystem:testDebugUnitTest
```

On Windows use `.\gradlew.bat`. If a Windows JetBrains Runtime reports a Unix-domain socket path error, create `.local/java-tmp` and set `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=.local/java-tmp` for that shell.

Outputs:

| Artifact | Location |
| --- | --- |
| Debug APK, signed with your local debug key | `app/build/outputs/apk/debug/app-debug.apk` |
| Unsigned release APK | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| App lint report | `app/build/reports/lint-results-debug.html` |
| JVM test reports | Each module's `build/reports/tests/` |
| Device test reports | Each tested Android module's `build/reports/androidTests/connected/` |

Release signing is intentionally not configured in source. An unsigned APK cannot be installed. CI builds and checks source but does not publish or sign official releases.

## Device checks

Use `adb devices` and set `ANDROID_SERIAL` to the intended physical device before instrumented tests:

```sh
./gradlew :app:connectedDebugAndroidTest :core:designsystem:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest
```

App instrumentation can interact with the installed launcher and its state. Prefer a dedicated test device/profile or a known backup; do not run broad destructive storage fixtures against a personal library. Ordinary `adb install -r` updates a matching-signature build while retaining app data.

For UI/input changes, verify controller and touch, 100/110/120% launcher scale, larger Android font scale, and reduced motion. Hold a direction for several seconds to reach accelerated movement; test release, reversal, row boundaries, analog triggers, modal return, and focus restoration. Include a large library when changing scanning or artwork behavior.

Debug-only preview Activities under `app/src/debug/` provide synthetic shell/control examples. Their cover art is original geometric vector artwork. They are excluded from release builds.

## Repository layout

| Directory | Responsibility |
| --- | --- |
| `app/` | Activities, ViewModels, screens, input orchestration, discovery scheduling, launch coordination, optional Shizuku bridge |
| `core/domain/` | Models, repository interfaces, catalog/recency/navigation rules, ROM planning, emulator contracts |
| `core/data/` | Room, DataStore, Android adapters, ROM access, extraction, artwork, status |
| `core/designsystem/` | Theme, shell metrics, controls, cards, focus semantics, fonts, vectors |
| `docs/` | Current user, compatibility, design, and development documentation |
| `scripts/audio/` | Reproducible generation of original controller sounds |

The [architecture](architecture.md) and [design system](design-system.md) define the shared rules. Keep exported Room schemas. Build outputs, local reference clones, SDK paths, release packages, and signing material belong outside the tracked source tree.

## Publishing a preview

1. Update versionName/versionCode and the changelog, document known limits, and complete relevant verification.
2. Build the release variant from the intended source commit. Confirm the merged manifest has no debug/test Activities and is not debuggable.
3. Align and sign the unsigned APK with the intended preview signing certificate using Android Build Tools `zipalign` and `apksigner`. Keep the keystore and passwords outside Git.
4. Verify the signature with `apksigner verify --verbose --print-certs`. Record the certificate SHA-256 and APK SHA-256 alongside the APK.
5. Tag the verified commit, create a GitHub **prerelease**, and upload the APK, `SHA256SUMS.txt`, and `SIGNING_CERTIFICATE.txt`. Review download links and the published checksums.

For 0.10.3, the release variant uses the same development certificate as the earlier project-device builds. This preserves updates for that certificate only. CI debug artifacts have their own runner-generated keys and are not official release replacements. See [preview signing details](getting-started.md#install-the-preview).

Do not commit APKs or signing keys. Do not relabel untested emulator contracts as successful game boots. Historical planning and visual evidence remain available in Git history instead of the current documentation tree.
