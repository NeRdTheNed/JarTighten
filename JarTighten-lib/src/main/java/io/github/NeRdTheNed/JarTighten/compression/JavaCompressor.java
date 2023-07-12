package io.github.NeRdTheNed.JarTighten.compression;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;

/** Compressor using the JVM Deflater class */
public class JavaCompressor implements Compressor {
    /** Cached deflater */
    private final Deflater jvmCompressor;

    public JavaCompressor(Deflater jvmCompressor, int strategy) {
        this.jvmCompressor = jvmCompressor;
        jvmCompressor.setLevel(Deflater.BEST_COMPRESSION);
        jvmCompressor.setStrategy(strategy);
    }

    public JavaCompressor(Deflater jvmCompressor) {
        this(jvmCompressor, Deflater.DEFAULT_STRATEGY);
    }

    public JavaCompressor(int strategy) {
        this(new Deflater(Deflater.BEST_COMPRESSION, true), strategy);
    }

    public JavaCompressor() {
        this(new Deflater(Deflater.BEST_COMPRESSION, true));
    }

    /**
     * Compress using the best standard JVM deflate compression.
     *
     * @param uncompressedData uncompressed data
     * @return compressed data
     */
    @Override
    public byte[] compress(byte[] uncompressedData) {
        jvmCompressor.setInput(uncompressedData);
        jvmCompressor.finish();
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final byte[] buffer = new byte[4096];

        while (!jvmCompressor.finished()) {
            final int deflated = jvmCompressor.deflate(buffer);
            bos.write(buffer, 0, deflated);
        }

        jvmCompressor.reset();
        return bos.toByteArray();
    }

}
