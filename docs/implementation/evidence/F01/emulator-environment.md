# F01 emulator environment

Prepared 7 September 2026 for software validation. This is not a physical Flip 2 result.

- Android Studio bundled JBR: OpenJDK 17.0.11.
- Android Emulator: 35.2.10.0, Windows Hypervisor Platform acceleration available.
- Android SDK already contained platform 34, Build Tools 34.0.0 and 35.0.0, and an API 35 Google Play image.
- Added the official `system-images;android-33;default;x86_64` package, revision 2, using the installed SDK license. The SDK manager returned exit code 0. No license prompt was accepted by automation.
- Created `Handheld_Foundation_API_33` under ignored `.local/android/avd`; existing user AVDs were not changed.
- Runtime: Android 13 / API 33, x86_64; 1920×1080 at 240 dpi. This is a software test configuration, not a measured Flip 2 density.
- ADB serial: `emulator-5556`. Boot verification returned `sys.boot_completed=1`, Android release `13`, and boot animation `stopped`.
- Emulator runs headlessly with `-no-window -no-audio -no-snapshot -gpu swiftshader_indirect -no-boot-anim -port 5556`.

The installed Android Studio contains SDK manager classes. The package was installed with its JBR and classpath `plugins/android/lib/*;lib/*`, main class `com.android.sdklib.tool.sdkmanager.SdkManagerCli`, explicit `--sdk_root`, and the package identifier above. AVD creation used `com.android.sdklib.tool.AvdManagerCli` with `com.android.sdkmanager.toolsdir`; its generated image path was corrected in the new local AVD to the installed SDK's absolute image path.

Android SDK access and downloads required sandbox escalation. Machine paths, emulator data, logs, and Gradle caches remain untracked.

## Host Java connection diagnostic

The first Gradle invocation failed before project compilation: Java could not create its local selector pipe (`Unable to establish loopback connection`, caused by `UnixDomainSockets.connect: Invalid argument`). A standalone `Selector.open()` probe reproduced the problem. Setting the process-local JVM property `jdk.net.unixdomain.tmpdir` to the existing workspace `.local/java-tmp` directory made the same probe succeed with `sun.nio.ch.WEPollSelectorImpl`. Changing the selector provider alone did not fix it. The build runner must pass the working property to both client and child JVMs on this host; no global Java settings were changed.

The property directs local socket files to a usable task directory. See [OpenJDK's Unix-domain socket implementation](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/sun/nio/ch/UnixDomainSockets.java). Actual build results are recorded in the story handoff.

Ordinary Activity launch evidence is recorded separately after an APK is available. Controller hardware, firmware, lid/reboot behavior, and physical display calibration remain pending.

References: [SDK manager](https://developer.android.com/tools/sdkmanager), [emulator command line](https://developer.android.com/studio/run/emulator-commandline).
