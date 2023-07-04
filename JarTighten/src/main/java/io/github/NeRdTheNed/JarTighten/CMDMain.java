package io.github.NeRdTheNed.JarTighten;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "JarTighten", mixinStandardHelpOptions = true, version = "JarTighten v1.0.1",
         description = "Jar file size optimiser")
public class CMDMain implements Callable<Integer> {
    @Parameters(index = "0", description = "The file to optimise")
    private Path inputFile;

    @Parameters(index = "1", description = "The optimised file")
    private Path outputFile;

    @Option(names = { "--exclude", "-e" }, paramLabel = "<filename>", description = "Files to exclude from optimisations which might hide them from standard zip libraries")
    List<String> excludes;

    @Option(names = { "--remove-timestamps", "-t" }, defaultValue = "false", description = "Remove timestamps")
    boolean removeTimestamps = false;

    @Option(names = { "--remove-file-length", "-l" }, defaultValue = "false", description = "Remove file length from local file headers")
    boolean removeFileLength = false;

    @Option(names = { "--remove-file-names", "-n" }, defaultValue = "false", description = "Remove file names from local file headers")
    boolean removeFileNames = false;

    @Option(names = { "--remove-comments", "-c" }, defaultValue = "false", description = "Remove file comments and zip comment")
    boolean removeComments = false;

    @Option(names = { "--remove-extra", "-E" }, defaultValue = "false", description = "Remove extra field")
    boolean removeExtra = false;

    @Option(names = { "--recompress-zopfli", "-z" }, defaultValue = "false", description = "Recompress files with CafeUndZopfli, uses compressed output if smaller")
    boolean recompressZopfli = false;

    @Option(names = { "--recompress-standard", "-r" }, negatable = true, defaultValue = "true", fallbackValue = "true", description = "Recompress files with standard Java deflate implementation, uses compressed output if smaller")
    boolean recompressStandard = true;

    @Option(names = { "--recompress-store", "-s" }, negatable = true, defaultValue = "true", fallbackValue = "true", description = "Check uncompressed size, stores uncompressed if smaller")
    boolean recompressStore = true;

    @Option(names = { "--recursive-store", "-R" }, defaultValue = "false", description = "Store the contents of all embeded zip or jar files uncompressed recursively and compress, uses compressed output if smaller")
    boolean recursiveStore = false;

    @Option(names = { "--overwrite", "-o" }, defaultValue = "false", description = "Overwrite existing output file")
    boolean overwrite = false;

    @Override
    public Integer call() throws Exception {
        if (!Files.isRegularFile(inputFile)) {
            throw new FileNotFoundException("Input file name argument " + inputFile.getFileName() + " is not a file!");
        }

        if (!overwrite && Files.isRegularFile(outputFile)) {
            throw new IllegalArgumentException("Output file name argument " + outputFile.getFileName() + " is already a file!");
        }

        final JarTighten jarTighten = new JarTighten(excludes != null ? excludes : new ArrayList<String>(), removeTimestamps, removeFileLength, removeFileNames, removeComments, removeExtra, recompressZopfli, recompressStandard, recompressStore, recursiveStore);
        return !jarTighten.optimiseJar(inputFile, outputFile, overwrite) ? 1 : CommandLine.ExitCode.OK;
    }

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new CMDMain()).execute(args);
        System.exit(exitCode);
    }
}
