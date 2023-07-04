# JarTighten

> Jar file size optimiser.

[![Build](https://github.com/NeRdTheNed/JarTighten/actions/workflows/gradle.yml/badge.svg)](https://github.com/NeRdTheNed/JarTighten/actions/workflows/gradle.yml)

JarTighten is a jar file size optimiser, including optimisations based on quirks of Java's zip parsing implementation. Although JarTighten is intended to be run as the final step in the optimisation pipeline (after deflate optimisation), it does include options for recompressing files with [CafeUndZopfli](https://github.com/eustas/CafeUndZopfli).

## Usage

JarTighten is available as a command line program:

```
Usage: JarTighten [-chlnrRstVz] [-e=<excludes>]... <inputFile> <outputFile>
Jar file size optimiser
      <inputFile>                The file to optimise
      <outputFile>               The optimised file
  -c, --remove-comments          Remove file comments and zip comment
  -e, --exclude=<excludes>       Files to exclude from optimisations which might hide them from standard zip libraries
  -h, --help                     Show this help message and exit.
  -l, --remove-file-length       Remove file length from local file headers
  -n, --remove-file-names        Remove file names from local file headers
  -r, --[no-]recompress-standard Recompress files with standard Java deflate implementation, uses compressed output if smaller
  -R, --recursive-store          Store the contents of all embeded zip or jar files uncompressed recursively and compress, uses compressed output if smaller
  -s, --[no-]recompress-store    Check uncompressed size, stores uncompressed if smaller
  -t, --remove-timestamps        Remove timestamps
  -V, --version                  Print version information and exit.
  -z, --recompress-zopfli        Recompress files with CafeUndZopfli, uses compressed output if smaller
```

A Gradle plugin with equivalent options is also available on the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.nerdthened.jartighten):

```groovy
plugins {
    ...
    id 'io.github.nerdthened.jartighten' version '1.0.0'
}

jartighten {
    // By default, JarTighten will optimise the output of the jar task.
    //inputFile = layout.projectDirectory.file(...)
    //outputFile = layout.projectDirectory.file(...)

    // Remove file timestamps
    removeTimestamps = true
    // Remove local header file length
    //removeFileLength = true
    // Remove local header file names
    //removeFileNames = true
    // Remove file commments and zip comment
    removeComments = true
    // Enable Zopfli recompression (very time consuming, may require configuring Gradle to use more memory)
    recompressZopfli = true
    // Disable standard JVM deflate recompression (enabled by default)
    //recompressStandard = false
    // Disable checking if storing a file as uncompressed would be smaller (enabled by default)
    //recompressStore = false
    // Store the contents of all embeded zip or jar files uncompressed recursively and compress, uses compressed output if smaller
    recursiveStore = true

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
