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

@DisableCachingByDefault(because = "Verification must inspect the exact generated package and external bytes")
public abstract class VerifyCombinedClientPackageTask extends DefaultTask {

    @Input public abstract Property<String> getPackageType();
    @Input public abstract Property<String> getRootDirectory();
    @Input public abstract Property<Boolean> getIncludeDistantHorizons();
    @Input public abstract Property<Boolean> getIncludeAngelica();
    @Input public abstract Property<String> getDistantHorizonsVersion();

    @InputFile @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getPackageFile();
    @Optional @InputFile @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getDistantHorizonsJar();
    @Optional @InputFile @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getGeneratedRefmap();
    @Optional @InputFile @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getLwjgl3ifyJar();
    @Optional @InputFile @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getGtnhLibJar();
    @Optional @InputFile @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getUniMixinsJar();
    @Optional @InputFile @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getAngelicaJar();
    @Optional @InputFile @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getRuntimeBundle();

    @OutputFile public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void verify() throws IOException {
        CombinedClientSupport.PackageDefinition definition = new CombinedClientSupport.PackageDefinition(
            getPackageType().get(), getRootDirectory().get(), getIncludeDistantHorizons().get(), getIncludeAngelica().get()
        );
        boolean includeDh = definition.includeDistantHorizons();
        boolean includeAngelica = definition.includeAngelica();
        CombinedClientSupport.Inputs inputs = new CombinedClientSupport.Inputs(
            includeDh ? require(getDistantHorizonsJar(), "Distant Horizons production JAR") : null,
            includeDh ? require(getGeneratedRefmap(), "Distant Horizons generated refmap") : null,
            getDistantHorizonsVersion().get(),
            require(getLwjgl3ifyJar(), "-PwdgLwjgl3ifyProductionJar"),
            require(getGtnhLibJar(), "-PwdgGtnhLibJar"),
            require(getUniMixinsJar(), "-PwdgUniMixinsJar"),
            includeAngelica ? require(getAngelicaJar(), "-PwdgAngelicaJar") : null,
            require(getRuntimeBundle(), "-PwdgLwjgl3ifyRuntimeBundle")
        );
        Map<String, Object> report = CombinedClientSupport.verifyPackage(
            definition, inputs, getPackageFile().get().getAsFile()
        );
        FoundationSupport.writeProperties(
            getReportFile().get().getAsFile().toPath(), report, definition.packageType() + " verification"
        );
        report.forEach((key, value) -> getLogger().lifecycle("{}={}", key, value));
        getLogger().lifecycle("{} verification PASSED", definition.packageType());
    }

    private static File require(RegularFileProperty property, String label) {
        if (!property.isPresent()) {
            throw new IllegalStateException("Missing explicit input: " + label);
        }
        return property.get().getAsFile();
    }
}
