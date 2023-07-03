package com.github.NeRdTheNed.JarTighten;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class JarTightenTask extends DefaultTask {
    @InputFile
    public abstract RegularFileProperty getInputFile();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void jarTighten() throws Exception {
        final Path inputPath = getInputFile().getAsFile().get().toPath();
        final Path outputPath = getOutputFile().getAsFile().get().toPath();
        // TODO
        final List<String> excludes = new ArrayList<>();
        // TODO
        final boolean removeTimestamps = false;
        // TODO
        final boolean removeFileLength = false;
        // TODO
        final boolean removeFileNames = false;
        // TODO
        final boolean recompress = false;
        JarTighten.optimiseJar(inputPath, outputPath, true, excludes != null ? excludes : new ArrayList<String>(), removeTimestamps, removeFileLength, removeFileNames, recompress);
    }
}
