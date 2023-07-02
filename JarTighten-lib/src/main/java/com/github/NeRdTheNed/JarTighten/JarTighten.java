package com.github.NeRdTheNed.JarTighten;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

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

public class JarTighten {
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

    private static final int EARLIEST_TIME = 0x6020;
    private static final int EARLIEST_DATE = 0x0021;

    private static void writeShortLE(OutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private static void writeIntLE(OutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    // TODO Optimisation, currently just copies input
    public static boolean optimiseJar(Path input, Path output, List<String> excludes, boolean removeTimestamps, boolean removeFileLength, boolean removeFileNames, boolean recompress) throws IOException {
        final DeflateDecompressor decomp = new DeflateDecompressor();
        final ZipArchive archive = ZipIO.readJvm(input);

        try
            (FileOutputStream outputStream = new FileOutputStream(output.toFile())) {
            final HashMap<Integer, EntryData> crcToEntryData = new HashMap<>();
            int offset = 0;

            // Local file headers:
            for (final LocalFileHeader fileHeader : archive.getLocalFiles()) {
                final int crc32 = fileHeader.getCrc32();
                int realCompressedSize = (int) fileHeader.getCompressedSize();
                final int realUncompressedSize = (int) fileHeader.getUncompressedSize();

                if ((realUncompressedSize == 0) || crcToEntryData.containsKey(crc32)) {
                    continue;
                }

                final int compressionMethod = fileHeader.getCompressionMethod();
                byte[] fileData = ByteDataUtil.toByteArray(fileHeader.getFileData());

                if (recompress && (compressionMethod == ZipCompressions.DEFLATED)) {
                    try {
                        final byte[] uncompressedData = ByteDataUtil.toByteArray(decomp.decompress(fileHeader, fileHeader.getFileData()));
                        // TODO Option customization
                        final Zopfli compressor = new Zopfli(8 << 20);
                        final Options options = new Options(OutputFormat.DEFLATE, BlockSplitting.FIRST, 20);
                        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        compressor.compress(options, uncompressedData, bos);
                        final byte[] zopfliCompressedData = bos.toByteArray();

                        // TODO Verify data integrity

                        if (zopfliCompressedData.length < realCompressedSize) {
                            fileData = zopfliCompressedData;
                            realCompressedSize = zopfliCompressedData.length;
                        }
                    } catch (final Exception e) {
                        // Ignored
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
                final int fileCommentLength = centralDir.getFileCommentLength();
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
                final byte[] fileComment = ByteDataUtil.toByteArray(centralDir.getFileComment());
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
            writeShortLE(outputStream, end.getZipCommentLength());
            // Comment
            final byte[] zipComment = ByteDataUtil.toByteArray(end.getZipComment());
            outputStream.write(zipComment);
        }

        return true;
    }

}
