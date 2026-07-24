package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.IOException;
import java.util.Map;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class VerifyWdgReleaseCandidateReproducibilityTask extends ReleaseCandidateInputsTask {
    @OutputFile public abstract RegularFileProperty getOutputFile();
    @OutputFile public abstract RegularFileProperty getSecondOutputFile();

    @TaskAction public void verifyReproducibility() throws IOException {
        Map<String, Object> report = ReleaseCandidateSupport.verifyReproducibility(
            inputs(), getOutputFile().get().getAsFile().toPath(), getSecondOutputFile().get().getAsFile().toPath()
        );
        FoundationSupport.writeProperties(getReportFile().get().getAsFile().toPath(), report, "WDG release reproducibility");
    }
}
