package com.wargamesdevelopment.distanthorizons.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/** Shared typed inputs for deterministic Change 007 release-candidate packaging tasks. */
public abstract class ReleaseCandidateInputsTask extends DefaultTask {
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getDistantHorizonsJar();
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getGeneratedRefmap();
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getLwjgl3ifyJar();
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getGtnhLibJar();
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getUniMixinsJar();
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getAngelicaJar();
    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE) public abstract DirectoryProperty getDocumentationDirectory();
    @Input public abstract Property<String> getProvenanceMode();
    @Input public abstract Property<String> getExpectedCommit();
    @Input public abstract Property<String> getSourceTreeDigest();
    @OutputFile public abstract RegularFileProperty getReportFile();

    final ReleaseCandidateSupport.Inputs inputs() {
        return new ReleaseCandidateSupport.Inputs(
            getDistantHorizonsJar().get().getAsFile(), getGeneratedRefmap().get().getAsFile(),
            getLwjgl3ifyJar().get().getAsFile(), getGtnhLibJar().get().getAsFile(), getUniMixinsJar().get().getAsFile(),
            getAngelicaJar().get().getAsFile(), getProvenanceMode().get(), getExpectedCommit().get(),
            getSourceTreeDigest().get(), getDocumentationDirectory().get().getAsFile()
        );
    }
}
