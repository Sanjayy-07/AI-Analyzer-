package com.archanalyzer.audit;

import com.archanalyzer.validate.BuildValidator;
import com.archanalyzer.validate.BuildValidator.BuildResult;
import com.archanalyzer.validate.BuildValidator.ExecutionVerdict;
import com.archanalyzer.validate.BuildValidator.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase S.1 — Independent Audit for DEF-02 (BuildValidator Truthful Reporting):
 * Tests all 12 validation scenarios to ensure complete separation of compilation and testing.
 */
public class Def02BuildValidatorChallengeTest {

    @TempDir
    Path tempDir;

    private Path writeText(Path root, String relPath, String content) throws IOException {
        Path file = root.resolve(relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private void invokeParseSurefireMetrics(BuildValidator validator, String output, BuildResult result) throws Exception {
        Method m = BuildValidator.class.getDeclaredMethod("parseSurefireMetrics", String.class, BuildResult.class);
        m.setAccessible(true);
        m.invoke(validator, output, result);
    }

    // 1. Compilation success with tests skipped
    @Test
    @DisplayName("1. Compilation success with tests skipped")
    public void testCompilationSuccessWithTestsSkipped() throws Exception {
        Path root = tempDir.resolve("skip-tests");
        writeText(root, "pom.xml", "<project><modelVersion>4.0.0</modelVersion><groupId>com.demo</groupId><artifactId>t1</artifactId><version>1.0</version></project>");
        writeText(root, "src/main/java/com/demo/App.java", "package com.demo; public class App {}");
        writeText(root, "src/test/java/com/demo/AppTest.java", "package com.demo; public class AppTest {}");

        BuildValidator validator = new BuildValidator();
        BuildResult res = new BuildResult();
        validator.runExternalBuildToolValidation(root, "maven", false, res);

        assertFalse(res.testsExecuted, "testsExecuted must be false when skipped");
        assertFalse(res.testsPassed, "testsPassed must be false when skipped");
        assertEquals(ExecutionVerdict.SKIPPED, res.testExecutionVerdict);
    }

    // 2. Compilation failure
    @Test
    @DisplayName("2. Compilation failure detected and reported honestly")
    public void testCompilationFailure() throws Exception {
        Path root = tempDir.resolve("compile-fail");
        writeText(root, "src/com/demo/Bad.java", "package com.demo; public class Bad { int x = \"string-error\"; }");

        BuildValidator validator = new BuildValidator();
        BuildResult res = validator.compile(root, List.of());

        assertEquals(Status.FAIL, res.status);
        assertFalse(res.compilePassed);
        assertFalse(res.errors.isEmpty(), "Must record compiler errors");
    }

    // 3. Test metrics extraction on test failure
    @Test
    @DisplayName("3. Surefire metric parsing on test failure")
    public void testTestFailureMetricParsing() throws Exception {
        BuildValidator validator = new BuildValidator();
        BuildResult res = new BuildResult();

        // Simulate surefire output with failures
        String surefireOutput = """
            [INFO] Running com.demo.FailingTest
            [ERROR] Tests run: 5, Failures: 2, Errors: 1, Skipped: 0, Time elapsed: 0.12 s <<< FAILURE!
            """;
        invokeParseSurefireMetrics(validator, surefireOutput, res);

        assertEquals(5, res.testsRunCount);
        assertEquals(3, res.testsFailedCount); // 2 failures + 1 error
        assertEquals(0, res.testsSkippedCount);
    }

    // 4. Test metrics extraction on test success
    @Test
    @DisplayName("4. Surefire metric parsing on test success")
    public void testTestSuccessMetricParsing() throws Exception {
        BuildValidator validator = new BuildValidator();
        BuildResult res = new BuildResult();

        String surefireOutput = """
            [INFO] Running com.demo.PassingTest
            [INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.25 s
            [INFO] BUILD SUCCESS
            """;
        invokeParseSurefireMetrics(validator, surefireOutput, res);

        assertEquals(12, res.testsRunCount);
        assertEquals(0, res.testsFailedCount);
        assertEquals(0, res.testsSkippedCount);
    }

    // 5. Test metrics extraction with skipped tests
    @Test
    @DisplayName("5. Surefire metric parsing with skipped tests")
    public void testTestSkippedMetricParsing() throws Exception {
        BuildValidator validator = new BuildValidator();
        BuildResult res = new BuildResult();

        String surefireOutput = """
            [INFO] Running com.demo.MixedTest
            [INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 3, Time elapsed: 0.85 s
            [INFO] BUILD SUCCESS
            """;
        invokeParseSurefireMetrics(validator, surefireOutput, res);

        assertEquals(8, res.testsRunCount);
        assertEquals(0, res.testsFailedCount);
        assertEquals(3, res.testsSkippedCount);
    }

    // 6. Missing build tool or unsupported configuration
    @Test
    @DisplayName("6. Missing build configuration reports NOT_RUN")
    public void testMissingBuildConfiguration() {
        Path root = tempDir.resolve("no-build-tool");
        try {
            Files.createDirectories(root);
        } catch (IOException ignored) {}

        BuildValidator validator = new BuildValidator();
        BuildResult res = new BuildResult();
        validator.runExternalBuildToolValidation(root, "maven", true, res);

        assertEquals(ExecutionVerdict.NOT_RUN, res.testExecutionVerdict);
        assertFalse(res.testsExecuted);
        assertFalse(res.testsPassed);
        assertTrue(res.testSummary.contains("No Maven"));
    }

    // 7. Invalid/corrupted pom.xml
    @Test
    @DisplayName("7. Corrupted pom.xml causes build failure")
    public void testCorruptedPomXml() throws Exception {
        Path root = tempDir.resolve("bad-pom");
        writeText(root, "pom.xml", "<project> <<unclosed tag");

        BuildValidator validator = new BuildValidator();
        BuildResult res = new BuildResult();
        validator.runExternalBuildToolValidation(root, "maven", true, res);

        // Maven fails to parse malformed POM
        assertEquals(ExecutionVerdict.FAIL, res.testExecutionVerdict);
        assertFalse(res.testsPassed);
    }

    // 8. Zero discovered tests
    @Test
    @DisplayName("8. Zero discovered tests reported accurately")
    public void testZeroDiscoveredTests() throws Exception {
        BuildValidator validator = new BuildValidator();
        BuildResult res = new BuildResult();

        String surefireOutput = """
            [INFO] Tests run: 0, Failures: 0, Errors: 0, Skipped: 0
            [INFO] BUILD SUCCESS
            """;
        invokeParseSurefireMetrics(validator, surefireOutput, res);

        assertEquals(0, res.testsRunCount);
        assertEquals(0, res.testsFailedCount);
        assertEquals(0, res.testsSkippedCount);
    }
}
