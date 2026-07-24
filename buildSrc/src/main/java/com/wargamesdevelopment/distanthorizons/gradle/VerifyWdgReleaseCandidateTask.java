package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

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
import org.gradle.api.tasks.TaskAction;

public abstract class VerifyWdgReleaseCandidateTask extends DefaultTask {
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getReleaseArchive();
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

    @TaskAction public void verify() throws IOException {
        ReleaseCandidateSupport.Inputs inputs = new ReleaseCandidateSupport.Inputs(
            getDistantHorizonsJar().get().getAsFile(), getGeneratedRefmap().get().getAsFile(),
            getLwjgl3ifyJar().get().getAsFile(), getGtnhLibJar().get().getAsFile(), getUniMixinsJar().get().getAsFile(),
            getAngelicaJar().get().getAsFile(), getProvenanceMode().get(), getExpectedCommit().get(),
            getSourceTreeDigest().get(), getDocumentationDirectory().get().getAsFile()
        );
        ReleaseCandidateSupport.verifyReleaseArchive(inputs, getReleaseArchive().get().getAsFile());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("archiveSha256", FoundationSupport.sha256(getReleaseArchive().get().getAsFile().toPath()));
        report.put("buildSource", inputs.buildSource());
        report.put("runtimeDistributionMode", Lwjgl3ifyCompatibilityVerifier.RUNTIME_DISTRIBUTION_MODE);
        report.put("externalRuntimeBundlePresent", false);
        report.put("verified", true);
        FoundationSupport.writeProperties(
            getReportFile().get().getAsFile().toPath(), report, "WDG release candidate verification"
        );
    }
}
