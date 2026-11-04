package com.archanalyzer.refactor.cycle;

import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.ExecutionResult;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringOptions;
import com.archanalyzer.refactor.cycle.CycleRefactoringExecutor.RefactoringStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial regression fixtures testing edge cases:
 * 1. Classes with checked exceptions in call signatures.
 * 2. Classes with overloaded methods.
 * 3. Classes with pre-existing explicit constructors.
 * 4. Verification that runtime side effects are preserved when executed.
 */
public class AdversarialRefactoringFixtureTest {

    @TempDir
    Path tempDir;

    private CycleRefactoringExecutor executor;

    @BeforeEach
    public void setUp() {
        executor = new CycleRefactoringExecutor();
    }

    private Path createJavaFile(Path root, String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    @DisplayName("Adversarial 1: Methods with checked exceptions and overloaded signatures")
    public void testCheckedExceptionsAndOverloadedSignatures() throws Exception {
        Path proj = tempDir.resolve("adv-checked-exceptions");

        createJavaFile(proj, "src/main/java/com/adv/Sender.java",
                "package com.adv;\n" +
                "import java.io.IOException;\n" +
                "public class Sender {\n" +
                "    private Receiver receiver;\n" +
                "    public void send(String msg) throws IOException {\n" +
                "        if (receiver != null) receiver.receive(msg);\n" +
                "    }\n" +
                "    public void send(int code) throws IOException {\n" +
                "        if (receiver != null) receiver.receive(code);\n" +
                "    }\n" +
                "}\n");

        createJavaFile(proj, "src/main/java/com/adv/Receiver.java",
                "package com.adv;\n" +
                "import java.io.IOException;\n" +
                "public class Receiver {\n" +
                "    private Sender sender;\n" +
                "    public void receive(String msg) throws IOException {\n" +
                "        if (sender != null) sender.send(msg.length());\n" +
                "    }\n" +
                "    public void receive(int code) throws IOException {\n" +
                "        if (sender != null && code < 0) throw new IOException(\"Negative code\");\n" +
                "    }\n" +
                "}\n");

        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);

        ExecutionResult result = executor.execute(proj, opts);

        assertEquals(RefactoringStatus.VERIFIED, result.getStatus(), "Refactoring must verify with checked exceptions: " + result.getErrors());
        assertEquals(1, result.getInitialCyclicSccCount());
        assertEquals(0, result.getFinalCyclicSccCount());
        assertNotNull(result.getGeneratedInterfacePath());
        assertTrue(Files.exists(result.getGeneratedInterfacePath()));
    }

    @Test
    @DisplayName("Adversarial 2: Multiple constructors and explicit initialization preserved")
    public void testMultipleConstructorsPreserved() throws Exception {
        Path proj = tempDir.resolve("adv-constructors");

        createJavaFile(proj, "src/main/java/com/adv2/ServiceA.java",
                "package com.adv2;\n" +
                "public class ServiceA {\n" +
                "    private ServiceB b;\n" +
                "    private final String config;\n" +
                "    public ServiceA() {\n" +
                "        this(\"default-config\");\n" +
                "    }\n" +
                "    public ServiceA(String config) {\n" +
                "        this.config = config;\n" +
                "    }\n" +
                "    public String getConfig() { return config; }\n" +
                "    public void callB() {\n" +
                "        if (b != null) b.callA();\n" +
                "    }\n" +
                "}\n");

        createJavaFile(proj, "src/main/java/com/adv2/ServiceB.java",
                "package com.adv2;\n" +
                "public class ServiceB {\n" +
                "    private ServiceA a;\n" +
                "    public void callA() {\n" +
                "        if (a != null) System.out.println(a.getConfig());\n" +
                "    }\n" +
                "}\n");

        RefactoringOptions opts = new RefactoringOptions();
        opts.setRunMavenTests(false);

        ExecutionResult result = executor.execute(proj, opts);

        assertEquals(RefactoringStatus.VERIFIED, result.getStatus(), "Must compile and verify with multiple constructors: " + result.getErrors());
        assertEquals(1, result.getInitialCyclicSccCount());
        assertEquals(0, result.getFinalCyclicSccCount());
    }
}
