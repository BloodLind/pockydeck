# ROM implementation dependencies and source references

The ROM feature batch uses these pinned direct dependencies and their Commons dependencies:

| Library | Version | Purpose | License/reference |
| --- | --- | --- | --- |
| Apache Commons Compress | 1.28.0 | ZIP/7Z and stream/archive decoding | Apache-2.0; [project](https://commons.apache.org/proper/commons-compress/), [license](https://github.com/apache/commons-compress/blob/rel/commons-compress-1.28.0/LICENSE.txt) |
| Apache Commons IO | 2.20.0 | Commons Compress dependency | Apache-2.0; original JAR license and notice retained |
| Apache Commons Codec | 1.19.0 | Commons Compress dependency | Apache-2.0; original JAR license and notice retained |
| Apache Commons Lang | 3.18.0 | Commons Compress dependency | Apache-2.0; original JAR license and notice retained |
| XZ for Java | 1.10 | LZMA2/XZ decoding | 0BSD; [project/license](https://tukaani.org/xz/java.html) |
| AndroidX WorkManager | 2.9.1 | Deferrable source reconciliation | Apache-2.0; [release reference](https://developer.android.com/jetpack/androidx/releases/work#2.9.1) |

License resources in dependency JARs are not automatically retained in the APK: the initial assembled debug APK omitted the Commons notices. Original license texts are now included explicitly under [`app/src/main/assets/licenses`](../app/src/main/assets/licenses), which packages them at `assets/licenses/`:

| Component | Files under `assets/licenses/` |
| --- | --- |
| Commons Compress | `commons-compress-1.28.0-LICENSE.txt`, `commons-compress-1.28.0-NOTICE.txt` |
| Commons IO | `commons-io-2.20.0-LICENSE.txt`, `commons-io-2.20.0-NOTICE.txt` |
| Commons Codec | `commons-codec-1.19.0-LICENSE.txt`, `commons-codec-1.19.0-NOTICE.txt` |
| Commons Lang | `commons-lang3-3.18.0-LICENSE.txt`, `commons-lang3-3.18.0-NOTICE.txt` |
| XZ for Java | `xz-1.10-COPYING.txt` |
| WorkManager runtime and Kotlin extensions; Apache-licensed AndroidX and Guava ListenableFuture dependencies | `Apache-2.0.txt` |
| Artifact/source mapping | `README.txt` |

The eight Commons files are byte-for-byte copies of `META-INF/LICENSE.txt` and `META-INF/NOTICE.txt` from the resolved versioned JARs. XZ's JAR has no bundled license resource, so its unmodified [`v1.10/COPYING`](https://github.com/tukaani-project/xz-java/blob/v1.10/COPYING) is supplied. Both WorkManager 2.9.1 AARs, including their nested `classes.jar`, and Guava ListenableFuture 1.0 have no separate distributed notice. The [official Apache-2.0 text](https://www.apache.org/licenses/LICENSE-2.0.txt) matches their published license declaration. The packaged README identifies these sources; this record covers the ROM dependency additions rather than every existing project dependency.

No emulator binary, core, ROM, BIOS, or provider credential is bundled. Debug builds contain the existing UI fixture artwork.

Emulator integration is independently implemented from public Android intent contracts, current upstream sources and read-only installed-version inspection. The [F16 compatibility record](implementation/evidence/F16/emulator-contracts.md) lists exact sources and where the separately cloned Argosy reference corroborated mappings. No Argosy implementation source or architecture was copied into the application.

Synthetic verification archives contain generated test bytes. Native test execution validates decoding and file access; synthetic contents do not establish game-boot compatibility.
