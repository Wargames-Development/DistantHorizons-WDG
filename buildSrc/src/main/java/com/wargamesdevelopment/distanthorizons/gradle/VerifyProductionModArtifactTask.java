package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.IOException;
import java.util.Map;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Artifact verification emits an audit report and must inspect exact bytes")
public abstract class VerifyProductionModArtifactTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getArtifactFile();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getGeneratedRefmap();

    @Input
    public abstract Property<String> getExpectedVersion();

    @Input
    public abstract Property<String> getExpectedProvenanceMode();

    @org.gradle.api.tasks.Optional
    @Input
    public abstract Property<String> getExpectedCommit();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void verify() throws IOException {
        Map<String, Object> report = DistantHorizonsArtifactVerifier.verify(
            getArtifactFile().get().getAsFile(),
            getGeneratedRefmap().get().getAsFile(),
            getExpectedVersion().get(),
            getExpectedProvenanceMode().get(),
            getExpectedCommit().getOrNull()
        );
        FoundationSupport.writeProperties(getReportFile().get().getAsFile().toPath(), report, "Distant Horizons production artifact verification");
        report.forEach((key, value) -> getLogger().lifecycle("{}={}", key, value));
        getLogger().lifecycle("Distant Horizons production artifact verification PASSED");
    }
}
