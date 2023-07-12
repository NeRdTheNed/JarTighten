package io.github.NeRdTheNed.JarTighten.compression;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.jcraft.jzlib.Deflater;
import com.jcraft.jzlib.DeflaterOutputStream;
import com.jcraft.jzlib.JZlib;

public class JZLibCompressor implements Compressor {
    /**
     * Compress using JZlib.
     *
     * @param uncompressedData uncompressed data
     * @return compressed data
     */
    @Override
    public byte[] compress(byte[] uncompressedData) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final Deflater jzlibCompressor = new Deflater(JZlib.Z_BEST_COMPRESSION, true);

        try
            (DeflaterOutputStream dos = new DeflaterOutputStream(bos, jzlibCompressor)) {
            dos.write(uncompressedData);
        }

        jzlibCompressor.end();
        return bos.toByteArray();
    }

}
