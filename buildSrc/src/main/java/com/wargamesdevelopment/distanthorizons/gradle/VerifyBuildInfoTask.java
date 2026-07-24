package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
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

public abstract class VerifyBuildInfoTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getBuildInfoFile();

    @Input
    public abstract Property<String> getExpectedVersion();

    @Input
    public abstract Property<String> getExpectedMode();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void verify() throws IOException {
        String json = Files.readString(getBuildInfoFile().get().getAsFile().toPath(), StandardCharsets.UTF_8);
        require(json, "\"schemaVersion\": 1");
        require(json, "\"modId\": \"distanthorizons\"");
        require(json, "\"modVersion\": \"" + getExpectedVersion().get() + "\"");
        require(json, "\"releaseChannel\": \"RELEASE_CANDIDATE\"");
        require(json, "\"repository\": \"" + ProvenanceSupport.REPOSITORY + "\"");
        require(json, "\"updaterPolicy\": \"MANAGED_DISABLED\"");
        require(json, "\"reproducibleBuild\": true");
        String mode = getExpectedMode().get().toUpperCase(java.util.Locale.ROOT);
        if (mode.equals("VALIDATION")) {
            require(json, "\"commit\": \"" + ProvenanceSupport.BASE_COMMIT + "\"");
            require(json, "\"treeState\": \"modified\"");
            require(json, "\"buildSource\": \"UNCOMMITTED_VALIDATION\"");
        } else if (mode.equals("FINAL")) {
            require(json, "\"treeState\": \"clean\"");
            require(json, "\"buildSource\": \"CLEAN_GIT_CHECKOUT\"");
            reject(json, "dirty");
        } else {
            throw new IllegalStateException("Unexpected provenance mode: " + mode);
        }
        for (String forbidden : java.util.List.of("UNKNOWN", "NO-GIT-TAG-SET", "/Users/", "C:\\\\Users\\", "/home/")) {
            reject(json, forbidden);
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("version", getExpectedVersion().get());
        report.put("mode", mode);
        report.put("sha256", FoundationSupport.sha256(getBuildInfoFile().get().getAsFile().toPath()));
        report.put("valid", true);
        FoundationSupport.writeProperties(getReportFile().get().getAsFile().toPath(), report, "build provenance");
        getLogger().lifecycle("Build provenance verification PASSED");
    }

    private static void require(String text, String token) {
        if (!text.contains(token)) {
            throw new IllegalStateException("build_info.json lacks expected token: " + token);
        }
    }

    private static void reject(String text, String token) {
        if (text.contains(token)) {
            throw new IllegalStateException("build_info.json contains forbidden token: " + token);
        }
    }
}
