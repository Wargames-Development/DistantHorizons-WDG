package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class AuditWargamesModpackCompatibilityTask extends DefaultTask {

    @InputFiles
    public abstract ConfigurableFileCollection getAuditInput();

    @OutputFile
    public abstract RegularFileProperty getJsonReport();

    @OutputFile
    public abstract RegularFileProperty getTextReport();

    @TaskAction
    public void audit() throws IOException {
        if (getAuditInput().getFiles().size() != 1) {
            throw new IllegalStateException("Supply exactly one -PwdgModpackAuditInput path");
        }
        File input = getAuditInput().getSingleFile();
        ModpackAuditSupport.AuditResult result = ModpackAuditSupport.audit(input);
        Files.createDirectories(getJsonReport().get().getAsFile().toPath().getParent());
        Files.writeString(getJsonReport().get().getAsFile().toPath(), ModpackAuditSupport.json(result), StandardCharsets.UTF_8);
        Files.writeString(getTextReport().get().getAsFile().toPath(), ModpackAuditSupport.text(result), StandardCharsets.UTF_8);
        if (!result.passed()) {
            throw new IllegalStateException("Modpack compatibility audit failed; inspect " + getTextReport().get().getAsFile());
        }
    }
}
