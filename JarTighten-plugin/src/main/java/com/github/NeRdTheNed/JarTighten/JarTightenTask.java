package com.github.NeRdTheNed.JarTighten;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public class JarTightenTask extends DefaultTask {
    private Object inputFile;
    private Object outputFile;

    @InputFile
    public File getInputFile() {
        return getProject().file(inputFile);
    }

    public void setInputFile(File sourceJar) {
        inputFile = sourceJar;
    }

    public void setInputFile(String sourceJar) {
        inputFile = getProject().file(sourceJar);
    }

    public void setInputFile(Object sourceJar) {
        inputFile = sourceJar;
    }

    @OutputFile
    public File getOutputFile() {
        return getProject().file(outputFile);
    }

    public void setOutputFile(File destinationJar) {
        outputFile = destinationJar;
    }

    public void setOutputFile(String destinationJar) {
        outputFile = getProject().file(destinationJar);
    }

    public void setOutputFile(Object destinationJar) {
        outputFile = destinationJar;
    }

    @TaskAction
    public void jarTighten() throws Exception {
        final Path inputPath = ((File) inputFile).toPath();
        final Path outputPath = ((File) outputFile).toPath();
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
