package io.github.NeRdTheNed.JarTighten;

import static lu.luz.jzopfli.Zopfli_lib.ZopfliCompress;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import lu.luz.jzopfli.ZopfliH.ZopfliFormat;
import lu.luz.jzopfli.ZopfliH.ZopfliOptions;
import ru.eustas.zopfli.Options;
import ru.eustas.zopfli.Options.BlockSplitting;
import ru.eustas.zopfli.Options.OutputFormat;
import ru.eustas.zopfli.Zopfli;
import software.coley.lljzip.ZipIO;
import software.coley.lljzip.format.ZipPatterns;
import software.coley.lljzip.format.compression.DeflateDecompressor;
import software.coley.lljzip.format.compression.ZipCompressions;
import software.coley.lljzip.format.model.AbstractZipFileHeader;
import software.coley.lljzip.format.model.CentralDirectoryFileHeader;
import software.coley.lljzip.format.model.EndOfCentralDirectory;
import software.coley.lljzip.format.model.LocalFileHeader;
import software.coley.lljzip.format.model.ZipArchive;
import software.coley.lljzip.util.ByteDataUtil;

/**
 * Jar file size optimiser, including optimisations based on quirks of Java's zip parsing implementation.
 */
public class JarTighten {
    /** Files to exclude from optimisations which might hide them from standard zip libraries */
    private final List<String> excludes;
    /**
     * Try multiple compression strategies for each compressor.
     * Improves compression at the cost of running each selected compressor multiple times.
     */
    private final boolean extensive;
    /** Remove timestamps */
    private final boolean removeTimestamps;
    /** Remove file length from local file headers */
    private final boolean removeFileLength;
    /** Remove file length from central directory entries */
    private final boolean removeDirEntryLength;
    /** Remove file names from local file headers */
    private final boolean removeFileNames;
    /** Remove info from the EOCD */
    private final boolean removeEOCDInfo;
    /** Remove file comments and zip comment */
    private final boolean removeComments;
    /** Remove extra field */
    private final boolean removeExtra;
    /** Remove directory entries */
    private final boolean removeDirectoryEntries;
    /** Deduplicate local file header entries with the same compressed contents */
    private final boolean deduplicateEntries;
    /** Recompress files with CafeUndZopfli, uses compressed output if smaller */
    private final boolean recompressZopfli;
    /** Recompress files with jzopfli, uses compressed output if smaller */
    private final boolean recompressJZopflii;
    /** Recompress files with standard Java deflate implementation, uses compressed output if smaller */
    private final boolean recompressStandard;
    /** Check uncompressed size, stores uncompressed if smaller */
    private final boolean recompressStore;
    /** Store the contents of all embedded zip or jar files uncompressed recursively and compress, uses compressed output if smaller */
    private final boolean recursiveStore;
    /** Sort zip entries in the way they're expected to be in a jar file */
    private final boolean sortEntries;
    /** Replace every value that the JVM doesn't read in local file headers with zeros. Overrides other options. */
    private final boolean zeroLocalFileHeaders;

    /** Creates a JarTighten instance with the given options. */
    public JarTighten(List<String> excludes, boolean extensive, boolean removeTimestamps, boolean removeFileLength, boolean removeDirEntryLength, boolean removeFileNames, boolean removeEOCDInfo, boolean removeComments, boolean removeExtra, boolean removeDirectoryEntries, boolean deduplicateEntries, boolean recompressZopfli, boolean recompressJZopflii, boolean recompressStandard, boolean recompressStore, boolean recursiveStore, boolean sortEntries, boolean zeroLocalFileHeaders) {
        this.excludes = excludes;
        this.extensive = extensive;
        this.removeTimestamps = removeTimestamps;
        this.removeFileLength = removeFileLength;
        this.removeDirEntryLength = removeDirEntryLength;
        this.removeFileNames = removeFileNames;
        this.removeEOCDInfo = removeEOCDInfo;
        this.removeComments = removeComments;
        this.removeExtra = removeExtra;
        this.removeDirectoryEntries = removeDirectoryEntries;
        this.deduplicateEntries = deduplicateEntries;
        this.recompressZopfli = recompressZopfli;
        this.recompressJZopflii = recompressJZopflii;
        this.recompressStandard = recompressStandard;
        this.recompressStore = recompressStore;
        this.recursiveStore = recursiveStore;
        this.sortEntries = sortEntries;
        this.zeroLocalFileHeaders = zeroLocalFileHeaders;
    }

    private static final class EntryData {
        final int crc32;
        final int uncompressedSize;
        final int compressedSize;
        final int compressionMethod;
        final int offset;

        public EntryData(int crc32, int uncompressedSize, int compressedSize, int compressionMethod, int offset) {
            this.crc32 = crc32;
            this.uncompressedSize = uncompressedSize;
            this.compressedSize = compressedSize;
            this.compressionMethod = compressionMethod;
            this.offset = offset;
        }
    }

    private static final class CompressionResult {
        final int compressionMethod;
        final byte[] compressedData;
        final int crc32;
        final int uncompressedSize;
        final int compressedSize;

        public CompressionResult(int compressionMethod, byte[] compressedData, int crc32, int uncompressedSize, int compressedSize) {
            this.compressionMethod = compressionMethod;
            this.compressedData = compressedData;
            this.crc32 = crc32;
            this.uncompressedSize = uncompressedSize;
            this.compressedSize = compressedSize;
        }
    }

    /** Zip time constant, used when removing timestamps */
    private static final int EARLIEST_TIME = 0x6020;

    /** Zip date constant, used when removing timestamps */
    private static final int EARLIEST_DATE = 0x0021;

    /** Zip version 2.0, minimum required version for deflate compression */
    private static final int ZIP_VERSION_2_0 = 0x14;

    /**
     * Write a short to the output stream as bytes in LE order.
     *
     * @param out the output stream
     * @param value the value to write
     */
    private static void writeShortLE(OutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    /**
     * Write an integer to the output stream as bytes in LE order.
     *
     * @param out the output stream
     * @param value the value to write
     */
    private static void writeIntLE(OutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    /** Cached lazily constructed Zopfli compressor. Lazily constructed due to RAM use. */
    private Supplier<Zopfli> zopfliCompressor = () -> {
        final Zopfli zop = new Zopfli(8 << 20);
        zopfliCompressor = () -> zop;
        return zop;
    };

    /**
     * Compress using Zopfli deflate compression.
     * TODO Option customisation
     *
     * @param uncompressedData uncompressed data
     * @param options zopfli compression options
     * @return compressed data
     */
    private byte[] compressZopfli(byte[] uncompressedData, Options options) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        zopfliCompressor.get().compress(options, uncompressedData, bos);
        return bos.toByteArray();
    }

    /**
     * Compress using jzopfli deflate compression.
     * TODO Option customisation
     *
     * @param uncompressedData uncompressed data
     * @param jzOptions zopfli compression options
     * @return compressed data
     */
    private byte[] compressJZopfli(byte[] uncompressedData, ZopfliOptions jzOptions) {
        // TODO Option customisation
        final byte[][] compressedData = {{ 0 }};
        final int[] outputSize = {0};
        ZopfliCompress(jzOptions, ZopfliFormat.ZOPFLI_FORMAT_DEFLATE, uncompressedData, uncompressedData.length, compressedData, outputSize);
        // TODO Verify data integrity
        return compressedData[0];
    }

    /**
     * Compress using the best standard JVM deflate compression.
     * TODO Option customisation
     *
     * @param uncompressedData uncompressed data
     * @param javaCompressor the compressor to use
     * @return compressed data
     */
    private byte[] compressStandard(byte[] uncompressedData, Deflater javaCompressor) {
        javaCompressor.setInput(uncompressedData);
        javaCompressor.finish();
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final byte[] buffer = new byte[4096];

        while (!javaCompressor.finished()) {
            final int deflated = javaCompressor.deflate(buffer);
            bos.write(buffer, 0, deflated);
        }

        javaCompressor.reset();
        return bos.toByteArray();
    }

    /** Cached CRC32 calculator */
    private final CRC32 crc32Calc = new CRC32();

    /**
     * Create a zip file with its contents and all embedded zip or jar files stored uncompressed recursively from the given input.
     *
     * @param zipInZip the input zip file
     * @return the recursively stored zip file
     */
    private CompressionResult asRecursiveStoredZip(ZipArchive zipInZip) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        optimiseJar(true, zipInZip, bos);
        final byte[] storedJar = bos.toByteArray();
        crc32Calc.update(storedJar);
        final int crc32 = (int) crc32Calc.getValue();
        crc32Calc.reset();
        final int uncompressedSize = storedJar.length;
        return new CompressionResult(ZipCompressions.STORED, storedJar, crc32, uncompressedSize, uncompressedSize);
    }

    /** Cached JVM compressors */
    private Supplier<Deflater[]> javaCompressors = makeCompressors();

    private Supplier<Deflater[]> makeCompressors() {
        return () -> {
            final Deflater[] compressors;
            final Deflater javaCompressorStandard = new Deflater(Deflater.BEST_COMPRESSION, true);

            if (extensive) {
                final Deflater javaCompressorFiltered = new Deflater(Deflater.BEST_COMPRESSION, true);
                final Deflater javaCompressorHuffman = new Deflater(Deflater.BEST_COMPRESSION, true);
                javaCompressorFiltered.setStrategy(Deflater.FILTERED);
                javaCompressorHuffman.setStrategy(Deflater.HUFFMAN_ONLY);
                compressors = new Deflater[] { javaCompressorStandard, javaCompressorFiltered, javaCompressorHuffman };
            } else {
                compressors = new Deflater[] { javaCompressorStandard };
            }

            javaCompressors = () -> compressors;
            return compressors;
        };
    }

    /** Amount of Zopfli iterations */
    private static final int ZOPFLI_ITER = 20;

    /** Amount of CafeUndZopfli iterations */
    private static final int CAFE_ZOPFLI_ITER = ZOPFLI_ITER;

    /** Amount of JZopfli iterations */
    private static final int JZOPFLI_ITER = ZOPFLI_ITER;

    /** JZopfli default max block splitting */
    private static final int JZOPFLI_DEFAULT_SPLIT = 15;

    /** Cached CafeUndZopfli options */
    private Supplier<Options[]> options = makeCafeOptions();

    private Supplier<Options[]> makeCafeOptions() {
        return () -> {
            final Options[] optionsArr = extensive ? new Options[] {
                new Options(OutputFormat.DEFLATE, BlockSplitting.FIRST, CAFE_ZOPFLI_ITER),
                new Options(OutputFormat.DEFLATE, BlockSplitting.LAST, CAFE_ZOPFLI_ITER),
                new Options(OutputFormat.DEFLATE, BlockSplitting.NONE, CAFE_ZOPFLI_ITER),
            } : new Options[] { new Options(OutputFormat.DEFLATE, BlockSplitting.FIRST, CAFE_ZOPFLI_ITER) };
            options = () -> optionsArr;
            return optionsArr;
        };
    }

    /** Cached JZopfli options */
    private Supplier<ZopfliOptions[]> jZopfliOptions = makeJZopfliOptions();

    private Supplier<ZopfliOptions[]> makeJZopfliOptions() {
        return () -> {
            final List<ZopfliOptions> jzopfliOptionsList = new ArrayList<>();

            if (extensive) {
                for (final int split : new int[] {JZOPFLI_DEFAULT_SPLIT, 0}) {
                    final ZopfliOptions jzOptionsFirst = new ZopfliOptions();
                    jzOptionsFirst.verbose = false;
                    jzOptionsFirst.verbose_more = false;
                    jzOptionsFirst.numiterations = JZOPFLI_ITER;
                    jzOptionsFirst.blocksplitting = true;
                    jzOptionsFirst.blocksplittinglast = false;
                    jzOptionsFirst.blocksplittingmax = split;
                    jzopfliOptionsList.add(jzOptionsFirst);
                    final ZopfliOptions jzOptionsLast = new ZopfliOptions();
                    jzOptionsLast.verbose = false;
                    jzOptionsLast.verbose_more = false;
                    jzOptionsLast.numiterations = JZOPFLI_ITER;
                    jzOptionsLast.blocksplitting = true;
                    jzOptionsLast.blocksplittinglast = true;
                    jzOptionsLast.blocksplittingmax = split;
                    jzopfliOptionsList.add(jzOptionsLast);
                }
                final ZopfliOptions jzOptionsNoSplit = new ZopfliOptions();
                jzOptionsNoSplit.verbose = false;
                jzOptionsNoSplit.verbose_more = false;
                jzOptionsNoSplit.numiterations = JZOPFLI_ITER;
                jzOptionsNoSplit.blocksplitting = false;
                jzOptionsNoSplit.blocksplittinglast = false;
                jzOptionsNoSplit.blocksplittingmax = 0;
                jzopfliOptionsList.add(jzOptionsNoSplit);
            } else {
                final ZopfliOptions jzOptionsFirst = new ZopfliOptions();
                jzOptionsFirst.verbose = false;
                jzOptionsFirst.verbose_more = false;
                jzOptionsFirst.numiterations = JZOPFLI_ITER;
                jzOptionsFirst.blocksplitting = true;
                jzOptionsFirst.blocksplittinglast = false;
                jzOptionsFirst.blocksplittingmax = JZOPFLI_DEFAULT_SPLIT;
                jzopfliOptionsList.add(jzOptionsFirst);
            }

            final ZopfliOptions[] optionsList = jzopfliOptionsList.toArray(new ZopfliOptions[0]);
            jZopfliOptions = () -> optionsList;
            return optionsList;
        };
    }

    /**
     * Find the smallest way to store the given input file.
     *
     * @param uncompressedData the input uncompressed data
     * @param crc32 the input crc32
     * @param uncompressedSize the input uncompressed size
     * @param compressedSize the input compressed size
     * @param compressionMethod the input compression method
     * @param compressedData the input compressed data
     * @param zipLike if true, the input file is a zip-based format
     * @return the best compressed result with the configured settings
     */
    private CompressionResult findSmallestOutput(byte[] uncompressedData, int crc32, int uncompressedSize, int compressedSize, int compressionMethod, byte[] compressedData, boolean zipLike) {
        if (recompressStandard) {
            // TODO Option customisation
            for (final Deflater javaCompressor : javaCompressors.get()) {
                final byte[] recompressedData = compressStandard(uncompressedData, javaCompressor);

                // TODO Verify data integrity

                if (recompressedData.length < compressedSize) {
                    compressedData = recompressedData;
                    compressedSize = recompressedData.length;
                    compressionMethod = ZipCompressions.DEFLATED;
                }
            }
        }

        if (recompressZopfli) {
            try {
                // TODO Option customisation
                for (final Options option : options.get()) {
                    final byte[] zopfliCompressedData = compressZopfli(uncompressedData, option);

                    // TODO Verify data integrity

                    if (zopfliCompressedData.length < compressedSize) {
                        compressedData = zopfliCompressedData;
                        compressedSize = zopfliCompressedData.length;
                        compressionMethod = ZipCompressions.DEFLATED;
                    }
                }
            } catch (final Exception e) {
                // TODO Handle errors more gracefully
                e.printStackTrace();
            }
        }

        if (recompressJZopflii) {
            try {
                // TODO Option customisation
                for (final ZopfliOptions option : jZopfliOptions.get()) {
                    final byte[] jzopfliCompressedData = compressJZopfli(uncompressedData, option);

                    // TODO Verify data integrity

                    if (jzopfliCompressedData.length < compressedSize) {
                        compressedData = jzopfliCompressedData;
                        compressedSize = jzopfliCompressedData.length;
                        compressionMethod = ZipCompressions.DEFLATED;
                    }
                }
            } catch (final Exception e) {
                // TODO Handle errors more gracefully
                e.printStackTrace();
            }
        }

        if (recompressStore && (uncompressedData.length < compressedSize)) {
            compressedData = uncompressedData;
            compressedSize = uncompressedData.length;
            compressionMethod = ZipCompressions.STORED;
        }

        if (zipLike && recursiveStore) {
            try {
                final ZipArchive zipInZip = ZipIO.readJvm(uncompressedData);
                final CompressionResult uncomZip = asRecursiveStoredZip(zipInZip);
                final CompressionResult comUncomZip = findSmallestOutput(uncomZip.compressedData, uncomZip.crc32, uncomZip.uncompressedSize, uncomZip.uncompressedSize, ZipCompressions.STORED, uncomZip.compressedData, false);

                if (comUncomZip.compressedSize < compressedSize) {
                    compressedData = comUncomZip.compressedData;
                    compressedSize = comUncomZip.compressedSize;
                    compressionMethod = comUncomZip.compressionMethod;
                    crc32 = comUncomZip.crc32;
                    uncompressedSize = comUncomZip.uncompressedSize;
                }
            } catch (final Exception e) {
                // TODO Handle errors more gracefully
                e.printStackTrace();
            }
        }

        return new CompressionResult(compressionMethod, compressedData, crc32, uncompressedSize, compressedSize);
    }

    /** Cached decompressor */
    private final DeflateDecompressor decomp = DeflateDecompressor.INSTANCE;

    /**
     * Decompress the data for the given LocalFileHeader,
     * or return the uncompressed data if the compression method is store.
     *
     * @param fileHeader the local file header
     * @param compressionMethod the compression method
     * @param compressedData compressed data
     * @return uncompressed data
     */
    private byte[] decompressData(LocalFileHeader fileHeader, int compressionMethod, byte[] compressedData) throws IOException {
        final byte[] uncompressedData;

        if (compressionMethod == ZipCompressions.DEFLATED) {
            uncompressedData = ByteDataUtil.toByteArray(decomp.decompress(fileHeader, fileHeader.getFileData()));
        } else if (compressionMethod == ZipCompressions.STORED) {
            uncompressedData = compressedData;
        } else {
            uncompressedData = ByteDataUtil.toByteArray(ZipCompressions.decompress(fileHeader));
        }

        return uncompressedData;
    }

    /**
     * Checks if the local file header may contain a zip based file.
     *
     * @param fileHeader the local file header
     * @return if the file is possibly a zip based file
     */
    private boolean isFilePossiblyZipLike(LocalFileHeader fileHeader) {
        final String localFileName = fileHeader.getFileNameAsString();
        return (localFileName != null) && (localFileName.endsWith(".jar") || localFileName.endsWith(".zip"));
    }

    /**
     * Find the smallest way to store the given input file.
     *
     * @param fileHeader the input file header
     * @param crc32 the input crc32
     * @param uncompressedSize the input uncompressed size
     * @param compressedSize the input compressed size
     * @param compressionMethod the input compression method
     * @param compressedData the input compressed data
     * @return the best compressed result with the configured settings
     */
    private CompressionResult findSmallestOutput(LocalFileHeader fileHeader, int crc32, int uncompressedSize, int compressedSize, int compressionMethod, byte[] compressedData) throws IOException {
        final byte[] uncompressedData = decompressData(fileHeader, compressionMethod, compressedData);
        final boolean zipLike = recursiveStore && isFilePossiblyZipLike(fileHeader);
        return findSmallestOutput(uncompressedData, crc32, uncompressedSize, compressedSize, compressionMethod, compressedData, zipLike);
    }

    /**
     * Create a stored CompressionResult from the given input.
     *
     * @param fileHeader the local file header
     * @param crc32 the input crc32
     * @param uncompressedSize the input uncompressed size
     * @param compressionMethod the input compression method
     * @param compressedData the input compressed data
     * @return a stored CompressionResult from the given input
     */
    private CompressionResult asStored(LocalFileHeader fileHeader, int crc32, int uncompressedSize, int compressionMethod, byte[] compressedData) throws IOException {
        final byte[] uncompressedData = decompressData(fileHeader, compressionMethod, compressedData);

        if (recursiveStore && isFilePossiblyZipLike(fileHeader)) {
            try {
                final ZipArchive zipInZip = ZipIO.readJvm(uncompressedData);
                return asRecursiveStoredZip(zipInZip);
            } catch (final Exception e) {
                // TODO Handle errors more gracefully
                e.printStackTrace();
            }
        }

        return new CompressionResult(ZipCompressions.STORED, uncompressedData, crc32, uncompressedSize, uncompressedSize);
    }

    /**
     * Create a deflated CompressionResult from the given input, stored as one type 0 block.
     * This is never useful for practical purposes,
     * as it's always larger than an uncompressed (stored) entry by at least 5-ish bytes.
     * TODO Handle uncompressed data larger than allowed in a single type 0 block
     *
     * @param fileHeader the local file header
     * @param crc32 the input crc32
     * @param uncompressedSize the input uncompressed size
     * @param compressionMethod the input compression method
     * @param compressedData the input compressed data
     * @return a stored CompressionResult from the given input
     */
    private CompressionResult asType0Block(LocalFileHeader fileHeader, int crc32, int uncompressedSize, int compressionMethod, byte[] compressedData) throws IOException {
        final byte[] uncompressedData = decompressData(fileHeader, compressionMethod, compressedData);
        final byte[] type0Block = new byte[uncompressedData.length + 5];
        // Block type 0 | final
        type0Block[0] = 0x01;
        // Calculate block sizes
        final int size = uncompressedData.length;
        final int invertedSize = ~size;
        // Block size
        type0Block[1] = (byte) (size & 0xFF);
        type0Block[2] = (byte) ((size >> 8) & 0xFF);
        // Inverted block size
        type0Block[3] = (byte) (invertedSize & 0xFF);
        type0Block[4] = (byte) ((invertedSize >> 8) & 0xFF);
        // Copy uncompressed data into block
        System.arraycopy(uncompressedData, 0, type0Block, 5, uncompressedData.length);
        return new CompressionResult(ZipCompressions.DEFLATED, type0Block, crc32, uncompressedSize, type0Block.length);
    }

    /**
     * A comparator for ordering entries in a Jar file.
     * Java expects either the first entry to be the manifest,
     * or the first entry to be the directory containing the manifest
     * and the second entry to be the manifest.
     */
    public class JarFileSorter implements Comparator<AbstractZipFileHeader> {
        /**
         * Sorts entries first by checking if they're either the META-INF folder,
         * the manifest file, and then by alphabetical order.
         *
         * @param e1 the first entry
         * @param e2 the second entry
         * @return the order
         */
        @Override
        public int compare(AbstractZipFileHeader e1, AbstractZipFileHeader e2) {
            final String e1name = e1.getFileNameAsString();
            final String e2name = e2.getFileNameAsString();
            final boolean isE1ManifestFolder = "META-INF/".equals(e1name);
            final boolean isE2ManifestFolder = "META-INF/".equals(e2name);

            if (isE1ManifestFolder && isE2ManifestFolder) {
                return 0;
            }

            if (isE1ManifestFolder) {
                return -1;
            }

            if (isE2ManifestFolder) {
                return 1;
            }

            final boolean isE1Manifest = "META-INF/MANIFEST.MF".equals(e1name);
            final boolean isE2Manifest = "META-INF/MANIFEST.MF".equals(e2name);

            if (isE1Manifest && isE2Manifest) {
                return 0;
            }

            if (isE1Manifest) {
                return -1;
            }

            if (isE2Manifest) {
                return 1;
            }

            return e1name.compareTo(e2name);
        }
    }

    /**
     * Optimises a ZipArchive, with the configured settings.
     *
     * @param forceRecursiveStore if true, store the contents of this and all embedded zip or jar files uncompressed recursively
     * @param archive the ZipArchive to optimise
     * @param outputStream output stream for optimised jar to be written to
     * @return true, if successful
     */
    private boolean optimiseJar(boolean forceRecursiveStore, ZipArchive archive, OutputStream outputStream) throws IOException {
        final boolean recompress = recompressZopfli || recompressStandard || recompressStore;
        final HashMap<Integer, EntryData> mapToEntryData = new HashMap<>();
        int offset = 0;
        final JarFileSorter sorter = new JarFileSorter();

        // Local file headers:
        for (final LocalFileHeader fileHeader : sortEntries ? archive.getLocalFiles().stream().sorted(sorter).collect(Collectors.toList()) : archive.getLocalFiles()) {
            int crc32 = fileHeader.getCrc32();
            final int originalCrc32 = crc32;
            int realCompressedSize = (int) fileHeader.getCompressedSize();
            int realUncompressedSize = (int) fileHeader.getUncompressedSize();

            if ((removeDirectoryEntries && (realUncompressedSize == 0)) || (deduplicateEntries && mapToEntryData.containsKey(crc32))) {
                continue;
            }

            int compressionMethod = fileHeader.getCompressionMethod();
            byte[] fileData = ByteDataUtil.toByteArray(fileHeader.getFileData());

            if (forceRecursiveStore) {
                try {
                    final CompressionResult stored = asStored(fileHeader, crc32, realUncompressedSize, compressionMethod, fileData);
                    compressionMethod = stored.compressionMethod;
                    fileData = stored.compressedData;
                    crc32 = stored.crc32;
                    realUncompressedSize = stored.uncompressedSize;
                    realCompressedSize = stored.compressedSize;
                } catch (final Exception e) {
                    // TODO Handle errors more gracefully
                    e.printStackTrace();
                }
            } else if (recompress) {
                try {
                    final CompressionResult newBest = findSmallestOutput(fileHeader, crc32, realUncompressedSize, realCompressedSize, compressionMethod, fileData);
                    compressionMethod = newBest.compressionMethod;
                    fileData = newBest.compressedData;
                    crc32 = newBest.crc32;
                    realUncompressedSize = newBest.uncompressedSize;
                    realCompressedSize = newBest.compressedSize;
                } catch (final Exception e) {
                    // TODO Handle errors more gracefully
                    e.printStackTrace();
                }
            }

            final boolean exclude = excludes.contains(fileHeader.getFileNameAsString());
            // Header
            writeIntLE(outputStream, ZipPatterns.LOCAL_FILE_HEADER_QUAD);
            // Minimum version
            int versionNeeded = fileHeader.getVersionNeededToExtract();

            // If deflate compression is used, make sure that the version needed field is at least 2.0
            if ((compressionMethod == ZipCompressions.DEFLATED) && (versionNeeded < ZIP_VERSION_2_0)) {
                versionNeeded = ZIP_VERSION_2_0;
            }

            writeShortLE(outputStream, zeroLocalFileHeaders ? 0 : versionNeeded);
            // General purpose bit flag
            int bitFlag = fileHeader.getGeneralPurposeBitFlag();
            // Clear the "Data Descriptor" / EXTSIG flag
            // TODO Option to keep this?
            bitFlag &= ~(1 << 3);
            writeShortLE(outputStream, zeroLocalFileHeaders ? 0 : bitFlag);
            // Compression method
            writeShortLE(outputStream, zeroLocalFileHeaders ? 0 : compressionMethod);
            // Last modification time
            final int lastModFileTime = removeTimestamps ? EARLIEST_TIME : fileHeader.getLastModFileTime();
            writeShortLE(outputStream, zeroLocalFileHeaders ? 0 : lastModFileTime);
            // Last modification date
            final int lastModFileDate = removeTimestamps ? EARLIEST_DATE : fileHeader.getLastModFileDate();
            writeShortLE(outputStream, zeroLocalFileHeaders ? 0 : lastModFileDate);
            // CRC32
            writeIntLE(outputStream, zeroLocalFileHeaders ? 0 : crc32);
            // Compressed size
            final int localCompressedSize = (removeFileLength && !exclude) ? 0 : realCompressedSize;
            writeIntLE(outputStream, zeroLocalFileHeaders ? 0 : localCompressedSize);
            // Uncompressed size
            final int localUncompressedSize = (removeFileLength && !exclude) ? 0 : realUncompressedSize;
            writeIntLE(outputStream, zeroLocalFileHeaders ? 0 : localUncompressedSize);
            // File name optimisation
            final String fileNameStr = fileHeader.getFileNameAsString();
            final boolean isManifest = "META-INF/".equals(fileNameStr) || "META-INF/MANIFEST.MF".equals(fileNameStr);
            final int fileNameLength;
            final byte[] fileName;

            if (zeroLocalFileHeaders || (removeFileNames && !exclude)) {
                if (isManifest) {
                    // For some reason, the manifest requires the correct file name length offset,
                    // but not the correct name.
                    fileNameLength = fileHeader.getFileNameLength();
                    fileName = new byte[fileNameLength];
                } else {
                    fileNameLength = 0;
                    fileName = new byte[] { };
                }
            } else {
                fileNameLength = fileHeader.getFileNameLength();
                fileName = ByteDataUtil.toByteArray(fileHeader.getFileName());
            }

            // File name length
            writeShortLE(outputStream, fileNameLength);
            // Extra field length
            final int extraFieldLength = (zeroLocalFileHeaders || removeExtra) ? 0 : fileHeader.getExtraFieldLength();
            writeShortLE(outputStream, extraFieldLength);
            // File name
            outputStream.write(fileName);
            // Extra field
            final byte[] extra = (zeroLocalFileHeaders || removeExtra) ? new byte[] { } : ByteDataUtil.toByteArray(fileHeader.getExtraField());
            outputStream.write(extra);
            // Compressed data
            // TODO This feels wrong?
            outputStream.write(fileData, 0, realCompressedSize);
            final EntryData entryData = new EntryData(crc32, realUncompressedSize, realCompressedSize, compressionMethod, offset);

            if (deduplicateEntries) {
                mapToEntryData.put(crc32, entryData);

                if (crc32 != originalCrc32) {
                    mapToEntryData.put(originalCrc32, entryData);
                }
            } else {
                final CentralDirectoryFileHeader cenDir = fileHeader.getLinkedDirectoryFileHeader();

                if (cenDir != null) {
                    mapToEntryData.put((int) cenDir.getRelativeOffsetOfLocalHeader(), entryData);
                } else if (fileHeader.hasOffset()) {
                    mapToEntryData.put((int) fileHeader.offset(), entryData);
                } else {
                    System.err.println("File " + fileHeader.getFileNameAsString() + " somehow had no offset?");
                }
            }

            offset += 30 + fileNameLength + extraFieldLength + realCompressedSize;
        }

        final int startCentral = offset;
        int centralEntries = 0;

        // Central directory file headers:
        for (final CentralDirectoryFileHeader centralDir : sortEntries ? archive.getCentralDirectories().stream().sorted(sorter).collect(Collectors.toList()) : archive.getCentralDirectories()) {
            final int crc32 = centralDir.getCrc32();
            final int key = deduplicateEntries ? crc32 : (int) centralDir.getRelativeOffsetOfLocalHeader();

            if (!mapToEntryData.containsKey(key)) {
                continue;
            }

            final EntryData entryData = mapToEntryData.get(key);
            final int uncompressedSize = entryData.uncompressedSize;

            if (removeDirectoryEntries && ((uncompressedSize == 0) || (centralDir.getUncompressedSize() == 0))) {
                continue;
            }

            // Header
            writeIntLE(outputStream, ZipPatterns.CENTRAL_DIRECTORY_FILE_HEADER_QUAD);
            // Made by
            writeShortLE(outputStream, centralDir.getVersionMadeBy());
            // Minimum version
            int versionNeeded = centralDir.getVersionNeededToExtract();

            // If deflate compression is used, make sure that the version needed field is at least 2.0
            if ((entryData.compressionMethod == ZipCompressions.DEFLATED) && (versionNeeded < ZIP_VERSION_2_0)) {
                versionNeeded = ZIP_VERSION_2_0;
            }

            writeShortLE(outputStream, versionNeeded);
            // General purpose bit flag
            int bitFlag = centralDir.getGeneralPurposeBitFlag();
            // Clear the "Data Descriptor" / EXTSIG flag
            // TODO Option to keep this?
            bitFlag &= ~(1 << 3);
            writeShortLE(outputStream, bitFlag);
            // Compression method
            writeShortLE(outputStream, entryData.compressionMethod);
            // Last modification time
            final int lastModFileTime = removeTimestamps ? EARLIEST_TIME : centralDir.getLastModFileTime();
            writeShortLE(outputStream, lastModFileTime);
            // Last modification date
            final int lastModFileDate = removeTimestamps ? EARLIEST_DATE : centralDir.getLastModFileDate();
            writeShortLE(outputStream, lastModFileDate);
            // CRC32
            writeIntLE(outputStream, entryData.crc32);
            // Sizes
            final String fileNameStr = centralDir.getFileNameAsString();
            final boolean isManifest = "META-INF/".equals(fileNameStr) || "META-INF/MANIFEST.MF".equals(fileNameStr);
            final boolean exclude = excludes.contains(fileNameStr);
            // Compressed size
            final int dirCompressedSize = removeDirEntryLength && !isManifest && !exclude && (entryData.compressionMethod == ZipCompressions.DEFLATED) ? Integer.MAX_VALUE : entryData.compressedSize;
            writeIntLE(outputStream, dirCompressedSize);
            // Uncompressed size
            final int dirUncompressedSize = removeDirEntryLength && !isManifest && !exclude ? Integer.MAX_VALUE : uncompressedSize;
            writeIntLE(outputStream, dirUncompressedSize);
            // File name length
            final int fileNameLength = centralDir.getFileNameLength();
            writeShortLE(outputStream, fileNameLength);
            // Extra field length
            final int extraFieldLength = removeExtra ? 0 : centralDir.getExtraFieldLength();
            writeShortLE(outputStream, extraFieldLength);
            // File comment length
            final int fileCommentLength = removeComments ? 0 : centralDir.getFileCommentLength();
            writeShortLE(outputStream, fileCommentLength);
            // Disk number where file starts
            writeShortLE(outputStream, centralDir.getDiskNumberStart());
            // Internal file attributes
            writeShortLE(outputStream, centralDir.getInternalFileAttributes());
            // External file attributes
            writeIntLE(outputStream, centralDir.getExternalFileAttributes());
            // Relative offset of local file header
            writeIntLE(outputStream, entryData.offset);
            // File name
            final byte[] fileName = ByteDataUtil.toByteArray(centralDir.getFileName());
            outputStream.write(fileName);
            // Extra field
            final byte[] extra = removeExtra ? new byte[] { } : ByteDataUtil.toByteArray(centralDir.getExtraField());
            outputStream.write(extra);
            // File comment
            final byte[] fileComment = removeComments ? new byte[] { } : ByteDataUtil.toByteArray(centralDir.getFileComment());
            outputStream.write(fileComment);
            centralEntries++;
            offset += 46 + fileNameLength + extraFieldLength + fileCommentLength;
        }

        // End of central directory record:
        final EndOfCentralDirectory end = archive.getEnd();
        // Header
        writeIntLE(outputStream, ZipPatterns.END_OF_CENTRAL_DIRECTORY_QUAD);
        // Disk number
        writeShortLE(outputStream, removeEOCDInfo ? Integer.MAX_VALUE : end.getDiskNumber());
        // Central directory start disk
        writeShortLE(outputStream, removeEOCDInfo ? Integer.MAX_VALUE : end.getCentralDirectoryStartDisk());
        // TODO What is this?
        writeShortLE(outputStream, removeEOCDInfo ? 0 : centralEntries);
        // Central directory entries
        writeShortLE(outputStream, removeEOCDInfo ? 0 : centralEntries);
        // Central directory size
        writeIntLE(outputStream, offset - startCentral);
        // Central directory offset
        writeIntLE(outputStream, startCentral);
        // Comment length
        writeShortLE(outputStream, removeComments ? 0 : end.getZipCommentLength());
        // Comment
        final byte[] zipComment = removeComments ? new byte[] { } : ByteDataUtil.toByteArray(end.getZipComment());
        outputStream.write(zipComment);
        return true;
    }

    /**
     * Optimises a ZipArchive, with the configured settings.
     *
     * @param archive the ZipArchive to optimise
     * @param outputStream output stream for optimised jar to be written to
     * @return true, if successful
     */
    public boolean optimiseJar(ZipArchive archive, OutputStream outputStream) throws IOException {
        return optimiseJar(false, archive, outputStream);
    }

    /**
     * Optimises a jar file at the given path, with the configured settings.
     *
     * @param input the input jar file
     * @param output the output jar file
     * @param overwrite if true, overwrite existing output file
     * @return true, if successful
     */
    public boolean optimiseJar(Path input, Path output, boolean overwrite) throws IOException {
        if (!Files.isRegularFile(input)) {
            return false;
        }

        final ZipArchive archive = ZipIO.readJvm(input);
        Path possibleTempPath = output;
        boolean handleSame = false;

        if (Files.isRegularFile(output)) {
            if (!overwrite) {
                return false;
            }

            if (Files.isSameFile(input, output)) {
                handleSame = true;
                possibleTempPath = Files.createTempFile("JarTighten-temp-", ".jar");
                possibleTempPath.toFile().deleteOnExit();
            } else {
                Files.delete(output);
            }
        }

        boolean returnVal = false;

        try
            (FileOutputStream outputStream = new FileOutputStream(possibleTempPath.toFile())) {
            returnVal = optimiseJar(archive, outputStream);
        }

        if (returnVal && handleSame) {
            Files.copy(possibleTempPath, output, StandardCopyOption.REPLACE_EXISTING);
        }

        return returnVal;
    }

}
