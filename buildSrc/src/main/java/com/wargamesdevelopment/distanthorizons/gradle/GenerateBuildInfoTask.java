package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Git state and an explicit tracked-source digest define the deterministic output")
public abstract class GenerateBuildInfoTask extends DefaultTask {

    @Internal
    public abstract DirectoryProperty getRepositoryDirectory();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceFiles();

    @Input
    public abstract Property<String> getModId();

    @Input
    public abstract Property<String> getModVersion();

    @Input
    public abstract Property<String> getMinecraftVersion();

    @Input
    public abstract Property<String> getForgeVersion();

    @Input
    public abstract Property<String> getProvenanceMode();

    @Optional
    @Input
    public abstract Property<String> getExplicitSourceCommit();

    @Optional
    @Input
    public abstract Property<String> getExplicitSourceBranch();

    @Optional
    @Input
    public abstract Property<String> getExplicitSourceTreeState();

    @Optional
    @Input
    public abstract Property<String> getExplicitSourceTreeDigest();

    @Optional
    @Input
    public abstract Property<String> getExpectedFinalCommit();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void generate() throws IOException {
        ProvenanceSupport.Resolved resolved = ProvenanceSupport.resolve(
            getRepositoryDirectory().get().getAsFile().toPath(),
            new ArrayList<>(getSourceFiles().getFiles()),
            getProvenanceMode().get(),
            getExplicitSourceCommit().getOrNull(),
            getExplicitSourceBranch().getOrNull(),
            getExplicitSourceTreeState().getOrNull(),
            getExplicitSourceTreeDigest().getOrNull(),
            getExpectedFinalCommit().getOrNull()
        );
        String json = ProvenanceSupport.buildInfoJson(
            getModId().get(),
            getModVersion().get(),
            getMinecraftVersion().get(),
            getForgeVersion().get(),
            resolved
        );
        var output = getOutputFile().get().getAsFile().toPath();
        Files.createDirectories(output.getParent());
        Files.writeString(output, json, StandardCharsets.UTF_8);
        getLogger().lifecycle("Generated build_info.json: commit={}, state={}, source={}, digest={}",
            resolved.commit(), resolved.treeState(), resolved.buildSource(), resolved.sourceTreeDigest());
    }
}
