package com.github.NeRdTheNed.JarTighten;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "JarTighten", mixinStandardHelpOptions = true, version = "JarTighten alpha",
         description = "TODO")
public class CMDMain implements Callable<Integer> {
    @Parameters(index = "0", description = "The file to optimise")
    private Path inputFile;

    @Parameters(index = "1", description = "The optimised file")
    private Path outputFile;

    @Option(names = { "--exclude", "-e" }, description = "Files to exclude from optimisations which might hide them from standard zip libraries")
    List<String> excludes;

    @Override
    public Integer call() throws Exception {
        if (!inputFile.toFile().isFile()) {
            throw new Exception("Argument " + inputFile.getFileName() + " is not a file!");
        }

        if (outputFile.toFile().isFile()) {
            throw new Exception("Argument " + outputFile.getFileName() + " is already a file!");
        }

        return !JarTighten.optimiseJar(inputFile, outputFile, excludes != null ? excludes : new ArrayList<String>()) ? 1 : CommandLine.ExitCode.OK;
    }

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new CMDMain()).execute(args);
        System.exit(exitCode);
    }
}

