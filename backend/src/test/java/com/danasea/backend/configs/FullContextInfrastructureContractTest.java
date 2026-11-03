package com.danasea.backend.configs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.MergedAnnotations;

class FullContextInfrastructureContractTest {

    @Test
    void allConcreteSpringBootTestsInheritCommonInfrastructureBase() throws Exception {
        Path testRoot = findTestJavaRoot();
        List<Class<?>> concreteSpringBootTests = new ArrayList<>();
        List<String> nonInheritingClasses = new ArrayList<>();

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try (Stream<Path> stream = Files.walk(testRoot)) {
            List<Path> javaFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().endsWith("Test.java.bak"))
                    .toList();

            for (Path javaFile : javaFiles) {
                Path relative = testRoot.relativize(javaFile);
                String className = relative.toString()
                        .replace(File.separatorChar, '.')
                        .replaceAll("\\.java$", "");

                Class<?> clazz;
                try {
                    clazz = Class.forName(className, false, classLoader);
                } catch (Throwable t) {
                    continue;
                }

                if (Modifier.isAbstract(clazz.getModifiers()) || clazz.isInterface()) {
                    continue;
                }

                boolean isSpringBootTest = MergedAnnotations.from(
                        clazz, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
                        .isPresent(SpringBootTest.class);

                if (isSpringBootTest) {
                    concreteSpringBootTests.add(clazz);
                    if (!AbstractExternalInfrastructureIntegrationTest.class.isAssignableFrom(clazz)) {
                        nonInheritingClasses.add(clazz.getName());
                    }
                }
            }
        }

        assertThat(concreteSpringBootTests)
                .as("Should have found concrete @SpringBootTest classes across backend tests")
                .isNotEmpty();

        assertThat(nonInheritingClasses)
                .as("All concrete @SpringBootTest classes must inherit AbstractExternalInfrastructureIntegrationTest to guarantee real infrastructure ownership. Violations: %s", nonInheritingClasses)
                .isEmpty();
    }

    private Path findTestJavaRoot() {
        Path direct = Path.of("src", "test", "java");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        Path backendDirect = Path.of("backend", "src", "test", "java");
        if (Files.isDirectory(backendDirect)) {
            return backendDirect;
        }
        throw new IllegalStateException("Cannot locate test root src/test/java or backend/src/test/java");
    }
}
