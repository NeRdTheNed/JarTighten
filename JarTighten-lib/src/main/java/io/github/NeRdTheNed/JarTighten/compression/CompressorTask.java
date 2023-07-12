package io.github.NeRdTheNed.JarTighten.compression;

import java.util.concurrent.Callable;

public class CompressorTask implements Callable<byte[]> {

    private final Compressor compressor;
    private final byte[] uncompressedData;

    CompressorTask(Compressor comp, byte[] uncompressedData) {
        compressor = comp;
        this.uncompressedData = uncompressedData;
    }

    @Override
    public byte[] call() throws Exception {
        return compressor.compress(uncompressedData);
    }

}
