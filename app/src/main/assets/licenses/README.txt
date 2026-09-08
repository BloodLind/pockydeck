Dependency and icon license and notice sources
=============================================

This directory contains unmodified upstream license and notice texts for the
dependency additions and icon assets. The source mapping below is maintained by
the launcher; it does not change any upstream license or notice.

org.apache.commons:commons-compress:1.28.0
  commons-compress-1.28.0-LICENSE.txt <- JAR META-INF/LICENSE.txt
  commons-compress-1.28.0-NOTICE.txt  <- JAR META-INF/NOTICE.txt
  https://commons.apache.org/proper/commons-compress/

commons-io:commons-io:2.20.0
  commons-io-2.20.0-LICENSE.txt <- JAR META-INF/LICENSE.txt
  commons-io-2.20.0-NOTICE.txt  <- JAR META-INF/NOTICE.txt
  https://commons.apache.org/proper/commons-io/

commons-codec:commons-codec:1.19.0
  commons-codec-1.19.0-LICENSE.txt <- JAR META-INF/LICENSE.txt
  commons-codec-1.19.0-NOTICE.txt  <- JAR META-INF/NOTICE.txt
  https://commons.apache.org/proper/commons-codec/

org.apache.commons:commons-lang3:3.18.0
  commons-lang3-3.18.0-LICENSE.txt <- JAR META-INF/LICENSE.txt
  commons-lang3-3.18.0-NOTICE.txt  <- JAR META-INF/NOTICE.txt
  https://commons.apache.org/proper/commons-lang/

org.tukaani:xz:1.10
  xz-1.10-COPYING.txt <- release v1.10 COPYING
  https://raw.githubusercontent.com/tukaani-project/xz-java/v1.10/COPYING
  https://tukaani.org/xz/java.html
  The binary JAR contains no separate license or notice resource.

androidx.work:work-runtime:2.9.1
androidx.work:work-runtime-ktx:2.9.1
  Apache-2.0.txt <- https://www.apache.org/licenses/LICENSE-2.0.txt
  License declaration: each artifact's published Maven POM.
  https://developer.android.com/jetpack/androidx/releases/work#2.9.1
  Both AARs and their nested classes.jar contain no separate notice resource.

Apache-licensed AndroidX transitive dependencies and
com.google.guava:listenablefuture:1.0
  Apache-2.0.txt also supplies their Apache License, Version 2.0 text.
  ListenableFuture's binary JAR contains no separate notice resource.
  https://github.com/google/guava

Google Material Symbols
  material-symbols-LICENSE.txt <- upstream repository LICENSE
  https://raw.githubusercontent.com/google/material-design-icons/master/LICENSE
  https://developers.google.com/fonts/docs/material_symbols
  Static SVG exports are recorded in docs/references/material-symbols/sources.json.
  Converted to Android VectorDrawable with original path data and a Y offset
  matching the SVG viewBox. No upstream path shapes were modified.

dev.rikka.shizuku:api:13.1.5 and dev.rikka.shizuku:provider:13.1.5
  shizuku-api-13.1.5-LICENSE.txt <- upstream MIT LICENSE
  https://github.com/RikkaApps/Shizuku-API
  Optional, explicitly approved Shizuku integration for read-only process presence.

No dependency source files were modified. Emulator binaries, cores, ROMs and
BIOS files are not included in these assets.
