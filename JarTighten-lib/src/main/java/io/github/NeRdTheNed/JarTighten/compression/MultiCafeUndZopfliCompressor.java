package io.github.NeRdTheNed.JarTighten.compression;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import ru.eustas.zopfli.Options;
import ru.eustas.zopfli.Options.BlockSplitting;
import ru.eustas.zopfli.Options.OutputFormat;
import ru.eustas.zopfli.Zopfli;

/** Compressor using CafeUndZopfli */
public class MultiCafeUndZopfliCompressor implements MultiCompressor<Options> {
    /** Cached compressor */
    private final Zopfli zopfliCompressor;
    /** CafeUndZopfli options */
    private final Options[] options;

    /** Construct CafeUndZopfli options for the given settings */
    private static Options[] getOptions(int iter, boolean extensive) {
        return extensive ? new Options[] {
                   new Options(OutputFormat.DEFLATE, BlockSplitting.FIRST, iter),
                   new Options(OutputFormat.DEFLATE, BlockSplitting.LAST, iter),
                   new Options(OutputFormat.DEFLATE, BlockSplitting.NONE, iter),
        } : new Options[] { new Options(OutputFormat.DEFLATE, BlockSplitting.FIRST, iter) };
    }

    MultiCafeUndZopfliCompressor(Zopfli zopfliCompressor, Options[] options) {
        this.zopfliCompressor = zopfliCompressor;
        this.options = options;
    }

    MultiCafeUndZopfliCompressor(Options[] options) {
        this(new Zopfli(8 << 20), options);
    }

    MultiCafeUndZopfliCompressor(int iter, boolean extensive) {
        this(getOptions(iter, extensive));
    }

    /**
     * Compress using Zopfli deflate compression.
     *
     * @param uncompressedData uncompressed data
     * @return compressed data
     */
    @Override
    public byte[] compress(byte[] uncompressedData) throws IOException {
        byte[] compressedData = null;

        for (final Options option : options) {
            final byte[] currentResult = compressWithOptions(uncompressedData, option);

            if ((compressedData == null) || (currentResult.length < compressedData.length)) {
                compressedData = currentResult;
            }
        }

        if (compressedData == null) {
            throw new IOException("Unable to compress data with CafeUndZopfli");
        }

        return compressedData;
    }

    /**
     * Compress using Zopfli deflate compression.
     *
     * @param uncompressedData uncompressed data
     * @param options zopfli compression options
     * @return compressed data
     */
    @Override
    public byte[] compressWithOptions(byte[] uncompressedData, Options options) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        zopfliCompressor.compress(options, uncompressedData, bos);
        return bos.toByteArray();
    }

}
