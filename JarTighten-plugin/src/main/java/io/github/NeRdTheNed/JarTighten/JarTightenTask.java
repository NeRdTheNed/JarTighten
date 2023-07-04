package io.github.NeRdTheNed.JarTighten;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskExecutionException;

/** A task to optimise a given jar file with JarTighten */
public abstract class JarTightenTask extends DefaultTask {
    /** Input jar file to optimise */
    @InputFile
    public abstract RegularFileProperty getInputFile();

    /** Output optimised jar file */
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /** Recompress files with CafeUndZopfli, uses compressed output if smaller */
    @Input
    @Optional
    public abstract Property<Boolean> getRecompressZopfli();

    /** Recompress files with standard Java deflate implementation, uses compressed output if smaller */
    @Input
    @Optional
    public abstract Property<Boolean> getRecompressStandard();

    /** Check uncompressed size, stores uncompressed if smaller */
    @Input
    @Optional
    public abstract Property<Boolean> getRecompressStore();

    /** Remove timestamps */
    @Input
    @Optional
    public abstract Property<Boolean> getRemoveTimestamps();

    /** Remove file length from local file headers */
    @Input
    @Optional
    public abstract Property<Boolean> getRemoveFileLength();

    /** Remove file names from local file headers */
    @Input
    @Optional
    public abstract Property<Boolean> getRemoveFileNames();

    /** Remove file comments and zip comment */
    @Input
    @Optional
    public abstract Property<Boolean> getRemoveComments();

    /** Remove extra field */
    @Input
    @Optional
    public abstract Property<Boolean> getRemoveExtra();

    /** Store the contents of all embeded zip or jar files uncompressed recursively and compress, uses compressed output if smaller */
    @Input
    @Optional
    public abstract Property<Boolean> getRecursiveStore();

    /** Files to exclude from optimisations which might hide them from standard zip libraries */
    @Input
    @Optional
    public abstract ListProperty<String> getExcludes();

    /** Optimise a jar file with JarTighten */
    @TaskAction
    public void jarTighten() {
        final Path inputPath = getInputFile().getAsFile().get().toPath();
        final Path outputPath = getOutputFile().getAsFile().get().toPath();
        final List<String> excludes = getExcludes().getOrNull();
        final boolean removeTimestamps = getRemoveTimestamps().getOrElse(false);
        final boolean removeFileLength = getRemoveFileLength().getOrElse(false);
        final boolean removeFileNames = getRemoveFileNames().getOrElse(false);
        final boolean removeComments = getRemoveComments().getOrElse(false);
        final boolean removeExtra = getRemoveExtra().getOrElse(false);
        final boolean recompressZopfli = getRecompressZopfli().getOrElse(false);
        final boolean recompressStandard = getRecompressStandard().getOrElse(true);
        final boolean recompressStore = getRecompressStore().getOrElse(true);
        final boolean recursiveStore = getRecursiveStore().getOrElse(false);
        final JarTighten jarTighten = new JarTighten(excludes != null ? excludes : new ArrayList<String>(), removeTimestamps, removeFileLength, removeFileNames, removeComments, removeExtra, recompressZopfli, recompressStandard, recompressStore, recursiveStore);
        final boolean didSucceed;

        try {
            didSucceed = jarTighten.optimiseJar(inputPath, outputPath, true);
        } catch (final IOException e) {
            throw new TaskExecutionException(this, e);
        }

        if (!didSucceed) {
            throw new TaskExecutionException(this, new Exception("Failed to run JarTighten"));
        }
    }
}
