package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class AuditDedicatedServerTask extends DefaultTask {

    @InputFiles public abstract ConfigurableFileCollection getServerInput();
    @OutputFile public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void audit() throws IOException {
        if (getServerInput().getFiles().size() != 1) {
            throw new IllegalStateException("Supply exactly one -PwdgServerAuditInput path");
        }
        File input = getServerInput().getSingleFile();
        List<String> clientOnly = ModpackAuditSupport.clientOnlyServerArtifacts(input);
        StringBuilder report = new StringBuilder("Dedicated-server client-stack audit\n");
        if (clientOnly.isEmpty()) {
            report.append("Result: PASS\nNo Distant Horizons client-only release artifacts detected.\n");
        } else {
            report.append("Result: FAIL\nClient-only artifacts detected:\n");
            clientOnly.forEach(value -> report.append("- ").append(value).append('\n'));
        }
        report.append("\nGTNHLib and UniMixins are not automatically removed because other server mods may require them.\n");
        Files.createDirectories(getReportFile().get().getAsFile().toPath().getParent());
        Files.writeString(getReportFile().get().getAsFile().toPath(), report, StandardCharsets.UTF_8);
        if (!clientOnly.isEmpty()) {
            throw new IllegalStateException("Dedicated server contains client-only release artifacts");
        }
    }
}
