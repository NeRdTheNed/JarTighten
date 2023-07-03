package com.github.NeRdTheNed.JarTighten;

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

public abstract class JarTightenTask extends DefaultTask {
    @InputFile
    public abstract RegularFileProperty getInputFile();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Input
    @Optional
    public abstract Property<Boolean> getRecompressZopfli();

    @Input
    @Optional
    public abstract Property<Boolean> getRecompressStandard();

    @Input
    @Optional
    public abstract Property<Boolean> getRecompressStore();

    @Input
    @Optional
    public abstract Property<Boolean> getRemoveTimestamps();

    @Input
    @Optional
    public abstract Property<Boolean> getRemoveFileLength();

    @Input
    @Optional
    public abstract Property<Boolean> getRemoveFileNames();

    @Input
    @Optional
    public abstract Property<Boolean> getRecursiveStore();

    @Input
    @Optional
    public abstract ListProperty<String> getExcludes();

    @TaskAction
    public void jarTighten() {
        final Path inputPath = getInputFile().getAsFile().get().toPath();
        final Path outputPath = getOutputFile().getAsFile().get().toPath();
        final List<String> excludes = getExcludes().getOrNull();
        final boolean removeTimestamps = getRemoveTimestamps().getOrElse(false);
        final boolean removeFileLength = getRemoveFileLength().getOrElse(false);
        final boolean removeFileNames = getRemoveFileNames().getOrElse(false);
        final boolean recompressZopfli = getRecompressZopfli().getOrElse(false);
        final boolean recompressStandard = getRecompressStandard().getOrElse(true);
        final boolean recompressStore = getRecompressStore().getOrElse(true);
        final boolean recursiveStore = getRecursiveStore().getOrElse(false);
        final JarTighten jarTighten = new JarTighten(excludes != null ? excludes : new ArrayList<String>(), removeTimestamps, removeFileLength, removeFileNames, recompressZopfli, recompressStandard, recompressStore, recursiveStore);
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
