package io.github.NeRdTheNed.JarTighten.compression;

import java.io.IOException;

public interface Compressor {
    /** Compress data to a deflate stream */
    byte[] compress(byte[] uncompressedData) throws IOException;
}
