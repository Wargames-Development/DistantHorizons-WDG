package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.File;
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

@DisableCachingByDefault(because = "The task intentionally creates two isolated packages and compares exact bytes")
public abstract class VerifyCombinedClientReproducibilityTask extends DefaultTask {

    @Input public abstract Property<String> getDistantHorizonsVersion();
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getDistantHorizonsJar();
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getGeneratedRefmap();
    @Optional @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getLwjgl3ifyJar();
    @Optional @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getGtnhLibJar();
    @Optional @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getUniMixinsJar();
    @Optional @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getAngelicaJar();
    @Optional @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getRuntimeBundle();
    @OutputFile public abstract RegularFileProperty getFirstPackage();
    @OutputFile public abstract RegularFileProperty getSecondPackage();
    @OutputFile public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void verify() throws IOException {
        CombinedClientSupport.PackageDefinition definition = new CombinedClientSupport.PackageDefinition(
            "combined-client", "DistantHorizons-WDG-Combined-Client", true, true
        );
        CombinedClientSupport.Inputs inputs = new CombinedClientSupport.Inputs(
            getDistantHorizonsJar().get().getAsFile(),
            getGeneratedRefmap().get().getAsFile(),
            getDistantHorizonsVersion().get(),
            require(getLwjgl3ifyJar(), "-PwdgLwjgl3ifyProductionJar"),
            require(getGtnhLibJar(), "-PwdgGtnhLibJar"),
            require(getUniMixinsJar(), "-PwdgUniMixinsJar"),
            require(getAngelicaJar(), "-PwdgAngelicaJar"),
            require(getRuntimeBundle(), "-PwdgLwjgl3ifyRuntimeBundle")
        );
        Map<String, Object> report = CombinedClientSupport.verifyReproducibility(
            definition,
            inputs,
            getFirstPackage().get().getAsFile().toPath(),
            getSecondPackage().get().getAsFile().toPath()
        );
        FoundationSupport.writeProperties(
            getReportFile().get().getAsFile().toPath(), report, "combined-client reproducibility"
        );
        report.forEach((key, value) -> getLogger().lifecycle("{}={}", key, value));
        getLogger().lifecycle("Combined-client reproducibility verification PASSED");
    }

    private static File require(RegularFileProperty property, String label) {
        if (!property.isPresent()) {
            throw new IllegalStateException("Missing explicit input: " + label);
        }
        return property.get().getAsFile();
    }
}
