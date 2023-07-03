package com.github.NeRdTheNed.JarTighten;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.jvm.tasks.Jar;

public class JarTightenPlugin implements Plugin<Project> {

    @Override
    public void apply(Project target) {
        final Jar jarTask = ((Jar) target.getTasks().getByName("jar"));
        final JarTightenTask jarTightenTask = target.getTasks().create("jartighten", JarTightenTask.class);
        jarTightenTask.getInputFile().set(jarTask.getArchiveFile());
        jarTightenTask.getOutputFile().set(jarTask.getArchiveFile());
    }
}
