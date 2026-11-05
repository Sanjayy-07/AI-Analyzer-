package com.archanalyzer.audit;

import com.archanalyzer.refactor.cycle.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase S.2 Dedicated Defect Remediation & Semantic Regression Suite.
 * Validates the remediation of:
 * - DEFECT-S1-01: Double provider instantiation (side effect counter, instance identity, final fields).
 * - DEFECT-S1-02: Unsafe reflective default construction (parameterized providers, private ctors, explicit DI).
 * - DEFECT-S1-04: Existing setter method collision (setter reuse, body preservation, overloads).
 */
public class PhaseS2DefectRemediationChallengeTest {

    @TempDir
    Path tempDir;

    // =========================================================================
    // DEFECT-S1-01: Double Provider Instantiation Remediation Tests
    // =========================================================================

    @Test
    @DisplayName("S1-01.1: Provider constructor side-effect count is exactly 1 per caller instance")
    public void testS101_ExactSideEffectCount() throws Exception {
        Path root = tempDir.resolve("s101-count");
        Path prov = writeSource(root, "src/com/demo/TargetService.java",
                "package com.demo;\n" +
                "public class TargetService {\n" +
                "    public static int initCount = 0;\n" +
                "    public TargetService() { initCount++; }\n" +
                "    public int getCount() { return initCount; }\n" +
                "}\n");
        Path call = writeSource(root, "src/com/demo/Caller.java",
                "package com.demo;\n" +
                "public class Caller {\n" +
                "    private TargetService svc = new TargetService();\n" +
                "    public int run() { return svc.getCount(); }\n" +
                "}\n");

        runExtractionAndRewrite(root, prov, call, "TargetService", true);

        ClassLoader cl = compileAndLoad(root);
        Class<?> targetCls = cl.loadClass("com.demo.TargetService");
        Class<?> callerCls = cl.loadClass("com.demo.Caller");

        assertEquals(0, targetCls.getField("initCount").getInt(null));

        // First instantiation: side-effect must increment by EXACTLY 1 (not 2)
        Object c1 = callerCls.getDeclaredConstructor().newInstance();
        assertEquals(1, targetCls.getField("initCount").getInt(null),
                "DEFECT-S1-01 Fix: Provider must be instantiated exactly once");

        // Second instantiation: side-effect must increment by EXACTLY 1 (total 2)
        Object c2 = callerCls.getDeclaredConstructor().newInstance();
        assertEquals(2, targetCls.getField("initCount").getInt(null),
                "Subsequent instantiation must increment count by exactly 1");

        // Explicit DI constructor: must NOT evaluate default provider (count remains 2)
        Class<?> ifaceCls = cl.loadClass("com.demo.ITargetService");
        Object explicitSvc = targetCls.getDeclaredConstructor().newInstance(); // increments to 3
        assertEquals(3, targetCls.getField("initCount").getInt(null));

        Object c3 = callerCls.getDeclaredConstructor(ifaceCls).newInstance(explicitSvc);
        assertEquals(3, targetCls.getField("initCount").getInt(null),
                "Explicit DI constructor must not evaluate default provider");
    }

    @Test
    @DisplayName("S1-01.2: Provider instance identity is preserved (not overwritten by inline initializer)")
    public void testS101_ProviderInstanceIdentity() throws Exception {
        Path root = tempDir.resolve("s101-identity");
        Path prov = writeSource(root, "src/com/demo/TargetService.java",
                "package com.demo;\n" +
                "public class TargetService {\n" +
                "    private final long id = System.nanoTime();\n" +
                "    public long getId() { return id; }\n" +
                "}\n");
        Path call = writeSource(root, "src/com/demo/Caller.java",
                "package com.demo;\n" +
                "public class Caller {\n" +
                "    private TargetService svc = new TargetService();\n" +
                "    public TargetService getSvc() { return svc; }\n" +
                "}\n");

        runExtractionAndRewrite(root, prov, call, "TargetService", true);

        ClassLoader cl = compileAndLoad(root);
        Class<?> targetCls = cl.loadClass("com.demo.TargetService");
        Class<?> ifaceCls = cl.loadClass("com.demo.ITargetService");
        Class<?> callerCls = cl.loadClass("com.demo.Caller");

        Object customProvider = targetCls.getDeclaredConstructor().newInstance();
        Object caller = callerCls.getDeclaredConstructor(ifaceCls).newInstance(customProvider);

        Method getSvc = callerCls.getMethod("getSvc");
        Object retrieved = getSvc.invoke(caller);

        assertSame(customProvider, retrieved, "Injected provider identity must be strictly preserved without being overwritten");
    }

    @Test
    @DisplayName("S1-01.3: Final dependency fields compile cleanly with blank final assignment")
    public void testS101_FinalDependencyFields() throws Exception {
        Path root = tempDir.resolve("s101-final");
        Path prov = writeSource(root, "src/com/demo/TargetService.java",
                "package com.demo;\n" +
                "public class TargetService { public String name() { return \"final-ok\"; } }\n");
        Path call = writeSource(root, "src/com/demo/Caller.java",
                "package com.demo;\n" +
                "public class Caller {\n" +
                "    private final TargetService svc = new TargetService();\n" +
                "    public String getName() { return svc.name(); }\n" +
                "}\n");

        runExtractionAndRewrite(root, prov, call, "TargetService", true);

        ClassLoader cl = compileAndLoad(root);
        Class<?> callerCls = cl.loadClass("com.demo.Caller");

        Object caller = callerCls.getDeclaredConstructor().newInstance();
        Method getName = callerCls.getMethod("getName");
        assertEquals("final-ok", getName.invoke(caller));
    }

    // =========================================================================
    // DEFECT-S1-02: Unsafe Reflective Provider Construction Remediation Tests
    // =========================================================================

    @Test
    @DisplayName("S1-02.1: Target with parameterized constructor only omits unsafe default constructor")
    public void testS102_ParameterizedConstructorOnly() throws Exception {
        Path root = tempDir.resolve("s102-param");
        Path repo = writeSource(root, "src/com/demo/Repo.java",
                "package com.demo;\n" +
                "public class Repo { public String fetch() { return \"db-data\"; } }\n");
        Path prov = writeSource(root, "src/com/demo/TargetService.java",
                "package com.demo;\n" +
                "public class TargetService {\n" +
                "    private final Repo repo;\n" +
                "    public TargetService(Repo repo) { this.repo = repo; }\n" +
                "    public String getData() { return repo.fetch(); }\n" +
                "}\n");
        Path call = writeSource(root, "src/com/demo/Caller.java",
                "package com.demo;\n" +
                "public class Caller {\n" +
                "    private TargetService svc;\n" +
                "    public String query() { return svc != null ? svc.getData() : \"none\"; }\n" +
                "}\n");

        AstCallerRewriter.RewriteResult rw = runExtractionAndRewrite(root, prov, call, "TargetService", true);

        // Verification of requirements:
        assertFalse(rw.isDefaultProviderGenerated(), "Default provider factory must not be generated");
        assertTrue(rw.getExplanation().contains("lacks an accessible parameterless constructor"),
                "Explanation must describe why default constructor was omitted");

        ClassLoader cl = compileAndLoad(root);
        Class<?> callerCls = cl.loadClass("com.demo.Caller");
        Class<?> ifaceCls = cl.loadClass("com.demo.ITargetService");
        Class<?> targetCls = cl.loadClass("com.demo.TargetService");
        Class<?> repoCls = cl.loadClass("com.demo.Repo");

        // Default constructor does NOT exist, preventing unhandled runtime NoSuchMethodException
        assertThrows(NoSuchMethodException.class, () -> callerCls.getDeclaredConstructor());

        // Explicit DI constructor exists and executes correctly at runtime
        Object r = repoCls.getDeclaredConstructor().newInstance();
        Object target = targetCls.getDeclaredConstructor(repoCls).newInstance(r);
        Object caller = callerCls.getDeclaredConstructor(ifaceCls).newInstance(target);

        Method query = callerCls.getMethod("query");
        assertEquals("db-data", query.invoke(caller));
    }

    @Test
    @DisplayName("S1-02.2: Target with private constructor omits unsafe default provider")
    public void testS102_PrivateConstructorTarget() throws Exception {
        Path root = tempDir.resolve("s102-private");
        Path prov = writeSource(root, "src/com/demo/TargetService.java",
                "package com.demo;\n" +
                "public class TargetService {\n" +
                "    private TargetService() {}\n" +
                "    public static TargetService create() { return new TargetService(); }\n" +
                "    public String ping() { return \"pong\"; }\n" +
                "}\n");
        Path call = writeSource(root, "src/com/demo/Caller.java",
                "package com.demo;\n" +
                "public class Caller {\n" +
                "    private TargetService svc;\n" +
                "    public String test() { return svc != null ? svc.ping() : \"\"; }\n" +
                "}\n");

        AstCallerRewriter.RewriteResult rw = runExtractionAndRewrite(root, prov, call, "TargetService", true);

        assertFalse(rw.isDefaultProviderGenerated());
        ClassLoader cl = compileAndLoad(root);
        Class<?> callerCls = cl.loadClass("com.demo.Caller");
        assertThrows(NoSuchMethodException.class, () -> callerCls.getDeclaredConstructor());
    }

    // =========================================================================
    // DEFECT-S1-04: Existing Setter Method Collision Remediation Tests
    // =========================================================================

    @Test
    @DisplayName("S1-04.1: Pre-existing setter accepting concrete type is rewritten and reused without duplicate")
    public void testS104_ExistingSetterReused() throws Exception {
        Path root = tempDir.resolve("s104-reuse");
        Path prov = writeSource(root, "src/com/demo/TargetService.java",
                "package com.demo;\n" +
                "public class TargetService { public int calc(int x) { return x * 3; } }\n");
        Path call = writeSource(root, "src/com/demo/Caller.java",
                "package com.demo;\n" +
                "public class Caller {\n" +
                "    private TargetService svc;\n" +
                "    private boolean customSetterExecuted = false;\n" +
                "    public void setSvc(TargetService svc) {\n" +
                "        this.svc = svc;\n" +
                "        this.customSetterExecuted = true;\n" +
                "    }\n" +
                "    public boolean isCustomSetterExecuted() { return customSetterExecuted; }\n" +
                "    public int compute(int x) { return svc.calc(x); }\n" +
                "}\n");

        runExtractionAndRewrite(root, prov, call, "TargetService", true);

        ClassLoader cl = compileAndLoad(root);
        Class<?> callerCls = cl.loadClass("com.demo.Caller");
        Class<?> ifaceCls = cl.loadClass("com.demo.ITargetService");
        Class<?> targetCls = cl.loadClass("com.demo.TargetService");

        // Verify only ONE setSvc(ITargetService) method exists
        Method[] methods = callerCls.getDeclaredMethods();
        long setSvcCount = java.util.Arrays.stream(methods)
                .filter(m -> m.getName().equals("setSvc") && m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(ifaceCls))
                .count();
        assertEquals(1, setSvcCount, "Must have exactly 1 setter method, no duplicate signature collision");

        // Verify original setter body & side effects are preserved
        Object caller = callerCls.getDeclaredConstructor().newInstance();
        Object target = targetCls.getDeclaredConstructor().newInstance();
        Method setSvc = callerCls.getMethod("setSvc", ifaceCls);
        setSvc.invoke(caller, target);

        Method isCustom = callerCls.getMethod("isCustomSetterExecuted");
        assertTrue((Boolean) isCustom.invoke(caller), "Custom setter body statements must be preserved");

        Method compute = callerCls.getMethod("compute", int.class);
        assertEquals(15, compute.invoke(caller, 5));
    }

    @Test
    @DisplayName("S1-04.2: Overloaded setters with different parameter types are preserved")
    public void testS104_OverloadedSettersPreserved() throws Exception {
        Path root = tempDir.resolve("s104-overload");
        Path prov = writeSource(root, "src/com/demo/TargetService.java",
                "package com.demo;\n" +
                "public class TargetService { public String status() { return \"ok\"; } }\n");
        Path call = writeSource(root, "src/com/demo/Caller.java",
                "package com.demo;\n" +
                "public class Caller {\n" +
                "    private TargetService svc;\n" +
                "    private int id;\n" +
                "    public void setSvc(TargetService svc) { this.svc = svc; }\n" +
                "    public void setSvc(int id) { this.id = id; }\n" +
                "    public int getId() { return id; }\n" +
                "}\n");

        runExtractionAndRewrite(root, prov, call, "TargetService", true);

        ClassLoader cl = compileAndLoad(root);
        Class<?> callerCls = cl.loadClass("com.demo.Caller");
        Class<?> ifaceCls = cl.loadClass("com.demo.ITargetService");

        assertNotNull(callerCls.getMethod("setSvc", ifaceCls), "Interface setter must exist");
        assertNotNull(callerCls.getMethod("setSvc", int.class), "Overloaded int setter must be preserved");
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private Path writeSource(Path root, String relPath, String content) throws IOException {
        Path file = root.resolve(relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private AstCallerRewriter.RewriteResult runExtractionAndRewrite(Path root, Path providerFile, Path callerFile,
                                                                   String targetSimpleName, boolean decouple) throws Exception {
        AstInterfaceExtractor extractor = new AstInterfaceExtractor();
        AstInterfaceExtractor.ExtractionResult ext = extractor.extractInterface(root, providerFile, targetSimpleName);
        Files.writeString(ext.interfaceFile, ext.interfaceSource, StandardCharsets.UTF_8);
        SourceFileEditor.applyEditsToFile(providerFile, List.of(ext.classImplementsEdit));

        AstCallerRewriter rewriter = new AstCallerRewriter();
        AstCallerRewriter.RewriteResult rw = rewriter.rewriteCaller(
                callerFile,
                providerFile,
                targetSimpleName,
                "com.demo." + targetSimpleName,
                ext.interfaceName,
                ext.packageName.isEmpty() ? ext.interfaceName : ext.packageName + "." + ext.interfaceName,
                ext.packageName,
                decouple
        );
        SourceFileEditor.applyEditsToFile(callerFile, rw.getEdits());
        return rw;
    }

    private ClassLoader compileAndLoad(Path root) throws Exception {
        List<Path> javaFiles = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(javaFiles::add);
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        Path binDir = root.resolve("target/classes");
        Files.createDirectories(binDir);

        List<String> options = List.of("-d", binDir.toString(), "-source", "17", "-target", "17");
        var cu = fm.getJavaFileObjectsFromPaths(javaFiles);
        java.io.StringWriter out = new java.io.StringWriter();
        boolean ok = compiler.getTask(out, fm, null, options, null, cu).call();
        fm.close();

        assertTrue(ok, "Compilation failed: " + out);
        return new URLClassLoader(new URL[]{binDir.toUri().toURL()}, getClass().getClassLoader());
    }
}
