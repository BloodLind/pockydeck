# F15 archive extraction engine

`core/data/.../rom/archive/ArchiveExtractor.kt` provides pure Kotlin/Java file I/O. It consumes a local archive that the caller has already copied into its owned staging area; SAF access, free-space budgeting for that copy, cache lifecycle, game selection and publishing remain caller responsibilities.

API:

```kotlin
ArchiveExtractor().extract(
    archive: File,
    format: String,
    outputDirectory: File,
    limits: ExtractionLimits,
    onProgress: (Long) -> Unit = {},
    isCancelled: () -> Boolean = { false },
): ExtractionResult
```

`ExtractionLimits` requires a byte quota and defaults to 20,000 paths, a 512 MiB free-space reserve and 65,536 KiB decoder/parser memory. `ExtractionResult` contains successfully extracted ordinary files and actual output bytes. Files and supporting directory structure retain their archive-relative names. The caller can pass them to `RomScanPlanner` and must present a choice when multiple independent games are found.

## Supported inputs

- ZIP/DOSZ: stored and deflated members, including ZIP64, with central metadata validation and per-file CRC checks.
- 7Z: Commons Compress readers with an explicit parser/LZMA memory limit, no password, and no recovery of damaged headers. Unsupported/encrypted codec paths fail without silently skipping files.
- GZIP, XZ and BZIP2 streams: output basename comes from the provided source filename, never an embedded gzip pathname. Concatenated streams are read through their checksums.
- Ordinary POSIX/USTAR TAR and gzip/xz/bzip2 wrappers (`tar.gz`/`tgz`, `tar.xz`/`txz`, `tar.bz2`/`tbz2`/`tbz`). TAR entries use bounded 512-byte headers and ordinary regular-file/directory records. PAX/GNU extended metadata, sparse files, links and special records return an explicit unsupported-format error; they are not passed into a parser that could allocate an arbitrary metadata buffer.

The engine does not recursively extract nested archives or convert CHD, CSO, RVZ, WIA, ECM, NSZ/XCZ or other emulator-native formats. The launch coordinator decides whether an emulator should receive an archive unchanged, including arcade ROM sets; the engine never assumes an arcade ZIP should be unpacked.

## Integrity and bounds

- Output uses a validated canonical destination. Entry names cannot contain traversal components, absolute/drive paths, backslashes, alternate streams, control characters, Windows device names, excessive length/depth or path aliases that would collide across supported filesystems.
- Explicit and implicit directories count against the path quota. Duplicate names, case collisions, file/directory conflicts, links/reparse points and archive deletion records fail instead of overwriting earlier files.
- Existing destination files are never replaced. Existing link or file parents are rejected. No source files are deleted or modified. Failure leaves only attempted output in the caller-owned partial directory for that owner to discard; the extractor performs no recursive cleanup.
- The total is based on bytes actually produced. Declared sizes are also checked, but never trusted as the sole quota. Available space is checked before each file and each chunk while preserving the configured reserve. Integer subtraction prevents quota overflow.
- Cancellation is checked between entries, central-directory records, TAR records and 32 KiB copy chunks. Progress reports cumulative actual output bytes. Cancellation throws `CancellationException`; corrupt, unsafe, unsupported or over-budget content throws `IOException`.
- ZIP footer/ZIP64 and actual central-directory records are checked before Commons Compress builds its entry objects. Counts, offsets and metadata allocation are bounded. CRC is calculated independently over extracted bytes.
- 7Z applies `setMaxMemoryLimitKiB`, and XZ supplies its memory limit directly to the decoder. Fixed-block ZIP deflate, gzip and bzip2 do not allocate arbitrary LZMA dictionaries.

The extractor assumes a caller-owned private staging directory that other processes cannot replace concurrently; it does not provide a filesystem-wide lock. Archive integrity does not establish ROM validity, BIOS availability or emulator compatibility.

## Test coverage

The JVM suite creates real ZIP, 7Z, TAR, gzip, xz and bzip2 fixtures and covers layout, progress, actual byte quotas, stream sizes, traversal/absolute/drive/alternate-stream paths, duplicates and case collisions, existing-file preservation, ZIP/TAR links, 7Z reparse/deletion records, entry/path quotas, free-space reserve, mid-copy cancellation, corrupt ZIP CRC, encrypted ZIP flags, false central entry counts, LZMA memory limits, native-container rejection and truncated TAR recovery. Validation is performed with the combined application batch.

Implementation references: [Apache Commons Compress user guide](https://commons.apache.org/proper/commons-compress/examples.html), [ZIP API](https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/zip/ZipFile.html), [7Z memory-limit builder API](https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/sevenz/SevenZFile.Builder.html), [TAR entry API](https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/tar/TarArchiveEntry.html), [XZ decoder API](https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/compressors/xz/XZCompressorInputStream.html). The implementation targets Commons Compress 1.28.0 and XZ 1.10.
