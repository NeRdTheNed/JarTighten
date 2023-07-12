package io.github.NeRdTheNed.JarTighten.compression;

import static lu.luz.jzopfli.Zopfli_lib.ZopfliCompress;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import lu.luz.jzopfli.ZopfliH.ZopfliFormat;
import lu.luz.jzopfli.ZopfliH.ZopfliOptions;

/** Compressor using JZopfli */
public class MultiJZopfliCompressor implements MultiCompressor<ZopfliOptions> {
    /** JZopfli options */
    private final ZopfliOptions[] options;

    /** Construct JZopfli options for the given settings */
    private static ZopfliOptions[] getOptions(int iter, boolean extensive, int defaultSplit) {
        final List<ZopfliOptions> jzopfliOptionsList = new ArrayList<>();

        if (extensive) {
            for (final int split : new int[] {defaultSplit, 0}) {
                final ZopfliOptions jzOptionsFirst = new ZopfliOptions();
                jzOptionsFirst.verbose = false;
                jzOptionsFirst.verbose_more = false;
                jzOptionsFirst.numiterations = iter;
                jzOptionsFirst.blocksplitting = true;
                jzOptionsFirst.blocksplittinglast = false;
                jzOptionsFirst.blocksplittingmax = split;
                jzopfliOptionsList.add(jzOptionsFirst);
                final ZopfliOptions jzOptionsLast = new ZopfliOptions();
                jzOptionsLast.verbose = false;
                jzOptionsLast.verbose_more = false;
                jzOptionsLast.numiterations = iter;
                jzOptionsLast.blocksplitting = true;
                jzOptionsLast.blocksplittinglast = true;
                jzOptionsLast.blocksplittingmax = split;
                jzopfliOptionsList.add(jzOptionsLast);
            }
            final ZopfliOptions jzOptionsNoSplit = new ZopfliOptions();
            jzOptionsNoSplit.verbose = false;
            jzOptionsNoSplit.verbose_more = false;
            jzOptionsNoSplit.numiterations = iter;
            jzOptionsNoSplit.blocksplitting = false;
            jzOptionsNoSplit.blocksplittinglast = false;
            jzOptionsNoSplit.blocksplittingmax = 0;
            jzopfliOptionsList.add(jzOptionsNoSplit);
        } else {
            final ZopfliOptions jzOptionsFirst = new ZopfliOptions();
            jzOptionsFirst.verbose = false;
            jzOptionsFirst.verbose_more = false;
            jzOptionsFirst.numiterations = iter;
            jzOptionsFirst.blocksplitting = true;
            jzOptionsFirst.blocksplittinglast = false;
            jzOptionsFirst.blocksplittingmax = defaultSplit;
            jzopfliOptionsList.add(jzOptionsFirst);
        }

        return jzopfliOptionsList.toArray(new ZopfliOptions[0]);
    }

    MultiJZopfliCompressor(ZopfliOptions[] options) {
        this.options = options;
    }

    MultiJZopfliCompressor(int iter, boolean extensive, int defaultSplit) {
        this(getOptions(iter, extensive, defaultSplit));
    }

    /**
     * Compress using JZopfli deflate compression.
     *
     * @param uncompressedData uncompressed data
     * @return compressed data
     */
    @Override
    public byte[] compress(byte[] uncompressedData) throws IOException {
        byte[] compressedData = null;

        for (final ZopfliOptions option : options) {
            final byte[] currentResult = compressWithOptions(uncompressedData, option);

            if ((compressedData == null) || (currentResult.length < compressedData.length)) {
                compressedData = currentResult;
            }
        }

        if (compressedData == null) {
            throw new IOException("Unable to compress data with JZopfli");
        }

        return compressedData;
    }

    /**
     * Compress using JZopfli deflate compression.
     *
     * @param uncompressedData uncompressed data
     * @param jzOptions zopfli compression options
     * @return compressed data
     */
    @Override
    public byte[] compressWithOptions(byte[] uncompressedData, ZopfliOptions jzOptions) throws IOException {
        final byte[][] compressedData = {{ 0 }};
        final int[] outputSize = {0};
        ZopfliCompress(jzOptions, ZopfliFormat.ZOPFLI_FORMAT_DEFLATE, uncompressedData, uncompressedData.length, compressedData, outputSize);
        // TODO Verify data integrity
        return compressedData[0];
    }

}
