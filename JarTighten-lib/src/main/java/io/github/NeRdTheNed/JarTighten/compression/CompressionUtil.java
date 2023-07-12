package io.github.NeRdTheNed.JarTighten.compression;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.zip.Deflater;

import com.jcraft.jzlib.JZlib;

import io.github.NeRdTheNed.JarTighten.JarTighten.Strategy;

/** Utility class for finding the best way to compress given data in the deflate format */
public class CompressionUtil {
    /** Amount of Zopfli iterations */
    private static final int ZOPFLI_ITER = 20;

    /** JZopfli default max block splitting */
    private static final int JZOPFLI_DEFAULT_SPLIT = 15;

    private final Compressor[] compressors;

    /** Construct the list of compressors for the given settings */
    private static Compressor[] getCompressors(boolean java, boolean jzlib, boolean jzopfli, boolean cafeundzopfli, int iter, Strategy mode, int defaultSplit) {
        final List<Compressor> compressorsList = new ArrayList<>();

        if (java) {
            compressorsList.add(new JavaCompressor());

            if (mode.ordinal() >= Strategy.MULTI_CHEAP.ordinal()) {
                compressorsList.add(new JavaCompressor(Deflater.FILTERED));
                compressorsList.add(new JavaCompressor(Deflater.HUFFMAN_ONLY));
            }
        }

        if (jzopfli) {
            compressorsList.add(new MultiJZopfliCompressor(iter, mode.ordinal() >= Strategy.EXTENSIVE.ordinal(), defaultSplit));
        }

        if (cafeundzopfli) {
            compressorsList.add(new MultiCafeUndZopfliCompressor(iter, mode.ordinal() >= Strategy.EXTENSIVE.ordinal()));
        }

        if (jzlib) {
            compressorsList.add(new JZLibCompressor());

            if (mode.ordinal() >= Strategy.MULTI_CHEAP.ordinal()) {
                compressorsList.add(new JZLibCompressor(JZlib.Z_FILTERED));
                compressorsList.add(new JZLibCompressor(JZlib.Z_HUFFMAN_ONLY));
            }
        }

        return compressorsList.toArray(new Compressor[0]);
    }

    public CompressionUtil(boolean java, boolean jzlib, boolean jzopfli, boolean cafeundzopfli, int iter, Strategy mode, int defaultSplit) {
        compressors = getCompressors(java, jzlib, jzopfli, cafeundzopfli, iter, mode, defaultSplit);
    }

    public CompressionUtil(boolean java, boolean jzlib, boolean jzopfli, boolean cafeundzopfli, Strategy mode) {
        this(java, jzlib, jzopfli, cafeundzopfli, ZOPFLI_ITER, mode, JZOPFLI_DEFAULT_SPLIT);
    }

    private Supplier<ExecutorService> execService = () -> {
        final ExecutorService execServiceLocal = Executors.newCachedThreadPool();
        execService = () -> execServiceLocal;
        return execServiceLocal;
    };

    /** Use the configured compressors to find the smallest compressed output */
    public byte[] compress(byte[] uncompressedData, boolean threaded) throws IOException {
        byte[] compressedData = null;

        if (threaded) {
            final ExecutorCompletionService<byte[]> compService = new ExecutorCompletionService<>(execService.get());
            int tasks = 0;

            for (final Compressor compressor : compressors) {
                compService.submit(new CompressorTask(compressor, uncompressedData));
                tasks++;
            }

            for (int i = 0; i < tasks; i++) {
                try {
                    final Future<byte[]> result = compService.take();
                    final byte[] currentResult = result.get();

                    if ((compressedData == null) || (currentResult.length < compressedData.length)) {
                        compressedData = currentResult;
                    }
                } catch (final Exception e) {
                    // TODO Better error handling
                    System.err.println("Exception thrown while running compression task");
                    e.printStackTrace();
                }
            }
        } else {
            for (final Compressor compressor : compressors) {
                try {
                    final byte[] currentResult = compressor.compress(uncompressedData);

                    if ((compressedData == null) || (currentResult.length < compressedData.length)) {
                        compressedData = currentResult;
                    }
                } catch (final Exception e) {
                    // TODO Handle errors more gracefully
                    System.err.println("Exception thrown while compressing data");
                    e.printStackTrace();
                }
            }
        }

        if (compressedData == null) {
            throw new IOException("Unable to compress data");
        }

        return compressedData;
    }

}
