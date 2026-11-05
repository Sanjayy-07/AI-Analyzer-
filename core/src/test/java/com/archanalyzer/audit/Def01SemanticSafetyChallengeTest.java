package com.archanalyzer.audit;

import com.archanalyzer.parser.JavaParserAdapter;
import com.archanalyzer.refactor.cycle.AstCallerRewriter;
import com.archanalyzer.refactor.cycle.AstInterfaceExtractor;
import com.archanalyzer.refactor.cycle.SourceFileEditor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase S.1 — Independent Semantic Safety Challenge for DEF-01:
 * Evaluates constructor injection, reflection default provider, runtime behavior preservation,
 * and failure modes across Fixtures A through H.
 */
public class Def01SemanticSafetyChallengeTest {

    @TempDir
    Path tempDir;

    private Path writeSource(Path root, String relPath, String content) throws IOException {
        Path file = root.resolve(relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private ClassLoader compileAndLoad(Path projectRoot, List<Path> sources) throws Exception {
        Path bin = projectRoot.resolve("bin");
        Files.createDirectories(bin);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler required");

        List<String> args = new java.util.ArrayList<>();
        args.add("-d");
        args.add(bin.toString());
        for (Path s : sources) {
            args.add(s.toString());
        }

        int exitCode = compiler.run(null, null, null, args.toArray(new String[0]));
        assertEquals(0, exitCode, "Sources must compile cleanly: " + sources);

        return new URLClassLoader(new URL[]{bin.toUri().toURL()}, this.getClass().getClassLoader());
    }

    // =========================================================================
    // Fixture A: Simple no-argument constructor
    // =========================================================================
    @Test
    @DisplayName("Fixture A: Simple no-argument constructor behaves identically")
    public void testFixtureA_SimpleNoArgConstructor() throws Exception {
        Path root = tempDir.resolve("fixture-a");
        Path prov = writeSource(root, "src/com/demo/TargetService.java",
                "package com.demo;\n" +
                "public class TargetService {\n" +
                "    public TargetService() {}\n" +
                "    public String serve() { return \"served-ok\"; }\n" +
                "}\n");
        Path call = writeSource(root, "src/com/demo/Caller.java",
                "package com.demo;\n" +
                "public class Caller {\n" +
                "    private TargetService svc = new TargetService();\n" +
                "    public String doWork() { return svc.serve(); }\n" +
                "}\n");

        // 1. Interface extraction
        AstInterfaceExtractor extractor = new AstInterfaceExtractor();
        AstInterfaceExtractor.ExtractionResult ext = extractor.extractInterface(root, prov, "TargetService");
        Path ifaceFile = ext.interfaceFile;
        Files.writeString(ifaceFile, ext.interfaceSource, StandardCharsets.UTF_8);
        SourceFileEditor.applyEditsToFile(prov, List.of(ext.classImplementsEdit));

        // 2. Caller rewrite
        AstCallerRewriter rewriter = new AstCallerRewriter();
        AstCallerRewriter.RewriteResult rw = rewriter.rewriteCaller(
                call, "TargetService", "com.demo.TargetService",
                "ITargetService", "com.demo.ITargetService", "com.demo", true);
        SourceFileEditor.applyEditsToFile(call, rw.getEdits());

        // 3. Compile and execute
        ClassLoader cl = compileAndLoad(root, List.of(ifaceFile, prov, call));
        Class<?> callerCls = cl.loadClass("com.demo.Caller");

        // Test default constructor
        Object c1 = callerCls.getDeclaredConstructor().newInstance();
        Method doWork = callerCls.getMethod("doWork");
        assertEquals("served-ok", doWork.invoke(c1));

        // Test DI constructor
        Class<?> targetCls = cl.loadClass("com.demo.TargetService");
        Class<?> ifaceCls = cl.loadClass("com.demo.ITargetService");
        Object targetObj = targetCls.getDeclaredConstructor().newInstance();
        Constructor<?> diCtor = callerCls.getDeclaredConstructor(ifaceCls);
        Object c2 = diCtor.newInstance(targetObj);
        assertEquals("served-ok", doWork.invoke(c2));

        // Test null check in DI constructor
        InvocationTargetException ite = assertThrows(InvocationTargetException.class, () -> diCtor.newInstance(new Object[]{null}));
        assertTrue(ite.getCause() instanceof IllegalArgumentException, "Must reject null injection");
    }

    // =========================================================================
    // Fixture B: Constructor with required dependency
    // =========================================================================
    @Test
    @DisplayName("Fixture B: Target class without no-arg constructor causes default provider failure")
    public void testFixtureB_TargetWithoutNoArgConstructor() throws Exception {
        Path root = tempDir.resolve("fixture-b");
        Path repo = writeSource(root, "src/com/demo/Repo.java",
                "package com.demo;\n" +
                "public class Repo { public String get() { return \"data\"; } }\n");
        Path prov = writeSource(root, "src/com/demo/TargetService.java",
                "package com.demo;\n" +
                "public class TargetService {\n" +
                "    private final Repo repo;\n" +
                "    public TargetService(Repo repo) { this.repo = repo; }\n" +
                "    public String serve() { return repo.get(); }\n" +
                "}\n");
        Path call = writeSource(root, "src/com/demo/Caller.java",
                "package com.demo;\n" +
                "public class Caller {\n" +
                "    private TargetService svc;\n" +
                "    public String doWork() { return svc != null ? svc.serve() : \"no-svc\"; }\n" +
                "}\n");

        AstInterfaceExtractor extractor = new AstInterfaceExtractor();
        AstInterfaceExtractor.ExtractionResult ext = extractor.extractInterface(root, prov, "TargetService");
        Path ifaceFile = ext.interfaceFile;
        Files.writeString(ifaceFile, ext.interfaceSource, StandardCharsets.UTF_8);
        SourceFileEditor.applyEditsToFile(prov, List.of(ext.classImplementsEdit));

        AstCallerRewriter rewriter = new AstCallerRewriter();
        AstCallerRewriter.RewriteResult rw = rewriter.rewriteCaller(
                call, "TargetService", "com.demo.TargetService",
                "ITargetService", "com.demo.ITargetService", "com.demo", true);
        SourceFileEditor.applyEditsToFile(call, rw.getEdits());

        ClassLoader cl = compileAndLoad(root, List.of(repo, ifaceFile, prov, call));
        Class<?> callerCls = cl.loadClass("com.demo.Caller");

        // DEFECT-S1-02 REMEDIATED:
        // When TargetService has NO default constructor, AstCallerRewriter omits the default
        // no-argument constructor completely rather than generating a reflective factory guaranteed to fail!
        assertThrows(NoSuchMethodException.class, () -> callerCls.getDeclaredConstructor(),
                "Default no-arg constructor must be omitted when target lacks accessible parameterless constructor");
        assertFalse(rw.isDefaultProviderGenerated(), "Default provider factory must not be generated");
        assertTrue(rw.getExplanation().contains("lacks an accessible parameterless constructor"),
                "Refactoring explanation must document why explicit DI is required");

        // But explicit DI constructor Caller(ITargetService) STILL WORKS properly!
        Class<?> ifaceCls = cl.loadClass("com.demo.ITargetService");
        Class<?> targetCls = cl.loadClass("com.demo.TargetService");
        Class<?> repoCls = cl.loadClass("com.demo.Repo");
        Object r = repoCls.getDeclaredConstructor().newInstance();
        Object target = targetCls.getDeclaredConstructor(repoCls).newInstance(r);

        Object callerWithDi = callerCls.getDeclaredConstructor(ifaceCls).newInstance(target);
        Method doWork = callerCls.getMethod("doWork");
        assertEquals("data", doWork.invoke(callerWithDi), "Explicit DI must work even when default provider lacks no-arg constructor");
    }

    // =========================================================================
    // Fixture C: Constructor side effects
    // =========================================================================
    @Test
    @DisplayName("Fixture C: Constructor side effects are executed by default provider")
    public void testFixtureC_ConstructorSideEffects() throws Exception {
        Path root = tempDir.resolve("fixture-c");
        Path prov = writeSource(root, "src/com/demo/TargetService.java",
                "package com.demo;\n" +
                "public class TargetService {\n" +
                "    public static int initCount = 0;\n" +
                "    public TargetService() { initCount++; }\n" +
                "    public void run() {}\n" +
                "}\n");
        Path call = writeSource(root, "src/com/demo/Caller.java",
                "package com.demo;\n" +
                "public class Caller {\n" +
                "    private TargetService svc = new TargetService();\n" +
                "    public void work() { svc.run(); }\n" +
                "}\n");

        AstInterfaceExtractor extractor = new AstInterfaceExtractor();
        AstInterfaceExtractor.ExtractionResult ext = extractor.extractInterface(root, prov, "TargetService");
        Path ifaceFile = ext.interfaceFile;
        Files.writeString(ifaceFile, ext.interfaceSource, StandardCharsets.UTF_8);
        SourceFileEditor.applyEditsToFile(prov, List.of(ext.classImplementsEdit));

        AstCallerRewriter rewriter = new AstCallerRewriter();
        AstCallerRewriter.RewriteResult rw = rewriter.rewriteCaller(
                call, "TargetService", "com.demo.TargetService",
                "ITargetService", "com.demo.ITargetService", "com.demo", true);
        SourceFileEditor.applyEditsToFile(call, rw.getEdits());

        ClassLoader cl = compileAndLoad(root, List.of(ifaceFile, prov, call));
        Class<?> targetCls = cl.loadClass("com.demo.TargetService");
        Class<?> callerCls = cl.loadClass("com.demo.Caller");

        assertEquals(0, targetCls.getField("initCount").getInt(null));

        // Create caller via default constructor:
        // DEFECT-S1-01 REMEDIATED:
        // In the hardened implementation, the inline field initializer is stripped from the field declaration.
        // The default constructor delegates to this(createDefaultTargetService()), instantiating the provider
        // EXACTLY ONCE per caller instance. No double instantiation occurs!
        callerCls.getDeclaredConstructor().newInstance();
        int observedCount = targetCls.getField("initCount").getInt(null);
        assertEquals(1, observedCount, "DEFECT-S1-01 Remediated: provider instantiated exactly once (count=1)");

        callerCls.getDeclaredConstructor().newInstance();
        assertEquals(2, targetCls.getField("initCount").getInt(null), "Subsequent construction increments count by exactly 1 (1 -> 2)");

        // Explicit DI constructor does NOT invoke default provider:
        Class<?> ifaceCls = cl.loadClass("com.demo.ITargetService");
        Object explicitTarget = targetCls.getDeclaredConstructor().newInstance(); // increments to 3
        assertEquals(3, targetCls.getField("initCount").getInt(null));
        callerCls.getDeclaredConstructor(ifaceCls).newInstance(explicitTarget);
        assertEquals(3, targetCls.getField("initCount").getInt(null), "Explicit DI constructor does not evaluate default provider");
    }

    // =========================================================================
    // Fixture D: Constructor exception
    // =========================================================================
    @Test
    @DisplayName("Fixture D: Constructor exception is not swallowed by default provider")
    public void testFixtureD_ConstructorExceptionPreserved() throws Exception {
        Path root = tempDir.resolve("fixture-d");
        Path prov = writeSource(root, "src/com/demo/TargetService.java",
                "package com.demo;\n" +
                "public class TargetService {\n" +
                "    public TargetService() {\n" +
                "        throw new IllegalStateException(\"Intentional failure\");\n" +
                "    }\n" +
                "    public void ping() {}\n" +
                "}\n");
        Path call = writeSource(root, "src/com/demo/Caller.java",
                "package com.demo;\n" +
                "public class Caller {\n" +
                "    private TargetService svc;\n" +
                "    public void run() { if (svc != null) svc.ping(); }\n" +
                "}\n");

        AstInterfaceExtractor extractor = new AstInterfaceExtractor();
        AstInterfaceExtractor.ExtractionResult ext = extractor.extractInterface(root, prov, "TargetService");
        Path ifaceFile = ext.interfaceFile;
        Files.writeString(ifaceFile, ext.interfaceSource, StandardCharsets.UTF_8);
        SourceFileEditor.applyEditsToFile(prov, List.of(ext.classImplementsEdit));

        AstCallerRewriter rewriter = new AstCallerRewriter();
        AstCallerRewriter.RewriteResult rw = rewriter.rewriteCaller(
                call, "TargetService", "com.demo.TargetService",
                "ITargetService", "com.demo.ITargetService", "com.demo", true);
        SourceFileEditor.applyEditsToFile(call, rw.getEdits());

        ClassLoader cl = compileAndLoad(root, List.of(ifaceFile, prov, call));
        Class<?> callerCls = cl.loadClass("com.demo.Caller");

        // Instantiating caller should trigger default provider, which fails and propagates the exception
        InvocationTargetException ite = assertThrows(InvocationTargetException.class, () -> callerCls.getDeclaredConstructor().newInstance());
        Throwable cause = ite.getCause();
        assertTrue(cause instanceof IllegalStateException, "Exception must propagate as IllegalStateException");
        // Reflection wraps it: cause -> InvocationTargetException -> IllegalStateException("Intentional failure")
        Throwable reflectionTarget = cause.getCause();
        assertTrue(reflectionTarget instanceof InvocationTargetException);
        Throwable original = ((InvocationTargetException) reflectionTarget).getTargetException();
        assertTrue(original instanceof IllegalStateException);
        assertEquals("Intentional failure", original.getMessage(), "Original exception message must be preserved");
    }

    // =========================================================================
    // Fixture E: Private constructor
    // =========================================================================
    @Test
    @DisplayName("Fixture E: Private constructor in target causes IllegalAccessException")
    public void testFixtureE_PrivateConstructor() throws Exception {
        Path root = tempDir.resolve("fixture-e");
        Path prov = writeSource(root, "src/com/demo/TargetService.java",
                "package com.demo;\n" +
                "public class TargetService {\n" +
                "    private TargetService() {}\n" +
                "    public static TargetService create() { return new TargetService(); }\n" +
                "    public void ping() {}\n" +
                "}\n");
        Path call = writeSource(root, "src/com/demo/Caller.java",
                "package com.demo;\n" +
                "public class Caller {\n" +
                "    private TargetService svc;\n" +
                "    public void run() { if (svc != null) svc.ping(); }\n" +
                "}\n");

        AstInterfaceExtractor extractor = new AstInterfaceExtractor();
        AstInterfaceExtractor.ExtractionResult ext = extractor.extractInterface(root, prov, "TargetService");
        Path ifaceFile = ext.interfaceFile;
        Files.writeString(ifaceFile, ext.interfaceSource, StandardCharsets.UTF_8);
        SourceFileEditor.applyEditsToFile(prov, List.of(ext.classImplementsEdit));

        AstCallerRewriter rewriter = new AstCallerRewriter();
        AstCallerRewriter.RewriteResult rw = rewriter.rewriteCaller(
                call, "TargetService", "com.demo.TargetService",
                "ITargetService", "com.demo.ITargetService", "com.demo", true);
        SourceFileEditor.applyEditsToFile(call, rw.getEdits());

        ClassLoader cl = compileAndLoad(root, List.of(ifaceFile, prov, call));
        Class<?> callerCls = cl.loadClass("com.demo.Caller");

        // DEFECT-S1-02 REMEDIATED:
        // When TargetService constructor is private, AstCallerRewriter detects it is inaccessible
        // and omits default constructor and factory method, requiring explicit DI instead of failing at runtime!
        assertThrows(NoSuchMethodException.class, () -> callerCls.getDeclaredConstructor(),
                "Target with private constructor must omit default no-arg constructor");
        assertFalse(rw.isDefaultProviderGenerated(), "Default provider factory must not be generated for private constructor");
        Class<?> ifaceCls = cl.loadClass("com.demo.ITargetService");
        assertNotNull(callerCls.getDeclaredConstructor(ifaceCls), "Explicit DI constructor must be generated");
    }

    // =========================================================================
    // Fixture F: Abstract implementation
    // =========================================================================
    @Test
    @DisplayName("Fixture F: Abstract target class cannot be instantiated by default provider")
    public void testFixtureF_AbstractImplementation() throws Exception {
        Path root = tempDir.resolve("fixture-f");
        Path prov = writeSource(root, "src/com/demo/TargetService.java",
                "package com.demo;\n" +
                "public abstract class TargetService {\n" +
                "    public TargetService() {}\n" +
                "    public abstract void ping();\n" +
                "}\n");
        Path call = writeSource(root, "src/com/demo/Caller.java",
                "package com.demo;\n" +
                "public class Caller {\n" +
                "    private TargetService svc;\n" +
                "    public void run() { if (svc != null) svc.ping(); }\n" +
                "}\n");

        AstInterfaceExtractor extractor = new AstInterfaceExtractor();
        AstInterfaceExtractor.ExtractionResult ext = extractor.extractInterface(root, prov, "TargetService");
        Path ifaceFile = ext.interfaceFile;
        Files.writeString(ifaceFile, ext.interfaceSource, StandardCharsets.UTF_8);
        SourceFileEditor.applyEditsToFile(prov, List.of(ext.classImplementsEdit));

        AstCallerRewriter rewriter = new AstCallerRewriter();
        AstCallerRewriter.RewriteResult rw = rewriter.rewriteCaller(
                call, "TargetService", "com.demo.TargetService",
                "ITargetService", "com.demo.ITargetService", "com.demo", true);
        SourceFileEditor.applyEditsToFile(call, rw.getEdits());

        ClassLoader cl = compileAndLoad(root, List.of(ifaceFile, prov, call));
        Class<?> callerCls = cl.loadClass("com.demo.Caller");

        // DEFECT-S1-02 REMEDIATED:
        // Abstract class cannot be instantiated, so AstCallerRewriter omits default constructor and factory!
        assertThrows(NoSuchMethodException.class, () -> callerCls.getDeclaredConstructor(),
                "Abstract target class must omit default no-arg constructor");
        assertFalse(rw.isDefaultProviderGenerated(), "Default provider factory must not be generated for abstract class");
        Class<?> ifaceCls = cl.loadClass("com.demo.ITargetService");
        assertNotNull(callerCls.getDeclaredConstructor(ifaceCls), "Explicit DI constructor must be generated");
    }

    // =========================================================================
    // Fixture G: Multiple implementations
    // =========================================================================
    @Test
    @DisplayName("Fixture G: Default provider specifically instantiates original concrete class")
    public void testFixtureG_MultipleImplementations() throws Exception {
        Path root = tempDir.resolve("fixture-g");
        Path prov = writeSource(root, "src/com/demo/EmailNotification.java",
                "package com.demo;\n" +
                "public class EmailNotification {\n" +
                "    public String send() { return \"email\"; }\n" +
                "}\n");
        Path call = writeSource(root, "src/com/demo/AlertSender.java",
                "package com.demo;\n" +
                "public class AlertSender {\n" +
                "    private EmailNotification notifier = new EmailNotification();\n" +
                "    public String alert() { return notifier.send(); }\n" +
                "}\n");

        AstInterfaceExtractor extractor = new AstInterfaceExtractor();
        AstInterfaceExtractor.ExtractionResult ext = extractor.extractInterface(root, prov, "EmailNotification");
        Path ifaceFile = ext.interfaceFile;
        Files.writeString(ifaceFile, ext.interfaceSource, StandardCharsets.UTF_8);
        SourceFileEditor.applyEditsToFile(prov, List.of(ext.classImplementsEdit));

        // Create an alternate implementation
        Path alt = writeSource(root, "src/com/demo/SmsNotification.java",
                "package com.demo;\n" +
                "public class SmsNotification implements IEmailNotification {\n" +
                "    public String send() { return \"sms\"; }\n" +
                "}\n");

        AstCallerRewriter rewriter = new AstCallerRewriter();
        AstCallerRewriter.RewriteResult rw = rewriter.rewriteCaller(
                call, "EmailNotification", "com.demo.EmailNotification",
                "IEmailNotification", "com.demo.IEmailNotification", "com.demo", true);
        SourceFileEditor.applyEditsToFile(call, rw.getEdits());

        ClassLoader cl = compileAndLoad(root, List.of(ifaceFile, prov, alt, call));
        Class<?> alertCls = cl.loadClass("com.demo.AlertSender");
        Method alertMethod = alertCls.getMethod("alert");

        // 1. Default constructor must instantiate original EmailNotification (not SmsNotification)
        Object defaultSender = alertCls.getDeclaredConstructor().newInstance();
        assertEquals("email", alertMethod.invoke(defaultSender), "Default provider must instantiate the original concrete class");

        // 2. DI constructor allows explicit polymorphic injection of SmsNotification
        Class<?> ifaceCls = cl.loadClass("com.demo.IEmailNotification");
        Class<?> smsCls = cl.loadClass("com.demo.SmsNotification");
        Object smsInstance = smsCls.getDeclaredConstructor().newInstance();
        Object customSender = alertCls.getDeclaredConstructor(ifaceCls).newInstance(smsInstance);
        assertEquals("sms", alertMethod.invoke(customSender), "DI constructor must accept polymorphic alternative implementation");
    }

    // =========================================================================
    // Fixture H: Stateful provider
    // =========================================================================
    @Test
    @DisplayName("Fixture H: Stateful provider maintains internal state and separate identity across callers")
    public void testFixtureH_StatefulProvider() throws Exception {
        Path root = tempDir.resolve("fixture-h");
        Path prov = writeSource(root, "src/com/demo/CounterService.java",
                "package com.demo;\n" +
                "public class CounterService {\n" +
                "    private int count = 0;\n" +
                "    public int increment() { return ++count; }\n" +
                "    public int get() { return count; }\n" +
                "}\n");
        Path call = writeSource(root, "src/com/demo/Caller.java",
                "package com.demo;\n" +
                "public class Caller {\n" +
                "    private CounterService counter = new CounterService();\n" +
                "    public int tick() { return counter.increment(); }\n" +
                "    public int value() { return counter.get(); }\n" +
                "}\n");

        AstInterfaceExtractor extractor = new AstInterfaceExtractor();
        AstInterfaceExtractor.ExtractionResult ext = extractor.extractInterface(root, prov, "CounterService");
        Path ifaceFile = ext.interfaceFile;
        Files.writeString(ifaceFile, ext.interfaceSource, StandardCharsets.UTF_8);
        SourceFileEditor.applyEditsToFile(prov, List.of(ext.classImplementsEdit));

        AstCallerRewriter rewriter = new AstCallerRewriter();
        AstCallerRewriter.RewriteResult rw = rewriter.rewriteCaller(
                call, "CounterService", "com.demo.CounterService",
                "ICounterService", "com.demo.ICounterService", "com.demo", true);
        SourceFileEditor.applyEditsToFile(call, rw.getEdits());

        ClassLoader cl = compileAndLoad(root, List.of(ifaceFile, prov, call));
        Class<?> callerCls = cl.loadClass("com.demo.Caller");
        Method tick = callerCls.getMethod("tick");
        Method value = callerCls.getMethod("value");

        // Instance 1
        Object c1 = callerCls.getDeclaredConstructor().newInstance();
        assertEquals(1, tick.invoke(c1));
        assertEquals(2, tick.invoke(c1));
        assertEquals(2, value.invoke(c1));

        // Instance 2 has its own isolated provider instance
        Object c2 = callerCls.getDeclaredConstructor().newInstance();
        assertEquals(1, tick.invoke(c2));
        assertEquals(1, value.invoke(c2));
        assertEquals(2, value.invoke(c1)); // c1 unaffected
    }
}
