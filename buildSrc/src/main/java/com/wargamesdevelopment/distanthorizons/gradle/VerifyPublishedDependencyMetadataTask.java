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

@DisableCachingByDefault(because = "Publication metadata verification emits an audit report")
public abstract class VerifyPublishedDependencyMetadataTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getPomFile();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getModuleMetadataFile();

    @Input
    public abstract Property<String> getExpectedVersion();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void verify() throws IOException {
        Map<String, Object> report = PublishedMetadataVerifier.verify(
            getPomFile().get().getAsFile(),
            getModuleMetadataFile().get().getAsFile(),
            getExpectedVersion().get()
        );
        FoundationSupport.writeProperties(getReportFile().get().getAsFile().toPath(), report, "Distant Horizons published dependency metadata verification");
        report.forEach((key, value) -> getLogger().lifecycle("{}={}", key, value));
        getLogger().lifecycle("Distant Horizons published dependency metadata verification PASSED");
    }
}
