package io.github.NeRdTheNed.JarTighten;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
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

import com.github.NeRdTheNed.deft4j.util.compression.CompressionUtil.Strategy;

/** A task to optimise a given jar file with JarTighten */
public abstract class JarTightenTask extends DefaultTask {
    private static Strategy convertEnum(String stratergy) {
        switch (stratergy != null ? stratergy.toUpperCase() : "MULTI_CHEAP") {
        case "SINGLE":
            return Strategy.SINGLE;

        default:
        case "MULTI_CHEAP":
            return Strategy.MULTI_CHEAP;

        case "EXTENSIVE":
            return Strategy.EXTENSIVE;
        }
    }

    /** Input jar file to optimise */
    @InputFile
    public abstract RegularFileProperty getInputFile();

    /** Output optimised jar file */
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /**
     * Determines which compression strategies are run for each compressor.
     * Improves compression at the cost of running each selected compressor multiple times.
     */
    @Input
    @Optional
    public abstract Property<String> getMode();

    /** Recompress files with CafeUndZopfli, uses compressed output if smaller */
    @Input
    @Optional
    public abstract Property<Boolean> getRecompressZopfli();

    /** Recompress files with jzopfli, uses compressed output if smaller */
    @Input
    @Optional
    public abstract Property<Boolean> getRecompressJZopfli();

    /** Zopfli iterations. More iterations increases time spent optimising files. */
    @Input
    @Optional
    public abstract Property<Integer> getRecompressZopfliPasses();

    /** Recompress files with JZlib, uses compressed output if smaller */
    @Input
    @Optional
    public abstract Property<Boolean> getRecompressJZlib();

    /** Recompress files with standard Java deflate implementation, uses compressed output if smaller */
    @Input
    @Optional
    public abstract Property<Boolean> getRecompressStandard();

    /** Check uncompressed size, stores uncompressed if smaller */
    @Input
    @Optional
    public abstract Property<Boolean> getRecompressStore();

    /** Run each compressor in a separate thread. May improve performance. */
    @Input
    @Optional
    public abstract Property<Boolean> getRecompressMultithread();

    /** Remove timestamps */
    @Input
    @Optional
    public abstract Property<Boolean> getRemoveTimestamps();

    /** Remove file length from local file headers */
    @Input
    @Optional
    public abstract Property<Boolean> getRemoveFileLength();

    /** Remove file length from central directory entries */
    @Input
    @Optional
    public abstract Property<Boolean> getRemoveDirEntryLength();

    /** Remove file names from local file headers */
    @Input
    @Optional
    public abstract Property<Boolean> getRemoveFileNames();

    /** Remove info from the EOCD */
    @Input
    @Optional
    public abstract Property<Boolean> getRemoveEOCDInfo();

    /** Remove file comments and zip comment */
    @Input
    @Optional
    public abstract Property<Boolean> getRemoveComments();

    /** Remove extra field */
    @Input
    @Optional
    public abstract Property<Boolean> getRemoveExtra();

    /** Remove directory entries */
    @Input
    @Optional
    public abstract Property<Boolean> getRemoveDirectoryEntries();

    /** Deduplicate local file header entries with the same compressed contents */
    @Input
    @Optional
    public abstract Property<Boolean> getDeduplicateEntries();

    /** Store the contents of all embedded zip or jar files uncompressed recursively and compress, uses compressed output if smaller */
    @Input
    @Optional
    public abstract Property<Boolean> getRecursiveStore();

    /** Sort zip entries in the way they're expected to be in a jar file */
    @Input
    @Optional
    public abstract Property<Boolean> getSortEntries();

    /** Replace every value that the JVM doesn't read in local file headers with zeros. Overrides other options. */
    @Input
    @Optional
    public abstract Property<Boolean> getZeroLocalFileHeaders();

    /** Files to exclude from optimisations which might hide them from standard zip libraries */
    @Input
    @Optional
    public abstract ListProperty<String> getExcludes();

    /** Optimise existing deflate streams. Majorly increases time spent optimising files. */
    @Input
    @Optional
    public abstract Property<Boolean> getOptimiseDeflateStreamExisting();

    /** Optimise recompressed deflate streams. Majorly increases time spent optimising files. */
    @Input
    @Optional
    public abstract Property<Boolean> getOptimiseDeflateStreamRecompress();

    /** Compare sizes of deflate streams in bits instead of bytes. Majorly increases time spent optimising files. */
    @Input
    @Optional
    public abstract Property<Boolean> getCompareDeflateStreamBits();

    /** Mark the output jar file as executable on certain operating systems if not already set. Increases file size by 4 bytes. */
    @Input
    @Optional
    public abstract Property<Boolean> getMakeExecutableJar();

    /** Try merging deflate blocks. May majorly increase time spent optimising files. */
    @Input
    @Optional
    public abstract Property<Boolean> getMergeBlocks();

    /** Optimise a jar file with JarTighten */
    @TaskAction
    public void jarTighten() {
        final Path inputPath = getInputFile().getAsFile().get().toPath();
        final Path outputPath = getOutputFile().getAsFile().get().toPath();
        final List<String> excludes = getExcludes().getOrNull();
        final Strategy mode = convertEnum(getMode().getOrNull());
        final boolean removeTimestamps = getRemoveTimestamps().getOrElse(false);
        final boolean removeFileLength = getRemoveFileLength().getOrElse(false);
        final boolean removeDirEntryLength = getRemoveDirEntryLength().getOrElse(false);
        final boolean removeFileNames = getRemoveFileNames().getOrElse(false);
        final boolean removeEOCDInfo = getRemoveEOCDInfo().getOrElse(false);
        final boolean removeComments = getRemoveComments().getOrElse(false);
        final boolean removeExtra = getRemoveExtra().getOrElse(false);
        final boolean removeDirectoryEntries = getRemoveDirectoryEntries().getOrElse(true);
        final boolean deduplicateEntries = getDeduplicateEntries().getOrElse(false);
        final boolean recompressZopfli = getRecompressZopfli().getOrElse(false);
        final boolean recompressJZopfli = getRecompressJZopfli().getOrElse(false);
        final boolean recompressJZlib = getRecompressJZlib().getOrElse(true);
        final boolean recompressStandard = getRecompressStandard().getOrElse(true);
        final boolean recompressStore = getRecompressStore().getOrElse(true);
        final boolean recursiveStore = getRecursiveStore().getOrElse(false);
        final boolean sortEntries = getSortEntries().getOrElse(false);
        final boolean zeroLocalFileHeaders = getZeroLocalFileHeaders().getOrElse(false);
        final boolean optimiseDeflateStreamExisting = getOptimiseDeflateStreamExisting().getOrElse(false);
        final boolean optimiseDeflateStreamRecompress = getOptimiseDeflateStreamRecompress().getOrElse(false);
        final boolean compareDeflateStreamBits = getCompareDeflateStreamBits().getOrElse(false);
        final boolean recompressMultithread = getRecompressMultithread().getOrElse(true);
        final int recompressZopfliPasses = getRecompressZopfliPasses().getOrElse(20);
        final boolean makeExecutableJar = getMakeExecutableJar().getOrElse(false);
        final boolean mergeBlocks = getMergeBlocks().getOrElse(false);
        final JarTighten jarTighten = new JarTighten(excludes != null ? excludes : Collections.emptyList(), mode, removeTimestamps, removeFileLength, removeDirEntryLength, removeFileNames, removeEOCDInfo, removeComments, removeExtra, removeDirectoryEntries, deduplicateEntries, recompressZopfli, recompressJZopfli, recompressJZlib, recompressStandard, recompressStore, recursiveStore, sortEntries, zeroLocalFileHeaders, optimiseDeflateStreamExisting, optimiseDeflateStreamRecompress, compareDeflateStreamBits, recompressMultithread, recompressZopfliPasses, makeExecutableJar, mergeBlocks);
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
