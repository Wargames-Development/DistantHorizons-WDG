package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
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

@DisableCachingByDefault(because = "The task verifies exact artifacts built outside this repository")
public abstract class VerifyRequiredRuntimeArtifactsTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getDistantHorizonsJar();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getGeneratedRefmap();

    @Input
    public abstract Property<String> getDistantHorizonsVersion();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getLwjgl3ifyJar();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getLwjgl3ifyBundledClientPackage();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getRuntimeBundle();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getAngelicaJar();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getUniMixinsJar();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getGtnhLibJar();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void verify() throws IOException {
        File lwjgl = require(getLwjgl3ifyJar(), "wdgLwjgl3ifyProductionJar");
        File overlay = require(getLwjgl3ifyBundledClientPackage(), "wdgLwjgl3ifyBundledClientPackage");
        File runtime = require(getRuntimeBundle(), "wdgLwjgl3ifyRuntimeBundle");
        File angelica = require(getAngelicaJar(), "wdgAngelicaJar");
        File uniMixins = require(getUniMixinsJar(), "wdgUniMixinsJar");
        File gtnhLib = require(getGtnhLibJar(), "wdgGtnhLibJar");

        Map<String, Object> dh = DistantHorizonsArtifactVerifier.verify(
            getDistantHorizonsJar().get().getAsFile(),
            getGeneratedRefmap().get().getAsFile(),
            getDistantHorizonsVersion().get()
        );
        Map<String, Object> lwjglReport = Lwjgl3ifyCompatibilityVerifier.verify(lwjgl);
        Map<String, Object> gtnhReport = RuntimeArtifactVerifier.verifyGtnhLib(gtnhLib);
        Map<String, Object> angelicaReport = RuntimeArtifactVerifier.verifyAngelica(angelica);
        Map<String, Object> uniReport = RuntimeArtifactVerifier.verifyUniMixins(uniMixins);
        Map<String, Object> runtimeReport = RuntimeArtifactVerifier.verifyRuntimeBundle(runtime);
        Map<String, Object> overlayReport = RuntimeArtifactVerifier.verifyBundledClientOverlay(overlay, lwjgl, runtime);

        Map<String, Object> report = new LinkedHashMap<>();
        copyIdentity(report, "distantHorizons", dh);
        copyIdentity(report, "lwjgl3ify", lwjglReport);
        copyIdentity(report, "gtnhLib", gtnhReport);
        copyIdentity(report, "angelica", angelicaReport);
        copyIdentity(report, "uniMixins", uniReport);
        copyIdentity(report, "runtimeBundle", runtimeReport);
        copyIdentity(report, "bundledClient", overlayReport);
        report.put("distantHorizonsCommit", CombinedClientSupport.DISTANT_HORIZONS_COMMIT);
        report.put("lwjgl3ifyCommit", CombinedClientSupport.LWJGL3IFY_COMMIT);
        report.put("verified", true);

        FoundationSupport.writeProperties(
            getReportFile().get().getAsFile().toPath(),
            report,
            "Change 006 required runtime artifact verification"
        );
        report.forEach((key, value) -> getLogger().lifecycle("{}={}", key, value));
        getLogger().lifecycle("Required runtime artifact verification PASSED");
    }

    private static File require(RegularFileProperty property, String gradleProperty) {
        if (!property.isPresent()) {
            throw new IllegalStateException("Set -P" + gradleProperty + "=/absolute/path/to/the/exact/artifact");
        }
        return property.get().getAsFile();
    }

    private static void copyIdentity(Map<String, Object> target, String prefix, Map<String, Object> source) {
        for (String key : new String[] {"artifactName", "artifactSize", "artifactSha256", "version", "implementationVersion"}) {
            if (source.containsKey(key)) {
                target.put(prefix + "." + key, source.get(key));
            }
        }
    }
}
