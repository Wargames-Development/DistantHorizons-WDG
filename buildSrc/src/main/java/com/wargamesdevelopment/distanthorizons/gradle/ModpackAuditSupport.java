package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ModpackAuditSupport {

    private static final Pattern MOD_ID = Pattern.compile("\\\"modid\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Set<String> CLIENT_STACK_IDS = Set.of("distanthorizons", "lwjgl3ify", "angelica");
    private static final String DISTANT_HORIZONS_FILENAME = "distanthorizons-3.0.4-b-wdg-rc.1.jar";
    private static final String LWJGL3IFY_FILENAME = "lwjgl3ify-3.0.28-master.5+d7e60f5a0d.jar";

    private ModpackAuditSupport() {}

    public record JarRecord(String location, String filename, String sha256, Set<String> modIds) {}

    public record AuditResult(List<JarRecord> jars, List<String> errors, List<String> warnings) {
        public boolean passed() {
            return errors.isEmpty();
        }
    }

    public static AuditResult audit(File input) throws IOException {
        if (input == null || !input.exists()) {
            throw new IllegalArgumentException("Modpack audit input does not exist: " + input);
        }
        List<JarRecord> jars = new ArrayList<>();
        if (input.isDirectory()) {
            try (var paths = Files.walk(input.toPath())) {
                for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                    if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                        byte[] bytes = Files.readAllBytes(path);
                        jars.add(inspect(input.toPath().relativize(path).toString().replace(File.separatorChar, '/'), bytes));
                    }
                }
            }
        } else if (isZip(input.getName())) {
            try (ZipFile zip = new ZipFile(input)) {
                List<? extends ZipEntry> entries = zip.stream()
                    .filter(entry -> !entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .toList();
                for (ZipEntry entry : entries) {
                    try (InputStream stream = zip.getInputStream(entry)) {
                        jars.add(inspect(entry.getName(), stream.readAllBytes()));
                    }
                }
            }
        } else {
            List<String> lines = Files.readAllLines(input.toPath(), StandardCharsets.UTF_8);
            int index = 0;
            for (String line : lines) {
                String value = line.trim();
                if (!value.isEmpty() && !value.startsWith("#")) {
                    jars.add(new JarRecord("inventory:" + (++index), new File(value).getName(), "unavailable", Set.of()));
                }
            }
        }
        return analyze(jars);
    }

    static AuditResult analyze(List<JarRecord> jars) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, List<String>> modOwners = new TreeMap<>();
        Map<String, List<String>> hashOwners = new TreeMap<>();
        int mixinProviders = 0;
        int lwjglBootstraps = 0;

        for (JarRecord jar : jars) {
            String lower = jar.filename().toLowerCase(Locale.ROOT);
            for (String forbidden : List.of("optifine", "fastcraft", "betterfps")) {
                if (lower.contains(forbidden)) {
                    errors.add("Known conflict " + forbidden + ": " + jar.location());
                }
            }
            if (lower.contains("-sources") || lower.contains("-api") || lower.contains("-dev")) {
                errors.add("Development/source/API artifact in modpack: " + jar.location());
            }
            if ((lower.contains("distanthorizons") || jar.modIds().contains("distanthorizons"))
                && !lower.equals(DISTANT_HORIZONS_FILENAME)) {
                errors.add("Another or old Distant Horizons artifact conflicts with the RC: " + jar.location());
            }
            if ((lower.contains("lwjgl3ify") || jar.modIds().contains("lwjgl3ify"))
                && !lower.equals(LWJGL3IFY_FILENAME)) {
                errors.add("Another or old lwjgl3ify artifact conflicts with Change 005: " + jar.location());
            }
            if ((lower.contains("gtnhlib") || jar.modIds().contains("gtnhlib"))
                && !lower.equals(RuntimeArtifactVerifier.GTNHLIB_FILENAME)) {
                errors.add("Another GTNHLib version conflicts with 0.11.31: " + jar.location());
            }
            if ((lower.contains("unimixins") || jar.modIds().contains("unimixins"))
                && !lower.equals(RuntimeArtifactVerifier.UNIMIXINS_FILENAME)) {
                errors.add("Another UniMixins version conflicts with 0.1.23: " + jar.location());
            }
            if ((lower.contains("angelica") || jar.modIds().contains("angelica"))
                && !lower.equals(RuntimeArtifactVerifier.ANGELICA_FILENAME)) {
                errors.add("Another Angelica version conflicts with 2.1.54: " + jar.location());
            }
            if (lower.contains("gtnhmixins") && !lower.contains("unimixins")) {
                errors.add("Standalone GTNHMixins is forbidden; UniMixins already supplies it: " + jar.location());
            }
            if (lower.contains("mcpatcherforge") && !lower.contains("angelica")) {
                errors.add("Standalone MCPatcherForge conflicts with Angelica: " + jar.location());
            }
            if (lower.contains("notfine") && !lower.contains("angelica")) {
                errors.add("Standalone NotFine conflicts with Angelica: " + jar.location());
            }
            if (lower.contains("mixin") || jar.modIds().stream().anyMatch(id -> id.contains("mixin"))) {
                mixinProviders++;
            }
            if (lower.contains("lwjgl3ify") || jar.modIds().contains("lwjgl3ify")) {
                lwjglBootstraps++;
            }
            for (String id : jar.modIds()) {
                modOwners.computeIfAbsent(id.toLowerCase(Locale.ROOT), ignored -> new ArrayList<>()).add(jar.location());
            }
            if (!"unavailable".equals(jar.sha256())) {
                hashOwners.computeIfAbsent(jar.sha256(), ignored -> new ArrayList<>()).add(jar.location());
            }
        }

        for (Map.Entry<String, List<String>> entry : modOwners.entrySet()) {
            if (entry.getValue().size() > 1) {
                errors.add("Duplicate mod ID " + entry.getKey() + ": " + entry.getValue());
            }
        }
        for (Map.Entry<String, List<String>> entry : hashOwners.entrySet()) {
            if (entry.getValue().size() > 1) {
                errors.add("Duplicate JAR bytes " + entry.getKey() + ": " + entry.getValue());
            }
        }
        if (mixinProviders > 1) {
            warnings.add("Multiple Mixin-capable composite providers were detected; review versions and bootstrap order: " + mixinProviders);
        }
        if (lwjglBootstraps > 1) {
            errors.add("Duplicate LWJGL bootstrap mods detected: " + lwjglBootstraps);
        }
        for (String required : List.of("distanthorizons", "lwjgl3ify", "gtnhlib", "unimixins")) {
            if (!modOwners.containsKey(required)) {
                warnings.add("Expected client-stack mod ID not found structurally: " + required);
            }
        }
        return new AuditResult(List.copyOf(jars), List.copyOf(errors), List.copyOf(warnings));
    }

    public static String json(AuditResult result) {
        StringBuilder out = new StringBuilder("{\n  \"schemaVersion\": 1,\n");
        out.append("  \"passed\": ").append(result.passed()).append(",\n");
        appendArray(out, "errors", result.errors(), true);
        appendArray(out, "warnings", result.warnings(), true);
        out.append("  \"jars\": [\n");
        for (int i = 0; i < result.jars().size(); i++) {
            JarRecord jar = result.jars().get(i);
            out.append("    {\"location\": \"").append(json(jar.location())).append("\", \"filename\": \"")
                .append(json(jar.filename())).append("\", \"sha256\": \"").append(jar.sha256())
                .append("\", \"modIds\": [");
            int j = 0;
            for (String id : jar.modIds()) {
                if (j++ > 0) out.append(", ");
                out.append("\"").append(json(id)).append("\"");
            }
            out.append("]}").append(i + 1 < result.jars().size() ? "," : "").append("\n");
        }
        return out.append("  ]\n}\n").toString();
    }

    public static String text(AuditResult result) {
        StringBuilder out = new StringBuilder();
        out.append("Wargames modpack compatibility audit\n")
            .append("Result: ").append(result.passed() ? "PASS" : "FAIL").append("\n")
            .append("JARs inspected: ").append(result.jars().size()).append("\n\n");
        for (String error : result.errors()) out.append("ERROR: ").append(error).append('\n');
        for (String warning : result.warnings()) out.append("WARNING: ").append(warning).append('\n');
        return out.toString();
    }

    public static Set<String> clientStackIds() {
        return CLIENT_STACK_IDS;
    }

    public static List<String> clientOnlyServerArtifacts(File input) throws IOException {
        return clientOnlyServerArtifacts(allArtifactLocations(input));
    }

    static List<String> clientOnlyServerArtifacts(List<String> locations) {
        List<String> conflicts = new ArrayList<>();
        for (String location : locations) {
            String lower = location.replace('\\', '/').toLowerCase(Locale.ROOT);
            String filename = new File(lower).getName();
            if (filename.contains("distanthorizons") || filename.contains("lwjgl3ify")
                || filename.contains("angelica") || filename.equals("lwjgl3ify-wdg-java21-runtimes.zip")) {
                conflicts.add(location);
            }
        }
        conflicts.sort(String::compareTo);
        return List.copyOf(conflicts);
    }

    private static List<String> allArtifactLocations(File input) throws IOException {
        if (input == null || !input.exists()) {
            throw new IllegalArgumentException("Server audit input does not exist: " + input);
        }
        List<String> locations = new ArrayList<>();
        if (input.isDirectory()) {
            try (var paths = Files.walk(input.toPath())) {
                for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                    locations.add(input.toPath().relativize(path).toString().replace(File.separatorChar, '/'));
                }
            }
        } else if (isZip(input.getName())) {
            try (ZipFile zip = new ZipFile(input)) {
                zip.stream().filter(entry -> !entry.isDirectory()).map(ZipEntry::getName).sorted().forEach(locations::add);
            }
        } else {
            for (String line : Files.readAllLines(input.toPath(), StandardCharsets.UTF_8)) {
                String value = line.trim();
                if (!value.isEmpty() && !value.startsWith("#")) locations.add(value);
            }
        }
        return locations;
    }

    private static JarRecord inspect(String location, byte[] bytes) throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        try (JarInputStream jar = new JarInputStream(new ByteArrayInputStream(bytes))) {
            JarEntry entry;
            while ((entry = jar.getNextJarEntry()) != null) {
                if (entry.getName().equals("mcmod.info")) {
                    String text = new String(jar.readAllBytes(), StandardCharsets.UTF_8);
                    Matcher matcher = MOD_ID.matcher(text);
                    while (matcher.find()) ids.add(matcher.group(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        return new JarRecord(location, new File(location).getName(), sha256(bytes), Set.copyOf(ids));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isZip(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".jar");
    }

    private static void appendArray(StringBuilder out, String name, List<String> values, boolean comma) {
        out.append("  \"").append(name).append("\": [");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(", ");
            out.append("\"").append(json(values.get(i))).append("\"");
        }
        out.append("]").append(comma ? "," : "").append("\n");
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
