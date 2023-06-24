package com.github.NeRdTheNed.JarTighten;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import software.coley.llzip.ZipIO;
import software.coley.llzip.format.model.ZipArchive;
import software.coley.llzip.format.write.JavaZipWriterStrategy;

public class JarTighten {

    // TODO Optimisation
    public static boolean optimiseJar(Path input, Path output) throws IOException {
        final ZipArchive archive = ZipIO.readJvm(input);
        final FileOutputStream outputStream = new FileOutputStream(output.toFile());
        new JavaZipWriterStrategy().write(archive, outputStream);
        outputStream.close();
        return true;
    }

}
