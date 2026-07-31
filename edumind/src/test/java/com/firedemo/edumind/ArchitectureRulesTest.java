package com.firedemo.edumind;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureRulesTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/firedemo/edumind");
    private static final Set<String> ALLOWED_MODULES = Set.of(
            "assistant", "auth", "classroom", "homework", "integration",
            "knowledge", "live", "platform", "shared", "teaching");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([a-zA-Z0-9_.]+);");
    private static final Pattern MAPPER_IMPORT = Pattern.compile(
            "(?m)^import\\s+com\\.firedemo\\.edumind\\..*Mapper;");
    private static final Pattern INTEGRATION_IMPORT = Pattern.compile(
            "(?m)^import\\s+com\\.firedemo\\.edumind\\.integration\\.");
    private static final Pattern BUSINESS_IMPORT = Pattern.compile(
            "(?m)^import\\s+com\\.firedemo\\.edumind\\."
                    + "(assistant|auth|classroom|homework|integration|knowledge|live|teaching)\\.");

    @Test
    void productionCodeUsesOnlyBusinessFirstTopLevelModules() throws IOException {
        try (Stream<Path> children = Files.list(SOURCE_ROOT)) {
            List<String> modules = children
                    .filter(Files::isDirectory)
                    .filter(ArchitectureRulesTest::containsJavaSource)
                    .map(path -> path.getFileName().toString())
                    .toList();
            assertThat(modules).allMatch(ALLOWED_MODULES::contains);
        }
    }

    @Test
    void packageNamesAreLowercase() throws IOException {
        for (Path source : javaSources()) {
            Matcher matcher = PACKAGE.matcher(Files.readString(source));
            assertThat(matcher.find()).as("package declaration in %s", source).isTrue();
            assertThat(matcher.group(1)).as("package name in %s", source)
                    .isEqualTo(matcher.group(1).toLowerCase());
        }
    }

    @Test
    void restControllersDoNotAccessMappersDirectly() throws IOException {
        for (Path source : javaSources()) {
            String content = Files.readString(source);
            if (content.contains("@RestController")) {
                assertThat(MAPPER_IMPORT.matcher(content).find())
                        .as("direct mapper dependency in %s", source)
                        .isFalse();
            }
        }
    }

    @Test
    void assistantCoreDoesNotDependOnExternalAdapters() throws IOException {
        for (Path source : javaSourcesUnder("assistant")) {
            assertThat(INTEGRATION_IMPORT.matcher(Files.readString(source)).find())
                    .as("assistant-to-integration dependency in %s", source)
                    .isFalse();
        }
    }

    @Test
    void sharedKernelDoesNotDependOnBusinessModules() throws IOException {
        for (Path source : javaSourcesUnder("shared")) {
            assertThat(BUSINESS_IMPORT.matcher(Files.readString(source)).find())
                    .as("shared-to-business dependency in %s", source)
                    .isFalse();
        }
    }

    @Test
    void concreteApplicationServicesDoNotUseImplSuffix() throws IOException {
        assertThat(javaSources())
                .noneMatch(path -> path.getFileName().toString().endsWith("ServiceImpl.java"));
    }

    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static List<Path> javaSourcesUnder(String module) throws IOException {
        try (Stream<Path> paths = Files.walk(SOURCE_ROOT.resolve(module))) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static boolean containsJavaSource(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.anyMatch(path -> path.toString().endsWith(".java"));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to inspect " + directory, e);
        }
    }
}
