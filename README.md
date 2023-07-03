# JarTighten

> Jar file size optimiser.

[![Build](https://github.com/NeRdTheNed/JarTighten/actions/workflows/gradle.yml/badge.svg)](https://github.com/NeRdTheNed/JarTighten/actions/workflows/gradle.yml)

JarTighten is a jar file size optimiser, including optimisations based on quirks of Java's zip parsing implementation. Although JarTighten is intended to be run as the final step in the optimisation pipeline (after deflate optimisation), it does include options for recompressing files with [CafeUndZopfli](https://github.com/eustas/CafeUndZopfli).

## Usage

JarTighten is currently available as a command line program. A Gradle plugin is in development.
