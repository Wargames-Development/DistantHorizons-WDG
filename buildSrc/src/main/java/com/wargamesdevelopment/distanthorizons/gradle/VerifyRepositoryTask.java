package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.IOException;
import java.util.Map;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Repository verification must inspect the current working tree")
public abstract class VerifyRepositoryTask extends DefaultTask {

    @Internal
    public abstract DirectoryProperty getRepositoryDirectory();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getRepositoryFiles();

    @Input
    public abstract Property<String> getExpectedVersion();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void verify() throws IOException {
        Map<String, Object> report = RepositoryContractVerifier.verify(
            getRepositoryDirectory().get().getAsFile().toPath(), getExpectedVersion().get()
        );
        FoundationSupport.writeProperties(getReportFile().get().getAsFile().toPath(), report, "Distant Horizons repository verification");
        report.forEach((key, value) -> getLogger().lifecycle("{}={}", key, value));
        getLogger().lifecycle("Distant Horizons repository verification PASSED");
    }
}
