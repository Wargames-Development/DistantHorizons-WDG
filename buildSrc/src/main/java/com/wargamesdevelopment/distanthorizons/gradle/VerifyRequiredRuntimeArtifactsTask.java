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

    @Input
    public abstract Property<String> getProvenanceMode();

    @Input
    public abstract Property<String> getExpectedCommit();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getLwjgl3ifyJar();

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
        File angelica = require(getAngelicaJar(), "wdgAngelicaJar");
        File uniMixins = require(getUniMixinsJar(), "wdgUniMixinsJar");
        File gtnhLib = require(getGtnhLibJar(), "wdgGtnhLibJar");

        Map<String, Object> dh = DistantHorizonsArtifactVerifier.verify(
            getDistantHorizonsJar().get().getAsFile(),
            getGeneratedRefmap().get().getAsFile(),
            getDistantHorizonsVersion().get(),
            getProvenanceMode().get(),
            getExpectedCommit().get()
        );
        Map<String, Object> lwjglReport = Lwjgl3ifyCompatibilityVerifier.verify(lwjgl);
        Map<String, Object> gtnhReport = RuntimeArtifactVerifier.verifyGtnhLib(gtnhLib);
        Map<String, Object> angelicaReport = RuntimeArtifactVerifier.verifyAngelica(angelica);
        Map<String, Object> uniReport = RuntimeArtifactVerifier.verifyUniMixins(uniMixins);

        Map<String, Object> report = new LinkedHashMap<>();
        copyIdentity(report, "distantHorizons", dh);
        copyIdentity(report, "lwjgl3ify", lwjglReport);
        copyIdentity(report, "gtnhLib", gtnhReport);
        copyIdentity(report, "angelica", angelicaReport);
        copyIdentity(report, "uniMixins", uniReport);
        report.put("distantHorizonsCommit", dh.get("buildInfoCommit"));
        report.put("lwjgl3ifyCommit", Lwjgl3ifyCompatibilityVerifier.EXPECTED_COMMIT);
        report.put("lwjgl3ify.runtimeDistributionMode", lwjglReport.get("runtimeDistributionMode"));
        report.put("lwjgl3ify.embeddedRuntimeCount", lwjglReport.get("embeddedRuntimeCount"));
        report.put("lwjgl3ify.javaRuntimeVersion", lwjglReport.get("javaRuntimeVersion"));
        for (Lwjgl3ifyCompatibilityVerifier.RuntimeArchive runtime
            : Lwjgl3ifyCompatibilityVerifier.PRIMARY_RUNTIMES) {
            String prefix = "embeddedRuntime." + runtime.platformId();
            report.put(prefix + ".path", lwjglReport.get(prefix + ".path"));
            report.put(prefix + ".size", lwjglReport.get(prefix + ".size"));
            report.put(prefix + ".sha256", lwjglReport.get(prefix + ".sha256"));
        }
        report.put("optionalRuntimeExtensionPlatforms", lwjglReport.get("optionalRuntimeExtensionPlatforms"));
        report.put("externalRuntimeBundleRequired", false);
        report.put("bundledClientOverlayRequired", false);
        report.put("verified", true);

        FoundationSupport.writeProperties(
            getReportFile().get().getAsFile().toPath(),
            report,
            "Change 007 required runtime-bearing one-JAR artifact verification"
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
        for (String key : new String[] {
            "artifactName", "artifactSize", "artifactSha256", "version", "implementationVersion",
            "productionIdentity"
        }) {
            if (source.containsKey(key)) {
                target.put(prefix + "." + key, source.get(key));
            }
        }
    }
}
