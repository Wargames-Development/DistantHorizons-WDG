package com.wargamesdevelopment.distanthorizons.gradle;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class FoundationSupport {

    public static final int JAVA_21_CLASS_MAJOR = 65;
    public static final String MOD_ID = "distanthorizons";
    public static final String ROOT_PACKAGE_PATH = "com/seibel/distanthorizons/";
    public static final String REFMAP = "mixins.distanthorizons.refmap.json";
    public static final String NORMAL_MIXIN_CONFIG = "mixins.distanthorizons.json";
    public static final String EARLY_MIXIN_CONFIG = "mixins.distanthorizons.early.json";
    public static final String ACCESS_TRANSFORMER = "META-INF/distanthorizons_at.cfg";
    public static final String EARLY_LOADER = "com/seibel/distanthorizons/DistantHorizonsTweaker.class";

    private static final Pattern VERSION_PATTERN = Pattern.compile(
        "[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z][0-9A-Za-z.-]*)?"
    );
    private static final List<String> TEMPLATE_MARKERS = List.of(
        "ExampleMod",
        "SinTh0r4s",
        "SinTho0r4s",
        "com.myname.mymodid",
        "github.com/SinTh0r4s/MyMod",
        "github.com/SinTho0r4s/MyMod"
    );

    private FoundationSupport() {}

    public static String validateVersion(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!VERSION_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid unified mod version: '" + value + "'");
        }
        return normalized;
    }

    public static void requireVersionMatch(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                label + " version mismatch: expected=" + expected + ", actual=" + actual
            );
        }
    }

    public static void validateLwjglDevelopmentCandidateName(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith("-dev.jar")
            || lower.endsWith("-dev-preshadow.jar")
            || lower.endsWith("-sources.jar")
            || lower.endsWith("-api.jar")) {
            throw new IllegalStateException(
                "Local lwjgl3ify development input must be the exact shadow development JAR: " + name
            );
        }
    }

    public static Path resolveLocalPath(String configuredPath) {
        if (configuredPath == null || configuredPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Local artifact path is empty");
        }
        return Path.of(configuredPath.trim()).toAbsolutePath().normalize();
    }

    public static File requireReadableRegularFile(File file, String label) {
        if (file == null || !file.isFile() || !file.canRead()) {
            throw new IllegalStateException(label + " is not a readable regular file: " + file);
        }
        return file;
    }

    public static String readUtf8(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    public static void rejectTemplateMarkers(String text, String label) {
        for (String marker : TEMPLATE_MARKERS) {
            if (text.contains(marker)) {
                throw new IllegalStateException(label + " still contains template marker: " + marker);
            }
        }
    }

    public static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static String sha256(byte[] bytes) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return builder.toString();
    }

    public static int classMajor(byte[] bytes, String label) {
        if (bytes.length < 8
            || bytes[0] != (byte) 0xca
            || bytes[1] != (byte) 0xfe
            || bytes[2] != (byte) 0xba
            || bytes[3] != (byte) 0xbe) {
            throw new IllegalStateException(label + " is not a valid Java class file");
        }
        return ((bytes[6] & 0xff) << 8) | (bytes[7] & 0xff);
    }

    public static String extractJsonString(String json, String field) {
        Pattern pattern = Pattern.compile(
            "\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\""
        );
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("JSON field is missing: " + field);
        }
        return matcher.group(1);
    }

    public static Set<String> extractJsonStringArray(String json, String field) {
        Pattern pattern = Pattern.compile(
            "\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*\\[(.*?)]",
            Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("JSON array is missing: " + field);
        }
        Set<String> values = new LinkedHashSet<>();
        Matcher valueMatcher = Pattern.compile("\\\"([^\\\"]+)\\\"").matcher(matcher.group(1));
        while (valueMatcher.find()) {
            values.add(valueMatcher.group(1));
        }
        return values;
    }

    public static Properties loadProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }

    public static void writeProperties(Path path, Map<String, ?> values, String comment) throws IOException {
        Files.createDirectories(path.getParent());
        Properties properties = new Properties();
        for (Map.Entry<String, ?> entry : new TreeMap<>(values).entrySet()) {
            properties.setProperty(entry.getKey(), String.valueOf(entry.getValue()));
        }
        try (OutputStream output = Files.newOutputStream(path)) {
            properties.store(output, comment);
        }
    }

    public static void validateArchiveNames(List<String> names) {
        Map<String, Integer> exactCounts = new LinkedHashMap<>();
        Map<String, Set<String>> caseFolded = new LinkedHashMap<>();
        for (String name : names) {
            exactCounts.merge(name, 1, Integer::sum);
            caseFolded.computeIfAbsent(name.toLowerCase(Locale.ROOT), ignored -> new LinkedHashSet<>()).add(name);
        }
        List<String> exactDuplicates = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : exactCounts.entrySet()) {
            if (entry.getValue() > 1) {
                exactDuplicates.add(entry.getKey());
            }
        }
        if (!exactDuplicates.isEmpty()) {
            throw new IllegalStateException("Archive contains duplicate members: " + exactDuplicates);
        }
        List<Set<String>> collisions = new ArrayList<>();
        for (Set<String> values : caseFolded.values()) {
            if (values.size() > 1) {
                collisions.add(values);
            }
        }
        if (!collisions.isEmpty()) {
            throw new IllegalStateException("Archive contains case-folding collisions: " + collisions);
        }
    }

    public static final class ArchiveInventory implements AutoCloseable {
        private final File file;
        private final ZipFile zip;
        private final List<String> names;

        private ArchiveInventory(File file, ZipFile zip, List<String> names) {
            this.file = file;
            this.zip = zip;
            this.names = Collections.unmodifiableList(names);
        }

        public static ArchiveInventory open(File file) throws IOException {
            requireReadableRegularFile(file, "JAR");
            ZipFile zip = new ZipFile(file);
            List<String> names = new ArrayList<>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
            try {
                validateArchiveNames(names);
                return new ArchiveInventory(file, zip, names);
            } catch (RuntimeException failure) {
                zip.close();
                throw failure;
            }
        }

        public File file() {
            return file;
        }

        public List<String> names() {
            return names;
        }

        public boolean contains(String name) {
            return zip.getEntry(name) != null;
        }

        public void require(String name) {
            if (!contains(name)) {
                throw new IllegalStateException("Artifact is missing required member: " + name);
            }
        }

        public void requirePrefix(String prefix) {
            if (names.stream().noneMatch(name -> name.startsWith(prefix))) {
                throw new IllegalStateException("Artifact is missing required package/resource prefix: " + prefix);
            }
        }

        public List<String> namesEndingWith(String suffix) {
            List<String> matches = new ArrayList<>();
            for (String name : names) {
                if (name.endsWith(suffix)) {
                    matches.add(name);
                }
            }
            return matches;
        }

        public byte[] bytes(String name) throws IOException {
            ZipEntry entry = zip.getEntry(name);
            if (entry == null || entry.isDirectory()) {
                throw new IllegalStateException("Artifact member is missing or not a file: " + name);
            }
            try (InputStream input = zip.getInputStream(entry)) {
                return input.readAllBytes();
            }
        }

        public String text(String name) throws IOException {
            return new String(bytes(name), StandardCharsets.UTF_8);
        }

        public Manifest manifest() throws IOException {
            require("META-INF/MANIFEST.MF");
            try (InputStream input = new ByteArrayInputStream(bytes("META-INF/MANIFEST.MF"))) {
                return new Manifest(input);
            }
        }

        public String manifestValue(String name) throws IOException {
            Attributes attributes = manifest().getMainAttributes();
            return attributes.getValue(name);
        }

        @Override
        public void close() throws IOException {
            zip.close();
        }
    }
}
