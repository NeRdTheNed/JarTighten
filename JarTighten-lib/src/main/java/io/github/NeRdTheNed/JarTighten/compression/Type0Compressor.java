package io.github.NeRdTheNed.JarTighten.compression;

import java.io.IOException;

/** Compressor that outputs data as a type 0 block */
public class Type0Compressor implements Compressor {
    /**
     * Create a deflate stream comprised of type 0 blocks from the given input.
     * This is never useful for practical purposes,
     * as it's always larger than an uncompressed (stored) zip entry by at least 5-ish bytes.
     * TODO Handle uncompressed data larger than allowed in a single type 0 block
     *
     * @param uncompressedData the input uncompressed data
     * @return stored deflate stream from the given input
     */
    @Override
    public byte[] compress(byte[] uncompressedData) throws IOException {
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
        return type0Block;
    }

}
