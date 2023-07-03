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
         description = "Jar file size optimiser")
public class CMDMain implements Callable<Integer> {
    @Parameters(index = "0", description = "The file to optimise")
    private Path inputFile;

    @Parameters(index = "1", description = "The optimised file")
    private Path outputFile;

    @Option(names = { "--exclude", "-e" }, description = "Files to exclude from optimisations which might hide them from standard zip libraries")
    List<String> excludes;

    @Option(names = { "--remove-timestamps", "-t" }, negatable = true, defaultValue = "true", fallbackValue = "true", description = "Remove timestamps")
    boolean removeTimestamps = true;

    @Option(names = { "--remove-file-length", "-l" }, negatable = true, defaultValue = "true", fallbackValue = "true", description = "Remove file length from local file headers")
    boolean removeFileLength = true;

    @Option(names = { "--remove-file-names", "-n" }, negatable = true, defaultValue = "true", fallbackValue = "true", description = "Remove file names from local file headers")
    boolean removeFileNames = true;

    @Option(names = { "--recompress-zopfli", "-z" }, defaultValue = "false", description = "Recompress files with CafeUndZopfli, uses compressed output if smaller")
    boolean recompressZopfli = false;

    @Option(names = { "--recompress-standard", "-r" }, negatable = true, defaultValue = "true", fallbackValue = "true", description = "Recompress files with standard Java deflate implementation, uses compressed output if smaller")
    boolean recompressStandard = true;

    @Option(names = { "--recompress-store", "-s" }, negatable = true, defaultValue = "true", fallbackValue = "true", description = "Check uncompressed size, stores uncompressed if smaller")
    boolean recompressStore = true;

    @Override
    public Integer call() throws Exception {
        if (!inputFile.toFile().isFile()) {
            throw new Exception("Argument " + inputFile.getFileName() + " is not a file!");
        }

        if (outputFile.toFile().isFile()) {
            throw new Exception("Argument " + outputFile.getFileName() + " is already a file!");
        }

        return !JarTighten.optimiseJar(inputFile, outputFile, true, excludes != null ? excludes : new ArrayList<String>(), removeTimestamps, removeFileLength, removeFileNames, recompressZopfli, recompressStandard, recompressStore) ? 1 : CommandLine.ExitCode.OK;
    }

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new CMDMain()).execute(args);
        System.exit(exitCode);
    }
}
