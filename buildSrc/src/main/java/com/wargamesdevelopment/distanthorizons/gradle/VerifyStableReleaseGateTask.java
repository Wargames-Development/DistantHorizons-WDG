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

/** Deliberate future-only gate; Change 007 does not activate stable production. */
public abstract class VerifyStableReleaseGateTask extends DefaultTask {
    @Input public abstract Property<String> getReleaseChannel();
    @Input public abstract Property<Boolean> getStableConfirmation();
    @Input public abstract Property<String> getTreeState();
    @Input public abstract Property<String> getExpectedTag();
    @Input public abstract Property<String> getActualTag();
    @Input public abstract Property<String> getExpectedCommit();
    @Input public abstract Property<String> getActualCommit();
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getAcceptanceManifest();
    @OutputFile public abstract RegularFileProperty getReportFile();

    @TaskAction public void verify() throws IOException {
        StableReleaseGate.verify(
            getReleaseChannel().get(), getStableConfirmation().get(), getTreeState().get(),
            getExpectedTag().get(), getActualTag().get(), getExpectedCommit().get(), getActualCommit().get(),
            getAcceptanceManifest().get().getAsFile()
        );
        Map<String, Object> report = new java.util.LinkedHashMap<>();
        report.put("releaseChannel", getReleaseChannel().get());
        report.put("tag", getActualTag().get());
        report.put("commit", getActualCommit().get());
        report.put("treeState", getTreeState().get());
        report.put("verified", true);
        FoundationSupport.writeProperties(getReportFile().get().getAsFile().toPath(), report, "future stable release gate");
    }
}
