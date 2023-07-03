package com.github.NeRdTheNed.JarTighten;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.jvm.tasks.Jar;

public class JarTightenPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        if (project.getPlugins().hasPlugin(JavaPlugin.class)) {
            final Jar jarTask = (Jar) project.getTasks().getByName("jar");
            final JarTightenTask jarTightenTask = project.getTasks().create("jartighten", JarTightenTask.class);
            jarTightenTask.dependsOn("jar");
            jarTightenTask.getRecompress().convention(false);
            jarTightenTask.getRemoveTimestamps().convention(false);
            jarTightenTask.getRemoveFileLength().convention(false);
            jarTightenTask.getRemoveFileNames().convention(false);
            jarTightenTask.getInputFile().convention(jarTask.getArchiveFile());
            jarTightenTask.getOutputFile().convention(jarTask.getArchiveFile());
        }
    }
}
