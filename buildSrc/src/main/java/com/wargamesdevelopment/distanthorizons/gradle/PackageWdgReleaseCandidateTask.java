package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.IOException;
import java.util.Map;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class PackageWdgReleaseCandidateTask extends ReleaseCandidateInputsTask {
    @OutputFile public abstract RegularFileProperty getOutputFile();

    @TaskAction public void pack() throws IOException {
        Map<String, Object> report = ReleaseCandidateSupport.createReleaseArchive(inputs(), getOutputFile().get().getAsFile().toPath());
        FoundationSupport.writeProperties(getReportFile().get().getAsFile().toPath(), report, "WDG release candidate");
    }
}
