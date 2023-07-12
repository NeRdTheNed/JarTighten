package io.github.NeRdTheNed.JarTighten.compression;

import java.io.IOException;

public interface MultiCompressor<T> extends Compressor {
    /** Compress data to a deflate stream with the given settings */
    byte[] compressWithOptions(byte[] uncompressedData, T options) throws IOException;
}
