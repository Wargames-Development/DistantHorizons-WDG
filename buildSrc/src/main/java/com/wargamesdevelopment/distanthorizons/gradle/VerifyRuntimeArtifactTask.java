package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.IOException;
import java.util.Map;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "External production artifacts must be re-inspected byte-for-byte")
public abstract class VerifyRuntimeArtifactTask extends DefaultTask {

    @Input
    public abstract Property<String> getArtifactKind();

    @Input
    public abstract Property<String> getPropertyHint();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getArtifactFile();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void verify() throws IOException {
        if (!getArtifactFile().isPresent()) {
            throw new IllegalStateException("Set -P" + getPropertyHint().get() + "=/absolute/path/to/the/exact/artifact");
        }
        Map<String, Object> report = switch (getArtifactKind().get()) {
            case "gtnhlib" -> RuntimeArtifactVerifier.verifyGtnhLib(getArtifactFile().get().getAsFile());
            case "angelica" -> RuntimeArtifactVerifier.verifyAngelica(getArtifactFile().get().getAsFile());
            case "unimixins" -> RuntimeArtifactVerifier.verifyUniMixins(getArtifactFile().get().getAsFile());
            case "runtime-bundle" -> RuntimeArtifactVerifier.verifyRuntimeBundle(getArtifactFile().get().getAsFile());
            default -> throw new IllegalStateException("Unknown runtime artifact kind: " + getArtifactKind().get());
        };
        FoundationSupport.writeProperties(
            getReportFile().get().getAsFile().toPath(),
            report,
            getArtifactKind().get() + " verification"
        );
        report.forEach((key, value) -> getLogger().lifecycle("{}={}", key, value));
        getLogger().lifecycle("{} verification PASSED", getArtifactKind().get());
    }
}
