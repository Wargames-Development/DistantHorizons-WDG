package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

public final class ProvenanceSupport {

    public static final String REPOSITORY = "Wargames-Development/DistantHorizons-WDG";
    public static final String EXPECTED_BRANCH = "master";
    public static final String UPSTREAM_REPOSITORY = "DarkShadow44/DistantHorizonsStandalone";
    public static final String BASE_COMMIT = "170c809b2befbb3c54bd143ac68f8a9329e41f07";
    public static final String RELEASE_CHANNEL = "RELEASE_CANDIDATE";
    public static final String UPDATER_POLICY = "MANAGED_DISABLED";
    public static final String PACKAGE_CONTRACT = "change-007-rc-v1";
    public static final String VALIDATION_SOURCE = "UNCOMMITTED_VALIDATION";
    public static final String FINAL_SOURCE = "CLEAN_GIT_CHECKOUT";

    private ProvenanceSupport() {}

    public record Resolved(
        String commit,
        String shortCommit,
        String branch,
        String treeState,
        String sourceTreeDigest,
        String buildSource
    ) {}

    public static Resolved resolve(
        Path repository,
        List<File> sourceFiles,
        String mode,
        String explicitCommit,
        String explicitBranch,
        String explicitTreeState,
        String explicitDigest,
        String expectedFinalCommit
    ) throws IOException {
        String normalizedMode = required(mode, "provenance mode").toUpperCase(Locale.ROOT);
        if (!normalizedMode.equals("VALIDATION") && !normalizedMode.equals("FINAL")) {
            throw new IllegalArgumentException("wdgProvenanceMode must be VALIDATION or FINAL");
        }

        boolean hasGit = Files.isDirectory(repository.resolve(".git"));
        String commit;
        if (present(explicitCommit)) {
            commit = explicitCommit.trim();
        } else {
            requireGitMetadata(hasGit, "wdgSourceCommit");
            commit = git(repository, "rev-parse", "HEAD");
        }
        validateCommit(commit, "source commit");

        String branch;
        if (present(explicitBranch)) {
            branch = explicitBranch.trim();
        } else {
            requireGitMetadata(hasGit, "wdgSourceBranch");
            branch = git(repository, "branch", "--show-current");
        }
        branch = required(branch, "source branch");
        if (!branch.equals(EXPECTED_BRANCH)) {
            throw new IllegalStateException("Change 007 provenance requires branch " + EXPECTED_BRANCH + ", found " + branch);
        }

        String treeState;
        if (present(explicitTreeState)) {
            treeState = explicitTreeState.trim().toLowerCase(Locale.ROOT);
        } else {
            requireGitMetadata(hasGit, "wdgSourceTreeState");
            treeState = git(repository, "status", "--porcelain").isBlank() ? "clean" : "modified";
        }
        if (!treeState.equals("clean") && !treeState.equals("modified")) {
            throw new IllegalArgumentException("wdgSourceTreeState must be clean or modified");
        }
        String digest = present(explicitDigest)
            ? explicitDigest.trim().toLowerCase(Locale.ROOT)
            : digest(repository, sourceFiles);
        if (!digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("source tree digest must be a lowercase SHA-256");
        }

        String buildSource;
        if (normalizedMode.equals("VALIDATION")) {
            if (!commit.equals(BASE_COMMIT)) {
                throw new IllegalStateException(
                    "Validation provenance must identify Change 007 base commit " + BASE_COMMIT + ", found " + commit
                );
            }
            if (!treeState.equals("modified")) {
                throw new IllegalStateException("Validation provenance must identify a modified working tree");
            }
            buildSource = VALIDATION_SOURCE;
        } else {
            String expected = required(expectedFinalCommit, "wdgExpectedCommit for FINAL mode");
            validateCommit(expected, "expected final commit");
            if (!commit.equals(expected)) {
                throw new IllegalStateException("Final provenance commit mismatch: " + commit + " != " + expected);
            }
            if (!treeState.equals("clean")) {
                throw new IllegalStateException("Final release assets require a clean checkout");
            }
            buildSource = FINAL_SOURCE;
        }

        return new Resolved(commit, commit.substring(0, 12), branch, treeState, digest, buildSource);
    }

    public static String buildInfoJson(
        String modId,
        String modVersion,
        String minecraftVersion,
        String forgeVersion,
        Resolved resolved
    ) {
        return "{\n"
            + "  \"schemaVersion\": 1,\n"
            + "  \"modId\": \"" + json(modId) + "\",\n"
            + "  \"modVersion\": \"" + json(modVersion) + "\",\n"
            + "  \"releaseChannel\": \"" + RELEASE_CHANNEL + "\",\n"
            + "  \"repository\": \"" + REPOSITORY + "\",\n"
            + "  \"upstreamRepository\": \"" + UPSTREAM_REPOSITORY + "\",\n"
            + "  \"branchOrChannel\": \"" + json(resolved.branch()) + "\",\n"
            + "  \"commit\": \"" + resolved.commit() + "\",\n"
            + "  \"shortCommit\": \"" + resolved.shortCommit() + "\",\n"
            + "  \"treeState\": \"" + resolved.treeState() + "\",\n"
            + "  \"sourceTreeDigest\": \"" + resolved.sourceTreeDigest() + "\",\n"
            + "  \"buildSource\": \"" + resolved.buildSource() + "\",\n"
            + "  \"minecraftVersion\": \"" + json(minecraftVersion) + "\",\n"
            + "  \"forgeVersion\": \"" + json(forgeVersion) + "\",\n"
            + "  \"javaClassFileTarget\": 65,\n"
            + "  \"packageContractVersion\": \"" + PACKAGE_CONTRACT + "\",\n"
            + "  \"updaterPolicy\": \"" + UPDATER_POLICY + "\",\n"
            + "  \"reproducibleBuild\": true\n"
            + "}\n";
    }

    public static String digest(Path repository, List<File> files) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<Path> paths = new ArrayList<>();
            for (File file : files) {
                if (file.isFile()) {
                    paths.add(file.toPath().toAbsolutePath().normalize());
                }
            }
            paths.sort(Comparator.comparing(path -> normalizedRelative(repository, path)));
            for (Path path : paths) {
                String relative = normalizedRelative(repository, path);
                digest.update(relative.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(Files.readAllBytes(path));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String normalizedRelative(Path repository, Path path) {
        Path root = repository.toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Source file is outside repository: " + path);
        }
        return root.relativize(path).toString().replace(File.separatorChar, '/');
    }

    private static String git(Path repository, String... args) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repository.toString());
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        process.getInputStream().transferTo(output);
        try {
            int rc = process.waitFor();
            String text = output.toString(StandardCharsets.UTF_8).trim();
            if (rc != 0) {
                throw new IOException("Git command failed (" + rc + "): " + String.join(" ", command) + "\n" + text);
            }
            return text;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running Git", e);
        }
    }

    private static void requireGitMetadata(boolean hasGit, String property) {
        if (!hasGit) {
            throw new IllegalStateException(
                "Git metadata is absent; provide explicit -P" + property + " provenance input"
            );
        }
    }

    private static void validateCommit(String value, String label) {
        if (!value.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException(label + " must be a full lowercase 40-character Git commit");
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static String required(String value, String label) {
        if (!present(value)) {
            throw new IllegalArgumentException(label + " is missing");
        }
        return value.trim();
    }

    private static String json(String value) {
        return required(value, "JSON string").replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
