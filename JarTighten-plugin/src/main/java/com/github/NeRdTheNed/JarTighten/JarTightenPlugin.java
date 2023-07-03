package com.github.NeRdTheNed.JarTighten;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.jvm.tasks.Jar;

public class JarTightenPlugin implements Plugin<Project> {
    @Override
    public void apply(Project target) {
        final Jar jarTask = ((Jar) target.getTasks().getByName("jar"));
        final JarTightenTask jarTightenTask = target.getTasks().create("jartighten", JarTightenTask.class);
        jarTightenTask.getRecompress().convention(false);
        jarTightenTask.getRemoveTimestamps().convention(false);
        jarTightenTask.getRemoveFileLength().convention(false);
        jarTightenTask.getRemoveFileNames().convention(false);
        jarTightenTask.getInputFile().convention(jarTask.getArchiveFile());
        jarTightenTask.getOutputFile().convention(jarTask.getArchiveFile());
    }
}
