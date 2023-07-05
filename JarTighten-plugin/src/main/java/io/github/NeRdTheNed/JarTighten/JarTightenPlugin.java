package io.github.NeRdTheNed.JarTighten;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.jvm.tasks.Jar;

/** Configures a JarTightenTask using the output of the jar task by default */
public class JarTightenPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        if (project.getPlugins().hasPlugin(JavaPlugin.class)) {
            final Jar jarTask = (Jar) project.getTasks().getByName("jar");
            project.getTasks().register("jartighten", JarTightenTask.class, jarTightenTask -> {
                jarTightenTask.dependsOn("jar");
                jarTightenTask.getRecompressStore().convention(true);
                jarTightenTask.getRecompressStandard().convention(true);
                jarTightenTask.getRecompressZopfli().convention(false);
                jarTightenTask.getRemoveTimestamps().convention(false);
                jarTightenTask.getRemoveFileLength().convention(false);
                jarTightenTask.getRemoveFileNames().convention(false);
                jarTightenTask.getRemoveComments().convention(false);
                jarTightenTask.getRemoveExtra().convention(false);
                jarTightenTask.getRemoveDirectoryEntries().convention(true);
                jarTightenTask.getDeduplicateEntries().convention(false);
                jarTightenTask.getRecursiveStore().convention(false);
                jarTightenTask.getInputFile().convention(jarTask.getArchiveFile());
                jarTightenTask.getOutputFile().convention(jarTask.getArchiveFile());
            });
        }
    }
}
