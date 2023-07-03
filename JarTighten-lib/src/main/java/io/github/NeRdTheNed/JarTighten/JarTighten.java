package io.github.NeRdTheNed.JarTighten;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import ru.eustas.zopfli.Options;
import ru.eustas.zopfli.Options.BlockSplitting;
import ru.eustas.zopfli.Options.OutputFormat;
import ru.eustas.zopfli.Zopfli;
import software.coley.llzip.ZipIO;
import software.coley.llzip.format.ZipPatterns;
import software.coley.llzip.format.compression.DeflateDecompressor;
import software.coley.llzip.format.compression.ZipCompressions;
import software.coley.llzip.format.model.CentralDirectoryFileHeader;
import software.coley.llzip.format.model.EndOfCentralDirectory;
import software.coley.llzip.format.model.LocalFileHeader;
import software.coley.llzip.format.model.ZipArchive;
import software.coley.llzip.util.ByteDataUtil;

/**
 * Jar file size optimiser, including optimisations based on quirks of Java's zip parsing implementation.
 */
public class JarTighten {
    /** Files to exclude from optimisations which might hide them from standard zip libraries */
    private final List<String> excludes;
    /** Remove timestamps */
    private final boolean removeTimestamps;
    /** Remove file length from local file headers */
    private final boolean removeFileLength;
    /** Remove file names from local file headers */
    private final boolean removeFileNames;
    /** Remove file comments and zip comment */
    private final boolean removeComments;
    /** Recompress files with CafeUndZopfli, uses compressed output if smaller */
    private final boolean recompressZopfli;
    /** Recompress files with standard Java deflate implementation, uses compressed output if smaller */
    private final boolean recompressStandard;
    /** Check uncompressed size, stores uncompressed if smaller */
    private final boolean recompressStore;
    /** Store the contents of all embeded zip or jar files uncompressed recursively and compress, uses compressed output if smaller */
    private final boolean recursiveStore;

    /** Creates a JarTighten instance with the given options. */
    public JarTighten(List<String> excludes, boolean removeTimestamps, boolean removeFileLength, boolean removeFileNames, boolean removeComments, boolean recompressZopfli, boolean recompressStandard, boolean recompressStore, boolean recursiveStore) {
        this.excludes = excludes;
        this.removeTimestamps = removeTimestamps;
        this.removeFileLength = removeFileLength;
        this.removeFileNames = removeFileNames;
        this.removeComments = removeComments;
        this.recompressZopfli = recompressZopfli;
        this.recompressStandard = recompressStandard;
        this.recompressStore = recompressStore;
        this.recursiveStore = recursiveStore;
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

    /** Cached Zopfli options */
    private final Options options = new Options(OutputFormat.DEFLATE, BlockSplitting.FIRST, 20);

    /**
     * Compress using Zopfli deflate compression.
     * TODO Option customization
     *
     * @param uncompressedData uncompressed data
     * @return compressed data
     */
    private byte[] compressZopfli(byte[] uncompressedData) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        zopfliCompressor.get().compress(options, uncompressedData, bos);
        return bos.toByteArray();
    }

    /** Cached JVM compressor */
    private final Deflater javaCompressor = new Deflater(Deflater.BEST_COMPRESSION, true);

    /**
     * Compress using the best standard JVM deflate compression.
     * TODO Option customization
     *
     * @param uncompressedData uncompressed data
     * @return compressed data
     */
    private byte[] compressStandard(byte[] uncompressedData) {
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
     * Create a zip file with its contents and all embeded zip or jar files stored uncompressed recursively from the given input.
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

    /** Cached decompressor */
    private final DeflateDecompressor decomp = new DeflateDecompressor();

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
            // TODO Option customization
            final byte[] recompressedData = compressStandard(uncompressedData);

            // TODO Verify data integrity

            if (recompressedData.length < compressedSize) {
                compressedData = recompressedData;
                compressedSize = recompressedData.length;
                compressionMethod = ZipCompressions.DEFLATED;
            }
        }

        if (recompressZopfli) {
            try {
                // TODO Option customization
                final byte[] zopfliCompressedData = compressZopfli(uncompressedData);

                // TODO Verify data integrity

                if (zopfliCompressedData.length < compressedSize) {
                    compressedData = zopfliCompressedData;
                    compressedSize = zopfliCompressedData.length;
                    compressionMethod = ZipCompressions.DEFLATED;
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
        final byte[] uncompressedData;

        if (compressionMethod == ZipCompressions.DEFLATED) {
            uncompressedData = ByteDataUtil.toByteArray(decomp.decompress(fileHeader, fileHeader.getFileData()));
        } else if (compressionMethod == ZipCompressions.STORED) {
            uncompressedData = compressedData;
        } else {
            uncompressedData = ByteDataUtil.toByteArray(ZipCompressions.decompress(fileHeader));
        }

        final String localFileName = fileHeader.getFileNameAsString();
        final boolean zipLike = recursiveStore && (localFileName != null) && (localFileName.endsWith(".jar") || localFileName.endsWith(".zip"));
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
        byte[] uncompressedData;

        if (compressionMethod == ZipCompressions.DEFLATED) {
            uncompressedData = ByteDataUtil.toByteArray(decomp.decompress(fileHeader, fileHeader.getFileData()));
        } else if (compressionMethod == ZipCompressions.STORED) {
            uncompressedData = compressedData;
        } else {
            uncompressedData = ByteDataUtil.toByteArray(ZipCompressions.decompress(fileHeader));
        }

        final String localFileName = fileHeader.getFileNameAsString();
        final boolean zipLike = recursiveStore && (localFileName != null) && (localFileName.endsWith(".jar") || localFileName.endsWith(".zip"));

        if (zipLike) {
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
     * Optimises a ZipArchive, with the configured settings.
     *
     * @param forceRecursiveStore if true, store the contents of this and all embeded zip or jar files uncompressed recursively
     * @param archive the ZipArchive to optimise
     * @param outputStream output stream for optimised jar to be written to
     * @return true, if successful
     */
    private boolean optimiseJar(boolean forceRecursiveStore, ZipArchive archive, OutputStream outputStream) throws IOException {
        final boolean recompress = recompressZopfli || recompressStandard || recompressStore;
        final HashMap<Integer, EntryData> crcToEntryData = new HashMap<>();
        int offset = 0;

        // Local file headers:
        for (final LocalFileHeader fileHeader : archive.getLocalFiles()) {
            int crc32 = fileHeader.getCrc32();
            final int originalCrc32 = crc32;
            int realCompressedSize = (int) fileHeader.getCompressedSize();
            int realUncompressedSize = (int) fileHeader.getUncompressedSize();

            if ((realUncompressedSize == 0) || crcToEntryData.containsKey(crc32)) {
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
            writeShortLE(outputStream, fileHeader.getVersionNeededToExtract());
            // General purpose bit flag
            writeShortLE(outputStream, fileHeader.getGeneralPurposeBitFlag());
            // Compression method
            writeShortLE(outputStream, compressionMethod);
            // Last modification time
            final int lastModFileTime = removeTimestamps ? EARLIEST_TIME : fileHeader.getLastModFileTime();
            writeShortLE(outputStream, lastModFileTime);
            // Last modification date
            final int lastModFileDate = removeTimestamps ? EARLIEST_DATE : fileHeader.getLastModFileDate();
            writeShortLE(outputStream, lastModFileDate);
            // CRC32
            writeIntLE(outputStream, crc32);
            // Compressed size
            final int localCompressedSize = (removeFileLength && !exclude) ? 0 : realCompressedSize;
            writeIntLE(outputStream, localCompressedSize);
            // Uncompressed size
            final int localUncompressedSize = (removeFileLength && !exclude) ? 0 : realUncompressedSize;
            writeIntLE(outputStream, localUncompressedSize);
            // File name optimisation
            final boolean isManifest = fileHeader.getFileNameAsString().contains("MANIFEST");
            final int fileNameLength = (removeFileNames && !isManifest && !exclude) ? 0 : fileHeader.getFileNameLength();
            final byte[] fileName = (removeFileNames && !isManifest && !exclude) ? new byte[] { } : ByteDataUtil.toByteArray(fileHeader.getFileName());
            // File name length
            writeShortLE(outputStream, fileNameLength);
            // Extra field length
            final int extraFieldLength = fileHeader.getExtraFieldLength();
            writeShortLE(outputStream, extraFieldLength);
            // File name
            outputStream.write(fileName);
            // Extra field
            final byte[] extra = ByteDataUtil.toByteArray(fileHeader.getExtraField());
            outputStream.write(extra);
            // Compressed data
            // TODO This feels wrong?
            outputStream.write(fileData, 0, realCompressedSize);
            final EntryData entryData = new EntryData(crc32, realUncompressedSize, realCompressedSize, compressionMethod, offset);
            crcToEntryData.put(crc32, entryData);

            if (crc32 != originalCrc32) {
                crcToEntryData.put(originalCrc32, entryData);
            }

            offset += 30 + fileNameLength + extraFieldLength + realCompressedSize;
        }

        final int startCentral = offset;
        int centralEntries = 0;

        // Central directory file headers:
        for (final CentralDirectoryFileHeader centralDir : archive.getCentralDirectories()) {
            final int crc32 = centralDir.getCrc32();

            if (!crcToEntryData.containsKey(crc32)) {
                continue;
            }

            final EntryData entryData = crcToEntryData.get(crc32);
            final int uncompressedSize = entryData.uncompressedSize;

            if ((uncompressedSize == 0) || (centralDir.getUncompressedSize() == 0)) {
                continue;
            }

            // Header
            writeIntLE(outputStream, ZipPatterns.CENTRAL_DIRECTORY_FILE_HEADER_QUAD);
            // Made by
            writeShortLE(outputStream, centralDir.getVersionMadeBy());
            // Minimum version
            writeShortLE(outputStream, centralDir.getVersionNeededToExtract());
            // General purpose bit flag
            writeShortLE(outputStream, centralDir.getGeneralPurposeBitFlag());
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
            // Compressed size
            writeIntLE(outputStream, entryData.compressedSize);
            // Uncompressed size
            writeIntLE(outputStream, uncompressedSize);
            // File name length
            final int fileNameLength = centralDir.getFileNameLength();
            writeShortLE(outputStream, fileNameLength);
            // Extra field length
            final int extraFieldLength = centralDir.getExtraFieldLength();
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
            final byte[] extra = ByteDataUtil.toByteArray(centralDir.getExtraField());
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
        writeShortLE(outputStream, end.getDiskNumber());
        // Central directory start disk
        writeShortLE(outputStream, end.getCentralDirectoryStartDisk());
        // TODO What is this?
        writeShortLE(outputStream, end.getCentralDirectoryStartOffset());
        // Central directory entries
        writeShortLE(outputStream, centralEntries);
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
        final ZipArchive archive = ZipIO.readJvm(input);
        final File outputFile = output.toFile();

        if (outputFile.exists()) {
            if (!overwrite) {
                return false;
            }

            outputFile.delete();
        }

        try
            (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            return optimiseJar(archive, outputStream);
        }
    }

}
