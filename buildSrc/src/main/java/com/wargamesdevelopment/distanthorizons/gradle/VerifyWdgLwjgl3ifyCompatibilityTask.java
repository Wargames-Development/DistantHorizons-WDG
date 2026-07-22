package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.IOException;
import java.util.Map;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Cross-repository artifact verification must inspect exact external bytes")
public abstract class VerifyWdgLwjgl3ifyCompatibilityTask extends DefaultTask {

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getProductionArtifact();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void verify() throws IOException {
        if (!getProductionArtifact().isPresent()) {
            throw new IllegalStateException(
                "Set -PwdgLwjgl3ifyProductionJar=/absolute/path/to/the/exact/unclassified/reobfJar/output"
            );
        }
        Map<String, Object> report = Lwjgl3ifyCompatibilityVerifier.verify(
            getProductionArtifact().get().getAsFile()
        );
        FoundationSupport.writeProperties(getReportFile().get().getAsFile().toPath(), report, "lwjgl3ify-wdg compatibility verification");
        report.forEach((key, value) -> getLogger().lifecycle("{}={}", key, value));
        getLogger().lifecycle("lwjgl3ify-wdg compatibility verification PASSED");
    }
}
