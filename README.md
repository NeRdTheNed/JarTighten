# JarTighten

> Jar file size optimiser.

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.nerdthened.jartighten)](https://plugins.gradle.org/plugin/io.github.nerdthened.jartighten)
[![Build](https://github.com/NeRdTheNed/JarTighten/actions/workflows/gradle.yml/badge.svg)](https://github.com/NeRdTheNed/JarTighten/actions/workflows/gradle.yml)

JarTighten is a jar file size optimiser, including optimisations based on quirks of Java's zip parsing implementation. Although JarTighten is intended to be run as the final step in the optimisation pipeline (after deflate optimisation), it does include options for recompressing files with [CafeUndZopfli](https://github.com/eustas/CafeUndZopfli).

## Usage

JarTighten is available as a command line program:

```
Usage: JarTighten [-cdDEhijJlLMnorRsStVzZ] [--compare-size-bits]
                  [--optimise-existing-streams]
                  [--optimise-recompressed-streams]
                  [-I=<recompressZopfliPasses>] [-m=<mode>] [-e=<filename>]...
                  <inputFile> <outputFile>
Jar file size optimiser
      <inputFile>            The file to optimise
      <outputFile>           The optimised file
  -c, --remove-comments      Remove file comments and zip comment
      --compare-size-bits    Compare sizes of deflate streams in bits instead
                               of bytes. Majorly increases time spent
                               optimising files.
  -d, --[no-]remove-directory-entries
                             Remove directory entries
  -D, --deduplicate-entries  Deduplicate local file header entries with the
                               same compressed contents
  -e, --exclude=<filename>   Files to exclude from optimisations which might
                               hide them from standard zip libraries
  -E, --remove-extra         Remove extra field
  -h, --help                 Show this help message and exit.
  -i, --remove-eocd-info     Remove EOCD info
  -I, --iter, --zopfli-iter=<recompressZopfliPasses>
                             Zopfli iterations. More iterations increases time
                               spent optimising files.
  -j, --recompress-jzopfli   Recompress files with jzopfli, uses compressed
                               output if smaller
  -J, --[no-]recompress-jzlib
                             Recompress files with JZlib, uses compressed
                               output if smaller
  -l, --remove-file-length   Remove file length from local file headers
  -L, --remove-dir-entry-length
                             Remove file length from central directory entries
  -m, --mode=<mode>          Determines which compression strategies are run
                               for each compressor. Improves compression at the
                               cost of running each selected compressor
                               multiple times. Valid values: SINGLE,
                               MULTI_CHEAP, EXTENSIVE
  -M, --[no-]recompress-multithread
                             Run each compressor in a separate thread. May
                               improve performance.
  -n, --remove-file-names    Remove file names from local file headers
  -o, --overwrite            Overwrite existing output file
      --optimise-existing-streams
                             Optimise existing deflate streams. Majorly
                               increases time spent optimising files.
      --optimise-recompressed-streams
                             Optimise recompressed deflate streams. Majorly
                               increases time spent optimising files.
  -r, --[no-]recompress-standard
                             Recompress files with standard Java deflate
                               implementation, uses compressed output if smaller
  -R, --recursive-store      Store the contents of all embedded zip or jar
                               files uncompressed recursively and compress,
                               uses compressed output if smaller
  -s, --[no-]recompress-store
                             Check uncompressed size, stores uncompressed if
                               smaller
  -S, --sort-entries         Sort zip entries in the way they're expected to be
                               in a jar file
  -t, --remove-timestamps    Remove timestamps
  -V, --version              Print version information and exit.
  -z, --recompress-zopfli    Recompress files with CafeUndZopfli, uses
                               compressed output if smaller
  -Z, --zero-local-file-headers
                             Replace every value that the JVM doesn't read in
                               local file headers with zeros. Overrides other
                               options.
```

A Gradle plugin with equivalent options is also available on the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.nerdthened.jartighten):

```groovy
plugins {
    id 'io.github.nerdthened.jartighten' version '1.2.+'
}

jartighten {
    // By default, JarTighten will optimise the output of the jar task.
    //inputFile = layout.projectDirectory.file(...)
    //outputFile = layout.projectDirectory.file(...)

    // Remove file timestamps
    removeTimestamps = true
    // Remove local header file length
    //removeFileLength = true
    // Remove central directory entry file length
    //removeDirEntryLength = true
    // Remove local header file names
    //removeFileNames = true
    // Remove end of central directory info
    //removeEOCDInfo = true
    // Remove file commments and zip comment
    removeComments = true
    // Remove extra field
    removeExtra = true
    // Remove directory entries
    removeDirectoryEntries = true
    // Deduplicate local file header entries with the same compressed contents
    //deduplicateEntries = true
    // Enable CafeUndZopfli recompression (very time consuming, may require configuring Gradle to use more memory)
    recompressZopfli = true
    // Enable jzopfli recompression (very time consuming, may require configuring Gradle to use more memory)
    //recompressJZopfli = true
    // Zopfli iterations. More iterations increases time spent optimising files.
    //recompressZopfliPasses = 20
    // Disable JZlib recompression (enabled by default)
    //recompressJZlib = false
    // Disable standard JVM deflate recompression (enabled by default)
    //recompressStandard = false
    // Disable checking if storing a file as uncompressed would be smaller (enabled by default)
    //recompressStore = false
    // Disable running each compressor in a separate thread
    //recompressMultithread = false
    // Determines which compression strategies are run for each compressor.
    // Improves compression at the cost of running each selected compressor multiple times.
    // Valid values: SINGLE, MULTI_CHEAP, EXTENSIVE
    //mode = 'EXTENSIVE'
    // Store the contents of all embedded zip or jar files uncompressed recursively and compress, uses compressed output if smaller
    recursiveStore = true
    // Sort zip entries in the way they're expected to be in a jar file
    sortEntries = true
    // Zero all metadata that the JVM doesn't read. Overrides other options.
    //zeroLocalFileHeaders = true
    // Optimise existing deflate streams with deft4j. Majorly increases time spent optimising files.
    //optimiseDeflateStreamExisting = true
    // Optimise recompressed deflate streams with deft4j. Majorly increases time spent optimising files.
    //optimiseDeflateStreamRecompress = true
    // Compare sizes of deflate streams in bits instead of bytes. Majorly increases time spent optimising files.
    //compareDeflateStreamBits = true

    // Exclude a file from optimisations which might hide them from standard zip libraries
    //excludes = ["some/package/SomeFile.ext"]
}

build.finalizedBy(jartighten)

// You can also create custom JarTighten tasks:
import io.github.NeRdTheNed.JarTighten.JarTightenTask

tasks.register('jartightenCustom', JarTightenTask) {
    // Set the input and output files
    inputFile = layout.projectDirectory.file(...)
    outputFile = layout.projectDirectory.file(...)

    // Configure task etc
}

build.finalizedBy(jartightenCustom)
```
