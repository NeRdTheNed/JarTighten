package com.github.NeRdTheNed.JarTighten;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import software.coley.llzip.ZipIO;
import software.coley.llzip.format.ZipPatterns;
import software.coley.llzip.format.model.CentralDirectoryFileHeader;
import software.coley.llzip.format.model.EndOfCentralDirectory;
import software.coley.llzip.format.model.LocalFileHeader;
import software.coley.llzip.format.model.ZipArchive;
import software.coley.llzip.util.ByteDataUtil;

public class JarTighten {
    private static final boolean REMOVE_TIMESTAMPS = true;
    private static final int EARLIEST_TIME = 0x6020;
    private static final int EARLIEST_DATE = 0x0021;

    private static final boolean REMOVE_LOCAL_FILE_LENGTH = true;

    private static final boolean REMOVE_LOCAL_FILE_NAMES = true;

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
    public static boolean optimiseJar(Path input, Path output, List<String> excludes) throws IOException {
        final ZipArchive archive = ZipIO.readJvm(input);

        try
            (FileOutputStream outputStream = new FileOutputStream(output.toFile())) {
            final HashMap<Integer, Integer> crcToOffset = new HashMap<>();
            int offset = 0;

            // Local file headers:
            for (final LocalFileHeader fileHeader : archive.getLocalFiles()) {
                final int crc32 = fileHeader.getCrc32();
                final int realCompressedSize = (int) fileHeader.getCompressedSize();
                final int realUncompressedSize = (int) fileHeader.getUncompressedSize();

                if ((realUncompressedSize == 0) || crcToOffset.containsKey(crc32)) {
                    continue;
                }

                final boolean exclude = excludes.contains(fileHeader.getFileNameAsString());
                // Header
                writeIntLE(outputStream, ZipPatterns.LOCAL_FILE_HEADER_QUAD);
                // Minimum version
                writeShortLE(outputStream, fileHeader.getVersionNeededToExtract());
                // General purpose bit flag
                writeShortLE(outputStream, fileHeader.getGeneralPurposeBitFlag());
                // Compression method
                writeShortLE(outputStream, fileHeader.getCompressionMethod());
                // Last modification time
                final int lastModFileTime = REMOVE_TIMESTAMPS ? EARLIEST_TIME : fileHeader.getLastModFileTime();
                writeShortLE(outputStream, lastModFileTime);
                // Last modification date
                final int lastModFileDate = REMOVE_TIMESTAMPS ? EARLIEST_DATE : fileHeader.getLastModFileDate();
                writeShortLE(outputStream, lastModFileDate);
                // CRC32
                writeIntLE(outputStream, crc32);
                // Compressed size
                final int localCompressedSize = (REMOVE_LOCAL_FILE_LENGTH && !exclude) ? 0 : realCompressedSize;
                writeIntLE(outputStream, localCompressedSize);
                // Uncompressed size
                final int localUncompressedSize = (REMOVE_LOCAL_FILE_LENGTH && !exclude) ? 0 : realUncompressedSize;
                writeIntLE(outputStream, localUncompressedSize);
                // File name optimisation
                final boolean isManifest = fileHeader.getFileNameAsString().contains("MANIFEST");
                final int fileNameLength = (REMOVE_LOCAL_FILE_NAMES && !isManifest && !exclude) ? 0 : fileHeader.getFileNameLength();
                final byte[] fileName = (REMOVE_LOCAL_FILE_NAMES && !isManifest && !exclude) ? new byte[] { } : ByteDataUtil.toByteArray(fileHeader.getFileName());
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
                final byte[] fileData = ByteDataUtil.toByteArray(fileHeader.getFileData());
                outputStream.write(fileData, 0, realCompressedSize);
                crcToOffset.put(crc32, offset);
                offset += 30 + fileNameLength + extraFieldLength + realCompressedSize;
            }

            final int startCentral = offset;
            int centralEntries = 0;

            // Central directory file headers:
            for (final CentralDirectoryFileHeader centralDir : archive.getCentralDirectories()) {
                final int crc32 = centralDir.getCrc32();
                final int uncompressedSize = (int) centralDir.getUncompressedSize();

                if ((uncompressedSize == 0) || !crcToOffset.containsKey(crc32)) {
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
                writeShortLE(outputStream, centralDir.getCompressionMethod());
                // Last modification time
                final int lastModFileTime = REMOVE_TIMESTAMPS ? EARLIEST_TIME : centralDir.getLastModFileTime();
                writeShortLE(outputStream, lastModFileTime);
                // Last modification date
                final int lastModFileDate = REMOVE_TIMESTAMPS ? EARLIEST_DATE : centralDir.getLastModFileDate();
                writeShortLE(outputStream, lastModFileDate);
                // CRC32
                writeIntLE(outputStream, centralDir.getCrc32());
                // Compressed size
                writeIntLE(outputStream, (int) centralDir.getCompressedSize());
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
                writeIntLE(outputStream, crcToOffset.get(crc32));
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
