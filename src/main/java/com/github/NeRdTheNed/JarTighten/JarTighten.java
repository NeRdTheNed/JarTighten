package com.github.NeRdTheNed.JarTighten;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

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
    public static boolean optimiseJar(Path input, Path output) throws IOException {
        final ZipArchive archive = ZipIO.readJvm(input);

        try
            (FileOutputStream outputStream = new FileOutputStream(output.toFile())) {
            // Local file headers:
            for (final LocalFileHeader fileHeader : archive.getLocalFiles()) {
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
                writeIntLE(outputStream, fileHeader.getCrc32());
                // Compressed size
                writeIntLE(outputStream, (int) fileHeader.getCompressedSize());
                // Uncompressed size
                writeIntLE(outputStream, (int) fileHeader.getUncompressedSize());
                // File name length
                writeShortLE(outputStream, fileHeader.getFileNameLength());
                // Extra field length
                writeShortLE(outputStream, fileHeader.getExtraFieldLength());
                // File name
                final byte[] fileName = ByteDataUtil.toByteArray(fileHeader.getFileName());
                outputStream.write(fileName);
                // Extra field
                final byte[] extra = ByteDataUtil.toByteArray(fileHeader.getExtraField());
                outputStream.write(extra);
                // Compressed data
                // TODO This feels wrong?
                final byte[] fileData = ByteDataUtil.toByteArray(fileHeader.getFileData());
                outputStream.write(fileData, 0, (int) fileHeader.getCompressedSize());
            }

            // Central directory file headers:
            for (final CentralDirectoryFileHeader centralDir : archive.getCentralDirectories()) {
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
                writeIntLE(outputStream, (int) centralDir.getUncompressedSize());
                // File name length
                writeShortLE(outputStream, centralDir.getFileNameLength());
                // Extra field length
                writeShortLE(outputStream, centralDir.getExtraFieldLength());
                // File comment length
                writeShortLE(outputStream, centralDir.getFileCommentLength());
                // Disk number where file starts
                writeShortLE(outputStream, centralDir.getDiskNumberStart());
                // Internal file attributes
                writeShortLE(outputStream, centralDir.getInternalFileAttributes());
                // External file attributes
                writeIntLE(outputStream, centralDir.getExternalFileAttributes());
                // Relative offset of local file header
                writeIntLE(outputStream, (int) centralDir.getRelativeOffsetOfLocalHeader());
                // File name
                final byte[] fileName = ByteDataUtil.toByteArray(centralDir.getFileName());
                outputStream.write(fileName);
                // Extra field
                final byte[] extra = ByteDataUtil.toByteArray(centralDir.getExtraField());
                outputStream.write(extra);
                // File comment
                final byte[] fileComment = ByteDataUtil.toByteArray(centralDir.getFileComment());
                outputStream.write(fileComment);
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
            writeShortLE(outputStream, end.getNumEntries());
            // Central directory size
            writeIntLE(outputStream, (int) end.getCentralDirectorySize());
            // Central directory offset
            writeIntLE(outputStream, (int) end.getCentralDirectoryOffset());
            // Comment length
            writeShortLE(outputStream, end.getZipCommentLength());
            // Comment
            final byte[] zipComment = ByteDataUtil.toByteArray(end.getZipComment());
            outputStream.write(zipComment);
        }

        return true;
    }

}
