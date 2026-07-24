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

@DisableCachingByDefault(because = "Packaging performs its own deterministic byte-for-byte reproducibility verification")
public abstract class PackageCombinedClientTask extends DefaultTask {

    @Input
    public abstract Property<String> getPackageType();

    @Input
    public abstract Property<String> getRootDirectory();

    @Input
    public abstract Property<Boolean> getIncludeDistantHorizons();

    @Input
    public abstract Property<Boolean> getIncludeAngelica();

    @Input
    public abstract Property<String> getDistantHorizonsVersion();

    @Input
    public abstract Property<String> getProvenanceMode();

    @Input
    public abstract Property<String> getExpectedCommit();

    @Input
    public abstract Property<String> getSourceTreeDigest();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getDistantHorizonsJar();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getGeneratedRefmap();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getLwjgl3ifyJar();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getGtnhLibJar();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getUniMixinsJar();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getAngelicaJar();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void packageClient() throws IOException {
        CombinedClientSupport.PackageDefinition definition = definition();
        CombinedClientSupport.Inputs inputs = inputs();
        Map<String, Object> report = CombinedClientSupport.createPackage(
            definition,
            inputs,
            getOutputFile().get().getAsFile().toPath()
        );
        FoundationSupport.writeProperties(
            getReportFile().get().getAsFile().toPath(),
            report,
            definition.packageType() + " package"
        );
        report.forEach((key, value) -> getLogger().lifecycle("{}={}", key, value));
        getLogger().lifecycle("{} packaging PASSED", definition.packageType());
    }

    private CombinedClientSupport.PackageDefinition definition() {
        return new CombinedClientSupport.PackageDefinition(
            getPackageType().get(),
            getRootDirectory().get(),
            getIncludeDistantHorizons().get(),
            getIncludeAngelica().get()
        );
    }

    private CombinedClientSupport.Inputs inputs() {
        boolean includeDh = getIncludeDistantHorizons().get();
        boolean includeAngelica = getIncludeAngelica().get();
        return new CombinedClientSupport.Inputs(
            includeDh ? require(getDistantHorizonsJar(), "Distant Horizons production JAR") : null,
            includeDh ? require(getGeneratedRefmap(), "Distant Horizons generated refmap") : null,
            getDistantHorizonsVersion().get(),
            getProvenanceMode().get(),
            getExpectedCommit().get(),
            getSourceTreeDigest().get(),
            require(getLwjgl3ifyJar(), "-PwdgLwjgl3ifyProductionJar"),
            require(getGtnhLibJar(), "-PwdgGtnhLibJar"),
            require(getUniMixinsJar(), "-PwdgUniMixinsJar"),
            includeAngelica ? require(getAngelicaJar(), "-PwdgAngelicaJar") : null
        );
    }

    private static File require(RegularFileProperty property, String label) {
        if (!property.isPresent()) {
            throw new IllegalStateException("Missing explicit input: " + label);
        }
        return property.get().getAsFile();
    }
}
