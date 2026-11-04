package com.archanalyzer.validate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase S: Regression test suite for DEF-02 (BuildValidator Status Semantics).
 * Verifies that compilation, test compilation, and test execution are clearly distinguished,
 * and that -DskipTests=true is NEVER reported as tests executed or passed.
 */
public class BuildValidatorStatusSemanticsTest {

    @Test
    public void testStatusSeparationAndHonestReporting() {
        BuildValidator.BuildResult result = new BuildValidator.BuildResult();

        // Initial default state
        assertEquals(BuildValidator.Status.SKIPPED, result.status);
        assertFalse(result.compilePassed);
        assertFalse(result.testCompilePassed);
        assertFalse(result.testsExecuted);
        assertFalse(result.testsPassed);
        assertEquals(BuildValidator.ExecutionVerdict.NOT_RUN, result.testExecutionVerdict);
    }

    @Test
    public void testSkipTestsHonestReporting(@TempDir Path tempDir) throws IOException {
        // Create minimal project with pom.xml and test
        Path pom = tempDir.resolve("pom.xml");
        Path mainSrc = tempDir.resolve("src/main/java/com/demo/App.java");
        Path testSrc = tempDir.resolve("src/test/java/com/demo/AppTest.java");
        Files.createDirectories(mainSrc.getParent());
        Files.createDirectories(testSrc.getParent());

        Files.writeString(pom, "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><modelVersion>4.0.0</modelVersion>" +
                "<groupId>com.demo</groupId><artifactId>test-skip</artifactId><version>1.0</version></project>");
        Files.writeString(mainSrc, "package com.demo;\npublic class App { public static int add(int a, int b) { return a + b; } }");
        Files.writeString(testSrc, "package com.demo;\npublic class AppTest {}");

        BuildValidator validator = new BuildValidator();
        BuildValidator.BuildResult result = new BuildValidator.BuildResult();

        // 1. Run in-memory compilation
        validator.compile(tempDir, List.of(), null);

        // 2. Run external validation with executeTests = false (-DskipTests=true)
        validator.runExternalBuildToolValidation(tempDir, "maven", false, result);

        // DEF-02 Invariant: When tests are skipped, testsExecuted and testsPassed MUST be false!
        assertTrue(result.testCompilePassed, "Test compilation should pass");
        assertFalse(result.testsExecuted, "testsExecuted must be false when -DskipTests=true");
        assertFalse(result.testsPassed, "testsPassed must be false when -DskipTests=true");
        assertEquals(BuildValidator.ExecutionVerdict.SKIPPED, result.testExecutionVerdict,
                "Verdict must be SKIPPED, not PASS");
        assertTrue(result.testSummary.contains("-DskipTests=true"),
                "Summary must explicitly state tests were skipped: " + result.testSummary);
    }

    @Test
    public void testVerdictDistinctions() {
        // Verify all 6 verdicts exist and are distinct
        assertEquals(6, BuildValidator.ExecutionVerdict.values().length);
        assertNotNull(BuildValidator.ExecutionVerdict.valueOf("PASS"));
        assertNotNull(BuildValidator.ExecutionVerdict.valueOf("FAIL"));
        assertNotNull(BuildValidator.ExecutionVerdict.valueOf("NOT_RUN"));
        assertNotNull(BuildValidator.ExecutionVerdict.valueOf("SKIPPED"));
        assertNotNull(BuildValidator.ExecutionVerdict.valueOf("ERROR"));
        assertNotNull(BuildValidator.ExecutionVerdict.valueOf("TIMEOUT"));
    }
}
